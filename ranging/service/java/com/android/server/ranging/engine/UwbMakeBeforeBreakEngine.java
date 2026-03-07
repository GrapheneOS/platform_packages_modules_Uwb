/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.engine;

import static com.android.server.ranging.common.RangingUtils.InternalReason;

import android.ranging.RangingData;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.StateMachine;
import com.android.server.ranging.common.TimeoutListener;
import com.android.server.ranging.heuristic.DerivativeEstimator;
import com.android.server.ranging.heuristic.RangeHeuristicEventFactory;
import com.android.server.ranging.heuristic.RangeHeuristicEventFactory.RangeHeuristicEvent;
import com.android.server.ranging.heuristic.RawRangeMeters;
import com.android.server.ranging.heuristic.StreakCounter;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.Range;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A {@link RangingEngine} that dynamically transitions between UWB and an alternative technology
 * during a session. Transitions are made using a make-before-break strategy, meaning that the new
 * technology is started before the old one is stopped. This ensures there are no gaps in the
 * ranging data produced.
 */
public class UwbMakeBeforeBreakEngine implements RangingEngine {
    private static final String TAG = UwbMakeBeforeBreakEngine.class.getSimpleName();
    /** Distance in meters where the transitioning should occur. */
    private static final Range<Double> SWAP_THRESHOLD = Range.closed(6.0, 8.0);

    /**
     * How long to wait after starting a technology before it is deemed to have failed.
     */
    private static final Duration TECH_START_TIMEOUT = Duration.ofSeconds(5);
    /**
     * The lowest streak allowed (most consecutive ranging round failures) before the technology is
     * deemed to have failed.
     */
    private static final int MIN_ALLOWED_STREAK = -6;

    /**
     * How long the session is allowed to continue with no data updates. If the session continues
     * for longer than this without reporting data, it will be stopped internally.
     */
    private static final Duration NO_DATA_TIMEOUT = Duration.ofSeconds(10);

    private final RangingTechnology mAlt;
    private final EngineListener mListener;
    private final Executor mExecutor;
    private final RangingInjector mInjector;

    private final StateMachine<State> mStateMachine;
    private final RangeHeuristicEventFactory mEventFactory;

    private final TimeoutListener mNoDataTimeout;

    private StreakCounter mUwbStreakCounter;
    private StreakCounter mAltStreakCounter;

    private RangeHeuristicEvent mStartUwb;
    private RangeHeuristicEvent mOkToStopAlt;
    private RangeHeuristicEvent mStartAlt;
    private RangeHeuristicEvent mOkToStopUwb;

    private RangeHeuristicEvent mAltFailure;
    private RangeHeuristicEvent mUwbFailure;

    private RangeHeuristicEvent mNextEvent;

    private enum State {
        UWB_ONLY,
        SWAPPING,
        ALT_ONLY,
    }

    public UwbMakeBeforeBreakEngine(
            @NonNull RangingTechnology alternate, @NonNull EngineListener listener,
            @NonNull Executor executor, @NonNull RangingInjector injector
    ) {
        mAlt = alternate;
        mListener = listener;
        mExecutor = executor;
        mInjector = injector;
        mStateMachine = new StateMachine<>(State.SWAPPING);
        mEventFactory = new RangeHeuristicEventFactory(executor);
        mNoDataTimeout = new TimeoutListener(() -> {
            synchronized (UwbMakeBeforeBreakEngine.this) {
                Log.w(TAG, "Went " + NO_DATA_TIMEOUT
                        + " with no ranging data from any technology. Stopping session...");
                mNextEvent.cancel();
                mAltFailure.cancel();
                mUwbFailure.cancel();
                mListener.stopSession();
            }
        }, injector);
    }

    @Override
    public @NonNull EnumSet<RangingTechnology> getTechnologiesToStart() {
        return EnumSet.of(RangingTechnology.UWB, mAlt);
    }

    @Override
    public synchronized void start(Set<TechnologyConfig> configs) {
        EnumSet<RangingTechnology> technologies = getTechnologiesToStart();
        for (TechnologyConfig config : configs) {
            technologies.remove(config.getTechnology());
            if (config.getTechnology() == RangingTechnology.UWB) {
                mUwbStreakCounter = new StreakCounter(config, mExecutor, mInjector);
            } else if (config.getTechnology() == mAlt) {
                mAltStreakCounter = new StreakCounter(config, mExecutor, mInjector);
            } else {
                Log.e(TAG, "Attempted to start unexpected technology " + config.getTechnology()
                        + " ignoring...");
            }
        }
        if (!technologies.isEmpty()) {
            throw new IllegalStateException("Failed to start technologies: " + technologies);
        }

        // mAlt -> UWB transition events, distance decreasing.
        mStartUwb = mEventFactory.whenAll(
                mAltStreakCounter
                        .count(new RawRangeMeters(mExecutor)
                                .threshold(range -> range <= SWAP_THRESHOLD.upperEndpoint()))
                        .threshold(count -> count >= 2),
                mAltStreakCounter
                        .count(new DerivativeEstimator(0.4, mExecutor).threshold(d -> d < 0))
                        .threshold(count -> count >= 2));
        mOkToStopAlt = mEventFactory.whenAll(
                mUwbStreakCounter.count().threshold(count -> count >= 4),
                mUwbStreakCounter
                        .count(new RawRangeMeters(mExecutor)
                                .threshold(range -> range <= SWAP_THRESHOLD.lowerEndpoint()))
                        .threshold(count -> count >= 2),
                mUwbStreakCounter
                        .count(new DerivativeEstimator(0.4, mExecutor).threshold(d -> d < 0))
                        .threshold(count -> count >= 2));

        // UWB -> mAlt transition events, distance increasing.
        mStartAlt = mEventFactory.whenAll(
                mUwbStreakCounter
                        .count(new RawRangeMeters(mExecutor)
                                .threshold(range -> range >= SWAP_THRESHOLD.lowerEndpoint()))
                        .threshold(count -> count >= 2),
                mUwbStreakCounter
                        .count(new DerivativeEstimator(0.4, mExecutor).threshold(d -> d > 0))
                        .threshold(count -> count >= 2));
        mOkToStopUwb = mEventFactory.whenAll(
                mAltStreakCounter.count().threshold(count -> count >= 4),
                mAltStreakCounter
                        .count(new RawRangeMeters(mExecutor)
                                .threshold(range -> range >= SWAP_THRESHOLD.upperEndpoint()))
                        .threshold(count -> count >= 2),
                mAltStreakCounter
                        .count(new DerivativeEstimator(0.4, mExecutor).threshold(d -> d > 0))
                        .threshold(count -> count >= 3));

        // Failure detection.
        mAltFailure = mEventFactory.when(
                mAltStreakCounter.count().threshold(count -> {
                    Log.v(TAG, "Count of consecutive " + mAlt + " ranging round success/failure: "
                            + count);
                    return count <= MIN_ALLOWED_STREAK;
                }));
        mUwbFailure = mEventFactory.when(
                mUwbStreakCounter.count().threshold(count -> {
                    Log.v(TAG, "Count of consecutive UWB ranging round success/failure: " + count);
                    return count <= MIN_ALLOWED_STREAK;
                }));

        startUwbFailureListener();
        startAltFailureListener();
        mNoDataTimeout.start(NO_DATA_TIMEOUT);

        swapping();
    }

