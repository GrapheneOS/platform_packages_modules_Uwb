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
import static org.mockito.Mockito.mock;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.ranging.rtt.backend.RttDevice;
import com.android.ranging.rtt.backend.RttRangingDevice;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RttDeviceTest {
    @Test
    public void testRttDevice() {
        RttRangingDevice mRttRangingDevice = mock(RttRangingDevice.class);
        RttDevice.RttAddress rttAddress = new RttDevice.RttAddress("AA:BB:CC:AA:BB:CC");
        assertThat(rttAddress.getAddress()).isNotNull();
        assertThat(rttAddress.toBytes()).isNotNull();

        RttDevice rttDevice = new RttDevice(mRttRangingDevice);
        assertThat(rttDevice.getAddress()).isNotNull();
        assertEquals(rttDevice.getRttRangingDevice(), mRttRangingDevice);
    }
}
