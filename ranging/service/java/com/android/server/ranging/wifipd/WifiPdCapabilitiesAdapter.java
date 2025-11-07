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

package com.android.server.ranging.wifipd;

import static android.os.Build.VERSION.SDK_INT;
import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.rtt.WifiRttManager;
import android.net.wifi.usd.UsdManager;
import android.ranging.RangingCapabilities;

import androidx.annotation.Nullable;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.uwb.UwbCapabilitiesAdapter;


/**
 * Wifi pd capabilities adapter.
 */
public class WifiPdCapabilitiesAdapter extends CapabilitiesAdapter {
    private static final String TAG = UwbCapabilitiesAdapter.class.getSimpleName();

    private final Context mContext;
    private final WifiRttManager mWifiRttManager;
    private final UsdManager mUsdManager;


    /**
     * Is Wifi PD feature supported boolean.
     *
     * @param context the context
     * @return the boolean
     */
    public static boolean isSupported(Context context) {
        UsdManager usdManager = context.getSystemService(UsdManager.class);
        return SDK_INT >= 37 // Update to isAtLeastC() when available
                && usdManager != null
                && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
                && usdManager.getCharacteristics().isFindingProximityDetectionDevicesSupported();
    }

    public WifiPdCapabilitiesAdapter(
            @NonNull Context context,
            @NonNull CapabilitiesProvider.TechnologyAvailabilityListener listener
    ) {
        super(listener);
        mContext = context;
        if (isSupported(context)) {
            mUsdManager = context.getSystemService(UsdManager.class);
            mWifiRttManager = context.getSystemService(WifiRttManager.class);
        } else {
            mUsdManager = null;
            mWifiRttManager = null;
        }
    }

    @Override
    public @RangingCapabilities.RangingTechnologyAvailability int getAvailability() {
        if (mUsdManager == null || mWifiRttManager == null) {
            return NOT_SUPPORTED;
        } else if (mWifiRttManager.isAvailable()) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    @Nullable
    @Override
    public RangingCapabilities.TechnologyCapabilities getCapabilities() {
        // TODO Fill capabilities
        return null;
    }
}
