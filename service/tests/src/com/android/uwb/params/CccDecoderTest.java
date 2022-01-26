/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.uwb.params;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.uwb.util.UwbUtil;

import com.google.uwb.support.ccc.CccRangingStartedParams;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link com.android.uwb.params.CccDecoder}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class CccDecoderTest {
    private static final byte[] TEST_TLV_DATA =
            UwbUtil.getByteArray("0a0400010002"
                            + "a01001000200000000000000000000000000"
                            + "a1080001000200010002"
                            + "090400010002"
                            + "140101");
    private static final int TEST_TLV_NUM_PARAMS = 5;
    private final CccDecoder mCccDecoder = new CccDecoder();

    @Test
    public void testGetCccRangingOpened() throws Exception {
        TlvDecoderBuffer tlvDecoderBuffer =
                new TlvDecoderBuffer(TEST_TLV_DATA, TEST_TLV_NUM_PARAMS);
        assertThat(tlvDecoderBuffer.parse()).isTrue();

        CccRangingStartedParams cccRangingStartedParams = mCccDecoder.getParams(
                tlvDecoderBuffer, CccRangingStartedParams.class);
        assertThat(cccRangingStartedParams).isNotNull();

        assertThat(cccRangingStartedParams.getStartingStsIndex()).isEqualTo(0x00010002);
        assertThat(cccRangingStartedParams.getHopModeKey()).isEqualTo(0x00020001);
        assertThat(cccRangingStartedParams.getUwbTime0()).isEqualTo(0x0001000200010002L);
        assertThat(cccRangingStartedParams.getRanMultiplier()).isEqualTo(0x00010002 / 96);
    }
}
