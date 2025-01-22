/*
 * Copyright 2025 The Android Open Source Project
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

import android.ranging.uwb.UwbAddress;

import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UwbOobCapabilitiesTest {

    private static final UwbOobCapabilities UWB_CAPABILITIES =
            UwbOobCapabilities.builder()
                    .setUwbAddress(UwbAddress.fromBytes(new byte[]{8, 9}))
                    .setSupportedChannels(ImmutableList.of(5, 9))
                    .setSupportedPreambleIndexes(ImmutableList.of(1, 32))
                    .setSupportedConfigIds(ImmutableList.of(7, 15))
                    .setMinimumRangingIntervalMs(1000)
                    .setMinimumSlotDurationMs(20)
                    .setSupportedDeviceRole(
                            ImmutableList.of(
                                    UwbOobConfig.OobDeviceRole.INITIATOR,
                                    UwbOobConfig.OobDeviceRole.RESPONDER))
                    .build();

    private static final byte[] uwbTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x00,
                    // Size
                    0x14,
            };

    private static final byte[] unknownTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x07,
                    // Size
                    0x14,
            };

    private static final byte[] uwbCapabilityBytes =
            new byte[]{
                    // Uwb Address
                    0x08,
                    0x09,
                    // Supported channels bitmap
                    0x20,
                    0x02,
                    0x00,
                    0x00,
                    // Supported preamble indexes bitmap
                    0x01,
                    0x00,
                    0x00,
                    (byte) 0x80,
                    // Supported config ids bitmap
                    (byte) 0x80,
                    (byte) 0x80,
                    0x00,
                    0x00,
                    // Minimum ranging interval ms
                    (byte) 0xE8,
                    0x03,
                    // Minimum slot duration ms
                    0x14,
                    // Supported device role bitmap
                    0x03,
            };

    private static final byte[] uwbCapabilityWithHeaderBytes =
            Bytes.concat(uwbTechHeaderBytes, uwbCapabilityBytes);

    private static final byte[] uwbCapabilityWithUnknownHeaderBytes =
            Bytes.concat(unknownTechHeaderBytes, uwbCapabilityBytes);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(UWB_CAPABILITIES.toBytes()).isEqualTo(uwbCapabilityWithHeaderBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(UwbOobCapabilities.parseBytes(uwbCapabilityWithHeaderBytes))
                .isEqualTo(UWB_CAPABILITIES);
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(IllegalArgumentException.class,
                () -> UwbOobCapabilities.parseBytes(shortMessage));
    }

    @Test
    public void parseBytes_mismatchedHeaderSize_throws() throws Exception {
        byte[] mismatchedHeaderSizeBytes = Bytes.concat(uwbTechHeaderBytes, new byte[]{0x00, 0x01});
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobCapabilities.parseBytes(mismatchedHeaderSizeBytes));
    }

    @Test
    public void parseBytes_invalidTechnologyId_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobCapabilities.parseBytes(uwbCapabilityWithUnknownHeaderBytes));
    }
}
