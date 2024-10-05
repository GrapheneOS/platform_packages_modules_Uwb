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

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.ranging.DataNotificationConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbParameters;

import androidx.annotation.NonNull;

import com.android.ranging.uwb.backend.internal.RangingParameters;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbRangeDataNtfConfig;
import com.android.server.ranging.RangingConfig.TechnologyConfig;
import com.android.server.ranging.RangingParameters.DeviceRole;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.base.RequiredParam;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A complete configuration for UWB ranging. This encapsulates all information contained in a
 * configuration message sent over OOB and everything required to start a session in the underlying
 * UWB system API.
 */
public class UwbConfig extends UwbParameters implements TechnologyConfig {
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

    private final DeviceRole mDeviceRole;
    private final com.android.ranging.uwb.backend.internal.UwbAddress mLocalAddress;
    private final String mCountryCode;
    private final DataNotificationConfig mDataNotificationConfig;
    private final UwbComplexChannel mComplexChannel;

    private UwbConfig(Builder builder) {
        super(builder.mParameters);
        mDeviceRole = builder.mDeviceRole.get();
        mLocalAddress = builder.mLocalAddress.get();
        mCountryCode = builder.mCountryCode.get();
        mDataNotificationConfig = builder.mDataNotificationConfig;
        mComplexChannel = builder.mComplexChannel.get();
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
        DeviceRole deviceRole = fromOobDeviceRole(uwbConfigBytes[parseCursor]);
        parseCursor += DEVICE_ROLE_SIZE;

        // Parse Device Type
        int deviceType = uwbConfigBytes[parseCursor];
        parseCursor += DEVICE_TYPE_SIZE;

        UwbParameters.Builder paramsBuilder = new UwbParameters.Builder()
                .setSessionId(sessionId)
                .setConfigId(configId)
                .setSlotDurationMs(slotDurationMs)
                .setSessionKeyInfo(sessionKey);


        for (@UwbParameters.RangingUpdateRate int rate =
                UwbParameters.RangingUpdateRate.NORMAL;
                rate <= UwbParameters.RangingUpdateRate.FAST;
                rate++
        ) {
            if (Utils.getRangingTimingParams(configId).getRangingInterval((int) rate)
                    == rangingIntervalMs
            ) {
                paramsBuilder.setRangingUpdateRate(rate);
            }
        }

        return new Builder(paramsBuilder.build())
                .setLocalAddress(uwbAddress)
                .setCountryCode(countryCode)
                .setComplexChannel(new UwbComplexChannel(channel, preambleIndex))
                .build();
    }

    /** Serializes this {@link UwbConfig} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getSessionKeyInfoLength();
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.UWB.toByte())
                .put(getLocalAddress().toBytes())
                .put(Conversions.intToByteArray(getSessionId(), SESSION_ID_SIZE))
                .put(Conversions.intToByteArray(getConfigId(), CONFIG_ID_SIZE))
                .put(Conversions.intToByteArray(getComplexChannel().getChannel(), CHANNEL_SIZE))
                .put(Conversions.intToByteArray(getComplexChannel().getPreambleIndex(),
                        PREAMBLE_INDEX_SIZE))
                .put(Conversions.intToByteArray(
                        Utils.getRangingTimingParams((int) getConfigId())
                                .getRangingInterval((int) getRangingUpdateRate()),
                        RANGING_INTERVAL_SIZE))
                .put(Conversions.intToByteArray(getSlotDurationMs(), SLOT_DURATION_SIZE))
                .put(Conversions.intToByteArray(getSessionKeyInfoLength(), SESSION_KEY_LENGTH_SIZE))
                .put(getSessionKeyInfo())
                .put(getCountryCode().getBytes(US_ASCII))
                .put(Conversions.intToByteArray(
                        toOobDeviceRole(getDeviceRole()), DEVICE_ROLE_SIZE))
                .put(Conversions.intToByteArray(
                        toOobDeviceType(getDeviceType()), DEVICE_TYPE_SIZE))
                .array();
    }

    /** Returns the length of the session key. */
    public final int getSessionKeyInfoLength() {
        if (getSessionKeyInfo() == null) {
            return 0;
        } else {
            return getSessionKeyInfo().length;
        }
    }

