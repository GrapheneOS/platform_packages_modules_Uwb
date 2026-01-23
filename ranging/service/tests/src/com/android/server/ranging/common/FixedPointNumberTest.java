/*
 * Copyright 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FixedPointNumberTest {
    @Test
    public void testInvalidFractionalBitCount_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, -1, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, 65, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(true, 64, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, 0, -1, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, 0, 65, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(true, 0, 64, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(true, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, 32, 33, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(false, 33, 32, 0));
        assertThrows(
                IllegalArgumentException.class, () -> FixedPointNumber.create(true, 32, 32, 0));
    }

    @Test
    public void testFixedPointNumber_getters() {
        FixedPointNumber unsignedNumber = FixedPointNumber.create(false, 12, 34, 567890);
        assertThat(unsignedNumber.hasSignBit()).isFalse();
        assertThat(unsignedNumber.getIntegerBitCount()).isEqualTo(12);
        assertThat(unsignedNumber.getFractionalBitCount()).isEqualTo(34);
        assertThat(unsignedNumber.getRawValue()).isEqualTo(567890);

        FixedPointNumber positiveNumber = FixedPointNumber.create(true, 12, 34, 567890);
        assertThat(positiveNumber.hasSignBit()).isTrue();
        assertThat(positiveNumber.getIntegerBitCount()).isEqualTo(12);
        assertThat(positiveNumber.getFractionalBitCount()).isEqualTo(34);
        assertThat(positiveNumber.getRawValue()).isEqualTo(567890);

        FixedPointNumber negativeNumber = FixedPointNumber.create(true, 12, 34, -567890);
        assertThat(negativeNumber.hasSignBit()).isTrue();
        assertThat(negativeNumber.getIntegerBitCount()).isEqualTo(12);
        assertThat(negativeNumber.getFractionalBitCount()).isEqualTo(34);
        assertThat(negativeNumber.getRawValue()).isEqualTo(-567890);

        assertThat(FixedPointNumber.create(false, 4, 4, 0x80).getRawValue()).isEqualTo(0x80);
        assertThat(FixedPointNumber.create(true, 3, 4, 0x80).getRawValue()).isEqualTo(-0x80);
    }

    @Test
    public void testFixedPointNumber_zero() {
        assertThat(FixedPointNumber.create(false, 1, 0, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 1, 0, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 64, 0, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 63, 0, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 0, 1, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 0, 1, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 0, 64, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 0, 63, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 32, 32, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 31, 32, 0).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 32, 31, 0).getDoubleValue()).isEqualTo(0.0);
    }

    @Test
    public void testFixedPointNumber_normalization() {
        assertThat(FixedPointNumber.create(false, 2, 0, 0x04).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 0, 2, 0x04).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 1, 1, 0x04).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 2, 0, 0x08).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 0, 2, 0x08).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 1, 1, 0x08).getDoubleValue()).isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 63, 0, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 0, 63, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(0.0);
        assertThat(FixedPointNumber.create(false, 31, 32, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 62, 0, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 0, 62, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(0.0);
        assertThat(FixedPointNumber.create(true, 31, 31, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(0.0);
    }

    @Test
    public void testFixedPointNumber_powerOfTwo() {
        assertThat(FixedPointNumber.create(false, 1, 0, 1).getDoubleValue())
                .isWithin(1e-10).of(1.0);
        assertThat(FixedPointNumber.create(true, 1, 0, 1).getDoubleValue())
                .isWithin(1e-10).of(1.0);
        assertThat(FixedPointNumber.create(false, 64, 0, 1).getDoubleValue())
                .isWithin(1e-10).of(1.0);
        assertThat(FixedPointNumber.create(true, 63, 0, 1).getDoubleValue())
                .isWithin(1e-10).of(1.0);

        assertThat(FixedPointNumber.create(false, 0, 1, 1).getDoubleValue())
                .isWithin(1e-10).of(0.5);  // 2^-1
        assertThat(FixedPointNumber.create(true, 0, 1, 1).getDoubleValue())
                .isWithin(1e-10).of(0.5);  // 2^-1
        assertThat(FixedPointNumber.create(false, 0, 64, 1).getDoubleValue())
                .isWithin(1e-30).of(5.4210108624275222e-20);  // 2^-64
        assertThat(FixedPointNumber.create(true, 0, 63, 1).getDoubleValue())
                .isWithin(1e-30).of(1.0842021724855044e-19);  // 2^-63
        assertThat(FixedPointNumber.create(false, 32, 32, 1).getDoubleValue())
                .isWithin(1e-20).of(2.32830643653869634e-10);  // 2^-32
        assertThat(FixedPointNumber.create(true, 31, 32, 1).getDoubleValue())
                .isWithin(1e-20).of(2.32830643653869634e-10);  // 2^-32
        assertThat(FixedPointNumber.create(true, 32, 31, 1).getDoubleValue())
                .isWithin(1e-20).of(4.6566128730773926e-10);  // 2^-31

        assertThat(FixedPointNumber.create(false, 2, 0, 2).getDoubleValue())
                .isWithin(1e-10).of(2.0);
        assertThat(FixedPointNumber.create(true, 2, 0, 2).getDoubleValue())
                .isWithin(1e-10).of(2.0);
        assertThat(FixedPointNumber.create(false, 1, 1, 2).getDoubleValue())
                .isWithin(1e-10).of(1.0);
        assertThat(FixedPointNumber.create(true, 1, 1, 2).getDoubleValue())
                .isWithin(1e-10).of(1.0);
        assertThat(FixedPointNumber.create(false, 0, 2, 2).getDoubleValue())
                .isWithin(1e-10).of(0.5);
        assertThat(FixedPointNumber.create(true, 0, 2, 2).getDoubleValue())
                .isWithin(1e-10).of(0.5);

        assertThat(FixedPointNumber.create(false, 64, 0, 0x8000000000000000L).getDoubleValue())
                .isWithin(1e+5).of(9.2233720368547758e+18);  // 2^63
        assertThat(FixedPointNumber.create(true, 63, 0, 0x8000000000000000L).getDoubleValue())
                .isEqualTo(-0.0);  // -0.0
        assertThat(FixedPointNumber.create(true, 63, 0, 0x4000000000000000L).getDoubleValue())
                .isWithin(1e+5).of(4.6116860184273879e+18);  // 2^62
    }

    @Test
    public void testFixedPointNumber_doubleValue() {
        // Q9.24
        assertThat(FixedPointNumber.create(true, 8, 24, 0x01800000L).getDoubleValue())
                .isWithin(1e-10).of(1.5);
        // Q9.21
        assertThat(FixedPointNumber.create(true, 8, 21, 0x00300000L).getDoubleValue())
                .isWithin(1e-10).of(1.5);
        // Q10.4
        assertThat(FixedPointNumber.create(true, 9, 4, 0x0018L).getDoubleValue())
                .isWithin(1e-10).of(1.5);
        assertThat(FixedPointNumber.create(true, 9, 4, -8192).getDoubleValue())
                .isEqualTo(-0.0);
        assertThat(FixedPointNumber.create(true, 9, 4, 8191).getDoubleValue())
                .isWithin(1e-10).of(511.9375);
        assertThat(FixedPointNumber.create(true, 9, 4, -8191).getDoubleValue())
                .isWithin(1e-10).of(-511.9375);
        // Q12.12
        assertThat(FixedPointNumber.create(true, 11, 12, 0x001800L).getDoubleValue())
                .isWithin(1e-10).of(1.5);
        assertThat(FixedPointNumber.create(true, 11, 12, -8388608).getDoubleValue())
                .isEqualTo(-0.0);
        assertThat(FixedPointNumber.create(true, 11, 12, 8388607).getDoubleValue())
                .isWithin(1e-10).of(2.047999755859375e+3);
        assertThat(FixedPointNumber.create(true, 11, 12, -8388607).getDoubleValue())
                .isWithin(1e-10).of(-2.047999755859375e+3);
    }
}
