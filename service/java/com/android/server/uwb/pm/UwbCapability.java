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
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_10;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_12;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_13;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_14;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_5;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_6;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_8;
import static com.android.server.uwb.config.CapabilityParam.CHANNEL_9;
import static com.android.server.uwb.config.CapabilityParam.DS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS;
import static com.android.server.uwb.config.CapabilityParam.DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY;
import static com.android.server.uwb.config.CapabilityParam.INITIATOR;
import static com.android.server.uwb.config.CapabilityParam.MANY_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.ONE_TO_MANY;
import static com.android.server.uwb.config.CapabilityParam.RESPONDER;
import static com.android.server.uwb.config.CapabilityParam.SP0;
import static com.android.server.uwb.config.CapabilityParam.SP1;
import static com.android.server.uwb.config.CapabilityParam.SP3;
import static com.android.server.uwb.config.CapabilityParam.SS_TWR_DEFERRED;
import static com.android.server.uwb.config.CapabilityParam.STATIC_STS;
import static com.android.server.uwb.config.CapabilityParam.UNICAST;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.params.TlvBuffer;
import com.android.server.uwb.params.TlvDecoderBuffer;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * UWB_CAPABILITY defined in 8.5.3.2
 */
public class UwbCapability {
    public static final int FIRA_PHY_VERSION_RANGE = 0x80;
    public static final int FIRA_MAC_VERSION_RANGE = 0x81;
    public static final int DEVICE_ROLES = 0x82;
    public static final int RANGING_METHOD = 0x83;
    public static final int STS_CONFIG = 0x84;
    public static final int MULTI_NODE_MODE = 0x85;
    public static final int RANGING_TIME_STRUCT = 0x86;
    public static final int SCHEDULED_MODE = 0x87;
    public static final int HOPPING_MODE = 0x88;
    public static final int BLOCK_STRIDING = 0x89;
    public static final int UWB_INITIATION_TIME = 0x8A;
    public static final int CHANNELS = 0x8B;
    public static final int RFRAME_CONFIG = 0x8C;
    public static final int CC_CONSTRAINT_LENGTH = 0x8D;
    public static final int BPRF_PARAMETER_SETS = 0x8E;
    public static final int HPRF_PARAMETER_SETS = 0x8F;
    public static final int AOA_SUPPORT = 0x90;
    public static final int EXTENDED_MAC_ADDRESS = 0x91;
    public static final int UWB_CAPABILITY_MAX_COUNT = 18;

    public final FiraProtocolVersion mMinPhyVersionSupported;
    public final FiraProtocolVersion mMaxPhyVersionSupported;
    public final FiraProtocolVersion mMinMacVersionSupported;
    public final FiraProtocolVersion mMaxMacVersionSupported;
    public final Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> mDeviceRoles;
    public final Optional<EnumSet<FiraParams.RangingRoundCapabilityFlag>> mRangingMethod;
    public final Optional<EnumSet<FiraParams.StsCapabilityFlag>> mStsConfig;
    public final Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> mMultiNodeMode;
    public final Optional<Byte> mRangingTimeStruct;
    public final Optional<Byte> mScheduledMode;
    public final Optional<Boolean> mHoppingMode;
    public final Optional<Boolean> mBlockStriding;
    public final Optional<Boolean> mUwbInitiationTime;
    public final Optional<List<Integer>> mChannels;
    public final Optional<EnumSet<FiraParams.RframeCapabilityFlag>> mRframeConfig;
    public final Optional<EnumSet<FiraParams.PsduDataRateCapabilityFlag>> mCcConstraintLength;
    public final Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> mBprfParameterSet;
    public final Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> mHprfParameterSet;
    public final Optional<EnumSet<FiraParams.AoaCapabilityFlag>> mAoaSupport;
    public final Optional<Byte> mExtendedMacSupport;

