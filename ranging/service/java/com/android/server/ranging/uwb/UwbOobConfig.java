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

import android.ranging.uwb.UwbAddress;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Configuration for UWB sent as part SetConfigurationMessage for Finder OOB. */
@AutoValue
public abstract class UwbOobConfig {

    private static final int MIN_SIZE_BYTES = 19;

    // Size in bytes for properties when serialized.
    private static final int UWB_ADDRESS_SIZE = 2;
    private static final int SESSION_ID_SIZE = 4;
    private static final int CONFIG_ID_SIZE = 1;
    private static final int CHANNEL_SIZE = 1;
    private static final int PREAMBLE_INDEX_SIZE = 1;
    private static final int RANGING_INTERVAL_SIZE = 2;
    private static final int SLOT_DURATION_SIZE = 1;
    private static final int SESSION_KEY_LENGTH_SIZE = 1;
    private static final int STS_SESSION_KEY_SIZE = 8;
    private static final int PSTS_SHORT_SESSION_KEY_SIZE = 16;
    private static final int PSTS_LONG_SESSION_KEY_SIZE = 32;
    private static final int COUNTRY_CODE_SIZE = 2;
    private static final int DEVICE_ROLE_SIZE = 1;
    private static final int DEVICE_MODE_SIZE = 1;

    /** Returns the size of the object in bytes when serialized. */
    public final int getSize() {
        return MIN_SIZE_BYTES + getSessionKeyLength();
    }

    /**
     * Parses the given byte array and returns {@link UwbConfig} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static UwbOobConfig parseBytes(byte[] uwbConfigBytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(uwbConfigBytes);

        if (uwbConfigBytes.length < MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("UwbConfig size is %d, expected at least %d",
                            uwbConfigBytes.length, MIN_SIZE_BYTES));
        }

        if (uwbConfigBytes.length < header.getSize()) {
            throw new IllegalArgumentException(
                    String.format(
                            "UwbConfig header size field is %d, but the size of the array is %d",
                            header.getSize(), uwbConfigBytes.length));
        }

        if (header.getRangingTechnology() != RangingTechnology.UWB) {
            throw new IllegalArgumentException(
                    String.format(
                            "UwbConfig header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.UWB));
        }

        int parseCursor = header.getHeaderSize();

        // Parse Uwb Address
        UwbAddress uwbAddress =
                UwbAddress.fromBytes(Arrays.copyOfRange(
                        uwbConfigBytes, parseCursor, parseCursor + UWB_ADDRESS_SIZE));
        parseCursor += UWB_ADDRESS_SIZE;

        // Parse Session Id
        int sessionId =
                Conversions.byteArrayToInt(Arrays.copyOfRange(
                        uwbConfigBytes, parseCursor, parseCursor + SESSION_ID_SIZE));
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
                Conversions.byteArrayToInt(Arrays.copyOfRange(
                        uwbConfigBytes, parseCursor, parseCursor + RANGING_INTERVAL_SIZE));
        parseCursor += RANGING_INTERVAL_SIZE;

        // Parse Slot Duration
        int slotDurationMs = uwbConfigBytes[parseCursor];
        parseCursor += SLOT_DURATION_SIZE;

        // Parse Session Key
        int sessionKeyLength = uwbConfigBytes[parseCursor];
        parseCursor += SESSION_KEY_LENGTH_SIZE;

        if (uwbConfigBytes.length < MIN_SIZE_BYTES + sessionKeyLength) {
            throw new IllegalArgumentException(
                    "Failed to parse UwbConfig, invalid size. Bytes: "
                            + Arrays.toString(uwbConfigBytes));
        }
        byte[] sessionKey =
                Arrays.copyOfRange(uwbConfigBytes, parseCursor, parseCursor + sessionKeyLength);
        parseCursor += sessionKeyLength;

        // Parse Country Code
        String countryCode =
                new String(Arrays.copyOfRange(
                        uwbConfigBytes, parseCursor, parseCursor + COUNTRY_CODE_SIZE));
        parseCursor += COUNTRY_CODE_SIZE;

        // Parse Device Role
        int deviceRole = uwbConfigBytes[parseCursor];
        parseCursor += DEVICE_ROLE_SIZE;

        // Parse Device Mode
        int deviceMode = uwbConfigBytes[parseCursor];
        parseCursor += DEVICE_MODE_SIZE;

        return builder()
                .setUwbAddress(uwbAddress)
                .setSessionId(sessionId)
                .setSelectedConfigId(configId)
                .setSelectedChannel(channel)
                .setSelectedPreambleIndex(preambleIndex)
                .setSelectedRangingIntervalMs(rangingIntervalMs)
                .setSelectedSlotDurationMs(slotDurationMs)
                .setSessionKey(sessionKey)
                .setCountryCode(countryCode)
                .setDeviceRole(deviceRole)
                .setDeviceMode(deviceMode)
                .build();
    }

    /** Serializes this {@link UwbConfig} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getSessionKeyLength();
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.UWB.toByte())
                .put((byte) size)
                .put(getUwbAddress().getAddressBytes())
                .put(Conversions.intToByteArray(getSessionId(), SESSION_ID_SIZE))
                .put(Conversions.intToByteArray(getSelectedConfigId(), CONFIG_ID_SIZE))
                .put(Conversions.intToByteArray(getSelectedChannel(), CHANNEL_SIZE))
                .put(Conversions.intToByteArray(getSelectedPreambleIndex(), PREAMBLE_INDEX_SIZE))
                .put(Conversions.intToByteArray(
                        getSelectedRangingIntervalMs(), RANGING_INTERVAL_SIZE))
                .put(Conversions.intToByteArray(getSelectedSlotDurationMs(), SLOT_DURATION_SIZE))
                .put(Conversions.intToByteArray(getSessionKeyLength(), SESSION_KEY_LENGTH_SIZE))
                .put(getSessionKey())
                .put(getCountryCode().getBytes(US_ASCII))
                .put(Conversions.intToByteArray(getDeviceRole(), DEVICE_ROLE_SIZE))
                .put(Conversions.intToByteArray(getDeviceMode(), DEVICE_MODE_SIZE))
                .array();
    }

    /** Returns {@link UwbAddress} of the device. */
    public abstract UwbAddress getUwbAddress();

