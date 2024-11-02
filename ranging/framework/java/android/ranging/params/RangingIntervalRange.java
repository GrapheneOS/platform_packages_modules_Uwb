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

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingIntervalRange implements Parcelable {

    private final int mFastestRangingIntervalMs;

    private final int mSlowestRangingIntervalMs;

    private RangingIntervalRange(Builder builder) {
        this.mFastestRangingIntervalMs = builder.mFastestRangingIntervalMs;
        this.mSlowestRangingIntervalMs = builder.mSlowestRangingIntervalMs;

        if (mFastestRangingIntervalMs > mSlowestRangingIntervalMs) {
            throw new IllegalArgumentException(
                    "Fastest ranging interval cannot be greater than the slowest interval."
            );
        }
    }

    protected RangingIntervalRange(Parcel in) {
        mFastestRangingIntervalMs = in.readInt();
        mSlowestRangingIntervalMs = in.readInt();
    }

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

    public int getFastestRangingIntervalMs() {
        return mFastestRangingIntervalMs;
    }

    public int getSlowestRangingIntervalMs() {
        return mSlowestRangingIntervalMs;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mFastestRangingIntervalMs);
        dest.writeInt(mSlowestRangingIntervalMs);
    }

    public static final class Builder {
        private int mFastestRangingIntervalMs;
        private int mSlowestRangingIntervalMs;

        public Builder setFastestRangingIntervalMs(int intervalMs) {
            this.mFastestRangingIntervalMs = intervalMs;
            return this;
        }

        public Builder setSlowestRangingIntervalMs(int intervalMs) {
            this.mSlowestRangingIntervalMs = intervalMs;
            return this;
        }

        public RangingIntervalRange build() {
            return new RangingIntervalRange(this);
        }
    }
}
