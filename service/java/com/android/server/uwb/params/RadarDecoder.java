/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.server.uwb.config.CapabilityParam.RADAR_SUPPORT;
import static com.android.server.uwb.config.CapabilityParam.RADAR_SWEEP_SAMPLES_SUPPORTED;
import static com.android.server.uwb.config.ConfigParam.ANTENNA_BITMAP_KEY;
import static com.android.server.uwb.config.ConfigParam.BITS_PER_SAMPLE_KEY;
import static com.android.server.uwb.config.ConfigParam.CHANNEL_NUMBER_KEY;
import static com.android.server.uwb.config.ConfigParam.GPIO_BITMAP_KEY;
import static com.android.server.uwb.config.ConfigParam.NUMBER_OF_BURSTS_KEY;
import static com.android.server.uwb.config.ConfigParam.PREAMBLE_CODE_INDEX_KEY;
import static com.android.server.uwb.config.ConfigParam.PREAMBLE_DURATION_KEY;
import static com.android.server.uwb.config.ConfigParam.PRF_MODE_KEY;
import static com.android.server.uwb.config.ConfigParam.RADAR_DATA_TYPE_KEY;
import static com.android.server.uwb.config.ConfigParam.RFRAME_CONFIG_KEY;
import static com.android.server.uwb.config.ConfigParam.RX_GAIN_KEY;
import static com.android.server.uwb.config.ConfigParam.SAMPLES_PER_SWEEP_KEY;
import static com.android.server.uwb.config.ConfigParam.SESSION_PRIORITY_KEY;
import static com.android.server.uwb.config.ConfigParam.SWEEP_OFFSET_KEY;
import static com.android.server.uwb.config.ConfigParam.RADAR_TIMING_PARAMS_KEY;
import static com.android.server.uwb.config.ConfigParam.TX_POWER_KEY;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.base.ProtocolVersion;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarRangingStartedParams;
import com.google.uwb.support.radar.RadarSpecificationParams;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Radar decoder */
public class RadarDecoder extends TlvDecoder {
    @Override
    public <T extends Params> T getParams(TlvDecoderBuffer tlvs, Class<T> paramsType,
            ProtocolVersion protocolVersion)
            throws IllegalArgumentException {
        if (RadarSpecificationParams.class.equals(paramsType)) {
            return (T) getRadarSpecificationParamsFromTlvBuffer(tlvs);
        } else if (RadarRangingStartedParams.class.equals(paramsType)) {
            return (T) getRadarRangingStartedParamsFromTlvBuffer(tlvs);
        }
        return null;
    }

    private static boolean isBitSet(int flags, int mask) {
        return (flags & mask) != 0;
    }

    private RadarSpecificationParams getRadarSpecificationParamsFromTlvBuffer(
            TlvDecoderBuffer tlvs) {
        RadarSpecificationParams.Builder builder = new RadarSpecificationParams.Builder();

        byte radarCapabilities = tlvs.getByte(RADAR_SUPPORT);
        if (isBitSet(radarCapabilities, RADAR_SWEEP_SAMPLES_SUPPORTED)) {
            builder.addRadarCapability(
                    RadarParams.RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT);
        }
        return builder.build();
    }

    private RadarRangingStartedParams getRadarRangingStartedParamsFromTlvBuffer(
            TlvDecoderBuffer tlvs) {
        byte[] radarTimingParams = tlvs.getByteArray(RADAR_TIMING_PARAMS_KEY);
        ByteBuffer buffer = ByteBuffer.wrap(radarTimingParams).order(ByteOrder.LITTLE_ENDIAN);
        return new RadarRangingStartedParams.Builder()
                .setBurstPeriod(buffer.getInt())
                .setSweepPeriod(buffer.getShort())
                .setSweepsPerBurst(buffer.get())
                .setSamplesPerSweep(tlvs.getByte(SAMPLES_PER_SWEEP_KEY))
                .setChannelNumber(tlvs.getByte(CHANNEL_NUMBER_KEY))
                .setSweepOffset(tlvs.getShort(SWEEP_OFFSET_KEY))
                .setRframeConfig(tlvs.getByte(RFRAME_CONFIG_KEY))
                .setPreambleDuration(tlvs.getByte(PREAMBLE_DURATION_KEY))
                .setPreambleCodeIndex(tlvs.getByte(PREAMBLE_CODE_INDEX_KEY))
                .setSessionPriority(tlvs.getByte(SESSION_PRIORITY_KEY))
                .setBitsPerSample(tlvs.getByte(BITS_PER_SAMPLE_KEY))
                .setPrfMode(tlvs.getByte(PRF_MODE_KEY))
                .setNumberOfBursts(tlvs.getShort(NUMBER_OF_BURSTS_KEY))
                .setRadarDataType(tlvs.getByte(RADAR_DATA_TYPE_KEY))
                .setAntennaBitmap(tlvs.getShort(ANTENNA_BITMAP_KEY))
                .setGpioBitmap(tlvs.getByte(GPIO_BITMAP_KEY))
                .setTxPower(tlvs.getByte(TX_POWER_KEY))
                .setRxGain(tlvs.getByte(RX_GAIN_KEY))
                .build();
    }
}
