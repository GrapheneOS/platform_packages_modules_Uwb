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

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.ranging.rtt.backend.RttRangingParameters;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RttRangingParametersTest {

    @Test
    public void testRttRangingParameters() {
        int deviceRole = RttRangingParameters.DeviceRole.PUBLISHER;
        byte serviceId = 0;
        String serviceName = "";
        byte[] matchFilter = new byte[]{};
        int maxDistanceMm = 30 * 100 * 100;
        int minDistanceMm = 0;
        int rangingUpdateRate = RttRangingParameters.NORMAL;
        boolean enablePeriodicRangingHwFeature = false;

        RttRangingParameters parameters = new RttRangingParameters.Builder()
                .setDeviceRole(deviceRole)
                .setServiceId(serviceId)
                .setServiceName(serviceName)
                .setMatchFilter(matchFilter)
                .setMaxDistanceMm(maxDistanceMm)
                .setMinDistanceMm(minDistanceMm)
                .setUpdateRate(rangingUpdateRate)
                .setPeriodicRangingHwFeatureEnabled(enablePeriodicRangingHwFeature)
                .build();

        assertEquals(parameters.getDeviceRole(), deviceRole);
        assertEquals(parameters.getServiceId(), serviceId);
        assertEquals(parameters.getServiceName(), serviceName);
        assertEquals(parameters.getMatchFilter(), matchFilter);
        assertEquals(parameters.getMaxDistanceMm(), maxDistanceMm);
        assertEquals(parameters.getMinDistanceMm(), minDistanceMm);
        assertEquals(parameters.getUpdateRate(), rangingUpdateRate);
        assertEquals(parameters.isPeriodicRangingHwFeatureEnabled(),
                enablePeriodicRangingHwFeature);
    }
}
