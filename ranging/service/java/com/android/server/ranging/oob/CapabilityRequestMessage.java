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

/** The Capability Request Message Additional Data for Finder OOB. */
@AutoValue
public abstract class CapabilityRequestMessage {

    // Size when serialized
    private static final int SIZE_BYTES = 1;

    /**
     * Parses the given byte array and returns {@link CapabilityRequestMessage} object. Throws
     * {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CapabilityRequestMessage parseBytes(byte[] capabilityRequestBytes) {
        if (capabilityRequestBytes.length != SIZE_BYTES) {
            throw new IllegalArgumentException();
        }
        ImmutableList<RangingTechnology> rangingTechnologies =
                RangingTechnology.parseByte(capabilityRequestBytes[0]);

        return builder().setRequestedRangingTechnologies(rangingTechnologies).build();
    }

    /** Returns a builder for {@link CapabilityRequestMessage}. */
    public static Builder builder() {
        return new AutoValue_CapabilityRequestMessage.Builder();
    }

    /** Serializes this {@link CapabilityRequestMessage} object to bytes. */
    public final byte[] toBytes() {
        return new byte[]{RangingTechnology.toBitmap(getRequestedRangingTechnologies())};
    }

    /** Returns a list of ranging technologies for which capabilities are requested. */
    public abstract ImmutableList<RangingTechnology> getRequestedRangingTechnologies();

    /** Builder for {@link CapabilityRequestMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRequestedRangingTechnologies(
                ImmutableList<RangingTechnology> requestedRangingTechnologies);

        public abstract CapabilityRequestMessage build();
    }
}
