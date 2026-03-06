/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.server.ranging.common;

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.ConfigurationManager;

import com.google.common.collect.ImmutableSet;

import java.time.Duration;

/** Some technologies do not need to be configured, CS responder for example. */
public class EmptyTechnologyConfig implements ConfigurationManager.TechnologyConfig {
    RangingTechnology mTechnology;
    @RangingPreference.DeviceRole int mRole;
    ImmutableSet<RangingDevice> mPeers;

    public EmptyTechnologyConfig(
            RangingTechnology technology, @RangingPreference.DeviceRole int role,
            ImmutableSet<RangingDevice> peers
    ) {
        mTechnology = technology;
        mRole = role;
        mPeers = peers;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return mTechnology;
    }

    @Override
    public int getDeviceRole() {
        return mRole;
    }

    @Override
    public Duration getRangingInterval() {
        return null;
    }

    @Override
    public @NonNull ImmutableSet<RangingDevice> getPeerDevices() {
        return mPeers;
    }
}
