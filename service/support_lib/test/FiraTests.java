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

package com.google.uwb.support;

import static com.google.uwb.support.fira.FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
import static com.google.uwb.support.fira.FiraParams.AOA_TYPE_AZIMUTH_AND_ELEVATION;
import static com.google.uwb.support.fira.FiraParams.BPRF_PHR_DATA_RATE_6M81;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_8_BYTES;
import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_32;
import static com.google.uwb.support.fira.FiraParams.MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_MANY_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T32_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_HPRF;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_7M80;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.SFD_ID_VALUE_3;
import static com.google.uwb.support.fira.FiraParams.STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
import static com.google.uwb.support.fira.FiraParams.STS_LENGTH_128_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.STS_SEGMENT_COUNT_VALUE_2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraStatusCode;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FiraTests {
    @Test
    public void testOpenSessionParams() {
        FiraProtocolVersion protocolVersion = FiraParams.PROTOCOL_VERSION_1_1;
        int sessionId = 10;
        int deviceType = RANGING_DEVICE_TYPE_CONTROLEE;
        int deviceRole = RANGING_DEVICE_ROLE_INITIATOR;
        int rangingRoundUsage = RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
        int multiNodeMode = MULTI_NODE_MODE_MANY_TO_MANY;
        int addressMode = MAC_ADDRESS_MODE_8_BYTES;
        UwbAddress deviceAddress = UwbAddress.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        UwbAddress destAddress1 = UwbAddress.fromBytes(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        UwbAddress destAddress2 =
                UwbAddress.fromBytes(new byte[] {(byte) 0xFF, (byte) 0xFE, 3, 4, 5, 6, 7, 8});
        List<UwbAddress> destAddressList = new ArrayList<>();
        destAddressList.add(destAddress1);
        destAddressList.add(destAddress2);
        int initiationTimeMs = 100;
        int slotDurationRstu = 2400;
        int slotsPerRangingRound = 10;
        int rangingIntervalMs = 100;
        int blockStrideLength = 2;
        int maxRangingRoundRetries = 3;
        int sessionPriority = 100;
        boolean hasResultReportPhase = true;
        int measurementReportType = MEASUREMENT_REPORT_TYPE_INITIATOR_TO_RESPONDER;
        int inBandTerminationAttemptCount = 8;
        int channelNumber = 10;
        int preambleCodeIndex = 12;
        int rframeConfig = RFRAME_CONFIG_SP1;
        int prfMode = PRF_MODE_HPRF;
        int preambleDuration = PREAMBLE_DURATION_T32_SYMBOLS;
        int sfdId = SFD_ID_VALUE_3;
        int stsSegmentCount = STS_SEGMENT_COUNT_VALUE_2;
        int stsLength = STS_LENGTH_128_SYMBOLS;
        int psduDataRate = PSDU_DATA_RATE_7M80;
        int bprfPhrDataRate = BPRF_PHR_DATA_RATE_6M81;
        int fcsType = MAC_FCS_TYPE_CRC_32;
        boolean isTxAdaptivePayloadPowerEnabled = true;
        int stsConfig = STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY;
        int subSessionId = 24;
        byte[] vendorId = new byte[] {(byte) 0xFE, (byte) 0xDC};
        byte[] staticStsIV = new byte[] {(byte) 0xDF, (byte) 0xCE, (byte) 0xAB, 0x12, 0x34, 0x56};
        boolean isKeyRotationEnabled = true;
        int keyRotationRate = 15;
        int aoaResultRequest = AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
        int rangeDataNtfConfig = RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY;
        int rangeDataNtfProximityNear = 50;
        int rangeDataNtfProximityFar = 200;
        boolean hasTimeOfFlightReport = true;
        boolean hasAngleOfArrivalAzimuthReport = true;
        boolean hasAngleOfArrivalElevationReport = true;
        boolean hasAngleOfArrivalFigureOfMeritReport = true;
        int aoaType = AOA_TYPE_AZIMUTH_AND_ELEVATION;

        FiraOpenSessionParams params =
                new FiraOpenSessionParams.Builder()
                        .setProtocolVersion(protocolVersion)
                        .setSessionId(sessionId)
                        .setDeviceType(deviceType)
                        .setDeviceRole(deviceRole)
                        .setRangingRoundUsage(rangingRoundUsage)
                        .setMultiNodeMode(multiNodeMode)
                        .setDeviceAddress(deviceAddress)
                        .setDestAddressList(destAddressList)
                        .setInitiationTimeMs(initiationTimeMs)
                        .setSlotDurationRstu(slotDurationRstu)
                        .setSlotsPerRangingRound(slotsPerRangingRound)
                        .setRangingIntervalMs(rangingIntervalMs)
                        .setBlockStrideLength(blockStrideLength)
                        .setMaxRangingRoundRetries(maxRangingRoundRetries)
                        .setSessionPriority(sessionPriority)
                        .setMacAddressMode(addressMode)
                        .setHasResultReportPhase(hasResultReportPhase)
                        .setMeasurementReportType(measurementReportType)
                        .setInBandTerminationAttemptCount(inBandTerminationAttemptCount)
                        .setChannelNumber(channelNumber)
                        .setPreambleCodeIndex(preambleCodeIndex)
                        .setRframeConfig(rframeConfig)
                        .setPrfMode(prfMode)
                        .setPreambleDuration(preambleDuration)
                        .setSfdId(sfdId)
                        .setStsSegmentCount(stsSegmentCount)
                        .setStsLength(stsLength)
                        .setPsduDataRate(psduDataRate)
                        .setBprfPhrDataRate(bprfPhrDataRate)
                        .setFcsType(fcsType)
                        .setIsTxAdaptivePayloadPowerEnabled(isTxAdaptivePayloadPowerEnabled)
                        .setStsConfig(stsConfig)
                        .setSubSessionId(subSessionId)
                        .setVendorId(vendorId)
                        .setStaticStsIV(staticStsIV)
                        .setIsKeyRotationEnabled(isKeyRotationEnabled)
                        .setKeyRotationRate(keyRotationRate)
                        .setAoaResultRequest(aoaResultRequest)
                        .setRangeDataNtfConfig(rangeDataNtfConfig)
                        .setRangeDataNtfProximityNear(rangeDataNtfProximityNear)
                        .setRangeDataNtfProximityFar(rangeDataNtfProximityFar)
                        .setHasTimeOfFlightReport(hasTimeOfFlightReport)
                        .setHasAngleOfArrivalAzimuthReport(hasAngleOfArrivalAzimuthReport)
                        .setHasAngleOfArrivalElevationReport(hasAngleOfArrivalElevationReport)
                        .setHasAngleOfArrivalFigureOfMeritReport(
                                hasAngleOfArrivalFigureOfMeritReport)
                        .setAoaType(aoaType)
                        .build();

        assertEquals(params.getProtocolVersion(), protocolVersion);
        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getDeviceType(), deviceType);
        assertEquals(params.getDeviceRole(), deviceRole);
        assertEquals(params.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(params.getMultiNodeMode(), multiNodeMode);
        assertEquals(params.getDeviceAddress(), deviceAddress);
        assertEquals(params.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(params.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(params.getInitiationTimeMs(), initiationTimeMs);
        assertEquals(params.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(params.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(params.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(params.getBlockStrideLength(), blockStrideLength);
        assertEquals(params.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(params.getSessionPriority(), sessionPriority);
        assertEquals(params.getMacAddressMode(), addressMode);
        assertEquals(params.hasResultReportPhase(), hasResultReportPhase);
        assertEquals(params.getMeasurementReportType(), measurementReportType);
        assertEquals(params.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(params.getChannelNumber(), channelNumber);
        assertEquals(params.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(params.getRframeConfig(), rframeConfig);
        assertEquals(params.getPrfMode(), prfMode);
        assertEquals(params.getPreambleDuration(), preambleDuration);
        assertEquals(params.getSfdId(), sfdId);
        assertEquals(params.getStsSegmentCount(), stsSegmentCount);
        assertEquals(params.getStsLength(), stsLength);
        assertEquals(params.getPsduDataRate(), psduDataRate);
        assertEquals(params.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(params.getFcsType(), fcsType);
        assertEquals(params.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(params.getStsConfig(), stsConfig);
        assertEquals(params.getSubSessionId(), subSessionId);
        assertArrayEquals(params.getVendorId(), vendorId);
        assertArrayEquals(params.getStaticStsIV(), staticStsIV);
        assertEquals(params.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(params.getKeyRotationRate(), keyRotationRate);
        assertEquals(params.getAoaResultRequest(), aoaResultRequest);
        assertEquals(params.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(params.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear);
        assertEquals(params.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar);
        assertEquals(params.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(params.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(params.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                params.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(params.getAoaType(), aoaType);

        FiraOpenSessionParams fromBundle = FiraOpenSessionParams.fromBundle(params.toBundle());

        assertEquals(fromBundle.getRangingRoundUsage(), rangingRoundUsage);
        assertEquals(fromBundle.getMultiNodeMode(), multiNodeMode);

        assertEquals(fromBundle.getDeviceAddress(), deviceAddress);
        assertEquals(fromBundle.getDestAddressList().size(), destAddressList.size());
        for (int i = 0; i < destAddressList.size(); i++) {
            assertEquals(fromBundle.getDestAddressList().get(i), destAddressList.get(i));
        }

        assertEquals(fromBundle.getInitiationTimeMs(), initiationTimeMs);
        assertEquals(fromBundle.getSlotDurationRstu(), slotDurationRstu);
        assertEquals(fromBundle.getSlotsPerRangingRound(), slotsPerRangingRound);
        assertEquals(fromBundle.getRangingIntervalMs(), rangingIntervalMs);
        assertEquals(fromBundle.getBlockStrideLength(), blockStrideLength);
        assertEquals(fromBundle.getMaxRangingRoundRetries(), maxRangingRoundRetries);
        assertEquals(fromBundle.getSessionPriority(), sessionPriority);
        assertEquals(fromBundle.getMacAddressMode(), addressMode);
        assertEquals(fromBundle.hasResultReportPhase(), hasResultReportPhase);
        assertEquals(fromBundle.getMeasurementReportType(), measurementReportType);
        assertEquals(fromBundle.getInBandTerminationAttemptCount(), inBandTerminationAttemptCount);
        assertEquals(fromBundle.getChannelNumber(), channelNumber);
        assertEquals(fromBundle.getPreambleCodeIndex(), preambleCodeIndex);
        assertEquals(fromBundle.getRframeConfig(), rframeConfig);
        assertEquals(fromBundle.getPrfMode(), prfMode);
        assertEquals(fromBundle.getPreambleDuration(), preambleDuration);
        assertEquals(fromBundle.getSfdId(), sfdId);
        assertEquals(fromBundle.getStsSegmentCount(), stsSegmentCount);
        assertEquals(fromBundle.getStsLength(), stsLength);
        assertEquals(fromBundle.getPsduDataRate(), psduDataRate);
        assertEquals(fromBundle.getBprfPhrDataRate(), bprfPhrDataRate);
        assertEquals(fromBundle.getFcsType(), fcsType);
        assertEquals(fromBundle.isTxAdaptivePayloadPowerEnabled(), isTxAdaptivePayloadPowerEnabled);
        assertEquals(fromBundle.getStsConfig(), stsConfig);
        assertEquals(fromBundle.getSubSessionId(), subSessionId);
        assertArrayEquals(fromBundle.getVendorId(), vendorId);
        assertArrayEquals(fromBundle.getStaticStsIV(), staticStsIV);
        assertEquals(fromBundle.isKeyRotationEnabled(), isKeyRotationEnabled);
        assertEquals(fromBundle.getKeyRotationRate(), keyRotationRate);
        assertEquals(fromBundle.getAoaResultRequest(), aoaResultRequest);
        assertEquals(fromBundle.getRangeDataNtfConfig(), rangeDataNtfConfig);
        assertEquals(fromBundle.getRangeDataNtfProximityNear(), rangeDataNtfProximityNear);
        assertEquals(fromBundle.getRangeDataNtfProximityFar(), rangeDataNtfProximityFar);
        assertEquals(fromBundle.hasTimeOfFlightReport(), hasTimeOfFlightReport);
        assertEquals(fromBundle.hasAngleOfArrivalAzimuthReport(), hasAngleOfArrivalAzimuthReport);
        assertEquals(
                fromBundle.hasAngleOfArrivalElevationReport(), hasAngleOfArrivalElevationReport);
        assertEquals(
                fromBundle.hasAngleOfArrivalFigureOfMeritReport(),
                hasAngleOfArrivalFigureOfMeritReport);
        assertEquals(fromBundle.getAoaType(), aoaType);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testRangingReconfigureParams() {
        int sessionId = 10;
        int action = MULTICAST_LIST_UPDATE_ACTION_DELETE;
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setSessionId(sessionId)
                        .setAction(action)
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();
        assertEquals(params.getSessionId(), sessionId);
        assertEquals(params.getAction(), action);
        assertArrayEquals(params.getAddressList(), addressList);
        assertArrayEquals(params.getSubSessionIdList(), subSessionIdList);
        FiraRangingReconfigureParams fromBundle =
                FiraRangingReconfigureParams.fromBundle(params.toBundle());
        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getAction(), action);
        assertArrayEquals(fromBundle.getAddressList(), addressList);
        assertArrayEquals(fromBundle.getSubSessionIdList(), subSessionIdList);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    @Test
    public void testStatusCode() {
        int statusCode = STATUS_CODE_ERROR_ADDRESS_ALREADY_PRESENT;
        FiraStatusCode params = new FiraStatusCode.Builder().setStatusCode(statusCode).build();
        assertEquals(params.getStatusCode(), statusCode);

        FiraStatusCode fromBundle = FiraStatusCode.fromBundle(params.toBundle());
        assertEquals(fromBundle.getStatusCode(), statusCode);

        verifyProtocolPresent(params);
        verifyBundlesEqual(params, fromBundle);
    }

    private void verifyProtocolPresent(Params params) {
        assertTrue(Params.isProtocol(params.toBundle(), FiraParams.PROTOCOL_NAME));
    }

    private void verifyBundlesEqual(Params params, Params fromBundle) {
        PersistableBundle.kindofEquals(params.toBundle(), fromBundle.toBundle());
    }
}
