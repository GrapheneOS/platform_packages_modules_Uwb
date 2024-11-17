/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.ranging.uwb;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.params.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.params.RawRangingDevice.UPDATE_RATE_NORMAL;

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.params.DataNotificationConfig;
import android.ranging.params.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbRangeDataNtfConfig;
import com.android.server.ranging.RangingPeerConfig.TechnologyConfig;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.base.RequiredParam;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A complete configuration for UWB ranging. This encapsulates all information contained in a
 * configuration message sent over OOB and everything required to start a session in the underlying
 * UWB system API.
 */
public class UwbConfig implements TechnologyConfig {
    private static final String TAG = UwbConfig.class.getSimpleName();

    private static final int MIN_SIZE_BYTES = 20;

    // Size in bytes for properties when serialized.
    private static final int TECHNOLOGY_ID_SIZE = 1;
    private static final int UWB_ADDRESS_SIZE = 2;
    private static final int SESSION_ID_SIZE = 4;
    private static final int CONFIG_ID_SIZE = 1;
    private static final int CHANNEL_SIZE = 1;
    private static final int PREAMBLE_INDEX_SIZE = 1;
    private static final int RANGING_INTERVAL_SIZE = 4;
    private static final int SLOT_DURATION_SIZE = 1;
    private static final int SESSION_KEY_LENGTH_SIZE = 1;
    private static final int STS_SESSION_KEY_SIZE = 8;
    private static final int PSTS_SHORT_SESSION_KEY_SIZE = 16;
    private static final int PSTS_LONG_SESSION_KEY_SIZE = 32;
    private static final int COUNTRY_CODE_SIZE = 2;
    private static final int DEVICE_ROLE_SIZE = 1;
    private static final int DEVICE_TYPE_SIZE = 1;

    /** Returns the size of the object in bytes when serialized. */
    public final int getSize() {
        return MIN_SIZE_BYTES + getSessionKeyInfoLength();
    }

    private final String mCountryCode;
    private final DataNotificationConfig mDataNotificationConfig;
    private final UwbRangingParams mParameters;
    private final int mDeviceRole;
    private final boolean mIsAoaNeeded;
    private final Pair<RangingDevice, UwbAddress> mPeer;

    private UwbConfig(Builder builder) {
        mParameters = builder.mParameters;
        mCountryCode = builder.mCountryCode.get();
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mDeviceRole = builder.mDeviceRole;
        mIsAoaNeeded = builder.mIsAoaNeeded;
        mPeer = new Pair<>(builder.mPeerDevice.get(), builder.mParameters.getPeerAddress());
    }

