/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.google.uwb.support.rftest.RfTestOpenSessionParams;
import com.google.uwb.support.base.ProtocolVersion;
import android.uwb.UwbAddress;

import com.android.modules.utils.build.SdkLevel;

/** RfTest encoder */
public class RfTestEncoder extends TlvEncoder {
    @Override
    public TlvBuffer getTlvBuffer(final Params param, final ProtocolVersion protocolVersion) {
        if (param instanceof RfTestOpenSessionParams) {
            return getTlvBufferFromRfTestOpenSessionParams(param);
        }
        return null;
    }

    public TlvBuffer getRfTestTlvBuffer(final Params param) {
        if (param instanceof RfTestOpenSessionParams) {
            return getTlvBufferFromRfTestParams(param);
        }
        return null;
    }

    private TlvBuffer getTlvBufferFromRfTestParams(final Params param) {
        RfTestOpenSessionParams params = (RfTestOpenSessionParams) param;

        TlvBuffer.Builder tlvBufferBuilder = new TlvBuffer.Builder()
                .putInt(ConfigParam.NUMBER_OF_PACKETS, params.getNumberOfPackets())
                .putInt(ConfigParam.T_GAP, params.getTgap())
                .putInt(ConfigParam.T_START, params.getTstart())
                .putInt(ConfigParam.T_WIN, params.getTwin())
                .putByte(ConfigParam.RANDOMIZE_PSDU, (byte) params.getRandomizePsdu())
                .putByte(ConfigParam.PHR_RANGING_BIT, (byte) params.getPhrRangingBit())
                .putInt(ConfigParam.RMARKER_TX_START, params.getRmarkerTxStart())
                .putInt(ConfigParam.RMARKER_RX_START, params.getRmarkerRxStart())
                .putByte(ConfigParam.STS_INDEX_AUTO_INCR, (byte) params.getStsIndexAutoIncr())
                .putByte(ConfigParam.STS_DETECT_BITMAP, (byte) params.getStsDetectBitmap());

        return tlvBufferBuilder.build();
    }

    private TlvBuffer getTlvBufferFromRfTestOpenSessionParams(final Params baseParam) {
        RfTestOpenSessionParams params = (RfTestOpenSessionParams) baseParam;

        TlvBuffer.Builder tlvBufferBuilder = new TlvBuffer.Builder()
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannelNumber())
                .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                        (byte) params.getDestAddressList().size())
                .putByteArray(ConfigParam.DEVICE_MAC_ADDRESS, params.getDeviceAddress().size(),
                    getComputedMacAddress(params.getDeviceAddress()))
                .putShort(ConfigParam.SLOT_DURATION, (short) params.getSlotDurationRstu())
                .putInt(ConfigParam.STS_INDEX, params.getStsIndex())
                .putByte(ConfigParam.MAC_FCS_TYPE, (byte) params.getFcsType())
                .putByte(ConfigParam.DEVICE_ROLE, (byte) params.getDeviceRole())
                .putByte(ConfigParam.RFRAME_CONFIG, (byte) params.getRframeConfig())
                .putByte(ConfigParam.PREAMBLE_CODE_INDEX, (byte) params.getPreambleCodeIndex())
                .putByte(ConfigParam.SFD_ID, (byte) params.getSfdId())
                .putByte(ConfigParam.PSDU_DATA_RATE, (byte) params.getPsduDataRate())
                .putByte(ConfigParam.PREAMBLE_DURATION, (byte) params.getPreambleDuration())
                .putByte(ConfigParam.PRF_MODE, (byte) params.getPrfMode())
                .putByte(ConfigParam.NUMBER_OF_STS_SEGMENTS, (byte) params.getStsSegmentCount());
        return tlvBufferBuilder.build();
    }

    private static byte[] getComputedMacAddress(final UwbAddress address) {
        if (!SdkLevel.isAtLeastU()) {
            return TlvUtil.getReverseBytes(address.toBytes());
        }
        return address.toBytes();
    }
}
