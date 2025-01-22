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
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.StopRangingMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StopRangingMessageTest {

    private static final OobHeader OOB_HEADER =
            OobHeader.builder().setVersion(1).setMessageType(MessageType.STOP_RANGING).build();
    private static final ImmutableList<RangingTechnology> UWB_CS_TECH_LIST =
            ImmutableList.of(RangingTechnology.UWB, RangingTechnology.CS);
    private static final ImmutableList<RangingTechnology> UWB_TECH_ONLY_LIST =
            ImmutableList.of(RangingTechnology.UWB);

    private static final byte[] uwbCsTechBitmap = new byte[]{0x3, 0x00};
    private static final byte[] uwbTechOnlyBitmap = new byte[]{0x01, 0x00};
    private static final byte[] headerBytes = new byte[]{0x1, 0x6};
    private static final byte[] uwbCsTechMessage = Bytes.concat(headerBytes, uwbCsTechBitmap);
    private static final byte[] uwbOnlyMessage = Bytes.concat(headerBytes, uwbTechOnlyBitmap);
    private static final byte[] noTechsMessage = Bytes.concat(headerBytes, new byte[]{0x0, 0x0});

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        StopRangingMessage stopRangingMessageUwbCs =
                StopRangingMessage.builder()
                        .setOobHeader(OOB_HEADER)
                        .setRangingTechnologiesToStop(UWB_CS_TECH_LIST)
                        .build();
        StopRangingMessage stopRangingMessageUwbOnly =
                StopRangingMessage.builder()
                        .setOobHeader(OOB_HEADER)
                        .setRangingTechnologiesToStop(UWB_TECH_ONLY_LIST)
                        .build();

        assertThat(stopRangingMessageUwbCs.toBytes()).isEqualTo(uwbCsTechMessage);
        assertThat(stopRangingMessageUwbOnly.toBytes()).isEqualTo(uwbOnlyMessage);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(StopRangingMessage.parseBytes(uwbCsTechMessage))
                .isEqualTo(
                        StopRangingMessage.builder()
                                .setOobHeader(OOB_HEADER)
                                .setRangingTechnologiesToStop(UWB_CS_TECH_LIST)
                                .build());
        assertThat(StopRangingMessage.parseBytes(uwbOnlyMessage))
                .isEqualTo(
                        StopRangingMessage.builder()
                                .setOobHeader(OOB_HEADER)
                                .setRangingTechnologiesToStop(UWB_TECH_ONLY_LIST)
                                .build());
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] tooShortMessage = new byte[]{0x0A};
        assertThrows(
                IllegalArgumentException.class,
                () -> StopRangingMessage.parseBytes(tooShortMessage));
    }

    @Test
    public void parseBytes_invalidMessageType_throws() throws Exception {
        byte[] invalidMessageTypeMessageBytes =
                StopRangingMessage.builder()
                        .setOobHeader(
                                OobHeader.builder()
                                        .setVersion(1)
                                        .setMessageType(MessageType.SET_CONFIGURATION)
                                        .build())
                        .setRangingTechnologiesToStop(UWB_CS_TECH_LIST)
                        .build()
                        .toBytes();
        assertThrows(
                IllegalArgumentException.class,
                () -> StopRangingMessage.parseBytes(invalidMessageTypeMessageBytes));
    }

    @Test
    public void parseBytes_noTechsSet_parsesToEmpty() throws Exception {
        assertThat(StopRangingMessage.parseBytes(noTechsMessage))
                .isEqualTo(
                        StopRangingMessage.builder()
                                .setOobHeader(OOB_HEADER)
                                .setRangingTechnologiesToStop(ImmutableList.of())
                                .build());
    }
}
