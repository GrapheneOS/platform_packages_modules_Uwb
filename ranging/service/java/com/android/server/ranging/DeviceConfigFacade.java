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

package com.android.server.ranging;

import android.content.Context;
import android.os.Handler;
import android.provider.DeviceConfig;

import com.android.uwb.resources.R;

/**
 * This class allows getting all configurable flags from DeviceConfig.
 */
public class DeviceConfigFacade {
    private final Context mContext;

    // Cached values of fields updated via updateDeviceConfigFlags()
    private String[] mTechnologyPreferenceList;
    private int mRttRangingRequestDelay;

    public DeviceConfigFacade(Handler handler, Context context) {
        mContext = context;
        updateDeviceConfigFlags();
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_UWB,
                command -> handler.post(command),
                properties -> {
                    updateDeviceConfigFlags();
                });
    }

    // device config override with array is not natively supported, so read the value as a comma
    // separated string.
    private static String[] getDeviceConfigStringArray(String name, String[] defaultValue) {
        String value = DeviceConfig.getString(DeviceConfig.NAMESPACE_UWB, name, null);
        if (value == null) {
            return defaultValue;
        }
        return value.split(" , ");
    }

    private void updateDeviceConfigFlags() {
        mTechnologyPreferenceList = getDeviceConfigStringArray(
                "technology_preference_list",
                mContext.getResources().getStringArray(R.array.technology_preference_list)
        );
        mRttRangingRequestDelay = DeviceConfig.getInt(DeviceConfig.NAMESPACE_UWB,
                "rtt_ranging_request_delay",
                mContext.getResources().getInteger(R.integer.rtt_ranging_request_delay));
    }

    public String[] getTechnologyPreferenceList() {
        return mTechnologyPreferenceList;
    }

    public int getRttRangingRequestDelay() {
        return mRttRangingRequestDelay;
    }
}
