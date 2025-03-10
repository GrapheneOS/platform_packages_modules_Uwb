/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.server.ranging.rtt.RttOobConfig;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RttOobConfigTest {

    private static final RttOobConfig RTT_OOB_CONFIG = RttOobConfig.builder()
            .setUsePeriodicRanging(false)
            .setServiceName("rttConfigTest")
            .setDeviceRole(1)
            .build();

    private final byte[] mRttConfigBytes =
            new byte[]{
                    // Ranging technology Id
                    (byte) 0x02,
                    // Size
                    (byte) 0x12,
                    // Service name length
                    (byte) 0x0d,
                    // Service name
                    (byte) 0x72, (byte) 0x74, (byte) 0x74, (byte) 0x43, (byte) 0x6f, (byte) 0x6e,
                    (byte) 0x66, (byte) 0x69, (byte) 0x67, (byte) 0x54, (byte) 0x65,
                    (byte) 0x73, (byte) 0x74,
                    // Device role
                    (byte) 0x01,
                    //Periodic ranging
                    (byte) 0x00};

    @Test
    public void rttOobConfigValidate_tests() throws Exception {
        assertThat(RTT_OOB_CONFIG.toBytes()).isEqualTo(mRttConfigBytes);

        assertThat(RttOobConfig.parseBytes(mRttConfigBytes)).isEqualTo(RTT_OOB_CONFIG);

        byte[] shortMessage = new byte[]{0x0A};
        assertThrows(IllegalArgumentException.class, () -> RttOobConfig.parseBytes(shortMessage));

        byte[] unknownConfigBytes = new byte[]{0x09, 0x02};

        assertThrows(IllegalArgumentException.class,
                () -> RttOobConfig.parseBytes(unknownConfigBytes));
    }

}
