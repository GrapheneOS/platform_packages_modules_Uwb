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

package com.android.server.ranging.oob.packets;

import static com.android.server.ranging.common.RangingUtils.bitset;
import static com.android.server.ranging.common.RangingUtils.macAddressToBytes;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public final class BleCsCapabilitiesTest {

    private static final BleCsCapabilities CS_CAPABILITIES =
            new BleCsCapabilities.Builder()
                    .setSecurityLevels((byte) bitset(List.of(2, 4)))
                    .setAddress(macAddressToBytes("AC:37:43:BC:A9:28"))
                    .build();

    private static final byte[] csTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x01,
                    // Size
                    0x09,
            };

    private static final byte[] unknownTechHeaderBytes =
            new byte[]{
                    // Technology Id
                    0x09,
                    // Size
                    0x09,
            };

    private static final byte[] csCapabilityBytes =
            new byte[]{
                    // Supported Security Types
                    0x14,
                    // Bluetooth Address
                    (byte) 0xAC,
                    0x37,
                    0x43,
                    (byte) 0xBC,
                    (byte) 0xA9,
                    0x28,
            };

    private static final byte[] csCapabilityWithHeaderBytes =
            Bytes.concat(csTechHeaderBytes, csCapabilityBytes);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(CS_CAPABILITIES.toBytes()).isEqualTo(csCapabilityWithHeaderBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(BleCsCapabilities.fromBytes(csCapabilityWithHeaderBytes))
                .isEqualTo(CS_CAPABILITIES);
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(Exception.class,
                () -> BleCsCapabilities.fromBytes(shortMessage));
    }

    @Test
    public void parseBytes_mismatchedHeaderSize_throws() throws Exception {
        byte[] mismatchedHeaderSizeBytes = Bytes.concat(csTechHeaderBytes, new byte[]{0x00, 0x05});
        assertThrows(
                Exception.class,
                () -> BleCsCapabilities.fromBytes(mismatchedHeaderSizeBytes));
    }

    @Test
    public void parseBytes_invalidTechnologyId_throws() throws Exception {
        assertThrows(
                Exception.class,
                () -> BleCsCapabilities.fromBytes(unknownTechHeaderBytes));
    }
}
