/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging.tests.blerssi;

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import static junit.framework.Assert.assertEquals;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.rssi.BleRssiRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.blerssi.BleRssiConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@SmallTest
public class BleRssiConfigTest {

    @Test
    public void testBleRssiConfig() {
        BleRssiRangingParams bleRssiRangingParams = new BleRssiRangingParams.Builder(
                "AA:BB:CC:AA:BB:CC").build();
        SessionConfig sessionConfig = new SessionConfig.Builder().build();
        RangingDevice rangingDevice = new RangingDevice.Builder().build();

        BleRssiConfig config = new BleRssiConfig(
                DEVICE_ROLE_RESPONDER,
                bleRssiRangingParams,
                sessionConfig,
                rangingDevice);

        assertEquals(config.getDeviceRole(), DEVICE_ROLE_RESPONDER);
        assertEquals(config.getRangingParams(), bleRssiRangingParams);
        assertEquals(config.getSessionConfig(), sessionConfig);
        assertEquals(config.getPeerDevice(), rangingDevice);
    }
}
