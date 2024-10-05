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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Objects;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class UwbParameters implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceType.CONTROLEE,
            DeviceType.CONTROLLER
    })
    public @interface DeviceType {
        int CONTROLEE = 0;
        int CONTROLLER = 1;
    }

    private final @DeviceType int mDeviceType;

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

    private final byte[] mSessionKeyInfo;

    private final byte[] mSubSessionKeyInfo;

    private final UwbComplexChannel mComplexChannel;

    private final ImmutableMap<RangingDevice, UwbAddress> mPeerAddresses;


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

    private UwbParameters(Builder builder) {
        Preconditions.checkNotNull(builder.mConfigId,
                "Missing required parameter config id");
        Preconditions.checkNotNull(builder.mPeerAddresses,
                "Missing required parameter peer addresses");
        mDeviceType = builder.mDeviceType;
        mSessionId = builder.mSessionId;
        mSubSessionId = builder.mSubSessionId;
        mConfigId = builder.mConfigId;
        mSessionKeyInfo = builder.mSessionKeyInfo;
        mSubSessionKeyInfo = builder.mSubSessionKeyInfo;
        mComplexChannel = builder.mComplexChannel;
        mPeerAddresses = builder.mPeerAddresses;
        mRangingUpdateRate = builder.mRangingUpdateRate;
        mSlotDurationMs = builder.mSlotDurationMs;
        mIsAoaDisabled = builder.mIsAoaDisabled;
    }

    public UwbParameters(UwbParameters other) {
        mDeviceType = other.mDeviceType;
        mSessionId = other.mSessionId;
        mSubSessionId = other.mSubSessionId;
        mConfigId = other.mConfigId;
        mSessionKeyInfo = other.mSessionKeyInfo;
        mSubSessionKeyInfo = other.mSubSessionKeyInfo;
        mComplexChannel = other.mComplexChannel;
        mPeerAddresses = other.mPeerAddresses;
        mRangingUpdateRate = other.mRangingUpdateRate;
        mSlotDurationMs = other.mSlotDurationMs;
        mIsAoaDisabled = other.mIsAoaDisabled;
    }

    public static class Builder {
        private int mDeviceType = DeviceType.CONTROLEE;
        private int mSessionId;
        private int mSubSessionId;
        private Integer mConfigId = null;
        private byte[] mSessionKeyInfo = null;
        private byte[] mSubSessionKeyInfo = null;
        private UwbComplexChannel mComplexChannel;
        private ImmutableMap<RangingDevice, UwbAddress> mPeerAddresses = null;
        private @RangingUpdateRate int mRangingUpdateRate;
        private int mSlotDurationMs = 1;
        private boolean mIsAoaDisabled = false;

        // Build method to create an instance of RangingConfiguration
        public UwbParameters build() {
            return new UwbParameters(this);
        }

        public Builder setDeviceType(@DeviceType int type) {
            mDeviceType = type;
            return this;
        }

        public Builder setPeerAddresses(
                @NonNull ImmutableMap<RangingDevice, UwbAddress> addresses
        ) {
            mPeerAddresses = addresses;
            return this;
        }

        public Builder setConfigId(@ConfigId int config) {
            mConfigId = config;
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

        public Builder setComplexChannel(@Nullable UwbComplexChannel complexChannel) {
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

    protected UwbParameters(Parcel in) {
        mDeviceType = in.readInt();
        mSessionId = in.readInt();
        mSubSessionId = in.readInt();
        mConfigId = in.readInt();
        mSessionKeyInfo = in.readBlob();
        mSubSessionKeyInfo = in.readBlob();
        mComplexChannel = Objects.requireNonNull(
                in.readParcelable(
                        UwbComplexChannel.class.getClassLoader(), UwbComplexChannel.class));
        mRangingUpdateRate = in.readInt();
        mSlotDurationMs = in.readInt();
        mIsAoaDisabled = in.readByte() != 0;

        // Deserialize peerAddresses (Map<RangingDevice, UwbAddress>)
        int numPeers = in.readInt();
        ImmutableMap.Builder<RangingDevice, UwbAddress> peers = new ImmutableMap.Builder<>();
        for (int i = 0; i < numPeers; i++) {
            RangingDevice device = Objects.requireNonNull(
                    in.readParcelable(RangingDevice.class.getClassLoader(), RangingDevice.class));
            UwbAddress address = Objects.requireNonNull(
                    in.readParcelable(UwbAddress.class.getClassLoader(), UwbAddress.class));
            peers.put(device, address);
        }
        mPeerAddresses = peers.build();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mDeviceType);
        dest.writeInt(mSessionId);
        dest.writeInt(mSubSessionId);
        dest.writeInt(mConfigId);
        dest.writeBlob(mSessionKeyInfo);
        dest.writeBlob(mSubSessionKeyInfo);
        dest.writeParcelable(mComplexChannel, flags);
        dest.writeInt(mRangingUpdateRate);
        dest.writeInt(mSlotDurationMs);
        dest.writeByte((byte) (mIsAoaDisabled ? 1 : 0));
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

    public static final Creator<UwbParameters> CREATOR =
            new Creator<UwbParameters>() {
                @Override
                public UwbParameters createFromParcel(Parcel in) {
                    return new UwbParameters(in);
                }

                @Override
                public UwbParameters[] newArray(int size) {
                    return new UwbParameters[size];
                }
            };


    public @DeviceType int getDeviceType() {
        return mDeviceType;
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

    public @Nullable byte[] getSessionKeyInfo() {
        return mSessionKeyInfo;
    }

    public @Nullable byte[] getSubSessionKeyInfo() {
        return mSubSessionKeyInfo;
    }


    public @Nullable UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }


    public @NonNull ImmutableMap<RangingDevice, UwbAddress> getPeerAddresses() {
        return mPeerAddresses;
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
