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

package com.android.ranging.generic.ranging;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.uwb.backend.IUwb;
import androidx.core.uwb.backend.impl.internal.RangingCapabilities;
import androidx.core.uwb.backend.impl.internal.RangingDevice;
import androidx.core.uwb.backend.impl.internal.RangingParameters;
import androidx.core.uwb.backend.impl.internal.RangingPosition;
import androidx.core.uwb.backend.impl.internal.RangingSessionCallback;
import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbAvailabilityCallback;
import androidx.core.uwb.backend.impl.internal.UwbDevice;
import androidx.core.uwb.backend.impl.internal.UwbFeatureFlags;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import com.android.internal.annotations.GuardedBy;
import com.android.ranging.generic.RangingTechnology;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Optional;

/** Ranging Adapter for Ultra-Wide Band (UWB). */
class UwbAdapter implements RangingAdapter {

    public static String TAG = UwbAdapter.class.getSimpleName();

    public IUwb mIUwb;
    private UwbServiceImpl mUwbService;

    private final Optional<RangingDevice> uwbClient;
    private Optional<RangingSessionCallback> uwbListener;
    private Optional<RangingParameters> rangingParameters;
    private Optional<Callback> callback;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private UwbAdapterState internalState;

    private final ListeningExecutorService executorService;

    public UwbAdapter(Context context, ListeningExecutorService executorServices)
            throws RemoteException {

        UwbFeatureFlags uwbFeatureFlags = new UwbFeatureFlags.Builder()
                .setSkipRangingCapabilitiesCheck(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                .setReversedByteOrderFiraParams(
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
                .build();
        UwbAvailabilityCallback uwbAvailabilityCallback = (isUwbAvailable, reason) -> {
            // TODO: Implement when adding backend support.
        };
        mUwbService = new UwbServiceImpl(context, uwbFeatureFlags, uwbAvailabilityCallback);
        //TODO(b/331206299): Add support to pick controller or controlee.
        this.uwbClient =
                context.getPackageManager().hasSystemFeature("android.hardware.uwb")
                        ? Optional.of(mUwbService.getControlee(context))
                        : Optional.empty();
        this.rangingParameters = Optional.empty();
        this.callback = Optional.empty();
        this.uwbListener = Optional.empty();
        this.executorService = executorServices;
        synchronized (lock) {
            internalState = UwbAdapterState.STOPPED;
        }
    }

    @VisibleForTesting
    public UwbAdapter(
            Optional<RangingDevice> uwbClient,
            ListeningExecutorService executorService) {
        this.uwbClient = uwbClient;
        this.rangingParameters = Optional.empty();
        this.callback = Optional.empty();
        this.uwbListener = Optional.empty();
        synchronized (lock) {
            internalState = UwbAdapterState.STOPPED;
        }
        this.executorService = executorService;
    }

    @Override
    public RangingTechnology getType() {
        return RangingTechnology.UWB;
    }

    @Override
    public boolean isPresent() {
        return uwbClient.isPresent();
    }

    @SuppressLint("CheckResult")
    @Override
    public ListenableFuture<Boolean> isEnabled() throws RemoteException {
        if (uwbClient.isEmpty()) {
            return immediateFuture(false);
        }
        return Futures.submit(() -> {
            return mUwbService.isAvailable();
        }, executorService);
    }

    @Override
    public void start(Callback callback) {
        Log.i(TAG, "Start UwbAdapter called.");
        if (uwbClient.isEmpty()) {
            callback.onStopped(RangingAdapter.Callback.StoppedReason.ERROR);
            clear();
            return;
        }
        synchronized (lock) {
            if (internalState != UwbAdapterState.STOPPED) {
                Log.w(TAG, "Tried to start UWB while it is not in stopped state");
                return;
            }
            internalState = UwbAdapterState.STARTING;
        }
        this.callback = Optional.of(callback);
        startRanging(new UwbListener());
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop UwbAdapter API called.");
        if (uwbClient.isEmpty()) {
            Log.w(TAG, "Tried to stop UWB but it is not available.");
            clear();
            return;
        }
        synchronized (lock) {
            if (internalState == UwbAdapterState.STOPPED) {
                Log.w(TAG, "Tried to stop UWB while it is already in stopped state");
                return;
            }
        }
        stopRanging();
    }

    ListenableFuture<UwbAddress> getLocalAddress() throws RemoteException {
        if (uwbClient.isEmpty()) {
            clear();

            return immediateFailedFuture(new IllegalStateException("UWB is not available."));
        }
        return Futures.submit(() -> {
            return uwbClient.get().getLocalAddress();
        }, executorService);
    }

    ListenableFuture<RangingCapabilities> getCapabilities() throws RemoteException {
        if (uwbClient.isEmpty()) {
            clear();
            return immediateFailedFuture(new IllegalStateException("UWB is not available."));
        }
        return Futures.submit(() -> {
            return mUwbService.getRangingCapabilities();
        }, executorService);

    }

    void setRangingParameters(RangingParameters params) {
        rangingParameters = Optional.of(params);
    }

    private void startRanging(RangingSessionCallback uwbListener) {
        if (rangingParameters.isEmpty()) {
            callback.get().onStopped(RangingAdapter.Callback.StoppedReason.NO_PARAMS);
            return;
        }
        this.uwbListener = Optional.of(uwbListener);
        uwbClient.get().setRangingParameters(this.rangingParameters.get());
        var future = Futures.submit(() -> {
            uwbClient.get().startRanging(uwbListener, executorService);
        }, executorService);
        Futures.addCallback(
                future,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.i(TAG, "UWB startRanging call succeeded.");
                        // On started will be called after onRangingInitialized is invoked from
                        // the UWB callback.
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.w(TAG, "Failed UWB startRanging call.", t);
                        callback.get().onStopped(RangingAdapter.Callback.StoppedReason.ERROR);
                        synchronized (lock) {
                            internalState = UwbAdapterState.STOPPED;
                        }
                        clear();
                    }
                },
                executorService);
    }

