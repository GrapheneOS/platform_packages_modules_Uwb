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

import com.android.server.ranging.oob.packets.BleCsConfiguration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class BleCsConfigurationTest {

    private static final BleCsConfiguration CS_CONFIG = new BleCsConfiguration.Builder()
            .setSecurityLevel((byte) 0x1)
            .setAddress(new byte[] {0x1, 0x2, 0x3, 0x4, 0x5, 0x6})
            .build();

    private static final byte[] csConfigBytes =
            new byte[]{
                    // CS Technology Id
                    0x01,
                    // Size
                    0x09,
                    // Security level
                    0x01,
                    // Address
                    0x01, 0x02, 0x03, 0x04, 0x05, 0x06
            };

    @Test
    public void toBytes_convertsCorrectly() throws Exception {
        assertThat(CS_CONFIG.toBytes()).isEqualTo(csConfigBytes);
    }

    @Test
    public void parseBytes_parsesCorrectly() throws Exception {
        assertThat(BleCsConfiguration.fromBytes(csConfigBytes)).isEqualTo(CS_CONFIG);
    }

    @Test
    public void parseBytes_invalidSize_throws() throws Exception {
        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(Exception.class,
                () -> BleCsConfiguration.fromBytes(shortMessage));
    }

    @Test
    public void parseBytes_invalidTechnologyId_throws() throws Exception {
        byte[] unknownConfigBytes = new byte[]{0x09, 0x02};

        assertThrows(Exception.class,
                () -> BleCsConfiguration.fromBytes(unknownConfigBytes));
    }
}
