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
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingMeasurement implements Parcelable {
    private final double mMeasurement;

    //TODO: Add values once decided.
    private final int mConfidence;

    private RangingMeasurement(Builder builder) {
        mMeasurement = builder.mMeasurement;
        mConfidence = builder.mConfidence;
    }

    protected RangingMeasurement(Parcel in) {
        mMeasurement = in.readDouble();
        mConfidence = in.readInt();
    }

    public static final Creator<RangingMeasurement> CREATOR = new Creator<RangingMeasurement>() {
        @Override
        public RangingMeasurement createFromParcel(Parcel in) {
            return new RangingMeasurement(in);
        }

        @Override
        public RangingMeasurement[] newArray(int size) {
            return new RangingMeasurement[size];
        }
    };

    public double getMeasurement() {
        return mMeasurement;
    }

    public int getConfidence() {
        return mConfidence;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeDouble(mMeasurement);
        dest.writeInt(mConfidence);
    }

    public static final class Builder {
        private double mMeasurement = 0.0;
        private int mConfidence = 1;

        public Builder setMeasurement(double measurement) {
            mMeasurement = measurement;
            return this;
        }

        public Builder setConfidence(int confidence) {
            mConfidence = confidence;
            return this;
        }
    }
}
