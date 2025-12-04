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

package com.android.server.ranging.heuristic;

import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.heuristic.RangeHeuristic.HeuristicThreshold;

import com.google.common.collect.ObjectArrays;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoublePredicate;

/** Provides methods to construct {@link RangeHeuristicEvent}s grouped with an executor. */
public class RangeHeuristicEventFactory {
    private static final String TAG = RangeHeuristicEventFactory.class.getSimpleName();

    /** An asynchronous event triggered based on the value of a {@link RangeHeuristic}. */
    public abstract static class RangeHeuristicEvent {
        private final Set<Consumer<RangeHeuristicEvent>> mListeners;
        private final Executor mExecutor;

        private RangeHeuristicEvent(Executor executor) {
            mExecutor = executor;
            mListeners = Collections.synchronizedSet(new HashSet<>());
        }
        /** Register a listener to be run next time this event occurs. */
        public void onNextOccurrence(Consumer<RangeHeuristicEvent> listener) {
            mListeners.add(listener);
        }

        public void cancel() {
            mListeners.clear();
        }

        @VisibleForTesting
        public void complete(RangeHeuristicEvent event) {
            synchronized (mListeners) {
                mListeners.forEach(listener -> mExecutor.execute(() -> listener.accept(event)));
                mListeners.clear();
            }
        }
    }

    /** A {@link RangeHeuristicEvent} that triggers when the provided heuristic threshold is met. */
    public static class ThresholdEvent extends RangeHeuristicEvent {
        /** Use {@link RangeHeuristicEventFactory#when(HeuristicThreshold)} */
        private ThresholdEvent(HeuristicThreshold threshold, Executor executor) {
            super(executor);
            threshold.getHeuristic().registerListener(value -> {
                if (threshold.getCondition().test(value)) complete(this);
            });
        }
    }

    /**
     * A {@link RangeHeuristicEvent} that triggers when all provided heuristics meet their
     * threshold.
     */
    public static class ConjunctionEvent extends RangeHeuristicEvent {
        private final AtomicInteger mNumEventsRemaining;

        /**
         * Use {@link RangeHeuristicEventFactory#whenAll(
         * HeuristicThreshold, HeuristicThreshold, HeuristicThreshold...)})
         */
        private ConjunctionEvent(Executor executor, HeuristicThreshold... thresholds) {
            super(executor);
            mNumEventsRemaining = new AtomicInteger(thresholds.length);
            for (HeuristicThreshold threshold : thresholds) {
                threshold.getHeuristic().registerListener(new Listener(threshold.getCondition()));
            }
        }

        private class Listener implements RangeHeuristic.Listener {
            private final AtomicBoolean mIsCondition = new AtomicBoolean(false);
            private final DoublePredicate mCondition;

            Listener(DoublePredicate condition) {
                mCondition = condition;
            }

            @Override
            public void onHeuristicUpdated(double value) {
                if (mCondition.test(value)) {
                    if (mIsCondition.compareAndSet(false, true)
                            && mNumEventsRemaining.decrementAndGet() == 0
                    ) {
                        complete(ConjunctionEvent.this);
                    }
                } else if (mIsCondition.compareAndSet(true, false)) {
                    mNumEventsRemaining.incrementAndGet();
                }
            }
        }
    }

    /**
     * A {@link RangeHeuristicEvent} that triggers when any of the provided heuristics meet their
     * threshold. Listeners are notified with the event that won the race.
     */
    public static class RaceEvent extends RangeHeuristicEvent {
        private final AtomicBoolean mFinishLine = new AtomicBoolean();

        private RaceEvent(Executor executor, RangeHeuristicEvent[] events) {
            super(executor);
            Arrays.stream(events).forEach(event -> event.onNextOccurrence(
                    unused -> {
                        if (mFinishLine.compareAndSet(false, true)) complete(event);
                    }));
        }
    }

    private final Executor mExecutor;

    public RangeHeuristicEventFactory(Executor executor) {
        mExecutor = executor;
    }

    /**
     * Create an {@link RangeHeuristicEvent} that completes the next time the provided threshold is
     * satisfied.
     */
    public ThresholdEvent when(HeuristicThreshold threshold) {
        return new ThresholdEvent(threshold, mExecutor);
    }

    /**
     * Create a {@link RangeHeuristicEvent} that completes once all provided thresholds are passed
     * in parallel.
     */
    public ConjunctionEvent whenAll(
            HeuristicThreshold t1, HeuristicThreshold t2, HeuristicThreshold... rest
    ) {
        return new ConjunctionEvent(
                mExecutor, ObjectArrays.concat(t1, ObjectArrays.concat(t2, rest)));
    }


    /**
     * Creates a {@link RangeHeuristicEvent} that completes once any of the provided events
     * complete.
     */
    public RaceEvent whenAny(
            RangeHeuristicEvent e1, RangeHeuristicEvent e2, RangeHeuristicEvent... rest
    ) {
        return new RaceEvent(
                mExecutor, ObjectArrays.concat(e1, ObjectArrays.concat(e2, rest)));
    }
}
