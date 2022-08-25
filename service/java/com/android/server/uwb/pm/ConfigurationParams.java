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

package com.android.server.uwb.pm;

import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_180;
import static com.android.server.uwb.config.CapabilityParam.AOA_AZIMUTH_90;
import static com.android.server.uwb.config.CapabilityParam.AOA_ELEVATION;
import static com.android.server.uwb.config.CapabilityParam.AOA_FOM;
import static com.android.server.uwb.config.CapabilityParam.CC_CONSTRAINT_LENGTH_K3;
import static com.android.server.uwb.config.CapabilityParam.CC_CONSTRAINT_LENGTH_K7;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY;
import static com.android.server.uwb.config.CapabilityParam.MANY_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.ONE_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.SP0;
import static com.android.server.uwb.config.CapabilityParam.SP1;
import static com.android.server.uwb.config.CapabilityParam.SP3;
import static com.android.server.uwb.config.CapabilityParam.STATIC_STS;
import static com.android.server.uwb.config.CapabilityParam.UNICAST;

import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT;

import android.uwb.UwbAddress;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoderBuffer;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Optional;

/**
 * CONFIGURATION_PARAMS defined in 8.5.3.3.
 */
public class ConfigurationParams {

    public static final int FIRA_PHY_VERSION = 0x80;
    public static final int FIRA_MAC_VERSION = 0x81;
    public static final int DEVICE_ROLES = 0x82;
    public static final int RANGING_METHOD = 0x83;
    public static final int STS_CONFIG = 0x84;
    public static final int MULTI_NODE_MODE = 0x85;
    public static final int RANGING_TIME_STRUCT = 0x86;
    public static final int SCHEDULED_MODE = 0x87;
    public static final int HOPPING_MODE = 0x88;
    public static final int BLOCK_STRIDING = 0x89;
    public static final int UWB_INITIATION_TIME = 0x8A;
    public static final int CHANNEL = 0x8B;
    public static final int RFRAME_CONFIG = 0x8C;
    public static final int CC_CONSTRAINT_LENGTH = 0x8D;
    public static final int PRF_MODE = 0X8E;
    public static final int SP0_PHY_SET = 0x8F;
    public static final int SP1_PHY_SET = 0x90;
    public static final int SP3_PHY_SET = 0x91;
    public static final int PREAMBLE_CODE_INDEX = 0x92;
    public static final int RESULT_REPORT_CONFIG = 0x93;
    public static final int MAC_ADDRESS_MODE = 0x94;
    public static final int CONTROLEE_SHORT_MAC_ADDRESS = 0x95;
    public static final int CONTROLLER_MAC_ADDRESS = 0x96;
    public static final int SLOTS_PER_RR = 0x97;
    public static final int MAX_CONTENTION_PHASE_LENGTH = 0x98;
    public static final int SLOT_DURATION = 0x99;
    public static final int RANGING_INTERVAL = 0x9A;
    public static final int KEY_ROTATION_RATE = 0x9B;
    public static final int MAC_FCS_TYPE = 0x9C;
    public static final int MAX_RR_RETRY = 0x9D;
    public static final int CONFIGURATION_PARAMS_MAX_COUNT = 30;

    public final FiraProtocolVersion mPhyVersion;
    public final FiraProtocolVersion mMacVersion;
    public final Optional<Integer> mDeviceRole;
    public final Optional<Integer> mRangingMethod;
    public final Optional<FiraParams.StsCapabilityFlag> mStsConfig;
    public final Optional<FiraParams.MultiNodeCapabilityFlag> mMultiNodeMode;
    public final Optional<Byte> mRangingTimeStruct;
    public final Optional<Byte> mScheduledMode;
    public final Optional<Boolean> mHoppingMode;
    public final Optional<Boolean> mBlockStriding;
    public final Optional<Boolean> mUwbInitiationTime;
    public final Optional<Integer> mChannel;
    public final Optional<FiraParams.RframeCapabilityFlag> mRframeConfig;
    public final Optional<FiraParams.PsduDataRateCapabilityFlag> mCcConstraintLength;
    public final Optional<Integer> mPrfMode;
    public final Optional<Integer> mSp0PhyParameterSet;
    public final Optional<Integer> mSp1PhyParameterSet;
    public final Optional<Integer> mSp3PhyParameterSet;
    public final Optional<Integer> mPreambleCodeIndex;
    public final Optional<EnumSet<FiraParams.AoaCapabilityFlag>> mResultReportConfig;
    public final Optional<Integer> mMacAddressMode;
    public final Optional<UwbAddress> mControleeShortMacAddress;
    public final Optional<UwbAddress> mControllerMacAddress;
    public final Optional<Integer> mSlotsPerRangingRound;
    public final Optional<Integer> mMaxContentionPhaseLength;
    public final Optional<Integer> mSlotDuration;
    public final Optional<Integer> mRangingIntervalMs;
    public final Optional<Integer> mKeyRotationRate;
    public final Optional<Integer> mMacFcsType;
    public final Optional<Integer> mMaxRangingRoundRetry;