    /** Returns the session Id. */
    public abstract int getSessionId();

    /** Returns the selected config Id. */
    public abstract int getSelectedConfigId();

    /** Returns the selected channel. */
    public abstract int getSelectedChannel();

    /** Returns the selected preamble index. */
    public abstract int getSelectedPreambleIndex();

    /** Returns the selected ranging interval in ms. */
    public abstract int getSelectedRangingIntervalMs();

    /** Returns the selected slot duration in ms. */
    public abstract int getSelectedSlotDurationMs();

    /** Returns the length of the session key. */
    public final int getSessionKeyLength() {
        return getSessionKey().length;
    }

    /**
     * Returns the session key bytes. If S-STS is used then first two bytes are VENDOR ID and
     * following 6 bytes are STATIC STS IV. If P-STS is used then this is either a 16 byte or 32
     * byte session key.
     */
    @SuppressWarnings("mutable")
    public abstract byte[] getSessionKey();

    /** Returns ISO 3166-1 alpha-2 country code, represented by 2 ascii characters */
    public abstract String getCountryCode();

    /** Returns Device Role. */
    public abstract @OobDeviceRole int getDeviceRole();

    /** Returns Device Mode. */
    public abstract @OobDeviceMode int getDeviceMode();

    /** Returns a builder for {@link UwbConfig}. */
    public static Builder builder() {
        return new AutoValue_UwbOobConfig.Builder().setSessionKey(new byte[] {});
    }

    public @interface OobDeviceMode {
        int UNKNOWN = 0;
        int CONTROLLER = 1;
        int CONTROLEE = 2;
    }

    public @interface OobDeviceRole {
        int UNKNOWN = 0;
        int INITIATOR = 1;
        int RESPONDER = 2;
    }

    /** Builder for {@link UwbConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setUwbAddress(UwbAddress uwbAddress);

        public abstract Builder setSessionId(int sessionId);

        public abstract Builder setSelectedConfigId(int selectedConfigId);

        public abstract Builder setSelectedChannel(int selectedChannel);

        public abstract Builder setSelectedPreambleIndex(int selectedPreambleIndex);

        public abstract Builder setSelectedRangingIntervalMs(int selectedRangingIntervalMs);

        public abstract Builder setSelectedSlotDurationMs(int selectedSlotDurationMs);

        public abstract Builder setSessionKey(byte[] sessionKey);

        public abstract Builder setCountryCode(String countryCode);

        public abstract Builder setDeviceRole(@OobDeviceRole int deviceRole);

        public abstract Builder setDeviceMode(@OobDeviceMode int deviceMode);

        abstract UwbOobConfig autoBuild();

        public UwbOobConfig build() {
            UwbOobConfig uwbConfig = autoBuild();
            Preconditions.checkNotNull(uwbConfig.getUwbAddress(), "UwbAddress cannot be null");
            int sessionKeyLength = uwbConfig.getSessionKeyLength();
            Preconditions.checkArgument(
                    sessionKeyLength == STS_SESSION_KEY_SIZE
                            || sessionKeyLength == PSTS_SHORT_SESSION_KEY_SIZE
                            || sessionKeyLength == PSTS_LONG_SESSION_KEY_SIZE,
                    "Invalid session key length");
            Preconditions.checkArgument(
                    uwbConfig.getCountryCode().length() == COUNTRY_CODE_SIZE,
                    "Invalid country code length");
            return uwbConfig;
        }
    }
}
