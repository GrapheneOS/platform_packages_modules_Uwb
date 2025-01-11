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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 *
 */
public class RttServiceImpl implements RttService {
    private static final String TAG = RttServiceImpl.class.getSimpleName();
    private final Context mContext;
    private WifiAwareManager mWifiAwareManager;

    public RttServiceImpl(@NonNull Context context) {
        this.mContext = context;
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            mWifiAwareManager = context.getSystemService(WifiAwareManager.class);
            if (mWifiAwareManager == null) {
                Log.e(TAG, "Failed to get WifiAwareManager");
            }
        }
    }

    @Override
    public RttRangingDevice getPublisher(Context context) {
        return new RttRangingDevice(context, RttRangingDevice.DeviceType.PUBLISHER);
    }

    @Override
    public RttRangingDevice getSubscriber(Context context) {
        return new RttRangingDevice(context, RttRangingDevice.DeviceType.SUBSCRIBER);
    }

    @Override
    public boolean isAvailable() {
        if (mWifiAwareManager == null || !mWifiAwareManager.isAvailable()) {
            Log.w(TAG, "Wifi RTT unavailable");
            return false;
        }
        return true;
    }

    @SuppressLint("NewApi") // FLAG_RANGING_RTT_ENABLED will be enabled from Android 16.
    @Override
    public boolean hasPeriodicRangingSupport() {
        if (mWifiAwareManager == null) {
            Log.e(TAG, "WifiAwareManager is null");
            return false;
        }
        try {
            return mWifiAwareManager.getCharacteristics().isPeriodicRangingSupported();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to get WifiAwareManager#characteristics");
        }
        return false;
    }
}
