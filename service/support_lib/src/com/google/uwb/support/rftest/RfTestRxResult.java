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

/**
 * Represents the notification of RF RX test in the UWB testing framework.
 */
public final class RfTestRxResult  extends RfTestParams{
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_STATUS_CODE = "status_code";
    private static final String KEY_RX_DONE_TIMESTAMP_INT = "rx_done_timestamp_int";
    private static final String KEY_RX_DONE_TIMESTAMP_FRAC = "rx_done_timestamp_frac";
    private static final String KEY_AOA_AZIMUTH = "aoa_azimuth";
    private static final String KEY_AOA_ELEVATION = "aoa_elevation";
    private static final String KEY_TOA_GAP = "toa_gap";
    private static final String KEY_PHR = "phr";
    private static final String KEY_PSDU_DATA = "psdu_data";
    private static final String KEY_RAW_NOTIFICATION_DATA = "raw_notification_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";

    private final int mStatus;
    private final long mRxDoneTimestampInt;
    private final int mRxDoneTimestampFrac;
    private final double mAoaAzimuth;
    private final double mAoaElevation;
    private final int mToaGap;
    private final int mPhr;
    private final byte[] mPsduData;
    private final byte[] mRawNotificationData;
    private final int mRfTestOperationType;

    private RfTestRxResult(Builder builder) {
        this.mStatus = builder.mStatus;
        this.mRxDoneTimestampInt = builder.mRxDoneTimestampInt;
        this.mRxDoneTimestampFrac = builder.mRxDoneTimestampFrac;
        this.mAoaAzimuth = builder.mAoaAzimuth;
        this.mAoaElevation = builder.mAoaElevation;
        this.mToaGap = builder.mToaGap;
        this.mPhr = builder.mPhr;
        this.mPsduData = builder.mPsduData;
        this.mRawNotificationData = builder.mRawNotificationData;
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
        bundle.putLong(KEY_RX_DONE_TIMESTAMP_INT, mRxDoneTimestampInt);
        bundle.putInt(KEY_RX_DONE_TIMESTAMP_FRAC, mRxDoneTimestampFrac);
        bundle.putDouble(KEY_AOA_AZIMUTH, mAoaAzimuth);
        bundle.putDouble(KEY_AOA_ELEVATION, mAoaElevation);
        bundle.putInt(KEY_TOA_GAP, mToaGap);
        bundle.putInt(KEY_PHR, mPhr);
        bundle.putIntArray(KEY_PSDU_DATA, byteArrayToIntArray(mPsduData));
        bundle.putIntArray(KEY_RAW_NOTIFICATION_DATA, byteArrayToIntArray(mRawNotificationData));
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestRxResult} */
    public static RfTestRxResult fromBundle(PersistableBundle bundle) {
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

    private static RfTestRxResult parseBundleVersion1(PersistableBundle bundle) {
        RfTestRxResult.Builder builder = new RfTestRxResult.Builder(
                bundle.getInt(KEY_RF_OPERATION_TYPE),
                bundle.getInt(KEY_STATUS_CODE),
                bundle.getLong(KEY_RX_DONE_TIMESTAMP_INT),
                bundle.getInt(KEY_RX_DONE_TIMESTAMP_FRAC),
                bundle.getDouble(KEY_AOA_AZIMUTH),
                bundle.getDouble(KEY_AOA_ELEVATION),
                bundle.getInt(KEY_TOA_GAP),
                bundle.getInt(KEY_PHR),
                intArrayToByteArray(bundle.getIntArray(KEY_PSDU_DATA)),
                intArrayToByteArray(bundle.getIntArray(KEY_RAW_NOTIFICATION_DATA)));
        return builder.build();
    }

    /**
     * Returns the RF Test Operation Type.
     * <p>This integer indicates the specific type of RF test operation being performed.
     * @return RF test operation type.
     */
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    /**
     * Returns the status code of the received frame.
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * Returns the integer part of the RX_DONE timestamp.
     */
    public long getRxDoneTimestampInteger() {
        return mRxDoneTimestampInt;
    }

    /**
     * Returns the fractional part of the RX_DONE timestamp.
     */
    public int getRxDoneTimestampFractional() {
        return mRxDoneTimestampFrac;
    }

    /**
     * Returns the angle of arrival (AoA) azimuth value.
     */
    public double getAoaAzimuth() {
        return mAoaAzimuth;
    }

    /**
     * Returns the angle of arrival (AoA) elevation value.
     *
     */
    public double getAoaElevation() {
        return mAoaElevation;
    }

    /**
     * Returns the Time of Arrival (ToA) gap between the main path and the first path.
     */
    public int getToaGap() {
        return mToaGap;
    }

    /**
     * Returns the received Physical Header (PHR) value.
     */
    public int getPhr() {
        return mPhr;
    }

    /**
     * Returns the received Physical Service Data Unit (PSDU) payload.
     *
     * <p>The length of this field is specified in the PSDU Data Length field,
     * which can range:
     * <ul>
     *   <li>0 to 127 bytes for BPRF</li>
     *   <li>0 to 4095 bytes for HPRF</li>
     * </ul>
     *
     * @return received PSDU data, or {@code null} if not present
     */
    @Nullable
    public byte[] getPsduData() {
        return mPsduData;
    }

    /**
     * Returns the raw notification data.
     *
     * <p>This includes all fields received in the RF test notification.
     *
     * @return raw notification bytes, or {@code null} if unavailable
     */
    @Nullable
    public byte[] getRawNotificationData() {
        return mRawNotificationData;
    }

    /**
     * Builder for a {@link TestRxResult} object.
     */
    public static final class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private int mStatus = 0;
        private long mRxDoneTimestampInt = 0;
        private int mRxDoneTimestampFrac = 0;
        private double mAoaAzimuth = 0;
        private double mAoaElevation = 0;
        private int mToaGap = 0;
        private int mPhr = 0;
        private byte[] mPsduData = null;
        private byte[] mRawNotificationData = null;

        public Builder(int rfOperationType, int status, long rxDoneTimestampInt,
                int rxDoneTimestampFrac, double aoaAzimuth, double aoaElevation, int toaGap,
                int phr, byte[] psduData, byte[] rawNotificationData) {
            this.mRfTestOperationType.set(rfOperationType);
            this.mStatus = status;
            this.mRxDoneTimestampInt = rxDoneTimestampInt;
            this.mRxDoneTimestampFrac = rxDoneTimestampFrac;
            this.mAoaAzimuth = aoaAzimuth;
            this.mAoaElevation = aoaElevation;
            this.mToaGap = toaGap;
            this.mPhr = phr;
            this.mPsduData = psduData;
            this.mRawNotificationData = rawNotificationData;
        }

        /**
         * Build the {@link RfTestRxResult} object
         */
        public RfTestRxResult build() {
            return new RfTestRxResult(this);
        }
    }
}