    /**
     * Parses the given byte array and returns {@link UwbConfig} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static UwbConfig parseBytes(byte[] uwbConfigBytes) {
        if (uwbConfigBytes.length < MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Failed to parse UwbConfig, invalid size. Bytes: " + Arrays.toString(
                            uwbConfigBytes));
        }

        // Parse Ranging Technology Id
        int parseCursor = 0;
        ImmutableList<RangingTechnology> technology =
                RangingTechnology.parseByte(uwbConfigBytes[parseCursor]);
        if (technology.size() != 1 || technology.get(0) != RangingTechnology.UWB) {
            throw new IllegalArgumentException("Couldn't parse UwbConfig, invalid technology id");
        }
        parseCursor += TECHNOLOGY_ID_SIZE;

        // Parse Uwb Address
        com.android.ranging.uwb.backend.internal.UwbAddress uwbAddress =
                com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                        Arrays.copyOfRange(
                                uwbConfigBytes, parseCursor, parseCursor + UWB_ADDRESS_SIZE));
        parseCursor += UWB_ADDRESS_SIZE;

        // Parse Session Id
        int sessionId =
                Conversions.byteArrayToInt(
                        Arrays.copyOfRange(uwbConfigBytes, parseCursor,
                                parseCursor + SESSION_ID_SIZE));
        parseCursor += SESSION_ID_SIZE;

        // Parse Config Id
        int configId = uwbConfigBytes[parseCursor];
        parseCursor += CONFIG_ID_SIZE;

        // Parse Channel
        int channel = uwbConfigBytes[parseCursor];
        parseCursor += CHANNEL_SIZE;

        // Parse Preamble Index
        int preambleIndex = uwbConfigBytes[parseCursor];
        parseCursor += PREAMBLE_INDEX_SIZE;

        // Parse Ranging Interval Ms
        int rangingIntervalMs =
                Conversions.byteArrayToInt(
                        Arrays.copyOfRange(uwbConfigBytes, parseCursor,
                                parseCursor + RANGING_INTERVAL_SIZE));
        parseCursor += RANGING_INTERVAL_SIZE;

        // Parse Slot Duration
        int slotDurationMs = uwbConfigBytes[parseCursor];
        parseCursor += SLOT_DURATION_SIZE;

        // Parse Session Key
        int sessionKeyLength = uwbConfigBytes[parseCursor];
        parseCursor += SESSION_KEY_LENGTH_SIZE;

        if (uwbConfigBytes.length < MIN_SIZE_BYTES + sessionKeyLength) {
            throw new IllegalArgumentException(
                    "Failed to parse UwbConfig, invalid size. Bytes: " + Arrays.toString(
                            uwbConfigBytes));
        }
        byte[] sessionKey = Arrays.copyOfRange(uwbConfigBytes, parseCursor, sessionKeyLength);
        parseCursor += sessionKeyLength;

        // Parse Country Code
        String countryCode =
                new String(
                        Arrays.copyOfRange(uwbConfigBytes, parseCursor,
                                parseCursor + COUNTRY_CODE_SIZE));
        parseCursor += COUNTRY_CODE_SIZE;

        // Parse Device Role
        @RangingPreference.DeviceRole int deviceRole = fromOobDeviceRole(
                uwbConfigBytes[parseCursor]);
        parseCursor += DEVICE_ROLE_SIZE;

        // Parse Device Type
        int deviceType = fromOobDeviceType(uwbConfigBytes[parseCursor]);
        parseCursor += DEVICE_TYPE_SIZE;

        if (deviceType != deviceRole) {
            Log.w(TAG, "parsed device role " + deviceRole + " inconsistent with device type "
                    + deviceType + ". Ignoring type and using role.");
        }

        //TODO: check this when implementing smart ranging.
        UwbRangingParams.Builder paramsBuilder = new UwbRangingParams.Builder(
                sessionId, configId, UwbAddress.fromBytes(uwbAddress.toBytes()),
                UwbAddress.fromBytes(new byte[]{0, 0}))
                .setComplexChannel(new UwbComplexChannel.Builder().setChannel(
                        channel).setPreambleIndex(preambleIndex).build())
                .setSlotDuration(slotDurationMs)
                .setSessionKeyInfo(sessionKey);

        for (@RawRangingDevice.RangingUpdateRate int rate =
                UPDATE_RATE_NORMAL;
                rate <= UPDATE_RATE_FREQUENT;
                rate++
        ) {
            if (Utils.getRangingTimingParams(configId).getRangingInterval((int) rate)
                    == rangingIntervalMs
            ) {
                paramsBuilder.setRangingUpdateRate(rate);
            }
        }

        return new Builder(paramsBuilder.build())
                .setCountryCode(countryCode)
                .build();
    }

    /** Serializes this {@link UwbConfig} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getSessionKeyInfoLength();
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.UWB.toByte())
                .put(mParameters.getDeviceAddress().getAddressBytes())
                .put(Conversions.intToByteArray(mParameters.getSessionId(), SESSION_ID_SIZE))
                .put(Conversions.intToByteArray(mParameters.getConfigId(), CONFIG_ID_SIZE))
                .put(Conversions.intToByteArray(mParameters.getComplexChannel().getChannel(),
                        CHANNEL_SIZE))
                .put(Conversions.intToByteArray(mParameters.getComplexChannel().getPreambleIndex(),
                        PREAMBLE_INDEX_SIZE))
                .put(Conversions.intToByteArray(
                        Utils.getRangingTimingParams((int) mParameters.getConfigId())
                                .getRangingInterval((int) mParameters.getRangingUpdateRate()),
                        RANGING_INTERVAL_SIZE))
                .put(Conversions.intToByteArray(
                        mParameters.getSlotDuration(), SLOT_DURATION_SIZE))
                .put(Conversions.intToByteArray(getSessionKeyInfoLength(), SESSION_KEY_LENGTH_SIZE))
                .put(mParameters.getSessionKeyInfo())
                .put(getCountryCode().getBytes(US_ASCII))
                .put(Conversions.intToByteArray(
                        toOobDeviceRole(mDeviceRole), DEVICE_ROLE_SIZE))
                .put(Conversions.intToByteArray(
                        toOobDeviceType(mDeviceRole), DEVICE_TYPE_SIZE))
                .array();
    }

    /** Returns the length of the session key. */
    public final int getSessionKeyInfoLength() {
        if (mParameters.getSessionKeyInfo() == null) {
            return 0;
        } else {
            return mParameters.getSessionKeyInfo().length;
        }
    }

    public @NonNull UwbRangingParams getParameters() {
        return mParameters;
    }

    public @NonNull String getCountryCode() {
        return mCountryCode;
    }

    public @NonNull DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    public int getDeviceRole() {
        return mDeviceRole;
    }

    public boolean isAoaNeeded() {
        return mIsAoaNeeded;
    }

