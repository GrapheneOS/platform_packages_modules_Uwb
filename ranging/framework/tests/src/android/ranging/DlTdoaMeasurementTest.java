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

package android.ranging;

import static com.google.common.truth.Truth.assertThat;

import android.ranging.DlTdoaMeasurement;
import android.ranging.DlTdoaMeasurement.AnchorLocation;
import android.ranging.DlTdoaMeasurement.RelativeLocation;
import android.ranging.DlTdoaMeasurement.Wgs84Location;
import android.ranging.DlTdoaMeasurement.ZElementExtension;
import android.ranging.uwb.DlTdoaRangingParams;
import android.ranging.uwb.UwbSpecificData;
import android.util.Range;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DlTdoaMeasurementTest {
    @Test
    public void testWgs84Location_getters() {
        Wgs84Location wgs84Location = new Wgs84Location(45.0, 90.0, 128.0);
        assertThat(wgs84Location.getLatitude()).isEqualTo(45.0);
        assertThat(wgs84Location.getLongitude()).isEqualTo(90.0);
        assertThat(wgs84Location.getAltitude()).isEqualTo(128.0);
    }

    @Test
    public void testRelativeLocation_getters() {
        RelativeLocation relativeLocation = new RelativeLocation(1000, 2000, 3000);
        assertThat(relativeLocation.getX()).isEqualTo(1000);
        assertThat(relativeLocation.getY()).isEqualTo(2000);
        assertThat(relativeLocation.getZ()).isEqualTo(3000);
    }

    @Test
    public void testZElementExtension_getters() {
        ZElementExtension zElementExtension = new ZElementExtension(
                256.0, ZElementExtension.MOVEMENT_EXPECTATION_MOVABLE, 1024.0, 12,
                false, false, 1024.0 - 0.5, 1024.0 + 0.5);
        assertThat(zElementExtension.getAnchorFloorNumber()).isEqualTo(256.0);
        assertThat(zElementExtension.isAnchorFloorNumberOutOfRange()).isFalse();
        assertThat(zElementExtension.getExpectedToMove()).isEqualTo(
                ZElementExtension.MOVEMENT_EXPECTATION_MOVABLE);
        assertThat(zElementExtension.getAnchorHeightAboveFloor()).isEqualTo(1024.0);
        assertThat(zElementExtension.isAnchorHeightAboveFloorOutOfRange()).isFalse();
        assertThat(zElementExtension.getAnchorHeightAboveFloorUncertainty()).isEqualTo(12);
        assertThat(zElementExtension.getAnchorHeightAboveFloorRange())
                .isEqualTo(new Range<Double>(1024.0 - 0.5, 1024.0 + 0.5));
    }

    @Test
    public void testAnchorLocation_Wgs84() {
        Wgs84Location wgs84Location = new Wgs84Location(45.0, 90.0, 100.0);
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_WGS84, new byte[12], wgs84Location, null, null);
        assertThat(anchorLocation.getCoordinateType()).isEqualTo(AnchorLocation.COORDINATE_WGS84);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[12]);
        assertThat(anchorLocation.getWgs84Location()).isEqualTo(wgs84Location);
        assertThat(anchorLocation.getRelativeLocation()).isNull();
        assertThat(anchorLocation.getZElementExtension()).isNull();
    }

    @Test
    public void testAnchorLocation_Relative() {
        RelativeLocation relativeLocation = new RelativeLocation(1000, 2000, 3000);
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_RELATIVE, new byte[10], null, relativeLocation, null);
        assertThat(anchorLocation.getCoordinateType())
                .isEqualTo(AnchorLocation.COORDINATE_RELATIVE);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[10]);
        assertThat(anchorLocation.getWgs84Location()).isNull();
        assertThat(anchorLocation.getRelativeLocation()).isEqualTo(relativeLocation);
        assertThat(anchorLocation.getZElementExtension()).isNull();
    }

    @Test
    public void testAnchorLocation_Wgs84_ZElement() {
        Wgs84Location wgs84Location = new Wgs84Location(45.0, 90.0, 100.0);
        ZElementExtension zElementExtension = new ZElementExtension(
                0.0, ZElementExtension.MOVEMENT_EXPECTATION_STATIONARY, 0.0, 0,
                false, false, Double.NaN, Double.NaN);
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_WGS84_PLUS_Z_ELEMENT, new byte[18],
                wgs84Location, null, zElementExtension);
        assertThat(anchorLocation.getCoordinateType())
                .isEqualTo(AnchorLocation.COORDINATE_WGS84_PLUS_Z_ELEMENT);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[18]);
        assertThat(anchorLocation.getWgs84Location()).isEqualTo(wgs84Location);
        assertThat(anchorLocation.getRelativeLocation()).isNull();
        assertThat(anchorLocation.getZElementExtension()).isEqualTo(zElementExtension);
    }

    @Test
    public void testAnchorLocation_Relative_ZElement() {
        RelativeLocation relativeLocation = new RelativeLocation(1000, 2000, 3000);
        ZElementExtension zElementExtension = new ZElementExtension(
                0.0, ZElementExtension.MOVEMENT_EXPECTATION_STATIONARY, 0.0, 0,
                false, false, Double.NaN, Double.NaN);
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_RELATIVE_PLUS_Z_ELEMENT, new byte[16],
                null, relativeLocation, zElementExtension);
        assertThat(anchorLocation.getCoordinateType())
                .isEqualTo(AnchorLocation.COORDINATE_RELATIVE_PLUS_Z_ELEMENT);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[16]);
        assertThat(anchorLocation.getWgs84Location()).isNull();
        assertThat(anchorLocation.getRelativeLocation()).isEqualTo(relativeLocation);
        assertThat(anchorLocation.getZElementExtension()).isEqualTo(zElementExtension);
    }

    @Test
    public void testAnchorLocation_Relative_GravityAligned() {
        RelativeLocation relativeLocation = new RelativeLocation(1000, 2000, 3000);
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED, new byte[10],
                null, relativeLocation, null);
        assertThat(anchorLocation.getCoordinateType())
                .isEqualTo(AnchorLocation.COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[10]);
        assertThat(anchorLocation.getWgs84Location()).isNull();
        assertThat(anchorLocation.getRelativeLocation()).isEqualTo(relativeLocation);
        assertThat(anchorLocation.getZElementExtension()).isNull();
    }

    @Test
    public void testAnchorLocation_Relative_GravityAligned_ZElement() {
        RelativeLocation relativeLocation = new RelativeLocation(1000, 2000, 3000);
        ZElementExtension zElementExtension = new ZElementExtension(
                0.0, ZElementExtension.MOVEMENT_EXPECTATION_STATIONARY, 0.0, 0,
                false, false, Double.NaN, Double.NaN);
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED_PLUS_Z_ELEMENT,
                new byte[16], null, relativeLocation, zElementExtension);
        assertThat(anchorLocation.getCoordinateType())
                .isEqualTo(AnchorLocation
                        .COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED_PLUS_Z_ELEMENT);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[16]);
        assertThat(anchorLocation.getWgs84Location()).isNull();
        assertThat(anchorLocation.getRelativeLocation()).isEqualTo(relativeLocation);
        assertThat(anchorLocation.getZElementExtension()).isEqualTo(zElementExtension);
    }

    @Test
    public void testAnchorLocation_Unknown() {
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_UNKNOWN, new byte[] {(byte) 0xFF}, null, null, null);
        assertThat(anchorLocation.getCoordinateType())
                .isEqualTo(AnchorLocation.COORDINATE_UNKNOWN);
        assertThat(anchorLocation.getRawBytes()).isEqualTo(new byte[] {(byte) 0xFF});
        assertThat(anchorLocation.getWgs84Location()).isNull();
        assertThat(anchorLocation.getRelativeLocation()).isNull();
        assertThat(anchorLocation.getZElementExtension()).isNull();
    }

    @Test
    public void testDlTdoaMeasurement_getters() {
        AnchorLocation anchorLocation = new AnchorLocation(
                AnchorLocation.COORDINATE_UNKNOWN, new byte[] {(byte) 0xFF}, null, null, null);
        DlTdoaMeasurement measurement = new DlTdoaMeasurement.Builder(
                DlTdoaRangingParams.MEASUREMENT_VERSION_2, DlTdoaMeasurement.MESSAGE_TYPE_POLL_DTM,
                0b0101, 3, 5, UwbSpecificData.NLOS,
                new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08},
                new byte[] {0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18},
                1.1f, 2.2f, 0x01234567L, 0x23456789L, 0x1234)
                .setAoaAzimuth(90.0f)
                .setAoaAzimuthFom(33)
                .setAoaElevation(45.0f)
                .setAoaElevationFom(66)
                .setRssi(-123)
                .setActiveRangingRoundIndexes(new byte[]{1, 3, 5, 7})
                .setAnchorLocation(anchorLocation)
                .setSuperclusterId(123)
                .build();
        assertThat(measurement.getMeasurementVersion())
                .isEqualTo(DlTdoaRangingParams.MEASUREMENT_VERSION_2);
        assertThat(measurement.getMessageType())
                .isEqualTo(DlTdoaMeasurement.MESSAGE_TYPE_POLL_DTM);
        assertThat(measurement.getMessageControl()).isEqualTo(0b0101);
        assertThat(measurement.isTxTimestampInCommonTimeBase()).isTrue();
        assertThat(measurement.getBlockIndex()).isEqualTo(3);
        assertThat(measurement.getRoundIndex()).isEqualTo(5);
        assertThat(measurement.getNlos()).isEqualTo(UwbSpecificData.NLOS);
        assertThat(measurement.getAoaAzimuth()).isEqualTo(90.0f);
        assertThat(measurement.getAoaAzimuthFom()).isEqualTo(33);
        assertThat(measurement.getAoaElevation()).isEqualTo(45.0f);
        assertThat(measurement.getAoaElevationFom()).isEqualTo(66);
        assertThat(measurement.getRssi()).isEqualTo(-123);
        assertThat(measurement.getTxTimestamp())
                .isEqualTo(new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08});
        assertThat(measurement.getRxTimestamp())
                .isEqualTo(new byte[] {0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18});
        assertThat(measurement.getAnchorCfo()).isEqualTo(1.1f);
        assertThat(measurement.getCfo()).isEqualTo(2.2f);
        assertThat(measurement.getInitiatorReplyTime()).isEqualTo(0x01234567L);
        assertThat(measurement.getResponderReplyTime()).isEqualTo(0x23456789L);
        assertThat(measurement.getInitiatorResponderTof()).isEqualTo(0x1234);
        assertThat(measurement.getAnchorLocation().getCoordinateType())
                .isEqualTo(AnchorLocation.COORDINATE_UNKNOWN);
        assertThat(measurement.getAnchorLocation().getRawBytes())
                .isEqualTo(new byte[] {(byte) 0xFF});
        assertThat(measurement.getAnchorLocation().getWgs84Location()).isNull();
        assertThat(measurement.getAnchorLocation().getRelativeLocation()).isNull();
        assertThat(measurement.getAnchorLocation().getZElementExtension()).isNull();
        assertThat(measurement.getActiveRangingRoundIndexes()).isEqualTo(List.of(1, 3, 5, 7));
        assertThat(measurement.hasSuperclusterId()).isTrue();
        assertThat(measurement.getSuperclusterId()).isEqualTo(123);
    }
}
