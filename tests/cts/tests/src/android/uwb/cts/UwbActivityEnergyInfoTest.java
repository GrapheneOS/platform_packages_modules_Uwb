/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.uwb.cts;

import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.os.Parcel;
import android.uwb.UwbActivityEnergyInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;


import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test of {@link UwbActivityEnergyInfo}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UwbActivityEnergyInfoTest {
    private static final long TIMESTAMP_MS = 10000;
    private static final int STACK_STATE = STATE_ENABLED_INACTIVE;
    private static final long TX_DURATION_MS = 10;
    private static final long RX_DURATION_MS = 20;
    private static final long IDLE_DURATION_MS = 300;
    private static final long WAKE_COUNT = 50;

    @Test
    public void testBuilder() {
        UwbActivityEnergyInfo.Builder builder = new UwbActivityEnergyInfo.Builder(TIMESTAMP_MS,
                STACK_STATE, TX_DURATION_MS, RX_DURATION_MS, IDLE_DURATION_MS, WAKE_COUNT);
        UwbActivityEnergyInfo info = tryBuild(builder, true);

        assertEquals(TIMESTAMP_MS, info.getTimeSinceBootMillis());
        assertEquals(STACK_STATE, info.getStackState());
        assertEquals(TX_DURATION_MS, info.getControllerTxDurationMillis());
        assertEquals(RX_DURATION_MS, info.getControllerRxDurationMillis());
        assertEquals(IDLE_DURATION_MS, info.getControllerIdleDurationMillis());
        assertEquals(WAKE_COUNT, info.getControllerWakeCount());
    }

    private UwbActivityEnergyInfo tryBuild(UwbActivityEnergyInfo.Builder builder,
            boolean expectSuccess) {
        UwbActivityEnergyInfo info = null;
        try {
            info = builder.build();
            if (!expectSuccess) {
                fail("Expected UwbActivityEnergyInfo.Builder.build() to fail");
            }
        } catch (IllegalStateException e) {
            if (expectSuccess) {
                fail("Expected UwbActivityEnergyInfo.Builder.build() to succeed");
            }
        }
        return info;
    }

    @Test
    public void testInvalidParams() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new UwbActivityEnergyInfo.Builder(-1, STACK_STATE, TX_DURATION_MS,
                        RX_DURATION_MS, IDLE_DURATION_MS, WAKE_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new UwbActivityEnergyInfo.Builder(TIMESTAMP_MS, -1, TX_DURATION_MS,
                        RX_DURATION_MS, IDLE_DURATION_MS, WAKE_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new UwbActivityEnergyInfo.Builder(TIMESTAMP_MS, STACK_STATE, -1,
                        RX_DURATION_MS, IDLE_DURATION_MS, WAKE_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new UwbActivityEnergyInfo.Builder(TIMESTAMP_MS, STACK_STATE, TX_DURATION_MS,
                        -1, IDLE_DURATION_MS, WAKE_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new UwbActivityEnergyInfo.Builder(TIMESTAMP_MS, STACK_STATE, TX_DURATION_MS,
                        RX_DURATION_MS, -1, WAKE_COUNT));
        assertThrows(IllegalArgumentException.class,
                () -> new UwbActivityEnergyInfo.Builder(TIMESTAMP_MS, STACK_STATE, TX_DURATION_MS,
                        RX_DURATION_MS, IDLE_DURATION_MS, -1));

    }

    @Test
    public void testParcel() throws Exception {
        Parcel parcel = Parcel.obtain();
        UwbActivityEnergyInfo info = UwbTestUtils.getUwbActivityEnergyInfo();
        info.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        UwbActivityEnergyInfo infoFromParcel =
                UwbActivityEnergyInfo.CREATOR.createFromParcel(parcel);
        assertEquals(info, infoFromParcel);
    }

    @Test
    public void testToStringThrowsNoExceptions() throws Exception {
        UwbActivityEnergyInfo info = UwbTestUtils.getUwbActivityEnergyInfo();
        try {
            String infoString = info.toString();
        } catch (Exception e) {
            throw new AssertionError("Should throw a RuntimeException", e);
        }
    }
}
