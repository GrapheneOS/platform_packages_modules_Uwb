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
 * Represents the parameters for an Out-of-Band (OOB) responder in a ranging session.
 * This class contains configuration and device handle information for establishing
 * a ranging session with an initiator.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class OobResponderRangingParams extends RangingParams implements Parcelable {

    private final DeviceHandle mDeviceHandle;

    private OobResponderRangingParams(Builder builder) {
        setRangingSessionType(RangingParams.RANGING_SESSION_OOB);
        mDeviceHandle = builder.mDeviceHandle;
    }


    protected OobResponderRangingParams(Parcel in) {
        setRangingSessionType(in.readInt());
        mDeviceHandle = in.readParcelable(DeviceHandle.class.getClassLoader(), DeviceHandle.class);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(getRangingSessionType());
        dest.writeParcelable(mDeviceHandle, flags);
    }

    @NonNull
    public static final Creator<OobResponderRangingParams> CREATOR =
            new Creator<OobResponderRangingParams>() {
                @Override
                public OobResponderRangingParams createFromParcel(Parcel in) {
                    return new OobResponderRangingParams(in);
                }

                @Override
                public OobResponderRangingParams[] newArray(int size) {
                    return new OobResponderRangingParams[size];
                }
            };

    /**
     * Returns the DeviceHandle associated with this OOB responder.
     *
     * @return The DeviceHandle of the OOB responder.
     */
    @NonNull
    public DeviceHandle getDeviceHandle() {
        return mDeviceHandle;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Builder class for creating instances of {@link OobResponderRangingParams}.
     */
    public static final class Builder {
        private final DeviceHandle mDeviceHandle;

        /**
         * Constructs a new Builder instance with the specified DeviceHandle.
         *
         * @param deviceHandle The DeviceHandle to associate with this OOB responder.
         */
        public Builder(DeviceHandle deviceHandle) {
            mDeviceHandle = deviceHandle;
        }

        /**
         * Builds an instance of {@link OobResponderRangingParams} with the provided parameters.
         *
         * @return A new OobResponderRangingParams instance.
         */
        @NonNull
        public OobResponderRangingParams build() {
            return new OobResponderRangingParams(this);
        }
    }
}
