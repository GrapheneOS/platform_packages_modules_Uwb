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

package com.android.server.ranging.tests.oob.packets;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.MessageId;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.StopResponse;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.UnknownOobMessage;
import com.android.server.ranging.oob.packets.Version;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class OobMessageTest {

    @Test
    public void parseBytes_validHeader_parsesCorrectly() throws Exception {
        byte[] requestMessageHeader1 = new byte[]{0x1, 0x0};
        byte[] requestMessageHeader2 = new byte[]{(byte) 0xff, 0x3};

        assertThat(OobMessage.fromBytes(requestMessageHeader1)).isEqualTo(
                new UnknownOobMessage.Builder()
                        .setVersion(Version.Current)
                        .setId(MessageId.CapabilitiesRequest)
                        .setPayload(new byte[] {})
                        .build());
        assertThat(OobMessage.fromBytes(requestMessageHeader2)).isEqualTo(
                new UnknownOobMessage.Builder()
                        .setVersion(Version.Future((byte) 255))
                        .setId(MessageId.ConfigurationResponse)
                        .setPayload(new byte[] {})
                        .build());
    }

    @Test
    public void parseBytes_tooShortPayload_throwsException() throws Exception {
        byte[] requestMessageHeader = new byte[]{0x2};
        assertThrows(Exception.class,
                () -> OobMessage.fromBytes(requestMessageHeader));
    }

    @Test
    public void parseBytes_unknownMessageType_parsesCorrectly() throws Exception {
        byte[] requestMessageHeader = new byte[]{(byte) 0xff, (byte) 0xff};
        assertThat(OobMessage.fromBytes(requestMessageHeader))
                .isEqualTo(new UnknownOobMessage.Builder()
                        .setVersion(Version.Future((byte) 255))
                        .setId(MessageId.Reserved((byte) 255))
                        .setPayload(new byte[] {})
                        .build());
    }

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        OobMessage header1 =
                new ConfigurationRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToConfigure(new TechnologySet.Builder().build())
                        .setTechnologiesToStart(new TechnologySet.Builder().build())
                        .setConfigs(new Configuration[]{})
                        .build();
        OobMessage header2 =
                new StopResponse.Builder()
                        .setVersion(Version.Future((byte) 255))
                        .setStoppedTechnologies(new TechnologySet.Builder().build())
                        .build();

        assertThat(header1.toBytes()).isEqualTo(new byte[]{0x1, 0x2, 0, 0, 0, 0});
        assertThat(header2.toBytes()).isEqualTo(new byte[]{(byte) 0xff, 0x7, 0, 0});
    }
}
