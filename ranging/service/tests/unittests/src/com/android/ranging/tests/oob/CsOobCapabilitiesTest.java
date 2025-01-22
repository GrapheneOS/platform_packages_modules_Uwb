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

import com.android.server.ranging.cs.CsOobCapabilities;
import com.android.server.ranging.cs.CsOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CsOobCapabilitiesTest {

    private static final CsOobCapabilities CS_CAPABILITIES =
            CsOobCapabilities.builder()
                    .setSupportedSecurityTypes(ImmutableList.of(
                            CsOobConfig.CsSecurityType.LEVEL_TWO,
                            CsOobConfig.CsSecurityType.LEVEL_FOUR))
                    .setBluetoothAddress("AC:37:43:BC:A9:28")
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

    private static final byte[] csCapabilityBytesUnknownSecurityType =
            new byte[]{
                    // Supported Security Types
                    (byte) 0x80,
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

    private static final byte[] csCapabilityWithUnknownSecurityTypeBytes =
            Bytes.concat(csTechHeaderBytes, csCapabilityBytesUnknownSecurityType);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(CS_CAPABILITIES.toBytes()).isEqualTo(csCapabilityWithHeaderBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(CsOobCapabilities.parseBytes(csCapabilityWithHeaderBytes))
                .isEqualTo(CS_CAPABILITIES);
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(IllegalArgumentException.class,
                () -> CsOobCapabilities.parseBytes(shortMessage));
    }

    @Test
    public void parseBytes_mismatchedHeaderSize_throws() throws Exception {
        byte[] mismatchedHeaderSizeBytes = Bytes.concat(csTechHeaderBytes, new byte[]{0x00, 0x05});
        assertThrows(
                IllegalArgumentException.class,
                () -> CsOobCapabilities.parseBytes(mismatchedHeaderSizeBytes));
    }

    @Test
    public void parseBytes_invalidTechnologyId_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsOobCapabilities.parseBytes(unknownTechHeaderBytes));
    }

    @Test
    public void constructor_invalidBluetoothAddress_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CsOobCapabilities.builder()
                                .setSupportedSecurityTypes(ImmutableList.of(
                                        CsOobConfig.CsSecurityType.LEVEL_TWO,
                                        CsOobConfig.CsSecurityType.LEVEL_FOUR))
                                .setBluetoothAddress("AC:37:43BC:A9:28")
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CsOobCapabilities.builder()
                                .setSupportedSecurityTypes(ImmutableList.of(
                                        CsOobConfig.CsSecurityType.LEVEL_TWO,
                                        CsOobConfig.CsSecurityType.LEVEL_FOUR))
                                .setBluetoothAddress("AC:37:43:BC:A9:")
                                .build());
    }

    @Test
    public void parseBytes_invalidSecurityType_returnsUnknownSecurityType() throws Exception {
        CsOobCapabilities csCapabilities =
                CsOobCapabilities.parseBytes(csCapabilityWithUnknownSecurityTypeBytes);

        assertThat(csCapabilities.getSupportedSecurityTypes()).containsExactly(
                CsOobConfig.CsSecurityType.UNKNOWN);
    }
}
