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

/**
 * Represents a response message for set configuration, start ranging, and stop ranging requests.
 * The response is a bitmap of the status for the requested action for each technology.
 */
@AutoValue
public abstract class StatusResponseMessage {

    // Size in bytes when serialized
    private static final int SIZE_IN_BYTES = 2;

    /**
     * Parses the given byte array and returns {@link StatusResponseMessage} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static StatusResponseMessage parseBytes(byte[] payload) {
        OobHeader header = OobHeader.parseBytes(payload);

        if (header.getMessageType() != MessageType.SET_CONFIGURATION_RESPONSE
                && header.getMessageType() != MessageType.START_RANGING_RESPONSE
                && header.getMessageType() != MessageType.STOP_RANGING_RESPONSE) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid message type: %s, expected status response type",
                            header.getMessageType()));
        }

        if (payload.length < header.getSize() + SIZE_IN_BYTES) {
            throw new IllegalArgumentException(
                    String.format("StatusResponseMessage payload size is %d bytes",
                            payload.length));
        }

        int parseCursor = header.getSize();
        ImmutableList<RangingTechnology> rangingTechnologiesStatus =
                RangingTechnology.fromBitmap(
                        Arrays.copyOfRange(payload, parseCursor, parseCursor + SIZE_IN_BYTES));

        return builder()
                .setOobHeader(header)
                .setSuccessfulRangingTechnologies(rangingTechnologiesStatus)
                .build();
    }

    /** Serializes this {@link StatusResponseMessage} object to bytes. */
    public final byte[] toBytes() {
        int size = SIZE_IN_BYTES + getOobHeader().getSize();
        ByteBuffer byteBuffer = ByteBuffer.allocate(size);
        byteBuffer
                .put(getOobHeader().toBytes())
                .put(RangingTechnology.toBitmap(getSuccessfulRangingTechnologies()));
        return byteBuffer.array();
    }

    /** Returns the OOB header. */
    public abstract OobHeader getOobHeader();

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
        public abstract Builder setOobHeader(OobHeader oobHeader);

        public abstract Builder setSuccessfulRangingTechnologies(
                ImmutableList<RangingTechnology> successfulRangingTechnologies);

        public abstract StatusResponseMessage build();
    }
}
