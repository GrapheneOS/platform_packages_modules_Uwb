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

package com.android.ranging.adapter;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.content.Context;
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
import androidx.core.uwb.backend.impl.internal.UwbAvailabilityCallback;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbDevice;
import androidx.core.uwb.backend.impl.internal.UwbFeatureFlags;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import com.android.ranging.RangingData;
import com.android.ranging.RangingTechnology;

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

    /** Invariant: non-null while a ranging session is active */
    private Callback mCallbacks;
    private RangingParameters mRangingParameters;

    /** @return true if UWB is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature("android.hardware.uwb");
    }

    public UwbAdapter(
            @NonNull Context context, @NonNull ListeningExecutorService executorService,
            @NonNull DeviceType deviceType
    ) {
        if (!UwbAdapter.isSupported(context)) {
            throw new IllegalArgumentException("UWB system feature not found.");
        }

        UwbFeatureFlags uwbFeatureFlags = new UwbFeatureFlags.Builder()
                .setSkipRangingCapabilitiesCheck(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                .setReversedByteOrderFiraParams(
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
                .build();
        UwbAvailabilityCallback uwbAvailabilityCallback = (isUwbAvailable, reason) -> {
            // TODO: Implement when adding backend support.
        };
        mUwbService = new UwbServiceImpl(context, uwbFeatureFlags, uwbAvailabilityCallback);
        mUwbClient = deviceType == DeviceType.CONTROLLER
                ? mUwbService.getController(context)
                : mUwbService.getControlee(context);
        mExecutorService = executorService;
        mCallbacks = null;
        mRangingParameters = null;
    }

    @Override
    public RangingTechnology getType() {
        return RangingTechnology.UWB;
    }

    @Override
    public ListenableFuture<Boolean> isEnabled() throws RemoteException {
        return Futures.submit(mUwbService::isAvailable, mExecutorService);
    }

    @Override
    public void start(Callback callbacks) {
        Log.i(TAG, "Start called.");

        mCallbacks = callbacks;
        if (mRangingParameters == null) {
            Log.w(TAG, "Tried to start adapter but no ranging parameters have been provided");
            mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.NO_PARAMS);
            return;
        }
        mUwbClient.setRangingParameters(mRangingParameters);

        var future = Futures.submit(() -> {
            mUwbClient.startRanging(mUwbListener, Executors.newSingleThreadExecutor());
        }, mExecutorService);
        Futures.addCallback(future, mUwbClientResultHandlers.startRanging, mExecutorService);
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");

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

    @VisibleForTesting
    public void setLocalAddress(@NonNull UwbAddress uwbAddress) {
        mUwbClient.setLocalAddress(uwbAddress);
    }

    public ListenableFuture<RangingCapabilities> getCapabilities() throws RemoteException {
        return Futures.submit(mUwbService::getRangingCapabilities, mExecutorService);
    }

    /**
     * Set the parameters for the UWB session. This must be called before starting the session.
     * @param params for UWB session configuration.
     */
    public void setRangingParameters(@NonNull RangingParameters params) {
        mRangingParameters = params;
    }

    private class UwbListener implements RangingSessionCallback {
        @Override
        public void onRangingInitialized(UwbDevice device) {
            Log.i(TAG, "onRangingInitialized");
            mCallbacks.onStarted();
        }

        @Override
        public void onRangingResult(UwbDevice device, RangingPosition position) {
            RangingData.Builder rangingDataBuilder =
                    new RangingData.Builder()
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

        @Override
        public void onRangingSuspended(UwbDevice device, @RangingSuspendedReason int reason) {
            Log.i(TAG, "onRangingSuspended: " + reason);

            if (reason == RangingSessionCallback.REASON_STOP_RANGING_CALLED) {
                mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.REQUESTED);
            } else {
                mCallbacks.onStopped(RangingAdapter.Callback.StoppedReason.ERROR);
            }
            clear();
        }
    }

    @VisibleForTesting
    public void setComplexChannelForTesting() {
        if (mUwbClient instanceof RangingController) {
            mUwbClient.setForTesting(true);
        }
    }

    private void clear() {
        mRangingParameters = null;
        mCallbacks = null;
    }

    @VisibleForTesting
    public RangingSessionCallback getListener() {
        return mUwbListener;
    }

    public enum DeviceType {
        CONTROLEE,
        CONTROLLER,
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
