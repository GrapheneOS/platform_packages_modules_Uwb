/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.ranging.uwb;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DlTdoaRangingParamsTest {

    @Test
    public void testCreateFromFiraConfigPacket_withWifiTimeSync() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (33), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x21, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 12 (0x5C)
            (byte) 0x5C,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22,
            // WiFi Time Sync Sub-Element: Type 0xB, Length 15 (0xBF)
            (byte) 0xBF, (byte) 0x00,
            // Profile ID (0x01)
            (byte) 0x01,
            // Mode 0x0 (Short), Control 0x03 (b0=1, b1=1)
            (byte) 0x03,
            // DT-Anchor Address
            (byte) 0xAA, (byte) 0xBB,
            // Time Offset
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // Time Offset Uncertainty
            (byte) 0x00, (byte) 0x00,
            // Round Index (b0)
            (byte) 0x07,
            // Initiator DT-Anchor Count
            (byte) 0x01,
            // Entry 1 Address
            (byte) 0xCC, (byte) 0xDD,
            // Entry 1 Round Index
            (byte) 0x08,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getRangingRoundIndexes())
                .isEqualTo(new byte[] {(byte) 0x07, (byte) 0x08});
    }

    @Test
    public void testCreateFromFiraConfigPacket_withWifiTimeSync_rangingRoundIndexesOverride() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (33), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x21, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 12 (0x5C)
            (byte) 0x5C,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22,
            // WiFi Time Sync Sub-Element: Type 0xB, Length 15 (0xBF)
            (byte) 0xBF, (byte) 0x00,
            // Profile ID (0x01)
            (byte) 0x01,
            // Mode 0x0 (Short), Control 0x03 (b0=1, b1=1)
            (byte) 0x03,
            // DT-Anchor Address
            (byte) 0xAA, (byte) 0xBB,
            // Time Offset
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // Time Offset Uncertainty
            (byte) 0x00, (byte) 0x00,
            // Round Index (b0)
            (byte) 0x07,
            // Initiator DT-Anchor Count
            (byte) 0x01,
            // Entry 1 Address
            (byte) 0xCC, (byte) 0xDD,
            // Entry 1 Round Index
            (byte) 0x08,
        };

        DlTdoaRangingParams params =
                DlTdoaRangingParams.createFromFiraConfigPacket(
                    config, new byte[] {(byte) 0x05, (byte) 0x05});

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getRangingRoundIndexes())
                .isEqualTo(new byte[] {(byte) 0x05, (byte) 0x05});
    }

    @Test
    public void testCreateFromFiraConfigPacket_onlyUwbConfig() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (16), OUI (0x5A, 0x18, 0xFF)
            (byte) 0x0DD, (byte) 0x10, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 12 (0x5C)
            (byte) 0x5C,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getDeviceAddress())
                .isEqualTo(UwbAddress.fromBytes(new byte[] {0x11, 0x22}));
    }

    @Test
    public void testCreateFromFiraConfigPacket_bothSubElements() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (33), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x21, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 12 (0x5C)
            (byte) 0x5C,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22,
            // WiFi Time Sync Sub-Element: Type 0xB, Length 15 (0xBF)
            (byte) 0xBF, (byte) 0x00,
            // Profile ID (0x01)
            (byte) 0x01,
            // Mode 0x0 (Short), Control 0x03 (b0=1, b1=1)
            (byte) 0x03,
            // DT-Anchor Address
            (byte) 0xAA, (byte) 0xBB,
            // Time Offset
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00,
            // Time Offset Uncertainty
            (byte) 0x00, (byte) 0x00,
            // Round Index (b0)
            (byte) 0x07,
            // Initiator DT-Anchor Count
            (byte) 0x01,
            // Entry 1 Address
            (byte) 0xCC, (byte) 0xDD,
            // Entry 1 Round Index
            (byte) 0x08,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getDeviceAddress())
                .isEqualTo(UwbAddress.fromBytes(new byte[] {0x11, 0x22}));
        assertThat(params.getRangingRoundIndexes())
                .isEqualTo(new byte[] {(byte) 0x07, (byte) 0x08});
    }

    @Test
    public void testCreateFromFiraConfigPacket_onlyUwbConfig_defaultMeasurementVersion() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (12), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x0C, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 8 (0x58)
            (byte) 0x58,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params).isNotNull();
        assertThat(params.getMeasurementVersion()).isEqualTo(
                DlTdoaRangingParams.MEASUREMENT_VERSION_1);
    }

    @Test
    public void testCreateFromFiraConfigPacket_onlyUwbConfig_measurementVersion1() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (15), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 11 (0x5B)
            (byte) 0x5B,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DL_TDOA_MEASUREMENT_NTF_V2 (Tag 0x4F, Len 1, Value 0x00)
            (byte) 0x4F, (byte) 0x01, (byte) 0x00,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params).isNotNull();
        assertThat(params.getMeasurementVersion()).isEqualTo(
                DlTdoaRangingParams.MEASUREMENT_VERSION_1);
    }

    @Test
    public void testCreateFromFiraConfigPacket_onlyUwbConfig_measurementVersion2() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (15), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 11 (0x5B)
            (byte) 0x5B,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DL_TDOA_MEASUREMENT_NTF_V2 (Tag 0x4F, Len 1, Value 0x01)
            (byte) 0x4F, (byte) 0x01, (byte) 0x01,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params).isNotNull();
        assertThat(params.getMeasurementVersion()).isEqualTo(
                DlTdoaRangingParams.MEASUREMENT_VERSION_2);
    }

    @Test
    public void testCreateFromFiraConfigPacket_onlyUwbConfig_unsupportedMeasurementVersion() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (15), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 11 (0x5B)
            (byte) 0x5B,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // DL_TDOA_MEASUREMENT_NTF_V2 (Tag 0x4F, Len 1, Value 0x02 - Unsupported)
            (byte) 0x4F, (byte) 0x01, (byte) 0x02,
        };

        assertThrows(IllegalArgumentException.class,
                () -> DlTdoaRangingParams.createFromFiraConfigPacket(config, null));
    }

    @Test
    public void testCreateFromFiraConfigPacket_validBleCpConfig() {
        byte[] config = {
            // BLE Specific Header: Length 46, Type 0x16 (Service Data), UUID 0xFFF3
            (byte) 0x2E, (byte) 0x16, (byte) 0xF3, (byte) 0xFF,
            // Fragmentation Indication: Unfragmented (0xF0)
            (byte) 0xF0,
            // UWB Config Sub-Element: Type 5, Length flag for extension (0x5F)
            (byte) 0x5F,
            // Length extension: 25. subElementLength = 15 + 25 = 40.
            (byte) 0x19,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
            (byte) 0x06, (byte) 0x02, (byte) 0x20, (byte) 0x08,
            // PREAMBLE_CODE_INDEX (Tag 20, Len 1)
            (byte) 0x14, (byte) 0x01, (byte) 0x0C,
            // VENDOR_ID (Tag 39, Len 2)
            (byte) 0x27, (byte) 0x02, (byte) 0x08, (byte) 0x07,
            // STATIC_STS_IV (Tag 40, Len 6)
            (byte) 0x28, (byte) 0x06, (byte) 0xCA, (byte) 0xC8, (byte) 0xA6, (byte) 0xF7,
            (byte) 0x6F, (byte) 0x08,

            // SLOT_DURATION (Tag 8, Len 2)
            (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x09,
            // SLOTS_PER_RR (Tag 27, Len 1)
            (byte) 0x1B, (byte) 0x01, (byte) 0x0A,
            // RANGING_DURATION (Tag 9, Len 4)
            (byte) 0x09, (byte) 0x04, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
        };
        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params).isNotNull();
        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getDeviceAddress())
                .isEqualTo(UwbAddress.fromBytes(new byte[] {(byte) 0x20, (byte) 0x08}));
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(12);
    }

    @Test
    public void testCreateFromFiraConfigPacket_fragmentedBleConfig() {
        // Total data = 40. Sub-element header = 2. Total payload = 42.
        // Fragmentation indication (1) + UWB Config header (2) + partial TLVs (9) = 12.
        // adLength = 3 + 12 = 15.
        // Fragmentation indication (1) + remaining TLVs (31) = 32. adLength = 3 + 32 = 35.
        byte[] config = new byte[16 + 36];

        // Fragment 1
        // Length (15)
        config[0] = (byte) 15;
        // Data Type (0x16 - Service Data), UUID (0xFFF3)
        config[1] = (byte) 0x16;
        config[2] = (byte) 0xF3;
        config[3] = (byte) 0xFF;
        // Fragmentation indication: First fragment (0xF4)
        config[4] = (byte) 0xF4;
        // UWB Config Sub-Element: Type 5, Length flag for extension (0x5F)
        config[5] = (byte) 0x5F;
        // Length extension: 25. subElementLength = 15 + 25 = 40.
        config[6] = (byte) 0x19;
        // Profile ID (0x02), UWB Config ID (0x00)
        config[7] = (byte) 0x02;
        config[8] = (byte) 0x00;
        // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
        config[9] = (byte) 0x06;
        config[10] = (byte) 0x02;
        config[11] = (byte) 0x20;
        config[12] = (byte) 0x08;
        // PREAMBLE_CODE_INDEX (Tag 20, Len 1)
        config[13] = (byte) 0x14;
        config[14] = (byte) 0x01;
        // Value for Tag 20
        config[15] = (byte) 0x0C;

        // Fragment 2
        // Length (35)
        config[16] = (byte) 35;
        // Data Type (0x16), UUID (0xFFF4)
        config[17] = (byte) 0x16;
        config[18] = (byte) 0xF4;
        config[19] = (byte) 0xFF;
        // Fragmentation indication: Second fragment (0xF5)
        config[20] = (byte) 0xF5;
        // VENDOR_ID (Tag 39, Len 2)
        config[21] = (byte) 0x27;
        config[22] = (byte) 0x02;
        config[23] = (byte) 0x08;
        config[24] = (byte) 0x07;
        // STATIC_STS_IV (Tag 40, Len 6)
        config[25] = (byte) 0x28;
        config[26] = (byte) 0x06;
        config[27] = (byte) 0xCA;
        config[28] = (byte) 0xC8;
        config[29] = (byte) 0xA6;
        config[30] = (byte) 0xF7;
        config[31] = (byte) 0x6F;
        config[32] = (byte) 0x08;
        // SLOT_DURATION (Tag 8, Len 2)
        config[33] = (byte) 0x08;
        config[34] = (byte) 0x02;
        config[35] = (byte) 0x60;
        config[36] = (byte) 0x09;
        // SLOTS_PER_RR (Tag 27, Len 1)
        config[37] = (byte) 0x1B;
        config[38] = (byte) 0x01;
        config[39] = (byte) 0x0A;
        // RANGING_DURATION (Tag 9, Len 4)
        config[40] = (byte) 0x09;
        config[41] = (byte) 0x04;
        config[42] = (byte) 0xE8;
        config[43] = (byte) 0x03;
        config[44] = (byte) 0x00;
        config[45] = (byte) 0x00;
        // SESSION_ID (Tag 159, Len 4)
        config[46] = (byte) 0x9F;
        config[47] = (byte) 0x04;
        config[48] = (byte) 0x67;
        config[49] = (byte) 0x45;
        config[50] = (byte) 0x23;
        config[51] = (byte) 0x01;

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params).isNotNull();
        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getDeviceAddress())
                .isEqualTo(UwbAddress.fromBytes(new byte[] {(byte) 0x20, (byte) 0x08}));
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(12);
    }

    @Test
    public void testCreateFromFiraConfigPacket_fragmentedAcrossTwoPackets() {
        // Re-aligning to avoid confusing myself.
        // Packet 1: 20 bytes. Payload 16. adLength = 3 + 16 = 19.
        byte[] packet1 = new byte[] {
            // Length (19)
            (byte) 0x13,
            // Data Type (0x16), UUID (0xFFF3)
            (byte) 0x16, (byte) 0xF3, (byte) 0xFF,
            // Fragmentation indication: First fragment (0xF4)
            (byte) 0xF4,
            // UWB Config Sub-Element: Type 5, Length flag for extension (0x5F)
            (byte) 0x5F,
            // Length extension
            (byte) 0x19,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // DEVICE_MAC_ADDRESS (Tag 6, Len 2)
            (byte) 0x06, (byte) 0x02, (byte) 0x20, (byte) 0x08,
            // PREAMBLE_CODE_INDEX (Tag 20, Len 1)
            (byte) 0x14, (byte) 0x01, (byte) 0x0C,
            // VENDOR_ID (Tag 39, Len 2)
            (byte) 0x27, (byte) 0x02, (byte) 0x08, (byte) 0x07
        };
        // Packet 2: 32 bytes. Payload 28. adLength = 3 + 28 = 31.
        byte[] packet2 = new byte[] {
            // Length (31)
            (byte) 0x1F,
            // Data Type (0x16), UUID (0xFFF4)
            (byte) 0x16, (byte) 0xF4, (byte) 0xFF,
            // Fragmentation indication: Second fragment (0xF5)
            (byte) 0xF5,
            // STATIC_STS_IV (Tag 40, Len 6)
            (byte) 0x28, (byte) 0x06, (byte) 0xCA, (byte) 0xC8, (byte) 0xA6,
            (byte) 0xF7, (byte) 0x6F, (byte) 0x08,
            // SLOT_DURATION (Tag 8, Len 2)
            (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x09,
            // SLOTS_PER_RR (Tag 27, Len 1)
            (byte) 0x1B, (byte) 0x01, (byte) 0x0A,
            // RANGING_DURATION (Tag 9, Len 4)
            (byte) 0x09, (byte) 0x04, (byte) 0xE8, (byte) 0x03, (byte) 0x00,
            (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23,
            (byte) 0x01
        };
        // IV(6) + Slot(4) + SlotsRR(3) + Ranging(6) + Session(6) = 25. Correct.

        byte[] combinedConfig = new byte[packet1.length + packet2.length];
        System.arraycopy(packet1, 0, combinedConfig, 0, packet1.length);
        System.arraycopy(packet2, 0, combinedConfig, packet1.length, packet2.length);

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(
                combinedConfig, null);

        assertThat(params).isNotNull();
        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getDeviceAddress())
                .isEqualTo(UwbAddress.fromBytes(new byte[] {(byte) 0x20, (byte) 0x08}));
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(12);
    }

    @Test
    public void testCreateFromFiraConfigPacket_withInitiatorRoundIndex() {
        byte[] config = {
            // WiFi Specific Header: ID (0xDD), Length (15), OUI (0x5A, 0x18, 0xFF)
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // UWB Config Sub-Element: Type 5, Length 11 (0x5B)
            (byte) 0x5B,
            // Profile ID (0x02), UWB Config ID (0x00)
            (byte) 0x02, (byte) 0x00,
            // SESSION_ID (Tag 159, Len 4)
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // INITIATOR_DT_ANCHOR_ROUND_INDEX (Tag 0x52, Len 1, Value 0x05)
            (byte) 0x52, (byte) 0x01, (byte) 0x05,
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getRangingRoundIndexes()).isEqualTo(new byte[] {(byte) 0x05});
    }
}
