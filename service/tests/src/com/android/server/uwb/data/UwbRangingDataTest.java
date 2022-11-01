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

package com.android.server.uwb.data;

import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.util.UwbUtil.convertFloatToQFormat;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.uwb.data.UwbRangingData}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbRangingDataTest {
    private static final long TEST_SEQ_COUNTER = 5;
    private static final long TEST_SESSION_ID = 7;
    private static final int TEST_RCR_INDICATION = 7;
    private static final long TEST_CURR_RANGING_INTERVAL = 100;
    private static final int TEST_MAC_ADDRESS_MODE = 1;
    private static final byte[] TEST_MAC_ADDRESS = {0x1, 0x3};
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 0;
    private static final int TEST_BLOCK_INDEX = 5;
    private static final int TEST_FRAME_SEQ_NUMBER = 1;
    private static final int TEST_DISTANCE = 101;
    private static final int TEST_AOA_AZIMUTH = 67;
    private static final float TEST_AOA_AZIMUTH_FLOAT =
            UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(TEST_AOA_AZIMUTH, 16), 9, 7);
    private static final int TEST_AOA_AZIMUTH_Q97_FORMAT =
            UwbUtil.convertFloatToQFormat(TEST_AOA_AZIMUTH_FLOAT, 9, 7);
    private static final int TEST_AOA_AZIMUTH_FOM = 50;
    private static final int TEST_AOA_ELEVATION = 37;
    private static final float TEST_AOA_ELEVATION_FLOAT =
            UwbUtil.convertQFormatToFloat(UwbUtil.twos_compliment(TEST_AOA_ELEVATION, 16), 9, 7);
    private static final int TEST_AOA_ELEVATION_Q97_FORMAT =
            UwbUtil.convertFloatToQFormat(TEST_AOA_ELEVATION_FLOAT, 9, 7);
    private static final int TEST_AOA_ELEVATION_FOM = 90;
    private static final float TEST_AOA_DEST_AZIMUTH = 67;
    private static final int TEST_AOA_DEST_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_DEST_ELEVATION = 37;
    private static final int TEST_AOA_DEST_ELEVATION_FOM = 90;
    private static final int TEST_SLOT_IDX = 10;
    private static final int TEST_RSSI = -1;

    private UwbRangingData mUwbRangingData;

    @Test
    public void testInitializeUwbRangingData_withUwbTwoWayMeasurement() throws Exception {
        final int noOfRangingMeasures = 1;
        final byte[] rawNtfData = {0x10, 0x01};
        final UwbTwoWayMeasurement[] uwbTwoWayMeasurements =
                new UwbTwoWayMeasurement[noOfRangingMeasures];
        final int rangingMeasuresType = RANGING_MEASUREMENT_TYPE_TWO_WAY;
        uwbTwoWayMeasurements[0] = new UwbTwoWayMeasurement(TEST_MAC_ADDRESS, TEST_STATUS, TEST_LOS,
                TEST_DISTANCE, convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7),
                TEST_AOA_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7),
                TEST_AOA_ELEVATION_FOM, convertFloatToQFormat(TEST_AOA_DEST_AZIMUTH, 9, 7),
                TEST_AOA_DEST_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_DEST_ELEVATION, 9, 7),
                TEST_AOA_DEST_ELEVATION_FOM, TEST_SLOT_IDX, TEST_RSSI);
        mUwbRangingData = new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, rangingMeasuresType,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbTwoWayMeasurements, rawNtfData);

        assertThat(mUwbRangingData.getSequenceCounter()).isEqualTo(TEST_SEQ_COUNTER);
        assertThat(mUwbRangingData.getSessionId()).isEqualTo(TEST_SESSION_ID);
        assertThat(mUwbRangingData.getRcrIndication()).isEqualTo(TEST_RCR_INDICATION);
        assertThat(mUwbRangingData.getCurrRangingInterval()).isEqualTo(TEST_CURR_RANGING_INTERVAL);
        assertThat(mUwbRangingData.getRangingMeasuresType()).isEqualTo(rangingMeasuresType);
        assertThat(mUwbRangingData.getMacAddressMode()).isEqualTo(TEST_MAC_ADDRESS_MODE);
        assertThat(mUwbRangingData.getNoOfRangingMeasures()).isEqualTo(1);
        assertThat(mUwbRangingData.getRawNtfData()).isEqualTo(rawNtfData);

        final String testString = "UwbRangingData { "
                + " SeqCounter = " + TEST_SEQ_COUNTER
                + ", SessionId = " + TEST_SESSION_ID
                + ", RcrIndication = " + TEST_RCR_INDICATION
                + ", CurrRangingInterval = " + TEST_CURR_RANGING_INTERVAL
                + ", RangingMeasuresType = " + rangingMeasuresType
                + ", MacAddressMode = " + TEST_MAC_ADDRESS_MODE
                + ", NoOfRangingMeasures = " + noOfRangingMeasures
                + ", RangingTwoWayMeasures = " + Arrays.toString(uwbTwoWayMeasurements)
                + ", RawNotificationData = " + Arrays.toString(rawNtfData)
                + '}';

        assertThat(mUwbRangingData.toString()).isEqualTo(testString);
    }

    @Test
    public void testInitializeUwbRangingData_withUwbOwrAoaMeasurement() throws Exception {
        final int noOfRangingMeasures = 1;
        final UwbOwrAoaMeasurement uwbOwrAoaMeasurement = new UwbOwrAoaMeasurement(TEST_MAC_ADDRESS,
                TEST_STATUS, TEST_LOS, TEST_FRAME_SEQ_NUMBER, TEST_BLOCK_INDEX,
                TEST_AOA_AZIMUTH_Q97_FORMAT, TEST_AOA_AZIMUTH_FOM,
                TEST_AOA_ELEVATION_Q97_FORMAT, TEST_AOA_ELEVATION_FOM);
        final int rangingMeasuresType = RANGING_MEASUREMENT_TYPE_OWR_AOA;
        mUwbRangingData = new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, rangingMeasuresType,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbOwrAoaMeasurement);

        assertThat(mUwbRangingData.getSequenceCounter()).isEqualTo(TEST_SEQ_COUNTER);
        assertThat(mUwbRangingData.getSessionId()).isEqualTo(TEST_SESSION_ID);
        assertThat(mUwbRangingData.getRcrIndication()).isEqualTo(TEST_RCR_INDICATION);
        assertThat(mUwbRangingData.getCurrRangingInterval()).isEqualTo(TEST_CURR_RANGING_INTERVAL);
        assertThat(mUwbRangingData.getRangingMeasuresType()).isEqualTo(rangingMeasuresType);
        assertThat(mUwbRangingData.getMacAddressMode()).isEqualTo(TEST_MAC_ADDRESS_MODE);
        assertThat(mUwbRangingData.getNoOfRangingMeasures()).isEqualTo(1);

        final String testString = "UwbRangingData { "
                + " SeqCounter = " + TEST_SEQ_COUNTER
                + ", SessionId = " + TEST_SESSION_ID
                + ", RcrIndication = " + TEST_RCR_INDICATION
                + ", CurrRangingInterval = " + TEST_CURR_RANGING_INTERVAL
                + ", RangingMeasuresType = " + rangingMeasuresType
                + ", MacAddressMode = " + TEST_MAC_ADDRESS_MODE
                + ", NoOfRangingMeasures = " + noOfRangingMeasures
                + ", RangingOwrAoaMeasure = " + uwbOwrAoaMeasurement.toString()
                + '}';

        assertThat(mUwbRangingData.toString()).isEqualTo(testString);
    }
}
