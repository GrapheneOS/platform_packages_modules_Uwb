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
import static com.android.server.ranging.common.RangingUtils.technologyBitset;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.ranging.uwb.UwbAddress;
import android.util.Log;

import com.android.server.ranging.RangingTechnology;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CapabilitiesResponseTest {

    private static final UwbAddress UWB_ADDRESS = UwbAddress.fromBytes(new byte[]{8, 9});
    private static final ImmutableList<RangingTechnology> SUPPORTED_RANGING_TECHNOLOGIES =
            ImmutableList.of(RangingTechnology.UWB);
    private static final UwbCapabilities UWB_CAPABILITIES =
            new UwbCapabilities.Builder()
                    .setAddress(UWB_ADDRESS.getAddressBytes())
                    .setChannels(bitset(ImmutableList.of(5, 9)))
                    .setPreambleIndexes(bitset(ImmutableList.of(0, 31)))
                    .setConfigIds(bitset(ImmutableList.of(7, 15)))
                    .setMinInterval((short) 1000)
                    .setMinSlotDuration((byte) 20)
                    .setRoles((byte) (Byte.toUnsignedInt(UwbDeviceRole.Initiator.toByte())
                                    | Byte.toUnsignedInt(UwbDeviceRole.Responder.toByte())))
                    .build();
    private static final CapabilitiesResponseV1 CAPABILITY_RESPONSE_MESSAGE =
            new CapabilitiesResponseV1.Builder()
                    .setVersion(Version.Current)
                    .setSupportedTechnologies(technologyBitset(SUPPORTED_RANGING_TECHNOLOGIES))
                    .setCapabilities(new Capabilities[]{ UWB_CAPABILITIES })
                    .build();

    private static final byte[] oobHeaderBytes =
            new byte[]{
                    // Version
                    0x02,
                    // Message type
                    0x01,
            };

    private static final byte[] rangingTechBitmapUwb =
            new byte[]{
                    // No technologies set
                    0x01, 0x00
            };


    private static final byte[] rangingTechsBitmapUwbAndCs =
            new byte[]{
                    // Uwb and CS technology set
                    (byte) 0x03, 0x00
            };

    private static final byte[] uwbCapabilityBytes =
            new byte[]{
                    // Ranging technology Id (UWB)
                    0x00,
                    // Size
                    0x14,
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

    private static final byte[] csCapabilityBytes =
            new byte[]{
                    // Ranging technology Id (UWB)
                    0x01,
                    // Size
                    0x09,
                    // Security level bitmap
                    0x02,
                    // Device Address2
                    0x01,
                    0x02,
                    0x03,
                    0x04,
                    0x05,
                    0x06,
            };

    private static final byte[] capabilityResponseMessageUwbBytes =
            Bytes.concat(oobHeaderBytes, rangingTechBitmapUwb, uwbCapabilityBytes);


    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        Log.d("asdf", CAPABILITY_RESPONSE_MESSAGE.toString());
        assertThat(CAPABILITY_RESPONSE_MESSAGE.toBytes()).isEqualTo(
                capabilityResponseMessageUwbBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(CapabilitiesResponseV1.fromBytes(capabilityResponseMessageUwbBytes))
                .isEqualTo(CAPABILITY_RESPONSE_MESSAGE);
    }

    @Test
    public void toBytes_noTechnologiesSet_convertsCorrectly() throws Exception {
        CapabilitiesResponseV1 capabilityResponseNoTechnologiesSet =
                new CapabilitiesResponseV1.Builder()
                        .setVersion(Version.Current)
                        .setSupportedTechnologies(new TechnologySet.Builder().build())
                        .setCapabilities(new Capabilities[] {})
                        .build();
        byte[] nothingSetBytes = new byte[]{0x2, 0x1, 0x0, 0x0};
        assertThat(capabilityResponseNoTechnologiesSet.toBytes()).isEqualTo(nothingSetBytes);
    }

    @Test
    public void parseBytes_invalidMessageSize_throws() throws Exception {
        byte[] data = new byte[]{};

        assertThrows(Exception.class,
                () -> CapabilitiesResponseV1.fromBytes(data));
    }

    @Test
    public void parseBytes_multipleTechnologies_parsesCorrectly() throws Exception {
        final byte[] responseBytes = Bytes.concat(
                oobHeaderBytes,
                rangingTechsBitmapUwbAndCs,
                uwbCapabilityBytes,
                csCapabilityBytes);
        CapabilitiesResponseV1 response = CapabilitiesResponseV1.fromBytes(responseBytes);
        assertThat(response.getSupportedTechnologies())
                .isEqualTo(new TechnologySet.Builder().setUwb(true).setBleCs(true).build());

        UwbCapabilities uwbCapabilities = (UwbCapabilities) response.getCapabilities()[0];
        assertThat(uwbCapabilities.getAddress()).isEqualTo(new byte[] {0x08, 0x09});

        BleCsCapabilities csCapabilities = (BleCsCapabilities) response.getCapabilities()[1];
        assertThat(csCapabilities.getAddress()).isEqualTo(macAddressToBytes("01:02:03:04:05:06"));

    }
}
