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

import android.content.Context;
import com.google.android.apps.common.inject.annotation.ApplicationContext;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.uwb.RangingCapabilities;
import com.google.android.gms.nearby.uwb.RangingParameters;
import com.google.android.gms.nearby.uwb.RangingPosition;
import com.google.android.gms.nearby.uwb.RangingSessionCallback;
import com.google.android.gms.nearby.uwb.UwbAddress;
import com.google.android.gms.nearby.uwb.UwbClient;
import com.google.android.gms.nearby.uwb.UwbDevice;
import com.google.android.libraries.gmstasks.TaskFutures;
import com.google.android.libraries.precisionfinding.RangingTechnology;
import com.google.android.libraries.spot.concurrent.Executors.LightweightExecutor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.flogger.GoogleLogger;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.util.Optional;
import javax.inject.Inject;

/** Ranging Adapter for Ultra-Wide Band (UWB). */
class UwbAdapter implements RangingAdapter {

    private static final GoogleLogger logger = GoogleLogger.forEnclosingClass();

    private final Optional<UwbClient> uwbClient;
    private Optional<RangingSessionCallback> uwbListener;
    private Optional<RangingParameters> rangingParameters;
    private Optional<Callback> callback;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private UwbAdapterState internalState;

    private final ListeningExecutorService executorService;

    @Inject
    UwbAdapter(
            @ApplicationContext Context context,
            @LightweightExecutor ListeningExecutorService executorServices) {
        this.uwbClient =
                context.getPackageManager().hasSystemFeature("android.hardware.uwb")
                        ? Optional.of(Nearby.getUwbControleeClient(context))
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
            Optional<UwbClient> uwbClient,
            @LightweightExecutor ListeningExecutorService executorService) {
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

    @Override
    public ListenableFuture<Boolean> isEnabled() {
        if (uwbClient.isEmpty()) {
            return immediateFuture(false);
        }
        return TaskFutures.toListenableFuture(uwbClient.get().isAvailable());
    }

    @Override
    public void start(Callback callback) {
        logger.atInfo().log("Start UwbAdapter called.");
        if (uwbClient.isEmpty()) {
            callback.onStopped(RangingAdapter.Callback.StoppedReason.ERROR);
            clear();
            return;
        }
        synchronized (lock) {
            if (internalState != UwbAdapterState.STOPPED) {
                logger.atWarning().log("Tried to start UWB while it is not in stopped state");
                return;
            }
            internalState = UwbAdapterState.STARTING;
        }
        this.callback = Optional.of(callback);
        startRanging(new UwbListener());
    }

    @Override
    public void stop() {
        logger.atInfo().log("Stop UwbAdapter API called.");
        if (uwbClient.isEmpty()) {
            logger.atWarning().log("Tried to stop UWB but it is not available.");
            clear();
            return;
        }
        synchronized (lock) {
            if (internalState == UwbAdapterState.STOPPED) {
                logger.atWarning().log("Tried to stop UWB while it is already in stopped state");
                return;
            }
        }
        stopRanging();
    }

    ListenableFuture<UwbAddress> getLocalAddress() {
        if (uwbClient.isEmpty()) {
            clear();

            return immediateFailedFuture(new IllegalStateException("UWB is not available."));
        }
        return TaskFutures.toListenableFuture(uwbClient.get().getLocalAddress());
    }

    ListenableFuture<RangingCapabilities> getCapabilities() {
        if (uwbClient.isEmpty()) {
            clear();
            return immediateFailedFuture(new IllegalStateException("UWB is not available."));
        }
        return TaskFutures.toListenableFuture(uwbClient.get().getRangingCapabilities());
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
        var future =
                TaskFutures.toListenableFuture(
                        uwbClient.get().startRanging(this.rangingParameters.get(), uwbListener));
        Futures.addCallback(
                future,
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        logger.atInfo().log("UWB startRanging call succeeded.");
                        // On started will be called after onRangingInitialized is invoked from
                        // the UWB callback.
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        logger.atWarning().withCause(t).log("Failed UWB startRanging call.");
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
        logger.atInfo().log("UwbAdapter stopRanging.");
        var future =
                TaskFutures.toListenableFuture(uwbClient.get().stopRanging(this.uwbListener.get()));
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
                        logger.atWarning().withCause(t).log("Failed UWB stopRanging call.");
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

        public UwbListener() {}

        @Override
        public void onRangingInitialized(UwbDevice device) {
            logger.atInfo().log("onRangingInitialized");
            synchronized (lock) {
                if (internalState != UwbAdapterState.STARTING) {
                    logger.atSevere().log("Uwb initialized but wasn't in STARTING state.");
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
                    logger.atSevere().log(
                            "onRangingResult callback received but UwbAdapter not in STARTED state.");
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
            logger.atInfo().log("onRangingSuspended: %d", reason);
            synchronized (lock) {
                if (internalState == UwbAdapterState.STOPPED) {
                    logger.atSevere().log(
                            "onRangingSuspended callback received but UwbAdapter was in STOPPED state.");
                    return;
                }
                internalState = UwbAdapterState.STOPPED;
            }
            if (reason == RangingSuspendedReason.STOP_RANGING_CALLED) {
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
