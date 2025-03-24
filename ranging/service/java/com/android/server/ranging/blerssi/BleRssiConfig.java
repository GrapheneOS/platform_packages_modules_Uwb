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

package com.android.server.ranging.blerssi;

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.raw.RawRangingDevice;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig;

import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.Objects;

public class BleRssiConfig implements RangingSessionConfig.UnicastTechnologyConfig {
    private static final String TAG = BleRssiConfig.class.getSimpleName();

    public static final ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration>
            BLE_RSSI_UPDATE_RATE_DURATIONS = ImmutableMap.of(
            UPDATE_RATE_NORMAL, Duration.ofSeconds(1),
            UPDATE_RATE_INFREQUENT, Duration.ofSeconds(3),
            UPDATE_RATE_FREQUENT, Duration.ofMillis(500));

    private final SessionConfig mSessionConfig;
    private final BleRssiRangingParams mRangingParams;

    private final RangingDevice mPeerDevice;

    @RangingPreference.DeviceRole
    private final int mDeviceRole;

    public BleRssiConfig(int deviceRole,
            BleRssiRangingParams bleRssiRangingParams,
            SessionConfig sessionConfig,
            RangingDevice peerDevice) {
        mDeviceRole = deviceRole;
        mRangingParams = bleRssiRangingParams;
        mSessionConfig = sessionConfig;
        mPeerDevice = peerDevice;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.RSSI;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    public BleRssiRangingParams getRangingParams() {
        return mRangingParams;
    }

    @Override
    public @RangingPreference.DeviceRole int getDeviceRole() {
        return mDeviceRole;
    }

    @Override
    public @NonNull RangingDevice getPeerDevice() {
        return mPeerDevice;
    }

    @Override
    public String toString() {
        return "BleRssiConfig{ "
                + "mSessionConfig="
                + mSessionConfig
                + ", mRangingParams="
                + mRangingParams
                + ", mDeviceRole="
                + mDeviceRole
                + ", mPeerDevice="
                + mPeerDevice
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleRssiConfig that)) return false;
        return mDeviceRole == that.mDeviceRole && Objects.equals(mSessionConfig,
                that.mSessionConfig) && Objects.equals(mRangingParams, that.mRangingParams)
                && Objects.equals(mPeerDevice, that.mPeerDevice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionConfig, mRangingParams, mPeerDevice, mDeviceRole);
    }
}
