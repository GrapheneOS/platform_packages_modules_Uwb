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

package android.ranging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RangingDataTest {

    @Test
    public void build_buildsDataWithAllFieldsSet() {
        int rangingTechnology = RangingManager.UWB;
        RangingMeasurement distance = new RangingMeasurement.Builder()
                .setMeasurement(10.5)
                .setConfidence(RangingMeasurement.CONFIDENCE_HIGH)
                .setRawConfidence(0.8)
                .setError(0.1)
                .build();
        RangingMeasurement azimuth = new RangingMeasurement.Builder()
                .setMeasurement(45.0)
                .setConfidence(RangingMeasurement.CONFIDENCE_MEDIUM)
                .setRawConfidence(0.4)
                .setError(0.2)
                .build();
        RangingMeasurement elevation = new RangingMeasurement.Builder()
                .setMeasurement(30.0)
                .setConfidence(RangingMeasurement.CONFIDENCE_LOW)
                .setRawConfidence(0.09)
                .setError(0.3)
                .build();

        int rssi = -2;
        long timestamp = System.currentTimeMillis();
        double delaySpreadMeters = 2.0;
        byte detectedAttackLevel = 0x01;
        double velocityMetersPerSec = 1.5;

        RangingData rangingData = new RangingData.Builder()
                .setRangingTechnology(rangingTechnology)
                .setDistance(distance)
                .setAzimuth(azimuth)
                .setElevation(elevation)
                .setRssi(rssi)
                .setTimestampMillis(timestamp)
                .setDelaySpreadMeters(delaySpreadMeters)
                .setDetectedAttackLevel(detectedAttackLevel)
                .setVelocityMetersPerSec(velocityMetersPerSec)
                .build();

        assertEquals(rangingTechnology, rangingData.getRangingTechnology());
        assertEquals(distance, rangingData.getDistance());
        assertEquals(azimuth, rangingData.getAzimuth());
        assertEquals(elevation, rangingData.getElevation());
        assertEquals(rssi, rangingData.getRssi());
        assertEquals(timestamp, rangingData.getTimestampMillis());
        assertEquals(delaySpreadMeters, rangingData.getDelaySpreadMeters(), 0.001);
        assertEquals(detectedAttackLevel, rangingData.getDetectedAttackLevel());
        assertEquals(velocityMetersPerSec, rangingData.getVelocityMetersPerSec(), 0.001);

        assertTrue(rangingData.hasRssi());
        assertTrue(rangingData.hasDelaySpread());
        assertTrue(rangingData.hasDetectedAttackLevel());
        assertTrue(rangingData.hasVelocity());

        assertEquals(distance.getMeasurement(), rangingData.getDistance().getMeasurement(), 0.001);
        assertEquals(distance.getConfidence(), rangingData.getDistance().getConfidence());
        assertEquals(distance.getRawConfidence(), rangingData.getDistance().getRawConfidence(),
                0.001);
        assertEquals(distance.getError(), rangingData.getDistance().getError(), 0.001);

        assertEquals(azimuth.getMeasurement(), rangingData.getAzimuth().getMeasurement(), 0.001);
        assertEquals(azimuth.getConfidence(), rangingData.getAzimuth().getConfidence());
        assertEquals(azimuth.getRawConfidence(), rangingData.getAzimuth().getRawConfidence(),
                0.001);
        assertEquals(azimuth.getError(), rangingData.getAzimuth().getError(), 0.001);

        assertEquals(elevation.getMeasurement(), rangingData.getElevation().getMeasurement(),
                0.001);
        assertEquals(elevation.getConfidence(), rangingData.getElevation().getConfidence());
        assertEquals(elevation.getRawConfidence(),
                rangingData.getElevation().getRawConfidence(), 0.001);
        assertEquals(elevation.getError(), rangingData.getElevation().getError(), 0.001);

    }

    @Test
    public void build_buildsRangingDataWithMinimalFieldsSet() {
        int rangingTechnology = RangingManager.UWB;
        RangingMeasurement distance = new RangingMeasurement.Builder()
                .setMeasurement(10.5)
                .build();

        long timestamp = System.currentTimeMillis();

        RangingData rangingData = new RangingData.Builder()
                .setRangingTechnology(rangingTechnology)
                .setDistance(distance)
                .setTimestampMillis(timestamp)
                .build();

        assertEquals(rangingTechnology, rangingData.getRangingTechnology());
        assertEquals(distance, rangingData.getDistance());
        assertEquals(timestamp, rangingData.getTimestampMillis());

        assertNull(rangingData.getAzimuth());
        assertNull(rangingData.getElevation());
        assertFalse(rangingData.hasRssi());
        assertFalse(rangingData.hasDelaySpread());
        assertFalse(rangingData.hasDetectedAttackLevel());
        assertFalse(rangingData.hasVelocity());

        assertEquals(distance.getConfidence(), rangingData.getDistance().getConfidence());
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_failsWhenDistanceIsMissing() {
        new RangingData.Builder()
                .setRangingTechnology(RangingManager.UWB)
                .setTimestampMillis(System.currentTimeMillis())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_failsWhenTimestampIsMissing() {
        new RangingData.Builder()
                .setDistance(new RangingMeasurement.Builder().setMeasurement(10.5).build())
                .setRangingTechnology(RangingManager.UWB)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_failsWhenRangingTechnologyIsMissing() {
        new RangingData.Builder()
                .setDistance(new RangingMeasurement.Builder().setMeasurement(10.5).build())
                .setTimestampMillis(System.currentTimeMillis())
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_RangingMeasurement_failsWhenMeasurementIsMissing() {
        new RangingMeasurement.Builder().build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_RangingMeasurement_failsWhenErrorIsNegative() {
        new RangingMeasurement.Builder().setMeasurement(10).setError(-1).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_RangingMeasurement_failsWhenConfidenceLevelIsGreaterThan1() {
        new RangingMeasurement.Builder().setMeasurement(1).setRawConfidence(11).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_RangingMeasurement_failsWhenConfidenceLevelIsLessThan0() {
        new RangingMeasurement.Builder().setMeasurement(1).setRawConfidence(-1).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void build_failsWhenDelaySpreadIsNegative() {
        new RangingData.Builder()
                .setDistance(new RangingMeasurement.Builder().setMeasurement(10).build())
                .setTimestampMillis(1)
                .setRangingTechnology(RangingManager.UWB)
                .setDelaySpreadMeters(-1)
                .build();
    }
}
