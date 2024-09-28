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
import com.google.common.collect.ImmutableList;

import java.util.BitSet;

/**
 * Represents a response message for set configuration, start ranging, and stop ranging requests.
 * The response is a bitmap of the status for the requested action for each technology.
 */
@AutoValue
public abstract class StatusResponseMessage {

    // Size in bytes when serialized
    private static final int SIZE_IN_BYTES = 1;

    /**
     * Parses the given byte array and returns {@link StatusResponseMessage} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static StatusResponseMessage parseBytes(byte[] statusResponseBytes) {
        if (statusResponseBytes.length != SIZE_IN_BYTES) {
            throw new IllegalArgumentException("Invalid message size");
        }

        BitSet bitset = BitSet.valueOf(statusResponseBytes);
        ImmutableList.Builder<RangingTechnology> successfulListBuilder = ImmutableList.builder();
        for (RangingTechnology technology : RangingTechnology.values()) {
            if (bitset.get(technology.getValue())) {
                successfulListBuilder.add(technology);
            }
        }

        return builder().setSuccessfulRangingTechnologies(successfulListBuilder.build()).build();
    }

    /** Serializes this {@link StatusResponseMessage} object to bytes. */
    public final byte toByte() {
        if (getSuccessfulRangingTechnologies().isEmpty()) {
            return 0x0;
        }
        BitSet bitset = new BitSet();
        for (RangingTechnology technology : getSuccessfulRangingTechnologies()) {
            bitset.set(technology.getValue());
        }
        return bitset.toByteArray()[0];
    }

    /** Returns a list of the status of the requested action for each Ranging Technology. */
    public abstract ImmutableList<RangingTechnology> getSuccessfulRangingTechnologies();

    /** Returns a builder for {@link StatusResponseMessage}. */
    public static Builder builder() {
        return new AutoValue_StatusResponseMessage.Builder();
    }

    public abstract Builder toBuilder();

    /** Builder for {@link StatusResponseMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setSuccessfulRangingTechnologies(
                ImmutableList<RangingTechnology> successfulRangingTechnologies);

        public abstract StatusResponseMessage build();
    }
}
