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

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.cs.CsRangingParams;
import android.ranging.rtt.RttRangingParams;
import android.ranging.uwb.UwbRangingParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.Set;

/**
 * A complete configuration for a ranging session. This encapsulates all information contained in
 * an OOB configuration message and everything needed to configure each requested ranging
 * technology's underlying API.
 */
public class RangingConfig {

    private static final String TAG = RangingConfig.class.getSimpleName();

    private final RangingPreference mPreference;
    private final ImmutableMap<RangingTechnology, TechnologyConfig> mTechnologyConfigs;

    private final boolean mIsFusionEnabled;

    private final Duration mNoInitialDataTimeout;
    private final Duration mNoUpdatedDataTimeout;

    /** A complete configuration for a specific ranging technology */
    public interface TechnologyConfig {
    }

    private RangingConfig(Builder builder) {
        mPreference = builder.mPreference;
        mNoInitialDataTimeout = builder.mNoInitialDataTimeout;
        mNoUpdatedDataTimeout = builder.mNoUpdatedDataTimeout;
        // Technology-specific configurations
        ImmutableMap.Builder<RangingTechnology, TechnologyConfig> technologyConfigsBuilder =
                new ImmutableMap.Builder<>();

        if (builder.mUwbRangingParams != null) {
            UwbConfig uwbConfig = getUwbConfig(builder);
            technologyConfigsBuilder.put(RangingTechnology.UWB, uwbConfig);
        }
        if (builder.mCsRangingParams != null) {
            CsConfig csConfig = getCsConfig();
            technologyConfigsBuilder.put(RangingTechnology.CS, csConfig);
        }
        if (builder.mRttRangingParams != null) {
            RttConfig rttConfig = getRttConfig(builder);
            technologyConfigsBuilder.put(RangingTechnology.RTT, rttConfig);
        }
        mTechnologyConfigs = technologyConfigsBuilder.build();

        mIsFusionEnabled = builder.mPreference.getSensorFusionParameters().isSensorFusionEnabled();
    }

    public @NonNull ImmutableMap<RangingTechnology, TechnologyConfig> getTechnologyConfigs() {
        return mTechnologyConfigs;
    }

    /** @return a new fusion engine matching the current configuration. */
    public @NonNull FusionEngine createConfiguredFusionEngine() {
        if (mIsFusionEnabled) {
            return new FilteringFusionEngine(
                    new DataFusers.PreferentialDataFuser(RangingTechnology.UWB)
            );
        } else {
            return new NoOpFusionEngine();
        }
    }

    public @NonNull Duration getNoInitialDataTimeout() {
        return mNoInitialDataTimeout;
    }

    public @NonNull Duration getNoUpdatedDataTimeout() {
        return mNoUpdatedDataTimeout;
    }

    private UwbConfig getUwbConfig(Builder builder) {
        UwbConfig.Builder configBuilder = new UwbConfig.Builder(builder.mUwbRangingParams)
                .setPeerDevice(builder.mPeerDevice)
                .setAoaNeeded(mPreference.isAoaNeeded())
                .setDeviceRole(mPreference.getDeviceRole())
                // TODO(370077264): Set country code based on geolocation.
                .setCountryCode("US")
                .setDataNotificationConfig(mPreference.getDataNotificationConfig());

        return configBuilder.build();
    }

    private RttConfig getRttConfig(Builder builder) {
        RttRangingParams rttRangingParams = builder.mRttRangingParams;
        return new RttConfig(mPreference.getDeviceRole(), rttRangingParams,
                mPreference.getDataNotificationConfig(), builder.mPeerDevice);
    }

    private @Nullable CsConfig getCsConfig() {
        return null;
    }

    public static class Builder {
        private final RangingPreference mPreference;
        private RangingDevice mPeerDevice;
        private UwbRangingParams mUwbRangingParams;
        private CsRangingParams mCsRangingParams;
        private RttRangingParams mRttRangingParams;
        private Duration mNoInitialDataTimeout = Duration.ofSeconds(999);
        private Duration mNoUpdatedDataTimeout = Duration.ofSeconds(999);

        public RangingConfig build() {
            return new RangingConfig(this);
        }

        public Builder setPeerDevice(RangingDevice device) {
            mPeerDevice = device;
            return this;
        }

        public Builder setUwbRangingParams(UwbRangingParams params) {
            mUwbRangingParams = params;
            return this;
        }

        public Builder setRttRangingParams(RttRangingParams params) {
            mRttRangingParams = params;
            return this;
        }

        public Builder setCsRangingParams(CsRangingParams params) {
            mCsRangingParams = params;
            return this;
        }

        public Builder(@NonNull RangingPreference preference) {
            mPreference = preference;
        }

        /**
         * @param timeout after which the session will be stopped if no ranging data was produced
         *                directly after starting.
         */
        public Builder setNoInitialDataTimeout(@NonNull Duration timeout) {
            mNoInitialDataTimeout = timeout;
            return this;
        }

        /**
         * @param timeout after which the session will be stopped if there is no new ranging data
         *                produced.
         */
        public Builder setNoUpdatedDataTimeout(@NonNull Duration timeout) {
            mNoUpdatedDataTimeout = timeout;
            return this;
        }
    }

    @VisibleForTesting
    public static class NoOpFusionEngine extends FusionEngine {

        @VisibleForTesting
        public NoOpFusionEngine() {
            super(new DataFusers.PassthroughDataFuser());
        }

        @Override
        public @NonNull Set<RangingTechnology> getDataSources() {
            return Set.of();
        }

        @Override
        public void addDataSource(@NonNull RangingTechnology technology) {
        }

        @Override
        public void removeDataSource(@NonNull RangingTechnology technology) {
        }
    }
}
