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
import androidx.annotation.Nullable;

import java.util.concurrent.Executor;

/** A {@link RangeHeuristic} that estimates the derivative of the range. */
public class DerivativeEstimator extends RangeHeuristic {
    @Nullable volatile RangingData mLastData = null;
    private final ExpWeightedMovingAvg mAvg;

    public DerivativeEstimator(double recencyBias, Executor executor) {
        super(executor);
        mAvg = new ExpWeightedMovingAvg(recencyBias);
    }

    @Override
    public void onData(@NonNull RangingData data) {
        if (mLastData != null) {
            double slope =
                    (data.getDistance().getMeasurement() - mLastData.getDistance().getMeasurement())
                    / (data.getTimestampMillis() - mLastData.getTimestampMillis());
            if (!Double.isNaN(slope) && Math.abs(slope) >= 0.0001 /* 10 cm/s */) {
                onHeuristicUpdated(mAvg.next(slope));
            }
        }
        mLastData = data;
    }

    private static class ExpWeightedMovingAvg {
        private final double mRecencyBias;
        private double mPrev = Double.NaN;

        ExpWeightedMovingAvg(double recencyBias) {
            mRecencyBias = recencyBias;
        }

        double next(double value) {
            if (Double.isNaN(mPrev)) {
                mPrev = value;
            } else {
                mPrev = (mRecencyBias * value) + ((1 - mRecencyBias) * mPrev);
            }
            return mPrev;
        }
    }
}