    private synchronized void uwbOnly() {
        Log.v(TAG, "UWB only");
        mStateMachine.setState(State.UWB_ONLY);

        mNextEvent = mStartAlt;
        mNextEvent.onNextOccurrence(unused -> {
            synchronized (UwbMakeBeforeBreakEngine.this) {
                startAltFailureListener();
                mListener.startTechnologies(Set.of(mAlt));
                swapping();
            }
        });
    }

    private synchronized void swapping() {
        Log.v(TAG, "Swapping");
        mStateMachine.setState(State.SWAPPING);

        mNextEvent = mEventFactory.whenAny(mOkToStopUwb, mOkToStopAlt);
        mNextEvent.onNextOccurrence(any -> {
            synchronized (UwbMakeBeforeBreakEngine.this) {
                if (any == mOkToStopUwb) {
                    mUwbFailure.cancel();
                    mListener.stopTechnologies(
                            Set.of(RangingTechnology.UWB), InternalReason.ENGINE_REQUEST);
                    altOnly();
                } else if (any == mOkToStopAlt) {
                    mAltFailure.cancel();
                    mListener.stopTechnologies(Set.of(mAlt), InternalReason.ENGINE_REQUEST);
                    uwbOnly();
                }
            }
        });
    }

    private synchronized void altOnly() {
        Log.v(TAG, mAlt + " only");
        mStateMachine.setState(State.ALT_ONLY);

        mNextEvent = mStartUwb;
        mNextEvent.onNextOccurrence(unused -> {
            synchronized (UwbMakeBeforeBreakEngine.this) {
                startUwbFailureListener();
                mListener.startTechnologies(Set.of(RangingTechnology.UWB));
                swapping();
            }
        });
    }

    @Override
    public synchronized void onData(@NonNull RangingData data) {
        mNoDataTimeout.reset(NO_DATA_TIMEOUT);
        mUwbStreakCounter.onData(data);
        mAltStreakCounter.onData(data);
    }

    @Override
    public synchronized void onTechnologyStopped(
            @NonNull RangingTechnology technology, @InternalReason int reason
    ) {
        if (reason == InternalReason.ENGINE_REQUEST || reason == InternalReason.LOCAL_REQUEST
                || reason == InternalReason.REMOTE_REQUEST) {
            return;
        }
        Log.i(TAG, "Unexpected stop of " + technology + " for reason " + reason);

        if (technology == RangingTechnology.UWB) {
            handleUwbFailure();
        } else if (technology == mAlt) {
            handleAltFailure();
        }
    }

    private synchronized void handleUwbFailure() {
        Log.i(TAG, "Detected UWB failure");
        switch (mStateMachine.getState()) {
            case UWB_ONLY -> reset();
            case SWAPPING -> {
                mNextEvent.cancel();
                altOnly();
            }
            case ALT_ONLY -> { /* ignore */ }
        }
    }

    private synchronized void handleAltFailure() {
        Log.i(TAG, "Detected " + mAlt + " failure");
        switch (mStateMachine.getState()) {
            case UWB_ONLY -> { /* ignore */ }
            case SWAPPING -> {
                mNextEvent.cancel();
                uwbOnly();
            }
            case ALT_ONLY -> reset();
        }
    }

    private synchronized void reset() {
        Log.w(TAG, "All active technologies have stopped. Resetting...");
        mNextEvent.cancel();
        mAltFailure.cancel();
        mUwbFailure.cancel();
        startUwbFailureListener();
        startAltFailureListener();
        mListener.startTechnologies(getTechnologiesToStart());
        swapping();
    }

    private void startUwbFailureListener() {
        mUwbStreakCounter.onStart(TECH_START_TIMEOUT);
        mUwbFailure.onNextOccurrence(unused ->
                mListener.stopTechnologies(
                        Set.of(RangingTechnology.UWB), InternalReason.SYSTEM_POLICY));
    }

    private void startAltFailureListener() {
        mAltStreakCounter.onStart(TECH_START_TIMEOUT);
        mAltFailure.onNextOccurrence(unused ->
                mListener.stopTechnologies(Set.of(mAlt), InternalReason.SYSTEM_POLICY));
    }
}
