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

package com.android.server.ranging.tests.cs;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.ble.cs.BleCsRangingCapabilities.CS_SECURITY_LEVEL_FOUR;
import static android.ranging.ble.cs.BleCsRangingParams.LOCATION_TYPE_INDOOR;
import static android.ranging.ble.cs.BleCsRangingParams.SIGHT_TYPE_LINE_OF_SIGHT;

import static junit.framework.Assert.assertEquals;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.cs.CsConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SmallTest
public class CsConfigTest {

    @Test
    public void testCsConfig() {
        BleCsRangingParams bleCsRangingParams = new BleCsRangingParams.Builder("AA:BB:CC:AA:BB:CC")
                .setSightType(SIGHT_TYPE_LINE_OF_SIGHT)
                .setLocationType(LOCATION_TYPE_INDOOR)
                .setSecurityLevel(CS_SECURITY_LEVEL_FOUR)
                .build();

        SessionConfig sessionConfig = new SessionConfig.Builder().build();
        RangingDevice rangingDevice = new RangingDevice.Builder().build();

        CsConfig config = new CsConfig(
                bleCsRangingParams,
                sessionConfig,
                rangingDevice);

        assertEquals(config.getDeviceRole(), DEVICE_ROLE_INITIATOR);
        assertEquals(config.getRangingParams(), bleCsRangingParams);
        assertEquals(config.getSessionConfig(), sessionConfig);
        assertEquals(config.getPeerDevice(), rangingDevice);
    }
}
