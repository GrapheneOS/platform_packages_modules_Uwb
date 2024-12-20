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

import android.annotation.IntDef;

import com.google.auto.value.AutoValue;

/** Header for OOB messages. */
@AutoValue
public abstract class OobHeader {

    @IntDef({
            OobVersion.CURRENT,
    })
    public @interface OobVersion {
        int CURRENT = 0;
    }

    private static final int SIZE_BYTES = 2;

    public static OobHeader parseBytes(byte[] payload) {
        if (payload.length < SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "Header is too short, expected at least %d bytes, got %d",
                            SIZE_BYTES, payload.length));
        }

        int parseCursor = 0;
        int version = payload[parseCursor++];
        MessageType messageType = MessageType.parseByte(payload[parseCursor++]);
        return builder().setVersion(version).setMessageType(messageType).build();
    }

    public byte[] toBytes() {
        byte[] payload = new byte[SIZE_BYTES];
        int parseCursor = 0;
        payload[parseCursor++] = (byte) getVersion();
        payload[parseCursor++] = (byte) getMessageType().getValue();
        return payload;
    }

    public int getSize() {
        return SIZE_BYTES;
    }

    /** Returns the version. */
    public abstract int getVersion();

    /** Returns the message type. */
    public abstract MessageType getMessageType();

    /** Returns a builder for {@link OobHeader}. */
    public static Builder builder() {
        return new AutoValue_OobHeader.Builder();
    }

    /** Builder for {@link OobHeader}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setVersion(int version);

        public abstract Builder setMessageType(MessageType messageType);

        public abstract OobHeader build();
    }
}
