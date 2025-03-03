/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.ranging.cs;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.annotation.NonNull;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.raw.RawRangingDevice;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig.UnicastTechnologyConfig;

import com.google.common.collect.ImmutableMap;

import java.time.Duration;

/**
 * Only the CS initiator needs to be configured. The responder does not need to call into any API
 * and therefore has no configuration or adapter.
 */
public class CsConfig implements UnicastTechnologyConfig {
    private static final String TAG = CsConfig.class.getSimpleName();

    // TODO(390665219): Update this once we decide on a set of measurement intervals for channel
    //  sounding.
    public static final ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration>
            CS_UPDATE_RATE_DURATIONS = ImmutableMap.of(
                    UPDATE_RATE_NORMAL, Duration.ofMillis(200),
                    UPDATE_RATE_INFREQUENT, Duration.ofSeconds(5),
                    UPDATE_RATE_FREQUENT, Duration.ofMillis(100));

    private final SessionConfig mSessionConfig;
    private final BleCsRangingParams mRangingParams;

    private final RangingDevice mPeerDevice;

    public CsConfig(
            BleCsRangingParams bleCsRangingParams,
            SessionConfig sessionConfig,
            @NonNull RangingDevice peerDevice) {
        mRangingParams = bleCsRangingParams;
        mSessionConfig = sessionConfig;
        mPeerDevice = peerDevice;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.CS;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    public BleCsRangingParams getRangingParams() {
        return mRangingParams;
    }

    @Override
    public @RangingPreference.DeviceRole int getDeviceRole() {
        return DEVICE_ROLE_INITIATOR;
    }

    @Override
    public @NonNull RangingDevice getPeerDevice() {
        return mPeerDevice;
    }

    @Override
    public String toString() {
        return "CsConfig{ "
                + "mSessionConfig="
                + mSessionConfig
                + ", mRangingParams="
                + mRangingParams
                + ", mPeerDevice="
                + mPeerDevice
                + " }";
    }
}
