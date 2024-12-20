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

import android.os.RemoteException;
import android.ranging.oob.IOobSendDataListener;
import android.ranging.oob.OobHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OobController {
    private static final String TAG = OobController.class.getSimpleName();

    private final ConcurrentMap<OobHandle, OobConnection> mActiveConnections;

    private @Nullable IOobSendDataListener mOobDataSender = null;

    public static class ReceivedMessage {
        private final OobHandle mOobHandle;
        private final byte[] mMessageBytes;

        ReceivedMessage(OobHandle handle, byte[] message) {
            mOobHandle = handle;
            mMessageBytes = message;
        }

        public OobHandle getOobHandle() {
            return mOobHandle;
        }

        public byte[] asBytes() {
            return mMessageBytes;
        }
    }

    private static class OobConnection {
        private boolean mIsConnected = true;
        private final SettableFuture<ReceivedMessage> mAwaitingMessage;

        OobConnection(SettableFuture<ReceivedMessage> awaitingMessage) {
            mAwaitingMessage = awaitingMessage;
        }
    }

    public OobController() {
        mActiveConnections = new ConcurrentHashMap<>();
    }

    public void registerDataSender(IOobSendDataListener oobDataSender) {
        if (mOobDataSender != null) {
            Log.w(TAG, "Re-registered oob send data listener");
        }
        mOobDataSender = oobDataSender;
    }

    public ListenableFuture<ReceivedMessage> registerMessageListener(OobHandle handle) {
        SettableFuture<ReceivedMessage> future = SettableFuture.create();
        mActiveConnections.put(handle, new OobConnection(future));
        return future;
    }

    public void sendMessage(OobHandle handle, byte[] message) {
        if (mOobDataSender == null) {
            Log.e(TAG, "Attempt to send oob message with no data sender registered");
            return;
        }

        try {
            mOobDataSender.sendOobData(handle, message);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to send OOB message over binder: ", e);
        }
    }

    public void handleOobDataReceived(OobHandle oobHandle, byte[] data) {
        OobConnection connection = mActiveConnections.remove(oobHandle);
        if (connection == null) {
            Log.w(TAG, "Received OOB message on unknown handle " + oobHandle
                    + ". Ignoring...");
        } else {
            connection.mAwaitingMessage.set(new ReceivedMessage(oobHandle, data));
        }
    }

    public void handleOobDeviceDisconnected(OobHandle oobHandle) {
        OobConnection connection = mActiveConnections.get(oobHandle);
        if (connection != null) {
            Log.i(TAG, "A peer with an active connection has disconnected on handle " + oobHandle);
            connection.mIsConnected = false;
        }
    }

    public void handleOobDeviceReconnected(OobHandle oobHandle) {
        OobConnection connection = mActiveConnections.get(oobHandle);
        if (connection == null) {
            Log.w(TAG, "Unknown peer reconnected on handle " + oobHandle + ". Ignoring...");
        } else {
            Log.i(TAG, "The peer on handle " + oobHandle + " has reconnected");
            connection.mIsConnected = true;
        }
    }

    public void handleOobClosed(OobHandle oobHandle) {
        OobConnection connection = mActiveConnections.remove(oobHandle);
        if (connection != null) {
            Log.w(TAG, "Oob handle " + oobHandle + " closed with an active connection");
            connection.mAwaitingMessage.setException(new IllegalStateException("Oob closed"));
        }
    }
}
