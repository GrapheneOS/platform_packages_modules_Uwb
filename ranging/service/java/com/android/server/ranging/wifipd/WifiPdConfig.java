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

package com.android.server.ranging.wifipd;

import static android.net.wifi.rtt.ProximityDetectionConfig.RANGING_SERVICE_ROLE_ADVERTISER;
import static android.net.wifi.rtt.ProximityDetectionConfig.RANGING_SERVICE_ROLE_SEEKER;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.wifi.pd.WifiPdRangingParams;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.ConfigurationManager;

import java.time.Duration;

public class WifiPdConfig implements ConfigurationManager.UnicastTechnologyConfig {

    private final SessionConfig mSessionConfig;
    private final WifiPdRangingParams mPdRangingParams;
    private final RangingDevice mPeerDevice;
    private final int mDeviceRole;

    public WifiPdConfig(
            int deviceRole,
            WifiPdRangingParams wifiPdRangingParams,
            SessionConfig sessionConfig,
            RangingDevice peerDevice
    ) {
        mDeviceRole = deviceRole == DEVICE_ROLE_RESPONDER ? RANGING_SERVICE_ROLE_ADVERTISER
                : RANGING_SERVICE_ROLE_SEEKER;
        mPdRangingParams = wifiPdRangingParams;
        mSessionConfig = sessionConfig;
        mPeerDevice = peerDevice;
    }

    public WifiPdRangingParams getPdRangingParams() {
        return mPdRangingParams;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    @NonNull
    @Override
    public RangingDevice getPeerDevice() {
        return mPeerDevice;
    }

    @NonNull
    @Override
    public RangingTechnology getTechnology() {
        return RangingTechnology.WIFI_PD;
    }

    @Override
    public int getDeviceRole() {
        return mDeviceRole;
    }

    @Override
    public Duration getRangingInterval() {
        return switch (mPdRangingParams.getRangingUpdateRate()) {
            case UPDATE_RATE_NORMAL -> Duration.ofMillis(240);
            case UPDATE_RATE_INFREQUENT -> Duration.ofMillis(600);
            case UPDATE_RATE_FREQUENT -> Duration.ofMillis(120);
            default -> throw new IllegalStateException("Unknown update rate");
        };
    }
}
