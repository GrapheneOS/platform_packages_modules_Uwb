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

package com.android.server.ranging.uwb;

import android.annotation.NonNull;
import android.ranging.RangingPreference;
import android.ranging.uwb.DlTdoaRangingParams;
import android.ranging.SessionConfig;
import android.ranging.RangingDevice;
import android.ranging.uwb.UwbAddress;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * A complete configuration for UWB DL-TDOA ranging.
 */
public class DlTdoaConfig implements TechnologyConfig {

    private final DlTdoaRangingParams mParams;
    private final SessionConfig mSessionConfig;
    private final RangingDevice mTagDevice;
    private final UwbAddress mAddress;

    public DlTdoaConfig(
            @NonNull DlTdoaRangingParams params,
            @NonNull SessionConfig sessionConfig,
            @RangingPreference.DeviceRole int deviceRole,
            @NonNull RangingDevice tagDevice,
            @NonNull UwbAddress address) {
        mParams = Objects.requireNonNull(params);
        mSessionConfig = sessionConfig;
        mTagDevice = Objects.requireNonNull(tagDevice);
        mAddress = address;
    }

    @NonNull
    @Override
    public RangingTechnology getTechnology() {
        return RangingTechnology.UWB;
    }

    @Override
    public int getDeviceRole() {
        return RangingPreference.DEVICE_ROLE_DT_TAG;
    }

    @NonNull
    public DlTdoaRangingParams getParams() {
        return mParams;
    }

    public RangingDevice getTagDevice() {
        return mTagDevice;
    }

    public UwbAddress getDeviceAddress() {
        return mAddress;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DlTdoaConfig)) return false;
        DlTdoaConfig that = (DlTdoaConfig) o;
        return Objects.equals(mParams, that.mParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mParams);
    }

    @Override
    public String toString() {
        return "DlTdoaConfig{" +
                "mParams=" + mParams +
                '}';
    }
  @Override
  public Duration getRangingInterval() {
    // TODO: implement this method.
    return null;
  }
}
