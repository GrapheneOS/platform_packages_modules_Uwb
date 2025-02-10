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

import static com.android.server.ranging.RangingAdapter.Callback.ClosedReason.ERROR;
import static com.android.server.ranging.RangingAdapter.Callback.ClosedReason.FAILED_TO_START;
import static com.android.server.ranging.RangingAdapter.Callback.ClosedReason.SYSTEM_POLICY;

import android.annotation.Nullable;
import android.app.AlarmManager;
import android.content.AttributionSource;
import android.content.Context;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingMeasurement;
import android.ranging.RangingPreference;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.ranging.rtt.backend.RttDevice;
import com.android.ranging.rtt.backend.RttRangingDevice;
import com.android.ranging.rtt.backend.RttRangingParameters;
import com.android.ranging.rtt.backend.RttRangingPosition;
import com.android.ranging.rtt.backend.RttRangingSessionCallback;
import com.android.ranging.rtt.backend.RttService;
import com.android.ranging.rtt.backend.RttServiceImpl;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.util.DataNotificationManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.Executors;

/** Ranging adapter for WiFi Round-To-Trip(RTT). */
public class RttAdapter implements RangingAdapter {

    private static final String TAG = RttAdapter.class.getSimpleName();

    private final Context mContext;
    private final RangingInjector mRangingInjector;
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
    private RttConfig mConfig;

    private DataNotificationManager mDataNotificationManager;
    @Nullable
    private AttributionSource mNonPrivilegedAttributionSource;
    private final AlarmManager mAlarmManager;
    private final AlarmManager.OnAlarmListener mMeasurementLimitListener;

    public RttAdapter(
            @NonNull Context context,
            @NonNull RangingInjector rangingInjector,
            @NonNull ListeningExecutorService executorService,
            @RangingPreference.DeviceRole int role
    ) {
        this(context, rangingInjector, executorService, new RttServiceImpl(context), role);
    }

    @VisibleForTesting
    public RttAdapter(@NonNull Context context,
            @NonNull RangingInjector rangingInjector,
            @NonNull ListeningExecutorService executorService,
            @NonNull RttService rttService,
            @RangingPreference.DeviceRole int role) {
        if (!RttCapabilitiesAdapter.isSupported(context)) {
            throw new IllegalArgumentException("WiFi RTT system feature not found.");
        }
        mContext = context;
        mRangingInjector = rangingInjector;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mRttService = rttService;
        mRttClient = role == DEVICE_ROLE_INITIATOR
                ? mRttService.getSubscriber(context)
                : mRttService.getPublisher(context);

        mExecutorService = executorService;
        mCallbacks = null;
        mPeerDevice = null;
        mDataNotificationManager = new DataNotificationManager(
                new DataNotificationConfig.Builder().build(),
                new DataNotificationConfig.Builder().build()
        );
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mMeasurementLimitListener = () -> {
            Log.i(TAG, "Measurements limit exceeded. Stopping the session");
            Executors.newCachedThreadPool().execute(this::stop);
        };
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.RTT;
    }

    public DataNotificationManager getDataNotificationManager() {
        return mDataNotificationManager;
    }
    @Override
    public void start(
            @NonNull RangingSessionConfig.TechnologyConfig config,
            @Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callbacks
    ) {
        Log.i(TAG, "Start called.");
        mNonPrivilegedAttributionSource = nonPrivilegedAttributionSource;
        mCallbacks = callbacks;
        if (mNonPrivilegedAttributionSource != null && !mRangingInjector.isForegroundAppOrService(
                mNonPrivilegedAttributionSource.getUid(),
                mNonPrivilegedAttributionSource.getPackageName())) {
            Log.w(TAG, "Background ranging is not supported");
            closeForReason(SYSTEM_POLICY);
            return;
        }
        if (!(config instanceof RttConfig rttConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            closeForReason(ERROR);
            return;
        }
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            closeForReason(FAILED_TO_START);
            return;
        }
        mConfig = rttConfig;
        mPeerDevice = rttConfig.getPeerDevice();
        mRttClient.setRangingParameters(rttConfig.asBackendParameters());
        mDataNotificationManager = new DataNotificationManager(
                rttConfig.getSessionConfig().getDataNotificationConfig(),
                rttConfig.getSessionConfig().getDataNotificationConfig());

        var future = Futures.submit(() -> {
            mRttClient.startRanging(mRttListener, Executors.newSingleThreadExecutor());
        }, mExecutorService);
        Futures.addCallback(future, mRttClientResultHandlers.startRanging, mExecutorService);
        if (mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
            RangingUtils.setMeasurementsLimitTimeout(
                    mAlarmManager,
                    mMeasurementLimitListener,
                    mConfig.getSessionConfig().getRangingMeasurementsLimit(),
                    RttRangingParameters.getIntervalMs(mRttClient.getRttRangingParameters()));
        }
    }

    @Override
    public void reconfigureRangingInterval(int intervalSkipCount) {
        Log.i(TAG, "Reconfigure ranging interval called");
        mRttClient.reconfigureRangingInterval(intervalSkipCount);
    }

    @Override
    public void appMovedToBackground() {
        if (mNonPrivilegedAttributionSource != null && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
        }
    }

    @Override
    public void appMovedToForeground() {
        if (mNonPrivilegedAttributionSource != null && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToForeground();
        }
    }

    @Override
    public void appInBackgroundTimeout() {
        if (mNonPrivilegedAttributionSource != null && mStateMachine.getState() != State.STOPPED) {
            stop();
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");
        if (mStateMachine.getState() == State.STOPPED) {
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
                    mCallbacks.onStarted(ImmutableSet.of(mPeerDevice));
                }
            }
        }

        @Override
        public void onRangingResult(RttDevice peer, RttRangingPosition position) {
            if (!mDataNotificationManager.shouldSendResult(position.getDistanceMeters())) {
                return;
            }
            RangingData.Builder dataBuilder = new RangingData.Builder()
                    .setRangingTechnology(RangingManager.WIFI_NAN_RTT)
                    .setDistance(new RangingMeasurement.Builder()
                            .setMeasurement(position.getDistanceMeters())
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

        private static @Callback.ClosedReason int convertReason(@RttSuspendedReason int reason) {
            switch (reason) {
                case REASON_WRONG_PARAMETERS:
                case REASON_FAILED_TO_START:
                    return Callback.ClosedReason.FAILED_TO_START;
                case REASON_STOPPED_BY_PEER:
                    return Callback.ClosedReason.REMOTE_REQUEST;
                case REASON_STOP_RANGING_CALLED:
                    return Callback.ClosedReason.LOCAL_REQUEST;
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
            if (mCallbacks != null) {
                mCallbacks.onStopped(ImmutableSet.of(mPeerDevice));
                mCallbacks.onClosed(reason);
            }
            clear();
        }
    }

    private void clear() {
        if (mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
            mAlarmManager.cancel(mMeasurementLimitListener);
        }
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
                closeForReason(ERROR);
            }
        };

        public final FutureCallback<Void> stopRanging = new FutureCallback<>() {
            @Override
            public void onSuccess(Void v) {
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.w(TAG, "stopRanging failed ", t);
                closeForReason(ERROR);
            }
        };
    }
}
