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

import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_CHANNELS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_CHAPS_PER_SLOT;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_HOPPING_CONFIG_MODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_HOPPING_SEQUENCES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_PULSE_SHAPE_COMBOS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_RAN_MULTIPLIER;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_SYNC_CODES;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_UWB_CONFIGS;
import static android.hardware.uwb.fira_android.UwbVendorCapabilityTlvTypes.CCC_SUPPORTED_VERSIONS;

import com.android.server.uwb.config.ConfigParam;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccProtocolVersion;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccSpecificationParams;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TODO (b/208678993): Verify if this param parsing is correct.
 */
public class CccDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramsType)
            throws IllegalArgumentException {
        if (CccRangingStartedParams.class.equals(paramsType)) {
            return (T) getCccRangingStartedParamsFromTlvBuffer(tlvs);
        }
        if (CccSpecificationParams.class.equals(paramsType)) {
            return (T) getCccSpecificationParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    public CccRangingStartedParams getCccRangingStartedParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        byte[] hopModeKey = tlvs.getByteArray(ConfigParam.HOP_MODE_KEY);
        int hopModeKeyInt = ByteBuffer.wrap(hopModeKey).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return new CccRangingStartedParams.Builder()
                // STS_Index0  0 - 0x3FFFFFFFF
                .setStartingStsIndex(tlvs.getInt(ConfigParam.STS_INDEX))
                .setHopModeKey(hopModeKeyInt)
                //  UWB_Time0 0 - 0xFFFFFFFFFFFFFFFF  UWB_INITIATION_TIME
                .setUwbTime0(tlvs.getLong(ConfigParam.UWB_TIME0))
                // RANGING_INTERVAL = RAN_Multiplier * 96
                .setRanMultiplier(tlvs.getInt(ConfigParam.RANGING_INTERVAL) / 96)
                .setSyncCodeIndex(tlvs.getByte(ConfigParam.PREAMBLE_CODE_INDEX))
                .build();
    }

     // TODO(b/208678993): Plumb the output of GetCapsInfo to getSpecificationInfo API using this.
    public CccSpecificationParams getCccSpecificationParamsFromTlvBuffer(TlvDecoderBuffer tlvs) {
        CccSpecificationParams.Builder builder = new CccSpecificationParams.Builder();
        byte[] versions = tlvs.getByteArray(CCC_SUPPORTED_VERSIONS);
        if (versions.length % 2 != 0) {
            throw new IllegalArgumentException("Invalid supported protocol versions len "
                    + versions.length);
        }
        for (int i = 0; i < versions.length; i += 2) {
            builder.addProtocolVersion(CccProtocolVersion.fromBytes(versions, i));
        }
        byte[] configs = tlvs.getByteArray(CCC_SUPPORTED_UWB_CONFIGS);
        for (int i = 0; i < configs.length; i++) {
            builder.addUwbConfig(configs[i]);
        }
        byte[] pulse_shape_combos = tlvs.getByteArray(CCC_SUPPORTED_PULSE_SHAPE_COMBOS);
        for (int i = 0; i < pulse_shape_combos.length; i++) {
            builder.addPulseShapeCombo(CccPulseShapeCombo.fromBytes(pulse_shape_combos, i));
        }
        builder.setRanMultiplier(tlvs.getInt(CCC_SUPPORTED_RAN_MULTIPLIER));
        byte[] chaps_per_slot = tlvs.getByteArray(CCC_SUPPORTED_CHAPS_PER_SLOT);
        for (int i = 0; i < chaps_per_slot.length; i++) {
            builder.addChapsPerSlot(chaps_per_slot[i]);
        }
        byte[] sync_codes = tlvs.getByteArray(CCC_SUPPORTED_SYNC_CODES);
        for (int i = 0; i < sync_codes.length; i++) {
            builder.addSyncCode(sync_codes[i]);
        }
        byte[] channels = tlvs.getByteArray(CCC_SUPPORTED_CHANNELS);
        for (int i = 0; i < channels.length; i++) {
            builder.addChannel(channels[i]);
        }
        byte[] hoppingConfigModes = tlvs.getByteArray(CCC_SUPPORTED_HOPPING_CONFIG_MODES);
        for (int i = 0; i < hoppingConfigModes.length; i++) {
            builder.addHoppingConfigMode(hoppingConfigModes[i]);
        }
        byte[] hoppingSequences = tlvs.getByteArray(CCC_SUPPORTED_HOPPING_SEQUENCES);
        for (int i = 0; i < hoppingSequences.length; i++) {
            builder.addHoppingSequence(hoppingSequences[i]);
        }
        return builder.build();
    }
}
