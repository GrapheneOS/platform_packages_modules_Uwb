/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import android.os.Handler;
import android.provider.DeviceConfig;

/**
 * This class allows getting all configurable flags from DeviceConfig.
 */
public class DeviceConfigFacade {
    public static final int DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS = 5_000;

    private final UwbInjector mUwbInjector;

    // Cached values of fields updated via updateDeviceConfigFlags()
    private int mRangingResultLogIntervalMs;

    public DeviceConfigFacade(Handler handler, UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;

        updateDeviceConfigFlags();
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_UWB,
                command -> handler.post(command),
                properties -> {
                    updateDeviceConfigFlags();
                });
    }

    private void updateDeviceConfigFlags() {
        mRangingResultLogIntervalMs = DeviceConfig.getInt(DeviceConfig.NAMESPACE_UWB,
                "ranging_result_log_interval_ms", DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
    }

    /**
     * Gets ranging result logging interval in ms
     */
    public int getRangingResultLogIntervalMs() {
        return mRangingResultLogIntervalMs;
    }
}