    /** Returns the device's role within the session. */
    public @NonNull DeviceRole getDeviceRole() {
        return mDeviceRole;
    }

    public @NonNull com.android.ranging.uwb.backend.internal.UwbAddress getLocalAddress() {
        return mLocalAddress;
    }

    public @NonNull String getCountryCode() {
        return mCountryCode;
    }

    public @NonNull DataNotificationConfig getDataNotificationConfig() {
        return mDataNotificationConfig;
    }

    @Override
    public @NonNull UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    /**
     * @return the configuration converted to a
     * {@link androidx.core.uwb.backend.impl.internal.RangingParameters} accepted by the UWB
     * backend.
     */
    public RangingParameters asBackendParameters() {
        return new RangingParameters(
                (int) getConfigId(),
                getSessionId(),
                getSubSessionId(),
                getSessionKeyInfo(),
                getSubSessionKeyInfo(),
                toBackend(getComplexChannel()),
                getPeerAddresses().values().stream().map(UwbConfig::toBackend).toList(),
                (int) getRangingUpdateRate(),
                toBackend(getDataNotificationConfig()),
                (int) getSlotDurationMs(),
                isAoaDisabled()
        );
    }

    public static int toOobDeviceRole(@NonNull DeviceRole role) {
        switch (role) {
            case INITIATOR: return 0x01;
            case RESPONDER: return 0x02;
            default: return 0x00;
        }
    }

    public static @NonNull DeviceRole fromOobDeviceRole(int role) {
        switch (role) {
            case 0x01: return DeviceRole.INITIATOR;
            case 0x02: return DeviceRole.RESPONDER;
            default: throw new IllegalArgumentException("Unknown device role with value " + role);
        }
    }

    public static int toOobDeviceType(@UwbParameters.DeviceType int type) {
        switch (type) {
            case UwbParameters.DeviceType.CONTROLEE: return 0x02;
            case UwbParameters.DeviceType.CONTROLLER: return 0x01;
            default: return 0x00;
        }
    }

    public static @UwbParameters.DeviceType int fromOobDeviceType(int type) {
        switch (type) {
            case 0x01: return UwbParameters.DeviceType.CONTROLLER;
            case 0x02: return UwbParameters.DeviceType.CONTROLEE;
            default: throw new IllegalArgumentException(
                    "Unknown device type with value " + type);
        }
    }

    public static @NonNull UwbRangeDataNtfConfig toBackend(
            @NonNull DataNotificationConfig rangeDataNtfConfig
    ) {
        return new UwbRangeDataNtfConfig.Builder()
                .setRangeDataConfigType((int) rangeDataNtfConfig.getRangeDataNtfConfigType())
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
        return com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(address.toBytes());
    }


    /** Builder for {@link UwbConfig}. */
    public static class Builder {
        private final UwbParameters mParameters;
        private final RequiredParam<DeviceRole> mDeviceRole = new RequiredParam<>();
        private final RequiredParam<com.android.ranging.uwb.backend.internal.UwbAddress>
                mLocalAddress = new RequiredParam<>();
        private final RequiredParam<String> mCountryCode = new RequiredParam<>();
        private DataNotificationConfig mDataNotificationConfig =
                new DataNotificationConfig.Builder().build();

        private final RequiredParam<UwbComplexChannel> mComplexChannel = new RequiredParam<>();


        public Builder(@NonNull UwbParameters parameters) {
            mParameters = parameters;
        }

        public @NonNull UwbConfig build() {
            return new UwbConfig(this);
        }

        public Builder setDeviceRole(@NonNull DeviceRole role) {
            mDeviceRole.set(role);
            return this;
        }

        public Builder setLocalAddress(
                @NonNull com.android.ranging.uwb.backend.internal.UwbAddress address
        ) {
            mLocalAddress.set(address);
            return this;
        }

        public Builder setCountryCode(@NonNull String countryCode) {
            mCountryCode.set(countryCode);
            return this;
        }

        public Builder setDataNotificationConfig(@NonNull DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }

        public Builder setComplexChannel(@NonNull UwbComplexChannel complexChannel) {
            mComplexChannel.set(complexChannel);
            return this;
        }
    }
}
