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

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.annotation.FlaggedApi;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.wifi.rtt.RttRangingParams;
import android.ranging.wifi.rtt.RttStationRangingParams;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;
import com.android.ranging.rtt.backend.RttRangingParameters;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.Objects;

public class RttConfig implements TechnologyConfig {

    private final SessionConfig mSessionConfig;
    private final RttRangingParams mRangingParams;
    private final RttStationRangingParams mStationRangingParams;
    private final RangingDevice mPeerDevice;

    private final @RangingPreference.DeviceRole int mDeviceRole;
    private final RangingTechnology mTech;

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
        mTech = RangingTechnology.RTT;
        mStationRangingParams = null;
    }

    @FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_25Q4)
    public RttConfig(
            int deviceRole,
            @NonNull RttStationRangingParams rttStationRangingParams,
            @NonNull SessionConfig sessionConfig,
            @NonNull RangingDevice peerDevice
    ) {
        mDeviceRole = deviceRole;
        mStationRangingParams = rttStationRangingParams;
        mSessionConfig = sessionConfig;
        mPeerDevice = peerDevice;
        mTech = RangingTechnology.RTT_STATION;
        mRangingParams = null;
    }

    @Override
    @NonNull
    public RangingTechnology getTechnology() {
        //return RangingTechnology.RTT;
        return mTech;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    public RttRangingParams getRangingParams() {
        return mRangingParams;
    }

    @FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_25Q4)
    public RttStationRangingParams getStationRangingParams() {
        return mStationRangingParams;
    }

    @Override
    public Duration getRangingInterval() {
        return switch (getRangingParams().getRangingUpdateRate()) {
            case UPDATE_RATE_NORMAL -> {
                if (getRangingParams().isPeriodicRangingHwFeatureEnabled()) {
                    yield Duration.ofMillis(256);
                } else {
                    yield Duration.ofMillis(512);
                }
            }
            case UPDATE_RATE_INFREQUENT -> Duration.ofMillis(8192);
            case UPDATE_RATE_FREQUENT -> {
                if (getRangingParams().isPeriodicRangingHwFeatureEnabled()) {
                    yield Duration.ofMillis(128);
                } else {
                    yield Duration.ofMillis(256);
                }
            }
            default -> throw new IllegalStateException("Unknown update rate");
        };
    }

    @Override
    public @RangingPreference.DeviceRole int getDeviceRole() {
        return mDeviceRole;
    }

    @Override
    public @NonNull ImmutableSet<RangingDevice> getPeerDevices() {
        return ImmutableSet.of(mPeerDevice);
    }

    public RttRangingParameters asBackendParameters() {
        RttRangingParameters.Builder builder = new RttRangingParameters.Builder();
        builder.setDeviceRole(mDeviceRole);
        if (mTech == RangingTechnology.RTT_STATION) {
            builder.setBssid(mStationRangingParams.getBssid());
            builder.setChannelWidth(mStationRangingParams.getChannelWidth());
            builder.setUpdateRate(mStationRangingParams.getRangingUpdateRate());
        } else {
            builder.setServiceName(mRangingParams.getServiceName());
            builder.setMatchFilter(mRangingParams.getMatchFilter());
            builder.setUpdateRate(mRangingParams.getRangingUpdateRate());
            builder.setPeriodicRangingHwFeatureEnabled(
                    mRangingParams.isPeriodicRangingHwFeatureEnabled());
        }
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
                + ", mStationRangingParams="
                + mStationRangingParams
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
                rttConfig.mPeerDevice) && Objects.equals(mStationRangingParams,
                rttConfig.mStationRangingParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionConfig, mRangingParams,
                mPeerDevice, mDeviceRole, mStationRangingParams);
    }
}
