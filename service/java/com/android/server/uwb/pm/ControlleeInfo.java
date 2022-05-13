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

package com.android.server.uwb.pm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Provide the controllee info, see CSML 8.5.3.2
 */
public class ControlleeInfo {
    // TODO: add controllee info
    ControlleeInfo() {
    }
    /**
     * Converts the controllee info to the bytes which are combined per the TLV of CSML 8.5.3.2.
     */
    @NonNull
    public byte[] toBytes() {
        return new byte[0];
    }

    /**
     * Converts the controlleeInfo from the data stream, which is encoded per the CSML 8.5.3.2.
     * @return null if the data cannot be decoded per spec.
     */
    @Nullable
    public static ControlleeInfo fromBytes(@NonNull byte[] data) {
        // TODO: decode the controllee info
        return new ControlleeInfo();
    }
}
