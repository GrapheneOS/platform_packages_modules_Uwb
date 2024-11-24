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


import com.android.ranging.uwb.backend.internal.UwbAddress;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Capability data for UWB sent as part of CapabilityResponseMessage. */
@AutoValue
public abstract class UwbCapabilities {

    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 19;

    // Size in bytes for each properties for serialization/deserialization.
    private static final int TECHNOLOGY_ID_SIZE = 1;
    private static final int UWB_ADDRESS_SIZE = 2;
    private static final int CHANNELS_SIZE = 4;
    private static final int PREAMBLES_SIZE = 4;
    private static final int CONFIG_IDS_SIZE = 2;
    private static final int MIN_INTERVAL_SIZE = 4;
    private static final int MIN_SLOT_SIZE = 1;
    private static final int DEVICE_ROLE_SIZE = 1;

    private static final int CHANNELS_SHIFT = 0;
    private static final int PREAMBLES_SHIFT = 1;
    private static final int CONFIG_IDS_SHIFT = 0;
    private static final int DEVICE_ROLE_SHIFT = 1;

    /** Returns the size of the object in bytes when serialized. */
    public static int getSize() {
        return EXPECTED_SIZE_BYTES;
    }

    /**
     * Parses the given byte array and returns {@link UwbCapabilities} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static UwbCapabilities parseBytes(byte[] capabilitiesBytes) {
        if (capabilitiesBytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException("Couldn't parse UwbCapabilities, invalid byte size");
        }

        int parseCursor = 0;
        var technology = RangingTechnology.parseByte(capabilitiesBytes[parseCursor]);
        if (technology.size() != 1 || technology.get(0) != RangingTechnology.UWB) {
            throw new IllegalArgumentException(
                    "Couldn't parse UwbCapabilities, invalid technology id");
        }
        parseCursor += TECHNOLOGY_ID_SIZE;

        // Parse UWB Address
        UwbAddress uwbAddress = UwbAddress.fromBytes(
                Arrays.copyOfRange(capabilitiesBytes, parseCursor, parseCursor + UWB_ADDRESS_SIZE));
        parseCursor += UWB_ADDRESS_SIZE;

        // Parse Supported Channels
        ImmutableList<Integer> supportedChannels =
                Conversions.byteArrayToIntList(
                        Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                parseCursor + CHANNELS_SIZE),
                        CHANNELS_SHIFT);
        parseCursor += CHANNELS_SIZE;

        // Parse Supported Preamble Indexes
        ImmutableList<Integer> supportedPreambleIndexes =
                Conversions.byteArrayToIntList(
                        Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                parseCursor + PREAMBLES_SIZE),
                        PREAMBLES_SHIFT);
        parseCursor += PREAMBLES_SIZE;

        // Parse Supported Config Ids
        ImmutableList<Integer> supportedConfigIds =
                Conversions.byteArrayToIntList(
                        Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                parseCursor + CONFIG_IDS_SIZE),
                        CONFIG_IDS_SHIFT);
        parseCursor += CONFIG_IDS_SIZE;

        // Parse Minimum Ranging Interval Ms
        int minimumRangingIntervalMs =
                Conversions.byteArrayToInt(
                        Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                parseCursor + MIN_INTERVAL_SIZE));
        parseCursor += MIN_INTERVAL_SIZE;

        // Parse Minimum Slot Duration Ms
        int minimumSlotDurationMs =
                Conversions.byteArrayToInt(
                        Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                parseCursor + MIN_SLOT_SIZE));
        parseCursor += MIN_SLOT_SIZE;

        // Parse Device Role
        ImmutableList.Builder<Integer> deviceRoles = new ImmutableList.Builder<>();
        for (byte role : Arrays.copyOfRange(
                capabilitiesBytes, parseCursor, parseCursor + DEVICE_ROLE_SIZE)) {
            deviceRoles.add(UwbConfig.fromOobDeviceRole(role));
        }
        parseCursor += DEVICE_ROLE_SIZE;

        return UwbCapabilities.builder()
                .setUwbAddress(uwbAddress)
                .setSupportedChannels(supportedChannels)
                .setSupportedConfigIds(supportedConfigIds)
                .setSupportedPreambleIndexes(supportedPreambleIndexes)
                .setMinimumRangingIntervalMs(minimumRangingIntervalMs)
                .setMinimumSlotDurationMs(minimumSlotDurationMs)
                .setSupportedDeviceRole(deviceRoles.build())
                .build();
    }

    /** Serializes this {@link UwbCapabilities} object to bytes. */
    public final byte[] toBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
        byteBuffer
                .put(RangingTechnology.UWB.toByte())
                .put(getUwbAddress().toBytes())
                .put(Conversions.intListToByteArrayBitmap(getSupportedChannels(), CHANNELS_SIZE,
                        CHANNELS_SHIFT))
                .put(
                        Conversions.intListToByteArrayBitmap(
                                getSupportedPreambleIndexes(), PREAMBLES_SIZE, PREAMBLES_SHIFT))
                .put(
                        Conversions.intListToByteArrayBitmap(
                                getSupportedConfigIds(), CONFIG_IDS_SIZE, CONFIG_IDS_SHIFT))
                .put(Conversions.intToByteArray(getMinimumRangingIntervalMs(), MIN_INTERVAL_SIZE))
                .put(Conversions.intToByteArray(getMinimumSlotDurationMs(), MIN_SLOT_SIZE))
                .put(
                        Conversions.intListToByteArrayBitmap(
                                getSupportedDeviceRole().stream()
                                        .map(UwbConfig::toOobDeviceRole)
                                        .collect(Collectors.toList()),
                                DEVICE_ROLE_SIZE,
                                DEVICE_ROLE_SHIFT));

