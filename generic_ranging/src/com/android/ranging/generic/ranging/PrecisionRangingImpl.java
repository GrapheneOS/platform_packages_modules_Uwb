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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.uwb.backend.impl.internal.RangingCapabilities;
import androidx.core.uwb.backend.impl.internal.RangingParameters;
import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;

import com.android.internal.annotations.GuardedBy;
import com.android.ranging.generic.RangingTechnology;
import com.android.sensor.Estimate;
import com.android.sensor.MultiSensorFinderListener;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.DoNotCall;

import dagger.Lazy;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/** Precision Ranging Implementation (Generic Ranging Layer). */
public final class PrecisionRangingImpl implements PrecisionRanging {

    private static final String TAG = PrecisionRangingImpl.class.getSimpleName();

    /**
     * Default frequency of the task running the periodic update when {@link
     * PrecisionRangingConfig#getMaxUpdateInterval} is set to 0.
     */
    private static final long DEFAULT_INTERNAL_UPDATE_INTERVAL_MS = 100;

    /**
     * Frequency of the task running the periodic update calculated based on what {@link
     * PrecisionRangingConfig#getMaxUpdateInterval} is set to, or default when {@link
     * PrecisionRangingConfig#getMaxUpdateInterval} is 0.
     */
    private final long periodicUpdateIntervalMs;

    private final Object lock = new Object();

    /** Keeps the internal state of precision ranging (such as starting, active or stopped). */
    @GuardedBy("lock")
    private State internalState;

    /** Keeps the state of each individual ranging adapter (such as starting, active or stopped). */
    @GuardedBy("lock")
    private final Map<RangingTechnology, State> rangingAdaptersStateMap;

    private final ImmutableMap<RangingTechnology, RangingAdapter> rangingAdapters;
    private final Map<RangingTechnology, RangingAdapter.Callback> rangingAdapterListeners;

    /**
     * Some of the ranging adapters need to be configured before being called. This list keeps track
     * of all adapters that were configured so we can report an error to the caller if any of them
     * were not.
     */
    private final EnumSet<RangingTechnology> rangingConfigurationsAdded;

    private final Context context;
    private final PrecisionRangingConfig config;
    private Optional<PrecisionRanging.Callback> callback;

    /**
     * In this instance the primary fusion algorithm is the ArCoreMultiSensorFinder algorithm. In
     * future we could create a common interface that a fusion algorithm should conform to and then
     * make this generic so the caller can choose which fusion algorithm to use.
     */
//    private Optional<ArCoreMultiSensorFinder> fusionAlgorithm;
//
//    private Optional<MultiSensorFinderListener> fusionAlgorithmListener;

    // TODO(b/331206299): Check after arcore is integrated.
    //private final TimeSource timeSource;

    /**
     * The executor where periodic updater is executed. Periodic updater updates the caller with
     * new
     * data if available and stops precision ranging if stopping conditions are met. Periodic
     * updater
     * doesn't report new data if config.getMaxUpdateInterval is 0, in that case updates happen
     * immediately after new data is received.
     */
    private final ScheduledExecutorService periodicUpdateExecutorService;

    /**
     * Executor service for running async tasks such as starting/stopping individual ranging
     * adapters
     * and fusion algorithm. Most of the results of running the tasks are received via listeners.
     */
    private final ExecutorService internalExecutorService;

    @GuardedBy("lock")
    private Optional<RangingData> lastUwbRangingDataResult;

    @GuardedBy("lock")
    private Optional<RangingData> lastCsRangingDataResult;

    @GuardedBy("lock")
    private Optional<FusionData> lastFusionDataResult;

    /**
     * Last update time is used to check if we should report new data via the callback if available.
     * It's not used as a reason to stop precision ranging, last received times are used instead for
     * that.
     */
    private Instant lastUpdateTime;

    /**
     * Start time is used to check if we're in a grace period right after starting so we don't stop
     * precision ranging before giving it a chance to start producing data.
     */
    private Instant startTime;

    /**
     * Last Range data received is used to check if precision ranging should be stopped if we didn't
     * receive any data for too long, or to check if we should stop due to "drifting" in case fusion
     * algorithm is still reporting data, but we didn't feed any ranging data into for far too long.
     */
    private Instant lastRangeDataReceivedTime;