    public ConfigurationParams(
            FiraProtocolVersion phyVersion,
            FiraProtocolVersion macVersion,
            Optional<Integer> deviceRole,
            Optional<Integer> rangingMethod,
            Optional<FiraParams.StsCapabilityFlag> stsConfig,
            Optional<FiraParams.MultiNodeCapabilityFlag> multiNodeMode,
            Optional<Byte> rangingTimeStruct, Optional<Byte> scheduledMode,
            Optional<Boolean> hoppingMode, Optional<Boolean> blockStriding,
            Optional<Boolean> uwbInitiationTime,
            Optional<Integer> channel,
            Optional<FiraParams.RframeCapabilityFlag> rframeConfig,
            Optional<FiraParams.PsduDataRateCapabilityFlag> ccConstraintLength,
            Optional<Integer> prfMode,
            Optional<Integer> sp0PhyParameterSet,
            Optional<Integer> sp1PhyParameterSet,
            Optional<Integer> sp3PhyParameterSet,
            Optional<Integer> preambleCodeIndex,
            Optional<EnumSet<FiraParams.AoaCapabilityFlag>> resultReportConfig,
            Optional<Integer> macAddressMode,
            Optional<UwbAddress> controleeShortMacAddress,
            Optional<UwbAddress> controllerMacAddress,
            Optional<Integer> slotsPerRangingRound,
            Optional<Integer> maxContentionPhaseLength,
            Optional<Integer> slotDuration,
            Optional<Integer> rangingIntervalMs,
            Optional<Integer> keyRotationRate,
            Optional<Integer> macFcsType,
            Optional<Integer> maxRangingRoundRetry) {
        mPhyVersion = phyVersion;
        mMacVersion = macVersion;
        mDeviceRole = deviceRole;
        mRangingMethod = rangingMethod;
        mStsConfig = stsConfig;
        mMultiNodeMode = multiNodeMode;
        mRangingTimeStruct = rangingTimeStruct;
        mScheduledMode = scheduledMode;
        mHoppingMode = hoppingMode;
        mBlockStriding = blockStriding;
        mUwbInitiationTime = uwbInitiationTime;
        mChannel = channel;
        mRframeConfig = rframeConfig;
        mCcConstraintLength = ccConstraintLength;
        mPrfMode = prfMode;
        mSp0PhyParameterSet = sp0PhyParameterSet;
        mSp1PhyParameterSet = sp1PhyParameterSet;
        mSp3PhyParameterSet = sp3PhyParameterSet;
        mPreambleCodeIndex = preambleCodeIndex;
        mResultReportConfig = resultReportConfig;
        mMacAddressMode = macAddressMode;
        mControleeShortMacAddress = controleeShortMacAddress;
        mControllerMacAddress = controllerMacAddress;
        mSlotsPerRangingRound = slotsPerRangingRound;
        mMaxContentionPhaseLength = maxContentionPhaseLength;
        mSlotDuration = slotDuration;
        mRangingIntervalMs = rangingIntervalMs;
        mKeyRotationRate = keyRotationRate;
        mMacFcsType = macFcsType;
        mMaxRangingRoundRetry = maxRangingRoundRetry;
    }

