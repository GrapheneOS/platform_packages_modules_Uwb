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

import android.ranging.uwb.UwbAddress;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CapabilityResponseMessageTest {

    private static final OobHeader OOB_HEADER =
            OobHeader.builder().setVersion(1).setMessageType(
                    MessageType.CAPABILITY_RESPONSE).build();
    private static final UwbAddress UWB_ADDRESS = UwbAddress.fromBytes(new byte[]{8, 9});
    private static final ImmutableList<RangingTechnology> SUPPORTED_RANGING_TECHNOLOGIES =
            ImmutableList.of(RangingTechnology.UWB);
    private static final ImmutableList<RangingTechnology> RANGING_TECHNOLOGY_PRIORITY_LIST =
            ImmutableList.of(RangingTechnology.UWB);
    private static final UwbOobCapabilities UWB_CAPABILITIES =
            UwbOobCapabilities.builder()
                    .setUwbAddress(UWB_ADDRESS)
                    .setSupportedChannels(ImmutableList.of(5, 9))
                    .setSupportedPreambleIndexes(ImmutableList.of(1, 32))
                    .setSupportedConfigIds(ImmutableList.of(7, 15))
                    .setMinimumRangingIntervalMs(1000)
                    .setMinimumSlotDurationMs(20)
                    .setSupportedDeviceRole(
                            ImmutableList.of(
                                    UwbOobConfig.OobDeviceRole.INITIATOR,
                                    UwbOobConfig.OobDeviceRole.RESPONDER))
                    .build();
    private static final CapabilityResponseMessage CAPABILITY_RESPONSE_MESSAGE =
            CapabilityResponseMessage.builder()
                    .setSupportedRangingTechnologies(SUPPORTED_RANGING_TECHNOLOGIES)
                    .setHeader(OOB_HEADER)
                    .setUwbCapabilities(UWB_CAPABILITIES)
                    .setRangingTechnologiesPriority(RANGING_TECHNOLOGY_PRIORITY_LIST)
                    .build();

    private static final byte[] oobHeaderBytes =
            new byte[]{
                    // Version
                    0x01,
                    // Message type
                    0x01,
            };

    private static final byte[] rangingTechBitmapUwb =
            new byte[]{
                    // No technologies set
                    0x01, 0x00
            };

    private static final byte[] rangingTechsBitmapUnknown =
            new byte[]{
                    // Unknown technology set
                    (byte) 0x80, 0x00
            };

    private static final byte[] rangingTechsBitmapUnknownAndUwb =
            new byte[]{
                    // Unknown and Uwb technology set
                    (byte) 0x81, 0x00
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

    private static final byte[] unknownTechnologyCapabilityBytes =
            new byte[]{
                    // Invalid ranging tech id
                    0x07,
                    // Size
                    0x02
            };

    private static final byte[] capabilityResponseMessageUwbBytes =
            Bytes.concat(oobHeaderBytes, rangingTechBitmapUwb, uwbCapabilityBytes);

    private static final byte[] capabilityResponseMessageUnknownBytes =
            Bytes.concat(oobHeaderBytes, rangingTechsBitmapUnknown,
                    unknownTechnologyCapabilityBytes);

    private static final byte[] capabilityResponseMessageUnknownAndUwbBytes =
            Bytes.concat(
                    oobHeaderBytes,
                    rangingTechsBitmapUnknownAndUwb,
                    unknownTechnologyCapabilityBytes,
                    uwbCapabilityBytes);

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(CAPABILITY_RESPONSE_MESSAGE.toBytes()).isEqualTo(
                capabilityResponseMessageUwbBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(CapabilityResponseMessage.parseBytes(capabilityResponseMessageUwbBytes))
                .isEqualTo(CAPABILITY_RESPONSE_MESSAGE);
    }

    @Test
    public void toBytes_noTechnologiesSet_convertsCorrectly() throws Exception {
        CapabilityResponseMessage capabilityResponseNoTechnologiesSet =
                CapabilityResponseMessage.builder()
                        .setHeader(OOB_HEADER)
                        .setSupportedRangingTechnologies(ImmutableList.of())
                        .build();
        byte[] nothingSetBytes = new byte[]{0x1, 0x1, 0x0, 0x0};
        assertThat(capabilityResponseNoTechnologiesSet.toBytes()).isEqualTo(nothingSetBytes);
    }

    @Test
    public void parseBytes_invalidMessageSize_throws() throws Exception {
        byte[] data = new byte[]{};

        assertThrows(IllegalArgumentException.class,
                () -> CapabilityResponseMessage.parseBytes(data));
    }

    @Test
    public void parseBytes_invalidMessageType_throws() throws Exception {
        byte[] invalidMessageTypeMessageBytes =
                CapabilityResponseMessage.builder()
                        .setHeader(
                                OobHeader.builder()
                                        .setVersion(1)
                                        .setMessageType(MessageType.SET_CONFIGURATION)
                                        .build())
                        .setSupportedRangingTechnologies(ImmutableList.of())
                        .build()
                        .toBytes();

        assertThrows(
                IllegalArgumentException.class,
                () -> CapabilityResponseMessage.parseBytes(invalidMessageTypeMessageBytes));
    }

    @Test
    public void parseBytes_unknownRangingTechnologyId_throwsException() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> CapabilityResponseMessage
                        .parseBytes(capabilityResponseMessageUnknownBytes));
    }

    @Test
    public void parseBytes_unknownAndUwbRangingTechnologyIds_throwsException() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> CapabilityResponseMessage
                        .parseBytes(capabilityResponseMessageUnknownAndUwbBytes));
    }


    @Test
    public void builder_invalidPriorityList_duplicates_throws() throws Exception {
        ImmutableList<RangingTechnology> priorityList =
                ImmutableList.of(RangingTechnology.UWB, RangingTechnology.UWB);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CapabilityResponseMessage.builder()
                                .setHeader(OOB_HEADER)
                                .setUwbCapabilities(UWB_CAPABILITIES)
                                .setSupportedRangingTechnologies(SUPPORTED_RANGING_TECHNOLOGIES)
                                .setRangingTechnologiesPriority(priorityList)
                                .build());
    }

    @Test
    public void builder_invalidPriorityList_mismatch_throws() throws Exception {
        ImmutableList<RangingTechnology> priorityList = ImmutableList.of(RangingTechnology.CS);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CapabilityResponseMessage.builder()
                                .setHeader(OOB_HEADER)
                                .setUwbCapabilities(UWB_CAPABILITIES)
                                .setSupportedRangingTechnologies(SUPPORTED_RANGING_TECHNOLOGIES)
                                .setRangingTechnologiesPriority(priorityList)
                                .build());
    }

    @Test
    public void builder_invalidPriorityList_shouldBeEmpty_throws() throws Exception {
        ImmutableList<RangingTechnology> priorityList = ImmutableList.of(RangingTechnology.UWB);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        CapabilityResponseMessage.builder()
                                .setHeader(OOB_HEADER)
                                .setSupportedRangingTechnologies(SUPPORTED_RANGING_TECHNOLOGIES)
                                .setRangingTechnologiesPriority(priorityList)
                                .build());
    }
}
