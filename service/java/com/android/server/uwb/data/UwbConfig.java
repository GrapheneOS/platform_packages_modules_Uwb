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

package com.android.server.uwb.data;

import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_DISABLE;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_16;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_BPRF;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.UWB_PREAMBLE_CODE_INDEX_10;

import android.annotation.IntDef;
import android.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.MacFcsType;
import com.google.uwb.support.fira.FiraParams.MultiNodeMode;
import com.google.uwb.support.fira.FiraParams.PrfMode;
import com.google.uwb.support.fira.FiraParams.RangingRoundUsage;
import com.google.uwb.support.fira.FiraParams.RframeConfig;
import com.google.uwb.support.fira.FiraParams.StsConfig;
import com.google.uwb.support.fira.FiraParams.UwbChannel;
import com.google.uwb.support.fira.FiraParams.UwbPreambleCodeIndex;

public class UwbConfig {

    /** UWB OOB types */
    @IntDef(
            value = {
                    OOB_TYPE_NONE,
                    OOB_TYPE_BLE,
            })
    public @interface OobType{}

    public static final int OOB_TYPE_NONE = 0;
    public static final int OOB_TYPE_BLE = 1;

    /** UWB OOB roles */
    @IntDef(
            value = {
                    CENTRAL_GATT_CLIENT,
                    PERIPHERAL_GATT_SERVER,
                    PERIPHERAL,
                    CENTRAL,
            })
    public @interface OobBleRole {}

    public static final int CENTRAL_GATT_CLIENT = 0;
    public static final int PERIPHERAL_GATT_SERVER = 1;
    public static final int PERIPHERAL = 2;
    public static final int CENTRAL = 3;

    /** UWB device role */
    @IntDef(
            value = {
                    CONTROLEE_AND_RESPONDER,
                    CONTROLEE_AND_INITIATOR,
                    CONTROLLER_AND_RESPONDER,
                    CONTROLLER_AND_INITIATOR,
            })
    public @interface UwbRole {}

    public static final int CONTROLEE_AND_RESPONDER = 0;
    public static final int CONTROLEE_AND_INITIATOR = 1;
    public static final int CONTROLLER_AND_RESPONDER = 2;
    public static final int CONTROLLER_AND_INITIATOR = 3;

    /** UWB Scheduled mode */
    @IntDef(
            value = {
                    CONTENTION_BASED,
                    TIME_BASED,

            })
    public @interface ScheduledMode {}

    public static final int CONTENTION_BASED = 0;
    public static final int TIME_BASED = 1;

    @UwbRole public final int mUwbRole;
    @RangingRoundUsage public final int mRangingRoundUsage;
    @MultiNodeMode public final int mMultiNodeMode;
    @RframeConfig public final int mRframeConfig;
    @StsConfig public final int mStsConfig;
    public final int mRoundHopping;
    @ScheduledMode public final int mScheduledMode;
    public final int mMaxContentionPhaseLength;
    public final boolean mTofReport;
    public final boolean mAoaAzimuthReport;
    public final boolean mAoaElevationReport;
    public final boolean mAoaFomReport;
    public final boolean mBlockStriding;
    public final int mSlotDurationRstu;
    public final int mSlotsPerRangingRound;
    public final int mRangingIntervalMs;
    @UwbChannel public final int mUwbChannel;
    @UwbPreambleCodeIndex public final int mUwbPreambleCodeIndex;
    public final int mSp0PhyParameterSet;
    public final int mSp1PhyParameterSet;
    public final int mSp3PhyParameterSet;
    public final int mMaxRetry;
    @PrfMode public final int mConstraintLengthConvolutionalCode;
    public final int mUwbInitiationTimeMs;
    public final int mKeyRotationRate;
    @MacFcsType public final int mMacFcsType;
    public final int mRangingRoundControl;
    @Nullable public final byte[] mVendorID;
    @Nullable public final byte[] mStaticStsIV;
    @OobType public final int mOobType;
    @OobBleRole public final int mOobBleRole;

