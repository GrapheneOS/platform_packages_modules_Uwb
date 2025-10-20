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

package android.ranging.ble.cs;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

import java.util.Objects;

/**
 * Represents the {@link android.ranging.RangingManager.RangingTechnology#BLE_CS} specific data.
 * This is generally used by algorithms for fine tuning ranging data.
 *
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class BleCsSpecificData implements Parcelable {

    private final double mDelaySpreadMeters;

    private BleCsSpecificData(Builder builder) {
        mDelaySpreadMeters = builder.mDelaySpreadMeters;
    }

    private BleCsSpecificData(Parcel in) {
        mDelaySpreadMeters = in.readDouble();
    }

    @NonNull
    public static final Creator<BleCsSpecificData> CREATOR =
            new Creator<>() {
                @Override
                public BleCsSpecificData createFromParcel(Parcel in) {
                    return new BleCsSpecificData(in);
                }

                @Override
                public BleCsSpecificData[] newArray(int size) {
                    return new BleCsSpecificData[size];
                }
            };

    /**
     * Gets the delay spread in meters.
     *
     * @return the delay spread in meters
     */
    public double getDelaySpreadMeters() {
        return mDelaySpreadMeters;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeDouble(mDelaySpreadMeters);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BleCsSpecificData)) return false;
        BleCsSpecificData that = (BleCsSpecificData) o;
        return Double.compare(that.mDelaySpreadMeters, mDelaySpreadMeters) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDelaySpreadMeters);
    }

    @Override
    public String toString() {
        return "BleCsSpecificData: "
                + "DelaySpreadMeters=" + mDelaySpreadMeters;
    }

    /**
     * Builder class for creating instances of {@link BleCsSpecificData}.
     *
     * @hide
     */
    public static final class Builder {
        private double mDelaySpreadMeters = Double.NaN;

        /**
         * Sets delay spread in meters.
         *
         * @param delaySpreadMeters the delay spread in meters
         * @return the Builder instance
         */
        @NonNull
        public Builder setDelaySpreadMeters(double delaySpreadMeters) {
            mDelaySpreadMeters = delaySpreadMeters;
            return this;
        }

        /**
         * Build additional ranging data.
         *
         * @return the additional ranging data
         */
        @NonNull
        public BleCsSpecificData build() {
            return new BleCsSpecificData(this);
        }
    }
}
