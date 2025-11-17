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

import com.android.server.ranging.engine.heuristic.RangeHeuristic;
import com.android.server.ranging.engine.heuristic.RangeHeuristic.HeuristicThreshold;

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

/** Manages asynchronous {@link EngineEvent}s for a {@link RangingEngine} */
class EngineEventFactory {
    private static final String TAG = EngineEventFactory.class.getSimpleName();

    /** An asynchronous {@link RangingEngine} related event. */
    abstract static class EngineEvent {
        private final Set<Consumer<EngineEvent>> mListeners;
        private final Executor mExecutor;

        EngineEvent(Executor executor) {
            mExecutor = executor;
            mListeners = Collections.synchronizedSet(new HashSet<>());
        }
        /** Register a listener to be run next time this event occurs. */
        void onNextOccurrence(Consumer<EngineEvent> listener) {
            mListeners.add(listener);
        }

        void cancel() {
            mListeners.clear();
        }

        void complete(EngineEvent event) {
            synchronized (mListeners) {
                mListeners.forEach(listener -> mExecutor.execute(() -> listener.accept(event)));
                mListeners.clear();
            }
        }
    }

    /** An {@link EngineEvent} based on a heuristic. */
    static class HeuristicEvent extends EngineEvent {
        /** Use {@link EngineEventFactory#when(HeuristicThreshold)} */
        private HeuristicEvent(HeuristicThreshold threshold, Executor executor) {
            super(executor);
            threshold.getHeuristic().registerListener(value -> {
                if (threshold.getCondition().test(value)) complete(this);
            });
        }
    }

    static class HeuristicConjunctionEvent extends EngineEvent {
        private final AtomicInteger mNumEventsRemaining;

        /**
         * Use {@link EngineEventFactory#whenAll(
         * HeuristicThreshold, HeuristicThreshold, HeuristicThreshold...)})
         */
        private HeuristicConjunctionEvent(Executor executor, HeuristicThreshold... thresholds) {
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
                        complete(HeuristicConjunctionEvent.this);
                    }
                } else if (mIsCondition.compareAndSet(true, false)) {
                    mNumEventsRemaining.incrementAndGet();
                }
            }
        }
    }

    public static class RaceEvent extends EngineEvent {
        private final AtomicBoolean mFinishLine = new AtomicBoolean();

        private RaceEvent(Executor executor, EngineEvent[] events) {
            super(executor);
            Arrays.stream(events).forEach(event -> event.onNextOccurrence(
                    unused -> {
                        if (mFinishLine.compareAndSet(false, true)) complete(event);
                    }));
        }
    }

    private final Executor mExecutor;

    EngineEventFactory(Executor executor) {
        mExecutor = executor;
    }

    /**
     * Create an {@link EngineEvent} that completes the next time the provided threshold is
     * satisfied.
     */
    HeuristicEvent when(HeuristicThreshold threshold) {
        return new HeuristicEvent(threshold, mExecutor);
    }

    /**
     * Create a {@link EngineEvent} that completes once all provided thresholds are passed in
     * parallel.
     */
    HeuristicConjunctionEvent whenAll(
            HeuristicThreshold t1, HeuristicThreshold t2, HeuristicThreshold... rest
    ) {
        return new HeuristicConjunctionEvent(
                mExecutor, ObjectArrays.concat(t1, ObjectArrays.concat(t2, rest)));
    }


    /**
     * Creates a {@link EngineEvent} that completes once any of the provided events complete.
     */
    RaceEvent whenAny(EngineEvent e1, EngineEvent e2, EngineEvent... rest) {
        return new RaceEvent(
                mExecutor, ObjectArrays.concat(e1, ObjectArrays.concat(e2, rest)));
    }
}
