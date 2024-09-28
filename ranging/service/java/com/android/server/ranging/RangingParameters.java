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

import androidx.annotation.NonNull;

import com.android.server.ranging.cs.CsParameters;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.uwb.UwbParameters;
import com.android.server.ranging.RangingTechnology;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.time.Duration;
import java.util.Optional;

/** Parameters for a generic ranging session. */
public class RangingParameters {
    /** Parameters for a specific generic ranging technology. */
    public interface TechnologyParameters { }

    public enum DeviceRole {
        /**
         * The device is a controlee within the session.
         */
        CONTROLEE,
        /**
         * The device is the session controller. It decides when the session is started or stopped,
         * ranging technology preferences, etc.
         */
        CONTROLLER
    }

    private final DeviceRole mRole;
    private final Duration mNoInitialDataTimeout;
    private final Duration mNoUpdatedDataTimeout;
    private final FusionEngine.DataFuser mDataFuser;
    private final ImmutableMap<RangingTechnology, TechnologyParameters> mTechParams;

    private RangingParameters(@NonNull RangingParameters.Builder builder) {
        Preconditions.checkArgument(builder.mNoInitialDataTimeout != null,
                "No initial data timeout required but not provided");
        Preconditions.checkArgument(builder.mNoUpdatedDataTimeout != null,
                "No updated data timeout required but not provided");

        mRole = builder.mRole;
        mNoInitialDataTimeout = builder.mNoInitialDataTimeout;
        mNoUpdatedDataTimeout = builder.mNoUpdatedDataTimeout;
        mDataFuser = builder.mDataFuser;

        ImmutableMap.Builder<RangingTechnology, TechnologyParameters> techParamsBuilder =
                new ImmutableMap.Builder<>();
        if (builder.mUwbParameters != null) {
            techParamsBuilder.put(RangingTechnology.UWB, builder.mUwbParameters);
        }
        if (builder.mCsParameters != null) {
            techParamsBuilder.put(RangingTechnology.CS, builder.mCsParameters);
        }
        mTechParams = techParamsBuilder.build();
    }

    /**
     * @return The configured device role.
     */
    public @NonNull DeviceRole getRole() {
        return mRole;
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

    /**
     * @return the configured data fuser, or {@code Optional.empty()} if fusion wasn't set.
     */
    public Optional<FusionEngine.DataFuser> getDataFuser() {
        return Optional.ofNullable(mDataFuser);
    }

    /**
     * @return UWB parameters, or {@code Optional.empty()} if they were never set.
     */
    public Optional<UwbParameters> getUwbParameters() {
        return Optional.ofNullable(mTechParams.get(RangingTechnology.UWB))
                .map(params -> (UwbParameters) params);
    }

    /**
     * @return channel sounding parameters, or {@code Optional.empty()} if they were never set.
     */
    public Optional<CsParameters> getCsParameters() {
        return Optional.ofNullable(mTechParams.get(RangingTechnology.CS))
                .map(params -> (CsParameters) params);
    }

    /** @return A map between technologies and their corresponding generic parameters object. */
    public @NonNull ImmutableMap<RangingTechnology, TechnologyParameters> getTechnologyParams() {
        return mTechParams;
    }

    public static class Builder {
        private final DeviceRole mRole;
        private Duration mNoInitialDataTimeout = null;
        private Duration mNoUpdatedDataTimeout = null;
        private FusionEngine.DataFuser mDataFuser = null;
        private UwbParameters mUwbParameters = null;
        private CsParameters mCsParameters = null;

        /**
         * @param role of the device within the session.
         */
        public Builder(DeviceRole role) {
            mRole = role;
        }

        /** Build the {@link RangingParameters object} */
        public RangingParameters build() {
            return new RangingParameters(this);
        }

        /**
         * @param timeout after which the session will be stopped if no ranging data was produced
         *                directly after starting.
         */
        public Builder setNoInitialDataTimeout(Duration timeout) {
            mNoInitialDataTimeout = timeout;
            return this;
        }

        /**
         * @param timeout after which the session will be stopped if there is no new ranging data
         *                produced.
         */
        public Builder setNoUpdatedDataTimeout(Duration timeout) {
            mNoUpdatedDataTimeout = timeout;
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
    }
}