    private UwbConfig(
            @UwbRole int uwbRole,
            @RangingRoundUsage int rangingRoundUsage,
            @MultiNodeMode int multiNodeMode,
            @RframeConfig int rframeConfig,
            @StsConfig int stsConfig,
            int roundHopping,
            @ScheduledMode int scheduledMode,
            int maxContentionPhaseLength,
            boolean tofReport,
            boolean aoaAzimuthReport,
            boolean aoaElevationReport,
            boolean aoaFomReport,
            boolean blockStriding,
            int slotDurationRstu,
            int slotsPerRangingRound,
            int rangingIntervalMs,
            @UwbChannel int uwbChannel,
            @UwbPreambleCodeIndex int uwbPreambleCodeIndex,
            int sp0PhyParameterSet,
            int sp1PhyParameterSet,
            int sp3PhyParameterSet,
            int maxRetry,
            @PrfMode int constraintLengthConvolutionalCode,
            int uwbInitiationTimeMs,
            int keyRotationRate,
            @MacFcsType int macFcsType,
            int rangingRoundControl,
            @Nullable byte[] vendorID,
            @Nullable byte[] staticStsIV,
            @OobType int oobType,
            @OobBleRole int oobBleRole) {
        mUwbRole = uwbRole;
        mRangingRoundUsage = rangingRoundUsage;
        mMultiNodeMode = multiNodeMode;
        mRframeConfig = rframeConfig;
        mStsConfig = stsConfig;
        mRoundHopping = roundHopping;
        mScheduledMode = scheduledMode;
        mMaxContentionPhaseLength = maxContentionPhaseLength;
        mTofReport = tofReport;
        mAoaAzimuthReport = aoaAzimuthReport;
        mAoaElevationReport = aoaElevationReport;
        mAoaFomReport = aoaFomReport;
        mBlockStriding = blockStriding;
        mSlotDurationRstu = slotDurationRstu;
        mSlotsPerRangingRound = slotsPerRangingRound;
        mRangingIntervalMs = rangingIntervalMs;
        mUwbChannel = uwbChannel;
        mUwbPreambleCodeIndex = uwbPreambleCodeIndex;
        mSp0PhyParameterSet = sp0PhyParameterSet;
        mSp1PhyParameterSet = sp1PhyParameterSet;
        mSp3PhyParameterSet = sp3PhyParameterSet;
        mMaxRetry = maxRetry;
        mConstraintLengthConvolutionalCode = constraintLengthConvolutionalCode;
        mUwbInitiationTimeMs = uwbInitiationTimeMs;
        mKeyRotationRate = keyRotationRate;
        mMacFcsType = macFcsType;
        mRangingRoundControl = rangingRoundControl;
        this.mVendorID = vendorID;
        this.mStaticStsIV = staticStsIV;
        mOobType = oobType;
        mOobBleRole = oobBleRole;
    }

    public static final class Builder {
        private final RequiredParam<Integer> mUwbRole = new RequiredParam<>();
        private final RequiredParam<Integer> mMultiNodeMode = new RequiredParam<>();
        private final RequiredParam<Integer> mStsConfig = new RequiredParam<>();
        private final RequiredParam<Integer> mOobType = new RequiredParam<>();
        private final RequiredParam<Integer> mOobBleRole = new RequiredParam<>();

        /** UCI spec default: DS-TWR with deferred mode */
        @RangingRoundUsage
        private int mRangingRoundUsage = RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;

        /** UCI spec default: SP3 */
        @RframeConfig  private int mRframeConfig = RFRAME_CONFIG_SP3;

        /** Round hopping disabled */
        private int mRoundHopping = HOPPING_MODE_DISABLE;

        /** UCI spec default: Time Based */
        @ScheduledMode
        private int mScheduledMode = TIME_BASED;

        /** Time based used by default */
        private int mMaxContentionPhaseLength = 0;

        /** UCI spec default: time of flight report enabled */
        private boolean mTofReport = true;

        /** UCI spec default: No AoA azimuth report */
        private boolean mAoaAzimuthReport = false;

