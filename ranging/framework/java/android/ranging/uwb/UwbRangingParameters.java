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

package android.ranging.uwb;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingDevice;

import androidx.annotation.IntRange;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class UwbRangingParameters implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceRole.RESPONDER,
            DeviceRole.INITIATOR,
    })
    public @interface DeviceRole {
        int RESPONDER = 0;
        /** The device that initiates the session. */
        int INITIATOR = 1;
    }

    private final @DeviceRole int mDeviceRole;

    private final int mSessionId;

    private final int mSubSessionId;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            ConfigId.UNICAST_DS_TWR,
            ConfigId.MULTICAST_DS_TWR,
            ConfigId.UNICAST_DS_TWR_NO_AOA,
            ConfigId.PROVISIONED_UNICAST_DS_TWR,
            ConfigId.PROVISIONED_MULTICAST_DS_TWR,
            ConfigId.PROVISIONED_UNICAST_DS_TWR_NO_AOA,
            ConfigId.PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR,
    })
    public @interface ConfigId {
        /**
         * FiRa-defined unicast {@code STATIC STS DS-TWR} ranging, deferred mode, ranging interval
         * 240 ms. Typical use case: device tracking tags.
         */
        int UNICAST_DS_TWR = 1;
        int MULTICAST_DS_TWR = 2;
        /** Same as {@code CONFIG_ID_1}, except Angle-of-arrival (AoA) data is not reported. */
        int UNICAST_DS_TWR_NO_AOA = 3;
        /** Same as {@code CONFIG_ID_1}, except P-STS security mode is enabled. */
        int PROVISIONED_UNICAST_DS_TWR = 4;
        /** Same as {@code CONFIG_ID_2}, except P-STS security mode is enabled. */
        int PROVISIONED_MULTICAST_DS_TWR = 5;
        /** Same as {@code CONFIG_ID_3}, except P-STS security mode is enabled. */
        int PROVISIONED_UNICAST_DS_TWR_NO_AOA = 6;
        /** Same as {@code CONFIG_ID_2}, except P-STS individual controlee key mode is enabled. */
        int PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR = 7;
    }

    private final @ConfigId int mConfigId;

    private final UwbAddress mDeviceAddress;

    private final byte[] mSessionKeyInfo;

    private final byte[] mSubSessionKeyInfo;

    private final UwbComplexChannel mComplexChannel;

    private final Map<RangingDevice, UwbAddress> mPeerAddresses;


    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RangingUpdateRate.NORMAL,
            RangingUpdateRate.INFREQUENT,
            RangingUpdateRate.FAST,
    })
    public @interface RangingUpdateRate {
        int NORMAL = 1;
        int INFREQUENT = 2;
        int FAST = 3;
    }

    private final @RangingUpdateRate int mRangingUpdateRate;

    private final @IntRange(from = 1, to = 2) int mSlotDurationMs;

    private final boolean mIsAoaDisabled;

    private UwbRangingParameters(Builder builder) {
        if (builder.mConfigId == null) {
            throw new IllegalArgumentException("Missing required parameter: configId");
        }
        if (builder.mPeerAddresses == null) {
            throw new IllegalArgumentException("Missing required parameter: peerAddresses");
        }
        if (builder.mDeviceAddress == null) {
            throw new IllegalArgumentException("Missing required parameter: deviceAddress");
        }
        if (builder.mComplexChannel == null) {
            throw new IllegalArgumentException("Missing required parameter: complexChannel");
        }
        mDeviceRole = builder.mDeviceRole;
        mSessionId = builder.mSessionId;
        mSubSessionId = builder.mSubSessionId;
        mConfigId = builder.mConfigId;
        mDeviceAddress = builder.mDeviceAddress;
        mSessionKeyInfo = builder.mSessionKeyInfo;
        mSubSessionKeyInfo = builder.mSubSessionKeyInfo;
        mComplexChannel = builder.mComplexChannel;
        mPeerAddresses = builder.mPeerAddresses;
        mRangingUpdateRate = builder.mRangingUpdateRate;
        mSlotDurationMs = builder.mSlotDurationMs;
        mIsAoaDisabled = builder.mIsAoaDisabled;
    }

    public static class Builder {
        private @DeviceRole int mDeviceRole = DeviceRole.RESPONDER;
        private int mSessionId;
        private int mSubSessionId;
        private Integer mConfigId = null;
        private UwbAddress mDeviceAddress = null;
        private byte[] mSessionKeyInfo = null;
        private byte[] mSubSessionKeyInfo = null;
        private UwbComplexChannel mComplexChannel;
        private Map<RangingDevice, UwbAddress> mPeerAddresses = null;
        private @RangingUpdateRate int mRangingUpdateRate;
        private int mSlotDurationMs = 1;
        private boolean mIsAoaDisabled = false;

        // Build method to create an instance of RangingConfiguration
        public UwbRangingParameters build() {
            return new UwbRangingParameters(this);
        }

        /** @param role of the device within the session. */
        public Builder setDeviceRole(@DeviceRole int role) {
            mDeviceRole = role;
            return this;
        }

        public Builder setPeerAddresses(@NonNull Map<RangingDevice, UwbAddress> addresses) {
            mPeerAddresses = addresses;
            return this;
        }

        public Builder setConfigId(@ConfigId int config) {
            mConfigId = config;
            return this;
        }

        public Builder setDeviceAddress(@NonNull UwbAddress address) {
            mDeviceAddress = address;
            return this;
        }

        public Builder setSessionId(int sessionId) {
            mSessionId = sessionId;
            return this;
        }

        public Builder setSubSessionId(int subSessionId) {
            mSubSessionId = subSessionId;
            return this;
        }

        public Builder setSessionKeyInfo(byte[] sessionKeyInfo) {
            mSessionKeyInfo = sessionKeyInfo;
            return this;
        }

        public Builder setSubSessionKeyInfo(byte[] subSessionKeyInfo) {
            mSubSessionKeyInfo = subSessionKeyInfo;
            return this;
        }

        public Builder setComplexChannel(@NonNull UwbComplexChannel complexChannel) {
            mComplexChannel = complexChannel;
            return this;
        }

        public Builder setRangingUpdateRate(@RangingUpdateRate int rate) {
            mRangingUpdateRate = rate;
            return this;
        }

        public Builder setSlotDurationMs(@IntRange(from = 1, to = 2) int durationMs) {
            mSlotDurationMs = durationMs;
            return this;
        }

        public Builder setAoaDisabled(boolean isAoaDisabled) {
            mIsAoaDisabled = isAoaDisabled;
            return this;
        }
    }

    UwbRangingParameters(Parcel in) {
        mDeviceRole = in.readInt();
        mSessionId = in.readInt();
        mSubSessionId = in.readInt();
        mConfigId = in.readInt();
        mDeviceAddress = in.readParcelable(UwbAddress.class.getClassLoader(), UwbAddress.class);
        mSessionKeyInfo = in.readBlob();
        mSubSessionKeyInfo = in.readBlob();
        mComplexChannel = Objects.requireNonNull(
                in.readParcelable(
                        UwbComplexChannel.class.getClassLoader(), UwbComplexChannel.class));
        mRangingUpdateRate = in.readInt();
        mSlotDurationMs = in.readInt();
        mIsAoaDisabled = in.readBoolean();

        // Deserialize peerAddresses (Map<RangingDevice, UwbAddress>)
        int numPeers = in.readInt();
        mPeerAddresses = new HashMap<>();
        for (int i = 0; i < numPeers; i++) {
            RangingDevice device = Objects.requireNonNull(
                    in.readParcelable(RangingDevice.class.getClassLoader(), RangingDevice.class));
            UwbAddress address = Objects.requireNonNull(
                    in.readParcelable(UwbAddress.class.getClassLoader(), UwbAddress.class));
            mPeerAddresses.put(device, address);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDeviceRole);
        dest.writeInt(mSessionId);
        dest.writeInt(mSubSessionId);
        dest.writeInt(mConfigId);
        dest.writeParcelable(mDeviceAddress, flags);
        dest.writeBlob(mSessionKeyInfo);
        dest.writeBlob(mSubSessionKeyInfo);
        dest.writeParcelable(mComplexChannel, flags);
        dest.writeInt(mRangingUpdateRate);
        dest.writeInt(mSlotDurationMs);
        dest.writeBoolean(mIsAoaDisabled);
        // Serialize peerAddresses (Map<RangingDevice, UwbAddress>)
        dest.writeInt(mPeerAddresses.size());  // Write the size of the Map
        for (Map.Entry<RangingDevice, UwbAddress> entry : mPeerAddresses.entrySet()) {
            dest.writeParcelable(entry.getKey(), flags);  // Write each RangingDevice
            dest.writeParcelable(entry.getValue(), flags);  // Write each UwbAddress
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<UwbRangingParameters> CREATOR = new Creator<>() {
        @Override
        public UwbRangingParameters createFromParcel(Parcel in) {
            return new UwbRangingParameters(in);
        }

        @Override
        public UwbRangingParameters[] newArray(int size) {
            return new UwbRangingParameters[size];
        }
    };

    /** @return the device's role within the session. */
    public @DeviceRole int getDeviceRole() {
        return mDeviceRole;
    }

    public int getSessionId() {
        return mSessionId;
    }

    public int getSubSessionId() {
        return mSubSessionId;
    }

    public @ConfigId int getConfigId() {
        return mConfigId;
    }

    public @NonNull UwbAddress getDeviceAddress() {
        return mDeviceAddress;
    }

    public @Nullable byte[] getSessionKeyInfo() {
        return mSessionKeyInfo;
    }

    public @Nullable byte[] getSubSessionKeyInfo() {
        return mSubSessionKeyInfo;
    }

    public @NonNull UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    public @NonNull Map<RangingDevice, UwbAddress> getPeerAddresses() {
        return Map.copyOf(mPeerAddresses);
    }

    public @RangingUpdateRate int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    public @IntRange(from = 1, to = 2) int getSlotDurationMs() {
        return mSlotDurationMs;
    }

    public boolean isAoaDisabled() {
        return mIsAoaDisabled;
    }
}
