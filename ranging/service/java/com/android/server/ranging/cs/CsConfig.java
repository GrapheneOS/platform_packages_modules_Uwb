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

import android.annotation.NonNull;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfiguration;
import android.ranging.ble.cs.CsRangingParams;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig.UnicastTechnologyConfig;

public class CsConfig implements UnicastTechnologyConfig {
    private static final String TAG = CsConfig.class.getSimpleName();

    private final SessionConfiguration mSessionConfig;
    private final CsRangingParams mRangingParams;

    private final RangingDevice mPeerDevice;

    @RangingPreference.DeviceRole
    private final int mDeviceRole;

    public CsConfig(int deviceRole,
            CsRangingParams csRangingParams,
            SessionConfiguration sessionConfig,
            @NonNull RangingDevice peerDevice) {
        mDeviceRole = deviceRole;
        mRangingParams = csRangingParams;
        mSessionConfig = sessionConfig;
        mPeerDevice = peerDevice;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.CS;
    }

    public SessionConfiguration getSessionConfig() {
        return mSessionConfig;
    }

    public CsRangingParams getRangingParams() {
        return mRangingParams;
    }

    public int getDeviceRole() {
        return mDeviceRole;
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
                + ", mDeviceRole="
                + mDeviceRole
                + ", mPeerDevice="
                + mPeerDevice
                + " }";
    }
}
