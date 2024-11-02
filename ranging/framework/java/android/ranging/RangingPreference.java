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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.params.DataNotificationConfig;
import android.ranging.params.SensorFusionParams;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents the configuration preferences for a ranging session.
 *
 * <p>The {@code RangingPreference} class allows users to specify various parameters
 * required for a ranging session, including ranging parameters, sensor fusion settings,
 * and data notification configurations. It provides a {@link Builder} to construct
 * an instance with custom configurations.</p>
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingPreference implements Parcelable {

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DEVICE_ROLE_RESPONDER,
            DEVICE_ROLE_INITIATOR,
    })
    public @interface DeviceRole {
    }

    /** The device that responds to a session. */
    public static final int DEVICE_ROLE_RESPONDER = 0;
    /** The device that initiates the session. */
    public static final int DEVICE_ROLE_INITIATOR = 1;

    @DeviceRole
    private final int mDeviceRole;
    private final android.ranging.params.RangingParams mRangingParameters;
    private final android.ranging.params.SensorFusionParams mFusionParameters;

    private final android.ranging.params.DataNotificationConfig mDataNotificationConfig;
    private final boolean mIsAoaNeeded;

    private RangingPreference(Builder builder) {
        mDeviceRole = builder.mDeviceRole;
        mRangingParameters = builder.mRangingParameters;
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mFusionParameters = builder.mFusionParameters;
        mIsAoaNeeded = builder.mIsAoaNeeded;
    }

    private RangingPreference(Parcel in) {
        mDeviceRole = in.readInt();
        mRangingParameters = in.readParcelable(
                android.ranging.params.RangingParams.class.getClassLoader(),
                android.ranging.params.RangingParams.class);
        mFusionParameters = in.readParcelable(
                android.ranging.params.SensorFusionParams.class.getClassLoader(),
                android.ranging.params.SensorFusionParams.class);
        mDataNotificationConfig = in.readParcelable(
                android.ranging.params.DataNotificationConfig.class.getClassLoader(),
                android.ranging.params.DataNotificationConfig.class);
        mIsAoaNeeded = in.readBoolean();
    }

    @NonNull
    public static final Creator<RangingPreference> CREATOR = new Creator<>() {
        @Override
        public RangingPreference createFromParcel(Parcel in) {
            return new RangingPreference(in);
        }

        @Override
        public RangingPreference[] newArray(int size) {
            return new RangingPreference[size];
        }
    };

    /**
     * Returns the device role.
     */
    public int getDeviceRole() {
        return mDeviceRole;
    }

    /**
     * Returns whether Aoa was requested by the app.
     */
    public boolean isAoaNeeded() {
        return mIsAoaNeeded;
    }

    /**
     * Returns the ranging parameters associated with this preference.
     *
     * @return the {@link android.ranging.params.RangingParams} or {@code null} if not set.
     */
    @Nullable
    public android.ranging.params.RangingParams getRangingParameters() {
        return mRangingParameters;
    }

    /**
     * Returns the sensor fusion parameters used for this preference.
     *
     * @return a non-null {@link android.ranging.params.SensorFusionParams} instance.
     */
    @NonNull
    public android.ranging.params.SensorFusionParams getSensorFusionParameters() {
        return mFusionParameters;
    }

    /**
     * Returns the data notification configuration for this preference.
     *
     * @return a non-null {@link android.ranging.params.DataNotificationConfig} instance.
     */
    @NonNull
    public android.ranging.params.DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mDeviceRole);
        dest.writeParcelable(mRangingParameters, flags);
        dest.writeParcelable(mFusionParameters, flags);
        dest.writeParcelable(mDataNotificationConfig, flags);
        dest.writeBoolean(mIsAoaNeeded);
    }

    /**
     * Builder for creating instances of {@code RangingPreference}.
     */
    public static final class Builder {
        @DeviceRole
        private int mDeviceRole;
        private android.ranging.params.RangingParams mRangingParameters;
        private android.ranging.params.DataNotificationConfig mDataNotificationConfig;
        private android.ranging.params.SensorFusionParams mFusionParameters;
        private boolean mIsAoaNeeded = false;

        /**
         * Sets the role of the device within the session.
         *
         * @param role the role of the device, either {@link DEVICE_ROLE_RESPONDER or
         *             {@link DEVICE_ROLE_INITIATOR}.
         * @return this Builder instance.
         */
        @NonNull
        public Builder setDeviceRole(@DeviceRole int role) {
            mDeviceRole = role;
            return this;
        }

        /**
         * Sets the ranging parameters for this preference.
         *
         * @param rangingParameters the {@link android.ranging.params.RangingParams} to use.
         * @return the builder instance.
         * @throws IllegalArgumentException if the uwbParameters is null.
         */
        @NonNull
        public Builder setRangingParameters(
                @NonNull android.ranging.params.RangingParams rangingParameters) {
            mRangingParameters = rangingParameters;
            return this;
        }

        /**
         * Sets the sensor fusion parameters for this preference.
         *
         * @param parameters the {@link android.ranging.params.SensorFusionParams} to use.
         * @return the builder instance.
         * @throws IllegalArgumentException if the parameters is null.
         */
        @NonNull
        public Builder setSensorFusionParameters(
                @NonNull android.ranging.params.SensorFusionParams parameters) {
            mFusionParameters = parameters;
            return this;
        }

        /**
         * Sets the data notification configuration for this preference.
         *
         * @param config the {@link android.ranging.params.DataNotificationConfig} to use.
         * @return the builder instance for chaining.
         * @throws IllegalArgumentException if the config is null.
         */
        @NonNull
        public Builder setDataNotificationConfig(
                @NonNull android.ranging.params.DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }

        /**
         * Sets whether Angle of Arrival (AoA) is required for the ranging operation.
         *
         * @param isAoaNeeded {@code true} if AoA data is required; {@code false} otherwise.
         * @return The {@link Builder} instance.
         */
        @NonNull
        public Builder setAoaNeeded(boolean isAoaNeeded) {
            mIsAoaNeeded = isAoaNeeded;
            return this;
        }

        /**
         * Builds the {@code RangingPreference} instance.
         *
         * <p>If the {@link android.ranging.params.DataNotificationConfig} or
         * {@link android.ranging.params.SensorFusionParams}
         * are not set, default instances will be used.</p>
         *
         * @return a new {@code RangingPreference} instance.
         */
        @NonNull
        public RangingPreference build() {
            if (mDataNotificationConfig == null) {
                mDataNotificationConfig = new DataNotificationConfig.Builder().build();
            }
            if (mFusionParameters == null) {
                mFusionParameters = new SensorFusionParams.Builder().build();
            }
            return new RangingPreference(this);
        }

    }
}
