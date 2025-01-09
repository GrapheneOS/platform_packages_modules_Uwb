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

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.test.filters.SmallTest;

import com.android.ranging.rtt.backend.internal.RttRangingParameters;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.rtt.RttConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SmallTest
public class RttConfigTest {

    @Test
    public void testRttConfig() {
        int deviceRole = DEVICE_ROLE_INITIATOR;
        RttRangingParams rttRangingParams = new RttRangingParams.Builder("unit_test_rtt")
                .setMatchFilter(new byte[]{0, 1})
                .build();
        SessionConfig sessionConfig = new SessionConfig.Builder().build();
        RangingDevice peerDevice = new RangingDevice.Builder().build();

        RttConfig config = new RttConfig(deviceRole,
                rttRangingParams,
                sessionConfig,
                peerDevice);

        assertEquals(config.getTechnology(), RangingTechnology.RTT);
        assertEquals(config.getRangingParams(), rttRangingParams);
        assertEquals(config.getDeviceRole(), deviceRole);
        assertEquals(config.getSessionConfig(), sessionConfig);
        assertEquals(config.getPeerDevice(), peerDevice);

        RttRangingParameters params = config.asBackendParameters();
        assertThat(params).isNotNull();
        assertThat(params.toString()).isNotNull();
    }
}

