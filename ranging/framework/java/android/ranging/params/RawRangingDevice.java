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
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingDevice;
import android.ranging.cs.CsRangingParams;
import android.ranging.rtt.RttRangingParams;
import android.ranging.uwb.UwbRangingParams;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents a device participating in ranging operations.
 * This class supports multiple ranging technologies, including UWB, CS, and RTT.
 * The configuration for each technology is provided through corresponding parameter objects.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RawRangingDevice implements Parcelable {

    /**
     * Defines the configuration IDs for different ranging scenarios.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            UPDATE_RATE_NORMAL,
            UPDATE_RATE_INFREQUENT,
            UPDATE_RATE_FAST,
    })
    public @interface RangingUpdateRate {
    }

    /** Ranging interval between 200ms - 240ms for UWB, 2 seconds for BT-CS. */
    public static final int UPDATE_RATE_NORMAL = 1;
    /** Ranging interval between 600ms - 800ms for UWB, 5 seconds for BT-CS. */
    public static final int UPDATE_RATE_INFREQUENT = 2;
    /** Ranging interval between 100ms - 200ms for UWB, 1 second for BT-CS. */
    public static final int UPDATE_RATE_FAST = 3;
    private final RangingDevice mRangingDevice;
    private final UwbRangingParams mUwbRangingParams;
    private final CsRangingParams mCsRangingParams;
    private final RttRangingParams mRttRangingParams;


    private RawRangingDevice(Builder builder) {
        mRangingDevice = builder.mRangingDevice;
        mUwbRangingParams = builder.mUwbRangingParams;
        mCsRangingParams = builder.mCsRangingParams;
        mRttRangingParams = builder.mRttRangingParams;
    }


    private RawRangingDevice(Parcel in) {
        mRangingDevice = in.readParcelable(RangingDevice.class.getClassLoader(), RangingDevice.class
        );
        mUwbRangingParams = in.readParcelable(UwbRangingParams.class.getClassLoader(),
                UwbRangingParams.class);
        mCsRangingParams = in.readParcelable(CsRangingParams.class.getClassLoader(),
                CsRangingParams.class
        );
        mRttRangingParams = in.readParcelable(RttRangingParams.class.getClassLoader(),
                RttRangingParams.class);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mRangingDevice, flags);
        dest.writeParcelable(mUwbRangingParams, flags);
        dest.writeParcelable(mCsRangingParams, flags);
        dest.writeParcelable(mRttRangingParams, flags);
    }

    public static final Creator<RawRangingDevice> CREATOR = new Creator<RawRangingDevice>() {
        @Override
        public RawRangingDevice createFromParcel(Parcel in) {
            return new RawRangingDevice(in);
        }

        @Override
        public RawRangingDevice[] newArray(int size) {
            return new RawRangingDevice[size];
        }
    };

    /**
     * Returns the {@link RangingDevice} associated with this instance.
     *
     * @return the ranging device.
     */
    public RangingDevice getRangingDevice() {
        return mRangingDevice;
    }

    /**
     * Returns the UWB ranging parameters, if applicable.
     *
     * @return the {@link UwbRangingParams}, or {@code null} if not set.
     */
    @Nullable
    public UwbRangingParams getUwbRangingParams() {
        return mUwbRangingParams;
    }

    /**
     * Returns the CS ranging parameters, if applicable.
     *
     * @return the {@link CsRangingParams}, or {@code null} if not set.
     */
    @Nullable
    public CsRangingParams getCsRangingParams() {
        return mCsRangingParams;
    }

    /**
     * Returns the RTT ranging parameters, if applicable.
     *
     * @return the {@link RttRangingParams}, or {@code null} if not set.
     */
    @Nullable
    public RttRangingParams getRttRangingParams() {
        return mRttRangingParams;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Builder class for creating instances of {@link RawRangingDevice}.
     */
    public static final class Builder {
        private RangingDevice mRangingDevice;
        private UwbRangingParams mUwbRangingParams;
        private CsRangingParams mCsRangingParams;
        private RttRangingParams mRttRangingParams;

        /**
         * Sets the ranging device.
         *
         * @param rangingDevice the {@link RangingDevice} to be set.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setRangingDevice(RangingDevice rangingDevice) {
            mRangingDevice = rangingDevice;
            return this;
        }

        /**
         * Sets the UWB ranging parameters.
         *
         * @param params the {@link UwbRangingParams} to be set.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setUwbRangingParams(UwbRangingParams params) {
            mUwbRangingParams = params;
            return this;
        }

        /**
         * Sets the RTT ranging parameters.
         *
         * @param params the {@link RttRangingParams} to be set.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setRttRangingParams(RttRangingParams params) {
            mRttRangingParams = params;
            return this;
        }

        /**
         * Sets the CS ranging parameters.
         *
         * @param params the {@link CsRangingParams} to be set.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setCsRangingParams(CsRangingParams params) {
            mCsRangingParams = params;
            return this;
        }

        /**
         * Builds and returns a new {@link RawRangingDevice} instance.
         *
         * @return a new {@link RawRangingDevice} configured with the specified parameters.
         */
        @NonNull
        public RawRangingDevice build() {
            return new RawRangingDevice(this);
        }
    }
}
