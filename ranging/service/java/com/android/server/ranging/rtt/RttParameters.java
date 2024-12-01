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

import androidx.annotation.NonNull;

import com.android.ranging.rtt.backend.internal.RttRangingParameters;
import com.android.server.ranging.RangingSessionConfig;
import com.android.server.ranging.RangingTechnology;

import java.time.Duration;

public class RttParameters extends com.android.ranging.rtt.backend.internal.RttRangingParameters
        implements RangingSessionConfig.UnicastTechnologyConfig {
    public RttParameters(Builder builder) {
        super(builder);
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.RTT;
    }

    @Override
    public @NonNull RangingDevice getPeerDevice() {
        throw new UnsupportedOperationException("Not implemented!");
    }

    public static class Builder extends RttRangingParameters.Builder {
        /** Set the service ID that produced this data */
        public Builder setServiceId(byte serviceId) {
            mServiceId = serviceId;
            return this;
        }

        public Builder setServiceName(String serviceName) {
            mServiceName = serviceName;
            return this;
        }

        public Builder setMaxDistanceMm(int maxDistanceMm) {
            mMaxDistanceMm = maxDistanceMm;
            return this;
        }

        public Builder setMinDistanceMm(int minDistanceMm) {
            mMinDistanceMm = minDistanceMm;
            return this;
        }

        public Builder setEnablePublisherRanging(boolean enablePublisherRange) {
            mEnablePublisherRanging = enablePublisherRange;
            return this;
        }

        public Builder setPublisherPingDuration(@NonNull Duration ping) {
            mPublisherPingDuration = ping;
            return this;
        }

        public Builder setMatchFilter(byte[] matchFilter) {
            mMatchFilter = matchFilter;
            return this;
        }

        public RttParameters build() {
            return new RttParameters(this);
        }
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
