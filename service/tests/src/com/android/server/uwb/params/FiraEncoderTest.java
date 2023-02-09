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


import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_UT_TAG;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_UL_TDOA;
import static com.google.uwb.support.fira.FiraParams.SESSION_TYPE_RANGING;
import static com.google.uwb.support.fira.FiraParams.TX_TIMESTAMP_40_BIT;
import static com.google.uwb.support.fira.FiraParams.UL_TDOA_DEVICE_ID_16_BIT;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;


/**
 * Unit tests for {@link com.android.server.uwb.params.FiraEncoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class FiraEncoderTest {
    private static final FiraOpenSessionParams.Builder TEST_FIRA_OPEN_SESSION_PARAMS =
            new FiraOpenSessionParams.Builder()
                    .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                    .setSessionId(1)
                    .setSessionType(SESSION_TYPE_RANGING)
                    .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG)
                    .setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER)
                    .setDeviceRole(RANGING_DEVICE_ROLE_RESPONDER)
                    .setDeviceAddress(UwbAddress.fromBytes(new byte[] { 0x4, 0x6}))
                    .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[] { 0x4, 0x6})))
                    .setMultiNodeMode(MULTI_NODE_MODE_UNICAST)
                    .setRangingRoundUsage(RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE)
                    .setVendorId(new byte[]{0x5, 0x78})
                    .setStaticStsIV(new byte[]{0x1a, 0x55, 0x77, 0x47, 0x7e, 0x7d})
                    .setRangeDataNtfAoaAzimuthLower(-1.5)
                    .setRangeDataNtfAoaAzimuthUpper(2.5)
                    .setRangeDataNtfAoaElevationLower(-1.5)
                    .setRangeDataNtfAoaElevationUpper(1.2);

    private static final byte[] TEST_FIRA_OPEN_SESSION_TLV_DATA =
            UwbUtil.getByteArray("000101010101020100030100040109050101060206040702060408"
                    + "0260090904C80000000B01000C01030D01010E01040F0200001002204E11010012010314010"
                    + "A1501021601001701011B01191C01001F01002301002401002501322601002702780528061A"
                    + "5577477E7D2901012A0200002C01002D01002E01012F01013101"
                    + "003501012B04000000001D0807D59E4707D56022");

    private static final FiraRangingReconfigureParams.Builder TEST_FIRA_RECONFIGURE_PARAMS =
            new FiraRangingReconfigureParams.Builder()
                    .setBlockStrideLength(6)
                    .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_AOA_LEVEL_TRIG)
                    .setRangeDataProximityFar(6)
                    .setRangeDataProximityNear(4)
                    .setRangeDataAoaAzimuthLower(-1.5)
                    .setRangeDataAoaAzimuthUpper(2.5)
                    .setRangeDataAoaElevationLower(-1.5)
                    .setRangeDataAoaElevationUpper(1.2);

    private static final byte[] TEST_FIRA_RECONFIGURE_TLV_DATA =
            UwbUtil.getByteArray("2D01060E01040F020400100206001D0807D59E4707D56022");

    private static final FiraOpenSessionParams.Builder TEST_FIRA_UT_TAG_OPEN_SESSION_PARAM =
            new FiraOpenSessionParams.Builder()
                    .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                    .setSessionId(2)
                    .setSessionType(SESSION_TYPE_RANGING)
                    .setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER)
                    .setDeviceRole(RANGING_DEVICE_UT_TAG)
                    .setDeviceAddress(UwbAddress.fromBytes(new byte[] { 0x4, 0x6}))
                    .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[] { 0x4, 0x6})))
                    .setMultiNodeMode(MULTI_NODE_MODE_UNICAST)
                    .setVendorId(new byte[]{0x5, 0x78})
                    .setStaticStsIV(new byte[]{0x1a, 0x55, 0x77, 0x47, 0x7e, 0x7d})
                    .setRangingRoundUsage(RANGING_ROUND_USAGE_UL_TDOA)
                    .setUlTdoaTxIntervalMs(1200)
                    .setUlTdoaRandomWindowMs(30)
                    .setUlTdoaDeviceIdType(UL_TDOA_DEVICE_ID_16_BIT)
                    .setUlTdoaDeviceId(new byte[]{0x0B, 0x0A})
                    .setUlTdoaTxTimestampType(TX_TIMESTAMP_40_BIT);

    private static final byte[] TEST_FIRA_OPEN_SESSION_TLV_DATA_UT_TAG =
            UwbUtil.getByteArray("000101010100020100030100040109050101060206040702060408"
                    + "0260090904C80000000B01000C01030D01010E01010F0200001002204E11010412010314010"
                    + "A1501021601001701011B01191C01001F01002301002401002501322601002702780528061A"
                    + "5577477E7D2901012A0200002C01002D01002E01012F01013101003501012B0400000000330"
                    + "4B004000034041E0000003803010B0A390101");

    private final FiraEncoder mFiraEncoder = new FiraEncoder();

    @Test
    public void testFiraOpenSessionParams() throws Exception {
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        TlvBuffer tlvs = mFiraEncoder.getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(41);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_FIRA_OPEN_SESSION_TLV_DATA);
    }

    @Test
    public void testFiraRangingReconfigureParams() throws Exception {
        FiraRangingReconfigureParams params = TEST_FIRA_RECONFIGURE_PARAMS.build();
        TlvBuffer tlvs = mFiraEncoder.getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(5);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_FIRA_RECONFIGURE_TLV_DATA);
    }

    @Test
    public void testFiraOpenSesisonParamsViaTlvEncoder() throws Exception {
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        TlvBuffer tlvs = TlvEncoder.getEncoder(FiraParams.PROTOCOL_NAME).getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(41);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_FIRA_OPEN_SESSION_TLV_DATA);
    }

    @Test
    public void testFiraRangingReconfigureParamsViaTlvEncoder() throws Exception {
        FiraRangingReconfigureParams params = TEST_FIRA_RECONFIGURE_PARAMS.build();
        TlvBuffer tlvs = TlvEncoder.getEncoder(FiraParams.PROTOCOL_NAME).getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(5);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_FIRA_RECONFIGURE_TLV_DATA);
    }

    @Test
    public void testFiraOpenSessionParamsUtTag() throws Exception {
        FiraOpenSessionParams params = TEST_FIRA_UT_TAG_OPEN_SESSION_PARAM.build();
        TlvBuffer tlvs = mFiraEncoder.getTlvBuffer(params);

        assertThat(tlvs.getNoOfParams()).isEqualTo(44);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_FIRA_OPEN_SESSION_TLV_DATA_UT_TAG);
    }
}
