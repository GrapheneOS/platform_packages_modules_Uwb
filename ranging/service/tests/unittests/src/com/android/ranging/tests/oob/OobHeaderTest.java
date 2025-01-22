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

import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OobHeaderTest {

    @Test
    public void parseBytes_validHeader_parsesCorrectly() throws Exception {
        byte[] requestMessageHeader1 = new byte[]{0x1, 0x0};
        byte[] requestMessageHeader2 = new byte[]{0x5, 0x3};

        assertThat(OobHeader.parseBytes(requestMessageHeader1))
                .isEqualTo(
                        OobHeader.builder()
                                .setVersion(1)
                                .setMessageType(MessageType.CAPABILITY_REQUEST)
                                .build());
        assertThat(OobHeader.parseBytes(requestMessageHeader2))
                .isEqualTo(
                        OobHeader.builder()
                                .setVersion(5)
                                .setMessageType(MessageType.SET_CONFIGURATION_RESPONSE)
                                .build());
    }

    @Test
    public void parseBytes_headerWithAdditionalBytes_parsesCorrectly() throws Exception {
        byte[] requestMessageHeader = new byte[]{0x2, 0x2, 0x0B, 0x33};
        assertThat(OobHeader.parseBytes(requestMessageHeader))
                .isEqualTo(
                        OobHeader.builder()
                                .setVersion(2)
                                .setMessageType(MessageType.SET_CONFIGURATION)
                                .build());
    }

    @Test
    public void parseBytes_tooShortPayload_throwsException() throws Exception {
        byte[] requestMessageHeader = new byte[]{0x2};
        assertThrows(IllegalArgumentException.class,
                () -> OobHeader.parseBytes(requestMessageHeader));
    }

    @Test
    public void parseBytes_unknownMessageType_parsesCorrectly() throws Exception {
        byte[] requestMessageHeader = new byte[]{0x2, 0x9};
        assertThat(OobHeader.parseBytes(requestMessageHeader))
                .isEqualTo(OobHeader.builder().setVersion(2).setMessageType(
                        MessageType.UNKNOWN).build());
    }

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        OobHeader header1 =
                OobHeader.builder().setVersion(1).setMessageType(
                        MessageType.SET_CONFIGURATION).build();
        OobHeader header2 =
                OobHeader.builder().setVersion(7).setMessageType(
                        MessageType.STOP_RANGING_RESPONSE).build();

        assertThat(header1.toBytes()).isEqualTo(new byte[]{0x1, 0x2});
        assertThat(header2.toBytes()).isEqualTo(new byte[]{0x7, 0x7});
    }
}