    /**
     * Last Fusion data received time is used to check if precision ranging should be stopped if we
     * didn't receive any data for too long.
     */
    private Instant lastFusionDataReceivedTime;

    /**
     * This is used to check if stop is needed in case all ranging adapters are stopped. If we
     * didn't
     * previously receive any data from the fusion algorithm then we can stop safely since we know
     * we
     * won't be getting any useful results. Otherwise we don't stop immediately but after the drift
     * timeout period.
     */
    private boolean seenSuccessfulFusionData;

    /** Factory for creating {@link PrecisionRangingImpl}. */
    @AssistedFactory
    public interface Factory extends PrecisionRanging.Factory {
        @Override
        PrecisionRangingImpl create(PrecisionRangingConfig config);
    }

    /**
     * Constructs Precision Ranging. Additional setup might be needed depending on the ranging
     * technologies requested in the configuration.
     */
    @AssistedInject
    public PrecisionRangingImpl(
            Lazy<UwbAdapter> lazyUwbAdapter,
            Context context,
            @Assisted PrecisionRangingConfig config,
            ScheduledExecutorService scheduledExecutorService) {
        this(
                lazyUwbAdapter,
                context,
                config,
                scheduledExecutorService,
                //TimeSource.system(),
                //Optional.empty(),
                Optional.empty());
    }

    @VisibleForTesting
    public PrecisionRangingImpl(
            Lazy<UwbAdapter> lazyUwbAdapter,
            Context context,
            PrecisionRangingConfig config,
            ScheduledExecutorService scheduledExecutorService,
            //TimeSource timeSource,
            //Optional<ArCoreMultiSensorFinder> fusionAlgorithm,
            Optional<ImmutableMap<RangingTechnology, RangingAdapter>> rangingAdapters) {
        this.context = context;
        this.config = config;
        this.callback = Optional.empty();
        this.periodicUpdateExecutorService = scheduledExecutorService;
        this.internalExecutorService = scheduledExecutorService;
        //this.timeSource = timeSource;
        seenSuccessfulFusionData = false;
        rangingConfigurationsAdded = EnumSet.noneOf(RangingTechnology.class);
        rangingAdapterListeners = new HashMap<>();
        rangingAdaptersStateMap = new HashMap<>();
        lastUpdateTime = Instant.EPOCH;
        lastRangeDataReceivedTime = Instant.EPOCH;
        lastFusionDataReceivedTime = Instant.EPOCH;
        lastUwbRangingDataResult = Optional.empty();
        lastCsRangingDataResult = Optional.empty();
        lastFusionDataResult = Optional.empty();
        periodicUpdateIntervalMs =
                config.getMaxUpdateInterval().isZero()
                        ? DEFAULT_INTERNAL_UPDATE_INTERVAL_MS
                        : config.getMaxUpdateInterval().toMillis();
        //this.fusionAlgorithm = fusionAlgorithm;
        if (rangingAdapters.isPresent()) {
            this.rangingAdapters = rangingAdapters.get();
        } else {
            HashMap<RangingTechnology, RangingAdapter> adapters = new HashMap<>();
            for (RangingTechnology technology : config.getRangingTechnologiesToRangeWith()) {
                switch (technology) {
                    case UWB:
                        adapters.put(technology, lazyUwbAdapter.get());
                        break;
                    case CS:
                        throw new UnsupportedOperationException("CS support not implemented.");
                }
            }
            this.rangingAdapters = ImmutableMap.copyOf(adapters);
        }
        synchronized (lock) {
            internalState = State.STOPPED;
        }
    }

