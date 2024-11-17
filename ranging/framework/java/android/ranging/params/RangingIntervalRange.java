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

package android.ranging.params;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

import java.time.Duration;

/**
 * Represents a range for the fastest and slowest intervals in milliseconds between
 * successive ranging operations.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingIntervalRange implements Parcelable {

    private final Duration mFastestRangingInterval;

    private final Duration mSlowestRangingInterval;

    private RangingIntervalRange(Builder builder) {
        this.mFastestRangingInterval = builder.mFastestRangingInterval;
        this.mSlowestRangingInterval = builder.mSlowestRangingInterval;

        if (mFastestRangingInterval.toMillis() > mSlowestRangingInterval.toMillis()) {
            throw new IllegalArgumentException(
                    "Fastest ranging interval cannot be greater than the slowest interval."
            );
        }
    }

    protected RangingIntervalRange(Parcel in) {
        mFastestRangingInterval = Duration.ofMillis(in.readLong());
        mSlowestRangingInterval = Duration.ofMillis(in.readLong());
    }

    @NonNull
    public static final Creator<RangingIntervalRange> CREATOR =
            new Creator<RangingIntervalRange>() {
                @Override
                public RangingIntervalRange createFromParcel(Parcel in) {
                    return new RangingIntervalRange(in);
                }

                @Override
                public RangingIntervalRange[] newArray(int size) {
                    return new RangingIntervalRange[size];
                }
            };

    /**
     * Returns the fastest requested ranging interval in milliseconds.
     *
     * @return The fastest interval in milliseconds.
     */
    public Duration getFastestRangingInterval() {
        return mFastestRangingInterval;
    }

    /**
     * Returns the slowest acceptable ranging interval in milliseconds.
     *
     * @return The slowest interval in milliseconds.
     */
    public Duration getSlowestRangingInterval() {
        return mSlowestRangingInterval;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mFastestRangingInterval.toMillis());
        dest.writeLong(mSlowestRangingInterval.toMillis());
    }

    /**
     * Builder class for creating instances of {@link RangingIntervalRange}.
     */
    public static final class Builder {
        private Duration mFastestRangingInterval = Duration.ofMillis(100);
        private Duration mSlowestRangingInterval = Duration.ofMillis(5000);

        /**
         * Sets the fastest ranging interval in milliseconds.
         *
         * @param intervalMs The fastest interval in milliseconds.
         *                   Defaults to 100ms
         * @return The Builder instance, for chaining calls.
         */
        @NonNull
        public Builder setFastestRangingInterval(Duration intervalMs) {
            this.mFastestRangingInterval = intervalMs;
            return this;
        }

        /**
         * Sets the slowest ranging interval in milliseconds.
         *
         * @param intervalMs The slowest interval in milliseconds.
         *                   Defaults to 5000ms
         * @return The Builder instance, for chaining calls.
         */
        @NonNull
        public Builder setSlowestRangingInterval(Duration intervalMs) {
            this.mSlowestRangingInterval = intervalMs;
            return this;
        }

        /**
         * Builds an instance of {@link RangingIntervalRange} with the provided parameters.
         *
         * @return A new RangingIntervalRange instance.
         */
        @NonNull
        public RangingIntervalRange build() {
            return new RangingIntervalRange(this);
        }
    }
}
