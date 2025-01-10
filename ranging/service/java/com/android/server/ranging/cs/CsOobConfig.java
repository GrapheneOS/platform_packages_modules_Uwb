/*
 * Copyright 2025 The Android Open Source Project
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
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;

import java.nio.ByteBuffer;

/** Configuration for UWB sent as part SetConfigurationMessage for Finder OOB. */
@AutoValue
public abstract class CsOobConfig {

    private static final int EXPECTED_SIZE_BYTES = 2;

    /** Returns the size of the object in bytes when serialized. */
    public final int getSize() {
        return EXPECTED_SIZE_BYTES;
    }

    /** Enum representing security type for Channel Sounding. */
    public enum CsSecurityType {
        UNKNOWN(0),
        LEVEL_ONE(1),
        LEVEL_TWO(2),
        LEVEL_THREE(3),
        LEVEL_FOUR(4);

        private final int mValue;

        CsSecurityType(int value) {
            this.mValue = value;
        }

        public int getValue() {
            return mValue;
        }

        public static CsSecurityType fromValue(int value) {
            return value < 0 || value > LEVEL_FOUR.mValue ? UNKNOWN
                    : CsSecurityType.values()[value];
        }
    }

    /** Enum representing BR/EDR support and capability flag for Channel Sounding. */
    public enum CsLeFlag {
        LE_FLAG_LIMITED_DISCOVERY_MODE(0),
        LE_FLAG_GENERAL_DISCOVERY_MODE(1),
        LE_FLAG_BREDR_NOT_SUPPORTED(2),
        LE_FLAG_SIMULTANEOUS_CONTROLLER(3),
        LE_FLAG_SIMULTANEOUS_HOST(4),
        UNKNOWN(5);

        private final int mValue;

        CsLeFlag(int value) {
            this.mValue = value;
        }

        public byte toByte() {
            return (byte) mValue;
        }

        public static CsLeFlag parseByte(byte leFlagByte) {
            if (leFlagByte < 0 || leFlagByte > UNKNOWN.mValue) {
                return UNKNOWN;
            }
            return CsLeFlag.values()[leFlagByte];
        }
    }

    /**
     * Parses the given byte array and returns {@link CsOobConfig} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CsOobConfig parseBytes(byte[] csConfigBytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(csConfigBytes);

        if (csConfigBytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "CsOobConfig size is %d, expected at least %d",
                            csConfigBytes.length, EXPECTED_SIZE_BYTES));
        }

        if (header.getRangingTechnology() != RangingTechnology.CS) {
            throw new IllegalArgumentException(
                    String.format(
                            "CsOobConfig header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.CS));
        }

        int parseCursor = header.getHeaderSize();

        return builder().build();
    }

    /** Serializes this {@link CsOobConfig} object to bytes. */
    public final byte[] toBytes() {
        return ByteBuffer.allocate(EXPECTED_SIZE_BYTES)
                .put(RangingTechnology.CS.toByte())
                .put((byte) EXPECTED_SIZE_BYTES)
                .array();
    }

    /** Returns a builder for {@link CsOobConfig}. */
    public static Builder builder() {
        return new AutoValue_CsOobConfig.Builder();
    }

    /** Builder for {@link CsOobConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract CsOobConfig build();
    }
}
