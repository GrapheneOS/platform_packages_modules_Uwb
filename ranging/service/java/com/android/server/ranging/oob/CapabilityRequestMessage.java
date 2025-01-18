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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** The Capability Request Message Additional Data for Finder OOB. */
@AutoValue
public abstract class CapabilityRequestMessage {

    // Size of capability specific payload.
    private static final int CAPABILITY_SIZE_BYTES = 2;

    /**
     * Parses the given byte array and returns {@link CapabilityRequestMessage} object. Throws
     * {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CapabilityRequestMessage parseBytes(byte[] payload) {
        OobHeader header = OobHeader.parseBytes(payload);

        if (payload.length < header.getSize() + CAPABILITY_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("CapabilityRequestMessage payload size is %d bytes",
                            payload.length));
        }

        int parseCursor = header.getSize();
        byte[] capabilityBytes =
                Arrays.copyOfRange(payload, parseCursor, parseCursor + CAPABILITY_SIZE_BYTES);
        ImmutableSet<RangingTechnology> rangingTechnologies =
                ImmutableSet.copyOf(RangingTechnology.fromBitmap(capabilityBytes));

        return builder()
                .setHeader(header)
                .setRequestedRangingTechnologies(rangingTechnologies)
                .build();
    }

    /** Serializes this {@link CapabilityRequestMessage} object to bytes. */
    public final byte[] toBytes() {
        int size = CAPABILITY_SIZE_BYTES + getHeader().getSize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer
                .put(getHeader().toBytes())
                .put(RangingTechnology.toBitmap(getRequestedRangingTechnologies()));
        return byteBuffer.array();
    }

    /** Returns the OOB header. */
    public abstract OobHeader getHeader();

    /** Returns a list of ranging technologies for which capabilities are requested. */
    public abstract ImmutableSet<RangingTechnology> getRequestedRangingTechnologies();

    /** Returns a builder for {@link CapabilityRequestMessage}. */
    public static Builder builder() {
        return new AutoValue_CapabilityRequestMessage.Builder();
    }

    /** Builder for {@link CapabilityRequestMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRequestedRangingTechnologies(
                ImmutableSet<RangingTechnology> requestedRangingTechnologies);

        public abstract Builder setHeader(OobHeader header);

        public abstract CapabilityRequestMessage build();
    }
}
