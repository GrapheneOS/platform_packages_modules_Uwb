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

package com.android.server.ranging.session;

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;

import com.google.common.collect.ImmutableSet;

public class ConfigurationManager {
    /** A complete configuration for a session within a specific ranging technology's stack */
    public interface TechnologyConfig {
        @NonNull
        RangingTechnology getTechnology();

        @RangingPreference.DeviceRole int getDeviceRole();
    }

    /** A config for a technology that only supports 1 peer per session. */
    public interface UnicastTechnologyConfig extends TechnologyConfig {
        @NonNull
        RangingDevice getPeerDevice();
    }

    /** A config for a technology that supports multiple peers per session. */
    public interface MulticastTechnologyConfig extends TechnologyConfig {
        /**
         * @return the set of peers within this technology-specific session.
         */
        @NonNull
        ImmutableSet<RangingDevice> getPeerDevices();
    }
}
