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
import androidx.core.uwb.backend.impl.internal.UwbAddress;

import com.android.internal.annotations.GuardedBy;
import com.android.ranging.RangingUtils.StateMachine;
import com.android.ranging.cs.CsAdapter;
import com.android.ranging.uwb.UwbAdapter;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

/**  Implementation of the Android multi-technology ranging layer */
public final class RangingSessionImpl implements RangingSession {

    private static final String TAG = RangingSessionImpl.class.getSimpleName();

    /**
     * Default frequency of the task running the periodic update when {@link
     * RangingConfig#getMaxUpdateInterval} is set to 0.
     */
    private static final long DEFAULT_INTERNAL_UPDATE_INTERVAL_MS = 100;

    private final Context mContext;
    private final RangingConfig mConfig;

    /** Callback for session events. Invariant: Non-null while a session is ongoing */
    private RangingSession.Callback mCallback;

    /** Keeps track of state of the ranging session */
    private final StateMachine<State> mStateMachine;

    /**
     * Ranging adapters used for this session.
     * Must be thread safe. If you must synchronize on mAdapters and mStateMachine, make sure
     * mAdapters is the outer block, otherwise deadlock could occur!
     */
    private final Map<RangingTechnology, RangingAdapter> mAdapters;
    /** Must be thread safe */
    private final Map<RangingTechnology, RangingAdapter.Callback> mAdapterListeners;

    /**
     * In this instance the primary fusion algorithm is the ArCoreMultiSensorFinder algorithm. In
     * future we could create a common interface that a fusion algorithm should conform to and then
     * make this generic so the caller can choose which fusion algorithm to use.
     */
//    private Optional<ArCoreMultiSensorFinder> fusionAlgorithm;
//
//    private Optional<MultiSensorFinderListener> fusionAlgorithmListener;

    // TODO(b/331206299): Check after arcore is integrated.

    /**
     * The executor where periodic updater is executed. Periodic updater updates the caller with
     * new data if available and stops precision ranging if stopping conditions are met. Periodic
     * updater doesn't report new data if config.getMaxUpdateInterval is 0, in that case updates
     * happen immediately after new data is received.
     */
    private final ScheduledExecutorService mPeriodicUpdateExecutor;

    /**
     * Executor service for running async tasks such as starting/stopping individual ranging
     * adapters and fusion algorithm. Most of the results of running the tasks are received via
     * listeners.
     */
    private final ListeningExecutorService mAdapterExecutor;

    /** Last data received from each ranging technology */
    @GuardedBy("mStateMachine")
    private final Map<RangingTechnology, RangingReport> mLastRangingReports;

    /** Last data received from the fusion algorithm */
    @GuardedBy("mStateMachine")
    private Optional<FusionReport> mLastFusionReport;

    /**
     * Last update time is used to check if we should report new data via the callback if available.
     * It's not used as a reason to stop the ranging session, last received times are used instead
     * for that.
     */
    private Instant mLastUpdateTime;

    /**
     * Start time is used to check if we're in a grace period right after starting so we don't stop
     * ranging before giving it a chance to start producing data.
     */
    private Instant mStartTime;

    /**
     * Last Range data received is used to check if ranging session should be stopped if we didn't
     * receive any data for too long, or to check if we should stop due to "drifting" in case fusion
     * algorithm is still reporting data, but we didn't feed any ranging data into for far too long.
     */
    private Instant mLastRangeDataReceivedTime;

    /**
     * Last Fusion data received time is used to check if ranging session should be stopped if we
     * didn't receive any data for too long.
     */
    private Instant mLastFusionDataReceivedTime;