    /**
     * Converts the UwbCapabilities to the bytes which are combined per the TLV of CSML 8.5.3.2.
     */
    @NonNull
    public byte[] toBytes() {
        TlvBuffer.Builder configParamsBuilder = new TlvBuffer.Builder()
                .putByteArray(FIRA_PHY_VERSION, new byte[]{
                        (byte) mPhyVersion.getMajor(),
                        (byte) mPhyVersion.getMinor(),
                })
                .putByteArray(FIRA_MAC_VERSION, new byte[]{
                        (byte) mMacVersion.getMajor(),
                        (byte) mMacVersion.getMinor(),
                });

        mDeviceRole.ifPresent(integer -> configParamsBuilder.putByte(DEVICE_ROLES,
                integer.byteValue()));

        mRangingMethod.ifPresent(integer -> configParamsBuilder.putByte(RANGING_METHOD,
                integer.byteValue()));

        //configParamsBuilder.putByte(RANGING_METHOD,(byte) HAS_DS_TWR_SUPPORT.getValue());

        mStsConfig.ifPresent(stsCapabilityFlag -> configParamsBuilder.putByte(STS_CONFIG,
                (byte) stsCapabilityFlag.getValue()));

        mMultiNodeMode.ifPresent(
                multiNodeCapabilityFlag -> configParamsBuilder.putByte(MULTI_NODE_MODE,
                        (byte) multiNodeCapabilityFlag.getValue()));

        mRangingTimeStruct.ifPresent(
                aByte -> configParamsBuilder.putByte(RANGING_TIME_STRUCT, aByte));

        mScheduledMode.ifPresent(
                aByte -> configParamsBuilder.putByte(SCHEDULED_MODE, aByte));

        mHoppingMode.ifPresent(aBoolean -> configParamsBuilder.putByte(HOPPING_MODE,
                (byte) (aBoolean ? 1 : 0)));

        mBlockStriding.ifPresent(aBoolean -> configParamsBuilder.putByte(BLOCK_STRIDING,
                (byte) (aBoolean ? 1 : 0)));

        mUwbInitiationTime.ifPresent(aBoolean -> configParamsBuilder.putByte(UWB_INITIATION_TIME,
                (byte) (aBoolean ? 1 : 0)));

        mChannel.ifPresent(integer -> configParamsBuilder.putByte(CHANNEL, integer.byteValue()));

        mRframeConfig.ifPresent(rframeCapabilityFlag -> configParamsBuilder.putByte(RFRAME_CONFIG,
                (byte) rframeCapabilityFlag.getValue()));

        mCcConstraintLength.ifPresent(
                psduDataRateCapabilityFlag -> configParamsBuilder.putByte(CC_CONSTRAINT_LENGTH,
                        (byte) psduDataRateCapabilityFlag.getValue()));

        mPrfMode.ifPresent(integer -> configParamsBuilder.putByte(PRF_MODE, integer.byteValue()));

        mSp0PhyParameterSet.ifPresent(integer -> configParamsBuilder.putByte(SP0_PHY_SET,
                integer.byteValue()));

        mSp1PhyParameterSet.ifPresent(integer -> configParamsBuilder.putByte(SP1_PHY_SET,
                integer.byteValue()));

        mSp3PhyParameterSet.ifPresent(integer -> configParamsBuilder.putByte(SP3_PHY_SET,
                integer.byteValue()));

        mPreambleCodeIndex.ifPresent(
                integer -> configParamsBuilder.putByte(PREAMBLE_CODE_INDEX, integer.byteValue()));

        mPreambleCodeIndex.ifPresent(
                integer -> configParamsBuilder.putByte(PREAMBLE_CODE_INDEX, integer.byteValue()));

        if (mResultReportConfig.isPresent()) {
            byte resultReportConfig = 0;
            if (mResultReportConfig.get().contains(
                    FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT)) {
                resultReportConfig = (byte) (resultReportConfig | AOA_AZIMUTH_90);
            }
            if (mResultReportConfig.get().contains(
                    FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT)) {
                resultReportConfig = (byte) (resultReportConfig | AOA_AZIMUTH_180);
            }
            if (mResultReportConfig.get().contains(
                    FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT)) {
                resultReportConfig = (byte) (resultReportConfig | AOA_ELEVATION);
            }
            if (mResultReportConfig.get().contains(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT)) {
                resultReportConfig = (byte) (resultReportConfig | AOA_FOM);
            }
            configParamsBuilder.putByte(RESULT_REPORT_CONFIG, resultReportConfig);
        }

        mMacAddressMode.ifPresent(
                integer -> configParamsBuilder.putByte(MAC_ADDRESS_MODE, integer.byteValue()));

        mControleeShortMacAddress.ifPresent(
                uwbAddress -> configParamsBuilder.putByteArray(CONTROLEE_SHORT_MAC_ADDRESS,
                        uwbAddress.toBytes()));

        mControllerMacAddress.ifPresent(
                uwbAddress -> configParamsBuilder.putByteArray(CONTROLLER_MAC_ADDRESS,
                        uwbAddress.toBytes()));

        mSlotsPerRangingRound.ifPresent(
                integer -> configParamsBuilder.putByte(SLOTS_PER_RR, integer.byteValue()));

        mMaxContentionPhaseLength.ifPresent(
                integer -> configParamsBuilder.putByte(MAX_CONTENTION_PHASE_LENGTH,
                        integer.byteValue()));

        mSlotDuration.ifPresent(
                integer -> configParamsBuilder.putByteArray(SLOT_DURATION, ByteBuffer.allocate(
                        2).putInt(
                        integer).array()));

        mRangingIntervalMs.ifPresent(
                integer -> configParamsBuilder.putByteArray(RANGING_INTERVAL, ByteBuffer.allocate(
                        2).putInt(
                        integer).array()));

        mKeyRotationRate.ifPresent(
                integer -> configParamsBuilder.putByte(KEY_ROTATION_RATE,
                        mKeyRotationRate.get().byteValue()));

        mMacFcsType.ifPresent(
                integer -> configParamsBuilder.putByte(MAC_ADDRESS_MODE,
                        mMacFcsType.get().byteValue()));

        mMaxRangingRoundRetry.ifPresent(
                integer -> configParamsBuilder.putByteArray(MAX_RR_RETRY, ByteBuffer.allocate(
                        2).putInt(
                        integer).array()));

        return configParamsBuilder.build().getByteArray();
    }


    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private static boolean isPresent(TlvDecoderBuffer tlvDecoderBuffer, int tagType) {
        try {
            tlvDecoderBuffer.getByte(tagType);
        } catch (IllegalArgumentException e) {
            try {
                tlvDecoderBuffer.getByteArray(tagType);
            } catch (IllegalArgumentException e1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts the UwbCapabilities from the data stream, which is encoded per the CSML 8.5.3.2.
     *
     * @return null if the data cannot be decoded per spec.
     */
    @Nullable
    public static ConfigurationParams fromBytes(@NonNull byte[] data) {
        TlvDecoderBuffer configParamsTlv = new TlvDecoderBuffer(data,
                CONFIGURATION_PARAMS_MAX_COUNT);
        configParamsTlv.parse();
        ConfigurationParams.Builder configParamsBuilder = new ConfigurationParams.Builder();

        if (isPresent(configParamsTlv, FIRA_PHY_VERSION)) {
            byte[] firaPhyVersion = configParamsTlv.getByteArray(FIRA_PHY_VERSION);
            if (firaPhyVersion.length == 2) {
                FiraProtocolVersion version = new FiraProtocolVersion(firaPhyVersion[0],
                        firaPhyVersion[1]);
                configParamsBuilder.setPhyVersion(version);
            }
        }
        if (isPresent(configParamsTlv, FIRA_MAC_VERSION)) {
            byte[] firaMacVersion = configParamsTlv.getByteArray(FIRA_MAC_VERSION);
            if (firaMacVersion.length == 2) {
                FiraProtocolVersion version = new FiraProtocolVersion(firaMacVersion[0],
                        firaMacVersion[1]);
                configParamsBuilder.setMacVersion(version);
            }
        }
        if (isPresent(configParamsTlv, DEVICE_ROLES)) {
            configParamsBuilder.setDeviceRole(configParamsTlv.getByte(DEVICE_ROLES));
        }
        if (isPresent(configParamsTlv, RANGING_METHOD)) {
            configParamsBuilder.setRangingMethod(configParamsTlv.getByte(RANGING_METHOD));
            /*byte rangingMethodRaw = configParamsTlv.getByte(RANGING_METHOD);
            if (rangingMethodRaw == SS_TWR_DEFERRED) {
                configParamsBuilder.setRangingMethod(HAS_SS_TWR_SUPPORT);
            } else if (rangingMethodRaw == DS_TWR_DEFERRED) {
                configParamsBuilder.setRangingMethod(HAS_DS_TWR_SUPPORT);
            }*/
        }
        if (isPresent(configParamsTlv, STS_CONFIG)) {
            byte stsConfigRaw = configParamsTlv.getByte(STS_CONFIG);
            if (isBitSet(stsConfigRaw, STATIC_STS)) {
                configParamsBuilder.setStsConfig(HAS_STATIC_STS_SUPPORT);
            } else if (isBitSet(stsConfigRaw, DYNAMIC_STS)) {
                configParamsBuilder.setStsConfig(HAS_DYNAMIC_STS_SUPPORT);
            } else if (isBitSet(stsConfigRaw, DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                configParamsBuilder.setStsConfig(HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
        }
        if (isPresent(configParamsTlv, MULTI_NODE_MODE)) {
            byte multiNodeRaw = configParamsTlv.getByte(MULTI_NODE_MODE);
            if (isBitSet(multiNodeRaw, UNICAST)) {
                configParamsBuilder.setMultiNodeMode(HAS_UNICAST_SUPPORT);
            } else if (isBitSet(multiNodeRaw, ONE_TO_MANY)) {
                configParamsBuilder.setMultiNodeMode(HAS_ONE_TO_MANY_SUPPORT);
            } else if (isBitSet(multiNodeRaw, MANY_TO_MANY)) {
                configParamsBuilder.setMultiNodeMode(HAS_MANY_TO_MANY_SUPPORT);
            }
        }
        if (isPresent(configParamsTlv, RANGING_TIME_STRUCT)) {
            configParamsBuilder.setRangingTimeStruct(configParamsTlv.getByte(RANGING_TIME_STRUCT));
        }
        if (isPresent(configParamsTlv, SCHEDULED_MODE)) {
            configParamsBuilder.setScheduledMode(configParamsTlv.getByte(SCHEDULED_MODE));
        }
        if (isPresent(configParamsTlv, HOPPING_MODE)) {
            configParamsBuilder.setHoppingMode(configParamsTlv.getByte(HOPPING_MODE) == 1);
        }
        if (isPresent(configParamsTlv, BLOCK_STRIDING)) {
            configParamsBuilder.setBlockStriding(configParamsTlv.getByte(BLOCK_STRIDING) == 1);
        }
        if (isPresent(configParamsTlv, UWB_INITIATION_TIME)) {
            configParamsBuilder.setUwbInitiationTime(
                    configParamsTlv.getByte(UWB_INITIATION_TIME) == 1);
        }
        if (isPresent(configParamsTlv, CHANNEL)) {
            configParamsBuilder.setChannel(configParamsTlv.getByte(CHANNEL).intValue());
        }
        if (isPresent(configParamsTlv, RFRAME_CONFIG)) {
            byte rFrameConfigRaw = configParamsTlv.getByte(RFRAME_CONFIG);
            if (isBitSet(rFrameConfigRaw, SP0)) {
                configParamsBuilder.setRframeConfig(HAS_SP0_RFRAME_SUPPORT);
            } else if (isBitSet(rFrameConfigRaw, SP1)) {
                configParamsBuilder.setRframeConfig(HAS_SP1_RFRAME_SUPPORT);
            } else if (isBitSet(rFrameConfigRaw, SP3)) {
                configParamsBuilder.setRframeConfig(HAS_SP3_RFRAME_SUPPORT);
            }
        }
        if (isPresent(configParamsTlv, CC_CONSTRAINT_LENGTH)) {
            byte ccConstraintLengthRaw = configParamsTlv.getByte(CC_CONSTRAINT_LENGTH);
            if (isBitSet(ccConstraintLengthRaw, CC_CONSTRAINT_LENGTH_K3)) {
                configParamsBuilder.setCcConstraintLength(HAS_6M81_SUPPORT);
            } else if (isBitSet(ccConstraintLengthRaw, CC_CONSTRAINT_LENGTH_K7)) {
                configParamsBuilder.setCcConstraintLength(HAS_7M80_SUPPORT);
            }
        }
        if (isPresent(configParamsTlv, PRF_MODE)) {
            configParamsBuilder.setPrfMode(configParamsTlv.getByte(PRF_MODE).intValue());
        }
        if (isPresent(configParamsTlv, SP0_PHY_SET)) {
            configParamsBuilder.setSp0PhyParameterSet(
                    configParamsTlv.getByte(SP0_PHY_SET).intValue());
        }
        if (isPresent(configParamsTlv, SP1_PHY_SET)) {
            configParamsBuilder.setSp1PhyParameterSet(
                    configParamsTlv.getByte(SP1_PHY_SET).intValue());
        }
        if (isPresent(configParamsTlv, SP3_PHY_SET)) {
            configParamsBuilder.setSp3PhyParameterSet(
                    configParamsTlv.getByte(SP3_PHY_SET).intValue());
        }
        if (isPresent(configParamsTlv, PREAMBLE_CODE_INDEX)) {
            configParamsBuilder.setPreambleCodeIndex(
                    configParamsTlv.getByte(PREAMBLE_CODE_INDEX).intValue());
        }
        if (isPresent(configParamsTlv, PREAMBLE_CODE_INDEX)) {
            configParamsBuilder.setPreambleCodeIndex(
                    configParamsTlv.getByte(PREAMBLE_CODE_INDEX).intValue());
        }
        if (isPresent(configParamsTlv, RESULT_REPORT_CONFIG)) {
            EnumSet<FiraParams.AoaCapabilityFlag> aoaSupport = EnumSet.noneOf(
                    FiraParams.AoaCapabilityFlag.class);
            byte aoaSupportRaw = configParamsTlv.getByte(RESULT_REPORT_CONFIG);
            if (isBitSet(aoaSupportRaw, AOA_AZIMUTH_90)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaSupportRaw, AOA_AZIMUTH_180)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
            }
            if (isBitSet(aoaSupportRaw, AOA_ELEVATION)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
            }
            if (isBitSet(aoaSupportRaw, AOA_FOM)) {
                aoaSupport.add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);
            }
            configParamsBuilder.setResultReportConfig(aoaSupport);
        }
        if (isPresent(configParamsTlv, MAC_ADDRESS_MODE)) {
            configParamsBuilder.setMacAddressMode(
                    configParamsTlv.getByte(MAC_ADDRESS_MODE).intValue());
        }
        if (isPresent(configParamsTlv, CONTROLEE_SHORT_MAC_ADDRESS)) {
            UwbAddress controleeAddress = UwbAddress.fromBytes(
                    configParamsTlv.getByteArray(CONTROLEE_SHORT_MAC_ADDRESS));
            configParamsBuilder.setControleeShortMacAddress(controleeAddress);
        }
        if (isPresent(configParamsTlv, CONTROLLER_MAC_ADDRESS)) {
            UwbAddress controllerAddress = UwbAddress.fromBytes(
                    configParamsTlv.getByteArray(CONTROLLER_MAC_ADDRESS));
            configParamsBuilder.setControllerMacAddress(controllerAddress);
        }
        if (isPresent(configParamsTlv, SLOTS_PER_RR)) {
            configParamsBuilder.setSlotsPerRangingRound(
                    configParamsTlv.getByte(SLOTS_PER_RR).intValue());
        }
        if (isPresent(configParamsTlv, MAX_CONTENTION_PHASE_LENGTH)) {
            configParamsBuilder.setMaxContentionPhaseLength(
                    configParamsTlv.getByte(MAX_CONTENTION_PHASE_LENGTH).intValue());
        }
        if (isPresent(configParamsTlv, SLOT_DURATION)) {
            configParamsBuilder.setSlotDuration(
                    ByteBuffer.wrap(configParamsTlv.getByteArray(SLOT_DURATION)).getInt());
        }
        if (isPresent(configParamsTlv, RANGING_INTERVAL)) {
            configParamsBuilder.setRangingIntervalMs(
                    ByteBuffer.wrap(
                            configParamsTlv.getByteArray(RANGING_INTERVAL)).getInt());
        }
        if (isPresent(configParamsTlv, KEY_ROTATION_RATE)) {
            configParamsBuilder.setKeyRotationRate(
                    configParamsTlv.getByte(KEY_ROTATION_RATE).intValue());
        }
        if (isPresent(configParamsTlv, MAC_FCS_TYPE)) {
            configParamsBuilder.setMaxContentionPhaseLength(
                    configParamsTlv.getByte(MAC_FCS_TYPE).intValue());
        }
        if (isPresent(configParamsTlv, MAX_RR_RETRY)) {
            configParamsBuilder.setMaxRangingRoundRetry(
                    ByteBuffer.wrap(configParamsTlv.getByteArray(MAX_RR_RETRY)).getInt());
        }
        return configParamsBuilder.build();
    }

    /** Builder for CONFIGURATION_PARAMS */
    public static class Builder {
        // Set all default protocol version to FiRa 1.1
        private FiraProtocolVersion mPhyVersion = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMacVersion = new FiraProtocolVersion(1, 1);
        private Optional<Integer> mDeviceRole =
                Optional.empty();
        private Optional<Integer> mRangingMethod =
                Optional.empty();
        private Optional<FiraParams.StsCapabilityFlag> mStsConfig = Optional.empty();
        private Optional<FiraParams.MultiNodeCapabilityFlag> mMultiNodeMode =
                Optional.empty();
        private Optional<Byte> mRangingTimeStruct = Optional.empty();
        private Optional<Byte> mScheduledMode = Optional.empty();
        private Optional<Boolean> mHoppingMode = Optional.empty();
        private Optional<Boolean> mBlockStriding = Optional.empty();
        private Optional<Boolean> mUwbInitiationTime = Optional.empty();
        private Optional<Integer> mChannel = Optional.empty();
        private Optional<FiraParams.RframeCapabilityFlag> mRframeConfig = Optional.empty();
        private Optional<FiraParams.PsduDataRateCapabilityFlag> mCcConstraintLength =
                Optional.empty();
        private Optional<Integer> mPrfMode = Optional.empty();
        private Optional<Integer> mSp0PhyParameterSet = Optional.empty();
        private Optional<Integer> mSp1PhyParameterSet = Optional.empty();
        private Optional<Integer> mSp3PhyParameterSet = Optional.empty();
        private Optional<Integer> mPreambleCodeIndex = Optional.empty();
        private Optional<EnumSet<FiraParams.AoaCapabilityFlag>> mResultReportConfig =
                Optional.empty();
        private Optional<Integer> mMacAddressMode = Optional.empty();
        private Optional<UwbAddress> mControleeShortMacAddress = Optional.empty();
        private Optional<UwbAddress> mControllerMacAddress = Optional.empty();
        private Optional<Integer> mSlotsPerRangingRound = Optional.empty();
        private Optional<Integer> mMaxContentionPhaseLength = Optional.empty();
        private Optional<Integer> mSlotDuration = Optional.empty();
        private Optional<Integer> mRangingIntervalMs = Optional.empty();
        private Optional<Integer> mKeyRotationRate = Optional.empty();
        private Optional<Integer> mMacFcsType = Optional.empty();
        private Optional<Integer> mMaxRangingRoundRetry = Optional.empty();

        public ConfigurationParams.Builder setPhyVersion(
                FiraProtocolVersion phyVersion) {
            mPhyVersion = phyVersion;
            return this;
        }

        public ConfigurationParams.Builder setMacVersion(
                FiraProtocolVersion macVersion) {
            mMacVersion = macVersion;
            return this;
        }

        public ConfigurationParams.Builder setDeviceRole(
                int deviceRole) {
            mDeviceRole = Optional.of(deviceRole);
            return this;
        }

        public ConfigurationParams.Builder setRangingMethod(int rangingMethod) {
            mRangingMethod = Optional.of(rangingMethod);
            return this;
        }

        public ConfigurationParams.Builder setStsConfig(
                FiraParams.StsCapabilityFlag stsConfig) {
            mStsConfig = Optional.of(stsConfig);
            return this;
        }

        public ConfigurationParams.Builder setMultiNodeMode(
                FiraParams.MultiNodeCapabilityFlag multiNodeMode) {
            mMultiNodeMode = Optional.of(multiNodeMode);
            return this;
        }

        public ConfigurationParams.Builder setRangingTimeStruct(Byte rangingTimeStruct) {
            mRangingTimeStruct = Optional.of(rangingTimeStruct);
            return this;
        }

        public ConfigurationParams.Builder setScheduledMode(Byte scheduledMode) {
            mScheduledMode = Optional.of(scheduledMode);
            return this;
        }

        public ConfigurationParams.Builder setHoppingMode(Boolean hoppingMode) {
            mHoppingMode = Optional.of(hoppingMode);
            return this;
        }

        public ConfigurationParams.Builder setBlockStriding(Boolean blockStriding) {
            mBlockStriding = Optional.of(blockStriding);
            return this;
        }

        public ConfigurationParams.Builder setUwbInitiationTime(
                Boolean uwbInitiationTime) {
            mUwbInitiationTime = Optional.of(uwbInitiationTime);
            return this;
        }

        public ConfigurationParams.Builder setChannel(Integer channel) {
            mChannel = Optional.of(channel);
            return this;
        }

        public ConfigurationParams.Builder setRframeConfig(
                FiraParams.RframeCapabilityFlag rFrameConfig) {
            mRframeConfig = Optional.of(rFrameConfig);
            return this;
        }

        public ConfigurationParams.Builder setCcConstraintLength(
                FiraParams.PsduDataRateCapabilityFlag ccConstraintLength) {
            mCcConstraintLength = Optional.of(ccConstraintLength);
            return this;
        }

        public ConfigurationParams.Builder setPrfMode(Integer prfMode) {
            mPrfMode = Optional.of(prfMode);
            return this;
        }

        public ConfigurationParams.Builder setSp0PhyParameterSet(
                Integer sp0PhyParameterSet) {
            mSp0PhyParameterSet = Optional.of(sp0PhyParameterSet);
            return this;
        }

        public ConfigurationParams.Builder setSp1PhyParameterSet(
                Integer sp1PhyParameterSet) {
            mSp1PhyParameterSet = Optional.of(sp1PhyParameterSet);
            return this;
        }

        public ConfigurationParams.Builder setSp3PhyParameterSet(
                Integer sp3PhyParameterSet) {
            mSp3PhyParameterSet = Optional.of(sp3PhyParameterSet);
            return this;
        }

        public ConfigurationParams.Builder setPreambleCodeIndex(
                Integer preambleCodeIndex) {
            mPreambleCodeIndex = Optional.of(preambleCodeIndex);
            return this;
        }

        public ConfigurationParams.Builder setResultReportConfig(
                EnumSet<FiraParams.AoaCapabilityFlag> resultReportConfig) {
            mResultReportConfig = Optional.of(resultReportConfig);
            return this;
        }

        public ConfigurationParams.Builder setMacAddressMode(
                Integer macAddressMode) {
            mMacAddressMode = Optional.of(macAddressMode);
            return this;
        }

        public ConfigurationParams.Builder setControleeShortMacAddress(
                UwbAddress controleeShortMacAddress) {
            mControleeShortMacAddress = Optional.of(controleeShortMacAddress);
            return this;
        }

        public ConfigurationParams.Builder setControllerMacAddress(
                UwbAddress controllerMacAddress) {
            mControllerMacAddress = Optional.of(controllerMacAddress);
            return this;
        }

        public ConfigurationParams.Builder setSlotsPerRangingRound(
                Integer slotsPerRangingRound) {
            mSlotsPerRangingRound = Optional.of(slotsPerRangingRound);
            return this;
        }

        public ConfigurationParams.Builder setMaxContentionPhaseLength(
                Integer maxContentionPhaseLength) {
            mMaxContentionPhaseLength = Optional.of((maxContentionPhaseLength));
            return this;
        }

        public ConfigurationParams.Builder setSlotDuration(Integer slotDuration) {
            mSlotDuration = Optional.of(slotDuration);
            return this;
        }

        public ConfigurationParams.Builder setRangingIntervalMs(
                Integer rangingIntervalMs) {
            mRangingIntervalMs = Optional.of(rangingIntervalMs);
            return this;
        }

        public ConfigurationParams.Builder setKeyRotationRate(Integer keyRotationRate) {
            mKeyRotationRate = Optional.of(keyRotationRate);
            return this;
        }

        public ConfigurationParams.Builder setMacFcsType(Integer macFcsType) {
            mMacFcsType = Optional.of(macFcsType);
            return this;
        }

        public ConfigurationParams.Builder setMaxRangingRoundRetry(
                Integer maxRangingRoundRetry) {
            mMaxRangingRoundRetry = Optional.of(maxRangingRoundRetry);
            return this;
        }

        public ConfigurationParams build() {
            return new ConfigurationParams(
                    mPhyVersion,
                    mMacVersion,
                    mDeviceRole,
                    mRangingMethod,
                    mStsConfig,
                    mMultiNodeMode,
                    mRangingTimeStruct,
                    mScheduledMode,
                    mHoppingMode,
                    mBlockStriding,
                    mUwbInitiationTime,
                    mChannel,
                    mRframeConfig,
                    mCcConstraintLength,
                    mPrfMode,
                    mSp0PhyParameterSet,
                    mSp1PhyParameterSet,
                    mSp3PhyParameterSet,
                    mPreambleCodeIndex,
                    mResultReportConfig,
                    mMacAddressMode,
                    mControleeShortMacAddress,
                    mControllerMacAddress,
                    mSlotsPerRangingRound,
                    mMaxContentionPhaseLength,
                    mSlotDuration,
                    mRangingIntervalMs,
                    mKeyRotationRate,
                    mMacFcsType,
                    mMaxRangingRoundRetry
            );
        }
    }
}
