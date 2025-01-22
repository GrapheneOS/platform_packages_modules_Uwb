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

import static org.junit.Assert.assertThrows;

import com.android.server.ranging.cs.CsOobConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CsOobConfigTest {

    private static final CsOobConfig CS_CONFIG = CsOobConfig.builder().build();

    private static final byte[] csConfigBytes =
            new byte[]{
                    // CS Technology Id
                    0x01,
                    // Size
                    0x02,
            };

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(CS_CONFIG.toBytes()).isEqualTo(csConfigBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(CsOobConfig.parseBytes(csConfigBytes)).isEqualTo(CS_CONFIG);
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(IllegalArgumentException.class, () -> CsOobConfig.parseBytes(shortMessage));
    }

    @Test
    public void parseBytes_invalidTechnologyId_throws() throws Exception {
        byte[] unknownConfigBytes = new byte[]{0x09, 0x02};

        assertThrows(IllegalArgumentException.class,
                () -> CsOobConfig.parseBytes(unknownConfigBytes));
    }
}