    private UwbCapability(FiraProtocolVersion minPhyVersionSupported,
            FiraProtocolVersion maxPhyVersionSupported,
            FiraProtocolVersion minMacVersionSupported,
            FiraProtocolVersion maxMacVersionSupported,
            Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> deviceRoles,
            Optional<EnumSet<FiraParams.RangingRoundCapabilityFlag>> rangingMethod,
            Optional<EnumSet<FiraParams.StsCapabilityFlag>> stsConfig,
            Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> multiNodeMode,
            Optional<Byte> rangingTimeStruct,
            Optional<Byte> scheduledMode,
            Optional<Boolean> hoppingMode,
            Optional<Boolean> blockStriding,
            Optional<Boolean> uwbInitiationTime,
            Optional<List<Integer>> channels,
            Optional<EnumSet<FiraParams.RframeCapabilityFlag>> rframeConfig,
            Optional<EnumSet<FiraParams.PsduDataRateCapabilityFlag>> ccConstraintLength,
            Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> bprfParameterSet,
            Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> hprfParameterSet,
            Optional<EnumSet<FiraParams.AoaCapabilityFlag>> aoaSupport,
            Optional<Byte> extendedMacSupport) {
        mMinPhyVersionSupported = minPhyVersionSupported;
        mMaxPhyVersionSupported = maxPhyVersionSupported;
        mMinMacVersionSupported = minMacVersionSupported;
        mMaxMacVersionSupported = maxMacVersionSupported;
        mDeviceRoles = deviceRoles;
        mRangingMethod = rangingMethod;
        mStsConfig = stsConfig;
        mMultiNodeMode = multiNodeMode;
        mRangingTimeStruct = rangingTimeStruct;
        mScheduledMode = scheduledMode;
        mHoppingMode = hoppingMode;
        mBlockStriding = blockStriding;
        mUwbInitiationTime = uwbInitiationTime;
        mChannels = channels;
        mRframeConfig = rframeConfig;
        mCcConstraintLength = ccConstraintLength;
        mBprfParameterSet = bprfParameterSet;
        mHprfParameterSet = hprfParameterSet;
        mAoaSupport = aoaSupport;
        mExtendedMacSupport = extendedMacSupport;
    }

