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

package com.android.server.ranging.tests.fusion;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.ranging.RangingData;
import android.ranging.RangingManager;
import android.ranging.RangingMeasurement;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.uwb.fusion.UwbFilterEngine;
import com.android.uwb.fusion.math.SphericalVector;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RunWith(JUnit4.class)
@SmallTest
public class FilteringFusionEngineTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingInjector mMockInjector;
    private @Mock FusionEngine.DataFuser mMockDataFuser;
    private @Mock FusionEngine.Callback mMockCallback;

    private FilteringFusionEngine mSpyEngine;

    private Map<RangingTechnology, UwbFilterEngine> mockFiltersForTechnologies(
            Set<RangingTechnology> technologies
    ) {
        Map<RangingTechnology, UwbFilterEngine> mockFilters = new HashMap<>();
        for (RangingTechnology technology : technologies) {
            UwbFilterEngine mockEngine = mock(UwbFilterEngine.class, RETURNS_DEEP_STUBS);
            mockFilters.put(technology, mockEngine);
            when(mSpyEngine.createFilter(eq(technology))).thenReturn(mockEngine);
        }
        return mockFilters;
    }

    @Before
    public void setup() {
        mSpyEngine = spy(new FilteringFusionEngine(mMockDataFuser, true, mMockInjector));
    }

    @Test
    public void addDataSource_createsFilterForTechnology() {
        mockFiltersForTechnologies(Set.of(RangingTechnology.UWB));
        mSpyEngine.addDataSource(RangingTechnology.UWB);

        verify(mSpyEngine).createFilter(eq(RangingTechnology.UWB));
    }

    @Test
    public void removeDataSource_closesFilterForTechnology() {
        Map<RangingTechnology, UwbFilterEngine> filters =
                mockFiltersForTechnologies(Set.of(RangingTechnology.UWB));

        mSpyEngine.addDataSource(RangingTechnology.UWB);
        mSpyEngine.removeDataSource(RangingTechnology.UWB);

        verify(filters.get(RangingTechnology.UWB)).close();
    }

    @Test
    public void getDataSources_containsExactlyTechnologiesThatWereAdded() {
        Set<RangingTechnology> technologies = Set.of(
                RangingTechnology.UWB,
                RangingTechnology.CS,
                RangingTechnology.RTT);

        mockFiltersForTechnologies(technologies);

        for (RangingTechnology technology : technologies) {
            mSpyEngine.addDataSource(technology);
        }

        assertThat(mSpyEngine.getDataSources()).containsExactlyElementsIn(technologies);
    }

    @Test
    public void feed_filtersDataAndComputesNewPosition() {
        Map<RangingTechnology, UwbFilterEngine> mockFilters =
                mockFiltersForTechnologies(Set.of(RangingTechnology.UWB));

        RangingData data = new RangingData.Builder()
                .setRangingTechnology(RangingManager.UWB)
                .setDistance(new RangingMeasurement.Builder().setMeasurement(1.2).build())
                .setAzimuth(new RangingMeasurement.Builder().setMeasurement(2.3).build())
                .setElevation(new RangingMeasurement.Builder().setMeasurement(3.4).build())
                .setRssi(1)
                .setTimestampMillis(1234)
                .build();

        mSpyEngine.addDataSource(RangingTechnology.UWB);
        mSpyEngine.feed(data);

        verify(mockFilters.get(RangingTechnology.UWB))
                .add(any(SphericalVector.Annotated.class), eq(data.getTimestampMillis()));
        verify(mockFilters.get(RangingTechnology.UWB))
                .compute(eq(data.getTimestampMillis()));
    }

    @Test
    public void feed_fusesDataFromMultipleTechnologies() {
        Set<RangingTechnology> technologies = Set.of(RangingTechnology.UWB, RangingTechnology.CS);
        mockFiltersForTechnologies(technologies);

        for (RangingTechnology technology : technologies) {
            mSpyEngine.addDataSource(technology);
        }

        mSpyEngine.start(mMockCallback);

        // Feed some UWB data
        RangingData mockFusedUwbData = mock(RangingData.class);
        when(mMockDataFuser.fuse(any(), any())).thenReturn(Optional.of(mockFusedUwbData));

        RangingData inputUwbData = new RangingData.Builder()
                .setRangingTechnology(RangingManager.UWB)
                .setDistance(new RangingMeasurement.Builder().setMeasurement(1.2).build())
                .setAzimuth(new RangingMeasurement.Builder().setMeasurement(2.3).build())
                .setElevation(new RangingMeasurement.Builder().setMeasurement(3.4).build())
                .setRssi(1)
                .setTimestampMillis(1234)
                .build();
        mSpyEngine.feed(inputUwbData);

        verify(mMockDataFuser).fuse(
                argThat(data -> data.getRangingTechnology() == RangingTechnology.UWB.getValue()
                        && data.getTimestampMillis() == inputUwbData.getTimestampMillis()),
                eq(technologies));
        verify(mMockCallback).onData(eq(mockFusedUwbData));

        // Feed some CS data
        RangingData mockFusedCsData = mock(RangingData.class);
        when(mMockDataFuser.fuse(any(), any())).thenReturn(Optional.of(mockFusedCsData));

        RangingData inputCsData = new RangingData.Builder()
                .setRangingTechnology(RangingManager.BLE_CS)
                .setDistance(new RangingMeasurement.Builder().setMeasurement(4.5).build())
                .setRssi(2)
                .setTimestampMillis(2345)
                .build();
        mSpyEngine.feed(inputCsData);

        verify(mMockDataFuser).fuse(
                argThat(data -> data.getRangingTechnology() == RangingTechnology.CS.getValue()
                        && data.getTimestampMillis() == inputCsData.getTimestampMillis()),
                eq(technologies));
        verify(mMockCallback).onData(eq(mockFusedCsData));
    }
}