        return byteBuffer.array();
    }

    /** Returns the {@link UwbAddress} of the device. */
    public abstract UwbAddress getUwbAddress();

    /** Returns a list of supported channels. */
    public abstract ImmutableList<Integer> getSupportedChannels();

    /** Returns a list of supported preamble indexes. */
    public abstract ImmutableList<Integer> getSupportedPreambleIndexes();

    /** Returns a list of supported config Ids. */
    public abstract ImmutableList<Integer> getSupportedConfigIds();

    /** Returns minimum supported ranging interval in ms. */
    public abstract int getMinimumRangingIntervalMs();

    /** Returns minimum supported slot duration in ms. */
    public abstract int getMinimumSlotDurationMs();

    /** Returns supported device roles. */
    public abstract ImmutableList<Integer> getSupportedDeviceRole();

    /** Returns a builder for {@link UwbCapabilities}. */
    public static Builder builder() {
        return new AutoValue_UwbCapabilities.Builder();
    }

    /** Builder for {@link UwbCapabilities}. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setUwbAddress(UwbAddress uwbAddress);

        public abstract Builder setSupportedChannels(ImmutableList<Integer> supportedChannels);

        public abstract Builder setSupportedPreambleIndexes(
                ImmutableList<Integer> supportedPreambleIndexes);

        public abstract Builder setSupportedConfigIds(ImmutableList<Integer> supportedConfigIds);

        public abstract Builder setMinimumRangingIntervalMs(int minimumRangingIntervalMs);

        public abstract Builder setMinimumSlotDurationMs(int minimumSlotDurationMs);

        public abstract Builder setSupportedDeviceRole(
                ImmutableList<Integer> supportedDeviceRole);

        public abstract UwbCapabilities build();
    }

    @Override
    public String toString() {
        return "UwbCapabilities{ "
                + "uwbAddress="
                + getUwbAddress()
                + ", supportedChannels="
                + getSupportedChannels()
                + ", supportedPreambleIndexes="
                + getSupportedPreambleIndexes()
                + ", supportedConfigIds="
                + getSupportedConfigIds()
                + ", minimumRangingIntervalMs="
                + getMinimumRangingIntervalMs()
                + ", minimumSlotDurationMs="
                + getMinimumSlotDurationMs()
                + ", supportedDeviceRole="
                + getSupportedDeviceRole()
                + " }";
    }
}