    @Override
    public void start(@NonNull PrecisionRanging.Callback callback) {
        Log.i(TAG, "Start Precision Ranging called.");
        Preconditions.checkArgument(
                rangingConfigurationsAdded.containsAll(config.getRangingTechnologiesToRangeWith()),
                "Missing configuration for some ranging technologies that were requested.");
        synchronized (lock) {
            internalState = State.STARTING;
        }
        this.callback = Optional.of(callback);
        for (RangingTechnology technology : config.getRangingTechnologiesToRangeWith()) {
            synchronized (lock) {
                rangingAdaptersStateMap.put(technology, State.STARTING);
            }
            var listener = new RangingAdapterListener(technology);
            rangingAdapterListeners.put(technology, listener);
            internalExecutorService.execute(
                    () -> {
                        var adapter = rangingAdapters.get(technology);
                        if (adapter == null) {
                            Log.e(TAG,
                                    "No ranging adapter found when trying to start for "
                                            + technology);
                            return;
                        }
                        adapter.start(listener);
                    });
        }
        if (config.getUseFusingAlgorithm()) {
            internalExecutorService.execute(this::startFusingAlgorithm);
        }

        //startTime = timeSource.now();
        startTime = Instant.now();
        Log.i(TAG, "Starting periodic update. Start time: " + startTime);
        var unused =
                periodicUpdateExecutorService.scheduleWithFixedDelay(
                        this::performPeriodicUpdate, 0, periodicUpdateIntervalMs, MILLISECONDS);
    }

    /* Initiates and starts fusion algorithm. */
    private void startFusingAlgorithm() {
        Log.i(TAG, "Starting fusion algorithm.");
//        if (fusionAlgorithm.isEmpty()) {
//            fusionAlgorithm =
//                    Optional.of(
//                            new ArCoreMultiSensorFinder(
//                                    Sleeper.defaultSleeper(), timeSource, config
//                                    .getFusionAlgorithmConfig().get()));
//        }
//        fusionAlgorithmListener = Optional.of(new FusionAlgorithmListener());
//        fusionAlgorithm.get().subscribeToEstimates(fusionAlgorithmListener.get());
//        var result = fusionAlgorithm.get().start(context);
//        if (result != Status.OK) {
//            Log.w(TAG,"Fusion algorithm start failed: %s", result);
//            return;
//        }
    }

    /*
     * Periodic updater reports new data via the callback and stops precision ranging if
     * stopping conditions are met.
     */
    private void performPeriodicUpdate() {
        synchronized (lock) {
            if (internalState == State.STOPPED) {
                return;
            }
        }
        reportNewDataIfAvailable();
        checkAndStopIfNeeded();
    }

    /* Reports new data if available via the callback. */
    private void reportNewDataIfAvailable() {
        synchronized (lock) {
            if (internalState == State.STOPPED) {
                return;
            }
        }
        // Skip update if it's set to immediate updating (updateInterval == 0), or if not enough
        // time
        // has passed since last update.
        //Instant currentTime = timeSource.now();
        Instant currentTime = Instant.now();
        if (config.getMaxUpdateInterval().isZero()
                || currentTime.isBefore(lastUpdateTime.plus(config.getMaxUpdateInterval()))) {
            return;
        }
        // Skip update if there's no new data to report
        synchronized (lock) {
            if (lastUwbRangingDataResult.isEmpty()
                    && lastCsRangingDataResult.isEmpty()
                    && lastFusionDataResult.isEmpty()) {
                return;
            }
        }

        PrecisionData.Builder precisionDataBuilder = PrecisionData.builder();
        synchronized (lock) {
            ImmutableList.Builder<RangingData> rangingDataBuilder = ImmutableList.builder();
            lastUwbRangingDataResult.ifPresent(rangingDataBuilder::add);
            lastCsRangingDataResult.ifPresent(rangingDataBuilder::add);
            var rangingData = rangingDataBuilder.build();

            if (!rangingData.isEmpty()) {
                precisionDataBuilder.setRangingData(rangingData);
            }
            lastFusionDataResult.ifPresent(precisionDataBuilder::setFusionData);

            lastUwbRangingDataResult = Optional.empty();
            lastCsRangingDataResult = Optional.empty();
            lastFusionDataResult = Optional.empty();
        }
        //lastUpdateTime = timeSource.now();
        lastUpdateTime = Instant.now();
        precisionDataBuilder.setTimestamp(lastUpdateTime.toEpochMilli());
        PrecisionData precisionData = precisionDataBuilder.build();
        synchronized (lock) {
            if (internalState == State.STOPPED) {
                return;
            }
            callback.get().onData(precisionData);
        }
    }