    private void stopRanging() {
        Log.i(TAG, "UwbAdapter stopRanging.");
        var future =
                Futures.submit(() -> {
                    uwbClient.get().stopRanging();
                }, executorService);
        Futures.addCallback(
                future,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // On stopped will be called after onRangingSuspended is invoked from
                        // the UWB callback.
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.w(TAG, "Failed UWB stopRanging call.", t);
                        // We failed to stop but there's nothing else we can do.
                        callback.get().onStopped(RangingAdapter.Callback.StoppedReason.REQUESTED);
                        synchronized (lock) {
                            internalState = UwbAdapterState.STOPPED;
                        }
                        clear();
                    }
                },
                executorService);
    }

    private class UwbListener implements RangingSessionCallback {

        public UwbListener() {
        }

        @Override
        public void onRangingInitialized(UwbDevice device) {
            Log.i(TAG, "onRangingInitialized");
            synchronized (lock) {
                if (internalState != UwbAdapterState.STARTING) {
                    Log.e(TAG, "Uwb initialized but wasn't in STARTING state.");
                    return;
                }
                internalState = UwbAdapterState.STARTED;
            }
            callback.get().onStarted();
        }

        @Override
        public void onRangingResult(UwbDevice device, RangingPosition position) {
            synchronized (lock) {
                if (internalState != UwbAdapterState.STARTED) {
                    Log.e(TAG,
                            "onRangingResult callback received but UwbAdapter not in STARTED "
                                    + "state.");
                    return;
                }
            }

            RangingData rangingData =
                    RangingData.builder()
                            .setRangingTechnology(RangingTechnology.UWB)
                            .setRangeDistance(position.getDistance().getValue())
                            .setRssi(position.getRssiDbm())
                            .setTimestamp(position.getElapsedRealtimeNanos())
                            .build();
            callback.get().onRangingData(rangingData);
        }

        @Override
        public void onRangingSuspended(UwbDevice device, @RangingSuspendedReason int reason) {
            Log.i(TAG, "onRangingSuspended: " + reason);
            synchronized (lock) {
                if (internalState == UwbAdapterState.STOPPED) {
                    Log.e(TAG,
                            "onRangingSuspended callback received but UwbAdapter was in STOPPED "
                                    + "state.");
                    return;
                }
                internalState = UwbAdapterState.STOPPED;
            }
            if (reason == RangingSessionCallback.REASON_STOP_RANGING_CALLED) {
                callback.get().onStopped(RangingAdapter.Callback.StoppedReason.REQUESTED);
            } else {
                callback.get().onStopped(RangingAdapter.Callback.StoppedReason.ERROR);
            }
            clear();
        }
    }

    private void clear() {
        synchronized (lock) {
            Preconditions.checkState(
                    internalState == UwbAdapterState.STOPPED,
                    "Tried to clear object state while internalState != STOPPED");
        }
        this.uwbListener = Optional.empty();
        this.rangingParameters = Optional.empty();
        this.callback = Optional.empty();
    }

    @VisibleForTesting
    public RangingSessionCallback getListener() {
        return this.uwbListener.get();
    }

    private enum UwbAdapterState {
        STOPPED,
        STARTING,
        STARTED,
    }
}
