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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.ranging.RangingData;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.fusion.DataFusers;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.Set;

@RunWith(Enclosed.class)
public class DataFusersTest {
    @SmallTest
    public static class PassthroughDataFuserTest {
        private DataFusers.PassthroughDataFuser mFuser;

        @Before
        public void setup() {
            mFuser = new DataFusers.PassthroughDataFuser();
        }

        @Test
        public void fuse_reportsProvidedData() {
            RangingData mockData = mock(RangingData.class);
            Optional<RangingData> fused = mFuser.fuse(mockData, Set.of(RangingTechnology.UWB));
            assertThat(fused).isPresent();
            assertThat(fused.get()).isEqualTo(mockData);
        }
    }

    @SmallTest
    public static class PreferentialDataFuserTest {
        private DataFusers.PreferentialDataFuser mFuser;

        @Before
        public void setup() {
            mFuser = new DataFusers.PreferentialDataFuser(RangingTechnology.UWB);
        }

        @Test
        public void fuse_reportsDataFromPreferredTechnologyWhenPreferredActive() {
            RangingData mockData = mock(RangingData.class);
            when(mockData.getRangingTechnology()).thenReturn(RangingTechnology.UWB.getValue());
            Optional<RangingData> fused =
                    mFuser.fuse(mockData, Set.of(RangingTechnology.UWB, RangingTechnology.CS));
            assertThat(fused).isPresent();
            assertThat(fused.get()).isEqualTo(mockData);
        }

        @Test
        public void fuse_ignoresDataFromOtherTechnologiesWhenPreferredActive() {
            RangingData mockData = mock(RangingData.class);
            when(mockData.getRangingTechnology()).thenReturn(RangingTechnology.CS.getValue());
            Optional<RangingData> fused =
                    mFuser.fuse(mockData, Set.of(RangingTechnology.UWB, RangingTechnology.CS));
            assertThat(fused).isEmpty();
        }

        @Test
        public void fuse_reportsDataFromAnyTechnologyWhenPreferredInactive() {
            Set<RangingTechnology> active = Set.of(
                    RangingTechnology.CS, RangingTechnology.RTT, RangingTechnology.RSSI);

            RangingData mockCsData = mock(RangingData.class);
            when(mockCsData.getRangingTechnology()).thenReturn(RangingTechnology.CS.getValue());
            Optional<RangingData> fused1 = mFuser.fuse(mockCsData, active);
            assertThat(fused1).isPresent();
            assertThat(fused1.get()).isEqualTo(mockCsData);

            RangingData mockRttData = mock(RangingData.class);
            when(mockRttData.getRangingTechnology()).thenReturn(RangingTechnology.RTT.getValue());
            Optional<RangingData> fused2 = mFuser.fuse(mockRttData, active);
            assertThat(fused2).isPresent();
            assertThat(fused2.get()).isEqualTo(mockRttData);

            RangingData mockRssiData = mock(RangingData.class);
            when(mockRssiData.getRangingTechnology()).thenReturn(RangingTechnology.CS.getValue());
            Optional<RangingData> fused3 = mFuser.fuse(mockRssiData, active);
            assertThat(fused3).isPresent();
            assertThat(fused3.get()).isEqualTo(mockRssiData);
        }
    }
}
