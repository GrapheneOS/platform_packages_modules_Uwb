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

package com.android.server.ranging.rtt;

import static android.net.wifi.aware.WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED;
import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities.RangingTechnologyAvailability;
import android.ranging.wifi.rtt.RttRangingCapabilities;

import androidx.annotation.Nullable;

import com.android.ranging.rtt.backend.internal.RttServiceImpl;
import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.CapabilitiesProvider.TechnologyAvailabilityListener;

public class RttCapabilitiesAdapter extends CapabilitiesAdapter {

    private final Context mContext;
    private final RttServiceImpl mRttService;

    /** @return true if WiFi RTT is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    public RttCapabilitiesAdapter(
            @NonNull Context context,
            @NonNull TechnologyAvailabilityListener listener
    ) {
        super(listener);
        mContext = context;
        if (isSupported(mContext)) {
            mRttService = new RttServiceImpl(context);
            WifiAwareStateChangeReceiver receiver = new WifiAwareStateChangeReceiver();
            IntentFilter filter = new IntentFilter(ACTION_WIFI_AWARE_STATE_CHANGED);
            mContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mRttService = null;
        }
    }

    @Override
    public @RangingTechnologyAvailability int getAvailability() {
        if (mRttService == null) {
            return NOT_SUPPORTED;
        } else if (mRttService.isAvailable()) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    @Override
    @Nullable
    public RttRangingCapabilities getCapabilities() {
        if (getAvailability() == ENABLED) {
            return new RttRangingCapabilities.Builder()
                    .setPeriodicRangingHardwareFeature(mRttService.hasPeriodicRangingSupport())
                    .build();
        }
        return null;
    }

    private class WifiAwareStateChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            TechnologyAvailabilityListener listener = getAvailabilityListener();
            if (listener != null) {
                listener.onAvailabilityChange(
                        getAvailability(),
                        CapabilitiesProvider.AvailabilityChangedReason.SYSTEM_POLICY);
            }
        }
    }
}
