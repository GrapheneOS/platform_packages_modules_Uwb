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

package com.android.server.ranging.oob;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.cs.CsCapabilities;
import com.android.server.ranging.uwb.UwbCapabilities;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.annotation.Nullable;

/** The Capability Response Message Additional Data for Finder OOB. */
@AutoValue
public abstract class CapabilityResponseMessage {

    // Size of properties in bytes when serialized.
    private static final int MIN_SIZE_BYTES = 1;
    private static final int PROTOCOL_VERSION_SIZE = 1;

    /**
     * Parses the given byte array and returns {@link CapabilityResponseMessage} object. Throws
     * {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CapabilityResponseMessage parseBytes(byte[] capabilityResponseBytes) {
        if (capabilityResponseBytes.length < MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Failed to parse Capability Response Message, Invalid size. Bytes:"
                            + Arrays.toString(capabilityResponseBytes));
        }

        // Parse SupportedProtocolVersion
        int parseCursor = 0;
        int supportedProtocolVersion = capabilityResponseBytes[parseCursor];
        parseCursor += PROTOCOL_VERSION_SIZE;

        // Parse Capability data for different ranging technologies
        UwbCapabilities uwbCapabilities = null;
        CsCapabilities csCapabilities = null;
        ImmutableList.Builder<RangingTechnology> rangingTechnologiesPriority =
                ImmutableList.builder();
        while (parseCursor < capabilityResponseBytes.length) {
            ImmutableList<RangingTechnology> tech =
                    RangingTechnology.parseByte(capabilityResponseBytes[parseCursor]);
            if (tech.size() != 1) {
                throw new IllegalArgumentException(
                        "Failed to parse Capability Response Message, Invalid ranging technology "
                                + "Id. Bytes:"
                                + Arrays.toString(capabilityResponseBytes));
            }
            switch (tech.get(0)) {
                case UWB:
                    uwbCapabilities =
                            UwbCapabilities.parseBytes(
                                    Arrays.copyOfRange(
                                            capabilityResponseBytes, parseCursor,
                                            capabilityResponseBytes.length));
                    parseCursor += UwbCapabilities.getSize();
                    rangingTechnologiesPriority.add(RangingTechnology.UWB);

                    break;
                case CS:
                    // rangingTechnologiesPriority.add(RangingTechnology.CS);
                    throw new UnsupportedOperationException("Not implemented");
            }
        }

        return CapabilityResponseMessage.builder()
                .setSupportedProtocolVersion(supportedProtocolVersion)
                .setUwbCapabilities(uwbCapabilities)
                .setCsCapabilities(csCapabilities)
                .setRangingTechnologiesPriority(rangingTechnologiesPriority.build())
                .build();
    }

    /** Serializes this {@link CapabilityResponseMessage} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES;
        if (getUwbCapabilities() != null) {
            size += UwbCapabilities.getSize();
        }
        // if (csCapabilities != null) {
        //   size += csCapabilities.getSize();
        // }
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer.put((byte) getSupportedProtocolVersion());
        for (RangingTechnology tech : getRangingTechnologiesPriority()) {
            switch (tech) {
                case UWB:
                    UwbCapabilities uwbCapabilities = getUwbCapabilities();
                    if (uwbCapabilities != null) {
                        byteBuffer.put(uwbCapabilities.toBytes());
                    }
                    break;
                case CS:
                    throw new UnsupportedOperationException("Not implemented");
            }
        }
        return byteBuffer.array();
    }

    /** Returns the supported protocol version. */
    public abstract int getSupportedProtocolVersion();

    /**
     * Returns the priority of requested ranging technologies, with earlier items in the list being
     * of
     * higher priority.
     */
    public abstract ImmutableList<RangingTechnology> getRangingTechnologiesPriority();

    /** Returns an Optional of UWB capability data. */
    @Nullable
    public abstract UwbCapabilities getUwbCapabilities();

    /** Returns an Optional of CS capability data. */
    @Nullable
    public abstract CsCapabilities getCsCapabilities();

    /** Returns a builder for {@link CapabilityResponseMessage}. */
    public static Builder builder() {
        return new AutoValue_CapabilityResponseMessage.Builder()
                .setRangingTechnologiesPriority(ImmutableList.of());
    }

    /** Builder for {@link CapabilityResponseMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setSupportedProtocolVersion(int supportedProtocolVersion);

        public abstract Builder setUwbCapabilities(@Nullable UwbCapabilities uwbCapabilities);

        public abstract Builder setCsCapabilities(@Nullable CsCapabilities csCapabilities);

        public abstract Builder setRangingTechnologiesPriority(
                ImmutableList<RangingTechnology> rangingTechnologiesPriority);

        abstract CapabilityResponseMessage autoBuild();

        public final CapabilityResponseMessage build() {
            CapabilityResponseMessage capabilityResponseMessage = autoBuild();
            Preconditions.checkArgument(
                    (capabilityResponseMessage
                            .getRangingTechnologiesPriority()
                            .contains(RangingTechnology.UWB)
                            == (capabilityResponseMessage.getUwbCapabilities() != null)),
                    "Priority list doesn't match UWB capabilities set.");
            Preconditions.checkArgument(
                    (capabilityResponseMessage.getRangingTechnologiesPriority().contains(
                            RangingTechnology.CS)
                            == (capabilityResponseMessage.getCsCapabilities() != null)),
                    "Priority list doesn't match CS capabilities set.");
            Preconditions.checkArgument(
                    capabilityResponseMessage.getRangingTechnologiesPriority().size()
                            == Sets.newEnumSet(
                                    capabilityResponseMessage.getRangingTechnologiesPriority(),
                                    RangingTechnology.class)
                            .size(),
                    "Priority list contains duplicates.");
            return capabilityResponseMessage;
        }
    }
}
