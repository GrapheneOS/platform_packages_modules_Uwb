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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

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

    private final RangingParams mRangingParameters;
    private final SensorFusionParams mFusionParameters;

    private final DataNotificationConfig mDataNotificationConfig;

    private RangingPreference(Builder builder) {
        mRangingParameters = builder.mRangingParameters;
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mFusionParameters = builder.mFusionParameters;
    }

    private RangingPreference(Parcel in) {
        mRangingParameters = in.readParcelable(
                RangingParams.class.getClassLoader(), RangingParams.class);
        mFusionParameters = in.readParcelable(
                SensorFusionParams.class.getClassLoader(), SensorFusionParams.class);
        mDataNotificationConfig = in.readParcelable(
                DataNotificationConfig.class.getClassLoader(), DataNotificationConfig.class);
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
     * Returns the ranging parameters associated with this preference.
     *
     * @return the {@link RangingParameters} or {@code null} if not set.
     */
    @Nullable
    public RangingParams getRangingParameters() {
        return mRangingParameters;
    }

    /**
     * Returns the sensor fusion parameters used for this preference.
     *
     * @return a non-null {@link SensorFusionParams} instance.
     */
    @NonNull
    public SensorFusionParams getSensorFusionParameters() {
        return mFusionParameters;
    }

    /**
     * Returns the data notification configuration for this preference.
     *
     * @return a non-null {@link DataNotificationConfig} instance.
     */
    @NonNull
    public DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mRangingParameters, flags);
        dest.writeParcelable(mFusionParameters, flags);
        dest.writeParcelable(mDataNotificationConfig, flags);
    }

    /**
     * Builder for creating instances of {@code RangingPreference}.
     */
    public static final class Builder {
        private RangingParams mRangingParameters;
        private DataNotificationConfig mDataNotificationConfig;
        private SensorFusionParams mFusionParameters;

        /**
         * Sets the ranging parameters for this preference.
         *
         * @param rangingParameters the {@link RangingParameters} to use.
         * @return the builder instance.
         *
         * @throws IllegalArgumentException if the uwbParameters is null.
         */
        @NonNull
        public Builder setRangingParameters(@NonNull RangingParams rangingParameters) {
            mRangingParameters = rangingParameters;
            return this;
        }

        /**
         * Sets the sensor fusion parameters for this preference.
         *
         * @param parameters the {@link SensorFusionParams} to use.
         * @return the builder instance.
         *
         * @throws IllegalArgumentException if the parameters is null.
         */
        @NonNull
        public Builder setSensorFusionParameters(@NonNull SensorFusionParams parameters) {
            mFusionParameters = parameters;
            return this;
        }

        /**
         * Sets the data notification configuration for this preference.
         *
         * @param config the {@link DataNotificationConfig} to use.
         * @return the builder instance for chaining.
         *
         * @throws IllegalArgumentException if the config is null.
         */
        @NonNull
        public Builder setDataNotificationConfig(@NonNull DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }

        /**
         * Builds the {@code RangingPreference} instance.
         *
         * <p>If the {@link DataNotificationConfig} or {@link SensorFusionParams}
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
