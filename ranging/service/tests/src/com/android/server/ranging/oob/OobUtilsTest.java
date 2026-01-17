/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ranging.oob;

import static org.junit.Assert.assertEquals;

import android.ranging.MotionState;

import com.android.server.ranging.oob.packets.Motion;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OobUtilsTest {
    @Test
    public void fromOobMotion_validEnums_returnsCorrectInts() {
        assertEquals(MotionState.MOTION_NOT_DETECTED, OobUtils.fromOobMotion(Motion.NotDetected));
        assertEquals(MotionState.MOTION_SLIGHT, OobUtils.fromOobMotion(Motion.Slight));
        assertEquals(MotionState.MOTION_MODERATE, OobUtils.fromOobMotion(Motion.Moderate));
        assertEquals(MotionState.MOTION_LARGE, OobUtils.fromOobMotion(Motion.Large));
    }

    @Test
    public void fromOobMotion_nullInput_defaultsToNotDetected() {
        assertEquals(MotionState.MOTION_NOT_DETECTED, OobUtils.fromOobMotion(null));
    }
}
