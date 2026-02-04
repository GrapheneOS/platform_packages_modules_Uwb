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

import android.app.AlarmManager;
import android.app.AlarmManager.OnAlarmListener;
import android.os.SystemClock;
import android.ranging.RangingData;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.heuristic.RangeHeuristic.HeuristicThreshold;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.util.concurrent.AtomicDouble;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleUnaryOperator;


/**
 * Manages one or more {@link Streak} heuristics that track the same technology and therefore share
 * a ranging interval. The technology and interval are determined from the provided
 * {@link TechnologyConfig}.
 */
public class StreakCounter {

    /**
     * A {@link RangeHeuristic} that counts consecutive success and failure streaks as determined by
     * a threshold. That is, on each ranging interval, if the threshold is passed the success streak
     * is incremented, if the threshold is not passed the failure streak is incremented.
     * <p>
     * Values of the {@link Streak} heuristic are always whole numbers, where a positive value
     * indicates a success streak and a negative value indicates a failure streak.
     */
    public static class Streak extends RangeHeuristic {
        private final RangeHeuristic mHeuristic;
        private final AtomicDouble mStreak = new AtomicDouble(0);

        private Streak(HeuristicThreshold threshold, Executor executor) {
            super(executor);
            mHeuristic = threshold.getHeuristic();

            mHeuristic.registerListener(value -> {
                if (threshold.getCondition().test(value)) {
                    onHeuristicUpdated(nextStreak(Streak::success));
                } else {
                    onHeuristicUpdated(nextStreak(Streak::failure));
                }
            });
        }

        private Streak(RangeHeuristic heuristic, Executor executor) {
            super(executor);
            mHeuristic = heuristic;
            mHeuristic.registerListener(unused -> onHeuristicUpdated(nextStreak(Streak::success)));
        }

        @Override
        public void onData(@NonNull RangingData data) {
            mHeuristic.onData(data);
        }

        /**
         * Updates {@code mStreak} to the next streak value using a lock-free atomic operation.
         * @param op function that computes the next streak value from the current
         * @return the next streak value
         */
        private double nextStreak(DoubleUnaryOperator op) {
            double current;
            double next;
            do {
                current = mStreak.get();
                next = op.applyAsDouble(current);
            } while (!mStreak.compareAndSet(current, next));
            return next;
        }

        private static double success(double streak) {
            return Math.max(1, streak + 1);
        }

        private static double failure(double streak) {
            return Math.min(-1, streak - 1);
        }
    }

    private final long mFailureTimeoutMs;
    private final TechnologyConfig mConfig;
    private final AtomicBoolean mIsListeningForFailure = new AtomicBoolean(false);
    private final AlarmManager mAlarmManager;
    private final Executor mExecutor;
    private final RangingInjector mInjector;
    private final CopyOnWriteArrayList<Streak> mStreaks;
    private final OnAlarmListener mFailureListener = new OnAlarmListener() {
        @Override
        public void onAlarm() {
            mStreaks.forEach(
                    streak -> streak.onHeuristicUpdated(streak.nextStreak(Streak::failure)));
        }
    };

    public StreakCounter(TechnologyConfig config, Executor executor, RangingInjector injector) {
        mFailureTimeoutMs = config.getRangingInterval().toMillis();
        mConfig = config;
        mAlarmManager = injector.getContext().getSystemService(AlarmManager.class);
        mExecutor = executor;
        mInjector = injector;
        mStreaks = new CopyOnWriteArrayList<>();
    }

    /** Create a {@link Streak} heuristic that counts success and failure streaks. */
    public Streak count() {
        Streak count = new Streak(new RawRangeMeters(mExecutor), mExecutor);
        mStreaks.add(count);
        return count;
    }

    /**
     * Create a {@link Streak} heuristic that counts success and failure streaks of a heuristic.
     * The failure streak is incremented only when the heuristic fails to produce a value within
     * the ranging interval.
     */
    public Streak count(RangeHeuristic heuristic) {
        Streak count = new Streak(heuristic, mExecutor);
        mStreaks.add(count);
        return count;
    }

    /**
     * Create a {@link Streak} heuristic that counts success and failure streaks of the inner
     * heuristic value as determined by the threshold's condition.
     */
    public Streak count(HeuristicThreshold threshold) {
        Streak count = new Streak(threshold, mExecutor);
        mStreaks.add(count);
        return count;
    }

    /** Provide ranging data to pass to the {@link Streak} heuristics managed by this counter. */
    public void onData(@NonNull RangingData data) {
        if (data.getRangingTechnology() != mConfig.getTechnology().getValue()) return;

        stopFailureListener();
        mStreaks.forEach(count -> count.onData(data));
        startFailureListener();
    }

    private void stopFailureListener() {
        if (mIsListeningForFailure.compareAndSet(true, false)) {
            mAlarmManager.cancel(mFailureListener);
        }
    }

    private void startFailureListener() {
        if (mIsListeningForFailure.compareAndSet(false, true)) {
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + mFailureTimeoutMs, null, mFailureListener,
                    mInjector.getAlarmHandler());
        }
    }
}
