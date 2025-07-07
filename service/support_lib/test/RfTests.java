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

package com.google.uwb.support;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.rftest.UwbTestRxResult;
import com.android.server.uwb.rftest.UwbTestSrRxResult;
import com.android.server.uwb.util.UwbUtil;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.rftest.RfTestLoopbackResult;
import com.google.uwb.support.rftest.RfTestOpenSessionParams;
import com.google.uwb.support.rftest.RfTestParams;
import com.google.uwb.support.rftest.RfTestPerRxResult;
import com.google.uwb.support.rftest.RfTestRxResult;
import com.google.uwb.support.rftest.RfTestSrRxResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class RfTests {

    private static final int UWB_CHANNEL = 5;
    private static final int NO_OF_CONTROLEE = 3;
    private static final UwbAddress DEVICE_ADDRESS =
            UwbAddress.fromBytes(new byte[] {0x01, 0x02});
    private static final List<UwbAddress> DEST_ADDRESS_LIST = new ArrayList<>();
    private static final int SLOT_DURATION_RSTU = 100;
    private static final int STS_INDEX = 2;
    private static final int FCS_TYPE = 1;
    private static final int DEVICE_ROLE = 0;
    private static final int RFRAME_CONFIG = 2;
    private static final int PREAMBLE_CODE_INDEX = 9;
    private static final int SFD_ID = 5;
    private static final int PSDU_DATA_RATE = 680;
    private static final int PREAMBLE_DURATION = 64;
    private static final int PRF_MODE = 4;
    private static final int STS_SEGMENT_COUNT = 3;
    private static final int NO_OF_PACKETS = 100;
    private static final int TGAP = 2000;
    private static final int TSTART = 100;
    private static final int TWIN = 200;
    private static final int RANDOMIZE_PSDU = 1;
    private static final int PHR_RANGING_BIT = 1;
    private static final int RMARKER_TX_START = 500;
    private static final int RMARKER_RX_START = 600;
    private static final int STS_INDEX_AUTO_INCR = 1;
    private static final int STS_DETECT_BITMAP = 0x0F;

    // String constants for bundle keys
    private static final class BundleKeys {
        static final String CHANNEL_NUMBER = "channel_number";
        static final String NUMBER_OF_CONTROLEES = "number_of_controlees";
        static final String DEVICE_ADDRESS = "device_address";
        static final String SLOT_DURATION = "slot_duration";
        static final String STS_INDEX = "sts_index";
        static final String FCS_TYPE = "fcs_type";
        static final String DEVICE_ROLE = "device_role";
        static final String RFRAME_CONFIG = "rframe_config";
        static final String PREAMBLE_CODE_INDEX = "preamble_code_index";
        static final String SFD_ID = "sfd_id";
        static final String PSDU_DATA_RATE = "psdu_data_rate";
        static final String PREAMBLE_DURATION = "preamble_duration";
        static final String PRF_MODE = "prf_mode";
        static final String STS_SEGMENT_COUNT = "sts_segment_count";
        static final String NUMBER_OF_PACKETS = "number_of_packets";
        static final String TGAP = "t_gap";
        static final String TSTART = "t_start";
        static final String TWIN = "t_win";
        static final String RANDOMIZE_PSDU = "randomize_psdu";
        static final String PHR_RANGING_BIT = "phr_ranging_bit";
        static final String RMARKER_TX_START = "rmarker_tx_start";
        static final String RMARKER_RX_START = "rmarker_rx_start";
        static final String STS_INDEX_AUTO_INCR = "sts_index_auto_incr";
        static final String STS_DETECT_BITMAP = "sts_detect_bitmap_en";
    }

    @Before
    public void setup() {
        DEST_ADDRESS_LIST.add(DEVICE_ADDRESS);
    }

    @Test
    public void testRfTestOpenSessionParams() {
        RfTestOpenSessionParams.Builder originalBuilder = new RfTestOpenSessionParams.Builder()
                .setChannelNumber(UWB_CHANNEL)
                .setNumberOfControlee(NO_OF_CONTROLEE)
                .setDeviceAddress(DEVICE_ADDRESS)
                .setDestAddressList(DEST_ADDRESS_LIST)
                .setSlotDurationRstu(SLOT_DURATION_RSTU)
                .setStsIndex(STS_INDEX)
                .setFcsType(FCS_TYPE)
                .setDeviceRole(DEVICE_ROLE)
                .setRframeConfig(RFRAME_CONFIG)
                .setPreambleCodeIndex(PREAMBLE_CODE_INDEX)
                .setSfdId(SFD_ID)
                .setPsduDataRate(PSDU_DATA_RATE)
                .setPreambleDuration(PREAMBLE_DURATION)
                .setPrfMode(PRF_MODE)
                .setStsSegmentCount(STS_SEGMENT_COUNT)
                .setNumberOfPackets(NO_OF_PACKETS)
                .setTgap(TGAP)
                .setTstart(TSTART)
                .setTwin(TWIN)
                .setRandomizePsdu(RANDOMIZE_PSDU)
                .setPhrRangingBit(PHR_RANGING_BIT)
                .setRmarkerTxStart(RMARKER_TX_START)
                .setRmarkerRxStart(RMARKER_RX_START)
                .setStsIndexAutoIncr(STS_INDEX_AUTO_INCR)
                .setStsDetectBitmap(STS_DETECT_BITMAP);

        RfTestOpenSessionParams params = originalBuilder.build();
        PersistableBundle bundle = params.toBundle();
        RfTestOpenSessionParams newParams = RfTestOpenSessionParams.fromBundle(bundle);

        //test Builder
        verifyParams(params);
        //test bundle
        verifyParams(newParams);
    }

    @Test
    public void testInvalidBundleThrowsException() {
        PersistableBundle invalidBundle = new PersistableBundle();
        invalidBundle.putInt("invalid_key", 123);

        // Assert
        assertThrows(IllegalArgumentException.class,
                () -> RfTestOpenSessionParams.fromBundle(invalidBundle));
    }

    private void verifyParams(RfTestOpenSessionParams params) {
        assertNotNull(params);
        assertEquals(UWB_CHANNEL, params.getChannelNumber());
        assertEquals(NO_OF_CONTROLEE, params.getNoOfControlee());
        assertEquals(DEVICE_ADDRESS, params.getDeviceAddress());
        assertEquals(DEST_ADDRESS_LIST, params.getDestAddressList());
        assertEquals(SLOT_DURATION_RSTU, params.getSlotDurationRstu());
        assertEquals(STS_INDEX, params.getStsIndex());
        assertEquals(FCS_TYPE, params.getFcsType());
        assertEquals(DEVICE_ROLE, params.getDeviceRole());
        assertEquals(RFRAME_CONFIG, params.getRframeConfig());
        assertEquals(PREAMBLE_CODE_INDEX, params.getPreambleCodeIndex());
        assertEquals(SFD_ID, params.getSfdId());
        assertEquals(PSDU_DATA_RATE, params.getPsduDataRate());
        assertEquals(PREAMBLE_DURATION, params.getPreambleDuration());
        assertEquals(PRF_MODE, params.getPrfMode());
        assertEquals(STS_SEGMENT_COUNT, params.getStsSegmentCount());
        assertEquals(NO_OF_PACKETS, params.getNumberOfPackets());
        assertEquals(TGAP, params.getTgap());
        assertEquals(TSTART, params.getTstart());
        assertEquals(TWIN, params.getTwin());
        assertEquals(RANDOMIZE_PSDU, params.getRandomizePsdu());
        assertEquals(PHR_RANGING_BIT, params.getPhrRangingBit());
        assertEquals(RMARKER_TX_START, params.getRmarkerTxStart());
        assertEquals(RMARKER_RX_START, params.getRmarkerRxStart());
        assertEquals(STS_INDEX_AUTO_INCR, params.getStsIndexAutoIncr());
        assertEquals(STS_DETECT_BITMAP, params.getStsDetectBitmap());
    }

    @Test
    public void testRfTestPerRxResult() {
        int status = FiraParams.STATUS_CODE_OK;
        long attempts = 1;
        long acqDetect = 2;
        long acqReject = 3;
        long rxFail = 3;
        long syncCirReady = 4;
        long sfdFail = 5;
        long sfdFound = 6;
        long phrDecError = 7;
        long phrBitError = 8;
        long psduDecError = 9;
        long psduBitError = 10;
        long stsFound = 11;
        long eof = 12;

        RfTestPerRxResult perRxResult = new RfTestPerRxResult.Builder()
                .setOperationType(RfTestParams.TEST_PER_RX)
                .setStatus(status)
                .setAttempts(attempts)
                .setAcqDetect(acqDetect)
                .setAcqReject(acqReject)
                .setRxFail(rxFail)
                .setSyncCirReady(syncCirReady)
                .setSfdFail(sfdFail)
                .setSfdFound(sfdFound)
                .setPhrDecError(phrDecError)
                .setPhrBitError(phrBitError)
                .setPsduDecError(psduDecError)
                .setPsduBitError(psduBitError)
                .setStsFound(stsFound)
                .setEof(eof)
                .build();

        assertEquals(RfTestParams.TEST_PER_RX, perRxResult.getRfTestOperationType());
        assertEquals(status, perRxResult.getStatus());
        assertEquals(attempts, perRxResult.getAttempts());
        assertEquals(acqDetect, perRxResult.getAcqDetect());
        assertEquals(acqReject, perRxResult.getAcqReject());
        assertEquals(rxFail, perRxResult.getRxFail());
        assertEquals(syncCirReady, perRxResult.getSyncCirReady());
        assertEquals(sfdFail, perRxResult.getSfdFail());
        assertEquals(sfdFound, perRxResult.getSfdFound());
        assertEquals(phrDecError, perRxResult.getPhrDecError());
        assertEquals(phrBitError, perRxResult.getPhrBitError());
        assertEquals(psduDecError, perRxResult.getPsduDecError());
        assertEquals(psduBitError, perRxResult.getPsduBitError());
        assertEquals(stsFound, perRxResult.getStsFound());
        assertEquals(eof, perRxResult.getEof());

        RfTestPerRxResult fromBundle = RfTestPerRxResult.fromBundle(perRxResult.toBundle());
        assertEquals(status, fromBundle.getStatus());
        assertEquals(attempts, fromBundle.getAttempts());
        assertEquals(acqDetect, fromBundle.getAcqDetect());
        assertEquals(acqReject, fromBundle.getAcqReject());
        assertEquals(rxFail, fromBundle.getRxFail());
        assertEquals(syncCirReady, fromBundle.getSyncCirReady());
        assertEquals(sfdFail, fromBundle.getSfdFail());
        assertEquals(sfdFound, fromBundle.getSfdFound());
        assertEquals(phrDecError, fromBundle.getPhrDecError());
        assertEquals(phrBitError, fromBundle.getPhrBitError());
        assertEquals(psduDecError, fromBundle.getPsduDecError());
        assertEquals(psduBitError, fromBundle.getPsduBitError());
        assertEquals(stsFound, fromBundle.getStsFound());
        assertEquals(eof, fromBundle.getEof());
    }

    @Test
    public void testRfTestLoopbackResult() {
        int status = FiraParams.STATUS_CODE_OK;
        long txTsInt = 1;
        int txTsFrac = 2;
        long rxTsInt = 3;
        int rxTsFrac = 4;
        int aoaAzimuth = 5;
        int aoaElevation = 6;
        int phr = 7;
        byte[] psduData = new byte[] {1, 2, 3, 4};

        RfTestLoopbackResult loopbackResult = new RfTestLoopbackResult.Builder()
                .setOperationType(RfTestParams.TEST_LOOPBACK)
                .setStatus(status)
                .setTxTsInt(txTsInt)
                .setTxTsFrac(txTsFrac)
                .setRxTsInt(rxTsInt)
                .setRxTsFrac(rxTsFrac)
                .setAoaAzimuth(aoaAzimuth)
                .setAoaElevation(aoaElevation)
                .setPhr(phr)
                .setPsduData(psduData)
                .build();

        assertEquals(RfTestParams.TEST_LOOPBACK, loopbackResult.getRfTestOperationType());
        assertEquals(status, loopbackResult.getStatus());
        assertEquals(txTsInt, loopbackResult.getTxTsInt());
        assertEquals(txTsFrac, loopbackResult.getTxTsFrac());
        assertEquals(rxTsInt, loopbackResult.getRxTsInt());
        assertEquals(rxTsFrac, loopbackResult.getRxTsFrac());
        assertEquals(aoaAzimuth, loopbackResult.getAoaAzimuth());
        assertEquals(aoaElevation, loopbackResult.getAoaElevation());
        assertEquals(phr, loopbackResult.getPhr());
        assertArrayEquals(psduData, loopbackResult.getPsduData());

        RfTestLoopbackResult fromBundle = RfTestLoopbackResult.fromBundle(loopbackResult.toBundle());
        assertEquals(status, fromBundle.getStatus());
        assertEquals(txTsInt, fromBundle.getTxTsInt());
        assertEquals(txTsFrac, fromBundle.getTxTsFrac());
        assertEquals(rxTsInt, fromBundle.getRxTsInt());
        assertEquals(rxTsFrac, fromBundle.getRxTsFrac());
        assertEquals(aoaAzimuth, fromBundle.getAoaAzimuth());
        assertEquals(aoaElevation, fromBundle.getAoaElevation());
        assertEquals(phr, fromBundle.getPhr());
        assertArrayEquals(psduData, fromBundle.getPsduData());
    }

    @Test
    public void testRfTestRxResult() {
        int status = FiraParams.STATUS_CODE_OK;
        long rxDoneTsInt = 1;
        int rxDoneTsFrac = 2;
        int aoaAzimuth = 3;
        int aoaElevation = 4;
        int toaGap = 5;
        int phr = 6;
        byte[] psduData = new byte[] {0x01, 0x02 };
        byte[] rawNotificationData = new byte[] { 0x01, 0x03 };
        double delta = 0.0001;
        double aoaAzimuthAngle = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaAzimuth, 16), 9, 7);
        double aoaElevationAngle = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaElevation, 16), 9, 7);

        UwbTestRxResult rxResult = new UwbTestRxResult(status,
                rxDoneTsInt, rxDoneTsFrac, aoaAzimuth, aoaElevation, toaGap, phr, psduData,
                rawNotificationData);

        assertEquals(RfTestParams.TEST_RX, rxResult.getOperationType());
        assertEquals(status, rxResult.getStatus());
        assertArrayEquals(rawNotificationData, rxResult.getRawNotificationData());

        RfTestRxResult fromBundle = RfTestRxResult.fromBundle(rxResult.toBundle());

        assertEquals(RfTestParams.TEST_RX, fromBundle.getRfTestOperationType());
        assertEquals(status, fromBundle.getStatus());
        assertEquals(rxDoneTsInt, fromBundle.getRxDoneTimestampInteger());
        assertEquals(rxDoneTsFrac, fromBundle.getRxDoneTimestampFractional());
        assertEquals(aoaAzimuthAngle, fromBundle.getAoaAzimuth(), delta);
        assertEquals(aoaElevationAngle, fromBundle.getAoaElevation(), delta);
        assertEquals(toaGap, fromBundle.getToaGap());
        assertEquals(phr, fromBundle.getPhr());
        assertArrayEquals(psduData, fromBundle.getPsduData());
        assertArrayEquals(rawNotificationData, fromBundle.getRawNotificationData());
    }

    @Test
    public void testRfTestSrRxResult() {
        int status = FiraParams.STATUS_CODE_OK;
        long attempts = 1;
        long acqDetect = 2;
        long acqReject = 3;
        long rxFail = 4;
        long syncCirReady = 5;
        long sfdFail = 6;
        long sfdFound = 7;
        long stsFound = 8;
        long eof = 9;
        byte[] stsDetectBitmap = new byte[] {0x01, 0x02 };
        byte[] rawNotificationData = new byte[] { 0x01, 0x03 };

        UwbTestSrRxResult result = new UwbTestSrRxResult(status, attempts, acqDetect, acqReject,
                rxFail, syncCirReady, sfdFail, sfdFound, stsFound, eof, stsDetectBitmap,
                rawNotificationData);

        assertEquals(RfTestParams.TEST_SR_RX, result.getOperationType());
        assertEquals(status, result.getStatus());
        assertArrayEquals(rawNotificationData, result.getRawNotificationData());

        RfTestSrRxResult fromBundle = RfTestSrRxResult.fromBundle(result.toBundle());

        assertEquals(RfTestParams.TEST_SR_RX, fromBundle.getRfTestOperationType());
        assertEquals(status, fromBundle.getStatus());
        assertEquals(attempts, fromBundle.getAttempts());
        assertEquals(acqDetect, fromBundle.getAcqDetect());
        assertEquals(acqReject, fromBundle.getAcqReject());
        assertEquals(rxFail, fromBundle.getRxFail());
        assertEquals(syncCirReady, fromBundle.getSyncCirReady());
        assertEquals(sfdFail, fromBundle.getSfdFail());
        assertEquals(sfdFound, fromBundle.getSfdFound());
        assertEquals(stsFound, fromBundle.getStsFound());
        assertEquals(eof, fromBundle.getEof());
        assertArrayEquals(stsDetectBitmap, fromBundle.getStsDetectBitmap());
        assertArrayEquals(rawNotificationData, fromBundle.getRawNotificationData());
    }
}
