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

package com.android.server.ranging;

import android.ranging.DataNotificationConfig;
import android.ranging.uwb.UwbParameters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.ranging.uwb.backend.internal.UwbAddress;
import com.android.server.ranging.cs.CsParameters;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FusionEngine;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.base.RequiredParam;

import java.time.Duration;

/** Framework-configurable parameters for a ranging session. */
public class RangingParameters {
    public enum DeviceRole {
        RESPONDER,
        /** The device that initiates the session. */
        INITIATOR;

        public static final ImmutableList<DeviceRole> ROLES =
                ImmutableList.copyOf(DeviceRole.values());
    }

    private final DeviceRole mDeviceRole;
    private final Duration mNoInitialDataTimeout;
    private final Duration mNoUpdatedDataTimeout;
    private final DataNotificationConfig mDataNotificationConfig;
    private final FusionEngine.DataFuser mDataFuser;
    private final UwbParameters mUwbParameters;

    private final CsParameters mCsParameters;
    private final UwbAddress mLocalUwbAddress;

    private RangingParameters(@NonNull Builder builder) {
        mDeviceRole = builder.mDeviceRole;
        mNoInitialDataTimeout = builder.mNoInitialDataTimeout.get();
        mNoUpdatedDataTimeout = builder.mNoUpdatedDataTimeout.get();
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mDataFuser = builder.mDataFuser;
        mUwbParameters = builder.mUwbParameters;
        mCsParameters = builder.mCsParameters;
        mLocalUwbAddress = builder.mLocalUwbAddress;
    }

    public RangingParameters(@NonNull RangingParameters other) {
        mDeviceRole = other.mDeviceRole;
        mNoInitialDataTimeout = other.mNoInitialDataTimeout;
        mNoUpdatedDataTimeout = other.mNoUpdatedDataTimeout;
        mDataNotificationConfig = other.mDataNotificationConfig;
        mDataFuser = other.mDataFuser;
        mUwbParameters = other.mUwbParameters;
        mCsParameters = other.mCsParameters;
        mLocalUwbAddress = other.mLocalUwbAddress;
    }

    /** @return The device's role within the session. */
    public @NonNull DeviceRole getDeviceRole() {
        return mDeviceRole;
    }

    /**
     * @return timeout after which to stop the session if no ranging data is produced after
     * starting.
     */
    public @NonNull Duration getNoInitialDataTimeout() {
        return mNoInitialDataTimeout;
    }

    /**
     * @return timeout after which to stop the session if no new ranging data is produced.
     */
    public @NonNull Duration getNoUpdatedDataTimeout() {
        return mNoUpdatedDataTimeout;
    }

    /** @return configuration for when ranging data should be reported based on proximity. */
    public @NonNull DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    /**
     * @return the configured data fuser, or {@code Optional.empty()} if fusion wasn't set.
     */
    public @Nullable FusionEngine.DataFuser getDataFuser() {
        return mDataFuser;
    }

    /** @return ranging parameters for UWB, if they were provided */
    public @Nullable UwbParameters getUwbParameters() {
        return mUwbParameters;
    }

    /** @return ranging parameters for CS, if they were provided */
    public @Nullable CsParameters getCsParameters() {
        return mCsParameters;
    }

    public @Nullable UwbAddress getLocalUwbAddressSetForTesting() {
        return mLocalUwbAddress;
    }

    public static class Builder {
        private final DeviceRole mDeviceRole;
        private final RequiredParam<Duration> mNoInitialDataTimeout = new RequiredParam<>();
        private final RequiredParam<Duration> mNoUpdatedDataTimeout = new RequiredParam<>();
        private DataNotificationConfig mDataNotificationConfig = null;
        private FusionEngine.DataFuser mDataFuser = null;
        private UwbParameters mUwbParameters = null;
        private CsParameters mCsParameters = null;
        private UwbAddress mLocalUwbAddress = null;

        /**
         * @param role of the device within the session.
         */
        public Builder(DeviceRole role) {
            mDeviceRole = role;
        }

        /** Build the {@link RangingParameters object} */
        public RangingParameters build() {
            return new RangingParameters(this);
        }

        /**
         * @param timeout after which the session will be stopped if no ranging data was produced
         *                directly after starting.
         */
        public Builder setNoInitialDataTimeout(@NonNull Duration timeout) {
            mNoInitialDataTimeout.set(timeout);
            return this;
        }

        /**
         * @param timeout after which the session will be stopped if there is no new ranging data
         *                produced.
         */
        public Builder setNoUpdatedDataTimeout(@NonNull Duration timeout) {
            mNoUpdatedDataTimeout.set(timeout);
            return this;
        }

        /** @param config for when ranging data should be reported based on proximity. */
        public Builder setDataNotificationConfig(@NonNull DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }

        /**
         * @param fuser to use. See {@link DataFusers}.
         */
        public Builder useSensorFusion(FusionEngine.DataFuser fuser) {
            mDataFuser = fuser;
            return this;
        }

        /**
         * Range with UWB in this session.
         *
         * @param uwbParameters containing a configuration for UWB ranging.
         */
        public Builder useUwb(UwbParameters uwbParameters) {
            mUwbParameters = uwbParameters;
            return this;
        }

        /**
         * Range with Bluetooth Channel Sounding in this session.
         *
         * @param csParameters containing a configuration for CS ranging.
         */
        public Builder useCs(CsParameters csParameters) {
            mCsParameters = csParameters;
            return this;
        }

        @VisibleForTesting
        public Builder setLocalUwbAddressForTesting(@NonNull UwbAddress address) {
            mLocalUwbAddress = address;
            return this;
        }
    }
}
