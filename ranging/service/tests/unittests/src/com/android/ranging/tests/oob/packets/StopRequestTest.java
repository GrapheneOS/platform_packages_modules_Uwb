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

package com.android.server.ranging.tests.oob.packets;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.server.ranging.oob.packets.StopRequest;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.Version;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class StopRequestTest {
    private static final byte[] mUwbCsTechBitmap = new byte[]{0x3, 0x00};
    private static final byte[] mUwbTechOnlyBitmap = new byte[]{0x01, 0x00};
    private static final byte[] mHeaderBytes = new byte[]{0x1, 0x6};
    private static final byte[] mUwbCsTechMessage = Bytes.concat(mHeaderBytes, mUwbCsTechBitmap);
    private static final byte[] mUwbOnlyMessage = Bytes.concat(mHeaderBytes, mUwbTechOnlyBitmap);
    private static final byte[] mNoTechsMessage = Bytes.concat(mHeaderBytes, new byte[]{0x0, 0x0});

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

        assertThat(stopRangingMessageUwbCs.toBytes()).isEqualTo(mUwbCsTechMessage);
        assertThat(stopRangingMessageUwbOnly.toBytes()).isEqualTo(mUwbOnlyMessage);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(StopRequest.fromBytes(mUwbCsTechMessage))
                .isEqualTo(new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder()
                                .setUwb(true)
                                .setBleCs(true)
                                .build())
                        .build());
        assertThat(StopRequest.fromBytes(mUwbOnlyMessage))
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
        assertThat(StopRequest.fromBytes(mNoTechsMessage))
                .isEqualTo(new StopRequest.Builder()
                        .setVersion(Version.Current)
                        .setTechnologiesToStop(new TechnologySet.Builder().build())
                        .build());
    }
}
