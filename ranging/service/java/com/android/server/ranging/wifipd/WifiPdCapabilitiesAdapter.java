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

import static android.net.wifi.WifiAvailableChannel.OP_MODE_STA;
import static android.net.wifi.WifiScanner.WIFI_BAND_BOTH;
import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.DISABLED_USER_RESTRICTIONS;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.NonNull;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.ProximityDetectionCharacteristics;
import android.net.wifi.rtt.WifiRttManager;
import android.ranging.RangingCapabilities;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;

import androidx.annotation.Nullable;

import com.android.ranging.flags.Flags;
import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;


/**
 * Wifi pd capabilities adapter.
 */
public class WifiPdCapabilitiesAdapter extends CapabilitiesAdapter {

    private final Context mContext;
    private final WifiManager mWifiManager;
    private final WifiRttManager mWifiRttManager;
    private MacAddress mCachedPdMacAddress = null;

    /**
     * Is Wifi PD feature supported boolean.
     *
     * @param context the context
     * @return the boolean
     */
    public static boolean isSupported(Context context) {
        WifiRttManager wifiRttManager = context.getSystemService(WifiRttManager.class);
        try {
            return Flags.rangingStackUpdates26Q2()
                    && com.android.wifi.flags.Flags.proximityRanging()
                    && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
                    && wifiRttManager != null
                    && wifiRttManager.getProximityDetectionCharacteristics() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public WifiPdCapabilitiesAdapter(
            @NonNull Context context,
            @NonNull CapabilitiesProvider.TechnologyAvailabilityListener listener
    ) {
        super(listener);
        Objects.requireNonNull(listener);
        mContext = context;
        if (isSupported(context)) {
            mWifiRttManager = context.getSystemService(WifiRttManager.class);
            mWifiManager = context.getSystemService(WifiManager.class);
            mWifiRttManager.registerProximityDetectionMacAddressCallback(
                    Executors.newSingleThreadExecutor(),
                    macAddress -> {
                        mCachedPdMacAddress = macAddress;
                        listener.onAvailabilityChange(getAvailability(),
                                CapabilitiesProvider.AvailabilityChangedReason.SYSTEM_POLICY);
                    });
        } else {
            mWifiRttManager = null;
            mWifiManager = null;
        }
    }

    @Override
    public @RangingCapabilities.RangingTechnologyAvailability int getAvailability() {
        if (mWifiRttManager == null) {
            return NOT_SUPPORTED;
        } else if (mWifiRttManager.isAvailable() && getPrximityDetectionMacAddress() == null) {
            return DISABLED_USER_RESTRICTIONS;
        } else if (mWifiRttManager.isAvailable()) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    @Nullable
    @Override
    public RangingCapabilities.TechnologyCapabilities getCapabilities() {
        if (getAvailability() != ENABLED) {
            return null;
        }
        ProximityDetectionCharacteristics characteristics =
                mWifiRttManager.getProximityDetectionCharacteristics();
        Set<Integer> pasnModes = new HashSet<>();
        if (characteristics.isUnauthenticatedPasnModeSupported()) {
            pasnModes.add(WifiPdRangingCapabilities.UNAUTHENTICATED_PASN_MODE);
        }
        if (characteristics.isAuthenticatedPasnModeSupported()) {
            pasnModes.add(WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE);
        }
        Set<Integer> channelSet = mWifiManager.getUsableChannels(WIFI_BAND_BOTH, OP_MODE_STA)
                .stream()
                .map(WifiAvailableChannel::getFrequencyMhz)
                .collect(Collectors.toSet());

        return new WifiPdRangingCapabilities.Builder()
                .setSupportedPasnModes(pasnModes)
                .set80211mcSupported(characteristics.is80211mcBasedRangingSupported())
                .set80211azNtbSupported(characteristics.isNtbIstaRoleSupported())
                .setMinRangingInterval80211mcMillis(
                        characteristics.getMinAllowedRangingInterval80211mcMillis())
                .setMinRangingInterval80211azNtbMillis(
                        characteristics.getMinAllowedRangingIntervalNtbMillis())
                .setSupportedDiscoveryChannelFrequenciesMhz(channelSet)
                .setMaxPreamble(characteristics.isNtbIstaRoleSupported()
                        ? characteristics.getMaxSupportedPreambleNtb()
                        : characteristics.getMaxSupportedPreamble80211mcBased())
                .setMaxChannelWidth(characteristics.isNtbIstaRoleSupported()
                        ? characteristics.getMaxSupportedPacketWidthNtb()
                        : characteristics.getMaxSupportedPacketWidth80211mcBased())
                .setProximityDetectionMacAddress(getPrximityDetectionMacAddress())
                .build();
    }

    private MacAddress getPrximityDetectionMacAddress() {
        if (mCachedPdMacAddress == null) {
            mCachedPdMacAddress = mWifiRttManager.getProximityDetectionRandomizedMacAddress();
        }
        return mCachedPdMacAddress;
    }

    private final BroadcastReceiver mWifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)) {
                CapabilitiesProvider.TechnologyAvailabilityListener listener =
                        getAvailabilityListener();
                if (listener != null) {
                    listener.onAvailabilityChange(getAvailability(),
                            CapabilitiesProvider.AvailabilityChangedReason.SYSTEM_POLICY);
                }
            }
        }
    };
}