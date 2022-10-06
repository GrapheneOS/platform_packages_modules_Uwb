/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.uwb.support.oemextension.DeviceStatus;
import com.google.uwb.support.oemextension.SessionStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class OemExtensionTests {

    @Test
    public void testDeviceState() {
        int state = 1;
        String chipId = "TEST_CHIP_ID";

        DeviceStatus deviceState = new DeviceStatus.Builder()
                .setDeviceState(state)
                .setChipId(chipId)
                .build();

        assertEquals(deviceState.getDeviceState(), state);
        assertEquals(deviceState.getChipId(), chipId);

        DeviceStatus fromBundle = DeviceStatus.fromBundle(deviceState.toBundle());

        assertEquals(fromBundle.getDeviceState(), state);
        assertEquals(fromBundle.getChipId(), chipId);
    }

    @Test
    public void testSessionStatus() {
        long sessionId = 1;
        int state = 0;
        int reasonCode = 0;

        SessionStatus sessionStatus = new SessionStatus.Builder()
                .setSessionId(sessionId)
                .setState(state)
                .setReasonCode(reasonCode)
                .build();

        assertEquals(sessionStatus.getSessionId(), sessionId);
        assertEquals(sessionStatus.getState(), state);
        assertEquals(sessionStatus.getReasonCode(), reasonCode);

        SessionStatus fromBundle = SessionStatus.fromBundle(sessionStatus.toBundle());

        assertEquals(fromBundle.getSessionId(), sessionId);
        assertEquals(fromBundle.getState(), state);
        assertEquals(fromBundle.getReasonCode(), reasonCode);
    }
}
