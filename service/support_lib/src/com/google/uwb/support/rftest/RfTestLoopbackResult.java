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

import android.os.PersistableBundle;

import com.google.uwb.support.base.RequiredParam;

public final class RfTestLoopbackResult extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private static final String KEY_STATUS_CODE = "status_code";
    private static final String KEY_TX_TS_INT = "tx_ts_int";
    private static final String KEY_TX_TS_FRAC = "tx_ts_frac";
    private static final String KEY_RX_TS_INT = "rx_ts_int";
    private static final String KEY_RX_TS_FRAC = "rx_ts_frac";
    private static final String KEY_AOA_AZIMUTH = "aoa_azimuth";
    private static final String KEY_AOA_ELEVATION = "aoa_elevation";
    private static final String KEY_PHR = "phr";
    private static final String KEY_PSDU_DATA = "psdu_data";
    public static final String RAW_NTF_DATA = "raw_ntf_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";
    private final int mRfTestOperationType;
    private final int mStatus;
    private final long mTxTsInt;
    private final int mTxTsFrac;
    private final long mRxTsInt;
    private final int mRxTsFrac;
    private final int mAoaAzimuth;
    private final int mAoaElevation;
    private final int mPhr;
    private final byte[] mPsduData;
    private final byte[] mRawNtfData;

    public RfTestLoopbackResult(int status, long txTsInt, int txTsFrac, long rxTsInt,
                                 int rxTsFrac, int aoaAzimuth, int aoaElevation, int phr,
                                 byte[] psduData, byte[] rawNtfData, int rfTestOperationType) {
        this.mStatus = status;
        this.mTxTsInt = txTsInt;
        this.mTxTsFrac = txTsFrac;
        this.mRxTsInt = rxTsInt;
        this.mRxTsFrac = rxTsFrac;
        this.mAoaAzimuth = aoaAzimuth;
        this.mAoaElevation = aoaElevation;
        this.mPhr = phr;
        this.mPsduData = psduData;
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
        bundle.putLong(KEY_TX_TS_INT, mTxTsInt);
        bundle.putInt(KEY_TX_TS_FRAC, mTxTsFrac);
        bundle.putLong(KEY_RX_TS_INT, mRxTsInt);
        bundle.putInt(KEY_RX_TS_FRAC, mRxTsFrac);
        bundle.putInt(KEY_AOA_AZIMUTH, mAoaAzimuth);
        bundle.putInt(KEY_AOA_ELEVATION, mAoaElevation);
        bundle.putInt(KEY_PHR, mPhr);
        bundle.putIntArray(KEY_PSDU_DATA, byteArrayToIntArray(mPsduData));
        bundle.putIntArray(RAW_NTF_DATA, byteArrayToIntArray(mRawNtfData));
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestLoopbackResult} */
    public static RfTestLoopbackResult fromBundle(PersistableBundle bundle) {
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

    private static RfTestLoopbackResult parseBundleVersion1(PersistableBundle bundle) {
        RfTestLoopbackResult.Builder builder = new RfTestLoopbackResult.Builder()
                .setStatus(bundle.getInt(KEY_STATUS_CODE))
                .setTxTsInt(bundle.getLong(KEY_TX_TS_INT))
                .setTxTsFrac(bundle.getInt(KEY_TX_TS_FRAC))
                .setRxTsInt(bundle.getLong(KEY_RX_TS_INT))
                .setRxTsFrac(bundle.getInt(KEY_RX_TS_FRAC))
                .setAoaAzimuth(bundle.getInt(KEY_AOA_AZIMUTH))
                .setAoaElevation(bundle.getInt(KEY_AOA_ELEVATION))
                .setPhr(bundle.getInt(KEY_PHR))
                .setPsduData(intArrayToByteArray(bundle.getIntArray(KEY_PSDU_DATA)))
                .setRawNtfData(intArrayToByteArray(bundle.getIntArray(RAW_NTF_DATA)))
                .setOperationType(bundle.getInt(KEY_RF_OPERATION_TYPE));
        return builder.build();
    }

    public int getStatus() {
        return mStatus;
    }

    public long getTxTsInt() {
        return mTxTsInt;
    }

    public int getTxTsFrac() {
        return mTxTsFrac;
    }

    public long getRxTsInt() {
        return mRxTsInt;
    }

    public int getRxTsFrac() {
        return mRxTsFrac;
    }

    public int getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public int getAoaElevation() {
        return mAoaElevation;
    }

    public int getPhr() {
        return mPhr;
    }

    public byte[] getPsduData() {
        return mPsduData;
    }

    public byte[] getRawNtfData() {
        return mRawNtfData;
    }

    @RfTestOperationType
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    /**
     * Builder for a {@link RfTestLoopbackResult} object.
     */
    public static final class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private int mStatus;
        private long mTxTsInt;
        private int mTxTsFrac;
        private long mRxTsInt;
        private int mRxTsFrac;
        private int mAoaAzimuth;
        private int mAoaElevation;
        private int mPhr;
        private byte[] mPsduData;
        private byte[] mRawNtfData;

        public Builder setStatus(int status) {
            mStatus = status;
            return this;
        }

        public Builder setTxTsInt(long txTsInt) {
            this.mTxTsInt = txTsInt;
            return this;
        }

        public Builder setTxTsFrac(int txTsFrac) {
            this.mTxTsFrac = txTsFrac;
            return this;
        }

        public Builder setRxTsInt(long rxTsInt) {
            this.mRxTsInt = rxTsInt;
            return this;
        }

        public Builder setRxTsFrac(int rxTsFrac) {
            this.mRxTsFrac = rxTsFrac;
            return this;
        }

        public Builder setAoaAzimuth(int aoaAzimuth) {
            this.mAoaAzimuth = aoaAzimuth;
            return this;
        }

        public Builder setAoaElevation(int aoaElevation) {
            this.mAoaElevation = aoaElevation;
            return this;
        }

        public Builder setPhr(int phr) {
            this.mPhr = phr;
            return this;
        }

        public Builder setPsduData(byte[] psduData) {
            this.mPsduData = psduData;
            return this;
        }

        public Builder setRawNtfData(byte[] rawNtfData) {
            this.mRawNtfData = rawNtfData;
            return this;
        }

        public Builder setOperationType(@RfTestOperationType int rfTestOperationType) {
            mRfTestOperationType.set(rfTestOperationType);
            return this;
        }

        /**
         * Build the {@link RfTestLoopbackResult} object
         */
        public RfTestLoopbackResult build() {
            return new RfTestLoopbackResult(mStatus, mTxTsInt, mTxTsFrac, mRxTsInt,
                    mRxTsFrac, mAoaAzimuth, mAoaElevation, mPhr, mPsduData,
                    mRawNtfData, mRfTestOperationType.get());
        }
    }
}
