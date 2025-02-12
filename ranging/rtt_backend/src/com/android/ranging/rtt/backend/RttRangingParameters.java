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

package com.android.ranging.rtt.backend;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

public class RttRangingParameters {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceRole.PUBLISHER,
            DeviceRole.SUBSCRIBER,
    })
    public @interface DeviceRole {
        int PUBLISHER = 0;
        int SUBSCRIBER = 1;
    }

    @IntDef({
            INFREQUENT,
            NORMAL,
            FAST,
    })
    public @interface RangingUpdateRate {}

    /**
     * Requests for ranging data in 512 milliseconds
     */
    public static final int NORMAL = 1;

    /**
     * Requests for ranging data in 8192 milliseconds
     */
    public static final int INFREQUENT = 2;

    /**
     * Requests for ranging data in 256 milliseconds
     */
    public static final int FAST = 3;

    private final @DeviceRole int mDeviceRole;
    /**
     * Returns Service ID for WiFi Aware
     */
    protected final byte mServiceId;
    protected final byte[] mMatchFilter;
    protected final String mServiceName;
    protected final int mMaxDistanceMm;
    protected final int mMinDistanceMm;

    @RangingUpdateRate
    private final int mUpdateRate;

    private final boolean mEnablePeriodicRangingHwFeature;

    private final boolean mRangeDataNtfDisabled;

    public int getDeviceRole() {
        return mDeviceRole;
    }

    /**
     * get Service ID of the RTT session
     *
     * @return Service ID of the RTT session
     */
    public byte getServiceId() {
        return mServiceId;
    }

    /**
     * get Service name of the RTT session
     *
     * @return Service name of the RTT session
     */
    public String getServiceName() {
        return mServiceName;
    }

    /**
     * get Match filter bytes of the RTT session
     *
     * @return Match filter bytes of the RTT session
     */
    public byte[] getMatchFilter() {
        return mMatchFilter;
    }

    /**
     * get Max Distance limit of the RTT session(Unit : mm)
     *
     * @return Max Distance limit of the RTT session(Unit : mm)
     */
    public int getMaxDistanceMm() {
        return mMaxDistanceMm;
    }

    /**
     * get Min Distance limit of the RTT session(Unit : mm)
     *
     * @return Min Distance limit of the RTT session(Unit : mm)
     */
    public int getMinDistanceMm() {
        return mMinDistanceMm;
    }

    public int getUpdateRate() {
        return mUpdateRate;
    }

    public boolean isPeriodicRangingHwFeatureEnabled() {
        return mEnablePeriodicRangingHwFeature;
    }

    public boolean isRangeDataNtfDisabled() {
        return mRangeDataNtfDisabled;
    }

    public RttRangingParameters(Builder builder) {
        mDeviceRole = builder.mDeviceRole;
        mServiceId = builder.mServiceId;
        mServiceName = builder.mServiceName;
        mMatchFilter = builder.mMatchFilter;
        mMaxDistanceMm = builder.mMaxDistanceMm;
        mMinDistanceMm = builder.mMinDistanceMm;
        mUpdateRate = builder.mRangingUpdateRate;
        mEnablePeriodicRangingHwFeature = builder.mEnablePeriodicRangingHwFeature;
        mRangeDataNtfDisabled = builder.mRangeDataNtfDisabled;
    }


    /**
     * Returns a builder for {@link com.android.ranging.generic.ranging.rtt.RttRangingParameters}.
     */
    public static class Builder {
        protected @DeviceRole int mDeviceRole = DeviceRole.PUBLISHER;
        protected byte mServiceId = 0;
        protected String mServiceName = "";
        protected byte[] mMatchFilter = new byte[]{};
        protected int mMaxDistanceMm = 30 * 100 * 100;
        protected int mMinDistanceMm = 0;
        private int mRangingUpdateRate = NORMAL;
        private boolean mEnablePeriodicRangingHwFeature = false;
        private boolean mRangeDataNtfDisabled = false;

        public Builder setDeviceRole(int deviceRole) {
            mDeviceRole = deviceRole;
            return this;
        }

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

        public Builder setMatchFilter(byte[] matchFilter) {
            mMatchFilter = matchFilter;
            return this;
        }

        public Builder setUpdateRate(int updateRate) {
            mRangingUpdateRate = updateRate;
            return this;
        }

        public Builder setPeriodicRangingHwFeatureEnabled(boolean enabled) {
            mEnablePeriodicRangingHwFeature = enabled;
            return this;
        }

        public Builder setRangeDataNtfDisabled(boolean rangeDataNtfDisabled) {
            mRangeDataNtfDisabled = rangeDataNtfDisabled;
            return this;
        }

        public RttRangingParameters build() {
            return new RttRangingParameters(this);
        }
    }

    public static int getIntervalMs(@NonNull RttRangingParameters rttRangingParameters) {
        switch (rttRangingParameters.getUpdateRate()) {
            case FAST -> {
                return rttRangingParameters.isPeriodicRangingHwFeatureEnabled() ? 128 : 256;
            }
            case INFREQUENT -> {
                return 8192;
            }
            default -> {
                return rttRangingParameters.isPeriodicRangingHwFeatureEnabled() ? 256 : 512;
            }
        }
    }

    @Override
    public String toString() {
        return "RttRangingParameters{ "
                + "deviceRole: "
                + mDeviceRole
                + ", serviceId: "
                + mServiceId
                + ", serviceName: "
                + mServiceName
                + ", matchFilter: "
                + Arrays.toString(mMatchFilter)
                + ", maxDistanceMm: "
                + mMaxDistanceMm
                + ", minDistanceMm: "
                + mMinDistanceMm
                + ", enablePeriodicRangingHwFeature: "
                + mEnablePeriodicRangingHwFeature
                + ", rangeDataNtfDisabled: "
                + mRangeDataNtfDisabled
                + " }";
    }
}
