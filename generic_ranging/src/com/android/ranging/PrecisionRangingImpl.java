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

package com.android.ranging;

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
import com.android.ranging.RangingUtils.StateMachine;
import com.android.ranging.adapter.CsAdapter;
import com.android.ranging.adapter.RangingAdapter;
import com.android.ranging.adapter.UwbAdapter;
import com.android.sensor.Estimate;
import com.android.sensor.MultiSensorFinderListener;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.DoNotCall;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

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

    private final Map<RangingTechnology, RangingAdapter> mAdapters;
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

    /** Keeps track of state of the ranging session */
    private final StateMachine<State> mStateMachine;

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
     * adapters and fusion algorithm. Most of the results of running the tasks are received via
     * listeners.
     */
    private final ListeningExecutorService mAdapterExecutor;

    /** Last data received from each ranging technology */
    @GuardedBy("mStateMachine")
    private final Map<RangingTechnology, RangingData> mLastRangingData;

    @GuardedBy("mStateMachine")
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

    public PrecisionRangingImpl(
            Context context,
            PrecisionRangingConfig config,
            @NonNull ScheduledExecutorService periodicUpdateExecutor,
            @NonNull ListeningExecutorService rangingAdapterExecutor
            //TimeSource timeSource,
            //Optional<ArCoreMultiSensorFinder> fusionAlgorithm,
    ) {
        this.context = context;
        this.config = config;
        mStateMachine = new StateMachine<>(State.STOPPED);
        this.callback = Optional.empty();
        this.periodicUpdateExecutorService = periodicUpdateExecutor;
        mAdapterExecutor = rangingAdapterExecutor;
        //this.timeSource = timeSource;
        seenSuccessfulFusionData = false;
        rangingConfigurationsAdded = EnumSet.noneOf(RangingTechnology.class);
        mAdapters = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));
        rangingAdapterListeners = new HashMap<>();
        lastUpdateTime = Instant.EPOCH;
        lastRangeDataReceivedTime = Instant.EPOCH;
        lastFusionDataReceivedTime = Instant.EPOCH;
        mLastRangingData = new EnumMap<>(RangingTechnology.class);
        lastFusionDataResult = Optional.empty();
        periodicUpdateIntervalMs =
                config.getMaxUpdateInterval().isZero()
                        ? DEFAULT_INTERNAL_UPDATE_INTERVAL_MS
                        : config.getMaxUpdateInterval().toMillis();
        //this.fusionAlgorithm = fusionAlgorithm;

        for (RangingTechnology technology : config.getRangingTechnologiesToRangeWith()) {
            if (technology.isSupported(context)) {
                mAdapters.put(technology, newAdapter(technology));
            } else {
                Log.w(TAG, "Attempted to create adapter for unsupported technology " + technology);
            }
        }
    }

    private @NonNull RangingAdapter newAdapter(@NonNull RangingTechnology technology) {
        switch (technology) {
            case UWB:
                return new UwbAdapter(context, mAdapterExecutor, UwbAdapter.DeviceType.CONTROLLER);
            case CS:
                return new CsAdapter();
            default:
                throw new IllegalArgumentException(
                        "Tried to create adapter for unknown technology" + technology);
        }
    }

    @Override
    public void start(@NonNull PrecisionRanging.Callback callback) {
        Log.i(TAG, "Start Precision Ranging called.");
        Preconditions.checkArgument(
                rangingConfigurationsAdded.containsAll(config.getRangingTechnologiesToRangeWith()),
                "Missing configuration for some ranging technologies that were requested.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
            Log.w(TAG, "Failed transition STOPPED -> STARTING");
            return;
        }
        this.callback = Optional.of(callback);

        synchronized (mAdapters) {
            for (RangingTechnology technology : config.getRangingTechnologiesToRangeWith()) {
                var listener = new RangingAdapterListener(technology);
                rangingAdapterListeners.put(technology, listener);
                mAdapters.get(technology).start(listener);
            }
        }

        if (config.getUseFusingAlgorithm()) {
            mAdapterExecutor.execute(this::startFusingAlgorithm);
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
        if (mStateMachine.getState() == State.STOPPED) {
            return;
        }
        reportNewDataIfAvailable();
        checkAndStopIfNeeded();
    }

    /* Reports new data if available via the callback. */
    private void reportNewDataIfAvailable() {
        if (mStateMachine.getState() == State.STOPPED) {
            return;
        }
        // Skip update if it's set to immediate updating (updateInterval == 0), or if not enough
        // time has passed since last update.
        Instant currentTime = Instant.now();
        if (config.getMaxUpdateInterval().isZero()
                || currentTime.isBefore(lastUpdateTime.plus(config.getMaxUpdateInterval()))) {
            return;
        }
        // Skip update if there's no new data to report
        synchronized (mStateMachine) {
            if (mLastRangingData.isEmpty()) {
                return;
            }
        }

        PrecisionData.Builder precisionDataBuilder = PrecisionData.builder();
        synchronized (mStateMachine) {
            ImmutableList.Builder<RangingData> rangingDataBuilder = ImmutableList.builder();
            for (RangingTechnology technology : mAdapters.keySet()) {
                if (mLastRangingData.containsKey(technology)) {
                    rangingDataBuilder.add(mLastRangingData.get(technology));
                }
            }
            ImmutableList<RangingData> rangingData = rangingDataBuilder.build();
            if (!rangingData.isEmpty()) {
                precisionDataBuilder.setRangingData(rangingData);
            }
            lastFusionDataResult.ifPresent(precisionDataBuilder::setFusionData);

            for (RangingTechnology technology : mAdapters.keySet()) {
                mLastRangingData.remove(technology);
            }
            lastFusionDataResult = Optional.empty();
        }
        lastUpdateTime = Instant.now();
        precisionDataBuilder.setTimestamp(lastUpdateTime.toEpochMilli());
        PrecisionData precisionData = precisionDataBuilder.build();
        synchronized (mStateMachine) {
            if (mStateMachine.getState() == State.STOPPED) {
                return;
            }
            callback.get().onData(precisionData);
        }
    }

    /* Checks if stopping conditions are met and if so, stops precision ranging. */
    private void checkAndStopIfNeeded() {
        boolean noActiveRanging = mAdapters.isEmpty();

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
        Log.i(TAG, "stopPrecisionRanging with reason: " + reason);
        synchronized (mStateMachine) {
            if (mStateMachine.getState() == State.STOPPED) {
                Log.v(TAG, "Ranging already stopped, skipping");
                return;
            }
            mStateMachine.setState(State.STOPPED);
        }
        // stop all ranging techs
        synchronized (mAdapters) {
            for (RangingAdapter adapter : mAdapters.values()) {
                adapter.stop();
            }
        }

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
        synchronized (mStateMachine) {
            mLastRangingData.clear();
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
        if (!mAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) mAdapters.get(RangingTechnology.UWB);
        try {
            return uwbAdapter.getCapabilities();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get Uwb capabilities");
            return null;
        }
    }

    @Override
    public ListenableFuture<UwbAddress> getUwbAddress() throws RemoteException {
        if (!mAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) mAdapters.get(RangingTechnology.UWB);
        return uwbAdapter.getLocalAddress();
    }

    @Override
    public ListenableFuture<UwbComplexChannel> getUwbComplexChannel() throws RemoteException {
        if (!mAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) mAdapters.get(RangingTechnology.UWB);
        return uwbAdapter.getComplexChannel();
    }

    @Override
    public void setUwbConfig(RangingParameters rangingParameters) {
        if (config.getRangingTechnologiesToRangeWith().contains(RangingTechnology.UWB)) {
            UwbAdapter uwbAdapter = (UwbAdapter) mAdapters.get(RangingTechnology.UWB);
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
    public ListenableFuture<EnumMap<RangingTechnology, Integer>> getTechnologyStatus() {
        // Combine all isEnabled futures for each technology into a single future. The resulting
        // future contains a list of technologies grouped with their corresponding
        // enabled state.
        ListenableFuture<List<Map.Entry<RangingTechnology, Boolean>>> enabledStatesFuture;
        synchronized (mAdapters) {
            enabledStatesFuture = Futures.allAsList(mAdapters.entrySet().stream()
                    .map((var entry) -> Futures.transform(
                            entry.getValue().isEnabled(),
                            (Boolean isEnabled) -> Map.entry(entry.getKey(), isEnabled),
                            mAdapterExecutor)
                    )
                    .collect(Collectors.toList())
            );
        }

        // Transform the list of enabled states into a technology status map.
        return Futures.transform(
                enabledStatesFuture,
                (List<Map.Entry<RangingTechnology, Boolean>> enabledStates) -> {
                    EnumMap<RangingTechnology, Integer> statuses =
                            new EnumMap<>(RangingTechnology.class);
                    for (RangingTechnology technology : RangingTechnology.values()) {
                        statuses.put(technology, TechnologyStatus.UNUSED);
                    }

                    for (Map.Entry<RangingTechnology, Boolean> enabledState : enabledStates) {
                        RangingTechnology technology = enabledState.getKey();
                        if (enabledState.getValue()) {
                            statuses.put(technology, TechnologyStatus.ENABLED);
                        } else {
                            statuses.put(technology, TechnologyStatus.DISABLED);
                        }
                    }
                    return statuses;
                },
                mAdapterExecutor
        );
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
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STARTED
                        && !mStateMachine.transition(State.STARTING, State.STARTED)) {
                    Log.w(TAG, "Failed transition STARTING -> STARTED");
                    return;
                }
            }
            callback.get().onStarted();
        }

        @Override
        public void onStopped(RangingAdapter.Callback.StoppedReason reason) {
            synchronized (mAdapters) {
                if (mStateMachine.getState() != State.STOPPED) {
                    mAdapters.remove(technology);
                    rangingAdapterListeners.remove(technology);
                }
            }
        }

        @Override
        public void onRangingData(RangingData rangingData) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STARTED) {
                    return;
                }
                lastRangeDataReceivedTime = Instant.now();
                feedDataToFusionAlgorithm(rangingData);
                if (config.getMaxUpdateInterval().isZero()) {
                    PrecisionData precisionData =
                            PrecisionData.builder()
                                    .setRangingData(ImmutableList.of(rangingData))
                                    .setTimestamp(Instant.now().toEpochMilli())
                                    .build();
                    callback.get().onData(precisionData);
                }
                mLastRangingData.put(technology, rangingData);
            }
        }
    }

    /* Listener implementation for fusion algorithm callback. */
    private class FusionAlgorithmListener implements MultiSensorFinderListener {
        @Override
        public void onUpdatedEstimate(Estimate estimate) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STOPPED) {
                    return;
                }
                if (mStateMachine.transition(State.STARTING, State.STARTED)) {
                    callback.get().onStarted();
                }
                FusionData fusionData = FusionData.fromFusionAlgorithmEstimate(estimate);
                if (fusionData.getArCoreState() == FusionData.ArCoreState.OK) {
                    lastFusionDataReceivedTime = Instant.now();
                }
                lastFusionDataResult = Optional.of(fusionData);
                if (config.getMaxUpdateInterval().isZero()) {
                    PrecisionData precisionData =
                            PrecisionData.builder()
                                    .setFusionData(fusionData)
                                    .setTimestamp(Instant.now().toEpochMilli())
                                    .build();
                    callback.get().onData(precisionData);
                }
            }
        }
    }

    @VisibleForTesting
    public void useAdapterForTesting(RangingTechnology technology, RangingAdapter adapter) {
        mAdapters.put(technology, adapter);
    }

    private enum State {
        STARTING,
        STARTED,
        STOPPED,
    }
}