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

package com.android.server.ranging.tests.oob;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.ranging.wifi.rtt.RttRangingCapabilities;

import com.android.server.ranging.rtt.RttOobCapabilities;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

@RunWith(JUnit4.class)
public class RttOobCapabilitiesTest {

    private static final RttOobCapabilities RTT_OOB_CAPABILITIES =
            RttOobCapabilities.builder()
                    .setHasPeriodicRangingSupport(false)
                    .setMaxSupportedBandwidth(20)
                    .setMaxSupportedRxChain(3)
                    .build();


    private final byte[] mRttTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x02,
                    // Size
                    0x06,
            };

    private final byte[] mRttCapabilityBytes =
            new byte[]{
                    // 11mc or 11az
                    0x1,
                    // Has periodic ranging support
                    0x00,
                    // Max bandwidth,
                    0x14,
                    // Max supported Rx chain,
                    0x03
            };

    private final byte[] mRttCapabilityWithHeaderBytes =
            Bytes.concat(mRttTechHeaderBytes, mRttCapabilityBytes);

    @Test
    public void testGetRttOobCapabilities() {
        RttRangingCapabilities rangingCapabilities = new RttRangingCapabilities.Builder()
                .setMaxSupportedBandwidth(20)
                .setMaxSupportedRxChain(3)
                .setPeriodicRangingHardwareFeature(false)
                .build();

        assertThat(RttOobCapabilities.fromRangingCapabilities(rangingCapabilities)).isEqualTo(
                RTT_OOB_CAPABILITIES);
    }

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(RTT_OOB_CAPABILITIES.toBytes()).isEqualTo(mRttCapabilityWithHeaderBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(RttOobCapabilities.parseBytes(mRttCapabilityWithHeaderBytes))
                .isEqualTo(RTT_OOB_CAPABILITIES);
    }

    @Test
    public void testInvalidBytes() {
        byte[] invalidCapabilities = new byte[]{0};

        assertThrows(
                IllegalArgumentException.class,
                () -> RttOobCapabilities.parseBytes(invalidCapabilities)
        );

        byte[] invalidTechnology = Arrays.copyOf(mRttCapabilityWithHeaderBytes,
                mRttCapabilityWithHeaderBytes.length);
        invalidTechnology[0] = 0x00;

        assertThrows(
                IllegalArgumentException.class,
                () -> RttOobCapabilities.parseBytes(invalidTechnology)
        );
    }

}
