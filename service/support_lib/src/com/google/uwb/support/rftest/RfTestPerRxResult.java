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

public final class RfTestPerRxResult extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private static final String KEY_STATUS_CODE = "status_code";
    private static final String KEY_ATTEMPTS = "attempts";
    private static final String KEY_ACQ_DETECT = "acq_detect";
    private static final String KEY_ACQ_REJECT = "acq_reject";
    private static final String KEY_RX_FAIL = "rx_fail";
    private static final String KEY_SYNC_CIR_READY = "sync_cir_ready";
    private static final String KEY_SFD_FAIL = "sfd_fail";
    private static final String KEY_SFD_FOUND = "sfd_found";
    private static final String KEY_PHR_DEC_ERROR = "phr_dec_error";
    private static final String KEY_PHR_BIT_ERROR = "phr_bit_error";
    private static final String KEY_PSDU_DEC_ERROR = "psdu_dec_error";
    private static final String KEY_PSDU_BIT_ERROR = "psdu_bit_error";
    private static final String KEY_STS_FOUND = "sts_found";
    private static final String KEY_EOF = "eof";
    public static final String RAW_NTF_DATA = "raw_ntf_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";
    private final int mRfTestOperationType;
    private final int mStatus;
    private final long mAttempts;
    private final long mAcqDetect;
    private final long mAcqReject;
    private final long mRxFail;
    private final long mSyncCirReady;
    private final long mSfdFail;
    private final long mSfdFound;
    private final long mPhrDecError;
    private final long mPhrBitError;
    private final long mPsduDecError;
    private final long mPsduBitError;
    private final long mStsFound;
    private final long mEof;
    private final byte[] mRawNtfData;

    private RfTestPerRxResult(int status, long attempts, long acqDetect, long acqReject,
                              long rxFail,  long syncCirReady, long sfdFail, long sfdFound,
                              long phrDecError, long phrBitError,
                              long psduDecError, long psduBitError, long stsFound, long eof,
                              byte[] rawNtfData, int rfTestOperationType) {
        this.mStatus = status;
        this.mAttempts = attempts;
        this.mAcqDetect = acqDetect;
        this.mAcqReject = acqReject;
        this.mRxFail = rxFail;
        this.mSyncCirReady = syncCirReady;
        this.mSfdFail = sfdFail;
        this.mSfdFound = sfdFound;
        this.mPhrDecError = phrDecError;
        this.mPhrBitError = phrBitError;
        this.mPsduDecError = psduDecError;
        this.mPsduBitError = psduBitError;
        this.mStsFound = stsFound;
        this.mEof = eof;
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
        bundle.putLong(KEY_ATTEMPTS, mAttempts);
        bundle.putLong(KEY_ACQ_DETECT, mAcqDetect);
        bundle.putLong(KEY_ACQ_REJECT, mAcqReject);
        bundle.putLong(KEY_RX_FAIL, mRxFail);
        bundle.putLong(KEY_SYNC_CIR_READY, mSyncCirReady);
        bundle.putLong(KEY_SFD_FAIL, mSfdFail);
        bundle.putLong(KEY_SFD_FOUND, mSfdFound);
        bundle.putLong(KEY_PHR_DEC_ERROR, mPhrDecError);
        bundle.putLong(KEY_PHR_BIT_ERROR, mPhrBitError);
        bundle.putLong(KEY_PSDU_DEC_ERROR, mPsduDecError);
        bundle.putLong(KEY_PSDU_BIT_ERROR, mPsduBitError);
        bundle.putLong(KEY_STS_FOUND, mStsFound);
        bundle.putLong(KEY_EOF, mEof);
        bundle.putIntArray(RAW_NTF_DATA, byteArrayToIntArray(mRawNtfData));
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestPerRxResult} */
    public static RfTestPerRxResult fromBundle(PersistableBundle bundle) {
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

    private static RfTestPerRxResult parseBundleVersion1(PersistableBundle bundle) {
        RfTestPerRxResult.Builder builder = new RfTestPerRxResult.Builder()
                .setStatus(bundle.getInt(KEY_STATUS_CODE))
                .setAttempts(bundle.getLong(KEY_ATTEMPTS))
                .setAcqDetect(bundle.getLong(KEY_ACQ_DETECT))
                .setAcqReject(bundle.getLong(KEY_ACQ_REJECT))
                .setRxFail(bundle.getLong(KEY_RX_FAIL))
                .setSyncCirReady(bundle.getLong(KEY_SYNC_CIR_READY))
                .setSfdFail(bundle.getLong(KEY_SFD_FAIL))
                .setSfdFound(bundle.getLong(KEY_SFD_FOUND))
                .setPhrDecError(bundle.getLong(KEY_PHR_DEC_ERROR))
                .setPhrBitError(bundle.getLong(KEY_PHR_BIT_ERROR))
                .setPsduDecError(bundle.getLong(KEY_PSDU_DEC_ERROR))
                .setPsduBitError(bundle.getLong(KEY_PSDU_BIT_ERROR))
                .setStsFound(bundle.getLong(KEY_STS_FOUND))
                .setEof(bundle.getLong(KEY_EOF))
                .setRawNtfData(intArrayToByteArray(bundle.getIntArray(RAW_NTF_DATA)))
                .setOperationType(bundle.getInt(KEY_RF_OPERATION_TYPE));
        return builder.build();
    }

    public int getStatus() {
        return mStatus;
    }

    public long getAttempts() {
        return mAttempts;
    }

    public long getAcqDetect() {
        return mAcqDetect;
    }

    public long getAcqReject() {
        return mAcqReject;
    }

    public long getRxFail() {
        return mRxFail;
    }

    public long getSyncCirReady() {
        return mSyncCirReady;
    }

    public long getSfdFail() {
        return mSfdFail;
    }

    public long getSfdFound() {
        return mSfdFound;
    }

    public long getPhrDecError() {
        return mPhrDecError;
    }

    public long getPhrBitError() {
        return mPhrBitError;
    }

    public long getPsduDecError() {
        return mPsduDecError;
    }

    public long getPsduBitError() {
        return mPsduBitError;
    }

    public long getStsFound() {
        return mStsFound;
    }

    public long getEof() {
        return mEof;
    }

    public byte[] getRawNtfData() {
        return mRawNtfData;
    }

    @RfTestOperationType
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    /**
     * Builder for a {@link RfTestPerRxResult} object.
     */
    public static final class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private int mStatus;
        private long mAttempts;
        private long mAcqDetect;
        private long mAcqReject;
        private long mRxFail;
        private long mSyncCirReady;
        private long mSfdFail;
        private long mSfdFound;
        private long mPhrDecError;
        private long mPhrBitError;
        private long mPsduDecError;
        private long mPsduBitError;
        private long mStsFound;
        private long mEof;
        private byte[] mRawNtfData;

        public Builder setStatus(int status) {
            mStatus = status;
            return this;
        }

        public Builder setAttempts(long attempts) {
            mAttempts = attempts;
            return this;
        }

        public Builder setAcqDetect(long acqDetect) {
            mAcqDetect = acqDetect;
            return this;
        }

        public Builder setAcqReject(long acqReject) {
            mAcqReject = acqReject;
            return this;
        }

        public Builder setRxFail(long rxFail) {
            mRxFail = rxFail;
            return this;
        }

        public Builder setSyncCirReady(long syncCirReady) {
            mSyncCirReady = syncCirReady;
            return this;
        }

        public Builder setSfdFail(long sfdFail) {
            mSfdFail = sfdFail;
            return this;
        }

        public Builder setSfdFound(long sfdFound) {
            mSfdFound = sfdFound;
            return this;
        }

        public Builder setPhrDecError(long phrDecError) {
            mPhrDecError = phrDecError;
            return this;
        }

        public Builder setPhrBitError(long phrBitError) {
            mPhrBitError = phrBitError;
            return this;
        }

        public Builder setPsduDecError(long psduDecError) {
            mPsduDecError = psduDecError;
            return this;
        }

        public Builder setPsduBitError(long psduBitError) {
            mPsduBitError = psduBitError;
            return this;
        }

        public Builder setStsFound(long stsFound) {
            mStsFound = stsFound;
            return this;
        }

        public Builder setEof(long eof) {
            mEof = eof;
            return this;
        }

        public Builder setRawNtfData(byte[] rawNtfData) {
            mRawNtfData = rawNtfData;
            return this;
        }

        public Builder setOperationType(@RfTestOperationType int rfTestOperationType) {
            mRfTestOperationType.set(rfTestOperationType);
            return this;
        }

        /**
         * Build the {@link RfTestPerRxResult} object
         */
        public RfTestPerRxResult build() {
            return new RfTestPerRxResult(mStatus, mAttempts, mAcqDetect, mAcqReject,
                    mRxFail, mSyncCirReady, mSfdFail, mSfdFound, mPhrDecError, mPhrBitError,
                    mPsduDecError, mPsduBitError, mStsFound, mEof, mRawNtfData, mRfTestOperationType.get());
        }
    }
}
