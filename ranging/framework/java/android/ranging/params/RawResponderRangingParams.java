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
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

/**
 * Represents the parameters for a raw ranging session initiated by a responder device.
 * This class holds a {@link RawRangingDevice} object that participates in the session.
 *
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RawResponderRangingParams extends RangingParams implements Parcelable {

    private final RawRangingDevice mRawRangingDevice;

    private RawResponderRangingParams(Builder builder) {
        setRangingSessionType(RangingParams.RANGING_SESSION_RAW);
        mRawRangingDevice = builder.mRawRangingDevice;
    }

    private RawResponderRangingParams(Parcel in) {
        setRangingSessionType(in.readInt());
        mRawRangingDevice = in.readParcelable(RawRangingDevice.class.getClassLoader());
    }

    @NonNull
    public static final Creator<RawResponderRangingParams> CREATOR =
            new Creator<RawResponderRangingParams>() {
                @Override
                public RawResponderRangingParams createFromParcel(Parcel in) {
                    return new RawResponderRangingParams(in);
                }

                @Override
                public RawResponderRangingParams[] newArray(int size) {
                    return new RawResponderRangingParams[size];
                }
            };

    /**
     * Returns the {@link RawRangingDevice} participating in this session as the responder.
     *
     * @return the raw ranging device.
     */
    @NonNull
    public RawRangingDevice getRawRangingDevice() {
        return mRawRangingDevice;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(getRangingSessionType());
        dest.writeParcelable(mRawRangingDevice, flags);
    }

    /**
     * Builder class for constructing instances of {@link RawResponderRangingParams}.
     */
    public static final class Builder {
        private RawRangingDevice mRawRangingDevice;

        /**
         * Sets the {@link RawRangingDevice} for this responder session.
         *
         * @param rangingDevice the raw ranging device.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setRawRangingDevice(@NonNull RawRangingDevice rangingDevice) {
            mRawRangingDevice = rangingDevice;
            return this;
        }

        /**
         * Builds and returns a new {@link RawResponderRangingParams} instance.
         *
         * @return a configured instance of {@link RawResponderRangingParams}.
         */
        @NonNull
        public RawResponderRangingParams build() {
            return new RawResponderRangingParams(this);
        }
    }
}




