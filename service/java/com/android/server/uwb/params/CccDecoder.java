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

import com.android.server.uwb.config.ConfigParam;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccRangingStartedParams;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TODO (b/208678993): Verify if this param parsing is correct.
 */
public class CccDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramsType) {
        if (CccRangingStartedParams.class.equals(paramsType)) {
            return (T) getCccRangingStartedParamsFromTlvBuffer(tlvs);
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
}
