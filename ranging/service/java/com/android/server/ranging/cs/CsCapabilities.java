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

package com.android.server.ranging.cs;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Channel Sounding Capability data send as part of CapabilityResponseMessage during Finder OOB. */
@AutoValue
public abstract class CsCapabilities {

    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 2;

    // Size in bytes for each properties for serialization/deserialization.
    private static final int TECHNOLOGY_ID_SIZE = 1;
    private static final int SECURITY_LEVELS_SIZE = 4;

    private static final int SECURITY_LEVELS_SHIFT = 0;

    /** Returns the size of this {@link CsCapabilities} object when serialized. */
    public int getSize() {
        return EXPECTED_SIZE_BYTES;
    }

    /**
     * Parses the given byte array and returns {@link CsCapabilities} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CsCapabilities parseBytes(byte[] csCapabilitiesBytes) {
        if (csCapabilitiesBytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException("Couldn't parse CsCapabilities, invalid byte size");
        }

        int parseCursor = 0;
        var technology = RangingTechnology.parseByte(csCapabilitiesBytes[parseCursor]);
        if (technology.size() != 1 || technology.get(0) != RangingTechnology.CS) {
            throw new IllegalArgumentException(
                    "Couldn't parse CsCapabilities, invalid technology id");
        }
        parseCursor += TECHNOLOGY_ID_SIZE;

        // Parse Supported Channels
        ImmutableList<Integer> supportedSecurityLevels =
                Conversions.byteArrayToIntList(
                        Arrays.copyOfRange(csCapabilitiesBytes, parseCursor,
                                parseCursor + SECURITY_LEVELS_SIZE),
                        SECURITY_LEVELS_SHIFT);
        parseCursor += SECURITY_LEVELS_SIZE;

        return CsCapabilities.builder()
                .setSupportedSecurityLevels(supportedSecurityLevels)
                .build();
    }

    /** Serializes this {@link CsCapabilities} object to bytes. */
    public final byte[] toBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
        byteBuffer
                .put(RangingTechnology.CS.toByte())
                .put(Conversions.intListToByteArrayBitmap(getSupportedSecurityLevels(),
                        SECURITY_LEVELS_SIZE, SECURITY_LEVELS_SHIFT));

        return byteBuffer.array();
    }

    /** Returns a list of supported security levels. */
    public abstract ImmutableList<Integer> getSupportedSecurityLevels();

    /** Returns a builder for {@link CsCapabilities}. */
    public static Builder builder() {
        return new AutoValue_CsCapabilities.Builder();
    }

    /** Builder for {@link CsCapabilities}. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Returns maximum supported security level. */
        public abstract Builder
                setSupportedSecurityLevels(ImmutableList<Integer> supportedChannels);

        /** Returns a builder for {@link CsCapabilities}. */
        public abstract CsCapabilities build();
    }

    @Override
    public String toString() {
        return "CsCapabilities{ "
                + "supportedSecurityLevels="
                + getSupportedSecurityLevels()
                + " }";
    }
}