    /* Checks if stopping conditions are met and if so, stops precision ranging. */
    private void checkAndStopIfNeeded() {
        boolean noActiveRanging;
        synchronized (lock) {
            noActiveRanging =
                    !rangingAdaptersStateMap.containsValue(State.ACTIVE)
                            && !rangingAdaptersStateMap.containsValue(State.STARTING);
        }

        // if only ranging is used and all ranging techs are stopped then stop since we won't be
        // getting
        // any new data from this point.
        if (noActiveRanging && !config.getUseFusingAlgorithm()) {
            Log.i(TAG,
                    "stopping precision ranging cause: no active ranging in progress and  not "
                            + "using fusion"
                            + " algorithm");
            stopPrecisionRanging(PrecisionRanging.Callback.StoppedReason.NO_RANGES_TIMEOUT);
            return;
        }

        // if both ranging and fusion alg used, but all ranging techs are stopped then stop if there
        // were no successful fusion alg data up to this point since fusion alg can only work if it
        // received some ranging data.
        if (noActiveRanging && config.getUseFusingAlgorithm() && !seenSuccessfulFusionData) {
            Log.i(TAG,
                    "stopping precision ranging cause: no active ranging in progress and haven't "
                            + "seen"
                            + " successful fusion data");
            stopPrecisionRanging(PrecisionRanging.Callback.StoppedReason.NO_RANGES_TIMEOUT);
            return;
        }

        // if both ranging and fusion alg used but all ranges are stopped and there is successful
        // arcore
        // data then check if drift timeout expired.
        //Instant currentTime = timeSource.now();
        Instant currentTime = Instant.now();
        if (noActiveRanging && config.getUseFusingAlgorithm() && seenSuccessfulFusionData) {
            if (currentTime.isAfter(
                    lastRangeDataReceivedTime.plus(config.getFusionAlgorithmDriftTimeout()))) {
                Log.i(TAG,
                        "stopping precision ranging cause: fusion algorithm drift timeout [" +
                                config.getFusionAlgorithmDriftTimeout().toMillis() + " ms]");
                stopPrecisionRanging(PrecisionRanging.Callback.StoppedReason.FUSION_DRIFT_TIMEOUT);
                return;
            }
        }

        // If we're still inside the init timeout don't stop precision ranging for any of the
        // reasons below this.
        if (currentTime.isBefore(startTime.plus(config.getInitTimeout()))) {
            return;
        }

        // If we didn't receive data from any source for more than the update timeout then stop.
        Instant lastReceivedDataTime =
                lastRangeDataReceivedTime.isAfter(lastFusionDataReceivedTime)
                        ? lastRangeDataReceivedTime
                        : lastFusionDataReceivedTime;
        if (currentTime.isAfter(lastReceivedDataTime.plus(config.getNoUpdateTimeout()))) {
            Log.i(TAG,
                    "stopping precision ranging cause: no update timeout [" +
                            config.getNoUpdateTimeout().toMillis() + " ms]");
            stopPrecisionRanging(PrecisionRanging.Callback.StoppedReason.NO_RANGES_TIMEOUT);
            return;
        }

        // None of the stopping conditions met, no stopping needed.
    }

    /* Feeds ranging adapter data into the fusion algorithm. */
    private void feedDataToFusionAlgorithm(RangingData rangingData) {
        switch (rangingData.getRangingTechnology()) {
            case UWB:
//                fusionAlgorithm
//                        .get()
//                        .updateWithUwbMeasurement(rangingData.getRangeDistance(), rangingData
//                        .getTimestamp());
                break;
            case CS:
                throw new UnsupportedOperationException(
                        "CS support not implemented. Can't update fusion alg.");
        }
    }

    @Override
    public void stop() {
        stopPrecisionRanging(PrecisionRanging.Callback.StoppedReason.REQUESTED);
    }

