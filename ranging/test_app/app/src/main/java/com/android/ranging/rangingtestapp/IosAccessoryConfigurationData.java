/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.ranging.rangingtestapp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class IosAccessoryConfigurationData {
    private static short getByteBufferUint8(ByteBuffer buffer) {
        return (short) (buffer.get() & 0xFF);
    }

    private static int getByteBufferUint16(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    private static long getByteBufferUint32(ByteBuffer buffer) {
        return buffer.getInt() & 0xFFFFFFFFL;
    }

    static final byte[] VENDOR_ID = new byte[]{0x4C, 0x00};  // LITTLE_ENDIAN of 0x004C
    static final byte MULTI_NODE_MODE = 0x00;
    static final byte NUMBER_OF_CONTROLEES = 0x01;

    enum UpdateRateOption {
        AUTOMATIC((short) 0),
        INFREQUENT((short) 10),
        USER_INTERACTIVE((short) 20);

        private final short mValue;

        UpdateRateOption(short value) {
            mValue = value;
        }

        short getValue() {
            return mValue;
        }

        static UpdateRateOption fromValue(short value) {
            for (UpdateRateOption option : UpdateRateOption.values()) {
                if (option.mValue == value) {
                    return option;
                }
            }
            return AUTOMATIC;
        }
    }

    enum DeviceRole {
        RESPONDER((short) 0x00),
        INITIATOR((short) 0x01),
        ADVERTISER((short) 0x05),
        OBSERVER((short) 0x06),
        DT_ANCHOR((short) 0x07),
        DT_TAG((short) 0x08);

        private final short mValue;

        DeviceRole(short value) {
            mValue = value;
        }

        short getValue() {
            return mValue;
        }

        static DeviceRole fromValue(short value) {
            for (DeviceRole role : DeviceRole.values()) {
                if (role.mValue == value) {
                    return role;
                }
            }
            return RESPONDER;
        }
    }

    enum HoppingMode {
        DISABLE((short) 0x00),
        ENABLE((short) 0x01);

        private final short mValue;

        HoppingMode(short value) {
            mValue = value;
        }

        short getValue() {
            return mValue;
        }

        static HoppingMode fromValue(short value) {
            for (HoppingMode mode : HoppingMode.values()) {
                if (mode.mValue == value) {
                    return mode;
                }
            }
            return DISABLE;
        }
    }

    static class AccessoryConfigurationData {

        static class Builder {
            int mMajorVersion = 1;
            int mMinorVersion = 0;
            UpdateRateOption mPreferredUpdateRate = UpdateRateOption.AUTOMATIC;
            UwbConfigData mUwbConfigData = new UwbConfigData.Builder().build();

            Builder() {
            }

            Builder(AccessoryConfigurationData accessoryConfigurationData) {
                mMajorVersion = accessoryConfigurationData.mMajorVersion;
                mMinorVersion = accessoryConfigurationData.mMinorVersion;
                mPreferredUpdateRate = accessoryConfigurationData.mPreferredUpdateRate;
                mUwbConfigData = accessoryConfigurationData.mUwbConfigData;
            }

            Builder setMajorVersion(int majorVersion) {
                mMajorVersion = majorVersion;
                return this;
            }

            Builder setMinorVersion(int minorVersion) {
                mMinorVersion = minorVersion;
                return this;
            }

            Builder setPreferredUpdateRate(UpdateRateOption preferredUpdateRate) {
                mPreferredUpdateRate = preferredUpdateRate;
                return this;
            }

            Builder setUwbConfigData(UwbConfigData uwbConfigData) {
                assert uwbConfigData != null;
                mUwbConfigData = uwbConfigData;
                return this;
            }

            AccessoryConfigurationData build() {
                return new AccessoryConfigurationData(this);
            }
        }

        final int mMajorVersion;
        final int mMinorVersion;
        final UpdateRateOption mPreferredUpdateRate;
        final UwbConfigData mUwbConfigData;

        private AccessoryConfigurationData(Builder builder) {
            mMajorVersion = builder.mMajorVersion;
            mMinorVersion = builder.mMinorVersion;
            mPreferredUpdateRate = builder.mPreferredUpdateRate;
            mUwbConfigData = builder.mUwbConfigData;
        }

        byte[] serialize() {
            byte[] uwbConfigDataBytes = mUwbConfigData.serialize();
            int totalSize = 2 + 2 + 1 + 10 + 1 + uwbConfigDataBytes.length;
            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putShort((short) mMajorVersion);
            buffer.putShort((short) mMinorVersion);
            buffer.put((byte) mPreferredUpdateRate.getValue());
            buffer.put(new byte[10]); // Reserved
            buffer.put((byte) uwbConfigDataBytes.length);
            buffer.put(uwbConfigDataBytes);

            return buffer.array();
        }

        AccessoryConfigurationData deserialize(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int majorVersion = getByteBufferUint16(buffer);
            int minorVersion = getByteBufferUint16(buffer);
            UpdateRateOption preferredUpdateRate =
                    UpdateRateOption.fromValue(getByteBufferUint8(buffer));
            buffer.get(new byte[10]); // Reserved
            short uwbConfigDataSize = getByteBufferUint8(buffer);
            byte[] uwbConfigDataBytes = new byte[uwbConfigDataSize];
            buffer.get(uwbConfigDataBytes);
            UwbConfigData uwbConfigData = UwbConfigData.deserialize(uwbConfigDataBytes);

            return new Builder()
                    .setMajorVersion(majorVersion)
                    .setMinorVersion(minorVersion)
                    .setPreferredUpdateRate(preferredUpdateRate)
                    .setUwbConfigData(uwbConfigData)
                    .build();
        }

        public String toString() {
            return "AccessoryConfigurationData{"
                    + "mMajorVersion=" + mMajorVersion
                    + ", mMinorVersion=" + mMinorVersion
                    + ", mPreferredUpdateRate=" + mPreferredUpdateRate
                    + ", mUwbConfigData=" + mUwbConfigData
                    + "}";
        }
    }

    static class UwbConfigData {
        static class Builder {

            // Nearby Interaction with UWB Interoperability Specification, Release R3, Version 1.1
            int mMajorVersion = 1;
            int mMinorVersion = 1;
            long mManufacturerId = 0;
            long mUwbChipsetModelId = 0;
            long mUwbMiddlewareVersion = 0;
            DeviceRole mRangingRole = DeviceRole.RESPONDER;
            byte[] mSourceAddress = new byte[2];
            int mMaximumUwbClockDrift = 0; // in PPM

            // Nearby Interaction with UWB Interoperability Specification, Release R4, Version 2.0
            HoppingMode mRequestedHoppingMode = HoppingMode.DISABLE;
            int mRequestedNumSlotsPerRound = 25; // (default: 25, min: 6 for DS-TWR deferred mode)
            int mRequestedSlotDuration = 2400; // in RSTU (min: 2400, multiple of 1200)
            int mRequestedRangingInterval = 200; // in 1200 RSTU which is 1 ms (default: 200)

            Builder() {
            }

            Builder(UwbConfigData uwbConfigData) {
                mMajorVersion = uwbConfigData.mMajorVersion;
                mMinorVersion = uwbConfigData.mMinorVersion;
                mManufacturerId = uwbConfigData.mManufacturerId;
                mUwbChipsetModelId = uwbConfigData.mUwbChipsetModelId;
                mUwbMiddlewareVersion = uwbConfigData.mUwbMiddlewareVersion;
                mRangingRole = uwbConfigData.mRangingRole;
                mSourceAddress = uwbConfigData.mSourceAddress;
                mMaximumUwbClockDrift = uwbConfigData.mMaximumUwbClockDrift;
                mRequestedHoppingMode = uwbConfigData.mRequestedHoppingMode;
                mRequestedNumSlotsPerRound = uwbConfigData.mRequestedNumSlotsPerRound;
                mRequestedSlotDuration = uwbConfigData.mRequestedSlotDuration;
                mRequestedRangingInterval = uwbConfigData.mRequestedRangingInterval;
            }

            Builder setMajorVersion(int majorVersion) {
                mMajorVersion = majorVersion;
                return this;
            }

            Builder setMinorVersion(int minorVersion) {
                mMinorVersion = minorVersion;
                return this;
            }

            Builder setManufacturerId(long manufacturerId) {
                mManufacturerId = manufacturerId;
                return this;
            }

            Builder setUwbChipsetModelId(long uwbChipsetModelId) {
                mUwbChipsetModelId = uwbChipsetModelId;
                return this;
            }

            Builder setUwbMiddlewareVersion(long uwbMiddlewareVersion) {
                mUwbMiddlewareVersion = uwbMiddlewareVersion;
                return this;
            }

            Builder setRangingRole(DeviceRole rangingRole) {
                mRangingRole = rangingRole;
                return this;
            }

            Builder setSourceAddress(byte[] sourceAddress) {
                assert sourceAddress.length == 2;
                mSourceAddress = sourceAddress;
                return this;
            }

            Builder setMaximumUwbClockDrift(int maximumUwbClockDrift) {
                mMaximumUwbClockDrift = maximumUwbClockDrift;
                return this;
            }

            Builder setRequestedHoppingMode(HoppingMode requestedHoppingMode) {
                mRequestedHoppingMode = requestedHoppingMode;
                return this;
            }

            Builder setRequestedNumSlotsPerRound(int requestedNumSlotsPerRound) {
                mRequestedNumSlotsPerRound = requestedNumSlotsPerRound;
                return this;
            }

            Builder setRequestedSlotDuration(int requestedSlotDuration) {
                mRequestedSlotDuration = requestedSlotDuration;
                return this;
            }

            Builder setRequestedRangingInterval(int requestedRangingInterval) {
                mRequestedRangingInterval = requestedRangingInterval;
                return this;
            }

            UwbConfigData build() {
                return new UwbConfigData(this);
            }
        }

        // Nearby Interaction with UWB Interoperability Specification, Release R3, Version 1.1
        final int mMajorVersion;
        final int mMinorVersion;
        final long mManufacturerId;
        final long mUwbChipsetModelId;
        final long mUwbMiddlewareVersion;
        final DeviceRole mRangingRole;
        final byte[] mSourceAddress;
        final int mMaximumUwbClockDrift;

        // Nearby Interaction with UWB Interoperability Specification, Release R4, Version 2.0
        HoppingMode mRequestedHoppingMode;
        int mRequestedNumSlotsPerRound;
        int mRequestedSlotDuration;
        int mRequestedRangingInterval;

        private UwbConfigData(Builder builder) {
            mMajorVersion = builder.mMajorVersion;
            mMinorVersion = builder.mMinorVersion;
            mManufacturerId = builder.mManufacturerId;
            mUwbChipsetModelId = builder.mUwbChipsetModelId;
            mUwbMiddlewareVersion = builder.mUwbMiddlewareVersion;
            mRangingRole = builder.mRangingRole;
            mSourceAddress = builder.mSourceAddress;
            mMaximumUwbClockDrift = builder.mMaximumUwbClockDrift;
            mRequestedHoppingMode = builder.mRequestedHoppingMode;
            mRequestedNumSlotsPerRound = builder.mRequestedNumSlotsPerRound;
            mRequestedSlotDuration = builder.mRequestedSlotDuration;
            mRequestedRangingInterval = builder.mRequestedRangingInterval;
        }

        byte[] serialize() {
            int totalSize = 2 + 2 + 4 + 4 + 4 + 1 + 2 + 2;
            if (mMajorVersion >= 2) {
                totalSize += 4 + 1 + 2 + 2 + 2;
            }
            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            buffer.putShort((short) mMajorVersion);
            buffer.putShort((short) mMinorVersion);
            buffer.putInt((int) mManufacturerId);
            buffer.putInt((int) mUwbChipsetModelId);
            buffer.putInt((int) mUwbMiddlewareVersion);
            buffer.put((byte) mRangingRole.getValue());
            // handle endian difference between Apple and Android
            buffer.put(mSourceAddress);
            buffer.putShort((short) mMaximumUwbClockDrift);

            if (mMajorVersion >= 2) {
                buffer.put(new byte[4]); // Reserved
                buffer.put((byte) mRequestedHoppingMode.getValue());
                buffer.putShort((short) mRequestedNumSlotsPerRound);
                buffer.putShort((short) mRequestedSlotDuration);
                buffer.putShort((short) mRequestedRangingInterval);
            }

            return buffer.array();
        }

        static UwbConfigData deserialize(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            int majorVersion = getByteBufferUint16(buffer);
            int minorVersion = getByteBufferUint16(buffer);
            long manufacturerId = getByteBufferUint32(buffer);
            long uwbChipsetModelId = getByteBufferUint32(buffer);
            long uwbMiddlewareVersion = getByteBufferUint32(buffer);
            DeviceRole rangingRole = DeviceRole.fromValue(getByteBufferUint8(buffer));
            // handle endian difference between Apple and Android
            byte[] sourceAddress = new byte[2];
            buffer.get(sourceAddress);
            int maximumUwbClockDrift = getByteBufferUint16(buffer);

            Builder builder = new Builder()
                    .setMajorVersion(majorVersion)
                    .setMinorVersion(minorVersion)
                    .setManufacturerId(manufacturerId)
                    .setUwbChipsetModelId(uwbChipsetModelId)
                    .setUwbMiddlewareVersion(uwbMiddlewareVersion)
                    .setRangingRole(rangingRole)
                    .setSourceAddress(sourceAddress)
                    .setMaximumUwbClockDrift(maximumUwbClockDrift);

            if (majorVersion >= 2) {
                buffer.get(new byte[4]); // Reserved
                HoppingMode requestedHoppingMode =
                        HoppingMode.fromValue(getByteBufferUint8(buffer));
                int requestedNumSlotsPerRound = getByteBufferUint16(buffer);
                int requestedSlotDuration = getByteBufferUint16(buffer);
                int requestedRangingInterval = getByteBufferUint16(buffer);

                builder.setRequestedHoppingMode(requestedHoppingMode)
                        .setRequestedNumSlotsPerRound(requestedNumSlotsPerRound)
                        .setRequestedSlotDuration(requestedSlotDuration)
                        .setRequestedRangingInterval(requestedRangingInterval);
            }

            return builder.build();
        }

        public String toString() {
            return "UwbConfigData{"
                    + "mMajorVersion=" + mMajorVersion
                    + ", mMinorVersion=" + mMinorVersion
                    + ", mManufacturerId=" + mManufacturerId
                    + ", mUwbChipsetModelId=" + mUwbChipsetModelId
                    + ", mUwbMiddlewareVersion=" + mUwbMiddlewareVersion
                    + ", mRangingRole=" + mRangingRole
                    + ", mSourceAddress=" + Arrays.toString(mSourceAddress)
                    + ", mMaximumUwbClockDrift=" + mMaximumUwbClockDrift
                    + ", mRequestedHoppingMode=" + mRequestedHoppingMode
                    + ", mRequestedNumSlotsPerRound=" + mRequestedNumSlotsPerRound
                    + ", mRequestedSlotDuration=" + mRequestedSlotDuration
                    + ", mRequestedRangingInterval=" + mRequestedRangingInterval
                    + "}";
        }
    }

    static class AppleShareableConfigurationData {
        static class Builder {
            AppleUwbConfigData mAppleUwbConfigData = new AppleUwbConfigData.Builder().build();

            Builder() {
            }

            Builder(AppleShareableConfigurationData appleShareableConfigurationData) {
                mAppleUwbConfigData = appleShareableConfigurationData.mAppleUwbConfigData;
            }

            Builder setAppleUwbConfigData(AppleUwbConfigData appleUwbConfigData) {
                assert appleUwbConfigData != null;
                mAppleUwbConfigData = appleUwbConfigData;
                return this;
            }

            AppleShareableConfigurationData build() {
                return new AppleShareableConfigurationData(this);
            }
        }

        final AppleUwbConfigData mAppleUwbConfigData;

        private AppleShareableConfigurationData(Builder builder) {
            mAppleUwbConfigData = builder.mAppleUwbConfigData;
        }

        byte[] serialize() {
            return mAppleUwbConfigData.serialize();
        }

        static AppleShareableConfigurationData deserialize(byte[] bytes) {
            AppleUwbConfigData appleUwbConfigData = AppleUwbConfigData.deserialize(bytes);
            return new Builder().setAppleUwbConfigData(appleUwbConfigData).build();
        }

        public String toString() {
            return "AppleShareableConfigurationData{"
                    + "mAppleUwbConfigData=" + mAppleUwbConfigData
                    + "}";
        }
    }

    static class AppleUwbConfigData {
        private static final short CONFIG_DATA_LENGTH_VERSION_1_1 = 25;
        private static final short CONFIG_DATA_LENGTH_VERSION_2_0 = 30;

        static class Builder {
            // Nearby Interaction with UWB Interoperability Specification, Release R3, Version 1.1
            int mMajorVersion = 1;
            int mMinorVersion = 1;
            short mConfigDataLength = CONFIG_DATA_LENGTH_VERSION_1_1;
            String mRegulatoryCountryCode = "US";
            long mSessionId = 0;
            short mPreambleId = 10; // [9:12] (default = 10)
            short mChannelNumber = 9; // [5, 6, 8, 9, 10, 12, 13, 14] (default = 9)
            int mNumSlotsPerRRound = 25; // (default = 25)
            int mSlotDuration = 2400; // in RSTU (default = 2400)
            int mRangingDuration = 200; // in milliseconds (default = 200)
            short mRangingRoundControl = 0x03; // (default = 0x03 in time scheduled ranging)
                                               // (default = 0x06 in contention-based ranging)
            byte[] mStaticStsIv = new byte[6];
            byte[] mDestAddress = new byte[2];
            int mMaximumUwbClockDrift = 0; // in PPM

            // Nearby Interaction with UWB Interoperability Specification, Release R4, Version 2.0
            HoppingMode mHoppingMode = HoppingMode.DISABLE; // (default = 0x00)

            Builder setMajorVersion(int majorVersion) {
                mMajorVersion = majorVersion;
                if (mMajorVersion >= 2) {
                    setConfigDataLength(CONFIG_DATA_LENGTH_VERSION_2_0);
                } else {
                    setConfigDataLength(CONFIG_DATA_LENGTH_VERSION_1_1);
                }
                return this;
            }

            Builder setMinorVersion(int minorVersion) {
                mMinorVersion = minorVersion;
                return this;
            }

            Builder setConfigDataLength(short configDataLength) {
                assert configDataLength == CONFIG_DATA_LENGTH_VERSION_1_1
                        || configDataLength == CONFIG_DATA_LENGTH_VERSION_2_0;
                mConfigDataLength = configDataLength;
                return this;
            }

            Builder setRegulatoryCountryCode(String regulatoryCountryCode) {
                assert regulatoryCountryCode.length() == 2;
                mRegulatoryCountryCode = regulatoryCountryCode;
                return this;
            }

            Builder setSessionId(long sessionId) {
                mSessionId = sessionId;
                return this;
            }

            Builder setPreambleId(short preambleId) {
                mPreambleId = preambleId;
                return this;
            }

            Builder setChannelNumber(short channelNumber) {
                mChannelNumber = channelNumber;
                return this;
            }

            Builder setNumSlotsPerRRound(int numSlotsPerRRound) {
                mNumSlotsPerRRound = numSlotsPerRRound;
                return this;
            }

            Builder setSlotDuration(int slotDuration) {
                mSlotDuration = slotDuration;
                return this;
            }

            Builder setRangingDuration(int rangingDuration) {
                mRangingDuration = rangingDuration;
                return this;
            }

            Builder setRangingRoundControl(short rangingRoundControl) {
                mRangingRoundControl = rangingRoundControl;
                return this;
            }

            Builder setStaticStsIv(byte[] staticStsIv) {
                assert staticStsIv.length == 6;
                mStaticStsIv = staticStsIv;
                return this;
            }

            Builder setDestAddress(byte[] destAddress) {
                assert destAddress.length == 2;
                mDestAddress = destAddress;
                return this;
            }

            Builder setMaximumUwbClockDrift(int maximumUwbClockDrift) {
                mMaximumUwbClockDrift = maximumUwbClockDrift;
                return this;
            }

            Builder setHoppingMode(HoppingMode hoppingMode) {
                mHoppingMode = hoppingMode;
                return this;
            }

            AppleUwbConfigData build() {
                return new AppleUwbConfigData(this);
            }
        }

        // Nearby Interaction with UWB Interoperability Specification, Release R3, Version 1.1
        final int mMajorVersion;
        final int mMinorVersion;
        final short mConfigDataLength;
        final String mRegulatoryCountryCode;
        final long mSessionId;
        final short mPreambleId;
        final short mChannelNumber;
        final int mNumSlotsPerRRound;
        final int mSlotDuration;
        final int mRangingDuration;
        final short mRangingRoundControl;
        final byte[] mStaticStsIv;
        final byte[] mDestAddress;
        final int mMaximumUwbClockDrift;

        // Nearby Interaction with UWB Interoperability Specification, Release R4, Version 2.0
        final HoppingMode mHoppingMode;

        private AppleUwbConfigData(Builder builder) {
            mMajorVersion = builder.mMajorVersion;
            mMinorVersion = builder.mMinorVersion;
            mConfigDataLength = builder.mConfigDataLength;
            mRegulatoryCountryCode = builder.mRegulatoryCountryCode;
            mSessionId = builder.mSessionId;
            mPreambleId = builder.mPreambleId;
            mChannelNumber = builder.mChannelNumber;
            mNumSlotsPerRRound = builder.mNumSlotsPerRRound;
            mSlotDuration = builder.mSlotDuration;
            mRangingDuration = builder.mRangingDuration;
            mRangingRoundControl = builder.mRangingRoundControl;
            mStaticStsIv = builder.mStaticStsIv;
            mDestAddress = builder.mDestAddress;
            mMaximumUwbClockDrift = builder.mMaximumUwbClockDrift;
            mHoppingMode = builder.mHoppingMode;
        }

        byte[] serialize() {
            int totalSize = 2 + 2 + 1 + 2 + 4 + 1 + 1 + 2 + 2 + 2 + 1 + 6 + 2 + 2;
            if (mMajorVersion >= 2) {
                totalSize += 4 + 1;
            }

            ByteBuffer buffer = ByteBuffer.allocate(totalSize);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putShort((short) mMajorVersion);
            buffer.putShort((short) mMinorVersion);
            buffer.put((byte) mConfigDataLength);
            buffer.put(Arrays.copyOfRange(mRegulatoryCountryCode.getBytes(StandardCharsets.UTF_8),
                    0, 2));
            buffer.putInt((int) mSessionId);
            buffer.put((byte) mPreambleId);
            buffer.put((byte) mChannelNumber);
            buffer.putShort((short) mNumSlotsPerRRound);
            buffer.putShort((short) mSlotDuration);
            buffer.putShort((short) mRangingDuration);
            buffer.put((byte) mRangingRoundControl);
            buffer.put(Arrays.copyOfRange(mStaticStsIv, 0, 6));
            // handle endian difference between Apple and Android
            buffer.put(mDestAddress);
            buffer.putShort((short) mMaximumUwbClockDrift);

            if (mMajorVersion >= 2) {
                buffer.put(new byte[4]); // Reserved
                buffer.put((byte) mHoppingMode.getValue());
            }
            return buffer.array();
        }

        static AppleUwbConfigData deserialize(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int majorVersion = getByteBufferUint16(buffer);
            int minorVersion = getByteBufferUint16(buffer);
            short configDataLength = getByteBufferUint8(buffer);
            byte[] regulatoryCountryCodeBytes = new byte[2];
            buffer.get(regulatoryCountryCodeBytes);
            String regulatoryCountryCode =
                    new String(regulatoryCountryCodeBytes, StandardCharsets.UTF_8);
            long sessionId = getByteBufferUint32(buffer);
            short preambleId = getByteBufferUint8(buffer);
            short channelNumber = getByteBufferUint8(buffer);
            int numSlotsPerRRound = getByteBufferUint16(buffer);
            int slotDuration = getByteBufferUint16(buffer);
            int rangingDuration = getByteBufferUint16(buffer);
            short rangingRoundControl = getByteBufferUint8(buffer);
            byte[] staticStsIv = new byte[6];
            buffer.get(staticStsIv);
            // handle endian difference between Apple and Android
            byte[] destAddress = new byte[2];
            buffer.get(destAddress);
            int maximumUwbClockDrift = getByteBufferUint16(buffer);

            Builder builder = new Builder()
                    .setMajorVersion(majorVersion)
                    .setMinorVersion(minorVersion)
                    .setConfigDataLength(configDataLength)
                    .setRegulatoryCountryCode(regulatoryCountryCode)
                    .setSessionId(sessionId)
                    .setPreambleId(preambleId)
                    .setChannelNumber(channelNumber)
                    .setNumSlotsPerRRound(numSlotsPerRRound)
                    .setSlotDuration(slotDuration)
                    .setRangingDuration(rangingDuration)
                    .setRangingRoundControl(rangingRoundControl)
                    .setStaticStsIv(staticStsIv)
                    .setDestAddress(destAddress)
                    .setMaximumUwbClockDrift(maximumUwbClockDrift);

            if (majorVersion >= 2) {
                buffer.get(new byte[4]); // Reserved
                HoppingMode hoppingMode = HoppingMode.fromValue(buffer.get());
                builder.setHoppingMode(hoppingMode);
            }

            return builder.build();
        }

        public String toString() {
            return "AppleUwbConfigData{"
                    + "mMajorVersion=" + mMajorVersion
                    + ", mMinorVersion=" + mMinorVersion
                    + ", mConfigDataLength=" + mConfigDataLength
                    + ", mRegulatoryCountryCode='" + mRegulatoryCountryCode + '\''
                    + ", mSessionId=" + mSessionId
                    + ", mPreambleId=" + mPreambleId
                    + ", mChannelNumber=" + mChannelNumber
                    + ", mNumSlotsPerRRound=" + mNumSlotsPerRRound
                    + ", mSlotDuration=" + mSlotDuration
                    + ", mRangingDuration=" + mRangingDuration
                    + ", mRangingRoundControl=" + mRangingRoundControl
                    + ", mStaticStsIv=" + Arrays.toString(mStaticStsIv)
                    + ", mDestAddress=" + Arrays.toString(mDestAddress)
                    + ", mMaximumUwbClockDrift=" + mMaximumUwbClockDrift
                    + ", mHoppingMode=" + mHoppingMode
                    + "}";
        }
    }
}
