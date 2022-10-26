/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.server.uwb;

import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.util.UwbUtil.convertFloatToQFormat;
import static com.android.server.uwb.util.UwbUtil.degreeToRadian;

import android.os.PersistableBundle;
import android.util.Pair;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.UwbAddress;

import com.android.server.uwb.data.UwbOwrAoaMeasurement;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.params.TlvUtil;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.oemextension.RangingReportMetadata;

public class UwbTestUtils {
    public static final int TEST_SESSION_ID = 7;
    public static final int TEST_SESSION_ID_2 = 8;
    public static final byte[] PEER_SHORT_MAC_ADDRESS = {0x1, 0x3};
    public static final byte[] PEER_EXTENDED_MAC_ADDRESS =
            new byte[] {0x12, 0x14, 0x16, 0x18, 0x31, 0x33, 0x35, 0x37};
    public static final byte[] PEER_EXTENDED_MAC_ADDRESS_REVERSE_BYTES =
            new byte[] {0x37, 0x35, 0x33, 0x31, 0x18, 0x16, 0x14, 0x12};
    public static final byte[] PEER_EXTENDED_MAC_ADDRESS_2 =
            new byte[] {0x2, 0x4, 0x6, 0x8, 0x1, 0x3, 0x5, 0x7};
    public static final byte[] PEER_BAD_MAC_ADDRESS = new byte[] {0x12, 0x14, 0x16, 0x18};
    public static final byte[] PEER_EXTENDED_SHORT_MAC_ADDRESS =
            new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x35, 0x37};
    public static final UwbAddress PEER_UWB_ADDRESS = UwbAddress.fromBytes(
            PEER_EXTENDED_MAC_ADDRESS_REVERSE_BYTES);
    public static final PersistableBundle PERSISTABLE_BUNDLE = new PersistableBundle();
    public static final byte[] DATA_PAYLOAD = new byte[] {0x13, 0x15, 0x18};

    private static final long TEST_SEQ_COUNTER = 5;
    private static final int TEST_RCR_INDICATION = 7;
    private static final long TEST_CURR_RANGING_INTERVAL = 100;
    private static final int TEST_RANGING_MEASURES_TYPE = RANGING_MEASUREMENT_TYPE_TWO_WAY;
    private static final int TEST_MAC_ADDRESS_MODE = 1;
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 0;
    private static final int TEST_DISTANCE = 101;
    private static final float TEST_AOA_AZIMUTH = 67;
    private static final int TEST_AOA_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_ELEVATION = 37;
    private static final int TEST_AOA_ELEVATION_FOM = 90;
    private static final float TEST_AOA_DEST_AZIMUTH = 67;
    private static final int TEST_AOA_DEST_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_DEST_ELEVATION = 37;
    private static final int TEST_AOA_DEST_ELEVATION_FOM = 90;
    private static final int TEST_FRAME_SEQUENCE_NUMBER = 1;
    private static final int TEST_BLOCK_IDX = 100;
    private static final int TEST_SLOT_IDX = 10;
    private static final int TEST_RSSI = 150;

    private UwbTestUtils() {}

    /** Build UwbRangingData for all Ranging Measurement Type(s). */
    public static UwbRangingData generateRangingData(
            int rangingMeasurementType, int rangingStatus) {
        switch (rangingMeasurementType) {
            case RANGING_MEASUREMENT_TYPE_TWO_WAY:
                return generateTwoWayMeasurementRangingData(rangingStatus);
            case RANGING_MEASUREMENT_TYPE_OWR_AOA:
                return generateOwrAoaMeasurementRangingData(rangingStatus);
            default:
                return null;
        }
    }

    private static UwbRangingData generateTwoWayMeasurementRangingData(int rangingStatus) {
        final int noOfRangingMeasures = 1;
        final UwbTwoWayMeasurement[] uwbTwoWayMeasurements =
                new UwbTwoWayMeasurement[noOfRangingMeasures];
        uwbTwoWayMeasurements[0] = new UwbTwoWayMeasurement(PEER_SHORT_MAC_ADDRESS, rangingStatus,
                TEST_LOS, TEST_DISTANCE, convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7),
                TEST_AOA_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7),
                TEST_AOA_ELEVATION_FOM, convertFloatToQFormat(TEST_AOA_DEST_AZIMUTH, 9, 7),
                TEST_AOA_DEST_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_DEST_ELEVATION, 9, 7),
                TEST_AOA_DEST_ELEVATION_FOM, TEST_SLOT_IDX, TEST_RSSI);
        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_TWO_WAY,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbTwoWayMeasurements, new byte[0]);
    }

    private static UwbRangingData generateOwrAoaMeasurementRangingData(int rangingStatus) {
        final int noOfRangingMeasures = 1;
        final UwbOwrAoaMeasurement uwbOwrAoaMeasurement  = new UwbOwrAoaMeasurement(
                PEER_EXTENDED_MAC_ADDRESS, rangingStatus, TEST_LOS,
                TEST_FRAME_SEQUENCE_NUMBER, TEST_BLOCK_IDX,
                convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7), TEST_AOA_AZIMUTH_FOM,
                convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7), TEST_AOA_ELEVATION_FOM);
        return new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, RANGING_MEASUREMENT_TYPE_OWR_AOA,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbOwrAoaMeasurement);
    }

    // Helper method to generate a UwbRangingData instance and corresponding RangingMeasurement
    public static Pair<UwbRangingData, RangingReport> generateRangingDataAndRangingReport(
            byte[] macAddress, int rangingMeasurementType,
            boolean isAoaAzimuthEnabled, boolean isAoaElevationEnabled,
            boolean isDestAoaAzimuthEnabled, boolean isDestAoaElevationEnabled,
            long elapsedRealtimeNanos) {
        UwbRangingData uwbRangingData = generateRangingData(rangingMeasurementType, TEST_STATUS);

        // TODO(b/246678053): Add rawNtfData[] for both TwoWay and OWR AoA Measurements..
        PersistableBundle rangingReportMetadata = new RangingReportMetadata.Builder()
                .setSessionId(0)
                .build()
                .toBundle();

        AngleOfArrivalMeasurement aoaMeasurement = null;
        AngleOfArrivalMeasurement aoaDestMeasurement = null;
        if (isAoaAzimuthEnabled || isAoaElevationEnabled) {
            AngleMeasurement aoaAzimuth = null;
            AngleMeasurement aoaElevation = null;
            if (isAoaAzimuthEnabled) {
                aoaAzimuth =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_AZIMUTH), 0,
                                TEST_AOA_AZIMUTH_FOM / (double) 100);
            }
            if (isAoaElevationEnabled) {
                aoaElevation =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_ELEVATION), 0,
                                TEST_AOA_ELEVATION_FOM / (double) 100);
            }
            aoaMeasurement = new AngleOfArrivalMeasurement.Builder(aoaAzimuth)
                    .setAltitude(aoaElevation)
                    .build();
        }
        if (isDestAoaAzimuthEnabled || isDestAoaElevationEnabled) {
            AngleMeasurement aoaDestAzimuth = null;
            AngleMeasurement aoaDestElevation = null;
            if (isDestAoaAzimuthEnabled) {
                aoaDestAzimuth =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_DEST_AZIMUTH), 0,
                                TEST_AOA_DEST_AZIMUTH_FOM / (double) 100);
            }
            if (isDestAoaElevationEnabled) {
                aoaDestElevation =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_DEST_ELEVATION), 0,
                                TEST_AOA_DEST_ELEVATION_FOM / (double) 100);
            }
            aoaDestMeasurement = new AngleOfArrivalMeasurement.Builder(aoaDestAzimuth)
                    .setAltitude(aoaDestElevation)
                    .build();
        }

        RangingReport rangingReport = buildRangingReport(macAddress, rangingMeasurementType,
                aoaMeasurement, aoaDestMeasurement, elapsedRealtimeNanos, rangingReportMetadata);
        return Pair.create(uwbRangingData, rangingReport);
    }

    private static RangingReport buildRangingReport(byte[] macAddress, int rangingMeasurementType,
            AngleOfArrivalMeasurement aoaMeasurement, AngleOfArrivalMeasurement aoaDestMeasurement,
            long elapsedRealtimeNanos, PersistableBundle rangingReportMetadata) {

        PersistableBundle rangingMeasurementMetadata = new PersistableBundle();

        RangingMeasurement.Builder rangingMeasurementBuilder = new RangingMeasurement.Builder()
                .setRemoteDeviceAddress(UwbAddress.fromBytes(
                        TlvUtil.getReverseBytes(macAddress)))
                .setStatus(TEST_STATUS)
                .setElapsedRealtimeNanos(elapsedRealtimeNanos)
                .setAngleOfArrivalMeasurement(aoaMeasurement)
                .setLineOfSight(TEST_LOS);

        if (rangingMeasurementType == RANGING_MEASUREMENT_TYPE_TWO_WAY) {
            rangingMeasurementBuilder
                    .setDistanceMeasurement(
                            new DistanceMeasurement.Builder()
                                    .setMeters(TEST_DISTANCE / (double) 100)
                                    .setErrorMeters(0)
                                    .setConfidenceLevel(0)
                                    .build())
                    .setDestinationAngleOfArrivalMeasurement(aoaDestMeasurement)
                    .setRssiDbm(-TEST_RSSI / 2)
                    .setRangingMeasurementMetadata(rangingMeasurementMetadata);
        }

        return new RangingReport.Builder()
                .addMeasurement(rangingMeasurementBuilder.build())
                .addRangingReportMetadata(rangingReportMetadata)
                .build();
    }
}
