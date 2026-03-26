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

package com.android.ranging.tests.rtt.backend;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.net.wifi.rtt.RangingResult;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.ranging.rtt.backend.RttRangingPosition;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RttRangingPositionTest {

    @Mock
    private RangingResult mMockRangingResult;

    private RttRangingPosition mRttRangingPosition;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRttRangingPosition() {
        when(mMockRangingResult.getDistanceMm()).thenReturn(10000);
        when(mMockRangingResult.getRangingTimestampMillis()).thenReturn(50_000L);
        when(mMockRangingResult.getRssi()).thenReturn(50);
        when(mMockRangingResult.getNumSuccessfulMeasurements()).thenReturn(5);
        when(mMockRangingResult.getNumAttemptedMeasurements()).thenReturn(10);
        when(mMockRangingResult.getMeasurementBandwidth()).thenReturn(1);
        when(mMockRangingResult.getMeasurementChannelFrequencyMHz()).thenReturn(2412);
        when(mMockRangingResult.getLci()).thenReturn(new byte[]{1, 2});
        when(mMockRangingResult.getMinTimeBetweenNtbMeasurementsMicros()).thenReturn(100L);
        when(mMockRangingResult.getMaxTimeBetweenNtbMeasurementsMicros()).thenReturn(200L);
        when(mMockRangingResult.get80211azInitiatorTxLtfRepetitionsCount()).thenReturn(2);
        when(mMockRangingResult.get80211azResponderTxLtfRepetitionsCount()).thenReturn(3);
        when(mMockRangingResult.get80211azNumberOfTxSpatialStreams()).thenReturn(4);
        when(mMockRangingResult.get80211azNumberOfRxSpatialStreams()).thenReturn(5);

        mRttRangingPosition = new RttRangingPosition(mMockRangingResult);

        assertEquals(mRttRangingPosition.getDistanceMeters(), 10, 0);
        assertEquals(mRttRangingPosition.getRssiDbm(), 50);
        assertEquals(mRttRangingPosition.getRangingTimestampMillis(), 50_000L);
        assertThat(mRttRangingPosition.getAzimuth()).isNull();
        assertThat(mRttRangingPosition.getElevation()).isNull();
        assertEquals(mRttRangingPosition.getNumSuccessfulMeasurements(), 5);
        assertEquals(mRttRangingPosition.getNumAttemptedMeasurements(), 10);
        assertEquals(mRttRangingPosition.getMeasurementBandwidth(), 1);
        assertEquals(mRttRangingPosition.getMeasurementChannelFrequencyMHz(), 2412);
        assertThat(mRttRangingPosition.getLci()).isEqualTo(new byte[]{1, 2});
        assertEquals(mRttRangingPosition.getNtbMinMeasurementTime(), 100L);
        assertEquals(mRttRangingPosition.getNtbMaxMeasurementTime(), 200L);
        assertEquals(mRttRangingPosition.getI2rTxLtfRepetitions(), 2);
        assertEquals(mRttRangingPosition.getR2iTxLtfRepetitions(), 3);
        assertEquals(mRttRangingPosition.getNumTxSpatialStreams(), 4);
        assertEquals(mRttRangingPosition.getNumRxSpatialStreams(), 5);
    }
}
