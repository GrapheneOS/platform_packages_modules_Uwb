/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb.params;

import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.INITIATION_TIME_MS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_AOA_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_BPRF_PHR_DATA_RATES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_CHANNELS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_DEVICE_ROLES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_MAC_FCS_CRC_TYPES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_MULTI_NODE_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_PREAMBLE_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_PRF_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_PSDU_DATA_RATES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_RANGING_ROUND_USAGE_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_RFRAME_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_SFD_IDS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_STS_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTED_STS_SEGEMENTS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTS_ADAPTIVE_PAYLOAD_POWER;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTS_BLOCK_STRIDING;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.SUPPORTS_NON_DEFERRED_MODE;

import com.google.uwb.support.base.FlagEnum;
import com.google.uwb.support.base.Params;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FiraDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramType) {
        if (FiraSpecificationParams.class.equals(paramType)) {
            return (T) getFiraSpecificationParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    // TODO(b/208678993): Plumb the output of GetCapsInfo to getSpecificationInfo API using this.
    public FiraSpecificationParams getFiraSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        FiraSpecificationParams.Builder builder = new FiraSpecificationParams.Builder();
        byte[] channels = tlvs.getByteArray(SUPPORTED_CHANNELS);
        builder.setSupportedChannels(
                IntStream.range(0, channels.length)
                        .map(i -> channels[i])
                        .boxed()
                        .collect(Collectors.toList()));
        int aoaModes = tlvs.getInt(SUPPORTED_AOA_MODES);
        builder.setAoaCapabilities(
                FlagEnum.toEnumSet(aoaModes, FiraParams.AoaCapabilityFlag.values()));
        int deviceRoles = tlvs.getInt(SUPPORTED_DEVICE_ROLES);
        builder.setDeviceRoleCapabilities(
                FlagEnum.toEnumSet(deviceRoles, FiraParams.DeviceRoleCapabilityFlag.values()));
        builder.hasBlockStridingSupport(tlvs.getByte(SUPPORTS_BLOCK_STRIDING) == 1);
        builder.hasNonDeferredModeSupport(tlvs.getByte(SUPPORTS_NON_DEFERRED_MODE) == 1);
        builder.hasTxAdaptivePayloadPowerSupport(
                tlvs.getByte(SUPPORTS_ADAPTIVE_PAYLOAD_POWER) == 1);
        builder.setInitiationTimeMs(tlvs.getInt(INITIATION_TIME_MS));
        int macFcsCrcTypes = tlvs.getInt(SUPPORTED_MAC_FCS_CRC_TYPES);
        builder.setMacFcsCrcCapabilities(
                FlagEnum.toEnumSet(macFcsCrcTypes, FiraParams.MacFcsCrcCapabilityFlag.values()));
        int multiNodeModes = tlvs.getInt(SUPPORTED_MULTI_NODE_MODES);
        builder.setMultiNodeCapabilities(
                FlagEnum.toEnumSet(multiNodeModes, FiraParams.MultiNodeCapabilityFlag.values()));
        int preambleModes = tlvs.getInt(SUPPORTED_PREAMBLE_MODES);
        builder.setPreambleCapabilities(
                FlagEnum.toEnumSet(preambleModes, FiraParams.PreambleCapabilityFlag.values()));
        int prfModes = tlvs.getInt(SUPPORTED_PRF_MODES);
        builder.setPrfCapabilities(
                FlagEnum.toEnumSet(prfModes, FiraParams.PrfCapabilityFlag.values()));
        int rangingRoundUsages = tlvs.getInt(SUPPORTED_RANGING_ROUND_USAGE_MODES);
        builder.setRangingRoundCapabilities(
                FlagEnum.toEnumSet(rangingRoundUsages,
                        FiraParams.RangingRoundCapabilityFlag.values()));
        int rframeModes = tlvs.getInt(SUPPORTED_RFRAME_MODES);
        builder.setRframeCapabilities(
                FlagEnum.toEnumSet(rframeModes, FiraParams.RframeCapabilityFlag.values()));
        int sfdIds = tlvs.getInt(SUPPORTED_SFD_IDS);
        builder.setSfdCapabilities(
                FlagEnum.toEnumSet(sfdIds, FiraParams.SfdCapabilityFlag.values()));
        int stsModes = tlvs.getInt(SUPPORTED_STS_MODES);
        builder.setStsCapabilities(
                FlagEnum.toEnumSet(stsModes, FiraParams.StsCapabilityFlag.values()));
        int stsSegments = tlvs.getInt(SUPPORTED_STS_SEGEMENTS);
        builder.setStsSegmentsCapabilities(
                FlagEnum.toEnumSet(stsSegments, FiraParams.StsSegmentsCapabilityFlag.values()));
        int bprfPhrDataRates = tlvs.getInt(SUPPORTED_BPRF_PHR_DATA_RATES);
        builder.setBprfPhrDataRateCapabilities(
                FlagEnum.toEnumSet(bprfPhrDataRates,
                        FiraParams.BprfPhrDataRateCapabilityFlag.values()));
        int psduDataRates = tlvs.getInt(SUPPORTED_PSDU_DATA_RATES);
        builder.setPsduDataRateCapabilities(
                FlagEnum.toEnumSet(psduDataRates, FiraParams.PsduDataRateCapabilityFlag.values()));
        return builder.build();
    }
}
