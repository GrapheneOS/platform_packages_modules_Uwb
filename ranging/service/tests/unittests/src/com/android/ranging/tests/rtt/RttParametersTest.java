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

package com.android.server.ranging.tests.rtt;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.rtt.RttParameters;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SmallTest
public class RttParametersTest {

    @Test
    public void testRttParameters() {
        byte serviceID = 1;
        String serviceName = "test_rtt";
        int maxDistance = 10000;
        int minDistance = 1000;
        byte[] matchFilter = new byte[]{1};
        RttParameters rttParameters = new RttParameters.Builder()
                .setServiceId(serviceID)
                .setServiceName(serviceName)
                .setMaxDistanceMm(maxDistance)
                .setMinDistanceMm(minDistance)
                .setMatchFilter(matchFilter)
                .build();

        assertEquals(rttParameters.getServiceId(), serviceID);
        assertEquals(rttParameters.getServiceName(), serviceName);
        assertEquals(rttParameters.getMaxDistanceMm(), maxDistance);
        assertEquals(rttParameters.getMinDistanceMm(), minDistance);
        assertEquals(rttParameters.getMatchFilter(), matchFilter);
        assertThat(rttParameters.toString()).isNotNull();
    }
}
