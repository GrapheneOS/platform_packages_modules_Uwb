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

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.TechnologyHeader;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TechnologyHeaderTest {

    @Test
    public void parseBytes_validHeader_parsesCorrectly() throws Exception {
        byte[] header1 = new byte[]{0x3, 0x0A};
        byte[] header2 = new byte[]{0x0, 0x3};

        assertThat(TechnologyHeader.parseBytes(header1))
                .isEqualTo(
                        TechnologyHeader.builder()
                                .setRangingTechnology(RangingTechnology.RSSI)
                                .setSize(10)
                                .build());
        assertThat(TechnologyHeader.parseBytes(header2))
                .isEqualTo(
                        TechnologyHeader.builder()
                                .setRangingTechnology(RangingTechnology.UWB)
                                .setSize(3)
                                .build());
    }

    @Test
    public void parseBytes_headerWithAdditionalBytes_parsesCorrectly() throws Exception {
        byte[] header = new byte[]{0x1, 0x4, 0x0B, 0x33};
        assertThat(TechnologyHeader.parseBytes(header))
                .isEqualTo(
                        TechnologyHeader.builder()
                                .setRangingTechnology(RangingTechnology.CS)
                                .setSize(4)
                                .build());
    }

    @Test
    public void parseBytes_tooShortPayload_throwsException() throws Exception {
        byte[] header = new byte[]{0x2};
        assertThrows(IllegalArgumentException.class, () -> TechnologyHeader.parseBytes(header));
    }

    @Test
    public void parseBytes_unknownTechnology_throwsException() throws Exception {
        byte[] header = new byte[]{0x0B, 0x5};
        assertThrows(IllegalArgumentException.class, () -> TechnologyHeader.parseBytes(header));
    }

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        TechnologyHeader header1 =
                TechnologyHeader.builder().setRangingTechnology(RangingTechnology.UWB).setSize(
                        55).build();
        TechnologyHeader header2 =
                TechnologyHeader.builder()
                        .setRangingTechnology(RangingTechnology.RTT)
                        .setSize(10)
                        .build();

        assertThat(header1.toBytes()).isEqualTo(new byte[]{0x0, 0x37});
        assertThat(header2.toBytes()).isEqualTo(new byte[]{0x2, 0xA});
    }
}