    /* Calls stop on all ranging adapters and the fusion algorithm and resets all internal states
    . */
    private void stopPrecisionRanging(@PrecisionRanging.Callback.StoppedReason int reason) {
        synchronized (lock) {
            if (internalState == State.STOPPED) {
                return;
            }
            internalState = State.STOPPED;
        }
        Log.i(TAG, "stopPrecisionRanging with reason: " + reason);
        callback.get().onStopped(reason);
        // stop all ranging techs
        for (RangingTechnology technology : config.getRangingTechnologiesToRangeWith()) {
            synchronized (lock) {
                if (rangingAdaptersStateMap.get(technology) == State.STOPPED) {
                    continue;
                }
                rangingAdaptersStateMap.put(technology, State.STOPPED);
            }
            internalExecutorService.execute(
                    () -> {
                        var adapter = rangingAdapters.get(technology);
                        if (adapter == null) {
                            Log.e(TAG,
                                    "Adapter not found for ranging technology when trying to stop: "
                                            + technology);
                            return;
                        }
                        adapter.stop();
                    });
        }
        // stop fusion algorithm
        if (config.getUseFusingAlgorithm()) {
//            internalExecutorService.execute(
//                    () -> {
//                        var status = fusionAlgorithm.get().stop();
//                        if (status != Status.OK) {
//                            Log.w(TAG,"Fusion alg stop failed: " + status);
//                        }
//                    });
        }

        // reset internal states and objects
        synchronized (lock) {
            lastUwbRangingDataResult = Optional.empty();
            lastCsRangingDataResult = Optional.empty();
            lastFusionDataResult = Optional.empty();
        }
        lastUpdateTime = Instant.EPOCH;
        lastRangeDataReceivedTime = Instant.EPOCH;
        lastFusionDataReceivedTime = Instant.EPOCH;
        rangingAdapterListeners.clear();
        rangingConfigurationsAdded.clear();
        //fusionAlgorithmListener = Optional.empty();
        callback = Optional.empty();
        seenSuccessfulFusionData = false;
    }

    @Override
    public ListenableFuture<RangingCapabilities> getUwbCapabilities() {
        if (!rangingAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) rangingAdapters.get(RangingTechnology.UWB);
        try {
            return uwbAdapter.getCapabilities();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get Uwb capabilities");
            return null;
        }
    }

    @Override
    public ListenableFuture<UwbAddress> getUwbAddress() throws RemoteException {
        if (!rangingAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) rangingAdapters.get(RangingTechnology.UWB);
        return uwbAdapter.getLocalAddress();
    }

    @Override
    public ListenableFuture<UwbComplexChannel> getUwbComplexChannel() throws RemoteException {
        if (!rangingAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) rangingAdapters.get(RangingTechnology.UWB);
        return uwbAdapter.getComplexChannel();
    }

    @Override
    public void setUwbConfig(RangingParameters rangingParameters) {
        if (config.getRangingTechnologiesToRangeWith().contains(RangingTechnology.UWB)) {
            UwbAdapter uwbAdapter = (UwbAdapter) rangingAdapters.get(RangingTechnology.UWB);
            if (uwbAdapter == null) {
                Log.e(TAG,
                        "UWB adapter not found when setting config even though it was requested.");
                return;
            }
            uwbAdapter.setRangingParameters(rangingParameters);
        }
        rangingConfigurationsAdded.add(RangingTechnology.UWB);
    }

    @DoNotCall("Not implemented")
    @Override
    public void getCsCapabilities() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Sets CS configuration. */
    @DoNotCall("Not implemented")
    @Override
    public void setCsConfig() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public ListenableFuture<ImmutableMap<RangingTechnology, Integer>>
    rangingTechnologiesAvailability() throws RemoteException {

        List<ListenableFuture<Boolean>> enabledFutures = new ArrayList<>();
        for (RangingTechnology technology : config.getRangingTechnologiesToRangeWith()) {
            var adapter = rangingAdapters.get(technology);
            if (adapter == null) {
                return immediateFailedFuture(
                        new IllegalStateException(
                                "Adapter not found for ranging technology: " + technology));
            }
            enabledFutures.add(adapter.isEnabled());
        }
        var f = Futures.allAsList(enabledFutures);
        return Futures.transform(
                f,
                (List<Boolean> enabledList) -> {
                    ImmutableMap.Builder<RangingTechnology, Integer>
                            rangingTechnologiesAvailability =
                            ImmutableMap.builder();
                    for (int i = 0; i < config.getRangingTechnologiesToRangeWith().size(); i++) {
                        var tech = config.getRangingTechnologiesToRangeWith().get(i);
                        var adapter = rangingAdapters.get(tech);
                        if (adapter == null) {
                            Log.e(TAG, "Adapter not found for ranging technology: " + tech);
                            rangingTechnologiesAvailability.put(
                                    tech, RangingTechnologyAvailability.NOT_SUPPORTED);
                        } else if (!adapter.isPresent()) {
                            rangingTechnologiesAvailability.put(
                                    tech, RangingTechnologyAvailability.NOT_SUPPORTED);
                        } else if (!enabledList.get(i)) {
                            rangingTechnologiesAvailability.put(tech,
                                    RangingTechnologyAvailability.DISABLED);
                        } else {
                            rangingTechnologiesAvailability.put(tech,
                                    RangingTechnologyAvailability.ENABLED);
                        }
                    }
                    return rangingTechnologiesAvailability.buildOrThrow();
                },
                internalExecutorService);
    }

