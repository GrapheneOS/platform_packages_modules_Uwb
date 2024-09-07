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

package com.android.ranging.uwb;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.uwb.backend.impl.internal.RangingCapabilities;
import androidx.core.uwb.backend.impl.internal.RangingController;
import androidx.core.uwb.backend.impl.internal.RangingDevice;
import androidx.core.uwb.backend.impl.internal.RangingParameters;
import androidx.core.uwb.backend.impl.internal.RangingPosition;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.Utils;
import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbDevice;
import androidx.core.uwb.backend.impl.internal.UwbFeatureFlags;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import com.android.ranging.RangingAdapter;
import com.android.ranging.RangingParameters.DeviceRole;
import com.android.ranging.RangingParameters.TechnologyParameters;
import com.android.ranging.RangingReport;
import com.android.ranging.RangingTechnology;
import com.android.ranging.RangingUtils.StateMachine;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

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
            @NonNull DeviceRole role
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
                role);
    }

    @VisibleForTesting
    public UwbAdapter(
            @NonNull Context context, @NonNull ListeningExecutorService executorService,
            @NonNull UwbServiceImpl uwbService, @NonNull DeviceRole role
    ) {
        if (!UwbAdapter.isSupported(context)) {
            throw new IllegalArgumentException("UWB system feature not found.");
        }

        mStateMachine = new StateMachine<>(State.STOPPED);
        mUwbService = uwbService;
        mUwbClient = role == DeviceRole.CONTROLLER
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
    public void start(@NonNull TechnologyParameters parameters, @NonNull Callback callbacks) {
        Log.i(TAG, "Start called.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            return;
        }

        mCallbacks = callbacks;
        if (!(parameters instanceof RangingParameters)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            mCallbacks.onStopped(Callback.StoppedReason.FAILED_TO_START);
            return;
        }
        mUwbClient.setRangingParameters((RangingParameters) parameters);

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

    public ListenableFuture<UwbAddress> getLocalAddress() {
        return Futures.submit(() -> mUwbClient.getLocalAddress(), mExecutorService);
    }

    public ListenableFuture<UwbComplexChannel> getComplexChannel() {
        if (!(mUwbClient instanceof RangingController)) {
            return immediateFuture(null);
        }
        return Futures.submit(() -> ((RangingController) mUwbClient).getComplexChannel(),
                mExecutorService);
    }

    public ListenableFuture<RangingCapabilities> getCapabilities() throws RemoteException {
        return Futures.submit(mUwbService::getRangingCapabilities, mExecutorService);
    }

    private class UwbListener implements RangingSessionCallback {

        @Override
        public void onRangingInitialized(UwbDevice device) {
            Log.i(TAG, "onRangingInitialized");
            mCallbacks.onStarted();
        }

        @Override
        public void onRangingResult(UwbDevice device, RangingPosition position) {
            RangingReport.Builder rangingDataBuilder =
                    new RangingReport.Builder()
                            .setRangingTechnology(RangingTechnology.UWB)
                            .setRangeDistance(position.getDistance().getValue())
                            .setRssi(position.getRssiDbm())
                            .setTimestamp(position.getElapsedRealtimeNanos())
                            .setPeerAddress(device.getAddress().toBytes());

            if (position.getAzimuth() != null) {
                rangingDataBuilder.setAzimuth(position.getAzimuth().getValue());
            }
            if (position.getElevation() != null) {
                rangingDataBuilder.setElevation(position.getElevation().getValue());
            }
            mCallbacks.onRangingData(rangingDataBuilder.build());
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

            mCallbacks.onStopped(convertReason(reason));
            clear();
        }
    }

    @VisibleForTesting
    public void setComplexChannelForTesting() {
        if (mUwbClient instanceof RangingController) {
            mUwbClient.setForTesting(true);
        }
    }

    @VisibleForTesting
    public void setLocalAddressForTesting(@NonNull UwbAddress uwbAddress) {
        mUwbClient.setLocalAddress(uwbAddress);
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
