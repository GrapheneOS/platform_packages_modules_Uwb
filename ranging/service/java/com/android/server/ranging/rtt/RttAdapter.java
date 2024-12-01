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

package com.android.server.ranging.rtt;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;

import android.content.Context;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingMeasurement;
import android.ranging.RangingPreference;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.ranging.rtt.backend.internal.RttDevice;
import com.android.ranging.rtt.backend.internal.RttRangingDevice;
import com.android.ranging.rtt.backend.internal.RttRangingPosition;
import com.android.ranging.rtt.backend.internal.RttRangingSessionCallback;
import com.android.ranging.rtt.backend.internal.RttService;
import com.android.ranging.rtt.backend.internal.RttServiceImpl;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingSessionConfig;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.StateMachine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Executors;

/** Ranging adapter for WiFi Round-To-Trip(RTT). */
public class RttAdapter implements RangingAdapter {

    private static final String TAG = RttAdapter.class.getSimpleName();

    private final RttService mRttService;
    private final RttRangingDevice mRttClient;
    private final ListeningExecutorService mExecutorService;
    private final ExecutorResultHandlers mRttClientResultHandlers = new ExecutorResultHandlers();
    private final RttRangingSessionCallback mRttListener = new RttListener();
    private final StateMachine<State> mStateMachine;

    /** Invariant: non-null while a ranging session is active */
    private Callback mCallbacks;
    /** Invariant: non-null while a ranging session is active */
    private RangingDevice mPeerDevice;

    public RttAdapter(
            @NonNull Context context, @NonNull ListeningExecutorService executorService,
            @RangingPreference.DeviceRole int role
    ) {
        this(context, executorService, new RttServiceImpl(context), role);
    }

    @VisibleForTesting
    public RttAdapter(@NonNull Context context, @NonNull ListeningExecutorService executorService,
            @NonNull RttService rttService, @RangingPreference.DeviceRole int role) {
        if (!RttCapabilitiesAdapter.isSupported(context)) {
            throw new IllegalArgumentException("WiFi RTT system feature not found.");
        }

        mStateMachine = new StateMachine<>(State.STOPPED);
        mRttService = rttService;
        mRttClient = role == DEVICE_ROLE_INITIATOR
                ? mRttService.getSubscriber(context)
                : mRttService.getPublisher(context);

        mExecutorService = executorService;
        mCallbacks = null;
        mPeerDevice = null;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.RTT;
    }

    @Override
    public void start(
            @NonNull RangingSessionConfig.TechnologyConfig config, @NonNull Callback callbacks
    ) {
        Log.i(TAG, "Start called.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            return;
        }

        mCallbacks = callbacks;
        if (!(config instanceof RttConfig rttConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            closeForReason(Callback.ClosedReason.FAILED_TO_START);
            return;
        }
        mPeerDevice = rttConfig.getPeerDevice();
        mRttClient.setRangingParameters(rttConfig.asBackendParameters());

        var future = Futures.submit(() -> {
            mRttClient.startRanging(mRttListener, Executors.newSingleThreadExecutor());
        }, mExecutorService);
        Futures.addCallback(future, mRttClientResultHandlers.startRanging, mExecutorService);
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");
        if (!mStateMachine.transition(State.STARTED, State.STOPPED)) {
            Log.v(TAG, "Attempted to stop adapter when it was already stopped");
            return;
        }

        var future = Futures.submit(mRttClient::stopRanging, mExecutorService);
        Futures.addCallback(future, mRttClientResultHandlers.stopRanging, mExecutorService);
    }


    private class RttListener implements RttRangingSessionCallback {
        @Override
        public void onRangingInitialized(RttDevice device) {
            Log.i(TAG, "onRangingInitialized");
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STARTED) {
                    mCallbacks.onStarted(mPeerDevice);
                }
            }
        }

        @Override
        public void onRangingResult(RttDevice peer, RttRangingPosition position) {
            RangingData.Builder dataBuilder = new RangingData.Builder()
                    .setRangingTechnology(RangingManager.WIFI_NAN_RTT)
                    .setDistance(new RangingMeasurement.Builder()
                            .setMeasurement(position.getDistance())
                            .build())
                    .setRssi(position.getRssiDbm())
                    .setTimestampMillis(position.getRangingTimestampMillis());

            if (position.getAzimuth() != null) {
                dataBuilder.setAzimuth(new RangingMeasurement.Builder()
                        .setMeasurement(position.getAzimuth().getValue())
                        .build());
            }
            if (position.getElevation() != null) {
                dataBuilder.setElevation(new RangingMeasurement.Builder()
                        .setMeasurement(position.getElevation().getValue())
                        .build());
            }
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STARTED) {
                    mCallbacks.onRangingData(mPeerDevice, dataBuilder.build());
                }
            }
        }

        private static int convertReason(int reason) {
            switch (reason) {
                case REASON_WRONG_PARAMETERS:
                case REASON_FAILED_TO_START:
                    return Callback.ClosedReason.FAILED_TO_START;
                case REASON_STOPPED_BY_PEER:
                case REASON_STOP_RANGING_CALLED:
                    return Callback.ClosedReason.REQUESTED;
                case REASON_MAX_RANGING_ROUND_RETRY_REACHED:
                    return Callback.ClosedReason.LOST_CONNECTION;
                case REASON_SYSTEM_POLICY:
                    return Callback.ClosedReason.SYSTEM_POLICY;
                default:
                    return Callback.ClosedReason.UNKNOWN;
            }
        }

        @Override
        public void onRangingSuspended(RttDevice localDevice, int reason) {
            Log.i(TAG, "onRangingSuspended: " + reason);
            closeForReason(convertReason(reason));
        }
    }

    /** Close the session, disconnecting the peer and resetting internal state. */
    private void closeForReason(@Callback.ClosedReason int reason) {
        synchronized (mStateMachine) {
            mStateMachine.setState(State.STOPPED);
            mCallbacks.onStopped(mPeerDevice);
            mCallbacks.onClosed(reason);
            clear();
        }
    }

    private void clear() {
        mRttClient.stopRanging();
        mCallbacks = null;
        mPeerDevice = null;
    }

    public enum State {
        STARTED,
        STOPPED,
    }

    private class ExecutorResultHandlers {
        public final FutureCallback<Void> startRanging = new FutureCallback<>() {
            @Override
            public void onSuccess(Void v) {
                Log.i(TAG, "startRanging succeeded.");
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.w(TAG, "startRanging failed ", t);
                closeForReason(Callback.ClosedReason.ERROR);
            }
        };

        public final FutureCallback<Void> stopRanging = new FutureCallback<>() {
            @Override
            public void onSuccess(Void v) {
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.w(TAG, "stopRanging failed ", t);
                closeForReason(Callback.ClosedReason.ERROR);
            }
        };
    }
}
