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

/** The Stop Ranging Message Additional Data for Finder OOB. */
@AutoValue
public abstract class StopRangingMessage {

    // Size in bytes when serialized
    private static final int SIZE_IN_BYTES = 1;

    /**
     * Parses the given byte array and returns {@link StopRangingMessage} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static StopRangingMessage parseBytes(byte[] stopRangingBytes) {
        if (stopRangingBytes.length != SIZE_IN_BYTES) {
            throw new IllegalArgumentException();
        }
        ImmutableList<RangingTechnology> rangingTechnologiesToStop =
                RangingTechnology.parseByte(stopRangingBytes[0]);
        return builder().setRangingTechnologiesToStop(rangingTechnologiesToStop).build();
    }

    /** Serializes this {@link StopRangingMessage} object to bytes. */
    public final byte toByte() {
        return RangingTechnology.toBitmap(getRangingTechnologiesToStop());
    }

    /** Returns a list of ranging technologies that should stop ranging. */
    public abstract ImmutableList<RangingTechnology> getRangingTechnologiesToStop();

    /** Returns a builder for {@link StopRangingMessage}. */
    public static Builder builder() {
        return new AutoValue_StopRangingMessage.Builder();
    }

    /** Builder for {@link StopRangingMessage}. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setRangingTechnologiesToStop(
                ImmutableList<RangingTechnology> rangingTechnologiesToStop);

        public abstract StopRangingMessage build();
    }
}
