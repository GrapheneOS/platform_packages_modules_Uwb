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

import java.util.Objects;

/**
 * Represents ranging data, including distance, azimuth, elevation, and RSSI measurements,
 * along with the ranging technology used and a timestamp.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingData implements Parcelable {

    @RangingManager.RangingTechnology
    private final int mRangingTechnology;
    private final RangingMeasurement mDistance;
    @Nullable private final RangingMeasurement mAzimuth;
    @Nullable private final RangingMeasurement mElevation;
    private final int mRssi;
    private final long mTimestamp;

    private RangingData(Builder builder) {
        mRangingTechnology = builder.mRangingTechnology;
        mDistance = builder.mDistance;
        mAzimuth = builder.mAzimuth;
        mElevation = builder.mElevation;
        mRssi = builder.mRssi;
        mTimestamp = builder.mTimestamp;
    }

    private RangingData(Parcel in) {
        mRangingTechnology = in.readInt();
        mDistance = Objects.requireNonNull(
                in.readParcelable(
                        RangingMeasurement.class.getClassLoader(), RangingMeasurement.class));
        mAzimuth = in.readParcelable(
                RangingMeasurement.class.getClassLoader(), RangingMeasurement.class);
        mElevation = in.readParcelable(
                RangingMeasurement.class.getClassLoader(),
                RangingMeasurement.class);
        mRssi = in.readInt();
        mTimestamp = in.readLong();
    }

    @NonNull
    public static final Creator<RangingData> CREATOR = new Creator<RangingData>() {
        @Override
        public RangingData createFromParcel(Parcel in) {
            return new RangingData(in);
        }

        @Override
        public RangingData[] newArray(int size) {
            return new RangingData[size];
        }
    };

    /**
     * Returns the ranging technology used.
     *
     * @return The ranging technology as an integer.
     */
    @RangingManager.RangingTechnology
    public int getRangingTechnology() {
        return mRangingTechnology;
    }

    /**
     * Returns the distance measurement.
     *
     * @return The {@link RangingMeasurement} representing the distance.
     */
    @Nullable
    public RangingMeasurement getDistance() {
        return mDistance;
    }

    /**
     * Returns the azimuth measurement, or {@code null} if not available.
     *
     * @return The {@link RangingMeasurement} representing the azimuth, or {@code null}.
     */
    @Nullable
    public RangingMeasurement getAzimuth() {
        return mAzimuth;
    }

    /**
     * Returns the elevation measurement, or {@code null} if not available.
     *
     * @return The {@link RangingMeasurement} representing the elevation, or {@code null}.
     */
    @Nullable
    public RangingMeasurement getElevation() {
        return mElevation;
    }

    /**
     * Returns the RSSI (Received Signal Strength Indicator) value.
     *
     * @return The RSSI value as an integer.
     */
    public int getRssi() {
        return mRssi;
    }

    /**
     * Returns the timestamp of when the ranging data was collected.
     *
     * @return The timestamp in milliseconds.
     */
    public long getTimestamp() {
        return mTimestamp;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mRangingTechnology);
        dest.writeParcelable(mDistance, flags);
        dest.writeParcelable(mAzimuth, flags);
        dest.writeParcelable(mElevation, flags);
        dest.writeInt(mRssi);
        dest.writeLong(mTimestamp);
    }

    /**
     * Builder class for creating instances of {@link RangingData}.
     */
    public static final class Builder {
        @RangingManager.RangingTechnology
        private int mRangingTechnology;
        private RangingMeasurement mDistance;
        private RangingMeasurement mAzimuth;
        private RangingMeasurement mElevation;
        private int mRssi;
        private long mTimestamp;

        /**
         * Sets the ranging technology.
         *
         * @param rangingTechnology The ranging technology used.
         * @return This {@link Builder} instance.
         */
        @NonNull
        public Builder setRangingTechnology(int rangingTechnology) {
            mRangingTechnology = rangingTechnology;
            return this;
        }

        /**
         * Sets the distance measurement.
         *
         * @param distance The {@link RangingMeasurement} representing the distance.
         * @return This {@link Builder} instance.
         *
         * @throws IllegalArgumentException if the provided measurement is null.
         */
        @NonNull
        public Builder setDistance(@NonNull RangingMeasurement distance) {
            mDistance = distance;
            return this;
        }

        /**
         * Sets the azimuth measurement.
         *
         * @param azimuth The {@link RangingMeasurement} representing the azimuth.
         * @return This {@link Builder} instance.
         *
         * @throws IllegalArgumentException if the provided measurement is null.
         */
        @NonNull
        public Builder setAzimuth(@NonNull RangingMeasurement azimuth) {
            mAzimuth = azimuth;
            return this;
        }

        /**
         * Sets the elevation measurement.
         *
         * @param elevation The {@link RangingMeasurement} representing the elevation.
         * @return This {@link Builder} instance.
         *
         * @throws IllegalArgumentException if the provided measurement is null.
         */
        @NonNull
        public Builder setElevation(@NonNull RangingMeasurement elevation) {
            mElevation = elevation;
            return this;
        }

        /**
         * Sets the RSSI value.
         *
         * @param rssi The RSSI value as an integer.
         * @return This {@link Builder} instance.
         */
        @NonNull
        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        /**
         * Sets the timestamp of the ranging data.
         *
         * @param timestamp The timestamp in milliseconds.
         * @return This {@link Builder} instance.
         */
        @NonNull
        public Builder setTimestamp(long timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        /**
         * Builds and returns a new instance of {@link RangingData}.
         *
         * @return A new {@link RangingData} instance.
         */
        @NonNull
        public RangingData build() {
            return new RangingData(this);
        }
    }
}
