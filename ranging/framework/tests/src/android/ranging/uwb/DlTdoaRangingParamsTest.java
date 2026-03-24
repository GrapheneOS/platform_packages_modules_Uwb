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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DlTdoaRangingParamsTest {

    @Test
    public void testCreateFromFiraConfigPacket_withWifiTimeSync() {
        byte[] config = {
            // WiFi Specific Header: ID, Length (33), OUI
            (byte) 0xDD, (byte) 0x21, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // Sub-Element 0x05 (UWB Config): Type 5, Length 12
            (byte) 0x5C,
            (byte) 0x02, (byte) 0x00, // Profile ID, UWB Config ID
            // SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22, // DEVICE_MAC_ADDRESS
            // Sub-Element 0x0B (WiFi Time Sync): Type 0xB, Length 15 (with extension 0)
            (byte) 0xBF, (byte) 0x00,
            (byte) 0x01, // Profile ID
            (byte) 0x03, // Mode 0x0 (Short), Control 0x03 (b0=1, b1=1)
            (byte) 0xAA, (byte) 0xBB, // DT-Anchor Address
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // Time Offset
            (byte) 0x00, (byte) 0x00, // Time Offset Uncertainty
            (byte) 0x07, // Round Index (b0)
            (byte) 0x01, // Initiator DT-Anchor Count
            (byte) 0xCC, (byte) 0xDD, // Entry 1 Address
            (byte) 0x08, // Entry 1 Round Index
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getRangingRoundIndexes())
                .isEqualTo(new byte[] {(byte) 0x07, (byte) 0x08});
    }

    @Test
    public void testCreateFromFiraConfigPacket_withWifiTimeSync_rangingRoundIndexesOverride() {
        byte[] config = {
            // WiFi Specific Header: ID, Length (33), OUI
            (byte) 0xDD, (byte) 0x21, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // Sub-Element 0x05 (UWB Config): Type 5, Length 12
            (byte) 0x5C,
            (byte) 0x02, (byte) 0x00, // Profile ID, UWB Config ID
            // SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22, // DEVICE_MAC_ADDRESS
            // Sub-Element 0x0B (WiFi Time Sync): Type 0xB, Length 15 (with extension 0)
            (byte) 0xBF, (byte) 0x00,
            (byte) 0x01, // Profile ID
            (byte) 0x03, // Mode 0x0 (Short), Control 0x03 (b0=1, b1=1)
            (byte) 0xAA, (byte) 0xBB, // DT-Anchor Address
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // Time Offset
            (byte) 0x00, (byte) 0x00, // Time Offset Uncertainty
            (byte) 0x07, // Round Index (b0)
            (byte) 0x01, // Initiator DT-Anchor Count
            (byte) 0xCC, (byte) 0xDD, // Entry 1 Address
            (byte) 0x08, // Entry 1 Round Index
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
            // WiFi Specific Header: ID, Length (16), OUI
            (byte) 0xDD, (byte) 0x10, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // Sub-Element 0x05 (UWB Config): Type 5, Length 12
            (byte) 0x5C,
            (byte) 0x02, (byte) 0x00, // Profile ID, UWB Config ID
            // SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22, // DEVICE_MAC_ADDRESS
        };

        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        assertThat(params.getSessionId()).isEqualTo(0x01234567);
        assertThat(params.getDeviceAddress())
                .isEqualTo(UwbAddress.fromBytes(new byte[] {0x11, 0x22}));
    }

    @Test
    public void testCreateFromFiraConfigPacket_bothSubElements() {
        byte[] config = {
            // WiFi Specific Header: ID, Length (33), OUI
            (byte) 0xDD, (byte) 0x21, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // Sub-Element 0x05 (UWB Config): Type 5, Length 12
            (byte) 0x5C,
            (byte) 0x02, (byte) 0x00, // Profile ID, UWB Config ID
            // SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            (byte) 0x06, (byte) 0x02, (byte) 0x11, (byte) 0x22, // DEVICE_MAC_ADDRESS
            // Sub-Element 0x0B (WiFi Time Sync): Type 0xB, Length 15 (with extension 0)
            (byte) 0xBF, (byte) 0x00,
            (byte) 0x01, // Profile ID
            (byte) 0x03, // Mode 0x0 (Short), Control 0x03 (b0=1, b1=1)
            (byte) 0xAA, (byte) 0xBB, // DT-Anchor Address
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, // Time Offset
            (byte) 0x00, (byte) 0x00, // Time Offset Uncertainty
            (byte) 0x07, // Round Index (b0)
            (byte) 0x01, // Initiator DT-Anchor Count
            (byte) 0xCC, (byte) 0xDD, // Entry 1 Address
            (byte) 0x08, // Entry 1 Round Index
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
            // WiFi Specific Header
            (byte) 0xDD, (byte) 0x0C, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x58,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
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
            // WiFi Specific Header
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x5B,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // Tag-Length-Value for DL_TDOA_MEASUREMENT_NTF_V2
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
            // WiFi Specific Header
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x5B,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // Tag-Length-Value for DL_TDOA_MEASUREMENT_NTF_V2
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
            // WiFi Specific Header
            (byte) 0xDD, (byte) 0x0F, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x5B,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
            // Tag-Length-Value for DL_TDOA_MEASUREMENT_NTF_V2
            (byte) 0x4F, (byte) 0x01, (byte) 0x02,
        };

        assertThrows(IllegalArgumentException.class,
                () -> DlTdoaRangingParams.createFromFiraConfigPacket(config, null));
    }
}
