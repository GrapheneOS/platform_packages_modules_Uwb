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
public class BitChunkTest {
    @Test
    public void testGetBitsFromByte_invalidBitOffset_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromByte((byte) 0x01, -1, 8));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromByte((byte) 0x01, 8, 8));
    }

    @Test
    public void testGetBitsFromByte_invalidBitCount_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromByte((byte) 0x01, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromByte((byte) 0x01, 0, 9));
    }

    @Test
    public void testGetBitsFromByte_zeros() {
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 1, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 2, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 3, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 4, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 5, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 6, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 7, 1)).isEqualTo(0);

        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 1)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 2)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 3)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 4)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 5)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 6)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 7)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 0, 8)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 1, 7)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 2, 6)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 3, 5)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 4, 4)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 5, 3)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 6, 2)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 7, 1)).isEqualTo(0);

        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 3, 2)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 2, 4)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x00, 1, 6)).isEqualTo(0);
    }

    @Test
    public void testGetBitsFromByte_ones() {
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 1, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 2, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 3, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 4, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 5, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 6, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 7, 1)).isEqualTo(1);

        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 1)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 2)).isEqualTo(3);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 3)).isEqualTo(7);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 5)).isEqualTo(31);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 6)).isEqualTo(63);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 7)).isEqualTo(127);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 0, 8)).isEqualTo(255);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 1, 7)).isEqualTo(127);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 2, 6)).isEqualTo(63);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 3, 5)).isEqualTo(31);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 4, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 5, 3)).isEqualTo(7);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 6, 2)).isEqualTo(3);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 7, 1)).isEqualTo(1);

        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 3, 2)).isEqualTo(3);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 2, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0xFF, 1, 6)).isEqualTo(63);
    }

    @Test
    public void testGetBitsFromByte() {
        assertThat(BitChunk.getBitsFromByte((byte) 0x0F, 0, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0x1E, 1, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0x3C, 2, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0x78, 3, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0xF0, 4, 4)).isEqualTo(15);

        assertThat(BitChunk.getBitsFromByte((byte) 0x0F, 0, 4)).isEqualTo(15);
        assertThat(BitChunk.getBitsFromByte((byte) 0x1E, 0, 4)).isEqualTo(14);
        assertThat(BitChunk.getBitsFromByte((byte) 0x3C, 0, 4)).isEqualTo(12);
        assertThat(BitChunk.getBitsFromByte((byte) 0x78, 0, 4)).isEqualTo(8);
        assertThat(BitChunk.getBitsFromByte((byte) 0xF0, 0, 4)).isEqualTo(0);

        assertThat(BitChunk.getBitsFromByte((byte) 0x0F, 4, 4)).isEqualTo(0);
        assertThat(BitChunk.getBitsFromByte((byte) 0x1E, 4, 4)).isEqualTo(1);
        assertThat(BitChunk.getBitsFromByte((byte) 0x3C, 4, 4)).isEqualTo(3);
        assertThat(BitChunk.getBitsFromByte((byte) 0x78, 4, 4)).isEqualTo(7);
        assertThat(BitChunk.getBitsFromByte((byte) 0xF0, 4, 4)).isEqualTo(15);
    }

    @Test
    public void testGetBitsFromBytes_nullBytes_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> BitChunk.getBitsFromBytes(null, 0, 0, 8, false));
    }

    @Test
    public void testGetBitsFromBytes_emptyBytes_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(new byte[0], 0, 0, 8, false));
    }

    @Test
    public void testGetBitsFromBytes_invalidByteOffset_throwsException() {
        byte[] bytes = new byte[] {0x01};
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, -1, 0, 8, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 1, 0, 8, false));
    }

    @Test
    public void testGetBitsFromBytes_invalidBitOffset_throwsException() {
        byte[] bytes = new byte[] {0x01};
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 0, -1, 8, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 0, 8, 8, false));
    }

    @Test
    public void testGetBitsFromBytes_invalidBitCount_throwsException() {
        byte[] bytes = new byte[] {
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10,
                0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18};
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 0, 0, 0, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 0, 0, 65, false));
    }

    @Test
    public void testGetBitsFromBytes_invalidByteLength_throwsException() {
        byte[] bytes = new byte[] {0x01, 0x02};
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 0, 0, 17, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 1, 0, 9, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitChunk.getBitsFromBytes(bytes, 1, 7, 2, false));
    }

    @Test
    public void testGetBitsFromBytes_zeros() {
        byte[] bytes = new byte[] {
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 8, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 16, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 24, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 32, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 40, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 48, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 56, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 64, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 64, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 2, 0, 64, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 3, 0, 64, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 64, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 5, 0, 56, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 6, 0, 48, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 7, 0, 40, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 8, 0, 32, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 9, 0, 24, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 10, 0, 16, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 11, 0, 8, false)).isEqualTo(0L);

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 8, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 16, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 24, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 32, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 40, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 48, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 56, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 64, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 64, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 2, 0, 64, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 3, 0, 64, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 64, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 5, 0, 56, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 6, 0, 48, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 7, 0, 40, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 8, 0, 32, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 9, 0, 24, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 10, 0, 16, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 11, 0, 8, true)).isEqualTo(0L);
    }

    @Test
    public void testGetBitsFromBytes_ones() {
        byte[] bytes = new byte[] {
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 8, false)).isEqualTo(255L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 16, false)).isEqualTo(65535L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 24, false)).isEqualTo(16777215L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 32, false)).isEqualTo(4294967295L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 40, false)).isEqualTo(1099511627775L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 48, false)).isEqualTo(281474976710655L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 56, false)).isEqualTo(
                72057594037927935L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 63, false)).isEqualTo(
                9223372036854775807L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 64, false)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 64, false)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 2, 0, 64, false)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 3, 0, 64, false)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 64, false)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 63, false)).isEqualTo(
                9223372036854775807L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 5, 0, 56, false)).isEqualTo(
                72057594037927935L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 6, 0, 48, false)).isEqualTo(281474976710655L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 7, 0, 40, false)).isEqualTo(1099511627775L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 8, 0, 32, false)).isEqualTo(4294967295L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 9, 0, 24, false)).isEqualTo(16777215L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 10, 0, 16, false)).isEqualTo(65535L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 11, 0, 8, false)).isEqualTo(255L);

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 8, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 16, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 24, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 32, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 40, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 48, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 56, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 63, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 64, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 64, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 2, 0, 64, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 3, 0, 64, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 64, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 63, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 5, 0, 56, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 6, 0, 48, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 7, 0, 40, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 8, 0, 32, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 9, 0, 24, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 10, 0, 16, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 11, 0, 8, true)).isEqualTo(-1L);
    }

    @Test
    public void testGetBitsFromBytes_littleEndianByteOrder() {
        byte[] bytes = new byte[] {
                (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04,
                (byte) 0x05, (byte) 0x06, (byte) 0x07, (byte) 0x08,
                (byte) 0x09, (byte) 0x0A, (byte) 0x0B, (byte) 0x0C};

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 64, false)).isEqualTo(
                0x0807060504030201L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 64, false)).isEqualTo(
                0x0908070605040302L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 2, 0, 64, false)).isEqualTo(
                0x0A09080706050403L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 3, 0, 64, false)).isEqualTo(
                0x0B0A090807060504L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 4, 0, 64, false)).isEqualTo(
                0x0C0B0A0908070605L);
    }

    @Test
    public void testGetBitsFromBytes_twoBytes() {
        byte[] bytes = new byte[] {(byte) 0xF0, (byte) 0x0F};

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 4, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 4, true)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 1, 4, false)).isEqualTo(8L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 1, 4, true)).isEqualTo(-8L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 2, 4, false)).isEqualTo(12L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 2, 4, true)).isEqualTo(-4L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 3, 4, false)).isEqualTo(14L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 3, 4, true)).isEqualTo(-2L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 4, 4, false)).isEqualTo(15L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 4, 4, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 5, 4, false)).isEqualTo(15L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 5, 4, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 6, 4, false)).isEqualTo(15L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 6, 4, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 7, 4, false)).isEqualTo(15L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 7, 4, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 4, false)).isEqualTo(15L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 4, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 1, 4, false)).isEqualTo(7L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 1, 4, true)).isEqualTo(7L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 2, 4, false)).isEqualTo(3L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 2, 4, true)).isEqualTo(3L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 3, 4, false)).isEqualTo(1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 3, 4, true)).isEqualTo(1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 4, 4, false)).isEqualTo(0L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 4, 4, true)).isEqualTo(0L);

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 8, false)).isEqualTo(240L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 8, true)).isEqualTo(-16L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 1, 8, false)).isEqualTo(248L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 1, 8, true)).isEqualTo(-8L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 2, 8, false)).isEqualTo(252L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 2, 8, true)).isEqualTo(-4L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 3, 8, false)).isEqualTo(254L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 3, 8, true)).isEqualTo(-2L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 4, 8, false)).isEqualTo(255L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 4, 8, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 5, 8, false)).isEqualTo(127L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 5, 8, true)).isEqualTo(127L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 6, 8, false)).isEqualTo(63L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 6, 8, true)).isEqualTo(63L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 7, 8, false)).isEqualTo(31L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 7, 8, true)).isEqualTo(31L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 8, false)).isEqualTo(15L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 8, true)).isEqualTo(15L);
    }

    @Test
    public void testGetBitsFromBytes_threeBytes() {
        byte[] bytes = new byte[] {(byte) 0xF0, (byte) 0xFF, (byte) 0x0F};

        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 16, false)).isEqualTo(65520L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 0, 16, true)).isEqualTo(-16L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 1, 16, false)).isEqualTo(65528L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 1, 16, true)).isEqualTo(-8L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 2, 16, false)).isEqualTo(65532L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 2, 16, true)).isEqualTo(-4L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 3, 16, false)).isEqualTo(65534L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 3, 16, true)).isEqualTo(-2L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 4, 16, false)).isEqualTo(65535L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 4, 16, true)).isEqualTo(-1L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 5, 16, false)).isEqualTo(32767L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 5, 16, true)).isEqualTo(32767L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 6, 16, false)).isEqualTo(16383L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 6, 16, true)).isEqualTo(16383L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 7, 16, false)).isEqualTo(8191L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 0, 7, 16, true)).isEqualTo(8191L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 16, false)).isEqualTo(4095L);
        assertThat(BitChunk.getBitsFromBytes(bytes, 1, 0, 16, true)).isEqualTo(4095L);
    }
}
