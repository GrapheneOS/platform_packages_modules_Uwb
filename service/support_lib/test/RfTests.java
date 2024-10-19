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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.rftest.RfTestOpenSessionParams;

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
}
