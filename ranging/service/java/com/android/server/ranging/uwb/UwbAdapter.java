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

package com.android.server.ranging.uwb;

import static com.android.server.ranging.uwb.UwbConfig.toBackend;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.RangingCapabilities;
import com.android.ranging.uwb.backend.internal.RangingController;
import com.android.ranging.uwb.backend.internal.RangingDevice;
import com.android.ranging.uwb.backend.internal.RangingPosition;
import com.android.ranging.uwb.backend.internal.RangingSessionCallback;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbDevice;
import com.android.ranging.uwb.backend.internal.UwbFeatureFlags;
import com.android.ranging.uwb.backend.internal.UwbServiceImpl;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingConfig;
import com.android.server.ranging.RangingData;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.StateMachine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.uwb.support.fira.FiraParams;

import java.time.Duration;
import java.util.concurrent.Executors;

/** Ranging adapter for Ultra-wideband (UWB). */
public class UwbAdapter implements RangingAdapter {
    private static final String TAG = UwbAdapter.class.getSimpleName();

    private final UwbServiceImpl mUwbService;
    // private IUwb mIUwb;

    private final RangingDevice mUwbClient;
    private final ListeningExecutorService mExecutorService;
    private final ExecutorResultHandlers mUwbClientResultHandlers = new ExecutorResultHandlers();
    private final RangingSessionCallback mUwbListener = new UwbListener();
    private final StateMachine<State> mStateMachine;

    /** Invariant: non-null while a ranging session is active */
    private Callback mCallbacks;

