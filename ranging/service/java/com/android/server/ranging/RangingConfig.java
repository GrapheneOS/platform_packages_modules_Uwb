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

import android.ranging.uwb.UwbParameters;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;

/**
 * A complete configuration for a ranging session. This encapsulates all information contained in
 * an OOB configuration message and everything needed to configure each requested ranging
 * technology's underlying API.
 */
public class RangingConfig extends RangingParameters {

    private static final String TAG = RangingConfig.class.getSimpleName();

    private final RangingInjector mRangingInjector;

    private final ImmutableMap<RangingTechnology, TechnologyConfig> mTechnologyConfigs;

    /** A complete configuration for a specific ranging technology */
    public interface TechnologyConfig {
    }

    public RangingConfig(
            @NonNull RangingInjector rangingInjector, @NonNull RangingParameters parameters
    ) {
        super(parameters);
        mRangingInjector = rangingInjector;

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
    }

    public @NonNull ImmutableMap<RangingTechnology, TechnologyConfig> getTechnologyConfigs() {
        return mTechnologyConfigs;
    }

    private @Nullable UwbConfig getUwbConfig() {
        UwbParameters uwbParameters = getUwbParameters();
        if (uwbParameters == null) return null;
        UwbAdapter adapter = mRangingInjector.getAdapterProvider().getUwbAdapter();
        if (adapter == null) {
            Log.w(TAG, "Uwb was requested for this session but is not supported by the device");
            return null;
        }

        return new UwbConfig.Builder(uwbParameters)
                // TODO(370077264): Set country code based on geolocation.
                .setCountryCode("US")
                .setDeviceRole(getDeviceRole())
                .setLocalAddress(getLocalUwbAddressSetForTesting() != null
                        ? getLocalUwbAddressSetForTesting()
                        : adapter.getLocalAddress())
                .setDataNotificationConfig(getDataNotificationConfig())
                .build();
    }


    private @Nullable CsConfig getCsConfig() {
        return null;
    }
}
