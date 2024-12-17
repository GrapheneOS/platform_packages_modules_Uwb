/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging.util;

import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_DISABLE;
import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE;
import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_PROXIMITY_EDGE;
import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_PROXIMITY_LEVEL;

import android.ranging.DataNotificationConfig;

public class DataNotificationManager {

    private final DataNotificationConfig mInitialConfig;

    private DataNotificationConfig mCurrentConfig;

    private boolean mCheckProximityEdgeFlag = true;

    public DataNotificationManager(DataNotificationConfig initialConfig,
            DataNotificationConfig currentConfig) {
        mInitialConfig = initialConfig;
        mCurrentConfig = currentConfig;
    }

    public DataNotificationConfig getInitialConfig() {
        return mInitialConfig;
    }

    public DataNotificationConfig getCurrentConfig() {
        return mCurrentConfig;
    }

    public void updateConfigAppMovedToBackground() {
        mCurrentConfig = new DataNotificationConfig.Builder()
                .setNotificationConfigType(NOTIFICATION_CONFIG_DISABLE)
                .build();
        mCheckProximityEdgeFlag = true;
    }

    public void updateConfigAppMovedToForeground() {
        mCurrentConfig = mInitialConfig;
        mCheckProximityEdgeFlag = true;
    }

    public boolean shouldSendResult(double distanceMeters) {
        switch (mCurrentConfig.getNotificationConfigType()) {
            case NOTIFICATION_CONFIG_ENABLE -> {
                return true;
            }
            case NOTIFICATION_CONFIG_PROXIMITY_LEVEL -> {
                if (distanceMeters <= ((double) mCurrentConfig.getProximityFarCm() / 100)
                        && distanceMeters >= ((double) mCurrentConfig.getProximityNearCm() / 100)) {
                    return true;
                }
            }
            case NOTIFICATION_CONFIG_PROXIMITY_EDGE -> {
                return shouldSendProximityEdgeResult(distanceMeters);
            }
        }
        return false;
    }

    private boolean shouldSendProximityEdgeResult(double distanceMeters) {
        int nearMeters = mCurrentConfig.getProximityNearCm() / 100;
        int farMeters = mCurrentConfig.getProximityFarCm() / 100;

        // Notification for crossing above `far` or below `near`
        if (!mCheckProximityEdgeFlag && (distanceMeters <= nearMeters
                || distanceMeters >= farMeters)) {
            mCheckProximityEdgeFlag = true;
            return true;
        }

        // Notification for crossing back below `far` or back above `near`
        if (mCheckProximityEdgeFlag && (distanceMeters > nearMeters
                && distanceMeters < farMeters)) {
            mCheckProximityEdgeFlag = false;
            return true;
        }
        return false;
    }
}
