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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities;
import android.ranging.RangingManager.RangingTechnologyAvailability;

import androidx.annotation.Nullable;

import com.android.ranging.rtt.backend.internal.RttServiceImpl;
import com.android.server.ranging.CapabilitiesProvider.AvailabilityCallback;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;

public class RttCapabilitiesAdapter extends CapabilitiesAdapter {
    private final Context mContext;
    private final RttServiceImpl mRttService;

    /** @return true if WiFi RTT is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    public RttCapabilitiesAdapter(Context context) {
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
            return RangingTechnologyAvailability.NOT_SUPPORTED;
        } else if (mRttService.isAvailable()) {
            return RangingTechnologyAvailability.ENABLED;
        } else {
            return RangingTechnologyAvailability.DISABLED_USER;
        }
    }

    @Override
    public @Nullable RangingCapabilities.TechnologyCapabilities getCapabilities() {
        // TODO
        return null;
    }

    private class WifiAwareStateChangeReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            AvailabilityCallback callback = getAvailabilityCallback();
            if (callback != null) {
                callback.onAvailabilityChange(
                        getAvailability(),
                        AvailabilityCallback.AvailabilityChangedReason.SYSTEM_POLICY);
            }
        }
    }
}
