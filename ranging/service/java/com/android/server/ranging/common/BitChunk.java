/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ranging.common;

import android.annotation.NonNull;

import java.lang.Math;
import java.util.Objects;

/**
 * Parse a chunk of bits from a byte, or from bytes across byte boundaries.
 *
 * @hide
 */
public class BitChunk {
    /**
     * Parse a chunk of bits from a byte.
     *
     * @param b The byte to parse the bits from.
     * @param bitOffset The bit offset to start parsing from.
     * @param bitCount The number of bits to parse.
     * @return The integer value of the bits.
     * @throws IllegalArgumentException if the parameters are invalid.
     */
    public static int getBitsFromByte(byte b, int bitOffset, int bitCount) {
        if (bitOffset < 0 || bitOffset > 7 || bitCount < 1 || bitOffset + bitCount > 8) {
            throw new IllegalArgumentException(
                    "Invalid bit offset: " + bitOffset + ", " + bitCount);
        }
        int offsetLow = bitOffset;
        int offsetHigh = offsetLow + bitCount - 1;
        return (b & ((1 << (offsetHigh + 1)) - 1)) >>> offsetLow;
    }

    /**
     * Parse a chunk of bits from a byte array.
     *
     * @param bytes The byte array to parse the bits from.
     * @param byteOffset The byte offset to start parsing from.
     * @param bitOffset The bit offset to start parsing from.
     * @param bitCount The number of bits to parse.
     * @param signed Whether the integer value of the parsed bits are signed.
     * @return The integer value of the bits.
     * @throws NullPointerException if the bytes are null.
     * @throws IllegalArgumentException if the parameters are invalid.
     */
    public static long getBitsFromBytes(
            @NonNull byte[] bytes, int byteOffset, int bitOffset, int bitCount, boolean signed) {
        Objects.requireNonNull(bytes);

        int byteCount = (bitOffset + bitCount + 7) / 8;
        if (byteOffset < 0 || byteOffset >= bytes.length || bitOffset < 0 || bitOffset > 7
                || bitCount < 1 || bitCount > 64 || bytes.length < byteOffset + byteCount) {
            throw new IllegalArgumentException(
                    "Invalid byte length: " + bytes.length + ", byte offset: " + byteOffset
                            + ", bit count: " + bitCount);
        }

        long number = 0;
        int bitCountRemaining = bitCount;
        int bitCountRemainingInByte = 8 - bitOffset;
        int bitCountParsed = 0;
        while (bitCountRemaining > 0) {
            int bitCountParsedInByte = Math.min(bitCountRemaining, bitCountRemainingInByte);
            int bits = getBitsFromByte(bytes[byteOffset], bitOffset, bitCountParsedInByte);
            number |= (long) bits << bitCountParsed;
            bitOffset += bitCountParsedInByte;
            bitCountRemainingInByte -= bitCountParsedInByte;
            bitCountParsed += bitCountParsedInByte;
            bitCountRemaining -= bitCountParsedInByte;
            if (bitCountRemainingInByte == 0) {
                bitOffset = 0;
                byteOffset++;
                bitCountRemainingInByte = 8;
            }
        }

        if (signed) {
            int shiftCount = 64 - bitCount;
            // use signed shift to make sure the sign bit is maintained properly
            number = (number << shiftCount) >> shiftCount;
        }
        return number;
    }
}