        /** UCI spec default: No AoA Elevation report */
        private boolean mAoaElevationReport = false;

        /** UCI spec default: No AoA FOM report */
        private boolean mAoaFomReport = false;

        /** UCI spec default: No Block Striding */
        private boolean mBlockStriding = false;

        /** UCI spec default: 2400 RSTU (2 ms). */
        private int mSlotDurationRstu = 2400;

        /** UCI spec default: 30 slots per ranging round. */
        private int mSlotsPerRangingRound = 30;

        /** UCI spec default: RANGING_INTERVAL 200 ms */
        private int mRangingIntervalMs = 200;

        /** UCI spec default: Channel 9, which is the only mandatory channel. */
        @UwbChannel private int mUwbChannel = UWB_CHANNEL_9;

        /** UCI spec default: index 10 */
        @UwbPreambleCodeIndex
        private int mUwbPreambleCodeIndex = UWB_PREAMBLE_CODE_INDEX_10;

        /** SP0 PHY parameter set */
        private int mSp0PhyParameterSet = 1;

        /** SP1 PHY parameter set */
        private int mSp1PhyParameterSet = 3;

        /** SP3 PHY parameter set */
        private int mSp3PhyParameterSet = 4;

        /** UCI spec default: Unlimited */
        private int mMaxRetry = 0;

        /** UCI spec default: BPRF */
        @PrfMode private int mConstraintLengthConvolutionalCode = PRF_MODE_BPRF;

        /** UCI spec default: 0ms */
        private int mUwbInitiationTimeMs = 0;

        /** UCI spec default: No key rotation*/
        private int mKeyRotationRate = 0;

        /** UCI spec default: CRC-16 */
        @MacFcsType private int mMacFcsType = MAC_FCS_TYPE_CRC_16;

        private int mRangingRoundControl = 0;

        @Nullable private byte[] mVendorID = null;
        @Nullable private byte[] mStaticStsIV = null;

        public UwbConfig.Builder setUwbRole(@UwbRole int uwbRole) {
            mUwbRole.set(uwbRole);
            return this;
        }

        public UwbConfig.Builder setMultiNodeMode(@MultiNodeMode int uwbRole) {
            mMultiNodeMode.set(uwbRole);
            return this;
        }

        public UwbConfig.Builder setStsConfig(@StsConfig int uwbRole) {
            mStsConfig.set(uwbRole);
            return this;
        }

        public UwbConfig.Builder setRangingRoundUsage(@RangingRoundUsage int rangingRoundUsage) {
            mRangingRoundUsage = rangingRoundUsage;
            return this;
        }

        public UwbConfig.Builder setRframeConfig(@RframeConfig int rframeConfig) {
            mRframeConfig = rframeConfig;
            return this;
        }

        public UwbConfig.Builder setRoundHopping(int roundHopping) {
            mRoundHopping = roundHopping;
            return this;
        }

        public UwbConfig.Builder setScheduledMode(@ScheduledMode int scheduledMode) {
            mScheduledMode = scheduledMode;
            return this;
        }

        public UwbConfig.Builder setMaxContentionPhaseLength(int maxContentionPhaseLength) {
            mMaxContentionPhaseLength = maxContentionPhaseLength;
            return this;
        }

        public UwbConfig.Builder setTofReport(boolean tofReport) {
            mTofReport = tofReport;
            return this;
        }

        public UwbConfig.Builder setAoaAzimuthReport(boolean aoaAzimuthReport) {
            mAoaAzimuthReport = aoaAzimuthReport;
            return this;
        }

        public UwbConfig.Builder setAoaElevationReport(boolean aoaElevationReport) {
            mAoaElevationReport = aoaElevationReport;
            return this;
        }

        public UwbConfig.Builder setAoaFomReport(boolean aoaFomReport) {
            mAoaFomReport = aoaFomReport;
            return this;
        }

        public UwbConfig.Builder setBlockStriding(boolean blockStriding) {
            mBlockStriding = blockStriding;
            return this;
        }

        public UwbConfig.Builder setSlotDurationRstu(int slotDurationRstu) {
            mSlotDurationRstu = slotDurationRstu;
            return this;
        }

