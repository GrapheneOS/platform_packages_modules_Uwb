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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.rftest.RfTestOpenSessionParams;
import com.google.uwb.support.rftest.RfTestParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;

/** Unit tests for {@link com.android.server.uwb.params.RfTestEncoder}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class RfTestEncoderTest {
    private static final FiraProtocolVersion PROTOCOL_VERSION_DUMMY = new FiraProtocolVersion(0, 0);

    private static final RfTestOpenSessionParams.Builder TEST_RFTEST_OPEN_SESSION_PARAMS =
            new RfTestOpenSessionParams.Builder()
                    .setChannelNumber(FiraParams.UWB_CHANNEL_5)
                    .setNumberOfControlee(1)
                    .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x4, 0x6}))
                    .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[]{0x4, 0x6})))
                    .setSlotDurationRstu(2400)
                    .setStsIndex(0)
                    .setFcsType(0)
                    .setDeviceRole(1)
                    .setRframeConfig(FiraParams.RFRAME_CONFIG_SP3)
                    .setPreambleCodeIndex(90)
                    .setSfdId(2)
                    .setPsduDataRate(0)
                    .setPreambleDuration(0)
                    .setPrfMode(FiraParams.PRF_MODE_HPRF)
                    .setStsSegmentCount(1)
                    .setNumberOfPackets(1000)
                    .setTgap(2000)
                    .setTstart(450)
                    .setTwin(750)
                    .setRandomizePsdu(0)
                    .setPhrRangingBit(0)
                    .setRmarkerTxStart(0)
                    .setRmarkerRxStart(0)
                    .setStsIndexAutoIncr(0)
                    .setStsDetectBitmap(0);

    private static final int TEST_RFTEST_OPEN_SESSION_PARAMS_TLV_NUMBER = 14;
    private static final int TEST_RFTEST_PARAMS_TLV_NUMBER = 10;

    private static final String CHANNEL_NUMBER_TLV = "040105";
    private static final String NUMBER_OF_CONTROLEES_TLV = "050101";
    private static final String DEVICE_MAC_ADDRESS_TLV = "06020406";
    private static final String SLOT_DURATION_TLV = "08026009";
    private static final String STS_INDEX_TLV = "0A0400000000";
    private static final String MAC_FCS_TYPE_TLV = "0B0100";
    private static final String DEVICE_ROLE_INITIATOR_TLV = "110101";
    private static final String RFRAME_CONFIG_TLV = "120103";
    private static final String PREAMBLE_CODE_INDEX_TLV = "14015A";
    private static final String SFD_ID_TLV = "150102";
    private static final String PSDU_DATA_RATE_TLV = "160100";
    private static final String PREAMBLE_DURATION_TLV = "170100";
    private static final String PRF_MODE_TLV = "1F0101";
    private static final String NUMBER_OF_STS_SEGMENTS_TLV = "290101";

    private static final byte[] TEST_RFTEST_OPEN_SESSION_PARAMS_TLV_DATA =
            UwbUtil.getByteArray(CHANNEL_NUMBER_TLV
                    + NUMBER_OF_CONTROLEES_TLV
                    + DEVICE_MAC_ADDRESS_TLV
                    + SLOT_DURATION_TLV
                    + STS_INDEX_TLV
                    + MAC_FCS_TYPE_TLV
                    + DEVICE_ROLE_INITIATOR_TLV
                    + RFRAME_CONFIG_TLV
                    + PREAMBLE_CODE_INDEX_TLV
                    + SFD_ID_TLV
                    + PSDU_DATA_RATE_TLV
                    + PREAMBLE_DURATION_TLV
                    + PRF_MODE_TLV
                    + NUMBER_OF_STS_SEGMENTS_TLV);

    private static final String NUM_PACKETS = "000764000000280010";
    private static final String T_GAP = "010180";
    private static final String T_START = "020105";
    private static final String T_WIN = "0302FFFF";
    private static final String RANDOMIZE_PSDU = "040103";
    private static final String PHR_RANGING_BIT = "050109";
    private static final String RMARKER_TX_START = "06015a";
    private static final String RMARKER_RX_START = "070163";
    private static final String STS_INDEX_AUTO_INCR = "080100";
    private static final String STS_DETECT_BITMAP_EN = "090101";

    private static final byte[] TEST_RFTEST_PARAMS_TLV_DATA =
            UwbUtil.getByteArray(NUM_PACKETS
                    + T_GAP
                    + T_START
                    + T_WIN
                    + RANDOMIZE_PSDU
                    + PHR_RANGING_BIT
                    + RMARKER_TX_START
                    + RMARKER_RX_START
                    + STS_INDEX_AUTO_INCR
                    + STS_DETECT_BITMAP_EN);

    @Mock
    private UwbInjector mUwbInjector;
    private final RfTestEncoder mRfTestEncoder = new RfTestEncoder();

    private static void verifyRfTestOpenSessionParamsTlvBuffer(TlvBuffer tlvs) {
        assertThat(tlvs.getNoOfParams()).isEqualTo(TEST_RFTEST_OPEN_SESSION_PARAMS_TLV_NUMBER);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_RFTEST_OPEN_SESSION_PARAMS_TLV_DATA);
    }

    private static void verifyRfTestParamsTlvBuffer(TlvBuffer tlvs) {
        assertThat(tlvs.getNoOfParams()).isEqualTo(TEST_RFTEST_PARAMS_TLV_NUMBER);
        assertThat(tlvs.getByteArray()).isEqualTo(TEST_RFTEST_PARAMS_TLV_DATA);
    }

    @Test
    public void testRfTestOpenSessionParamsViaTlvEncoder() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        RfTestOpenSessionParams params = TEST_RFTEST_OPEN_SESSION_PARAMS.build();

        verifyRfTestOpenSessionParamsTlvBuffer(
                TlvEncoder.getEncoder(RfTestParams.PROTOCOL_NAME, mUwbInjector).getTlvBuffer(params,
                        PROTOCOL_VERSION_DUMMY));
    }

    @Test
    public void testRfTestOpenSessionParams() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        RfTestOpenSessionParams params = TEST_RFTEST_OPEN_SESSION_PARAMS.build();

        verifyRfTestOpenSessionParamsTlvBuffer(mRfTestEncoder.getTlvBuffer(params,
                PROTOCOL_VERSION_DUMMY));
    }

    @Test
    public void testRfTestParamsViaTlvEncoder() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        RfTestOpenSessionParams params = TEST_RFTEST_OPEN_SESSION_PARAMS.build();

        verifyRfTestOpenSessionParamsTlvBuffer(
                TlvEncoder.getEncoder(RfTestParams.PROTOCOL_NAME, mUwbInjector).getTlvBuffer(params,
                        PROTOCOL_VERSION_DUMMY));
    }

    @Test
    public void testRfTestParams() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        RfTestOpenSessionParams params = TEST_RFTEST_OPEN_SESSION_PARAMS.build();

        verifyRfTestOpenSessionParamsTlvBuffer(mRfTestEncoder.getTlvBuffer(params,
                PROTOCOL_VERSION_DUMMY));
    }
}
