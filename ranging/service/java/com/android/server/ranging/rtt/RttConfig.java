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
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.annotation.NonNull;

import com.android.ranging.rtt.backend.internal.RttRangingParameters;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig;

public class RttConfig implements RangingSessionConfig.UnicastTechnologyConfig {

    private final DataNotificationConfig mDataNotificationConfig;
    private final RttRangingParams mRangingParams;
    private final RangingDevice mPeerDevice;

    private final @RangingPreference.DeviceRole int mDeviceRole;

    public RttConfig(
            int deviceRole,
            @NonNull RttRangingParams rttRangingParams,
            @NonNull DataNotificationConfig dataNotificationConfig,
            @NonNull RangingDevice peerDevice
    ) {
        mDeviceRole = deviceRole;
        mRangingParams = rttRangingParams;
        mDataNotificationConfig = dataNotificationConfig;
        mPeerDevice = peerDevice;
    }

    @Override
    @NonNull public RangingTechnology getTechnology() {
        return RangingTechnology.RTT;
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

    @Override
    public @NonNull RangingDevice getPeerDevice() {
        return mPeerDevice;
    }

    public RttRangingParameters asBackendParameters() {
        RttRangingParameters.Builder builder = new RttRangingParameters.Builder()
                .setDeviceRole(mDeviceRole)
                .setServiceName(mRangingParams.getServiceName())
                .setMatchFilter(mRangingParams.getMatchFilter())
                .setEnablePublisherRanging(true)
                .setUpdateRate(mRangingParams.getRangingUpdateRate())
                .setPeriodicRangingHwFeatureEnabled(
                        mRangingParams.isPeriodicRangingHwFeatureEnabled());

        switch (mDataNotificationConfig.getNotificationConfigType()) {
            case DataNotificationConfig.ENABLE -> builder
                    .setMinDistanceMm(0)
                    .setMaxDistanceMm(50 * 100 * 100); // 50 meters.
            case DataNotificationConfig.DISABLE -> builder.setMinDistanceMm(0)
                    //Set to 1 millimeter to get around mMaxDistanceMm <= mMinDistanceMm
                    .setMaxDistanceMm(1);
            case DataNotificationConfig.PROXIMITY_LEVEL -> builder.setMinDistanceMm(
                            mDataNotificationConfig.getProximityNearCm() * 100)
                    .setMaxDistanceMm(mDataNotificationConfig.getProximityFarCm() * 100);
            case DataNotificationConfig.PROXIMITY_EDGE -> builder.setProximityEdge(
                    mDataNotificationConfig.getProximityNearCm() * 100,
                    mDataNotificationConfig.getProximityFarCm() * 100);
        }
        return builder.build();
    }

    @Override
    public String toString() {
        return "RttConfig{ "
                + "mDataNotificationConfig="
                + mDataNotificationConfig
                + ", mRangingParams="
                + mRangingParams
                + ", mPeerDevice="
                + mPeerDevice
                + ", mDeviceRole="
                + mDeviceRole
                + " }";
    }
}
