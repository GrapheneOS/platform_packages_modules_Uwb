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

package com.android.server.ranging.cs;

import android.content.Context;
import android.ranging.RangingManager.RangingTechnologyAvailability;

import androidx.annotation.Nullable;

import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;

public class CsCapabilitiesAdapter extends CapabilitiesAdapter {
    public static boolean isSupported(Context context) {
        return false;
    }

    @Override
    public @RangingTechnologyAvailability int getAvailability() {
        return RangingTechnologyAvailability.NOT_SUPPORTED;
    }

    @Override
    public @Nullable CsCapabilities getCapabilities() {
        // TODO
        return null;
    }
}
