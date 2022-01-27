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
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MacFcsCrcCapabilityFlag.HAS_CRC_16_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_ONE_TO_MANY_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PreambleCapabilityFlag.HAS_32_SYMBOLS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PreambleCapabilityFlag.HAS_64_SYMBOLS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.PrfCapabilityFlag.HAS_BPRF_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RangingRoundCapabilityFlag.HAS_SS_TWR_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP0_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP1_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.SfdCapabilityFlag.HAS_SFD0_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.SfdCapabilityFlag.HAS_SFD1_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.SfdCapabilityFlag.HAS_SFD2_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsCapabilityFlag.HAS_STATIC_STS_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsSegmentsCapabilityFlag.HAS_0_SEGMENT_SUPPORT;
import static com.google.uwb.support.fira.FiraParams.StsSegmentsCapabilityFlag.HAS_1_SEGMENT_SUPPORT;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.params.FiraDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class FiraDecoderTest {
    private static final byte[] TEST_FIRA_SPECIFICATION_TLV_DATA =
            UwbUtil.getByteArray("00020509"
                    + "010400000003"
                    + "020400000006"
                    + "030101"
                    + "040101"
                    + "050100"
                    + "060401010202"
                    + "070400000001"
                    + "080400000003"
                    + "090400000003"
                    + "0a0400000001"
                    + "0b0400000003"
                    + "0c040000000b"
                    + "0d0400000007"
                    + "0e0400000003"
                    + "0f0400000003"
                    + "100400000003"
                    + "110400000001");
    private static final int TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS = 18;
    private final FiraDecoder mFiraDecoder = new FiraDecoder();

    @Test
    public void testGetFiraSpecification() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(
                        TEST_FIRA_SPECIFICATION_TLV_DATA, TEST_FIRA_SPECIFICATION_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        FiraSpecificationParams firaSpecificationParams = mFiraDecoder.getParams(
                tlvDecoderBuffer, FiraSpecificationParams.class);
        assertThat(firaSpecificationParams).isNotNull();

        assertThat(firaSpecificationParams.getSupportedChannels()).isEqualTo(List.of(5, 9));
        assertThat(firaSpecificationParams.getAoaCapabilities()).isEqualTo(
                EnumSet.of(HAS_AZIMUTH_SUPPORT, HAS_ELEVATION_SUPPORT));
        assertThat(firaSpecificationParams.getDeviceRoleCapabilities()).isEqualTo(
                EnumSet.of(HAS_CONTROLEE_RESPONDER_SUPPORT,
                        HAS_CONTROLLER_INITIATOR_SUPPORT));
        assertThat(firaSpecificationParams.hasBlockStridingSupport()).isEqualTo(true);
        assertThat(firaSpecificationParams.hasNonDeferredModeSupport()).isEqualTo(true);
        assertThat(firaSpecificationParams.hasTxAdaptivePayloadPowerSupport()).isEqualTo(false);
        assertThat(firaSpecificationParams.getInitiationTimeMs()).isEqualTo(0x01010202);
        assertThat(firaSpecificationParams.getMacFcsCrcCapabilities()).isEqualTo(
                EnumSet.of(HAS_CRC_16_SUPPORT));
        assertThat(firaSpecificationParams.getMultiNodeCapabilities()).isEqualTo(
                EnumSet.of(HAS_ONE_TO_MANY_SUPPORT, HAS_UNICAST_SUPPORT));
        assertThat(firaSpecificationParams.getPreambleCapabilities()).isEqualTo(
                EnumSet.of(HAS_32_SYMBOLS_SUPPORT, HAS_64_SYMBOLS_SUPPORT));
        assertThat(firaSpecificationParams.getPrfCapabilities()).isEqualTo(
                EnumSet.of(HAS_BPRF_SUPPORT));
        assertThat(firaSpecificationParams.getRangingRoundCapabilities()).isEqualTo(
                EnumSet.of(HAS_DS_TWR_SUPPORT, HAS_SS_TWR_SUPPORT));
        assertThat(firaSpecificationParams.getRframeCapabilities()).isEqualTo(
                EnumSet.of(HAS_SP0_RFRAME_SUPPORT, HAS_SP1_RFRAME_SUPPORT,
                        HAS_SP3_RFRAME_SUPPORT));
        assertThat(firaSpecificationParams.getSfdCapabilities()).isEqualTo(
                EnumSet.of(HAS_SFD0_SUPPORT, HAS_SFD1_SUPPORT, HAS_SFD2_SUPPORT));
        assertThat(firaSpecificationParams.getStsCapabilities()).isEqualTo(
                EnumSet.of(HAS_STATIC_STS_SUPPORT, HAS_DYNAMIC_STS_SUPPORT));
        assertThat(firaSpecificationParams.getStsSegmentsCapabilities()).isEqualTo(
                EnumSet.of(HAS_0_SEGMENT_SUPPORT, HAS_1_SEGMENT_SUPPORT));
        assertThat(firaSpecificationParams.getBprfPhrDataRateCapabilities()).isEqualTo(
                EnumSet.of(FiraParams.BprfPhrDataRateCapabilityFlag.HAS_6M81_SUPPORT,
                        FiraParams.BprfPhrDataRateCapabilityFlag.HAS_850K_SUPPORT));
        assertThat(firaSpecificationParams.getPsduDataRateCapabilities()).isEqualTo(
                EnumSet.of(FiraParams.PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT));
    }
}
