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

package com.android.server.ranging.tests.oob;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.ranging.RangingUtils;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

@RunWith(JUnit4.class)
public final class UtilsTest {

    private static final ImmutableList<Integer> INT_LIST = ImmutableList.of(3, 4, 8);

    @Test
    public void intListToByteArrayBitmap_success() throws Exception {
        byte[] resultShift2Size3 = new byte[]{0x46, 0x00, 0x00};
        assertThat(
                Arrays.equals(
                        resultShift2Size3,
                        RangingUtils.Conversions.intListToByteArrayBitmap(
                                INT_LIST, /* expectedSizeBytes= */ 3, /* shift= */ 2)))
                .isTrue();

        byte[] resultShift2Size1 = new byte[]{0x46};
        assertThat(
                Arrays.equals(
                        resultShift2Size1,
                        RangingUtils.Conversions.intListToByteArrayBitmap(
                                INT_LIST, /* expectedSizeBytes= */ 1, /* shift= */ 2)))
                .isTrue();

        byte[] resultShift0Size2 = new byte[]{0x18, 0x01};
        assertThat(
                Arrays.equals(
                        resultShift0Size2,
                        RangingUtils.Conversions.intListToByteArrayBitmap(
                                INT_LIST, /* expectedSizeBytes= */ 2, /* shift= */ 0)))
                .isTrue();
    }

    @Test
    public void byteArrayToIntList_success() throws Exception {
        byte[] intList1Shift2Size2 = new byte[]{0x46, 0x00};
        assertThat(RangingUtils.Conversions.byteArrayToIntList(intList1Shift2Size2, /* shift= */ 2))
                .isEqualTo(INT_LIST);

        byte[] intList1Shit2Size1 = new byte[]{0x46};
        assertThat(RangingUtils.Conversions.byteArrayToIntList(intList1Shit2Size1, /* shift= */ 2))
                .isEqualTo(INT_LIST);

        byte[] intList1Shift0Size4 = new byte[]{0x18, 0x01, 0x00, 0x00};
        assertThat(RangingUtils.Conversions.byteArrayToIntList(intList1Shift0Size4, /* shift= */ 0))
                .isEqualTo(INT_LIST);
    }

    @Test
    public void intToByteArray_success() throws Exception {
        byte[] resultValue1Size4 = new byte[]{0x01, 0x00, 0x00, 0x00};
        assertThat(
                Arrays.equals(
                        resultValue1Size4,
                        RangingUtils.Conversions
                                .intToByteArray(/* value= */ 1, /* expectedSizeBytes= */ 4)))
                .isTrue();

        byte[] resultValue256Size3 = new byte[]{0x00, 0x01, 0x00};
        assertThat(
                Arrays.equals(
                        resultValue256Size3,
                        RangingUtils.Conversions.intToByteArray(/* value= */
                                256, /* expectedSizeBytes= */ 3)))
                .isTrue();

        byte[] resultValue257Size3 = new byte[]{0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertThat(
                Arrays.equals(
                        resultValue257Size3,
                        RangingUtils.Conversions
                                .intToByteArray(/* value= */ 257, /* expectedSizeBytes= */ 7)))
                .isTrue();
    }

    @Test
    public void byteArrayToInt_success() throws Exception {
        byte[] byteArrayValue8Size1 = new byte[]{0x08};

        assertThat(RangingUtils.Conversions.byteArrayToInt(byteArrayValue8Size1)).isEqualTo(8);

        byte[] byteArrayValue256Size3 = new byte[]{0x00, 0x01, 0x00};
        assertThat(RangingUtils.Conversions.byteArrayToInt(byteArrayValue256Size3)).isEqualTo(256);

        byte[] byteArrayValue23456Size4 = new byte[]{(byte) 0xA0, 0x5B, 0x00, 0x00};
        assertThat(RangingUtils.Conversions.byteArrayToInt(byteArrayValue23456Size4)).isEqualTo(
                23456);
    }
}
