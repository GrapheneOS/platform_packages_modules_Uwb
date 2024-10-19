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
 * UwbRangingParameters encapsulates the parameters required for a UWB ranging session.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class UwbRangingParams implements Parcelable {

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DeviceRole.RESPONDER,
            DeviceRole.INITIATOR,
    })
    public @interface DeviceRole {
        /** The device that responds to a session. */
        int RESPONDER = 0;
        /** The device that initiates the session. */
        int INITIATOR = 1;
    }

    @DeviceRole
    private final int mDeviceRole;

    private final int mSessionId;

    private final int mSubSessionId;

    /**
     * Defines the roles that a device can assume within a UWB ranging session.
     * @hide
     */
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
         * 240 ms.
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

    @ConfigId
    private final int mConfigId;

    private final UwbAddress mDeviceAddress;

    private final byte[] mSessionKeyInfo;

    private final byte[] mSubSessionKeyInfo;

    private final UwbComplexChannel mComplexChannel;

    private final Map<RangingDevice, UwbAddress> mPeerAddresses;


    /**
     * Defines the configuration IDs for different ranging scenarios.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RangingUpdateRate.NORMAL,
            RangingUpdateRate.INFREQUENT,
            RangingUpdateRate.FAST,
    })
    public @interface RangingUpdateRate {
        /** Ranging interval between 200ms - 240ms */
        int NORMAL = 1;
        /** Ranging interval between 600ms - 800ms */
        int INFREQUENT = 2;
        /** Ranging interval between 100ms - 200ms */
        int FAST = 3;
    }

    @RangingUpdateRate
    private final int mRangingUpdateRate;

    @IntRange(from = 1, to = 2)
    private final int mSlotDurationMillis;

    private final boolean mIsAoaDisabled;

    private UwbRangingParams(Builder builder) {
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
        mSlotDurationMillis = builder.mSlotDurationMillis;
        mIsAoaDisabled = builder.mIsAoaDisabled;
    }


    UwbRangingParams(Parcel in) {
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
        mSlotDurationMillis = in.readInt();
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
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mDeviceRole);
        dest.writeInt(mSessionId);
        dest.writeInt(mSubSessionId);
        dest.writeInt(mConfigId);
        dest.writeParcelable(mDeviceAddress, flags);
        dest.writeBlob(mSessionKeyInfo);
        dest.writeBlob(mSubSessionKeyInfo);
        dest.writeParcelable(mComplexChannel, flags);
        dest.writeInt(mRangingUpdateRate);
        dest.writeInt(mSlotDurationMillis);
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

    @NonNull
    public static final Creator<UwbRangingParams> CREATOR = new Creator<>() {
        @Override
        public UwbRangingParams createFromParcel(Parcel in) {
            return new UwbRangingParams(in);
        }

        @Override
        public UwbRangingParams[] newArray(int size) {
            return new UwbRangingParams[size];
        }
    };

    /**
     * Gets the role of the device in the ranging session.
     *
     * @return The device role as an integer.
     */
    @DeviceRole
    public int getDeviceRole() {
        return mDeviceRole;
    }

    /**
     * Gets the session ID associated with this ranging session.
     *
     * @return The session ID as an integer.
     */
    public int getSessionId() {
        return mSessionId;
    }

    /**
     * Gets the sub-session ID if applicable for the session.
     *
     * @return The sub-session ID as an integer.
     */
    public int getSubSessionId() {
        return mSubSessionId;
    }

    /**
     * Gets the configuration ID associated with this session.
     *
     * @return The configuration ID as an integer.
     */
    @ConfigId
    public int getConfigId() {
        return mConfigId;
    }

    /**
     * Gets the UWB address of the device.
     *
     * @return The {@link UwbAddress} of the device.
     */
    @NonNull
    public UwbAddress getDeviceAddress() {
        return mDeviceAddress;
    }

    /**
     * Gets session key information, if available.
     *
     * @return A byte array containing session key info, or null if not available.
     */
    @Nullable
    public byte[] getSessionKeyInfo() {
        return mSessionKeyInfo;
    }

    /**
     * Gets sub-session key information, if available.
     *
     * @return A byte array containing sub-session key info, or null if not available.
     */
    @Nullable
    public byte[] getSubSessionKeyInfo() {
        return mSubSessionKeyInfo;
    }

    /**
     * Gets the complex channel information for this session.
     *
     * @return A {@link UwbComplexChannel} object containing channel and preamble index.
     */
    @NonNull
    public UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    /**
     * Returns a map of peer devices and their UWB addresses.
     *
     * @return An immutable {@link Map} with peer devices as keys and their UWB addresses as values.
     */
    @NonNull
    public Map<RangingDevice, UwbAddress> getPeerAddresses() {
        return Map.copyOf(mPeerAddresses);
    }

    /**
     * Returns the update rate for ranging operations.
     *
     * @return The ranging update rate as an integer.
     */
    @RangingUpdateRate
    public int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    /**
     * Returns the slot duration in milliseconds.
     *
     * @return The slot duration in milliseconds, within the range [1, 2].
     */
    @IntRange(from = 1, to = 2)
    public int getSlotDurationMillis() {
        return mSlotDurationMillis;
    }

    /**
     * Checks if Angle of Arrival (AoA) measurements are disabled.
     *
     * @return {@code true} if AoA is disabled, {@code false} otherwise.
     */
    public boolean isAoaDisabled() {
        return mIsAoaDisabled;
    }

    /**
     * Builder class for creating instances of {@link UwbRangingParameters}
     */
    public static final class Builder {
        @DeviceRole
        private int mDeviceRole = DeviceRole.RESPONDER;
        private int mSessionId;
        private int mSubSessionId;
        private Integer mConfigId = null;
        private UwbAddress mDeviceAddress = null;
        private byte[] mSessionKeyInfo = null;
        private byte[] mSubSessionKeyInfo = null;
        private UwbComplexChannel mComplexChannel;
        private Map<RangingDevice, UwbAddress> mPeerAddresses = null;
        @RangingUpdateRate
        private int mRangingUpdateRate;
        private int mSlotDurationMillis = 1;
        private boolean mIsAoaDisabled = false;

        /**
         * Sets the role of the device within the session.
         *
         * @param role the role of the device, either {@link DeviceRole#RESPONDER} or
         *             {@link DeviceRole#INITIATOR}.
         * @return this Builder instance.
         */
        @NonNull
        public Builder setDeviceRole(@DeviceRole int role) {
            mDeviceRole = role;
            return this;
        }

        /**
         * Sets the peer addresses for the ranging session.
         *
         * @param addresses a non-null map of {@link RangingDevice} to {@link UwbAddress} for the
         *                  peers in the session.
         * @return this Builder instance.
         * @throws IllegalArgumentException if the provided map is null.
         */
        @NonNull
        public Builder setPeerAddresses(@NonNull Map<RangingDevice, UwbAddress> addresses) {
            mPeerAddresses = addresses;
            return this;
        }

        /**
         * Sets the configuration ID for the ranging parameters.
         *
         * @param config the configuration ID, defined as one of the constants in {@link ConfigId}.
         * @return this Builder instance.
         */
        @NonNull
        public Builder setConfigId(@ConfigId int config) {
            mConfigId = config;
            return this;
        }

        /**
         * Sets the device address for the ranging session.
         *
         * @param address a non-null {@link UwbAddress} representing the device's address.
         * @return this Builder instance.
         * @throws IllegalArgumentException if the provided address is null.
         */
        @NonNull
        public Builder setDeviceAddress(@NonNull UwbAddress address) {
            mDeviceAddress = address;
            return this;
        }

        /**
         * Sets the session ID for the ranging session.
         *
         * @param sessionId the session ID, which should be a unique identifier for the session.
         * @return this Builder instance.
         */
        @NonNull
        public Builder setSessionId(int sessionId) {
            mSessionId = sessionId;
            return this;
        }

        /**
         * Sets the sub-session ID for the ranging session.
         *
         * @param subSessionId the sub-session ID, which should be a unique identifier for the
         *                     sub-session.
         * @return this Builder instance for method chaining.
         */
        @NonNull
        public Builder setSubSessionId(int subSessionId) {
            mSubSessionId = subSessionId;
            return this;
        }

        /**
         * Sets the session key information for secure ranging.
         *
         * @param sessionKeyInfo a byte array containing session key information.
         * @return this Builder instance.
         *
         * @throws IllegalArgumentException if the provided byte array is null.
         */
        @NonNull
        public Builder setSessionKeyInfo(@NonNull byte[] sessionKeyInfo) {
            mSessionKeyInfo = sessionKeyInfo;
            return this;
        }

        /**
         * Sets the sub-session key information for secure ranging.
         *
         * @param subSessionKeyInfo a byte array containing sub-session key information.
         * @return this Builder instance.
         *
         * @throws IllegalArgumentException if the provided map is null.
         */
        @NonNull
        public Builder setSubSessionKeyInfo(@NonNull byte[] subSessionKeyInfo) {
            mSubSessionKeyInfo = subSessionKeyInfo;
            return this;
        }

        /**
         * Sets the complex channel configuration for the ranging session.
         *
         * @param complexChannel a non-null {@link UwbComplexChannel} instance representing the
         *                       channel configuration.
         * @return this Builder instance.
         * @throws IllegalArgumentException if the provided complex channel is null.
         */
        @NonNull
        public Builder setComplexChannel(@NonNull UwbComplexChannel complexChannel) {
            mComplexChannel = complexChannel;
            return this;
        }

        /**
         * Sets the ranging update rate for the session.
         *
         * @param rate the ranging update rate, defined as one of the constants in
         *             {@link RangingUpdateRate}.
         * @return this Builder instance.
         */
        @NonNull
        public Builder setRangingUpdateRate(@RangingUpdateRate int rate) {
            mRangingUpdateRate = rate;
            return this;
        }

        /**
         * Sets the slot duration in milliseconds for the ranging session.
         *
         * @param durationMs the duration of each slot, must be between 1 and 2 milliseconds.
         * @return this Builder instance.
         * @throws IllegalArgumentException if the provided duration is out of range.
         */
        @NonNull
        public Builder setSlotDurationMillis(@IntRange(from = 1, to = 2) int durationMs) {
            mSlotDurationMillis = durationMs;
            return this;
        }

        /**
         * Sets whether angle-of-arrival (AoA) measurements are disabled for the session.
         *
         * @param isAoaDisabled true if AoA measurements should be disabled, false otherwise.
         * @return this Builder instance.
         */
        @NonNull
        public Builder setAoaDisabled(boolean isAoaDisabled) {
            mIsAoaDisabled = isAoaDisabled;
            return this;
        }

        /**
         * Builds a new instance of {@link UwbRangingParams}.
         *
         * @return a new instance of {@link UwbRangingParams} created using the current state of
         * the builder.
         */
        @NonNull
        public UwbRangingParams build() {
            return new UwbRangingParams(this);
        }

    }
}
