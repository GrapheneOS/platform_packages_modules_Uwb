/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.server.ranging.uwb;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UwbAnchorLocationTest {

    @Test
    public void testFromBytesV1_Wgs84_FiRaExample() {
        // Raw Data: [0xB1, 0xB4, 0xD2, 0x32, 0x2E, 0xE1, 0x75, 0x86, 0x1F, 0x4F, 0xC0, 0xC0]
        // FiRa Shifting rules: Lat: 50.8231, Long: 12.9209, Alt: 0.1015 km
        byte[] rawData = new byte[] {
            (byte) 0xB1, (byte) 0xB4, (byte) 0xD2, (byte) 0x32,
            (byte) 0x2E, (byte) 0xE1, (byte) 0x75, (byte) 0x86,
            (byte) 0x1F, (byte) 0x4F, (byte) 0xC0, (byte) 0xC0
        };

        // [MessageControl] b6-b5: 0b01 (WGS84) -> 0x20
        int messageControl = 0b00100000;

        UwbAnchorLocation location = UwbAnchorLocation.fromBytesV1(messageControl, rawData);

        assertThat(location.getCoordinateType()).isEqualTo(UwbAnchorLocation.COORDINATE_WGS84);
        assertThat(location.getWgs84Location()).isNotNull();

        double tolerance = 0.0001;
        assertThat(location.getWgs84Location().getLatitude()).isWithin(tolerance).of(50.8231);
        assertThat(location.getWgs84Location().getLongitude()).isWithin(tolerance).of(12.9209);
        assertThat(location.getWgs84Location().getAltitude()).isWithin(tolerance).of(0.1015);
    }

    @Test
    public void testFromBytesV1_Relative_FiRaExample() {
        // Raw Data: [108, -28, -1, -12, 96, 0, 0, -74, -2, -1]
        // True Coordinate (m): (-7.06, 0.07, -0.33) -> (-7060, 70, -330) mm
        byte[] rawData = new byte[] {108, -28, -1, -12, 96, 0, 0, -74, -2, -1};

        // [MessageControl] b6-b5: 0b10 (Relative) -> 0x40
        int messageControl = 0b01000000;

        UwbAnchorLocation location = UwbAnchorLocation.fromBytesV1(messageControl, rawData);

        assertThat(location.getCoordinateType()).isEqualTo(UwbAnchorLocation.COORDINATE_RELATIVE);
        assertThat(location.getRelativeLocation()).isNotNull();

        assertThat(location.getRelativeLocation().getX()).isEqualTo(-7060);
        assertThat(location.getRelativeLocation().getY()).isEqualTo(70);
        assertThat(location.getRelativeLocation().getZ()).isEqualTo(-330);
    }

    @Test
    public void testFromBytesV1_Relative_FiRaExample2() {
        // Raw Data: [-62, -4, -1, -7, 33, 48, 0, 102, -2, -1]
        // True Coordinate (m): (-0.83, 5.01, -0.41) -> (-830, 5010, -410) mm
        byte[] rawData = new byte[] {-62, -4, -1, -7, 33, 48, 0, 102, -2, -1};

        // [MessageControl] b6-b5: 0b10 (Relative) -> 0x40
        int messageControl = 0b01000000;

        UwbAnchorLocation location = UwbAnchorLocation.fromBytesV1(messageControl, rawData);

        assertThat(location.getCoordinateType()).isEqualTo(UwbAnchorLocation.COORDINATE_RELATIVE);
        assertThat(location.getRelativeLocation()).isNotNull();

        assertThat(location.getRelativeLocation().getX()).isEqualTo(-830);
        assertThat(location.getRelativeLocation().getY()).isEqualTo(5010);
        assertThat(location.getRelativeLocation().getZ()).isEqualTo(-410);
    }

    @Test
    public void testFromBytesV1_Relative_FiRaExample3() {
        // Raw Data: [0, 0, 0, 8, -127, 48, 0, 0, 0, 0]
        // Calculated: X: 0, Y: 5000, Z: 0
        byte[] rawData = new byte[] {0, 0, 0, 8, -127, 48, 0, 0, 0, 0};

        // [MessageControl] b6-b5: 0b10 (Relative) -> 0x40
        int messageControl = 0b01000000;

        UwbAnchorLocation location = UwbAnchorLocation.fromBytesV1(messageControl, rawData);

        assertThat(location.getCoordinateType()).isEqualTo(UwbAnchorLocation.COORDINATE_RELATIVE);
        assertThat(location.getRelativeLocation()).isNotNull();

        assertThat(location.getRelativeLocation().getX()).isEqualTo(0);
        assertThat(location.getRelativeLocation().getY()).isEqualTo(5000);
        assertThat(location.getRelativeLocation().getZ()).isEqualTo(0);
    }

    @Test
    public void testFromBytesV1_Wgs84_FiRaExample2() {
        // Raw Data: 0x22, 0xB5, 0xD2, 0x32, 0x5D, 0x62, 0x75, 0x86, 0x3C, 0x90, 0x40, 0xC0
        // Expected results based on shifting rules: Lat: ~50.8231, Long: ~12.9209, Alt: 0.1018 km
        byte[] rawData = new byte[] {
            (byte) 0x22, (byte) 0xB5, (byte) 0xD2, (byte) 0x32,
            (byte) 0x5D, (byte) 0x62, (byte) 0x75, (byte) 0x86,
            (byte) 0x3C, (byte) 0x90, (byte) 0x40, (byte) 0xC0
        };

        // [MessageControl] b6-b5: 0b01 (WGS84) -> 0x20
        int messageControl = 0b00100000;

        UwbAnchorLocation location = UwbAnchorLocation.fromBytesV1(messageControl, rawData);

        assertThat(location.getCoordinateType()).isEqualTo(UwbAnchorLocation.COORDINATE_WGS84);
        assertThat(location.getWgs84Location()).isNotNull();

        double tolerance = 0.0001;
        assertThat(location.getWgs84Location().getLatitude()).isWithin(tolerance).of(50.8231);
        assertThat(location.getWgs84Location().getLongitude()).isWithin(tolerance).of(12.9209);
        assertThat(location.getWgs84Location().getAltitude()).isWithin(tolerance).of(0.1018);
    }
}
