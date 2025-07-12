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

package com.google.uwb.support.rftest;

import android.os.PersistableBundle;

import androidx.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.StatusCode;
import com.google.uwb.support.rftest.RfTestParams.RfTestOperationType;

/**
 * Represents the notification of RF SS TWR test in the UWB testing framework.
 */
public final class RfTestSsTwrResult  extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_STATUS_CODE = "status_code";
    private static final String KEY_MEASUREMENT = "measurement";
    private static final String KEY_RAW_NTF_DATA = "raw_ntf_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";

    private final int mStatus;
    private final long mMeasurement;
    private final byte[] mRawNtfData;
    private final int mRfTestOperationType;

    private RfTestSsTwrResult(Builder builder) {
        this.mStatus = builder.mStatus;
        this.mMeasurement = builder.mMeasurement;
        this.mRawNtfData = builder.mRawNtfData;
        this.mRfTestOperationType = builder.mRfTestOperationType.get();
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        bundle.putInt(KEY_STATUS_CODE, mStatus);
        bundle.putLong(KEY_MEASUREMENT, mMeasurement);
        bundle.putIntArray(KEY_RAW_NTF_DATA, byteArrayToIntArray(mRawNtfData));
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestSsTwrResult} */
    public static RfTestSsTwrResult fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static RfTestSsTwrResult parseBundleVersion1(PersistableBundle bundle) {
        RfTestSsTwrResult.Builder builder = new RfTestSsTwrResult.Builder(
                bundle.getInt(KEY_RF_OPERATION_TYPE),
                bundle.getInt(KEY_STATUS_CODE),
                bundle.getLong(KEY_MEASUREMENT),
                intArrayToByteArray(bundle.getIntArray(KEY_RAW_NTF_DATA)));
        return builder.build();
    }

    /**
     * Returns the RF Test Operation Type.
     * <p>This integer indicates the specific type of RF test operation being performed.
     * @return {@link RfTestOperationType} RF test operation type.
     */
    @RfTestOperationType
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    /**
     * Returns the {@link StatusCode} status code of the received frame.
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * Returns the RF test measurement value.
     *
     * @return The measurement value in 1 / (128 × 499.2 MHz) tick units.
     */
    public long getMeasurement() {
        return mMeasurement;
    }

    /**
     * Returns the raw notification data.
     * @return Raw byte array of the received notification.
     */
    @Nullable
    public byte[] getRawNotificationData() {
        return mRawNtfData;
    }

    /**
     * Builder for a {@link RfTestSsTwrResult} object.
     */
    public static final class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private int mStatus;
        private long mMeasurement = 0;
        private byte[] mRawNtfData = null;

        public Builder(int rfOperationType, int status, long measurement, byte[] rawNtfData) {
            this.mRfTestOperationType.set(rfOperationType);
            this.mStatus = status;
            this.mMeasurement = measurement;
            this.mRawNtfData = rawNtfData;
        }

        /**
         * Build the {@link RfTestSsTwrResult} object
         */
        public RfTestSsTwrResult build() {
            return new RfTestSsTwrResult(this);
        }
    }
}
