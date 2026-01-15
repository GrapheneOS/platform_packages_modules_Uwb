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

import android.ranging.MotionState;
import android.util.Log;

import com.android.server.ranging.oob.packets.Motion;

public final class OobUtils {
    private static final String TAG = OobUtils.class.getSimpleName();

    // Prevent instantiation
    private OobUtils() {}

    /**
     * Converts the OOB protocol {@link Motion} enum to the Android API {@link MotionState} integer.
     *
     * @param motion The {@link Motion} enum received via OOB.
     * @return The corresponding {@link MotionState} integer.
     */
    public static @MotionState.Motion int fromOobMotion(Motion motion) {
        if (motion == null) {
            Log.w(TAG, "Received null Motion enum");
            return MotionState.MOTION_NOT_DETECTED;
        }

        if (motion.equals(Motion.NotDetected)) {
            return MotionState.MOTION_NOT_DETECTED;
        }
        if (motion.equals(Motion.Slight)) {
            return MotionState.MOTION_SLIGHT;
        }
        if (motion.equals(Motion.Moderate)) {
            return MotionState.MOTION_MODERATE;
        }
        if (motion.equals(Motion.Large)) {
            return MotionState.MOTION_LARGE;
        }
        return MotionState.MOTION_NOT_DETECTED;
    }
}
