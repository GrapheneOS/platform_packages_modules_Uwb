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

import static com.google.common.collect.ImmutableList.toImmutableList;

import android.ranging.RangingCapabilities;
import android.ranging.RangingManager;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingCapabilities;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Capability data for UWB sent as part of CapabilityResponseMessage. */
@AutoValue
public abstract class UwbOobCapabilities implements RangingCapabilities.TechnologyCapabilities {

    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 20;

    // Size in bytes for each properties for serialization/deserialization.
    private static final int UWB_ADDRESS_SIZE = 2;
    private static final int CHANNELS_SIZE = 4;
    private static final int PREAMBLES_SIZE = 4;
    private static final int CONFIG_IDS_SIZE = 4;
    private static final int MIN_INTERVAL_SIZE = 2;
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

    @Override
    public @RangingManager.RangingTechnology int getTechnology() {
        return RangingTechnology.UWB.getValue();
    }

    /**
     * Parses the given byte array and returns {@link UwbOobCapabilities} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static UwbOobCapabilities parseBytes(byte[] capabilitiesBytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(capabilitiesBytes);

        if (capabilitiesBytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "UwbCapabilities size is %d, expected at least %d",
                            capabilitiesBytes.length, EXPECTED_SIZE_BYTES));
        }

        if (capabilitiesBytes.length < header.getSize()) {
            throw new IllegalArgumentException(
                    String.format(
                            "UwbCapabilities header size field is %d, but the size of the array "
                                    + "is %d",
                            header.getSize(), capabilitiesBytes.length));
        }

        if (header.getRangingTechnology() != RangingTechnology.UWB) {
            throw new IllegalArgumentException(
                    String.format(
                            "UwbCapabilities header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.UWB));
        }

        int parseCursor = header.getHeaderSize();

        // Parse UWB Address
        UwbAddress uwbAddress =
                UwbAddress.fromBytes(
                        Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                parseCursor + UWB_ADDRESS_SIZE));
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
        ImmutableList<Integer> deviceRoles =
                Conversions.byteArrayToIntList(
                                Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                        parseCursor + DEVICE_ROLE_SIZE),
                                DEVICE_ROLE_SHIFT)
                        .stream()
                        .collect(toImmutableList());
        parseCursor += DEVICE_ROLE_SIZE;

        return UwbOobCapabilities.builder()
                .setUwbAddress(uwbAddress)
                .setSupportedChannels(supportedChannels)
                .setSupportedConfigIds(supportedConfigIds)
                .setSupportedPreambleIndexes(supportedPreambleIndexes)
                .setMinimumRangingIntervalMs(minimumRangingIntervalMs)
                .setMinimumSlotDurationMs(minimumSlotDurationMs)
                .setSupportedDeviceRole(deviceRoles)
                .build();
    }

    /** Serializes this {@link UwbOobCapabilities} object to bytes. */
    public final byte[] toBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
        byteBuffer
                .put(RangingTechnology.UWB.toByte())
                .put((byte) EXPECTED_SIZE_BYTES)
                .put(getUwbAddress().getAddressBytes())
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
                                        .collect(toImmutableList()),
                                DEVICE_ROLE_SIZE,
                                DEVICE_ROLE_SHIFT));

        return byteBuffer.array();
    }

    public static UwbOobCapabilities fromRangingCapabilities(
            UwbRangingCapabilities capabilities, UwbAddress address
    ) {
        return UwbOobCapabilities.builder()
                .setUwbAddress(address)
                .setSupportedChannels(
                        ImmutableList.copyOf(capabilities.getSupportedChannels()))
                .setSupportedPreambleIndexes(
                        ImmutableList.copyOf(capabilities.getSupportedPreambleIndexes()))
                .setSupportedConfigIds(
                        ImmutableList.copyOf(capabilities.getSupportedConfigIds()))
                .setMinimumRangingIntervalMs(
                        (int) capabilities.getMinimumRangingInterval().toMillis())
                .setMinimumSlotDurationMs(capabilities.getSupportedSlotDurations()
                        .stream().min(Integer::compare).get())
                .setSupportedDeviceRole(ImmutableList.of(
                        UwbOobConfig.OobDeviceRole.INITIATOR,
                        UwbOobConfig.OobDeviceRole.RESPONDER))
                .build();
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

    /** Returns a builder for {@link UwbOobCapabilities}. */
    public static Builder builder() {
        return new AutoValue_UwbOobCapabilities.Builder();
    }

    /** Builder for {@link UwbOobCapabilities}. */
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

        public abstract UwbOobCapabilities build();
    }
}
