/*
 * Copyright 2024 The Android Open Source Project
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

import com.google.uwb.support.base.RequiredParam;

import android.os.PersistableBundle;

public final class RfTestPeriodicTxResult  extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private static final String KEY_STATUS_CODE = "status_code";
    public static final String RAW_NTF_DATA = "raw_ntf_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";
    private final int mRfTestOperationType;
    private final int mStatus;
    private final byte[] mRawNtfData;

    private RfTestPeriodicTxResult(int status, byte[] rawNtfData, int rfTestOperationType) {
        this.mStatus = status;
        this.mRawNtfData = rawNtfData;
        this.mRfTestOperationType = rfTestOperationType;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_STATUS_CODE, mStatus);
        bundle.putIntArray(RAW_NTF_DATA, byteArrayToIntArray(mRawNtfData));
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestPeriodicTxResult} */
    public static RfTestPeriodicTxResult fromBundle(PersistableBundle bundle) {
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

    private static RfTestPeriodicTxResult parseBundleVersion1(PersistableBundle bundle) {
        RfTestPeriodicTxResult.Builder builder = new RfTestPeriodicTxResult.Builder()
                .setStatus(bundle.getInt(KEY_STATUS_CODE))
                .setRawNtfData(intArrayToByteArray(bundle.getIntArray(RAW_NTF_DATA)))
                .setOperationType(bundle.getInt(KEY_RF_OPERATION_TYPE));
        return builder.build();
    }

    public int getStatus() {
        return mStatus;
    }

    /*
     * RfTestParams.RfTestOperationType defined as part of RfTest support lib
     * Possible values are:
     * - TEST_PERIODIC_TX = 0
     * - TEST_PER_RX = 1
     * - TEST_RX = 2
     * - TEST_LOOPBACK = 3
     * - TEST_SS_TWR = 4
     * - TEST_SR_RX = 5
     *
     * @return The RF test operation type.
     */
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    /**
     * Builder for a {@link RfTestPeriodicTxResult} object.
     */
    public static final class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private int mStatus;
        private byte[] mRawNtfData;

        public Builder setStatus(int status) {
            mStatus = status;
            return this;
        }

        public Builder setRawNtfData(byte[] rawNtfData) {
            mRawNtfData = rawNtfData;
            return this;
        }

        public Builder setOperationType(int rfTestOperationType) {
            mRfTestOperationType.set(rfTestOperationType);
            return this;
        }

        /**
         * Build the {@link RfTestPeriodicTxResult} object
         */
        public RfTestPeriodicTxResult build() {
            return new RfTestPeriodicTxResult(mStatus, mRawNtfData, mRfTestOperationType.get());
        }
    }
}
