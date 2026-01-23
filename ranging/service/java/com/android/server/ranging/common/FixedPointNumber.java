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

import java.lang.Math;
import java.util.Objects;

/**
 * Parses a fixed point number from a Q format.
 */
public class FixedPointNumber {
    private final boolean mHasSignBit;
    private final int mIntegerBitCount;
    private final int mFractionalBitCount;
    private final long mRawValue;

    /**
     * Creates a {@link FixedPointNumber} from a Q format. The total bit count must be less than or
     * equal to 64.
     *
     * @param hasSignBit Whether the Q format has a sign bit.
     * @param integerBitCount The number of integer bits. (signed bit not included)
     * @param fractionalBitCount The number of fractional bits.
     * @param rawValue The raw value of the number.
     * @return A {@link FixedPointNumber} instance.
     * @throws IllegalArgumentException if the parameters are invalid.
     */
    public static FixedPointNumber create(
            boolean hasSignBit, int integerBitCount, int fractionalBitCount, long rawValue) {
        // Check if the total bit count is valid.
        int signBitCount = hasSignBit ? 1 : 0;
        int valueBitCount = integerBitCount + fractionalBitCount;
        int totalBitCount = signBitCount + valueBitCount;
        if (integerBitCount < 0 || fractionalBitCount < 0 || valueBitCount < 1
                || totalBitCount > 64) {
            throw new IllegalArgumentException(
                    "Invalid bit count: " + signBitCount + ", " + integerBitCount + ", "
                    + fractionalBitCount);
        }

        // If the number should be signed and negative, set the sign bit towards MSB to 1.
        int unusedBitCount = 64 - totalBitCount;
        if (hasSignBit) {
            rawValue = (rawValue << unusedBitCount) >> unusedBitCount;
        } else {
            rawValue = (rawValue << unusedBitCount) >>> unusedBitCount;
        }

        return new FixedPointNumber(hasSignBit, integerBitCount, fractionalBitCount, rawValue);
    }

    private FixedPointNumber(
            boolean hasSignBit, int integerBitCount, int fractionalBitCount, long rawValue) {
        mHasSignBit = hasSignBit;
        mIntegerBitCount = integerBitCount;
        mFractionalBitCount = fractionalBitCount;
        mRawValue = rawValue;
    }

    public boolean hasSignBit() {
        return mHasSignBit;
    }

    public int getIntegerBitCount() {
        return mIntegerBitCount;
    }

    public int getFractionalBitCount() {
        return mFractionalBitCount;
    }

    public long getRawValue() {
        return mRawValue;
    }

    public double getDoubleValue() {
        // The number is negative and the value part is 0, the number is -0.0.
        if (mHasSignBit
                && (mRawValue & (1L << (mIntegerBitCount + mFractionalBitCount))) != 0
                && (mRawValue & ((1L << (mIntegerBitCount + mFractionalBitCount)) - 1)) == 0) {
            return -0.0;
        }

        double numerator = (double) mRawValue;
        double denominator = Math.pow(2.0, mFractionalBitCount);

        // If the number is unsigned and MSB is 1, convert the number with unsigned right shift.
        if (!mHasSignBit && mRawValue < 0) {
            numerator = (mRawValue >>> 1) * 2.0 + (mRawValue & 1) * 1.0;
        }

        return numerator / denominator;
    }

    @Override
    public String toString() {
        return "FixedPointNumber {"
                + " HasSignBit=" + mHasSignBit
                + ", IntegerBitCount=" + mIntegerBitCount
                + ", FractionalBitCount=" + mFractionalBitCount
                + ", RawValue=" + mRawValue
                + ", DoubleValue=" + getDoubleValue()
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FixedPointNumber)) return false;
        FixedPointNumber other = (FixedPointNumber) o;
        return mHasSignBit == other.mHasSignBit
                && mIntegerBitCount == other.mIntegerBitCount
                && mFractionalBitCount == other.mFractionalBitCount
                && mRawValue == other.mRawValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mHasSignBit, mIntegerBitCount, mFractionalBitCount, mRawValue);
    }
}
