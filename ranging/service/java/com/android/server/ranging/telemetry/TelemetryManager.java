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

package com.android.server.ranging.telemetry;

import android.content.AttributionSource;
import android.ranging.RangingConfig;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;

import com.android.server.ranging.RangingInjector;

import java.util.concurrent.ConcurrentHashMap;

public class TelemetryManager {
    private final RangingInjector mInjector;
    private final ConcurrentHashMap<SessionHandle, SessionTelemetryLogger> mLoggers;

    public TelemetryManager(RangingInjector injector) {
        mInjector = injector;
        mLoggers = new ConcurrentHashMap<>();
    }

    public void registerLogger(
            SessionHandle handle,
            @RangingPreference.DeviceRole int deviceRole,
            @RangingConfig.RangingSessionType int sessionType,
            AttributionSource attributionSource
    ) {
        mLoggers.put(handle, new SessionTelemetryLogger(
                handle, deviceRole, sessionType, attributionSource, mInjector));
    }

    public SessionTelemetryLogger getLogger(SessionHandle handle) {
        return mLoggers.get(handle);
    }

    public void unregisterLogger(SessionHandle handle) {
        mLoggers.remove(handle);
    }
}
