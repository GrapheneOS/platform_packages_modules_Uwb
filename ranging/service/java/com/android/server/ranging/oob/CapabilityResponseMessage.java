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
import com.android.server.ranging.cs.CsOobCapabilities;
import com.android.server.ranging.rtt.RttOobCapabilities;
import com.android.server.ranging.uwb.UwbOobCapabilities;

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
    private static final int MIN_SIZE_BYTES = 2;

    private static final int RANGING_TECHNOLOGIES_SIZE_BYTES = 2;

    /**
     * Parses the given byte array and returns {@link CapabilityResponseMessage} object. Throws
     * {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CapabilityResponseMessage parseBytes(byte[] payload) {
        OobHeader header = OobHeader.parseBytes(payload);

        if (header.getMessageType() != MessageType.CAPABILITY_RESPONSE) {
            throw new IllegalArgumentException(
                    String.format("Invalid message type: %s, expected %s",
                            header.getMessageType(), MessageType.CAPABILITY_RESPONSE));
        }

        if (payload.length < header.getSize() + MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format(
                    "CapabilityResponseMessage payload size is %d bytes", payload.length));
        }

        int parseCursor = header.getSize();

        // Parse ranging technologies bitfield
        byte[] rangingTechnologiesBytes =
                Arrays.copyOfRange(payload, parseCursor,
                        parseCursor + RANGING_TECHNOLOGIES_SIZE_BYTES);
        ImmutableList<RangingTechnology> rangingTechnologies =
                RangingTechnology.fromBitmap(rangingTechnologiesBytes);
        parseCursor += RANGING_TECHNOLOGIES_SIZE_BYTES;

        // Parse Capability data for different ranging technologies
        UwbOobCapabilities uwbCapabilities = null;
        CsOobCapabilities csCapabilities = null;
        RttOobCapabilities rttCapabilities = null;
        ImmutableList.Builder<RangingTechnology> rangingTechnologiesPriority =
                ImmutableList.builder();
        int countTechsParsed = 0;
        while (parseCursor < payload.length && countTechsParsed++ < rangingTechnologies.size()) {
            byte[] remainingBytes = Arrays.copyOfRange(payload, parseCursor, payload.length);
            TechnologyHeader techHeader = TechnologyHeader.parseBytes(remainingBytes);
            switch (techHeader.getRangingTechnology()) {
                case UWB:
                    uwbCapabilities = UwbOobCapabilities.parseBytes(remainingBytes);
                    parseCursor += techHeader.getSize();
                    rangingTechnologiesPriority.add(RangingTechnology.UWB);

                    break;
                case CS:
                    csCapabilities = CsOobCapabilities.parseBytes(remainingBytes);
                    parseCursor += techHeader.getSize();
                    rangingTechnologiesPriority.add(RangingTechnology.CS);
                    break;
                case RTT:
                    rttCapabilities = RttOobCapabilities.parseBytes(remainingBytes);
                    parseCursor += techHeader.getSize();
                    rangingTechnologiesPriority.add(RangingTechnology.RTT);
                    break;

                default:
                    rangingTechnologiesPriority.add(techHeader.getRangingTechnology());
                    parseCursor += techHeader.getSize();
                    break;
            }
        }

        return CapabilityResponseMessage.builder()
                .setHeader(header)
                .setSupportedRangingTechnologies(rangingTechnologies)
                .setUwbCapabilities(uwbCapabilities)
                .setCsCapabilities(csCapabilities)
                .setRttCapabilities(rttCapabilities)
                .setRangingTechnologiesPriority(rangingTechnologiesPriority.build())
                .build();
    }

    /** Serializes this {@link CapabilityResponseMessage} object to bytes. */
    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + getHeader().getSize();
        if (getUwbCapabilities() != null) {
            size += UwbOobCapabilities.getSize();
        }
        if (getRttCapabilities() != null) {
            size += getRttCapabilities().toBytes().length;
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer
                .put(getHeader().toBytes())
                .put(RangingTechnology.toBitmap(getSupportedRangingTechnologies()));
        for (RangingTechnology tech : getRangingTechnologiesPriority()) {
            switch (tech) {
                case UWB:
                    UwbOobCapabilities uwbCapabilities = getUwbCapabilities();
                    if (uwbCapabilities != null) {
                        byteBuffer.put(uwbCapabilities.toBytes());
                    }
                    break;
                case CS:
                    CsOobCapabilities csCapabilities = getCsCapabilities();
                    if (csCapabilities != null) {
                        byteBuffer.put(csCapabilities.toBytes());
                    }
                    break;
                case RTT:
                    RttOobCapabilities rttCapabilities = getRttCapabilities();
                    if (rttCapabilities != null) {
                        byteBuffer.put(rttCapabilities.toBytes());
                    }
                    break;
                default:
                    throw new UnsupportedOperationException("Not implemented");
            }
        }
        return byteBuffer.array();
    }

    /** Returns the OOB header. */
    public abstract OobHeader getHeader();

    /** Returns the supported ranging technologies. */
    public abstract ImmutableList<RangingTechnology> getSupportedRangingTechnologies();

    /**
     * Returns the priority of requested ranging technologies, with earlier items in the list being
     * of
     * higher priority.
     */
    public abstract ImmutableList<RangingTechnology> getRangingTechnologiesPriority();

    /** Returns an Optional of UWB capability data. */
    @Nullable
    public abstract UwbOobCapabilities getUwbCapabilities();

    /** Returns an Optional of CS capability data. */
    @Nullable
    public abstract CsOobCapabilities getCsCapabilities();

    @Nullable
    public abstract RttOobCapabilities getRttCapabilities();

    /** Returns a builder for {@link CapabilityResponseMessage}. */
    public static Builder builder() {
        return new AutoValue_CapabilityResponseMessage.Builder()
                .setRangingTechnologiesPriority(ImmutableList.of());
    }

    /** Builder for {@link CapabilityResponseMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setHeader(OobHeader header);

        public abstract Builder setSupportedRangingTechnologies(
                ImmutableList<RangingTechnology> rangingTechnologies);

        public abstract Builder setUwbCapabilities(@Nullable UwbOobCapabilities uwbCapabilities);

        public abstract Builder setCsCapabilities(@Nullable CsOobCapabilities csCapabilities);

        public abstract Builder setRttCapabilities(@Nullable RttOobCapabilities rttCapabilities);

        public abstract Builder setRangingTechnologiesPriority(
                ImmutableList<RangingTechnology> rangingTechnologiesPriority
        );

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
