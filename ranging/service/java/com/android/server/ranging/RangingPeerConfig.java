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
package com.android.server.ranging;

import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SensorFusionParams;
import android.ranging.raw.RawRangingDevice;

import androidx.annotation.NonNull;

import com.android.server.ranging.blerssi.BleRssiConfig;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;

/**
 * A complete configuration for a ranging peer. This encapsulates all information contained in
 * an OOB configuration message and everything needed to configure each requested ranging
 * technology's underlying API.
 */
public class RangingPeerConfig {
    private final RawRangingDevice mPeerDevice;
    private final @RangingPreference.DeviceRole int mDeviceRole;
    private final SensorFusionParams mFusionConfig;
    private final DataNotificationConfig mDataNotificationConfig;
    private final boolean mIsAoaNeeded;
    private final ImmutableMap<RangingTechnology, TechnologyConfig> mTechnologyConfigs;

    /** A complete configuration for a specific ranging technology */
    public interface TechnologyConfig {
    }

    private RangingPeerConfig(Builder builder) {
        mPeerDevice = builder.mPeerDevice;
        mDeviceRole = builder.mDeviceRole;
        mFusionConfig = builder.mFusionConfig;
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mIsAoaNeeded = builder.mIsAoaNeeded;

        ImmutableMap.Builder<RangingTechnology, TechnologyConfig> technologyConfigs =
                new ImmutableMap.Builder<>();
        insertUwbConfigIfSet(technologyConfigs);
        insertRttConfigIfSet(technologyConfigs);
        insertCsConfigIfSet(technologyConfigs);
        insertBleRssiConfigIfSet(technologyConfigs);
        mTechnologyConfigs = technologyConfigs.build();
    }

    public @NonNull RangingDevice getDevice() {
        return mPeerDevice.getRangingDevice();
    }

    public @RangingPreference.DeviceRole int getDeviceRole() {
        return mDeviceRole;
    }

    public @NonNull SensorFusionParams getSensorFusionConfig() {
        return mFusionConfig;
    }

    public @NonNull ImmutableMap<RangingTechnology, TechnologyConfig> getTechnologyConfigs() {
        return mTechnologyConfigs;
    }

    private void insertUwbConfigIfSet(
            @NonNull ImmutableMap.Builder<RangingTechnology, TechnologyConfig> configs
    ) {
        if (mPeerDevice.getUwbRangingParams() == null) return;

        configs.put(
                RangingTechnology.UWB,
                new UwbConfig.Builder(mPeerDevice.getUwbRangingParams())
                        .setPeerDevice(mPeerDevice.getRangingDevice())
                        .setAoaNeeded(mIsAoaNeeded)
                        .setDeviceRole(mDeviceRole)
                        // TODO(370077264): Set country code based on geolocation.
                        .setCountryCode("US")
                        .setDataNotificationConfig(mDataNotificationConfig)
                        .build()
        );
    }

    private void insertRttConfigIfSet(
            @NonNull ImmutableMap.Builder<RangingTechnology, TechnologyConfig> configs
    ) {
        if (mPeerDevice.getRttRangingParams() == null) return;

        configs.put(
                RangingTechnology.RTT,
                new RttConfig(
                        mDeviceRole,
                        mPeerDevice.getRttRangingParams(),
                        mDataNotificationConfig,
                        mPeerDevice.getRangingDevice())
        );
    }

    private void insertBleRssiConfigIfSet(
            @NonNull ImmutableMap.Builder<RangingTechnology, TechnologyConfig> configs
    ) {
        if (mPeerDevice.getBleRssiRangingParams() == null) return;

        configs.put(
                RangingTechnology.RSSI,
                new BleRssiConfig(
                        mDeviceRole,
                        mPeerDevice.getBleRssiRangingParams(),
                        mDataNotificationConfig,
                        mPeerDevice.getRangingDevice())
        );
    }

    private void insertCsConfigIfSet(
            @NonNull ImmutableMap.Builder<RangingTechnology, TechnologyConfig> configs
    ) {
        if (mPeerDevice.getCsRangingParams() == null) return;

        configs.put(
                RangingTechnology.CS,
                new CsConfig(
                        mDeviceRole,
                        mPeerDevice.getCsRangingParams(),
                        mDataNotificationConfig,
                        mPeerDevice.getRangingDevice())
        );
    }

    public static class Builder {
        private final RawRangingDevice mPeerDevice;
        private @RangingPreference.DeviceRole int mDeviceRole;
        private SensorFusionParams mFusionConfig;
        private boolean mIsAoaNeeded;
        private DataNotificationConfig mDataNotificationConfig;

        public RangingPeerConfig build() {
            return new RangingPeerConfig(this);
        }

        public Builder(@NonNull RawRangingDevice peer) {
            mPeerDevice = peer;
        }

        public Builder setDeviceRole(@RangingPreference.DeviceRole int role) {
            mDeviceRole = role;
            return this;
        }

        public Builder setSensorFusionConfig(@NonNull SensorFusionParams config) {
            mFusionConfig = config;
            return this;
        }
        public Builder setDataNotificationConfig(@NonNull DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }

        public Builder setAoaNeeded(boolean isAoaNeeded) {
            mIsAoaNeeded = isAoaNeeded;
            return this;
        }
    }

    @Override
    public String toString() {
        return "RangingPeerConfig{ "
                + "mPeerDevice="
                + mPeerDevice
                + ", mDeviceRole="
                + mDeviceRole
                + ", mFusionConfig="
                + mFusionConfig
                + ", mIsAoaNeeded="
                + mIsAoaNeeded
                + ", mTechnologyConfigs="
                + mTechnologyConfigs
                + " }";
    }
}
