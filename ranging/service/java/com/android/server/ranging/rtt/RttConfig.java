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

package com.android.server.ranging.rtt;

import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.annotation.NonNull;

import com.android.ranging.rtt.backend.RttRangingParameters;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig;

import java.util.Objects;

public class RttConfig implements RangingSessionConfig.UnicastTechnologyConfig {

    private final SessionConfig mSessionConfig;
    private final RttRangingParams mRangingParams;
    private final RangingDevice mPeerDevice;

    private final @RangingPreference.DeviceRole int mDeviceRole;

    public RttConfig(
            int deviceRole,
            @NonNull RttRangingParams rttRangingParams,
            @NonNull SessionConfig sessionConfig,
            @NonNull RangingDevice peerDevice
    ) {
        mDeviceRole = deviceRole;
        mRangingParams = rttRangingParams;
        mSessionConfig = sessionConfig;
        mPeerDevice = peerDevice;
    }

    @Override
    @NonNull
    public RangingTechnology getTechnology() {
        return RangingTechnology.RTT;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    public RttRangingParams getRangingParams() {
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

    public RttRangingParameters asBackendParameters() {
        RttRangingParameters.Builder builder = new RttRangingParameters.Builder()
                .setDeviceRole(mDeviceRole)
                .setServiceName(mRangingParams.getServiceName())
                .setMatchFilter(mRangingParams.getMatchFilter())
                .setUpdateRate(mRangingParams.getRangingUpdateRate())
                .setPeriodicRangingHwFeatureEnabled(
                        mRangingParams.isPeriodicRangingHwFeatureEnabled());

        DataNotificationConfig ntfConfig = mSessionConfig.getDataNotificationConfig();
        switch (ntfConfig.getNotificationConfigType()) {
            case DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE,
                    // Handled in adapter.
                    DataNotificationConfig.NOTIFICATION_CONFIG_PROXIMITY_EDGE -> builder
                    .setMinDistanceMm(0)
                    .setMaxDistanceMm(50 * 100 * 100); // 50 meters.
            case DataNotificationConfig.NOTIFICATION_CONFIG_DISABLE ->
                    builder.setRangeDataNtfDisabled(true);
            case DataNotificationConfig.NOTIFICATION_CONFIG_PROXIMITY_LEVEL ->
                    builder.setMinDistanceMm(
                                    ntfConfig.getProximityNearCm() * 100)
                            .setMaxDistanceMm(ntfConfig.getProximityFarCm() * 100);
        }
        return builder.build();
    }

    @Override
    public String toString() {
        return "RttConfig{ "
                + "mSessionConfig="
                + mSessionConfig
                + ", mRangingParams="
                + mRangingParams
                + ", mPeerDevice="
                + mPeerDevice
                + ", mDeviceRole="
                + mDeviceRole
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RttConfig rttConfig)) return false;
        return mDeviceRole == rttConfig.mDeviceRole && Objects.equals(mSessionConfig,
                rttConfig.mSessionConfig) && Objects.equals(mRangingParams,
                rttConfig.mRangingParams) && Objects.equals(mPeerDevice,
                rttConfig.mPeerDevice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionConfig, mRangingParams, mPeerDevice, mDeviceRole);
    }
}
