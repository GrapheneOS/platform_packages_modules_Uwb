/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link com.android.server.uwb.data.UwbDlTDoAMeasurement}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbDlTDoAMeasurementTest {
    private static final byte[] TEST_MAC_ADDRESS = {0x11, 0x13};
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_MESSAGE_TYPE = 0x02;
    private static final int TEST_MESSAGE_CONTROL = 0b0000001001001011;
    private static final int TEST_BLOCK_INDEX = 3;
    private static final int TEST_ROUND_INDEX = 7;
    private static final int TEST_NLOS = 0x01;
    private static final int TEST_AOA_AZIMUTH_QFORMAT = UwbUtil.twos_compliment(30 << 7, 16);
    private static final float TEST_AOA_AZIMUTH_FLOAT =
            UwbUtil.convertQFormatToFloat(TEST_AOA_AZIMUTH_QFORMAT, 9, 7);
    private static final int TEST_AOA_AZIMUTH_FOM = 33;
    private static final int TEST_AOA_ELEVATION_QFORMAT = UwbUtil.twos_compliment(60 << 7, 16);
    private static final float TEST_AOA_ELEVATION_FLOAT =
            UwbUtil.convertQFormatToFloat(TEST_AOA_ELEVATION_QFORMAT, 9, 7);
    private static final int TEST_AOA_ELEVATION_FOM = 66;
    private static final int TEST_RSSI_QFORMAT = 100;
    private static final int TEST_RSSI = -50;
    private static final long TEST_TX_TIMESTAMP = 1234567890L;
    private static final long TEST_RX_TIMESTAMP = 9876543210L;
    private static final int TEST_SUPERCLUSTER_ID = 5;
    private static final int TEST_ANCHOR_CFO_QFORMAT = UwbUtil.twos_compliment(10 << 10, 16);
    private static final float TEST_ANCHOR_CFO_FLOAT =
            UwbUtil.convertQFormatToFloat(TEST_ANCHOR_CFO_QFORMAT, 6, 10);
    private static final int TEST_CFO_QFORMAT = UwbUtil.twos_compliment(20 << 10, 16);
    private static final float TEST_CFO_FLOAT =
            UwbUtil.convertQFormatToFloat(TEST_CFO_QFORMAT, 6, 10);
    private static final long TEST_INITIATOR_REPLY_TIME = 12345L;
    private static final long TEST_RESPONDER_REPLY_TIME = 54321L;
    private static final int TEST_INITIATOR_RESPONDER_TOF = 100;
    private static final byte[] TEST_ANCHOR_LOCATION =
            {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99};
    private static final byte[] TEST_ACTIVE_RANGING_ROUNDS = {1, 3, 5, 7};
    private static final byte[] TEST_TX_TIMESTAMP_V2 = {0x01, 0x02, 0x03, 0x04};
    private static final byte[] TEST_RX_TIMESTAMP_V2 = {0x05, 0x06, 0x07, 0x08};

    private UwbDlTDoAMeasurement mUwbDlTDoAMeasurement;

    @Test
    public void testInitializeUwbDlTDoAMeasurement() throws Exception {
        mUwbDlTDoAMeasurement = new UwbDlTDoAMeasurement(
                TEST_MAC_ADDRESS, TEST_STATUS, TEST_MESSAGE_TYPE, TEST_MESSAGE_CONTROL,
                TEST_BLOCK_INDEX, TEST_ROUND_INDEX, TEST_NLOS, TEST_AOA_AZIMUTH_QFORMAT,
                TEST_AOA_AZIMUTH_FOM, TEST_AOA_ELEVATION_QFORMAT, TEST_AOA_ELEVATION_FOM,
                TEST_RSSI_QFORMAT, TEST_TX_TIMESTAMP, TEST_RX_TIMESTAMP, TEST_ANCHOR_CFO_QFORMAT,
                TEST_CFO_QFORMAT, TEST_INITIATOR_REPLY_TIME, TEST_RESPONDER_REPLY_TIME,
                TEST_INITIATOR_RESPONDER_TOF, TEST_ANCHOR_LOCATION, TEST_ACTIVE_RANGING_ROUNDS,
                TEST_SUPERCLUSTER_ID);

        assertThat(mUwbDlTDoAMeasurement.getMacAddress()).isEqualTo(TEST_MAC_ADDRESS);
        assertThat(mUwbDlTDoAMeasurement.getStatus()).isEqualTo(TEST_STATUS);
        assertThat(mUwbDlTDoAMeasurement.getMessageType()).isEqualTo(TEST_MESSAGE_TYPE);
        assertThat(mUwbDlTDoAMeasurement.getMessageControl()).isEqualTo(TEST_MESSAGE_CONTROL);
        assertThat(mUwbDlTDoAMeasurement.getBlockIndex()).isEqualTo(TEST_BLOCK_INDEX);
        assertThat(mUwbDlTDoAMeasurement.getRoundIndex()).isEqualTo(TEST_ROUND_INDEX);
        assertThat(mUwbDlTDoAMeasurement.getNLoS()).isEqualTo(TEST_NLOS);
        assertThat(mUwbDlTDoAMeasurement.getAoaAzimuth()).isEqualTo(TEST_AOA_AZIMUTH_FLOAT);
        assertThat(mUwbDlTDoAMeasurement.getAoaAzimuthFom()).isEqualTo(TEST_AOA_AZIMUTH_FOM);
        assertThat(mUwbDlTDoAMeasurement.getAoaElevation()).isEqualTo(TEST_AOA_ELEVATION_FLOAT);
        assertThat(mUwbDlTDoAMeasurement.getAoaElevationFom()).isEqualTo(TEST_AOA_ELEVATION_FOM);
        assertThat(mUwbDlTDoAMeasurement.getRssi()).isEqualTo(TEST_RSSI);
        assertThat(mUwbDlTDoAMeasurement.getTxTimestamp()).isEqualTo(TEST_TX_TIMESTAMP);
        assertThat(mUwbDlTDoAMeasurement.getRxTimestamp()).isEqualTo(TEST_RX_TIMESTAMP);
        assertThat(mUwbDlTDoAMeasurement.getAnchorCfo()).isEqualTo(TEST_ANCHOR_CFO_FLOAT);
        assertThat(mUwbDlTDoAMeasurement.getCfo()).isEqualTo(TEST_CFO_FLOAT);
        assertThat(mUwbDlTDoAMeasurement.getInitiatorReplyTime()).isEqualTo(
                TEST_INITIATOR_REPLY_TIME);
        assertThat(mUwbDlTDoAMeasurement.getResponderReplyTime())
                .isEqualTo(TEST_RESPONDER_REPLY_TIME);
        assertThat(mUwbDlTDoAMeasurement.getInitiatorResponderTof())
                .isEqualTo(TEST_INITIATOR_RESPONDER_TOF);
        assertThat(mUwbDlTDoAMeasurement.getAnchorLocation()).isEqualTo(TEST_ANCHOR_LOCATION);
        assertThat(mUwbDlTDoAMeasurement.getActiveRangingRounds())
                .isEqualTo(TEST_ACTIVE_RANGING_ROUNDS);
        assertThat(mUwbDlTDoAMeasurement.getSuperclusterId()).isEqualTo(TEST_SUPERCLUSTER_ID);
    }

    @Test
    public void testInitializeUwbDlTDoAMeasurementV2() throws Exception {
        mUwbDlTDoAMeasurement = new UwbDlTDoAMeasurement(
                TEST_MAC_ADDRESS, TEST_STATUS, TEST_MESSAGE_TYPE, TEST_MESSAGE_CONTROL,
                TEST_BLOCK_INDEX, TEST_ROUND_INDEX, TEST_NLOS, TEST_AOA_AZIMUTH_QFORMAT,
                TEST_AOA_AZIMUTH_FOM, TEST_AOA_ELEVATION_QFORMAT, TEST_AOA_ELEVATION_FOM,
                TEST_RSSI_QFORMAT, TEST_TX_TIMESTAMP, TEST_RX_TIMESTAMP,
                TEST_TX_TIMESTAMP_V2, TEST_RX_TIMESTAMP_V2,
                TEST_ANCHOR_CFO_QFORMAT, TEST_CFO_QFORMAT, TEST_INITIATOR_REPLY_TIME,
                TEST_RESPONDER_REPLY_TIME, TEST_INITIATOR_RESPONDER_TOF, TEST_ANCHOR_LOCATION,
                TEST_ACTIVE_RANGING_ROUNDS, TEST_SUPERCLUSTER_ID);

        assertThat(mUwbDlTDoAMeasurement.getMacAddress()).isEqualTo(TEST_MAC_ADDRESS);
        assertThat(mUwbDlTDoAMeasurement.getTxTimestampV2()).isEqualTo(TEST_TX_TIMESTAMP_V2);
        assertThat(mUwbDlTDoAMeasurement.getRxTimestampV2()).isEqualTo(TEST_RX_TIMESTAMP_V2);
        assertThat(mUwbDlTDoAMeasurement.getSuperclusterId()).isEqualTo(TEST_SUPERCLUSTER_ID);
    }

}
