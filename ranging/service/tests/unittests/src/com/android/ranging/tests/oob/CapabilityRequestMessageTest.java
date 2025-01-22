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

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CapabilityRequestMessageTest {

    private static final ImmutableSet<RangingTechnology> UWB_CS_TECH_LIST =
            ImmutableSet.of(RangingTechnology.UWB, RangingTechnology.CS);
    private static final ImmutableSet<RangingTechnology> UWB_TECH_ONLY_LIST =
            ImmutableSet.of(RangingTechnology.UWB);
    private static final byte[] uwbCsTechBitmap = new byte[]{0x3, 0x00};
    private static final byte[] uwbTechOnlyBitmap = new byte[]{0x01, 0x00};
    private static final OobHeader OOB_HEADER =
            OobHeader.builder().setVersion(1).setMessageType(
                    MessageType.CAPABILITY_REQUEST).build();
    private static final byte[] headerBytes = new byte[]{0x1, 0x0};
    private static final byte[] uwbCsTechMessage = Bytes.concat(headerBytes, uwbCsTechBitmap);
    private static final byte[] uwbOnlyMessage = Bytes.concat(headerBytes, uwbTechOnlyBitmap);
    private static final byte[] noTechsMessage = Bytes.concat(headerBytes, new byte[]{0x0, 0x0});

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        CapabilityRequestMessage capabilityRequestUwbCs =
                CapabilityRequestMessage.builder()
                        .setHeader(OOB_HEADER)
                        .setRequestedRangingTechnologies(UWB_CS_TECH_LIST)
                        .build();

        CapabilityRequestMessage capabilityRequestUwbOnly =
                CapabilityRequestMessage.builder()
                        .setHeader(OOB_HEADER)
                        .setRequestedRangingTechnologies(UWB_TECH_ONLY_LIST)
                        .build();

        assertThat(capabilityRequestUwbCs.toBytes()).isEqualTo(uwbCsTechMessage);
        assertThat(capabilityRequestUwbOnly.toBytes()).isEqualTo(uwbOnlyMessage);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        CapabilityRequestMessage capabilityRequestUwbCs =
                CapabilityRequestMessage.parseBytes(uwbCsTechMessage);
        CapabilityRequestMessage capabilityRequestUwbOnly =
                CapabilityRequestMessage.parseBytes(uwbOnlyMessage);

        assertThat(capabilityRequestUwbCs.getRequestedRangingTechnologies())
                .isEqualTo(UWB_CS_TECH_LIST);
        assertThat(capabilityRequestUwbCs.getHeader()).isEqualTo(OOB_HEADER);
        assertThat(capabilityRequestUwbOnly.getRequestedRangingTechnologies())
                .isEqualTo(UWB_TECH_ONLY_LIST);
        assertThat(capabilityRequestUwbOnly.getHeader()).isEqualTo(OOB_HEADER);
    }

    @Test
    public void parseBytes_invalidSize_throwsException() throws Exception {
        byte[] shortMessage = new byte[]{0x1};
        assertThrows(
                IllegalArgumentException.class,
                () -> CapabilityRequestMessage.parseBytes(shortMessage));
    }

    @Test
    public void parseBytes_invalidMessageType_throwsException() throws Exception {
        byte[] invalidMessageTypeMessageBytes =
                CapabilityRequestMessage.builder()
                        .setHeader(
                                OobHeader.builder()
                                        .setVersion(1)
                                        .setMessageType(MessageType.SET_CONFIGURATION)
                                        .build())
                        .setRequestedRangingTechnologies(UWB_CS_TECH_LIST)
                        .build()
                        .toBytes();
        assertThrows(
                IllegalArgumentException.class,
                () -> CapabilityRequestMessage.parseBytes(invalidMessageTypeMessageBytes));
    }

    @Test
    public void parseBytes_noTechs() throws Exception {
        CapabilityRequestMessage capabilityRequestEmpty =
                CapabilityRequestMessage.parseBytes(noTechsMessage);

        assertThat(capabilityRequestEmpty.getRequestedRangingTechnologies()).isEmpty();
    }
}