    public @NonNull Pair<RangingDevice, UwbAddress> getPeer() {
        return mPeer;
    }

    /**
     * @return the configuration converted to a
     * {@link androidx.core.uwb.backend.impl.internal.RangingParameters} accepted by the UWB
     * backend.
     */
    public com.android.ranging.uwb.backend.internal.RangingParameters asBackendParameters() {
        List<com.android.ranging.uwb.backend.internal.UwbAddress> peerAddressList =
                new ArrayList<>();
        peerAddressList.add(com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                mParameters.getPeerAddress().getAddressBytes()));
        return new com.android.ranging.uwb.backend.internal.RangingParameters(
                (int) mParameters.getConfigId(),
                mParameters.getSessionId(),
                mParameters.getSubSessionId(),
                mParameters.getSessionKeyInfo(),
                mParameters.getSubSessionKeyInfo(),
                toBackend(mParameters.getComplexChannel()),
                peerAddressList,
                (int) mParameters.getRangingUpdateRate(),
                toBackend(getDataNotificationConfig()),
                (int) mParameters.getSlotDuration(),
                mIsAoaNeeded
        );
    }


    public static int toOobDeviceType(@RangingPreference.DeviceRole int type) {
        switch (type) {
            case DEVICE_ROLE_RESPONDER:
                return 0x02;
            case DEVICE_ROLE_INITIATOR:
                return 0x01;
            default:
                return 0x00;
        }
    }

    public static @RangingPreference.DeviceRole int fromOobDeviceType(int type) {
        switch (type) {
            case 0x01:
                return DEVICE_ROLE_INITIATOR;
            case 0x02:
                return DEVICE_ROLE_RESPONDER;
            default:
                throw new IllegalArgumentException(
                        "Unknown device type with value " + type);
        }
    }

    public static int toOobDeviceRole(@RangingPreference.DeviceRole int role) {
        switch (role) {
            case DEVICE_ROLE_INITIATOR:
                return 0x01;
            case DEVICE_ROLE_RESPONDER:
                return 0x02;
            default:
                return 0x00;
        }
    }

    public static @RangingPreference.DeviceRole int fromOobDeviceRole(int role) {
        switch (role) {
            case 0x01:
                return DEVICE_ROLE_INITIATOR;
            case 0x02:
                return DEVICE_ROLE_RESPONDER;
            default:
                throw new IllegalArgumentException("Unknown device role with value " + role);
        }
    }

    public static @NonNull UwbRangeDataNtfConfig toBackend(
            @NonNull DataNotificationConfig rangeDataNtfConfig
    ) {
        return new UwbRangeDataNtfConfig.Builder()
                .setRangeDataConfigType((int) rangeDataNtfConfig.getNotificationConfigType())
                .setNtfProximityNear(rangeDataNtfConfig.getProximityNearCm())
                .setNtfProximityFar(rangeDataNtfConfig.getProximityFarCm())
                .build();
    }

    public static @NonNull com.android.ranging.uwb.backend.internal.UwbComplexChannel toBackend(
            @NonNull UwbComplexChannel complexChannel
    ) {
        return new com.android.ranging.uwb.backend.internal.UwbComplexChannel(
                (int) complexChannel.getChannel(), (int) complexChannel.getPreambleIndex());
    }

    public static @NonNull com.android.ranging.uwb.backend.internal.UwbAddress toBackend(
            @NonNull UwbAddress address
    ) {
        return com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                address.getAddressBytes());
    }


    /** Builder for {@link UwbConfig}. */
    public static class Builder {
        private final UwbRangingParams mParameters;
        private final RequiredParam<RangingDevice> mPeerDevice = new RequiredParam<>();
        private final RequiredParam<String> mCountryCode = new RequiredParam<>();
        private DataNotificationConfig mDataNotificationConfig =
                new DataNotificationConfig.Builder().build();

        private int mDeviceRole = DEVICE_ROLE_RESPONDER;
        private boolean mIsAoaNeeded = false;

        public Builder(@NonNull UwbRangingParams parameters) {
            mParameters = parameters;
        }

        public @NonNull UwbConfig build() {
            return new UwbConfig(this);
        }

        public Builder setPeerDevice(@NonNull RangingDevice device) {
            mPeerDevice.set(device);
            return this;
        }

        public Builder setCountryCode(@NonNull String countryCode) {
            mCountryCode.set(countryCode);
            return this;
        }

        public Builder setDeviceRole(@RangingPreference.DeviceRole int deviceRole) {
            mDeviceRole = deviceRole;
            return this;
        }

        public Builder setAoaNeeded(boolean isAoaNeeded) {
            mIsAoaNeeded = isAoaNeeded;
            return this;
        }

        public Builder setDataNotificationConfig(@NonNull DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }
    }
}
