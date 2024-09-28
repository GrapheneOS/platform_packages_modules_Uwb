/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.ranging.uwb;

/** Device role, whether it's an initiator or a responder. */
public enum UwbDeviceRole {
    UNKNOWN(0),
    INITIATOR(1),
    RESPONDER(2);

    private final int mValue;

    UwbDeviceRole(int value) {
        this.mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    public static UwbDeviceRole fromValue(int value) {
        return value < 0 || value > 2 ? UNKNOWN : UwbDeviceRole.values()[value];
    }
}
