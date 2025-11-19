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
import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.engine.EngineEventFactory.EngineEvent;
import com.android.server.ranging.engine.heuristic.DerivativeEstimator;
import com.android.server.ranging.engine.heuristic.RawRangeMeters;
import com.android.server.ranging.engine.heuristic.StreakCounter;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.Range;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * A {@link RangingEngine} that dynamically transitions between UWB and an alternative technology
 * during a session. Transitions are made using a break-before-make strategy, meaning that the old
 * technology is stopped before the new one is started. This results in brief gaps in the
 * ranging data produced.
 */
public class UwbBreakBeforeMakeEngine implements RangingEngine {
    private static final String TAG = UwbBreakBeforeMakeEngine.class.getSimpleName();
    // TODO(449167692): Find suitable threshold values
    /** Distance in meters where the transitioning should occur. */
    private static final Range<Double> SWAP_THRESHOLD = Range.closed(6.0, 8.0);
    /**
     * The lowest streak allowed (most consecutive ranging round failures) before the technology is
     * deemed to have failed.
     */
    private static final int MIN_ALLOWED_STREAK = -4;
    private static final double RECENCY_BIAS = 0.4;
    private final RangingTechnology mAlt;
    private final EngineListener mListener;
    private final Executor mExecutor;
    private final RangingInjector mInjector;
    private final EngineEventFactory mEventFactory;
    private final RawRangeMeters mRangeM;
    private final DerivativeEstimator mDerivative;

    private StreakCounter mUwbStreakCounter;
    private StreakCounter mAltStreakCounter;

    // Events that trigger a transition to the next stage
    private EngineEvent mStartAltTransition;
    private EngineEvent mStartUwbTransition;

    // Failure detection.
    private EngineEvent mAltFailure;
    private EngineEvent mUwbFailure;
    private EngineEvent mNextEvent; // Used to cancel the pending event in a given state
    private RangingTechnology mNextTechnology = null;

    public UwbBreakBeforeMakeEngine(
            @NonNull RangingTechnology alternate, @NonNull EngineListener listener,
            @NonNull Executor executor, @NonNull RangingInjector injector
    ) {
        mAlt = alternate;
        mListener = listener;
        mExecutor = executor;
        mInjector = injector;
        mEventFactory = new EngineEventFactory(executor);
        mRangeM = new RawRangeMeters(mExecutor);
        mDerivative = new DerivativeEstimator(RECENCY_BIAS, mExecutor);
    }

    @Override
    public @NonNull EnumSet<RangingTechnology> getTechnologiesToStart() {
        return EnumSet.of(RangingTechnology.UWB, mAlt);
    }

    @Override
    public synchronized void start(Set<TechnologyConfig> configs) {
        TechnologyConfig uwbConfig = null;
        TechnologyConfig altConfig = null;
        for (TechnologyConfig config : configs) {
            if (config.getTechnology() == RangingTechnology.UWB) {
                uwbConfig = config;
            } else if (config.getTechnology() == mAlt) {
                altConfig = config;
            } else {
                Log.e(TAG, "Attempted to start unexpected technology " + config.getTechnology()
                        + " ignoring...");
            }
        }

        if (uwbConfig == null || altConfig == null) {
            throw new IllegalStateException(
                "BreakBeforeMakeEngine requires configs for both UWB and " + mAlt);
        }

        mUwbStreakCounter = new StreakCounter(uwbConfig, mExecutor, mInjector);
        mAltStreakCounter = new StreakCounter(altConfig, mExecutor, mInjector);

        // UWB -> mAlt transition event: distance increasing and far
        mStartAltTransition = mEventFactory.whenAll(
            mUwbStreakCounter
                .count(mRangeM.threshold(range -> range >= SWAP_THRESHOLD.upperEndpoint()))
                .threshold(count -> count >= 2),
            mUwbStreakCounter
                .count(mDerivative.threshold(d -> d > 0))
                .threshold(count -> count >= 2));

        // mAlt -> UWB transition event: distance decreasing and near
        mStartUwbTransition = mEventFactory.whenAll(
            mAltStreakCounter
                .count(mRangeM.threshold(range -> range <= SWAP_THRESHOLD.lowerEndpoint()))
                .threshold(count -> count >= 2),
            mAltStreakCounter
                .count(mDerivative.threshold(d -> d < 0))
                .threshold(count -> count >= 2));

        // Failure detection
        mUwbFailure = mEventFactory.when(
            mUwbStreakCounter.count(mRangeM).threshold(count -> count <= MIN_ALLOWED_STREAK));
        mAltFailure = mEventFactory.when(
            mAltStreakCounter.count(mRangeM).threshold(count -> count <= MIN_ALLOWED_STREAK));

        startAlt();
    }

    private synchronized void startUwb() {
        Log.v(TAG, "Start UWB");
        mListener.startTechnologies(Set.of(RangingTechnology.UWB));
        // Listen for UWB failure
        mUwbFailure.onNextOccurrence(this::handleFailureEvent);

        mNextEvent = mStartAltTransition;
        mNextEvent.onNextOccurrence(unused -> {
            synchronized (UwbBreakBeforeMakeEngine.this) {
                mNextTechnology = mAlt;
                mListener.stopTechnologies(Set.of(RangingTechnology.UWB));
            }
        });
    }

    private synchronized void startAlt() {
        Log.v(TAG, "Start " + mAlt);
        mListener.startTechnologies(Set.of(mAlt));
        // Listen for ALT failure immediately.
        mAltFailure.onNextOccurrence(this::handleFailureEvent);

        // ALT is now fully active. Listen for the event to stop ALT and switch to UWB.
        mNextEvent = mStartUwbTransition;
        mNextEvent.onNextOccurrence(unused -> {
            synchronized (UwbBreakBeforeMakeEngine.this) {
                mNextTechnology = RangingTechnology.UWB;
                mListener.stopTechnologies(Set.of(mAlt));
            }
        });

    }

    @Override
    public synchronized void onData(@NonNull RangingData data) {
        mUwbStreakCounter.onData(data);
        mAltStreakCounter.onData(data);
        mRangeM.onData(data);
        mDerivative.onData(data);
    }

    @Override
    public synchronized void onTechnologyStopped(
            @NonNull RangingTechnology technology, @InternalReason int reason) {
        if (reason != InternalReason.ENGINE_REQUEST) return;

        // Local stop request completed.
        if (mNextEvent != null) mNextEvent.cancel();

        mUwbFailure.cancel();
        mAltFailure.cancel();
        if (mNextTechnology == RangingTechnology.UWB) startUwb();
        else startAlt();
    }

    private synchronized void handleFailureEvent(EngineEvent event) {
        if (event == mUwbFailure) {
            mNextTechnology = mAlt;
            mListener.stopTechnologies(Set.of(RangingTechnology.UWB));
        } else if (event == mAltFailure) {
            mNextTechnology = RangingTechnology.UWB;
            mListener.stopTechnologies(Set.of(mAlt));
        }
    }

    @VisibleForTesting
    EngineEvent getNextEvent() {
        return mNextEvent;
    }

    @VisibleForTesting
    EngineEvent getAltFailure() {
        return mAltFailure;
    }

    @VisibleForTesting
    EngineEvent getUwbFailure() {
        return mUwbFailure;
    }
}
