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

package com.android.ranging.rtt.backend;

/**
 * Callbacks used by startRanging.
 */
public interface RttRangingSessionCallback {

    int REASON_UNKNOWN = 0;
    int REASON_WRONG_PARAMETERS = 1;
    int REASON_FAILED_TO_START = 2;
    int REASON_STOPPED_BY_PEER = 3;
    int REASON_STOP_RANGING_CALLED = 4;
    int REASON_MAX_RANGING_ROUND_RETRY_REACHED = 5;
    int REASON_SYSTEM_POLICY = 6;
    int REASON_RTT_NOT_AVAILABLE = 7;

    /**
     * Callback when a ranging session has been initiated.
     */
    void onRangingInitialized(RttDevice device);

    /**
     * Callback when a ranging device's position is received.
     */
    void onRangingResult(RttDevice device, RttRangingPosition position);

    /**
     * Callback when a session has been suspended.
     */
    void onRangingSuspended(RttDevice device, int reason);

}
