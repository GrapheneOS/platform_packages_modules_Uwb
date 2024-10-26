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
import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingDevice;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_RTT_ENABLED)
public class RttRangingParams implements Parcelable {

    protected RttRangingParams(Parcel in) {
        mDeviceRole = in.readInt();
        mPeerDevice = in.readParcelable(RangingDevice.class.getClassLoader());
        mServiceName = in.readString();
        mMatchFilter = in.createByteArray();
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
        dest.writeInt(mDeviceRole);
        dest.writeParcelable(mPeerDevice, flags);
        dest.writeString(mServiceName);
        dest.writeByteArray(mMatchFilter);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DEVICE_ROLE_PUBLISHER,
            DEVICE_ROLE_SUBSCRIBER,
    })
    public @interface DeviceRole {
    }

    public static final int DEVICE_ROLE_PUBLISHER = 0;
    /** The device that initiates the session. */
    public static final int DEVICE_ROLE_SUBSCRIBER = 1;
    @DeviceRole
    private final int mDeviceRole;

    private final RangingDevice mPeerDevice;

    private final String mServiceName;

    private final byte[] mMatchFilter;

    public int getDeviceRole() {
        return mDeviceRole;
    }

    public RangingDevice getPeerDevice() {
        return mPeerDevice;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public byte[] getMatchFilter() {
        return mMatchFilter;
    }

    private RttRangingParams(Builder builder) {
        mDeviceRole = builder.mDeviceRole;
        mPeerDevice = builder.mPeerDevice;
        mServiceName = builder.mServiceName;
        mMatchFilter = builder.mMatchFilter;
    }

    public static final class Builder {
        @DeviceRole
        private int mDeviceRole = DEVICE_ROLE_PUBLISHER;
        private RangingDevice mPeerDevice;
        private String mServiceName = "";

        private byte[] mMatchFilter = new byte[0];

        public Builder setDeviceRole(@DeviceRole int deviceRole) {
            this.mDeviceRole = deviceRole;
            return this;
        }

        public Builder setPeerDevice(RangingDevice peerDevice) {
            this.mPeerDevice = peerDevice;
            return this;
        }

        public Builder setServiceName(String serviceName) {
            if (serviceName != null) {
                this.mServiceName = serviceName;
            }
            return this;
        }

        public Builder setMatchFilter(byte[] matchFilter) {
            if (matchFilter != null) {
                this.mMatchFilter = matchFilter.clone();
            }
            return this;
        }

        public RttRangingParams build() {
            return new RttRangingParams(this);
        }
    }

}