    @VisibleForTesting
    public Map<RangingTechnology, RangingAdapter.Callback> getRangingAdapterListeners() {
        return rangingAdapterListeners;
    }

//    @VisibleForTesting
//    public Optional<MultiSensorFinderListener> getFusionAlgorithmListener() {
//        return fusionAlgorithmListener;
//    }

    /* Listener implementation for ranging adapter callback. */
    private class RangingAdapterListener implements RangingAdapter.Callback {
        private final RangingTechnology technology;

        public RangingAdapterListener(RangingTechnology technology) {
            this.technology = technology;
        }

        @Override
        public void onStarted() {
            synchronized (lock) {
                if (internalState == State.STOPPED) {
                    return;
                }
                if (internalState == State.STARTING) {
                    internalState = State.ACTIVE;
                    // call started as soon as at least one ranging tech starts or fusion alg
                    // estimate
                    // received.
                    callback.get().onStarted();
                }
                rangingAdaptersStateMap.put(technology, State.ACTIVE);
            }
        }

        @Override
        public void onStopped(RangingAdapter.Callback.StoppedReason reason) {
            synchronized (lock) {
                if (internalState == State.STOPPED) {
                    return;
                }
                rangingAdaptersStateMap.put(technology, State.STOPPED);
            }
        }

        @Override
        public void onRangingData(RangingData rangingData) {
            synchronized (lock) {
                if (internalState == State.STOPPED) {
                    return;
                }
            }
            //lastRangeDataReceivedTime = timeSource.now();
            lastRangeDataReceivedTime = Instant.now();
            feedDataToFusionAlgorithm(rangingData);
            if (config.getMaxUpdateInterval().isZero()) {
                PrecisionData precisionData =
                        PrecisionData.builder()
                                .setRangingData(ImmutableList.of(rangingData))
                                .setTimestamp(Instant.now().toEpochMilli())
                                .build();
                synchronized (lock) {
                    if (internalState == State.STOPPED) {
                        return;
                    }
                    callback.get().onData(precisionData);
                }
            }
            switch (rangingData.getRangingTechnology()) {
                case UWB:
                    synchronized (lock) {
                        lastUwbRangingDataResult = Optional.of(rangingData);
                    }
                    break;
                case CS:
                    throw new UnsupportedOperationException("CS support not implemented.");
            }
        }
    }

    /* Listener implementation for fusion algorithm callback. */
    private class FusionAlgorithmListener implements MultiSensorFinderListener {
        @Override
        public void onUpdatedEstimate(Estimate estimate) {
            synchronized (lock) {
                if (internalState == State.STOPPED) {
                    return;
                }
                if (internalState == State.STARTING) {
                    internalState = State.ACTIVE;
                    // call started as soon as at least one ranging tech starts or fusion alg
                    //estimate received.
                    callback.get().onStarted();
                }
            }
            FusionData fusionData = FusionData.fromFusionAlgorithmEstimate(estimate);
            if (fusionData.getArCoreState() == FusionData.ArCoreState.OK) {
                lastFusionDataReceivedTime = Instant.now();
                seenSuccessfulFusionData = true;
            }
            synchronized (lock) {
                lastFusionDataResult = Optional.of(fusionData);
            }
            if (config.getMaxUpdateInterval().isZero()) {
                PrecisionData precisionData =
                        PrecisionData.builder()
                                .setFusionData(fusionData)
                                .setTimestamp(Instant.now().toEpochMilli())
                                .build();
                synchronized (lock) {
                    if (internalState == State.STOPPED) {
                        return;
                    }
                    callback.get().onData(precisionData);
                }
            }
        }
    }

    /* Internal states. */
    private enum State {
        STARTING,
        ACTIVE,
        STOPPED,
    }
}