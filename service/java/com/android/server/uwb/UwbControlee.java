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

package com.android.server.uwb;

import android.uwb.UwbAddress;

/**
 * Represents a remote controlee that is involved in a session.
 */
public class UwbControlee {
    private final UwbAddress mUwbAddress;

    /**
     * Creates a new UwbControlee.
     *
     * @param uwbAddress The address of the controlee.
     */
    public UwbControlee(UwbAddress uwbAddress) {
        mUwbAddress = uwbAddress;
    }

    /**
     * Gets the address of the controlee.
     *
     * @return A UwbAddress of the associated controlee.
     */
    public UwbAddress getUwbAddress() {
        return mUwbAddress;
    }
}
