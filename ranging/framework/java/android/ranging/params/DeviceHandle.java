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
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.ITransportHandle;
import android.ranging.RangingDevice;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class DeviceHandle implements Parcelable {

    private final RangingDevice mRangingDevice;

    private final ITransportHandle mTransportHandle;

    private DeviceHandle(Builder builder) {
        mRangingDevice = builder.mRangingDevice;
        mTransportHandle = builder.mTransportHandle;
    }

    protected DeviceHandle(Parcel in) {
        mRangingDevice = in.readParcelable(RangingDevice.class.getClassLoader());
        // Not need in service layer.
        mTransportHandle = null;
    }

    public static final Creator<DeviceHandle> CREATOR = new Creator<DeviceHandle>() {
        @Override
        public DeviceHandle createFromParcel(Parcel in) {
            return new DeviceHandle(in);
        }

        @Override
        public DeviceHandle[] newArray(int size) {
            return new DeviceHandle[size];
        }
    };

    public RangingDevice getRangingDevice() {
        return mRangingDevice;
    }

    @Nullable
    public ITransportHandle getTransportHandle() {
        return mTransportHandle;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mRangingDevice, flags);
    }

    public static final class Builder {
        private RangingDevice mRangingDevice;
        private ITransportHandle mTransportHandle;

        public Builder setRangingDevice(RangingDevice rangingDevice) {
            mRangingDevice = rangingDevice;
            return this;
        }

        public Builder setTransportHandle(ITransportHandle transportHandle) {
            mTransportHandle = transportHandle;
            return this;
        }

        public DeviceHandle build() {
            return new DeviceHandle(this);
        }
    }
}
