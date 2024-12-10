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

import java.nio.ByteBuffer;
import java.util.Arrays;

/** The Start Ranging Message Additional Data for Finder OOB. */
@AutoValue
public abstract class StartRangingMessage {

    // Size in bytes when serialized
    private static final int SIZE_IN_BYTES = 2;

    /**
     * Parses the given byte array and returns {@link StartRangingMessage} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static StartRangingMessage parseBytes(byte[] payload) {
        OobHeader header = OobHeader.parseBytes(payload);

        if (payload.length < header.getSize() + SIZE_IN_BYTES) {
            throw new IllegalArgumentException(
                    String.format("StartRangingMessage payload size is %d bytes", payload.length));
        }

        int parseCursor = header.getSize();
        ImmutableList<RangingTechnology> rangingTechnologiesToStart =
                RangingTechnology.fromBitmap(
                        Arrays.copyOfRange(payload, parseCursor, parseCursor + SIZE_IN_BYTES));

        return builder()
                .setOobHeader(header)
                .setRangingTechnologiesToStart(rangingTechnologiesToStart)
                .build();
    }

    /** Serializes this {@link StartRangingMessage} object to bytes. */
    public final byte[] toBytes() {
        int size = SIZE_IN_BYTES + getOobHeader().getSize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer
                .put(getOobHeader().toBytes())
                .put(RangingTechnology.toBitmap(getRangingTechnologiesToStart()));
        return byteBuffer.array();
    }

    /** Returns the OOB header. */
    public abstract OobHeader getOobHeader();

    /** Returns a list of ranging technologies that should start ranging. */
    public abstract ImmutableList<RangingTechnology> getRangingTechnologiesToStart();

    /** Returns a builder for {@link StartRangingMessage}. */
    public static Builder builder() {
        return new AutoValue_StartRangingMessage.Builder();
    }

    /** Builder for {@link StartRangingMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setOobHeader(OobHeader oobHeader);

        public abstract Builder setRangingTechnologiesToStart(
                ImmutableList<RangingTechnology> rangingTechnologiesToStart);

        public abstract StartRangingMessage build();
    }
}
