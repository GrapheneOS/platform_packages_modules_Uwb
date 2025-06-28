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
import android.net.wifi.WifiManager;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.WifiAwareManager;
import android.ranging.RangingManager;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 *
 */
public class RttServiceImpl implements RttService {
    private static final String TAG = RttServiceImpl.class.getSimpleName();
    private final Context mContext;
    private WifiAwareManager mWifiAwareManager;
    private WifiManager mWifiManager;
    private Characteristics mCharacteristics;

    /*public RttServiceImpl(@NonNull Context context) {
        this.mContext = context;
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            mWifiAwareManager = context.getSystemService(WifiAwareManager.class);
            if (mWifiAwareManager == null) {
                Log.e(TAG, "Failed to get WifiAwareManager");
            }
        }
    }*/

    public RttServiceImpl(@NonNull Context context, int tech) {
        this.mContext = context;
        if (tech == RangingManager.WIFI_STA_RTT
                && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            mWifiManager = context.getSystemService(WifiManager.class);
            if (mWifiManager == null) {
                Log.e(TAG, "Failed to get WifiManager");
            }
        } else if (tech == RangingManager.WIFI_NAN_RTT
                && mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WIFI_AWARE)) {
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
    public RttRangingDevice getStation(Context context) {
        return new RttRangingDevice(context, RttRangingDevice.DeviceType.STATION);
    }

    @Override
    public boolean isAvailable() {
        if (mWifiAwareManager == null || !mWifiAwareManager.isAvailable()) {
            Log.w(TAG, "Wifi RTT unavailable");
            return false;
        }
        return true;
    }

    @Override
    public boolean isWifiAvailable() {
        if (!mWifiManager.isWifiEnabled()) {
            Log.e(TAG, "Wifi is not enabled");
            return false;
        }
        return true;
    }

    public void getCharacteristics() {
        if (mCharacteristics != null) {
            return;
        }
        if (mWifiAwareManager == null) {
            Log.e(TAG, "WifiAwareManager is null");
            return;
        }
        try {
            mCharacteristics = mWifiAwareManager.getCharacteristics();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to get WifiAwareManager#characteristics");
        }
    }

    @SuppressLint("NewApi") // FLAG_RANGING_RTT_ENABLED will be enabled from Android 16.
    @Override
    public boolean hasPeriodicRangingSupport() {
        getCharacteristics();
        if (mCharacteristics == null) {
            Log.e(TAG, "Not able to get WiFi aware characteristics");
            return false;
        }
        return mCharacteristics.isPeriodicRangingSupported();
    }

    @SuppressLint("NewApi") // FLAG_RANGING_RTT_ENABLED will be enabled from Android 16.
    @Override
    public int getMaxSupportedBandwidth() {
        getCharacteristics();
        if (mCharacteristics == null) {
            Log.e(TAG, "Not able to get WiFi aware characteristics");
            return 255;
        }
        return mCharacteristics.getMaxSupportedRangingPacketBandwidth();
    }

    @SuppressLint("NewApi") // FLAG_RANGING_RTT_ENABLED will be enabled from Android 16.
    @Override
    public int getMaxSupportedRxChains() {
        getCharacteristics();
        if (mCharacteristics == null) {
            Log.e(TAG, "Not able to get WiFi aware characteristics");
            return 255;
        }
        return mCharacteristics.getMaxSupportedRxChains();
    }

    @SuppressLint("NewApi")
    @Override
    public int getNumSupportedBands() {
        return 2; //2g, 5g
    }

    @SuppressLint("NewApi")
    @Override
    public int getSupportedSecurity() {
        return 0; //No security
    }

}
