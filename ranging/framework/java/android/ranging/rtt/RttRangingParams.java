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

package android.ranging.rtt;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.params.RawRangingDevice;

import com.android.ranging.flags.Flags;

/**
 * Represents the parameters required to perform Wi-Fi Round Trip Time (RTT) ranging.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_RTT_ENABLED)
public class RttRangingParams implements Parcelable {

    private RttRangingParams(Parcel in) {
        mServiceName = in.readString();
        mMatchFilter = in.createByteArray();
        mRangingUpdateRate = in.readInt();
    }

    public static final Creator<RttRangingParams> CREATOR = new Creator<RttRangingParams>() {
        @Override
        public RttRangingParams createFromParcel(Parcel in) {
            return new RttRangingParams(in);
        }

        @Override
        public RttRangingParams[] newArray(int size) {
            return new RttRangingParams[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mServiceName);
        dest.writeByteArray(mMatchFilter);
        dest.writeInt(mRangingUpdateRate);
    }

    private final String mServiceName;

    private final byte[] mMatchFilter;

    @RawRangingDevice.RangingUpdateRate
    private final int mRangingUpdateRate;


    /**
     * Returns the service name associated with this RTT ranging session.
     *
     * @return the service name as a {@link String}.
     */
    public String getServiceName() {
        return mServiceName;
    }

    /**
     * Returns the match filter.
     *
     * @return a byte array representing the match filter.
     */
    public byte[] getMatchFilter() {
        return mMatchFilter;
    }

    @RawRangingDevice.RangingUpdateRate
    public int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    private RttRangingParams(Builder builder) {
        mServiceName = builder.mServiceName;
        mMatchFilter = builder.mMatchFilter;
        mRangingUpdateRate = builder.mRangingUpdateRate;
    }

    /**
     * Builder class for {@link RttRangingParams}.
     */
    public static final class Builder {
        private String mServiceName = "";

        private byte[] mMatchFilter = null;
        @RawRangingDevice.RangingUpdateRate
        private int mRangingUpdateRate;

        /**
         * Sets the service name for the RTT session.
         *
         * @param serviceName the service name to be set.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setServiceName(@NonNull String serviceName) {
            if (serviceName != null) {
                this.mServiceName = serviceName;
            }
            return this;
        }

        /**
         * Sets the match filter to identify specific devices or services for RTT.
         *
         * @param matchFilter a byte array representing the filter. If {@code null}, it will be
         *                    ignored.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setMatchFilter(@NonNull byte[] matchFilter) {
            if (matchFilter != null) {
                this.mMatchFilter = matchFilter.clone();
            }
            return this;
        }

        /**
         * Sets the update rate for the RTT ranging session.
         *
         * @param updateRate the update rate, as defined by
         *                   {@link RawRangingDevice.RangingUpdateRate}.
         * @return this {@link Builder} instance for chaining calls.
         */
        @NonNull
        public Builder setRangingUpdateRate(@RawRangingDevice.RangingUpdateRate int updateRate) {
            mRangingUpdateRate = updateRate;
            return this;
        }

        /**
         * Builds and returns a new {@link RttRangingParams} instance.
         *
         * @return a new {@link RttRangingParams} object configured with the provided parameters.
         */
        @NonNull
        public RttRangingParams build() {
            return new RttRangingParams(this);
        }
    }

}
