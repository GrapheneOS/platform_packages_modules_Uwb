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
package com.android.uwb;

import static com.android.uwb.util.TlvUtil.getBytesWithRightPadding;
import static com.android.uwb.util.TlvUtil.getLeBytes;

import android.util.Log;
import android.uwb.UwbAddress;

import com.android.uwb.config.ConfigParam;
import com.android.uwb.data.UwbCccConstants;
import com.android.uwb.data.UwbUciConstants;
import com.android.uwb.jni.NativeUwbManager;
import com.android.uwb.util.TlvBuffer;
import com.android.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class UwbConfigurationManager {
    private static final String TAG = "UwbConfManager";

    NativeUwbManager mNativeUwbManager;

    public UwbConfigurationManager(NativeUwbManager nativeUwbManager) {
        mNativeUwbManager = nativeUwbManager;
    }

    public int setAppConfigurations(int sessionId, String protocolName,
            Params params) { //openParams
        int status = UwbUciConstants.STATUS_CODE_FAILED;
        byte[] tlvByteArray = null;
        int noOfParams = 0;
        if (protocolName.equals(FiraParams.PROTOCOL_NAME)) {
            TlvBuffer tlvBuffer = getTlvBufferFromFiraOpenSessionParams(
                    (FiraOpenSessionParams) params);
            tlvByteArray = tlvBuffer.getByteArray();
            noOfParams = tlvBuffer.getNoOfParams();
        } else if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
            TlvBuffer tlvBuffer = null;

            if (params instanceof CccOpenRangingParams) {
                Log.i(TAG, "CccOpenRangingParams ");
                tlvBuffer = getTlvBufferFromCccOpenRangingParams(
                        (CccOpenRangingParams) params);
            } else if (params instanceof CccRangingStartedParams) {
                Log.i(TAG, "CccRangingStartedParams ");
                tlvBuffer = getTlvBufferFromCccRangingStartedParams(
                        (CccRangingStartedParams) params);
            }

            tlvByteArray = tlvBuffer.getByteArray();
            noOfParams = tlvBuffer.getNoOfParams();
        }

        if (tlvByteArray == null) {
            return status;
        }

        Log.d(TAG, "noOfParmas: " + noOfParams);
        byte[] appConfig = mNativeUwbManager.setAppConfigurations(sessionId, noOfParams,
                tlvByteArray.length, tlvByteArray);
        Log.i(TAG, "setAppConfigurations respData: " + UwbUtil.toHexString(appConfig));
        if ((appConfig != null) && (appConfig.length > 0)) {
            int offset = 0; // Reset index to Zero
            status = appConfig[offset++];
        } else {
            Log.e(TAG, "appConfigList is null or size of appConfigList is zero");
            status = UwbUciConstants.STATUS_CODE_FAILED;
        }

        return status;
    }

    public TlvBuffer getTlvBufferFromFiraOpenSessionParams(FiraOpenSessionParams params) {
        ByteBuffer dstAAddressList = ByteBuffer.allocate(1024);
        int addressMode = params.getMacAddressMode();
        for (UwbAddress address : params.getDestAddressList()) {
            dstAAddressList.put(address.toBytes());
        }

        int deviceType =
                (params.getDeviceType() == FiraOpenSessionParams.RANGING_DEVICE_TYPE_CONTROLLER)
                ? 1 : 0;
        int resultReportConfig = getResultReportConfig(params);
        int rangingRoundControl = getRangingRoundControl(params);

        TlvBuffer tlvBuffer = new TlvBuffer.Builder()
                .putByte(ConfigParam.DEVICE_TYPE, (byte) deviceType)
                .putByte(ConfigParam.RANGING_ROUND_USAGE, (byte) params.getRangingRoundUsage())
                .putByte(ConfigParam.STS_CONFIG, (byte) params.getStsConfig())
                .putByte(ConfigParam.MULTI_NODE_MODE, (byte) params.getMultiNodeMode())
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannelNumber())
                .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                        (byte) params.getDestAddressList().size())
                .putByteArray(ConfigParam.DEVICE_MAC_ADDRESS, params.getDeviceAddress().size(),
                        params.getDeviceAddress().toBytes())
                .putByteArray(ConfigParam.DST_MAC_ADDRESS, dstAAddressList.position(),
                        Arrays.copyOf(dstAAddressList.array(), dstAAddressList.position()))
                .putShort(ConfigParam.SLOT_DURATION, (short) params.getSlotDurationRstu())
                .putInt(ConfigParam.RANGING_INTERVAL, params.getRangingIntervalMs())
                .putByte(ConfigParam.MAC_FCS_TYPE, (byte) params.getFcsType())
                .putByte(ConfigParam.RANGING_ROUND_CONTROL,
                        (byte) rangingRoundControl/* params.getMeasurementReportType()*/)
                .putByte(ConfigParam.AOA_RESULT_REQ, (byte) params.getAoaResultRequest())
                .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG, (byte) params.getRangeDataNtfConfig())
                .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_NEAR,
                        (short) params.getRangeDataNtfProximityNear())
                .putShort(ConfigParam.RANGE_DATA_NTF_PROXIMITY_FAR,
                        (short) params.getRangeDataNtfProximityFar())
                .putByte(ConfigParam.DEVICE_ROLE, (byte) params.getDeviceRole())
                .putByte(ConfigParam.RFRAME_CONFIG, (byte) params.getRframeConfig())
                .putByte(ConfigParam.PREAMBLE_CODE_INDEX, (byte) params.getPreambleCodeIndex())
                .putByte(ConfigParam.SFD_ID, (byte) params.getSfdId())
                .putByte(ConfigParam.PSDU_DATA_RATE, (byte) params.getPsduDataRate())
                .putByte(ConfigParam.PREAMBLE_DURATION, (byte) params.getPreambleDuration())
                .putByte(ConfigParam.SLOTS_PER_RR, (byte) params.getSlotsPerRangingRound())
                .putByte(ConfigParam.TX_ADAPTIVE_PAYLOAD_POWER,
                        params.isTxAdaptivePayloadPowerEnabled() ? (byte) 1 : (byte) 0)
                .putByte(ConfigParam.PRF_MODE, (byte) params.getPrfMode())
                .putByte(ConfigParam.KEY_ROTATION,
                        params.isKeyRotationEnabled() ? (byte) 1 : (byte) 0)
                .putByte(ConfigParam.KEY_ROTATION_RATE, (byte) params.getKeyRotationRate())
                .putByte(ConfigParam.SESSION_PRIORITY, (byte) params.getSessionPriority())
                .putByte(ConfigParam.MAC_ADDRESS_MODE, (byte) params.getMacAddressMode())
                .putByteArray(ConfigParam.VENDOR_ID, ConfigParam.VENDOR_ID_BYTE_COUNT,
                        params.getVendorId())
                .putByteArray(ConfigParam.STATIC_STS_IV, ConfigParam.STATIC_STS_IV_BYTE_COUNT,
                        params.getStaticStsIV())
                .putByte(ConfigParam.NUMBER_OF_STS_SEGMENTS, (byte) params.getStsSegmentCount())
                .putShort(ConfigParam.MAX_RR_RETRY, (short) params.getMaxRangingRoundRetries())
                .putInt(ConfigParam.UWB_INITIATION_TIME, params.getInitiationTimeMs())
                .putByte(ConfigParam.HOPPING_MODE,
                        (byte) params.getHoppingMode())
                .putByte(ConfigParam.BLOCK_STRIDE_LENGTH, (byte) params.getBlockStrideLength())
                .putByte(ConfigParam.RESULT_REPORT_CONFIG, (byte) resultReportConfig)
                .putByte(ConfigParam.IN_BAND_TERMINATION_ATTEMPT_COUNT,
                        (byte) params.getInBandTerminationAttemptCount())
                // TODO - Remove comment after find the reason
                //  Currently, SetAppConfig command fail if subsession id is add
               // .putInt(ConfigParam.SUB_SESSION_ID, params.getSubSessionId())
                .putByte(ConfigParam.BPRF_PHR_DATA_RATE, (byte) params.getBprfPhrDataRate())
                .putByte(ConfigParam.STS_LENGTH, (byte) params.getStsLength())
                .build();
        return tlvBuffer;
    }

    public TlvBuffer getTlvBufferFromCccOpenRangingParams(CccOpenRangingParams params) {

        int hoppingConfig = params.getHoppingConfigMode();
        int hoppingSequence = params.getHoppingSequence();

        int hoppingMode = CccParams.HOPPING_CONFIG_MODE_NONE;

        switch (hoppingConfig) {

            case CccParams.HOPPING_CONFIG_MODE_CONTINUOUS:
                if (hoppingSequence == CccParams.HOPPING_SEQUENCE_DEFAULT) {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_CONTINUOUS_DEFAULT;
                } else {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_CONTINUOUS_AES;
                }
                break;
            case CccParams.HOPPING_CONFIG_MODE_ADAPTIVE:
                if (hoppingSequence == CccParams.HOPPING_SEQUENCE_DEFAULT) {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_MODE_ADAPTIVE_DEFAULT;
                } else {
                    hoppingMode = UwbCccConstants.HOPPING_CONFIG_MODE_MODE_ADAPTIVE_AES;
                }
                break;
        }

        TlvBuffer tlvBuffer = new TlvBuffer.Builder()
                .putByte(ConfigParam.DEVICE_TYPE,
                        (byte) UwbUciConstants.DEVICE_TYPE_CONTROLEE) // DEVICE_TYPE
                .putByte(ConfigParam.STS_CONFIG,
                        (byte) UwbUciConstants.STS_MODE_DYNAMIC) // STS_CONFIG
                .putByte(ConfigParam.CHANNEL_NUMBER, (byte) params.getChannel()) // CHANNEL_ID
                .putByte(ConfigParam.NUMBER_OF_CONTROLEES,
                        (byte) params.getNumResponderNodes()) // NUMBER_OF_ANCHORS
                .putInt(ConfigParam.RANGING_INTERVAL,
                        params.getRanMultiplier() * 96) //RANGING_INTERVAL = RAN_Multiplier * 96
                .putByte(ConfigParam.RANGE_DATA_NTF_CONFIG,
                        (byte) UwbUciConstants.RANGE_DATA_NTF_CONFIG_DISABLE) // RNG_DATA_NTF
                .putByte(ConfigParam.DEVICE_ROLE,
                        (byte) UwbUciConstants.RANGING_DEVICE_ROLE_INITIATOR) // DEVICE_ROLE
                .putByte(ConfigParam.MULTI_NODE_MODE,
                        (byte) FiraParams.MULTI_NODE_MODE_ONE_TO_MANY) // MULTI_NODE_MODE
                .putByte(ConfigParam.SLOTS_PER_RR,
                        (byte) params.getNumSlotsPerRound()) // SLOTS_PER_RR
                .putByte(ConfigParam.KEY_ROTATION, (byte) 0X01) // KEY_ROTATION
                .putByte(ConfigParam.HOPPING_MODE, (byte) hoppingMode) // HOPPING_MODE
                .putByteArray(ConfigParam.RANGING_PROTOCOL_VER,
                        ConfigParam.RANGING_PROTOCOL_VER_BYTE_COUNT,
                        params.getProtocolVersion().toBytes()) // RANGING_PROTOCOL_VER
                .putShort(ConfigParam.UWB_CONFIG_ID, (short) params.getUwbConfig()) // UWB_CONFIG_ID
                .putByte(ConfigParam.PULSESHAPE_COMBO,
                        params.getPulseShapeCombo().toBytes()[0]) // PULSESHAPE_COMBO
                .putShort(ConfigParam.URSK_TTL, (short) 0x2D0) // URSK_TTL
                .build();

        return tlvBuffer;
    }

    public TlvBuffer getTlvBufferFromCccRangingStartedParams(CccRangingStartedParams params) {

        TlvBuffer tlvBuffer = new TlvBuffer.Builder()
                .putInt(ConfigParam.STS_INDEX,
                        params.getStartingStsIndex()) // STS_Index0  0 - 0x3FFFFFFFF
                .putByteArray(ConfigParam.HOP_MODE_KEY, getBytesWithRightPadding(
                        ConfigParam.HOP_MODE_KEY_BYTE, getLeBytes(params.getHopModeKey())))
                //  UWB_Time0 0 - 0xFFFFFFFFFFFFFFFF  UWB_INITIATION_TIME
                .putInt(ConfigParam.UWB_INITIATION_TIME, (int) params.getUwbTime0())
                .build();

        return tlvBuffer;
    }

    // Merged data from other parameter values
    private int getResultReportConfig(FiraOpenSessionParams params) {
        int resultReportConfig = 0x00;
        resultReportConfig |= params.hasTimeOfFlightReport() ? 0x01 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalAzimuthReport() ? 0x02 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalElevationReport() ? 0x04 : 0x00;
        resultReportConfig |= params.hasAngleOfArrivalFigureOfMeritReport() ? 0x08 : 0x00;
        return resultReportConfig;
    }

    private int getRangingRoundControl(FiraOpenSessionParams params) {
        //RANGING_ROUND_CONTROL
        int rangingRoundControl = 0x02;

        // b0 : Ranging Result Report Message
        rangingRoundControl |= params.hasResultReportPhase() ? 0x01 : 0x00;

        // b7 : Measurement Report Message
        if (params.getMeasurementReportType()
                == FiraParams.MEASUREMENT_REPORT_TYPE_RESPONDER_TO_INITIATOR) {
            rangingRoundControl |= 0x80;
        }
        return rangingRoundControl;
    }
}
