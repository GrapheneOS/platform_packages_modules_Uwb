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

package android.ranging.oob;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingParams;

import com.android.ranging.flags.Flags;

/**
 * Represents the parameters for an Out-of-Band (OOB) responder in a ranging session.
 * This class contains configuration and device handle information for establishing
 * a ranging session with an initiator.
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class OobResponderRangingParams extends RangingParams implements Parcelable {

    private final android.ranging.oob.DeviceHandle mDeviceHandle;

    private OobResponderRangingParams(Builder builder) {
        setRangingSessionType(RangingParams.RANGING_SESSION_OOB);
        mDeviceHandle = builder.mDeviceHandle;
    }


    private OobResponderRangingParams(Parcel in) {
        setRangingSessionType(in.readInt());
        mDeviceHandle = in.readParcelable(
                DeviceHandle.class.getClassLoader(), android.ranging.oob.DeviceHandle.class);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
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
    public android.ranging.oob.DeviceHandle getDeviceHandle() {
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
        private final android.ranging.oob.DeviceHandle mDeviceHandle;

        /**
         * Constructs a new Builder instance with the specified DeviceHandle.
         *
         * @param deviceHandle The DeviceHandle to associate with this OOB responder.
         */
        public Builder(@NonNull android.ranging.oob.DeviceHandle deviceHandle) {
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

    @Override
    public String toString() {
        return "OobResponderRangingParams{ "
                + "mDeviceHandle="
                + mDeviceHandle
                + ", "
                + super.toString()
                + ", "
                + " }";
    }
}
