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

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingData;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.StateMachine;
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
 * during a session. Transitions are made using a make-before-break strategy, meaning that the new
 * technology is started before the old one is stopped. This ensures there are no gaps in the
 * ranging data produced.
 */
public class UwbMakeBeforeBreakEngine implements RangingEngine {
    private static final String TAG = UwbMakeBeforeBreakEngine.class.getSimpleName();

    private final RangingTechnology mAlt;
    private final StateMachine<State> mStateStateMachine;
    private final Range<Double> mSwapDistanceM;
    private final EngineEventFactory mEventFactory;
    private final Executor mExecutor;
    private final RangingInjector mInjector;

    private final RawRangeMeters mRangeM;
    private final DerivativeEstimator mDerivative;

    private StreakCounter mUwbStreakCounter;
    private StreakCounter mAltStreakCounter;

    private EngineEvent mStartUwb;
    private EngineEvent mOkToStopAlt;
    private EngineEvent mStartAlt;
    private EngineEvent mOkToStopUwb;

    private enum State {
        UWB_ONLY,
        SWAPPING,
        ALT_ONLY,
    }

    public UwbMakeBeforeBreakEngine(
            RangingTechnology alternate, Executor executor, RangingInjector injector
    ) {
        mAlt = alternate;
        mStateStateMachine = new StateMachine<>(State.SWAPPING);
        mEventFactory = new EngineEventFactory(executor);
        mExecutor = executor;
        mInjector = injector;
        // TODO(449167692): Find suitable threshold values
        mSwapDistanceM = Range.closed(9.0, 10.0);
        mRangeM = new RawRangeMeters(mExecutor);
        mDerivative = new DerivativeEstimator(0.4, mExecutor);
    }

    @Override
    public @NonNull EnumSet<RangingTechnology> getTechnologiesToStart() {
        return EnumSet.of(RangingTechnology.UWB, mAlt);
    }

    @Override
    public void start(Set<TechnologyConfig> configs) {
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
                        .count(mRangeM.threshold(range -> range <= mSwapDistanceM.upperEndpoint()))
                        .threshold(count -> count >= 2),
                mAltStreakCounter
                        .count(mDerivative.threshold(d -> d < 0))
                        .threshold(count -> count >= 2));
        mOkToStopAlt = mEventFactory.whenAll(
                mUwbStreakCounter
                        .count(mRangeM).threshold(count -> count >= 4),
                mUwbStreakCounter
                        .count(mRangeM.threshold(range -> range <= mSwapDistanceM.lowerEndpoint()))
                        .threshold(count -> count >= 2),
                mUwbStreakCounter
                        .count(mDerivative.threshold(d -> d < 0))
                        .threshold(count -> count >= 2));

        // UWB -> mAlt transition events, distance increasing.
        mStartAlt = mEventFactory.whenAll(
                mUwbStreakCounter
                        .count(mRangeM.threshold(range -> range >= mSwapDistanceM.lowerEndpoint()))
                        .threshold(count -> count >= 2),
                mUwbStreakCounter
                        .count(mDerivative.threshold(d -> d > 0))
                        .threshold(count -> count >= 2));
        mOkToStopUwb = mEventFactory.whenAll(
                mAltStreakCounter
                        .count(mRangeM).threshold(count -> count >= 4),
                mAltStreakCounter
                        .count(mRangeM.threshold(range -> range >= mSwapDistanceM.upperEndpoint()))
                        .threshold(count -> count >= 2),
                mAltStreakCounter
                        .count(mDerivative.threshold(d -> d > 0))
                        .threshold(count -> count >= 2));

        swapping();
    }

    private void uwbOnly() {
        mStateStateMachine.setState(State.UWB_ONLY);

        mStartAlt.onNextOccurrence(unused -> {
            // TODO: Start mAlt
            swapping();
        });
    }

    private void swapping() {
        mStateStateMachine.setState(State.SWAPPING);

        mEventFactory.whenAny(mOkToStopUwb, mOkToStopAlt).onNextOccurrence(any -> {
            if (any == mOkToStopUwb) {
                // TODO: Stop UWB
                altOnly();
            } else if (any == mOkToStopAlt) {
                // TODO: Stop mAlt
                uwbOnly();
            }
        });
    }

    private void altOnly() {
        mStateStateMachine.setState(State.ALT_ONLY);

        mStartUwb.onNextOccurrence(unused -> {
            // TODO: Start UWB
            swapping();
        });
    }

    @Override
    public void onData(@NonNull RangingData data) {
        mUwbStreakCounter.onData(data);
        mAltStreakCounter.onData(data);
        mRangeM.onData(data);
        mDerivative.onData(data);
    }

    @Override
    public void onTechnologyStopped(
            @NonNull RangingTechnology technology, @InternalReason int reason
    ) {
        throw new IllegalStateException("Not Implemented");
    }
}
