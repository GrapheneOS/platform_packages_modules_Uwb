/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ranging.oob;

import android.app.AlarmManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.ranging.oob.IOobSendDataListener;
import android.ranging.oob.OobHandle;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingUtils.StateMachine;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public class OobController {
    private static final String TAG = OobController.class.getSimpleName();

    private static final int OOB_DISCONNECT_TIMEOUT_MS = 5_000;

    private final RangingInjector mInjector;
    private final AlarmManager mAlarmManager;
    private final ConcurrentMap<OobHandle, OobConnection> mConnections;

    private @Nullable IOobSendDataListener mOobDataSender = null;

    public static class OobException extends Exception {
        public OobException(String message) {
            super(message);
        }
    }

    /**
     * An OOB connection between the local device and a remote device. Each connection is
     * uniquely identified by its {@link OobHandle}.
     */
    public class OobConnection implements AutoCloseable {
        private final OobHandle mHandle;
        private final String mDisconnectTimeoutAlarmTag;
        private final ConcurrentLinkedQueue<Pair<byte[], SettableFuture<Void>>> mPendingDataSends;
        private final ConcurrentLinkedQueue<SettableFuture<byte[]>> mPendingReceivers;
        private final ConcurrentLinkedQueue<byte[]> mReceivedData;
        private final StateMachine<State> mStateMachine = new StateMachine<>(State.CONNECTED);

        OobConnection(OobHandle handle) {
            mHandle = handle;
            mDisconnectTimeoutAlarmTag = "RangingOobConnection" + mHandle + "DisconnectTimeout";
            mPendingDataSends = Queues.newConcurrentLinkedQueue();
            mPendingReceivers = Queues.newConcurrentLinkedQueue();
            mReceivedData = Queues.newConcurrentLinkedQueue();
        }

        public FluentFuture<Void> sendData(byte[] data) {
            SettableFuture<Void> future = SettableFuture.create();
            setDataSendFuture(data, future);
            return FluentFuture.from(future);
        }

        public FluentFuture<byte[]> receiveData() {
            if (mStateMachine.getState() == State.CLOSED) {
                return FluentFuture.from(Futures.immediateFailedFuture(new OobException(
                        "Attempted to receive oob message on closed connection " + mHandle)));
            }

            if (mReceivedData.isEmpty()) {
                SettableFuture<byte[]> future = SettableFuture.create();
                mPendingReceivers.offer(future);
                return FluentFuture.from(future);
            } else {
                return FluentFuture.from(Futures.immediateFuture(mReceivedData.poll()));
            }
        }

        @Override
        public void close() {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.CLOSED) return;
                mStateMachine.setState(State.CLOSED);
            }

            mPendingDataSends.forEach((sender) ->
                    sender.second.setException(new OobException("Oob connection closed")));
            mPendingDataSends.clear();

            mPendingReceivers.forEach((receiver) ->
                    receiver.setException(new OobException("Oob connection closed")));
            mPendingReceivers.clear();

            mReceivedData.clear();
            mConnections.remove(mHandle);
        }

        private void handleReceiveData(byte[] data) {
            if (mPendingReceivers.isEmpty()) {
                mReceivedData.offer(data);
            } else {
                mPendingReceivers.poll().set(data);
            }
        }

        private void handleDisconnect() {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.CONNECTED) return;
                mStateMachine.setState(State.DISCONNECTED);
                mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + OOB_DISCONNECT_TIMEOUT_MS,
                        mDisconnectTimeoutAlarmTag, mDisconnectTimeoutListener,
                        mInjector.getAlarmHandler());
            }
        }

        private void handleReconnect() {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.DISCONNECTED) return;
                mStateMachine.setState(State.CONNECTED);
                mAlarmManager.cancel(mDisconnectTimeoutListener);
            }

            while (mStateMachine.getState() == State.CONNECTED && !mPendingDataSends.isEmpty()) {
                Pair<byte[], SettableFuture<Void>> send = mPendingDataSends.poll();
                setDataSendFuture(send.first, send.second);
            }
        }

        private void setDataSendFuture(byte[] data, SettableFuture<Void> future) {
            if (mOobDataSender == null) {
                future.setException(new OobException(
                        "Attempted to send oob message with no data sender registered"));
                return;
            }
            switch (mStateMachine.getState()) {
                case CLOSED: {
                    future.setException(new OobException(
                            "Attempted to send oob message on closed session " + mHandle));
                    return;
                }
                case CONNECTED: {
                    try {
                        mOobDataSender.sendOobData(mHandle, data);
                        future.setFuture(Futures.immediateVoidFuture());
                    } catch (RemoteException e) {
                        future.setException(new OobException(
                                "Failed to send oob message over binder: " + e));
                    }
                    return;
                }
                case DISCONNECTED: {
                    mPendingDataSends.add(Pair.create(data, future));
                    return;
                }
            }
        }

        private final AlarmManager.OnAlarmListener mDisconnectTimeoutListener = () -> {
            if (mStateMachine.getState() != State.DISCONNECTED) return;

            Log.w(TAG, "Oob connection in disconnected state for longer than timeout of "
                    + OOB_DISCONNECT_TIMEOUT_MS + " ms. Closing...");
            close();
        };
    }

    public OobController(RangingInjector injector) {
        mInjector = injector;
        mConnections = new ConcurrentHashMap<>();
        mAlarmManager = mInjector.getContext().getSystemService(AlarmManager.class);
    }

    public void registerDataSender(IOobSendDataListener oobDataSender) {
        if (mOobDataSender != null) {
            Log.w(TAG, "Re-registered oob send data listener");
        }
        mOobDataSender = oobDataSender;
    }

    public OobConnection createConnection(OobHandle handle) {
        OobConnection connection = new OobConnection(handle);
        mConnections.put(handle, connection);
        return connection;
    }

    public void handleOobDataReceived(OobHandle oobHandle, byte[] data) {
        OobConnection connection = mConnections.get(oobHandle);
        if (connection == null) {
            Log.w(TAG, "Received message on unknown connection " + oobHandle + ". Ignoring...");
        } else {
            connection.handleReceiveData(data);
        }
    }

    public void handleOobDeviceDisconnected(OobHandle oobHandle) {
        OobConnection connection = mConnections.get(oobHandle);
        if (connection == null) {
            Log.w(TAG, "Unknown peer disconnected on handle " + oobHandle + ". Ignoring...");
        } else {
            Log.v(TAG, "A peer with an active connection has disconnected on handle " + oobHandle);
            connection.handleDisconnect();
        }
    }

    public void handleOobDeviceReconnected(OobHandle oobHandle) {
        OobConnection connection = mConnections.get(oobHandle);
        if (connection == null) {
            Log.w(TAG, "Unknown peer reconnected on handle " + oobHandle + ". Ignoring...");
        } else {
            Log.v(TAG, "The peer on handle " + oobHandle + " has reconnected");
            connection.handleReconnect();
        }
    }

    public void handleOobClosed(OobHandle oobHandle) {
        OobConnection connection = mConnections.remove(oobHandle);
        if (connection == null) {
            Log.w(TAG, "Attempted to close unknown oob connection " + oobHandle + ". Ignoring...");
        } else {
            connection.close();
        }
    }

    private enum State {
        CONNECTED,
        DISCONNECTED,
        CLOSED
    }
}
