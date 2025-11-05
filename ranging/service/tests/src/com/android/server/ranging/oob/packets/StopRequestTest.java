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

package com.android.server.ranging.oob.packets;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StopRequestTest {
    private static final byte[] UWB_CS_TECH_BITMAP = new byte[]{0x3, 0x00};
    private static final byte[] UWB_TECH_ONLY_BITMAP = new byte[]{0x01, 0x00};
    private static final byte[] HEADER_BYTES = new byte[]{0x2, 0x6};
    private static final byte[] UWB_CS_TECH_MESSAGE =
            Bytes.concat(HEADER_BYTES, UWB_CS_TECH_BITMAP);
    private static final byte[] UWB_ONLY_MESSAGE = Bytes.concat(HEADER_BYTES, UWB_TECH_ONLY_BITMAP);
    private static final byte[] NO_TECHS_MESSAGE = Bytes.concat(HEADER_BYTES, new byte[]{0x0, 0x0});

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        StopRequest stopRangingMessageUwbCs =
                new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder()
                                .setUwb(true)
                                .setBleCs(true)
                                .build())
                        .build();
        StopRequest stopRangingMessageUwbOnly =
                new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder().setUwb(true).build())
                        .build();

        assertThat(stopRangingMessageUwbCs.toBytes()).isEqualTo(UWB_CS_TECH_MESSAGE);
        assertThat(stopRangingMessageUwbOnly.toBytes()).isEqualTo(UWB_ONLY_MESSAGE);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(StopRequest.fromBytes(UWB_CS_TECH_MESSAGE))
                .isEqualTo(new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder()
                                .setUwb(true)
                                .setBleCs(true)
                                .build())
                        .build());
        assertThat(StopRequest.fromBytes(UWB_ONLY_MESSAGE))
                .isEqualTo(new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder().setUwb(true).build())
                        .build());
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] tooShortMessage = new byte[]{0x0A};
        assertThrows(
                Exception.class,
                () -> StopRequest.fromBytes(tooShortMessage));
    }

    @Test
    public void parseBytes_noTechsSet_parsesToEmpty() throws Exception {
        assertThat(StopRequest.fromBytes(NO_TECHS_MESSAGE))
                .isEqualTo(new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder().build())
                        .build());
    }
}
