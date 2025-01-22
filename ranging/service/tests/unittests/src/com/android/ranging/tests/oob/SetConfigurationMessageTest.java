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

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SetConfigurationMessageTest {

    private static final OobHeader OOB_HEADER =
            OobHeader.builder().setVersion(1).setMessageType(MessageType.SET_CONFIGURATION).build();
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
    private static final SetConfigurationMessage SET_CONFIGURATION_MESSAGE =
            SetConfigurationMessage.builder()
                    .setHeader(OOB_HEADER)
                    .setRangingTechnologiesSet(ImmutableList.of(RangingTechnology.UWB))
                    .setStartRangingList(ImmutableList.of(RangingTechnology.UWB))
                    .setUwbConfig(UWB_CONFIG)
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

    private static final byte[] setConfigurationMessageUnknownBytes =
            new byte[]{
                    // Ranging technologies set bitmap
                    (byte) 0x80,
                    0x0,
                    // Ranging technologies to start ranging
                    0x0,
                    0x0
            };

    private static final byte[] unknownConfigBytes =
            new byte[]{
                    // Id
                    0x7,
                    // Size
                    0x2,
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

    private static final byte[] setConfigurationMessageUnknownSetBytes =
            Bytes.concat(oobHeaderBytes, setConfigurationMessageUnknownBytes, unknownConfigBytes);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(SET_CONFIGURATION_MESSAGE.toBytes()).isEqualTo(setConfigurationMessageBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(SetConfigurationMessage.parseBytes(setConfigurationMessageBytes))
                .isEqualTo(SET_CONFIGURATION_MESSAGE);
    }

    @Test
    public void toBytes_noConfigPresent_convertsCorrectly() throws Exception {
        SetConfigurationMessage message =
                SetConfigurationMessage.builder()
                        .setHeader(OOB_HEADER)
                        .setRangingTechnologiesSet(ImmutableList.of())
                        .build();
        byte[] expectedBytes = new byte[]{0x1, 0x2, 0x0, 0x0, 0x0, 0x0};

        assertThat(message.toBytes()).isEqualTo(expectedBytes);
    }

    @Test
    public void parseBytes_invalidMessageSize_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> SetConfigurationMessage.parseBytes(new byte[]{0x01}));
    }

    @Test
    public void parseBytes_invalidMessageType_throws() throws Exception {
        byte[] invalidMessageTypeMessageBytes =
                SetConfigurationMessage.builder()
                        .setHeader(
                                OobHeader.builder()
                                        .setVersion(1)
                                        .setMessageType(MessageType.CAPABILITY_REQUEST)
                                        .build())
                        .setRangingTechnologiesSet(ImmutableList.of())
                        .build()
                        .toBytes();
        assertThrows(
                IllegalArgumentException.class,
                () -> SetConfigurationMessage.parseBytes(invalidMessageTypeMessageBytes));
    }

    @Test
    public void parseBytes_unknownRangingTechnologyId_throwsException() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> SetConfigurationMessage.parseBytes(setConfigurationMessageUnknownSetBytes));
    }

    @Test
    public void uwbConfigBuilder_invalidCountryAddress_throwsException() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UwbOobConfig.builder()
                                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x2, 0x4}))
                                .setSessionId(672)
                                .setSelectedConfigId(12)
                                .setSelectedChannel(3)
                                .setSelectedPreambleIndex(4)
                                .setSelectedRangingIntervalMs(500)
                                .setSelectedSlotDurationMs(15)
                                .setSessionKey(hexStringToByteArray("0102030405060708"))
                                .setCountryCode("CLDN")
                                .setDeviceRole(UwbOobConfig.OobDeviceRole.INITIATOR)
                                .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLLER)
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UwbOobConfig.builder()
                                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x2, 0x4}))
                                .setSessionId(672)
                                .setSelectedConfigId(12)
                                .setSelectedChannel(3)
                                .setSelectedPreambleIndex(4)
                                .setSelectedRangingIntervalMs(500)
                                .setSelectedSlotDurationMs(15)
                                .setSessionKey(hexStringToByteArray("0102030405060708"))
                                .setCountryCode("B")
                                .setDeviceRole(UwbOobConfig.OobDeviceRole.INITIATOR)
                                .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLLER)
                                .build());
    }

    @Test
    public void uwbConfigBuilder_invalidSessionKeySize_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        UwbOobConfig.builder()
                                .setUwbAddress(UwbAddress.fromBytes(new byte[]{0x2, 0x4}))
                                .setSessionId(672)
                                .setSelectedConfigId(12)
                                .setSelectedChannel(3)
                                .setSelectedPreambleIndex(4)
                                .setSelectedRangingIntervalMs(500)
                                .setSelectedSlotDurationMs(15)
                                .setSessionKey(hexStringToByteArray("010203"))
                                .setCountryCode("US")
                                .setDeviceRole(UwbOobConfig.OobDeviceRole.INITIATOR)
                                .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLLER)
                                .build());
    }

    @Test
    public void builder_rangingTechnologiesSetAndConfigMismatch_throws() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SetConfigurationMessage.builder()
                                .setHeader(OOB_HEADER)
                                .setRangingTechnologiesSet(ImmutableList.of(RangingTechnology.CS))
                                .setUwbConfig(UWB_CONFIG)
                                .build());
    }

    @Test
    public void builder_rangingTechnologiesSetAndStartRangingListMismatch_throws()
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SetConfigurationMessage.builder()
                                .setHeader(OOB_HEADER)
                                .setStartRangingList(ImmutableList.of(RangingTechnology.CS))
                                .build());
    }

    @Test
    public void uwbConfig_parseBytes_invalidRangingTechId_throws() throws Exception {
        byte[] uwbConfigBytesInvalidId = uwbConfigBytes;
        uwbConfigBytesInvalidId[0] = 0x6;
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobConfig.parseBytes(uwbConfigBytesInvalidId));
    }

    @Test
    public void uwbConfig_parseBytes_invalidSessionKeyLength_throws() throws Exception {
        byte[] uwbConfigBytesInvalidSessionKeyLength = uwbConfigBytes;
        uwbConfigBytesInvalidSessionKeyLength[14] = 0x20;
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobConfig.parseBytes(uwbConfigBytesInvalidSessionKeyLength));
    }

    @Test
    public void uwbConfig_parseBytes_sizeLowerThanMinSize_throws() throws Exception {
        byte[] uwbConfigBytesTooSmall = new byte[]{0x01};
        assertThrows(
                IllegalArgumentException.class,
                () -> UwbOobConfig.parseBytes(uwbConfigBytesTooSmall));
    }
}
