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

import android.ranging.RangingPreference;
import android.ranging.uwb.UwbRangingParameters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;
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

    private final FusionEngine mFusionEngine;

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

        UwbConfig uwbConfig = getUwbConfig();
        if (uwbConfig != null) {
            technologyConfigsBuilder.put(RangingTechnology.UWB, uwbConfig);
        }

        CsConfig csConfig = getCsConfig();
        if (csConfig != null) {
            technologyConfigsBuilder.put(RangingTechnology.CS, csConfig);
        }

        mTechnologyConfigs = technologyConfigsBuilder.build();

        // Sensor fusion configuration
        if (builder.mPreference.getSensorFusionParameters().getUseSensorFusion()) {
            mFusionEngine = new FilteringFusionEngine(
                    new DataFusers.PreferentialDataFuser(RangingTechnology.UWB)
            );
        } else {
            mFusionEngine = new NoOpFusionEngine();
        }
    }

    public @NonNull ImmutableMap<RangingTechnology, TechnologyConfig> getTechnologyConfigs() {
        return mTechnologyConfigs;
    }

    public @NonNull FusionEngine getFusionEngine() {
        return mFusionEngine;
    }

    public @NonNull Duration getNoInitialDataTimeout() {
        return mNoInitialDataTimeout;
    }

    public @NonNull Duration getNoUpdatedDataTimeout() {
        return mNoUpdatedDataTimeout;
    }


    private @Nullable UwbConfig getUwbConfig() {
        if (mPreference.getRangingParameters() == null) return null;
        UwbRangingParameters uwbParameters = mPreference.getRangingParameters().getUwbParameters();
        if (uwbParameters == null) return null;

        UwbConfig.Builder configBuilder = new UwbConfig.Builder(uwbParameters)
                // TODO(370077264): Set country code based on geolocation.
                .setCountryCode("US")
                .setDataNotificationConfig(mPreference.getDataNotificationConfig());

        return configBuilder.build();
    }

    private @Nullable CsConfig getCsConfig() {
        return null;
    }

    public static class Builder {
        private final RangingPreference mPreference;
        private Duration mNoInitialDataTimeout = Duration.ofSeconds(999);
        private Duration mNoUpdatedDataTimeout = Duration.ofSeconds(999);

        public RangingConfig build() {
            return new RangingConfig(this);
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
        protected @NonNull Set<RangingTechnology> getDataSources() {
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
