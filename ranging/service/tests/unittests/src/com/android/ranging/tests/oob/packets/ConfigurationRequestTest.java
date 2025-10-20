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


import static com.android.server.ranging.common.RangingUtils.hexStringToByteArray;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.UwbConfiguration;
import com.android.server.ranging.oob.packets.UwbDeviceMode;
import com.android.server.ranging.oob.packets.UwbDeviceRole;
import com.android.server.ranging.oob.packets.Version;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;

@RunWith(JUnit4.class)
public final class ConfigurationRequestTest {

    private static final UwbConfiguration UWB_CONFIG =
            new UwbConfiguration.Builder()
                    .setAddress(new byte[]{0x2, 0x4})
                    .setSessionId(672)
                    .setConfigId((byte) 12)
                    .setChannel((byte) 3)
                    .setPreambleIndex((byte) 4)
                    .setInterval((short) 500)
                    .setSlotDuration((byte) 15)
                    .setSessionKey(hexStringToByteArray("0102030405060708"))
                    .setCountryCode("US".getBytes(StandardCharsets.UTF_8))
                    .setDeviceRole(UwbDeviceRole.Initiator)
                    .setDeviceMode(UwbDeviceMode.Controller)
                    .build();

    private static final ConfigurationRequest SET_CONFIGURATION_MESSAGE =
            new ConfigurationRequest.Builder()
                    .setVersion(Version.Current)
                    .setTechnologiesToConfigure(new TechnologySet.Builder().setUwb(true).build())
                    .setTechnologiesToStart(new TechnologySet.Builder().setUwb(true).build())
                    .setConfigs(new Configuration[]{ UWB_CONFIG })
                    .build();

    private static final byte[] oobHeaderBytes =
            new byte[]{
                    // Version
                    0x01,
                    // Message type
                    0x02,
            };

    private static final byte[] setConfigurationMessageMissingConfigBytes =
            new byte[]{
                    // Ranging technologies set bitmap
                    0x1,
                    0x0,
                    // Ranging technologies to start ranging
                    0x1,
                    0x0
            };

    private static final byte[] uwbConfigBytes =
            new byte[]{
                    // Ranging technology Id (0 for UWB)
                    0x0,
                    // Size
                    (byte) 0x1B,
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

    private static final byte[] setConfigurationMessageBytes =
            Bytes.concat(oobHeaderBytes, setConfigurationMessageMissingConfigBytes, uwbConfigBytes);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(SET_CONFIGURATION_MESSAGE.toBytes()).isEqualTo(setConfigurationMessageBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(ConfigurationRequest.fromBytes(setConfigurationMessageBytes))
                .isEqualTo(SET_CONFIGURATION_MESSAGE);
    }

    @Test
    public void toBytes_noConfigPresent_convertsCorrectly() throws Exception {
        ConfigurationRequest message = new ConfigurationRequest.Builder()
                .setVersion(Version.Current)
                .setTechnologiesToConfigure(new TechnologySet.Builder().build())
                .setTechnologiesToStart(new TechnologySet.Builder().build())
                .setConfigs(new Configuration[]{})
                .build();
        byte[] expectedBytes = new byte[]{0x1, 0x2, 0x0, 0x0, 0x0, 0x0};

        assertThat(message.toBytes()).isEqualTo(expectedBytes);
    }

    @Test
    public void parseBytes_invalidMessageSize_throws() throws Exception {
        assertThrows(
                Exception.class,
                () -> ConfigurationRequest.fromBytes(new byte[]{0x01}));
    }

    @Test
    public void uwbConfig_parseBytes_invalidRangingTechId_throws() throws Exception {
        byte[] uwbConfigBytesInvalidId = uwbConfigBytes;
        uwbConfigBytesInvalidId[0] = 0x6;
        assertThrows(
                Exception.class,
                () -> UwbConfiguration.fromBytes(uwbConfigBytesInvalidId));
    }

    @Test
    public void uwbConfig_parseBytes_invalidSessionKeyLength_throws() throws Exception {
        byte[] uwbConfigBytesInvalidSessionKeyLength = uwbConfigBytes;
        uwbConfigBytesInvalidSessionKeyLength[14] = 0x20;
        assertThrows(
                Exception.class,
                () -> UwbConfiguration.fromBytes(uwbConfigBytesInvalidSessionKeyLength));
    }

    @Test
    public void uwbConfig_parseBytes_sizeLowerThanMinSize_throws() throws Exception {
        byte[] uwbConfigBytesTooSmall = new byte[]{0x01};
        assertThrows(
                Exception.class,
                () -> UwbConfiguration.fromBytes(uwbConfigBytesTooSmall));
    }
}
