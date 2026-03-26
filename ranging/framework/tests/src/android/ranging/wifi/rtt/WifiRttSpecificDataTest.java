/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.ranging.wifi.rtt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class WifiRttSpecificDataTest {

    @Test
    public void testBuilderAndGetters() {
        byte[] lci = new byte[]{0x01, 0x02};
        WifiRttSpecificData data = new WifiRttSpecificData.Builder()
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(5)
                .setMeasurementBandwidth(1)
                .setMeasurementChannelFrequencyMHz(2412)
                .setLci(lci)
                .setDistanceStandardDeviationMeters(0.5)
                .setNtbMinMeasurementTimeMicros(100L)
                .setNtbMaxMeasurementTimeMicros(200L)
                .setI2rTxLtfRepetitions(2)
                .setR2iTxLtfRepetitions(3)
                .setNumTxSpatialStreams(4)
                .setNumRxSpatialStreams(5)
                .build();

        assertEquals(10, data.getNumAttemptedMeasurements());
        assertEquals(5, data.getNumSuccessfulMeasurements());
        assertEquals(1, data.getMeasurementBandwidth());
        assertEquals(2412, data.getMeasurementChannelFrequencyMHz());
        assertArrayEquals(lci, data.getLci());
        assertEquals(0.5, data.getDistanceStandardDeviationMeters(), 0.0001);
        assertEquals(100L, data.getNtbMinMeasurementTimeMicros());
        assertEquals(200L, data.getNtbMaxMeasurementTimeMicros());
        assertEquals(2, data.getI2rTxLtfRepetitions());
        assertEquals(3, data.getR2iTxLtfRepetitions());
        assertEquals(4, data.getNumTxSpatialStreams());
        assertEquals(5, data.getNumRxSpatialStreams());
    }

    @Test
    public void testParcelability() {
        byte[] lci = new byte[]{0x01, 0x02};
        WifiRttSpecificData data = new WifiRttSpecificData.Builder()
                .setNumAttemptedMeasurements(10)
                .setNumSuccessfulMeasurements(5)
                .setMeasurementBandwidth(1)
                .setMeasurementChannelFrequencyMHz(2412)
                .setLci(lci)
                .setDistanceStandardDeviationMeters(0.5)
                .setNtbMinMeasurementTimeMicros(100L)
                .setNtbMaxMeasurementTimeMicros(200L)
                .setI2rTxLtfRepetitions(2)
                .setR2iTxLtfRepetitions(3)
                .setNumTxSpatialStreams(4)
                .setNumRxSpatialStreams(5)
                .build();

        Parcel parcel = Parcel.obtain();
        data.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        WifiRttSpecificData parcellatedData = WifiRttSpecificData.CREATOR.createFromParcel(parcel);

        assertEquals(data, parcellatedData);
        parcel.recycle();
    }
}
