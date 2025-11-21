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


import android.ranging.RangingData;

import androidx.annotation.NonNull;

import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.function.DoublePredicate;

/**
 * A a double value produced asynchronously from a continuous stream of ranging data.
 * {@link Listener}s can be registered to the heuristic which are notified when its value changes.
 */
public abstract class RangeHeuristic {

    public interface Listener {
        void onHeuristicUpdated(double value);
    }

    /** A heuristic paired with a condition. */
    public static class HeuristicThreshold {
        public final RangeHeuristic mHeuristic;
        public final DoublePredicate mCondition;

        /** Public constructor is {@link RangeHeuristic#threshold(DoublePredicate)} */
        protected HeuristicThreshold(RangeHeuristic heuristic, DoublePredicate condition) {
            mHeuristic = heuristic;
            mCondition = condition;
        }

        public RangeHeuristic getHeuristic() {
            return mHeuristic;
        }

        public DoublePredicate getCondition() {
            return mCondition;
        }
    }

    protected final CopyOnWriteArraySet<Listener> mListeners;
    protected final Executor mExecutor;

    public RangeHeuristic(Executor executor) {
        mListeners = new CopyOnWriteArraySet<>();
        mExecutor = executor;
    }

    /** Provide ranging data from which to calculate the heuristic. */
    public abstract void onData(@NonNull RangingData data);


    /** Group {@code this} heuristic with a predicate to represent a threshold. */
    public HeuristicThreshold threshold(DoublePredicate predicate) {
        return new HeuristicThreshold(this, predicate);
    }

    /** Register a listener to get notified when the heuristic's value is updated. */
    public void registerListener(Listener listener) {
        mListeners.add(listener);
    }

    /** Subclasses call this to notify listeners of a new value. */
    protected void onHeuristicUpdated(double value) {
        mListeners.forEach(l -> mExecutor.execute(() -> l.onHeuristicUpdated(value)));
    }
}