    /**
     * Converts the UwbCapabilities to the bytes which are combined per the TLV of CSML 8.5.3.2.
     */
    @NonNull
    public byte[] toBytes() {
        TlvBuffer.Builder uwbCapabilityBuilder = new TlvBuffer.Builder()
                .putByteArray(FIRA_PHY_VERSION_RANGE, new byte[]{
                        (byte) mMinPhyVersionSupported.getMajor(),
                        (byte) mMinPhyVersionSupported.getMinor(),
                        (byte) mMaxPhyVersionSupported.getMajor(),
                        (byte) mMaxPhyVersionSupported.getMinor(),
                })
                .putByteArray(FIRA_MAC_VERSION_RANGE, new byte[]{
                        (byte) mMinMacVersionSupported.getMajor(),
                        (byte) mMinMacVersionSupported.getMinor(),
                        (byte) mMaxMacVersionSupported.getMajor(),
                        (byte) mMaxMacVersionSupported.getMinor(),
                });
        if (mDeviceRoles.isPresent()) {
            byte deviceRoles = 0;
            if (mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT)
                    && mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT)) {
                deviceRoles = (byte) (deviceRoles | RESPONDER);
            }
            if (mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT)
                    && mDeviceRoles.get().contains(
                    FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT)) {
                deviceRoles = (byte) (deviceRoles | INITIATOR);
            }
            uwbCapabilityBuilder.putByte(DEVICE_ROLES, deviceRoles);
        }
        if (mRangingMethod.isPresent()) {
            byte rangingMethod = 0;
            if (mRangingMethod.get().contains(
                    FiraParams.RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT)) {
                rangingMethod = (byte) (rangingMethod | SS_TWR_DEFERRED);
            }
            if (mRangingMethod.get().contains(
                    FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT)) {
                rangingMethod = (byte) (rangingMethod | DS_TWR_DEFERRED);
            }
            uwbCapabilityBuilder.putByte(RANGING_METHOD, rangingMethod);
        }
        if (mStsConfig.isPresent()) {
            byte stsConfig = 0;
            if (mStsConfig.get().contains(FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT)) {
                stsConfig = (byte) (stsConfig | STATIC_STS);
            }
            if (mStsConfig.get().contains(FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT)) {
                stsConfig = (byte) (stsConfig | DYNAMIC_STS);
            }
            if (mStsConfig.get().contains(
                    FiraParams.StsCapabilityFlag
                            .HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT)) {
                stsConfig = (byte) (stsConfig | DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY);
            }
            uwbCapabilityBuilder.putByte(STS_CONFIG, stsConfig);
        }
        if (mMultiNodeMode.isPresent()) {
            byte multiMode = 0;
            if (mMultiNodeMode.get().contains(
                    FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT)) {
                multiMode = (byte) (multiMode | UNICAST);
            }
            if (mMultiNodeMode.get().contains(
                    FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT)) {
                multiMode = (byte) (multiMode | ONE_TO_MANY);
            }
            if (mMultiNodeMode.get().contains(
                    FiraParams.MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT)) {
                multiMode = (byte) (multiMode | MANY_TO_MANY);
            }
            uwbCapabilityBuilder.putByte(MULTI_NODE_MODE, multiMode);
        }
        mRangingTimeStruct.ifPresent(
                aByte -> uwbCapabilityBuilder.putByte(RANGING_TIME_STRUCT, aByte));

        mScheduledMode.ifPresent(
                aByte -> uwbCapabilityBuilder.putByte(SCHEDULED_MODE, aByte));

        mHoppingMode.ifPresent(aBoolean -> uwbCapabilityBuilder.putByte(HOPPING_MODE,
                (byte) (aBoolean ? 1 : 0)));

        mBlockStriding.ifPresent(aBoolean -> uwbCapabilityBuilder.putByte(BLOCK_STRIDING,
                (byte) (aBoolean ? 1 : 0)));

        mUwbInitiationTime.ifPresent(aBoolean -> uwbCapabilityBuilder.putByte(UWB_INITIATION_TIME,
                (byte) (aBoolean ? 1 : 0)));
        if (mChannels.isPresent()) {
            byte channels = 0;
            if (mChannels.get().contains(5)) {
                channels = (byte) (channels | CHANNEL_5);
            }
            if (mChannels.get().contains(6)) {
                channels = (byte) (channels | CHANNEL_6);
            }
            if (mChannels.get().contains(8)) {
                channels = (byte) (channels | CHANNEL_8);
            }
            if (mChannels.get().contains(9)) {
                channels = (byte) (channels | CHANNEL_9);
            }
            if (mChannels.get().contains(10)) {
                channels = (byte) (channels | CHANNEL_10);
            }
            if (mChannels.get().contains(12)) {
                channels = (byte) (channels | CHANNEL_12);
            }
            if (mChannels.get().contains(13)) {
                channels = (byte) (channels | CHANNEL_13);
            }
            if (mChannels.get().contains(14)) {
                channels = (byte) (channels | CHANNEL_14);
            }
            uwbCapabilityBuilder.putByte(CHANNELS, channels);
        }
        if (mRframeConfig.isPresent()) {
            byte rFrameConfig = 0;
            if (mRframeConfig.get().contains(
                    FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT)) {
                rFrameConfig = (byte) (rFrameConfig | SP0);
            }
            if (mRframeConfig.get().contains(
                    FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT)) {
                rFrameConfig = (byte) (rFrameConfig | SP1);
            }
            if (mRframeConfig.get().contains(
                    FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT)) {
                rFrameConfig = (byte) (rFrameConfig | SP3);
            }
            uwbCapabilityBuilder.putByte(RFRAME_CONFIG, rFrameConfig);
        }
        if (mCcConstraintLength.isPresent()) {
            byte ccConstraintLength = 0;
            if (mCcConstraintLength.get().contains(
                    FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT)) {
                ccConstraintLength = (byte) (ccConstraintLength | CC_CONSTRAINT_LENGTH_K3);
            }
            if (mCcConstraintLength.get().contains(
                    FiraParams.PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT)) {
                ccConstraintLength = (byte) (ccConstraintLength | CC_CONSTRAINT_LENGTH_K7);
            }
            uwbCapabilityBuilder.putByte(CC_CONSTRAINT_LENGTH, ccConstraintLength);
        }
        if (mBprfParameterSet.isPresent()) {
            byte bprfParameterSet = (byte) FlagEnum.toInt(mBprfParameterSet.get());
            uwbCapabilityBuilder.putByte(BPRF_PARAMETER_SETS, bprfParameterSet);
        }
        if (mHprfParameterSet.isPresent()) {
            byte hprfParameterSet = (byte) FlagEnum.toInt(mHprfParameterSet.get());
            uwbCapabilityBuilder.putByte(HPRF_PARAMETER_SETS, hprfParameterSet);
        }
        if (mAoaSupport.isPresent()) {
            byte aoaSupport = 0;
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_AZIMUTH_90);
            }
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_AZIMUTH_180);
            }
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_ELEVATION);
            }
            if (mAoaSupport.get().contains(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT)) {
                aoaSupport = (byte) (aoaSupport | AOA_FOM);
            }
            uwbCapabilityBuilder.putByte(AOA_SUPPORT, aoaSupport);
        }
        return uwbCapabilityBuilder.build().getByteArray();
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
    public static UwbCapability fromBytes(@NonNull byte[] data) {
        TlvDecoderBuffer uwbCapabilityTlv = new TlvDecoderBuffer(data, UWB_CAPABILITY_MAX_COUNT);
        uwbCapabilityTlv.parse();
        UwbCapability.Builder uwbCapabilityBuilder = new UwbCapability.Builder();

        if (isPresent(uwbCapabilityTlv, FIRA_PHY_VERSION_RANGE)) {
            byte[] firaPhyVersionRange = uwbCapabilityTlv.getByteArray(FIRA_PHY_VERSION_RANGE);
            if (firaPhyVersionRange.length == 4) {
                FiraProtocolVersion minVersion = new FiraProtocolVersion(firaPhyVersionRange[0],
                        firaPhyVersionRange[1]);
                FiraProtocolVersion maxVersion = new FiraProtocolVersion(firaPhyVersionRange[2],
                        firaPhyVersionRange[3]);
                uwbCapabilityBuilder.setMinPhyVersionSupported(minVersion);
                uwbCapabilityBuilder.setMaxPhyVersionSupported(maxVersion);
            }
        }
        if (isPresent(uwbCapabilityTlv, FIRA_MAC_VERSION_RANGE)) {
            byte[] firaMacVersionRange = uwbCapabilityTlv.getByteArray(FIRA_MAC_VERSION_RANGE);
            if (firaMacVersionRange.length == 4) {
                FiraProtocolVersion minVersion = new FiraProtocolVersion(firaMacVersionRange[0],
                        firaMacVersionRange[1]);
                FiraProtocolVersion maxVersion = new FiraProtocolVersion(firaMacVersionRange[2],
                        firaMacVersionRange[3]);
                uwbCapabilityBuilder.setMinMacVersionSupported(minVersion);
                uwbCapabilityBuilder.setMaxMacVersionSupported(maxVersion);
            }
        }
        if (isPresent(uwbCapabilityTlv, DEVICE_ROLES)) {
            EnumSet<FiraParams.DeviceRoleCapabilityFlag> deviceRoles = EnumSet.noneOf(
                    FiraParams.DeviceRoleCapabilityFlag.class);
            byte deviceRolesRaw = uwbCapabilityTlv.getByte(DEVICE_ROLES);
            if (isBitSet(deviceRolesRaw, INITIATOR)) {
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_INITIATOR_SUPPORT);
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT);
            }
            if (isBitSet(deviceRolesRaw, RESPONDER)) {
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
                deviceRoles.add(
                        FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);
            }
            uwbCapabilityBuilder.setDeviceRoles(Optional.of(deviceRoles));
        }
        if (isPresent(uwbCapabilityTlv, RANGING_METHOD)) {
            EnumSet<FiraParams.RangingRoundCapabilityFlag> rangingMethod = EnumSet.noneOf(
                    FiraParams.RangingRoundCapabilityFlag.class);
            byte rangingMethodRaw = uwbCapabilityTlv.getByte(RANGING_METHOD);
            if (isBitSet(rangingMethodRaw, SS_TWR_DEFERRED)) {
                rangingMethod.add(FiraParams.RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT);
            }
            if (isBitSet(rangingMethodRaw, DS_TWR_DEFERRED)) {
                rangingMethod.add(FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT);
            }
            uwbCapabilityBuilder.setRangingMethod(Optional.of(rangingMethod));
        }
        if (isPresent(uwbCapabilityTlv, STS_CONFIG)) {
            EnumSet<FiraParams.StsCapabilityFlag> stsConfig = EnumSet.noneOf(
                    FiraParams.StsCapabilityFlag.class);
            byte stsConfigRaw = uwbCapabilityTlv.getByte(STS_CONFIG);
            if (isBitSet(stsConfigRaw, STATIC_STS)) {
                stsConfig.add(FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigRaw, DYNAMIC_STS)) {
                stsConfig.add(FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
            }
            if (isBitSet(stsConfigRaw, DYNAMIC_STS_RESPONDER_SPECIFIC_SUBSESSION_KEY)) {
                stsConfig.add(
                        FiraParams.StsCapabilityFlag
                                .HAS_DYNAMIC_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT);
            }
            uwbCapabilityBuilder.setStsConfig(Optional.of(stsConfig));
        }
        if (isPresent(uwbCapabilityTlv, MULTI_NODE_MODE)) {
            EnumSet<FiraParams.MultiNodeCapabilityFlag> multiNodeMode = EnumSet.noneOf(
                    FiraParams.MultiNodeCapabilityFlag.class);
            byte multiNodeRaw = uwbCapabilityTlv.getByte(MULTI_NODE_MODE);
            if (isBitSet(multiNodeRaw, UNICAST)) {
                multiNodeMode.add(FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT);
            }
            if (isBitSet(multiNodeRaw, ONE_TO_MANY)) {
                multiNodeMode.add(FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT);
            }
            if (isBitSet(multiNodeRaw, MANY_TO_MANY)) {
                multiNodeMode.add(FiraParams.MultiNodeCapabilityFlag.HAS_MANY_TO_MANY_SUPPORT);
            }
            uwbCapabilityBuilder.setMultiMode(Optional.of(multiNodeMode));
        }
        if (isPresent(uwbCapabilityTlv, RANGING_TIME_STRUCT)) {
            uwbCapabilityBuilder.setRangingTimeStruct(Optional.of(
                    uwbCapabilityTlv.getByte(RANGING_TIME_STRUCT)));
        }
        if (isPresent(uwbCapabilityTlv, SCHEDULED_MODE)) {
            uwbCapabilityBuilder.setScheduledMode(Optional.of(
                    uwbCapabilityTlv.getByte(SCHEDULED_MODE)));
        }
        if (isPresent(uwbCapabilityTlv, HOPPING_MODE)) {
            uwbCapabilityBuilder.setHoppingMode(Optional.of(
                    uwbCapabilityTlv.getByte(HOPPING_MODE) == 1));
        }
        if (isPresent(uwbCapabilityTlv, BLOCK_STRIDING)) {
            uwbCapabilityBuilder.setBlockStriding(Optional.of(
                    uwbCapabilityTlv.getByte(BLOCK_STRIDING) == 1));
        }
        if (isPresent(uwbCapabilityTlv, UWB_INITIATION_TIME)) {
            uwbCapabilityBuilder.setUwbInitiationTime(Optional.of(
                    uwbCapabilityTlv.getByte(UWB_INITIATION_TIME) == 1));
        }
        if (isPresent(uwbCapabilityTlv, CHANNELS)) {
            List<Integer> channels = new ArrayList<>();
            byte channelsRaw = uwbCapabilityTlv.getByte(CHANNELS);
            if (isBitSet(channelsRaw, CHANNEL_5)) {
                channels.add(5);
            }
            if (isBitSet(channelsRaw, CHANNEL_6)) {
                channels.add(6);
            }
            if (isBitSet(channelsRaw, CHANNEL_8)) {
                channels.add(8);
            }
            if (isBitSet(channelsRaw, CHANNEL_9)) {
                channels.add(9);
            }
            if (isBitSet(channelsRaw, CHANNEL_10)) {
                channels.add(10);
            }
            if (isBitSet(channelsRaw, CHANNEL_12)) {
                channels.add(12);
            }
            if (isBitSet(channelsRaw, CHANNEL_13)) {
                channels.add(13);
            }
            if (isBitSet(channelsRaw, CHANNEL_14)) {
                channels.add(14);
            }
            uwbCapabilityBuilder.setChannels(Optional.of(channels));
        }
        if (isPresent(uwbCapabilityTlv, RFRAME_CONFIG)) {
            EnumSet<FiraParams.RframeCapabilityFlag> rFrameConfig = EnumSet.noneOf(
                    FiraParams.RframeCapabilityFlag.class);
            byte rFrameConfigRaw = uwbCapabilityTlv.getByte(RFRAME_CONFIG);
            if (isBitSet(rFrameConfigRaw, SP0)) {
                rFrameConfig.add(FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT);
            }
            if (isBitSet(rFrameConfigRaw, SP1)) {
                rFrameConfig.add(FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT);
            }
            if (isBitSet(rFrameConfigRaw, SP3)) {
                rFrameConfig.add(FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);
            }
            uwbCapabilityBuilder.setRframeConfig(Optional.of(rFrameConfig));
        }
        if (isPresent(uwbCapabilityTlv, CC_CONSTRAINT_LENGTH)) {
            EnumSet<FiraParams.PsduDataRateCapabilityFlag> ccConstraintLength = EnumSet.noneOf(
                    FiraParams.PsduDataRateCapabilityFlag.class);
            byte ccConstraintLengthRaw = uwbCapabilityTlv.getByte(CC_CONSTRAINT_LENGTH);
            if (isBitSet(ccConstraintLengthRaw, CC_CONSTRAINT_LENGTH_K3)) {
                ccConstraintLength.add(FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT);
            }
            if (isBitSet(ccConstraintLengthRaw, CC_CONSTRAINT_LENGTH_K7)) {
                ccConstraintLength.add(FiraParams.PsduDataRateCapabilityFlag.HAS_7M80_SUPPORT);
            }
            uwbCapabilityBuilder.setCcConstraintLength(Optional.of(ccConstraintLength));
        }
        if (isPresent(uwbCapabilityTlv, AOA_SUPPORT)) {
            EnumSet<FiraParams.AoaCapabilityFlag> aoaSupport = EnumSet.noneOf(
                    FiraParams.AoaCapabilityFlag.class);
            byte aoaSupportRaw = uwbCapabilityTlv.getByte(AOA_SUPPORT);
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
            uwbCapabilityBuilder.setAoaSupport(Optional.of(aoaSupport));
        }
        if (isPresent(uwbCapabilityTlv, BPRF_PARAMETER_SETS)) {
            byte bprfSets = uwbCapabilityTlv.getByte(BPRF_PARAMETER_SETS);
            int bprfSetsValue = Integer.valueOf(bprfSets);
            EnumSet<FiraParams.BprfParameterSetCapabilityFlag> bprfFlag;
            bprfFlag = FlagEnum.toEnumSet(bprfSetsValue,
                    FiraParams.BprfParameterSetCapabilityFlag.values());
            uwbCapabilityBuilder.setBprfParameterSet(Optional.of(bprfFlag));
        }
        if (isPresent(uwbCapabilityTlv, HPRF_PARAMETER_SETS)) {
            byte hprfSets = uwbCapabilityTlv.getByte(HPRF_PARAMETER_SETS);
            int hprfSetsValue = Integer.valueOf(hprfSets);
            EnumSet<FiraParams.HprfParameterSetCapabilityFlag> hprfFlag;
            hprfFlag = FlagEnum.toEnumSet(hprfSetsValue,
                    FiraParams.HprfParameterSetCapabilityFlag.values());
            uwbCapabilityBuilder.setHprfParameterSet(Optional.of(hprfFlag));
        }
        return uwbCapabilityBuilder.build();
    }

    /** Builder for UwbCapabilities */
    public static class Builder {
        // Set all default protocol version to FiRa 1.1
        private FiraProtocolVersion mMinPhyVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMaxPhyVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMinMacVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMaxMacVersionSupported = new FiraProtocolVersion(1, 1);
        private Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> mDeviceRoles =
                Optional.empty();
        private Optional<EnumSet<FiraParams.RangingRoundCapabilityFlag>> mRangingMethod =
                Optional.empty();
        private Optional<EnumSet<FiraParams.StsCapabilityFlag>> mStsConfig = Optional.empty();
        private Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> mMultiNodeMode =
                Optional.empty();
        private Optional<Byte> mRangingTimeStruct = Optional.empty();
        private Optional<Byte> mScheduledMode = Optional.empty();
        private Optional<Boolean> mHoppingMode = Optional.empty();
        private Optional<Boolean> mBlockStriding = Optional.empty();
        private Optional<Boolean> mUwbInitiationTime = Optional.empty();
        private Optional<List<Integer>> mChannels = Optional.empty();
        private Optional<EnumSet<FiraParams.RframeCapabilityFlag>> mRframeConfig = Optional.empty();
        private Optional<EnumSet<FiraParams.PsduDataRateCapabilityFlag>> mCcConstraintLength =
                Optional.empty();
        private Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> mBprfParameterSet =
                Optional.empty();
        private Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> mHprfParameterSet =
                Optional.empty();
        private Optional<EnumSet<FiraParams.AoaCapabilityFlag>> mAoaSupport = Optional.empty();
        private Optional<Byte> mExtendedMacSupport = Optional.empty();

        public UwbCapability.Builder setMinPhyVersionSupported(
                FiraProtocolVersion minPhyVersionSupported) {
            mMinPhyVersionSupported = minPhyVersionSupported;
            return this;
        }

        public UwbCapability.Builder setMaxPhyVersionSupported(
                FiraProtocolVersion maxPhyVersionSupported) {
            mMaxPhyVersionSupported = maxPhyVersionSupported;
            return this;
        }

        public UwbCapability.Builder setMinMacVersionSupported(
                FiraProtocolVersion minMacVersionSupported) {
            mMinMacVersionSupported = minMacVersionSupported;
            return this;
        }

        public UwbCapability.Builder setMaxMacVersionSupported(
                FiraProtocolVersion maxMacVersionSupported) {
            mMaxMacVersionSupported = maxMacVersionSupported;
            return this;
        }

        public UwbCapability.Builder setDeviceRoles(
                Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> deviceRoles) {
            mDeviceRoles = deviceRoles;
            return this;
        }

        public UwbCapability.Builder setRangingMethod(
                Optional<EnumSet<FiraParams.RangingRoundCapabilityFlag>> rangingMethod) {
            mRangingMethod = rangingMethod;
            return this;
        }

        public UwbCapability.Builder setStsConfig(
                Optional<EnumSet<FiraParams.StsCapabilityFlag>> stsConfig) {
            mStsConfig = stsConfig;
            return this;
        }

        public UwbCapability.Builder setMultiMode(
                Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> multiNodeMode) {
            mMultiNodeMode = multiNodeMode;
            return this;
        }

        public UwbCapability.Builder setRangingTimeStruct(Optional<Byte> rangingTimeStruct) {
            mRangingTimeStruct = rangingTimeStruct;
            return this;
        }

        public UwbCapability.Builder setScheduledMode(Optional<Byte> scheduledMode) {
            mScheduledMode = scheduledMode;
            return this;
        }

        public UwbCapability.Builder setHoppingMode(Optional<Boolean> hoppingMode) {
            mHoppingMode = hoppingMode;
            return this;
        }

        public UwbCapability.Builder setBlockStriding(Optional<Boolean> blockStriding) {
            mBlockStriding = blockStriding;
            return this;
        }

        public UwbCapability.Builder setUwbInitiationTime(Optional<Boolean> uwbInitiationTime) {
            mUwbInitiationTime = uwbInitiationTime;
            return this;
        }

        public UwbCapability.Builder setChannels(Optional<List<Integer>> channels) {
            mChannels = channels;
            return this;
        }

        public UwbCapability.Builder setMultiNodeMode(
                Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> multiNodeMode) {
            mMultiNodeMode = multiNodeMode;
            return this;
        }

        public UwbCapability.Builder setRframeConfig(
                Optional<EnumSet<FiraParams.RframeCapabilityFlag>> rframeConfig) {
            mRframeConfig = rframeConfig;
            return this;
        }

        public UwbCapability.Builder setCcConstraintLength(
                Optional<EnumSet<FiraParams.PsduDataRateCapabilityFlag>> ccConstraintLength) {
            mCcConstraintLength = ccConstraintLength;
            return this;
        }

        public UwbCapability.Builder setBprfParameterSet(
                Optional<EnumSet<FiraParams.BprfParameterSetCapabilityFlag>> bprfParameterSet) {
            mBprfParameterSet = bprfParameterSet;
            return this;
        }

        public UwbCapability.Builder setHprfParameterSet(
                Optional<EnumSet<FiraParams.HprfParameterSetCapabilityFlag>> hprfParameterSet) {
            mHprfParameterSet = hprfParameterSet;
            return this;
        }

        public UwbCapability.Builder setAoaSupport(
                Optional<EnumSet<FiraParams.AoaCapabilityFlag>> aoaSupport) {
            mAoaSupport = aoaSupport;
            return this;
        }

        public UwbCapability.Builder setExtendedMacSupport(Optional<Byte> extendedMacSupport) {
            mExtendedMacSupport = extendedMacSupport;
            return this;
        }

        public UwbCapability build() {
            return new UwbCapability(
                    mMinPhyVersionSupported,
                    mMaxPhyVersionSupported,
                    mMinMacVersionSupported,
                    mMaxMacVersionSupported,
                    mDeviceRoles,
                    mRangingMethod,
                    mStsConfig,
                    mMultiNodeMode,
                    mRangingTimeStruct,
                    mScheduledMode,
                    mHoppingMode,
                    mBlockStriding,
                    mUwbInitiationTime,
                    mChannels,
                    mRframeConfig,
                    mCcConstraintLength,
                    mBprfParameterSet,
                    mHprfParameterSet,
                    mAoaSupport,
                    mExtendedMacSupport
            );
        }
    }
}