    public RangingSessionImpl(
            @NonNull Context context,
            @NonNull RangingConfig config,
            @NonNull ScheduledExecutorService periodicUpdateExecutor,
            @NonNull ListeningExecutorService rangingAdapterExecutor
            //Optional<ArCoreMultiSensorFinder> fusionAlgorithm,
    ) {
        mContext = context;
        mConfig = config;

        mStateMachine = new StateMachine<>(State.STOPPED);
        mCallback = null;

        mAdapters = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));
        mAdapterListeners = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));

        mPeriodicUpdateExecutor = periodicUpdateExecutor;
        mAdapterExecutor = rangingAdapterExecutor;

        mLastUpdateTime = Instant.EPOCH;

        mLastRangingReports = new EnumMap<>(RangingTechnology.class);
        mLastRangeDataReceivedTime = Instant.EPOCH;
        mLastFusionReport = Optional.empty();
        mLastFusionDataReceivedTime = Instant.EPOCH;

        //this.fusionAlgorithm = fusionAlgorithm;
    }

    private @NonNull RangingAdapter newAdapter(@NonNull RangingTechnology technology) {
        switch (technology) {
            case UWB:
                return new UwbAdapter(mContext, mAdapterExecutor, UwbAdapter.DeviceType.CONTROLLER);
            case CS:
                return new CsAdapter();
            default:
                throw new IllegalArgumentException(
                        "Tried to create adapter for unknown technology" + technology);
        }
    }

    @Override
    public void start(@NonNull RangingParameters parameters, @NonNull Callback callback) {
        // TODO(b/353991075): We might want to set useUwbMeasurements automatically based on
        // provided parameters to avoid this failure condition.
        if (mConfig.getUseFusingAlgorithm() && parameters.getUwbParameters().isPresent()) {
            Preconditions.checkArgument(
                    mConfig.getFusionAlgorithmConfig().get().getUseUwbMeasurements(),
                    "Fusion algorithm should accept UWB measurements since UWB was requested.");
        }
        EnumMap<RangingTechnology, RangingParameters.TechnologyParameters> paramsMap =
                parameters.asMap();
        mAdapters.keySet().retainAll(paramsMap.keySet());

        Log.i(TAG, "Start Precision Ranging called.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
            Log.w(TAG, "Failed transition STOPPED -> STARTING");
            return;
        }
        mCallback = callback;

        for (RangingTechnology technology : paramsMap.keySet()) {
            if (!technology.isSupported(mContext)) {
                Log.w(TAG, "Attempted to range with unsupported technology " + technology
                        + ", skipping");
                continue;
            }

            // Do not overwrite any adapters that were supplied for testing
            if (!mAdapters.containsKey(technology)) {
                mAdapters.put(technology, newAdapter(technology));
            }

            AdapterListener listener = new AdapterListener(technology);
            mAdapterListeners.put(technology, listener);
            mAdapters.get(technology).start(paramsMap.get(technology), listener);
        }

        if (mConfig.getUseFusingAlgorithm()) {
            mAdapterExecutor.execute(this::startFusingAlgorithm);
        }

        mStartTime = Instant.now();
        Log.i(TAG, "Starting periodic update. Start time: " + mStartTime);

        long periodicUpdateIntervalMs = mConfig.getMaxUpdateInterval().isZero()
                ? DEFAULT_INTERNAL_UPDATE_INTERVAL_MS
                : mConfig.getMaxUpdateInterval().toMillis();
        var unused = mPeriodicUpdateExecutor.scheduleWithFixedDelay(
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
        if (mConfig.getMaxUpdateInterval().isZero()
                || currentTime.isBefore(mLastUpdateTime.plus(mConfig.getMaxUpdateInterval()))) {
            return;
        }
        // Skip update if there's no new data to report
        synchronized (mStateMachine) {
            if (mLastRangingReports.isEmpty()) {
                return;
            }
        }

        RangingData.Builder dataBuilder = RangingData.builder();
        synchronized (mStateMachine) {
            ImmutableList.Builder<RangingReport> rangingReportsBuilder = ImmutableList.builder();
            for (RangingTechnology technology : mAdapters.keySet()) {
                if (mLastRangingReports.containsKey(technology)) {
                    rangingReportsBuilder.add(mLastRangingReports.get(technology));
                }
            }
            ImmutableList<RangingReport> rangingData = rangingReportsBuilder.build();
            if (!rangingData.isEmpty()) {
                dataBuilder.setRangingReports(rangingData);
            }
            mLastFusionReport.ifPresent(dataBuilder::setFusionReport);

            for (RangingTechnology technology : mAdapters.keySet()) {
                mLastRangingReports.remove(technology);
            }
            mLastFusionReport = Optional.empty();
        }
        mLastUpdateTime = Instant.now();
        dataBuilder.setTimestamp(mLastUpdateTime.toEpochMilli());
        RangingData data = dataBuilder.build();
        synchronized (mStateMachine) {
            if (mStateMachine.getState() == State.STOPPED) {
                return;
            }
            mCallback.onData(data);
        }
    }

    /* Checks if stopping conditions are met and if so, stops precision ranging. */
    private void checkAndStopIfNeeded() {
        boolean noActiveRanging = mAdapters.isEmpty();
        boolean seenFusionData = mLastFusionDataReceivedTime.equals(Instant.EPOCH);

        // if only ranging is used and all ranging techs are stopped then stop since we won't be
        // getting
        // any new data from this point.
        if (noActiveRanging && !mConfig.getUseFusingAlgorithm()) {
            Log.i(TAG,
                    "stopping precision ranging cause: no active ranging in progress and  not "
                            + "using fusion"
                            + " algorithm");
            stopPrecisionRanging(Callback.StoppedReason.EMPTY_SESSION_TIMEOUT);
            return;
        }

        // if both ranging and fusion alg used, but all ranging techs are stopped then stop if there
        // were no successful fusion alg data up to this point since fusion alg can only work if it
        // received some ranging data.
        if (noActiveRanging && mConfig.getUseFusingAlgorithm() && !seenFusionData) {
            Log.i(TAG,
                    "stopping precision ranging cause: no active ranging in progress and haven't "
                            + "seen"
                            + " successful fusion data");
            stopPrecisionRanging(Callback.StoppedReason.EMPTY_SESSION_TIMEOUT);
            return;
        }

        // if both ranging and fusion alg used but all ranges are stopped and there is successful
        // arcore
        // data then check if drift timeout expired.
        Instant currentTime = Instant.now();
        if (noActiveRanging && mConfig.getUseFusingAlgorithm() && seenFusionData) {
            if (currentTime.isAfter(
                    mLastRangeDataReceivedTime.plus(mConfig.getFusionAlgorithmDriftTimeout()))) {
                Log.i(TAG,
                        "stopping precision ranging cause: fusion algorithm drift timeout ["
                                + mConfig.getFusionAlgorithmDriftTimeout().toMillis() + " ms]");
                stopPrecisionRanging(Callback.StoppedReason.EMPTY_SESSION_TIMEOUT);
                return;
            }
        }

        // If we're still inside the init timeout don't stop precision ranging for any of the
        // reasons below this.
        if (currentTime.isBefore(mStartTime.plus(mConfig.getInitTimeout()))) {
            return;
        }

        // If we didn't receive data from any source for more than the update timeout then stop.
        Instant lastReceivedDataTime =
                mLastRangeDataReceivedTime.isAfter(mLastFusionDataReceivedTime)
                        ? mLastRangeDataReceivedTime
                        : mLastFusionDataReceivedTime;
        if (currentTime.isAfter(lastReceivedDataTime.plus(mConfig.getNoUpdateTimeout()))) {
            Log.i(TAG,
                    "stopping precision ranging cause: no update timeout ["
                            + mConfig.getNoUpdateTimeout().toMillis() + " ms]");
            stopPrecisionRanging(Callback.StoppedReason.EMPTY_SESSION_TIMEOUT);
            return;
        }

        // None of the stopping conditions met, no stopping needed.
    }

    /* Feeds ranging adapter data into the fusion algorithm. */
    private void feedDataToFusionAlgorithm(RangingReport rangingReport) {
        switch (rangingReport.getRangingTechnology()) {
            case UWB:
//                fusionAlgorithm
//                        .get()
//                        .updateWithUwbMeasurement(rangingData.getRangeDistance(), rangingData
//                        .getTimestamp());
                break;
            case CS:
//                throw new UnsupportedOperationException(
//                        "CS support not implemented. Can't update fusion alg.");
        }
    }

    @Override
    public void stop() {
        stopPrecisionRanging(RangingAdapter.Callback.StoppedReason.REQUESTED);
    }

    /* Calls stop on all ranging adapters and the fusion algorithm and resets all internal states
    . */
    private void stopPrecisionRanging(@Callback.StoppedReason int reason) {
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
            for (RangingTechnology technology : mAdapters.keySet()) {
                mAdapters.get(technology).stop();
                mCallback.onStopped(technology, reason);
            }
        }

        if (mConfig.getUseFusingAlgorithm()) {
//            internalExecutorService.execute(
//                    () -> {
//                        var status = fusionAlgorithm.get().stop();
//                        if (status != Status.OK) {
//                            Log.w(TAG,"Fusion alg stop failed: " + status);
//                        }
//                    });
        }

        mCallback.onStopped(null, reason);

        // reset internal states and objects
        synchronized (mStateMachine) {
            mLastRangingReports.clear();
            mLastFusionReport = Optional.empty();
        }
        mLastUpdateTime = Instant.EPOCH;
        mLastRangeDataReceivedTime = Instant.EPOCH;
        mLastFusionDataReceivedTime = Instant.EPOCH;
        mAdapters.clear();
        mAdapterListeners.clear();
        //fusionAlgorithmListener = Optional.empty();
        mCallback = null;
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

    @DoNotCall("Not implemented")
    @Override
    public void getCsCapabilities() {
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

//    @VisibleForTesting
//    public Optional<MultiSensorFinderListener> getFusionAlgorithmListener() {
//        return fusionAlgorithmListener;
//    }

    /* Listener implementation for ranging adapter callback. */
    private class AdapterListener implements RangingAdapter.Callback {
        private final RangingTechnology mTechnology;

        AdapterListener(RangingTechnology technology) {
            this.mTechnology = technology;
        }

        @Override
        public void onStarted() {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STOPPED) {
                    Log.w(TAG, "Received adapter onStarted but ranging session is stopped");
                    return;
                }
                if (mStateMachine.transition(State.STARTING, State.STARTED)) {
                    // The first adapter in the session has started, so start the session.
                    mCallback.onStarted(null);
                }
            }
            mCallback.onStarted(mTechnology);
        }

        @Override
        public void onStopped(@RangingAdapter.Callback.StoppedReason int reason) {
            synchronized (mAdapters) {
                if (mStateMachine.getState() != State.STOPPED) {
                    mAdapters.remove(mTechnology);
                    mAdapterListeners.remove(mTechnology);
                    mCallback.onStopped(mTechnology, reason);
                }
            }
        }

        @Override
        public void onRangingData(RangingReport rangingReports) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STARTED) {
                    return;
                }
                mLastRangeDataReceivedTime = Instant.now();
                feedDataToFusionAlgorithm(rangingReports);
                if (mConfig.getMaxUpdateInterval().isZero()) {
                    RangingData data =
                            RangingData.builder()
                                    .setRangingReports(ImmutableList.of(rangingReports))
                                    .setTimestamp(Instant.now().toEpochMilli())
                                    .build();
                    mCallback.onData(data);
                }
                mLastRangingReports.put(mTechnology, rangingReports);
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
                    mCallback.onStarted(null);
                }
                FusionReport fusionReport = FusionReport.fromFusionAlgorithmEstimate(estimate);
                if (fusionReport.getArCoreState() == FusionReport.ArCoreState.OK) {
                    mLastFusionDataReceivedTime = Instant.now();
                }
                mLastFusionReport = Optional.of(fusionReport);
                if (mConfig.getMaxUpdateInterval().isZero()) {
                    RangingData data =
                            RangingData.builder()
                                    .setFusionReport(fusionReport)
                                    .setTimestamp(Instant.now().toEpochMilli())
                                    .build();
                    mCallback.onData(data);
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
