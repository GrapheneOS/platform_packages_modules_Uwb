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

/**
 * Represents the notification of RF SR RX test in the UWB testing framework.
 */
public final class RfTestSrRxResult  extends RfTestParams{
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
    private static final String KEY_STS_FOUND = "sts_found";
    private static final String KEY_EOF = "eof";
    private static final String KEY_STS_DETECT_BITMAP = "sts_detect_bitmap_en";
    private static final String KEY_RAW_NTF_DATA = "raw_ntf_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";

    private final int mStatus;
    private final long mAttempts;
    private final long mAcqDetect;
    private final long mAcqReject;
    private final long mRxFail;
    private final long mSyncCirReady;
    private final long mSfdFail;
    private final long mSfdFound;
    private final long mStsFound;
    private final long mEof;
    private final byte[] mStsDetectBitmap;
    private final byte[] mRawNtfData;
    private final int mRfTestOperationType;

    private RfTestSrRxResult(Builder builder) {
        this.mStatus = builder.mStatus;
        this.mAttempts = builder.mAttempts;
        this.mAcqDetect = builder.mAcqDetect;
        this.mAcqReject = builder.mAcqReject;
        this.mRxFail = builder.mRxFail;
        this.mSyncCirReady = builder.mSyncCirReady;
        this.mSfdFail = builder.mSfdFail;
        this.mSfdFound = builder.mSfdFound;
        this.mStsFound = builder.mStsFound;
        this.mEof = builder.mEof;
        this.mStsDetectBitmap = builder.mStsDetectBitmap;
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
        bundle.putLong(KEY_ATTEMPTS, mAttempts);
        bundle.putLong(KEY_ACQ_DETECT, mAcqDetect);
        bundle.putLong(KEY_ACQ_REJECT, mAcqReject);
        bundle.putLong(KEY_RX_FAIL, mRxFail);
        bundle.putLong(KEY_SYNC_CIR_READY, mSyncCirReady);
        bundle.putLong(KEY_SFD_FAIL, mSfdFail);
        bundle.putLong(KEY_SFD_FOUND, mSfdFound);
        bundle.putLong(KEY_STS_FOUND, mStsFound);
        bundle.putLong(KEY_EOF, mEof);
        bundle.putIntArray(KEY_STS_DETECT_BITMAP, byteArrayToIntArray(mStsDetectBitmap));
        bundle.putIntArray(KEY_RAW_NTF_DATA, byteArrayToIntArray(mRawNtfData));
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestSrRxResult} */
    public static RfTestSrRxResult fromBundle(PersistableBundle bundle) {
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

    private static RfTestSrRxResult parseBundleVersion1(PersistableBundle bundle) {
        RfTestSrRxResult.Builder builder = new RfTestSrRxResult.Builder(
                bundle.getInt(KEY_RF_OPERATION_TYPE),
                bundle.getInt(KEY_STATUS_CODE),
                bundle.getLong(KEY_ATTEMPTS),
                bundle.getLong(KEY_ACQ_DETECT),
                bundle.getLong(KEY_ACQ_REJECT),
                bundle.getLong(KEY_RX_FAIL),
                bundle.getLong(KEY_SYNC_CIR_READY),
                bundle.getLong(KEY_SFD_FAIL),
                bundle.getLong(KEY_SFD_FOUND),
                bundle.getLong(KEY_STS_FOUND),
                bundle.getLong(KEY_EOF),
                intArrayToByteArray(bundle.getIntArray(KEY_STS_DETECT_BITMAP)),
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
     * Returns the status code of the received frame.
     * @return {@link StatusCode}
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * Returns the number of RX attempts.
     * @return Number of RX attempts.
     */
    public long getAttempts() {
        return mAttempts;
    }

    /**
     * Returns the number of acquisition detections.
     * @return Number of successful signal detections.
     */
    public long getAcqDetect() {
        return mAcqDetect;
    }

    /**
     * Returns the number of acquisition rejections.
     * @return Number of signal rejections.
     */
    public long getAcqReject() {
        return mAcqReject;
    }

    /**
     * Returns the number of RX failures.
     * @return Number of receive failures.
     */
    public long getRxFail() {
        return mRxFail;
    }

    /**
     * Returns the number of times the sync CIR was ready.
     * @return Number of sync CIR ready events.
     */
    public long getSyncCirReady() {
        return mSyncCirReady;
    }

    /**
     * Returns the number of SFD failures.
     * @return Number of SFD detection failures.
     */
    public long getSfdFail() {
        return mSfdFail;
    }

    /**
     * Returns the number of successful SFD detections.
     * @return Number of successful SFD detections.
     */
    public long getSfdFound() {
        return mSfdFound;
    }

    /**
     * Returns the number of successful STS detections.
     * @return Number of successful STS detections.
     */
    public long getStsFound() {
        return mStsFound;
    }

    /**
     * Returns the number of End-of-Frame (EOF) events.
     * @return Number of EOF events triggered.
     */
    public long getEof() {
        return mEof;
    }

    /**
     * Returns the STS detection bitmap.
     * @return Byte array representing STS detection bitmap.
     */
    @Nullable
    public byte[] getStsDetectBitmap() {
        return mStsDetectBitmap;
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
     * Builder for a {@link RfTestSrRxResult} object.
     */
    public static final class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private int mStatus = 0;
        private long mAttempts = 0;
        private long mAcqDetect = 0;
        private long mAcqReject = 0;
        private long mRxFail = 0;
        private long mSyncCirReady = 0;
        private long mSfdFail = 0;
        private long mSfdFound = 0;
        private long mStsFound = 0;
        private long mEof = 0;
        private byte[] mStsDetectBitmap = null;
        private byte[] mRawNtfData = null;

        public Builder(int rfTestOperationType, int status, long attempts, long acqDetect,
                long acqReject, long rxFail, long syncCirReady, long sfdFail, long sfdFound,
                long stsFound, long eof, byte[] stsDetectBitmap, byte[] rawNtfData) {
            this.mRfTestOperationType.set(rfTestOperationType);
            this.mStatus = status;
            this.mAttempts = attempts;
            this.mAcqDetect = acqDetect;
            this.mAcqReject = acqReject;
            this.mRxFail = rxFail;
            this.mSyncCirReady = syncCirReady;
            this.mSfdFail = sfdFail;
            this.mSfdFound = sfdFound;
            this.mStsFound = stsFound;
            this.mEof = eof;
            this.mStsDetectBitmap = stsDetectBitmap;
            this.mRawNtfData = rawNtfData;
        }

        /**
         * Build the {@link RfTestSrRxResult} object
         */
        public RfTestSrRxResult build() {
            return new RfTestSrRxResult(this);
        }
    }
}
