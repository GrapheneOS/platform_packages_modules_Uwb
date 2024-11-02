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

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.params.DataNotificationConfig;
import android.ranging.rtt.RttRangingParams;

import com.android.ranging.rtt.backend.internal.RttRangingParameters;
import com.android.server.ranging.RangingConfig;

public class RttConfig implements RangingConfig.TechnologyConfig {

    private final DataNotificationConfig mDataNotificationConfig;
    private final RttRangingParams mRangingParams;
    private final RangingDevice mPeerDevice;

    @RangingPreference.DeviceRole
    private final int mDeviceRole;

    public RttConfig(int deviceRole,
            RttRangingParams rttRangingParams,
            DataNotificationConfig dataNotificationConfig,
            RangingDevice peerDevice) {
        mDeviceRole = deviceRole;
        mRangingParams = rttRangingParams;
        mDataNotificationConfig = dataNotificationConfig;
        mPeerDevice = peerDevice;
    }

    public DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    public RttRangingParams getRangingParams() {
        return mRangingParams;
    }

    public int getDeviceRole() {
        return mDeviceRole;
    }

    public RangingDevice getPeerDevice() {
        return mPeerDevice;
    }

    public RttRangingParameters asBackendParameters() {
        return new RttRangingParameters.Builder()
                .setDeviceRole(mDeviceRole)
                .setServiceName(mRangingParams.getServiceName())
                .setMatchFilter(mRangingParams.getMatchFilter())
                .setMaxDistanceMm(mDataNotificationConfig.getProximityFarCm() * 100)
                .setMinDistanceMm(mDataNotificationConfig.getProximityNearCm() * 100)
                .setEnablePublisherRanging(true)
                .build();
    }
}