        public UwbConfig.Builder setSlotsPerRangingRound(int slotsPerRangingRound) {
            mSlotsPerRangingRound = slotsPerRangingRound;
            return this;
        }

        public UwbConfig.Builder setRangingIntervalMs(int rangingIntervalMs) {
            mRangingIntervalMs = rangingIntervalMs;
            return this;
        }

        public UwbConfig.Builder setUwbChannel(@UwbChannel int uwbChannel) {
            mUwbChannel = uwbChannel;
            return this;
        }

        public UwbConfig.Builder setUwbPreambleCodeIndex(@UwbPreambleCodeIndex
                int uwbPreambleCodeIndex) {
            mUwbPreambleCodeIndex = uwbPreambleCodeIndex;
            return this;
        }

        public UwbConfig.Builder setSp0PhyParameterSet(int sp0PhyParameterSet) {
            mSp0PhyParameterSet = sp0PhyParameterSet;
            return this;
        }

        public UwbConfig.Builder setSp1PhyParameterSet(int sp1PhyParameterSet) {
            mSp1PhyParameterSet = sp1PhyParameterSet;
            return this;
        }

        public UwbConfig.Builder setSp3PhyParameterSet(int sp3PhyParameterSet) {
            mSp3PhyParameterSet = sp3PhyParameterSet;
            return this;
        }
        public UwbConfig.Builder setMaxRetry(int maxRetry) {
            mMaxRetry = maxRetry;
            return this;
        }

        public UwbConfig.Builder setConstraintLengthConvolutionalCode(
                @PrfMode int constraintLengthConvolutionalCode) {
            mConstraintLengthConvolutionalCode = constraintLengthConvolutionalCode;
            return this;
        }

        public UwbConfig.Builder setUwbInitiationTimeMs(int uwbInitiationTimeMs) {
            mUwbInitiationTimeMs = uwbInitiationTimeMs;
            return this;
        }

        public UwbConfig.Builder setKeyRotationRate(int keyRotationRate) {
            mKeyRotationRate = keyRotationRate;
            return this;
        }

        public UwbConfig.Builder setKMacFcsType(@MacFcsType int macFcsType) {
            mMacFcsType = macFcsType;
            return this;
        }

        public UwbConfig.Builder setRangingRoundControl(int rangingRoundControl) {
            mRangingRoundControl = rangingRoundControl;
            return this;
        }

        public UwbConfig.Builder setVendorID(byte[] vendorID) {
            mVendorID = vendorID;
            return this;
        }

        public UwbConfig.Builder setStaticStsIV(byte[] staticStsIV) {
            mStaticStsIV = staticStsIV;
            return this;
        }

        public UwbConfig.Builder setOobType(@OobType int uwbRole) {
            mOobType.set(uwbRole);
            return this;
        }

        public UwbConfig.Builder setOobBleRole(@OobBleRole int uwbRole) {
            mOobBleRole.set(uwbRole);
            return this;
        }

        public UwbConfig build() {
            return new UwbConfig(
            mUwbRole.get(),
            mRangingRoundUsage,
            mMultiNodeMode.get(),
            mRframeConfig,
            mStsConfig.get(),
            mRoundHopping,
            mScheduledMode,
            mMaxContentionPhaseLength,
            mTofReport,
            mAoaAzimuthReport,
            mAoaElevationReport,
            mAoaFomReport,
            mBlockStriding,
            mSlotDurationRstu,
            mSlotsPerRangingRound,
            mRangingIntervalMs,
            mUwbChannel,
            mUwbPreambleCodeIndex,
            mSp0PhyParameterSet,
            mSp1PhyParameterSet,
            mSp3PhyParameterSet,
            mMaxRetry,
            mConstraintLengthConvolutionalCode,
            mUwbInitiationTimeMs,
            mKeyRotationRate,
            mMacFcsType,
            mRangingRoundControl,
            mVendorID,
            mStaticStsIV,
            mOobType.get(),
            mOobBleRole.get());
        }
    }
}
