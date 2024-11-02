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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the parameters for initiating a raw ranging session.
 * This class encapsulates a list of {@link RawRangingDevice} objects that participate in the
 * session.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RawInitiatorRangingParams extends RangingParams implements Parcelable {

    private final List<RawRangingDevice> mRawRangingDevices;

    private RawInitiatorRangingParams(Builder builder) {
        mRangingSessionType = RangingParams.RANGING_SESSION_RAW;
        mRawRangingDevices = new ArrayList<>(builder.mRawRangingDeviceList);
    }

    protected RawInitiatorRangingParams(Parcel in) {
        mRangingSessionType = in.readInt();
        mRawRangingDevices = in.createTypedArrayList(RawRangingDevice.CREATOR);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRangingSessionType);
        dest.writeTypedList(mRawRangingDevices);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<RawInitiatorRangingParams> CREATOR =
            new Creator<RawInitiatorRangingParams>() {
                @Override
                public RawInitiatorRangingParams createFromParcel(Parcel in) {
                    return new RawInitiatorRangingParams(in);
                }

                @Override
                public RawInitiatorRangingParams[] newArray(int size) {
                    return new RawInitiatorRangingParams[size];
                }
            };

    /**
     * Returns the list of {@link RawRangingDevice} objects involved in this session.
     *
     * @return a list of ranging devices.
     */
    public List<RawRangingDevice> getRawRangingDevices() {
        return mRawRangingDevices;
    }

    /**
     * Builder class for constructing instances of {@link RawInitiatorRangingParams}.
     */
    public static final class Builder {
        private final List<RawRangingDevice> mRawRangingDeviceList = new ArrayList<>();

        /**
         * Adds a {@link RawRangingDevice} to the list of devices for this session.
         *
         * @param rangingDevice the device to be added.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder addRawRangingDevice(RawRangingDevice rangingDevice) {
            mRawRangingDeviceList.add(rangingDevice);
            return this;
        }

        /**
         * Builds and returns a new {@link RawInitiatorRangingParams} instance.
         *
         * @return a configured instance of {@link RawInitiatorRangingParams}.
         */
        @NonNull
        public RawInitiatorRangingParams build() {
            return new RawInitiatorRangingParams(this);
        }
    }
}
