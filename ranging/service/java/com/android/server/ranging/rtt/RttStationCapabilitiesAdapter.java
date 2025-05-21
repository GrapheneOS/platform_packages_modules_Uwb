/*
 * Copyright 2025 The Android Open Source Project
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

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities.RangingTechnologyAvailability;
import android.ranging.RangingManager;
import android.ranging.wifi.rtt.RttStationRangingCapabilities;

import androidx.annotation.Nullable;

import com.android.ranging.flags.Flags;
import com.android.ranging.rtt.backend.RttServiceImpl;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.CapabilitiesProvider.TechnologyAvailabilityListener;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;

@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_25Q4)
public class RttStationCapabilitiesAdapter extends CapabilitiesAdapter {

    private final Context mContext;
    private final RttServiceImpl mRttService;

    /** @return true if WiFi RTT is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
                && (RangingInjector.getInstance().isRangingTechnologyEnabled(
                RangingTechnology.RTT_STATION));
    }

    public RttStationCapabilitiesAdapter(
            @NonNull Context context,
            @NonNull TechnologyAvailabilityListener listener
    ) {
        super(listener);
        mContext = context;
        if (isSupported(mContext)) {
            mRttService = new RttServiceImpl(context, RangingManager.WIFI_STA_RTT);
        } else {
            mRttService = null;
        }
    }

    @Override
    public @RangingTechnologyAvailability int getAvailability() {
        if (mRttService == null) {
            return NOT_SUPPORTED;
        } else if (mRttService.isWifiAvailable()) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    @Override
    @Nullable
    public RttStationRangingCapabilities getCapabilities() {
        if (getAvailability() == ENABLED) {
            return new RttStationRangingCapabilities.Builder()
                    .setNumSupportedBands(mRttService.getNumSupportedBands())
                    .setSupportedSecurity(mRttService.getSupportedSecurity())
                    .build();
        }
        return null;
    }
}

