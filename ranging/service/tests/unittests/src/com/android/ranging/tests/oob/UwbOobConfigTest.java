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


import static com.android.server.ranging.RangingUtils.Conversions.hexStringToByteArray;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.ranging.uwb.UwbAddress;

import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class UwbOobConfigTest {

    private static final UwbOobConfig UWB_CONFIG =
            UwbOobConfig.builder()
                    .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x2, 0x4}))
                    .setSessionId(672)
                    .setSelectedConfigId(12)
                    .setSelectedChannel(3)
                    .setSelectedPreambleIndex(4)
                    .setSelectedRangingIntervalMs(500)
                    .setSelectedSlotDurationMs(15)
                    .setSessionKey(hexStringToByteArray("0102030405060708"))
                    .setCountryCode("US")
                    .setDeviceRole(UwbOobConfig.OobDeviceRole.INITIATOR)
                    .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLLER)
                    .build();

    private static final byte[] uwbTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x00,
                    // Size
                    (byte) 0x1B,
            };

    private static final byte[] unknownTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x07,
                    // Size
                    (byte) 0x1B,
            };

    private static final byte[] uwbConfigBytes =
            new byte[]{
                    // Uwb address
                    0x2,
                    0x4,
                    // Session Id int little endian
                    (byte) 0xA0,
                    0x2,
                    0x0,
                    0x0,
                    // Selected Config Id
                    0xC,
                    // Selected Channel
                    0x3,
                    // Selected Preamble Index
                    0x4,
                    // Selected ranging interval int little endian
                    (byte) 0xF4,
                    0x1,
                    // Selected slot duration 1 byte
                    0xF,
                    // Session key length
                    0x8,
                    // Session key
                    0x1,
                    0x2,
                    0x3,
                    0x4,
                    0x5,
                    0x6,
                    0x7,
                    0x8,
                    // Country code (US ascii)
                    0x55,
                    0x53,
                    // Device role
                    0x1,
                    // Device mode
                    0x1,
            };

    private static final byte[] uwbConfigWithHeaderBytes =
            Bytes.concat(uwbTechHeaderBytes, uwbConfigBytes);

    private static final byte[] uwbConfigWithUnknownHeaderBytes =
            Bytes.concat(unknownTechHeaderBytes, uwbConfigBytes);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(UWB_CONFIG.toBytes()).isEqualTo(uwbConfigWithHeaderBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(UwbOobConfig.parseBytes(uwbConfigWithHeaderBytes)).isEqualTo(UWB_CONFIG);
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(IllegalArgumentException.class, () -> UwbOobConfig.parseBytes(shortMessage));
    }

    @Test
    public void parseBytes_mismatchedHeaderSize_throws() throws Exception {
        byte[] mismatchedHeaderSizeBytes = Bytes.concat(uwbTechHeaderBytes, new byte[]{0x00, 0x01});
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobConfig.parseBytes(mismatchedHeaderSizeBytes));
    }

    @Test
    public void parseBytes_invalidTechnologyId_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobConfig.parseBytes(uwbConfigWithUnknownHeaderBytes));
    }
}