    /** @return true if UWB is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB);
    }

    public UwbAdapter(
            @NonNull Context context, @NonNull ListeningExecutorService executorService,
            @FiraParams.RangingDeviceType int type
    ) {
        this(context, executorService,
                new UwbServiceImpl(
                        context,
                        new UwbFeatureFlags.Builder()
                                .setSkipRangingCapabilitiesCheck(
                                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                                .setReversedByteOrderFiraParams(
                                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
                                .build(),
                        (isUwbAvailable, reason) -> {
                            // TODO: Implement when adding backend support.
                        }
                ),
                type);
    }

    @VisibleForTesting
    public UwbAdapter(
            @NonNull Context context, @NonNull ListeningExecutorService executorService,
            @NonNull UwbServiceImpl uwbService, @FiraParams.RangingDeviceType int type
    ) {
        if (!UwbAdapter.isSupported(context)) {
            throw new IllegalArgumentException("UWB system feature not found.");
        }

        mStateMachine = new StateMachine<>(State.STOPPED);
        mUwbService = uwbService;
        mUwbClient = type == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER
                ? mUwbService.getController(context)
                : mUwbService.getControlee(context);
        mExecutorService = executorService;
        mCallbacks = null;
    }

    @Override
    public RangingTechnology getType() {
        return RangingTechnology.UWB;
    }

    @Override
    public ListenableFuture<Boolean> isEnabled() {
        return Futures.immediateFuture(mUwbService.isAvailable());
    }

    @Override
    public void start(@NonNull RangingConfig.TechnologyConfig config, @NonNull Callback callbacks) {
        Log.i(TAG, "Start called.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            return;
        }

        mCallbacks = callbacks;
        if (!(config instanceof UwbConfig uwbConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            mCallbacks.onStopped(Callback.StoppedReason.FAILED_TO_START);
            return;
        }
        mUwbClient.setRangingParameters(uwbConfig.asBackendParameters());
        mUwbClient.setLocalAddress(toBackend(uwbConfig.getParameters().getDeviceAddress()));

        var future = Futures.submit(() -> {
            mUwbClient.startRanging(mUwbListener, Executors.newSingleThreadExecutor());
        }, mExecutorService);
        Futures.addCallback(future, mUwbClientResultHandlers.startRanging, mExecutorService);
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");
        if (!mStateMachine.transition(State.STARTED, State.STOPPED)) {
            Log.v(TAG, "Attempted to stop adapter when it was already stopped");
            return;
        }

        var future = Futures.submit(mUwbClient::stopRanging, mExecutorService);
        Futures.addCallback(future, mUwbClientResultHandlers.stopRanging, mExecutorService);
    }

    public @NonNull UwbAddress getLocalAddress() {
        return UwbAddress.fromBytes(mUwbClient.getLocalAddress().toBytes());
    }

    public @Nullable UwbComplexChannel getComplexChannel() {
        if (!(mUwbClient instanceof RangingController controller)) {
            return null;
        }
        com.android.ranging.uwb.backend.internal.UwbComplexChannel complexChannel =
                controller.getComplexChannel();
        return new UwbComplexChannel(
                complexChannel.getChannel(), complexChannel.getPreambleIndex());
    }

    public ListenableFuture<RangingCapabilities> getCapabilities() throws RemoteException {
        return Futures.submit(mUwbService::getRangingCapabilities, mExecutorService);
    }

    private class UwbListener implements RangingSessionCallback {

        @Override
        public void onRangingInitialized(UwbDevice device) {
            Log.i(TAG, "onRangingInitialized");
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STARTED) {
                    mCallbacks.onStarted();
                }
            }
        }

        @Override
        public void onRangingResult(UwbDevice device, RangingPosition position) {
            RangingData.Builder dataBuilder = new RangingData.Builder()
                    .setTechnology(RangingTechnology.UWB)
                    .setRangeDistance(position.getDistance().getValue())
                    .setRssi(position.getRssiDbm())
                    .setTimestamp(Duration.ofNanos(position.getElapsedRealtimeNanos()))
                    .setPeerAddress(device.getAddress().toBytes());

            if (position.getAzimuth() != null) {
                dataBuilder.setAzimuthRadians(position.getAzimuth().getValue());
            }
            if (position.getElevation() != null) {
                dataBuilder.setElevationRadians(position.getElevation().getValue());
            }
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STARTED) {
                    mCallbacks.onRangingData(dataBuilder.build());
                }
            }
        }

        private static @Callback.StoppedReason int convertReason(
                @RangingSessionCallback.RangingSuspendedReason int reason) {
            switch (reason) {
                case REASON_WRONG_PARAMETERS:
                case REASON_FAILED_TO_START:
                    return Callback.StoppedReason.FAILED_TO_START;
                case REASON_STOPPED_BY_PEER:
                case REASON_STOP_RANGING_CALLED:
                    return Callback.StoppedReason.REQUESTED;
                case REASON_MAX_RANGING_ROUND_RETRY_REACHED:
                    return Callback.StoppedReason.LOST_CONNECTION;
                case REASON_SYSTEM_POLICY:
                    return Callback.StoppedReason.SYSTEM_POLICY;
                default:
                    return Callback.StoppedReason.UNKNOWN;
            }
        }

        @Override
        public void onRangingSuspended(UwbDevice device, @RangingSuspendedReason int reason) {
            Log.i(TAG, "onRangingSuspended: " + reason);

            synchronized (mStateMachine) {
                mCallbacks.onStopped(convertReason(reason));
                clear();
            }
        }
    }

    private void clear() {
        mCallbacks = null;
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
                // On started will be called after onRangingInitialized is invoked from
                // the UWB callback.
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.w(TAG, "startRanging failed ", t);
                mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.ERROR);
                clear();
            }
        };

        public final FutureCallback<Integer> stopRanging = new FutureCallback<>() {
            @Override
            public void onSuccess(@Utils.UwbStatusCodes Integer status) {
                // On stopped will be called after onRangingSuspended is invoked from
                // the UWB callback.
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.w(TAG, "stopRanging failed ", t);
                // We failed to stop but there's nothing else we can do.
                mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.REQUESTED);
                clear();
            }
        };
    }
}
