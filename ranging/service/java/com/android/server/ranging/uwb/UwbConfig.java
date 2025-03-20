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

package com.android.server.ranging.uwb;

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import androidx.annotation.NonNull;

import com.android.ranging.uwb.backend.internal.RangingParameters;
import com.android.ranging.uwb.backend.internal.UwbRangeDataNtfConfig;
import com.android.ranging.uwb.backend.internal.UwbRangeLimitsConfig;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig.MulticastTechnologyConfig;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.uwb.support.base.RequiredParam;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A complete configuration for UWB ranging. This encapsulates all information contained in a
 * configuration message sent over OOB and everything required to start a session in the underlying
 * UWB system API.
 */
public class UwbConfig implements MulticastTechnologyConfig {
    private static final String TAG = UwbConfig.class.getSimpleName();

    private final SessionConfig mSessionConfig;
    private final UwbRangingParams mParameters;
    private final int mDeviceRole;
    private final ImmutableBiMap<RangingDevice, UwbAddress> mPeerAddresses;

    private UwbConfig(Builder builder) {
        mParameters = builder.mParameters;
        mSessionConfig = builder.mSessionConfig;
        mDeviceRole = builder.mDeviceRole;
        mPeerAddresses = builder.mPeerAddresses.get();
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.UWB;
    }

    @Override
    public @NonNull ImmutableSet<RangingDevice> getPeerDevices() {
        return mPeerAddresses.keySet();
    }

    /** Returns the length of the session key. */
    public final int getSessionKeyInfoLength() {
        if (mParameters.getSessionKeyInfo() == null) {
            return 0;
        } else {
            return mParameters.getSessionKeyInfo().length;
        }
    }

    public @NonNull UwbRangingParams getParameters() {
        return mParameters;
    }

    @Override
    public @RangingPreference.DeviceRole int getDeviceRole() {
        return mDeviceRole;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    public @NonNull ImmutableBiMap<RangingDevice, UwbAddress> getPeerAddresses() {
        return mPeerAddresses;
    }


    /**
     * @return the configuration converted to a
     * {@link androidx.core.uwb.backend.impl.internal.RangingParameters} accepted by the UWB
     * backend.
     */
    public RangingParameters asBackendParameters(DataNotificationConfig dataNotificationConfig) {
        List<com.android.ranging.uwb.backend.internal.UwbAddress> peerAddresses = mPeerAddresses
                .values()
                .stream()
                .map((address) ->
                        com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                                address.getAddressBytes()))
                .collect(Collectors.toList());
        return new RangingParameters(
                (int) mParameters.getConfigId(),
                mParameters.getSessionId(),
                mParameters.getSubSessionId(),
                mParameters.getSessionKeyInfo(),
                mParameters.getSubSessionKeyInfo(),
                toBackend(mParameters.getComplexChannel()),
                peerAddresses,
                (int) mParameters.getRangingUpdateRate(),
                toBackend(dataNotificationConfig),
                (int) mParameters.getSlotDuration(),
                // RangingParameters has isAoaDisabled field, Inverting here.
                !mSessionConfig.isAngleOfArrivalNeeded(),
                new UwbRangeLimitsConfig.Builder().setRangeMaxNumberOfMeasurements(
                        mSessionConfig.getRangingMeasurementsLimit()
                ).build()
        );
    }

    public static @NonNull UwbRangeDataNtfConfig toBackend(
            @NonNull DataNotificationConfig rangeDataNtfConfig
    ) {
        return new UwbRangeDataNtfConfig.Builder()
                .setRangeDataConfigType((int) rangeDataNtfConfig.getNotificationConfigType())
                .setNtfProximityNear(rangeDataNtfConfig.getProximityNearCm())
                .setNtfProximityFar(rangeDataNtfConfig.getProximityFarCm())
                .build();
    }

    public static @NonNull com.android.ranging.uwb.backend.internal.UwbComplexChannel toBackend(
            @NonNull UwbComplexChannel complexChannel
    ) {
        return new com.android.ranging.uwb.backend.internal.UwbComplexChannel(
                (int) complexChannel.getChannel(), (int) complexChannel.getPreambleIndex());
    }

    public static @NonNull com.android.ranging.uwb.backend.internal.UwbAddress toBackend(
            @NonNull UwbAddress address
    ) {
        return com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                address.getAddressBytes());
    }


    /** Builder for {@link UwbConfig}. */
    public static class Builder {
        private final UwbRangingParams mParameters;
        private final RequiredParam<ImmutableBiMap<RangingDevice, UwbAddress>> mPeerAddresses =
                new RequiredParam<>();
        private SessionConfig mSessionConfig = new SessionConfig.Builder().build();

        private int mDeviceRole = DEVICE_ROLE_RESPONDER;
        private boolean mIsAoaNeeded = false;

        public Builder(@NonNull UwbRangingParams parameters) {
            mParameters = parameters;
        }

        public @NonNull UwbConfig build() {
            return new UwbConfig(this);
        }

        public Builder setPeerAddresses(
                @NonNull ImmutableBiMap<RangingDevice, UwbAddress> addresses
        ) {
            mPeerAddresses.set(addresses);
            return this;
        }

        public Builder setDeviceRole(@RangingPreference.DeviceRole int deviceRole) {
            mDeviceRole = deviceRole;
            return this;
        }

        public Builder setSessionConfig(SessionConfig sessionConfig) {
            mSessionConfig = sessionConfig;
            return this;
        }
    }

    @Override
    public String toString() {
        return "UwbConfig{"
                + "mParameters="
                + mParameters
                + ", mSessionConfig="
                + mSessionConfig
                + ", mDeviceRole="
                + mDeviceRole
                + ", mPeerAddresses="
                + mPeerAddresses
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UwbConfig uwbConfig)) return false;
        return mDeviceRole == uwbConfig.mDeviceRole && Objects.equals(mSessionConfig,
                uwbConfig.mSessionConfig) && Objects.equals(mParameters,
                uwbConfig.mParameters) && Objects.equals(mPeerAddresses,
                uwbConfig.mPeerAddresses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSessionConfig, mParameters, mDeviceRole, mPeerAddresses);
    }
}
