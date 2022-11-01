/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.uwb.advertisement;

import static com.android.server.uwb.util.DataTypeConversionUtil.byteArrayToI16;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UwbAdvertiseManager {
    private static final String TAG = "UwbAdvertiseManager";

    private final ConcurrentHashMap<Integer, UwbAdvertiseTarget> mAdvertiseTargetMap =
            new ConcurrentHashMap<>();

    // TODO(b/246678053): Use overlays to allow OEMs to modify these values.
    @VisibleForTesting public static final int CRITERIA_ANGLE = 10;
    @VisibleForTesting public static final int TIME_THRESHOLD = 5000;
    @VisibleForTesting public static final int SIZE_OF_ARRAY_TO_CHECK = 10;
    private static final int START_INDEX_TO_CAL_VARIANCE = 2;
    private static final int END_INDEX_TO_CAL_VARIANCE = 8;
    @VisibleForTesting public static final int TRUSTED_VALUE_OF_VARIANCE = 5;

    private final UwbInjector mUwbInjector;

    public UwbAdvertiseManager(UwbInjector uwbInjector) {
        this.mUwbInjector = uwbInjector;
    }

    /**
     * Check if the current device is pointing at the remote device, from which we have received
     * One-way Ranging AoA Measurement(s).
     */
    public boolean isPointedTarget(byte[] macAddress) {
        UwbAdvertiseTarget uwbAdvertiseTarget = getAdvertiseTarget(byteArrayToI16(macAddress));
        if (uwbAdvertiseTarget == null) {
            return false;
        }

        if (!uwbAdvertiseTarget.isWithinCriterionAngle()) {
            return false;
        }
        if (!isWithinCriterionVariance(uwbAdvertiseTarget)) {
            return false;
        }

        if (!isWithinTimeThreshold(uwbAdvertiseTarget)) {
            return false;
        }
        return true;
    }

    /**
     * Store a One-way Ranging AoA Measurement from the remote device in a UWB ranging session.
     */
    public void updateAdvertiseTarget(UwbOwrAoaMeasurement uwbOwrAoaMeasurement) {
        updateAdvertiseTargetInfo(uwbOwrAoaMeasurement);
    }

    private boolean isWithinCriterionVariance(UwbAdvertiseTarget uwbAdvertiseTarget) {
        if (!uwbAdvertiseTarget.isVarianceCalculated()) {
            return false;
        }

        if (uwbAdvertiseTarget.getVarianceOfAzimuth() > TRUSTED_VALUE_OF_VARIANCE) {
            return false;
        }

        if (uwbAdvertiseTarget.getVarianceOfElevation() > TRUSTED_VALUE_OF_VARIANCE) {
            return false;
        }
        return true;
    }

    private boolean isWithinTimeThreshold(UwbAdvertiseTarget uwbAdvertiseTarget) {
        long currentTime = mUwbInjector.getElapsedSinceBootMillis();
        if (currentTime - uwbAdvertiseTarget.getLastUpdatedTime() > TIME_THRESHOLD) {
            return false;
        }
        return true;
    }

    private UwbAdvertiseTarget updateAdvertiseTargetInfo(
            UwbOwrAoaMeasurement uwbOwrAoaMeasurement) {
        long currentTime = mUwbInjector.getElapsedSinceBootMillis();
        ByteBuffer byteBuffer = ByteBuffer.wrap(uwbOwrAoaMeasurement.getMacAddress());
        int macAddress = (int) byteBuffer.getShort();

        UwbAdvertiseTarget advertiseTarget = getOrAddAdvertiseTarget(macAddress);
        advertiseTarget.calculateAoaVariance(uwbOwrAoaMeasurement);
        advertiseTarget.updateLastMeasuredTime(currentTime);

        return advertiseTarget;
    }

    @VisibleForTesting
    @Nullable
    public UwbAdvertiseTarget getAdvertiseTarget(int macAddress) {
        return isAdvertiseTargetExist(macAddress) ? mAdvertiseTargetMap.get(macAddress) : null;
    }

    private UwbAdvertiseTarget getOrAddAdvertiseTarget(int macAddress) {
        UwbAdvertiseTarget uwbAdvertiseTarget;
        if (isAdvertiseTargetExist(macAddress)) {
            uwbAdvertiseTarget = mAdvertiseTargetMap.get(macAddress);
        } else {
            uwbAdvertiseTarget = addAdvertiseTarget(macAddress);
        }
        return uwbAdvertiseTarget;
    }

    private boolean isAdvertiseTargetExist(int macAddress) {
        return mAdvertiseTargetMap.containsKey(macAddress);
    }

    private UwbAdvertiseTarget addAdvertiseTarget(int macAddress) {
        UwbAdvertiseTarget advertiseTarget = new UwbAdvertiseTarget(macAddress);
        mAdvertiseTargetMap.put(macAddress, advertiseTarget);
        return advertiseTarget;
    }

    private static class UwbAdvertiseTarget {
        private final int mMacAddress;
        private final ArrayList<Double> mRecentAoaAzimuth = new ArrayList<>();
        private final ArrayList<Double> mRecentAoaElevation = new ArrayList<>();
        private double mVarianceOfAzimuth;
        private double mVarianceOfElevation;
        private long mLastMeasuredTime;
        private boolean mIsVarianceCalculated;

        private UwbAdvertiseTarget(int macAddress) {
            mMacAddress = macAddress;
            mIsVarianceCalculated = false;
        }

        private void calculateAoaVariance(UwbOwrAoaMeasurement owrAoaMeasurement) {
            double aoaAzimuth = owrAoaMeasurement.getAoaAzimuth();
            double aoaElevation = owrAoaMeasurement.getAoaElevation();

            mRecentAoaAzimuth.add(aoaAzimuth);
            mRecentAoaElevation.add(aoaElevation);

            if (mRecentAoaAzimuth.size() > SIZE_OF_ARRAY_TO_CHECK) {
                mRecentAoaAzimuth.remove(0);
                mRecentAoaElevation.remove(0);
            }

            if (mRecentAoaAzimuth.size() == SIZE_OF_ARRAY_TO_CHECK) {
                double[] azimuthArr =
                        mRecentAoaAzimuth
                                .subList(START_INDEX_TO_CAL_VARIANCE, END_INDEX_TO_CAL_VARIANCE)
                                .stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray();
                mVarianceOfAzimuth = getVariance(azimuthArr);
                double[] elevationArr =
                        mRecentAoaElevation
                                .subList(START_INDEX_TO_CAL_VARIANCE, END_INDEX_TO_CAL_VARIANCE)
                                .stream()
                                .mapToDouble(Double::doubleValue)
                                .toArray();
                mVarianceOfElevation = getVariance(elevationArr);
                mIsVarianceCalculated = true;
            } else {
                mIsVarianceCalculated = false;
            }
        }

        private boolean isWithinCriterionAngle(double aoa) {
            return Math.abs(aoa) <= CRITERIA_ANGLE;
        }

        private boolean isWithinCriterionAngle() {
            Optional<Double> outsideCriterionAngle;

            // Check if any stored AoaAzimuth value is outside the criterion angle range.
            outsideCriterionAngle = mRecentAoaAzimuth.stream()
                    .filter(x -> !isWithinCriterionAngle(x))
                    .findFirst();
            if (outsideCriterionAngle.isPresent()) return false;

            // Check if any stored AoaElevation value is outside the criterion angle range.
            outsideCriterionAngle = mRecentAoaElevation.stream()
                    .filter(x -> !isWithinCriterionAngle(x))
                    .findFirst();
            return !outsideCriterionAngle.isPresent();
        }

        // TODO(b/246678053): Can we receive measurements that are out of order (in terms of the
        // timestamp) ? In that case we should store the largest timestamp, not the latest, as this
        // stored value is used later for validating the measurements are within the time threshold.
        private void updateLastMeasuredTime(long time) {
            mLastMeasuredTime = time;
        }

        private double getVarianceOfAzimuth() {
            return mVarianceOfAzimuth;
        }

        private double getVarianceOfElevation() {
            return mVarianceOfElevation;
        }

        private long getLastUpdatedTime() {
            return mLastMeasuredTime;
        }

        @VisibleForTesting
        public boolean isVarianceCalculated() {
            return mIsVarianceCalculated;
        }

        private double getAverage(double[] array) {
            double sum = 0.0;

            for (int i = 0; i < array.length; i++) sum += array[i];

            return sum / array.length;
        }

        private double getVariance(double[] array) {
            if (array.length < 2) return Double.NaN;

            double sum = 0.0;
            double ret;
            double avg = getAverage(array);

            for (int i = 0; i < array.length; i++) {
                sum += Math.pow(array[i] - avg, 2);
            }
            ret = (double) sum / array.length;

            return ret;
        }

        @Override
        public String toString() {
            return " mMacAddress : "
                    + mMacAddress
                    + ", mVarOfAzimuth : "
                    + mVarianceOfAzimuth
                    + ", mVarOfElevation : "
                    + mVarianceOfElevation
                    + ", mRecentAoaAzimuth : "
                    + mRecentAoaAzimuth
                    + ", mRecentAoaElevation : "
                    + mRecentAoaElevation;
        }
    }
}
