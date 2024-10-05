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
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

import java.util.Objects;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingData implements Parcelable {

    @RangingManager.RangingTechnology
    private final int mRangingTechnology;
    private final RangingMeasurement mDistance;
    @Nullable private final RangingMeasurement mAzimuth;
    @Nullable private final RangingMeasurement mElevation;
    private final int mRssi;
    private final long mTimestamp;

    public RangingData(Builder builder) {
        mRangingTechnology = builder.mRangingTechnology;
        mDistance = builder.mDistance;
        mAzimuth = builder.mAzimuth;
        mElevation = builder.mElevation;
        mRssi = builder.mRssi;
        mTimestamp = builder.mTimestamp;
    }

    protected RangingData(Parcel in) {
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

    public int getRangingTechnology() {
        return mRangingTechnology;
    }

    public RangingMeasurement getDistance() {
        return mDistance;
    }

    public @Nullable RangingMeasurement getAzimuth() {
        return mAzimuth;
    }

    public @Nullable RangingMeasurement getElevation() {
        return mElevation;
    }

    public int getRssi() {
        return mRssi;
    }

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

    public static final class Builder {
        @RangingManager.RangingTechnology
        private int mRangingTechnology;
        private RangingMeasurement mDistance;
        private RangingMeasurement mAzimuth;
        private RangingMeasurement mElevation;
        private int mRssi;
        private long mTimestamp;

        public Builder setRangingTechnology(int rangingTechnology) {
            mRangingTechnology = rangingTechnology;
            return this;
        }

        public Builder setDistance(RangingMeasurement distance) {
            mDistance = distance;
            return this;
        }

        public Builder setAzimuth(RangingMeasurement azimuth) {
            mAzimuth = azimuth;
            return this;
        }

        public Builder setElevation(RangingMeasurement elevation) {
            mElevation = elevation;
            return this;
        }

        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        public Builder setTimestamp(long timestamp) {
            mTimestamp = timestamp;
            return this;
        }
    }
}
