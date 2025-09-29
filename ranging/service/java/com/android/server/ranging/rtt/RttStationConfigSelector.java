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

package com.android.server.ranging.rtt;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import static com.android.server.ranging.RangingUtils.getUpdateRateFromDurationRange;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.wifi.rtt.RttStationRangingCapabilities;
import android.ranging.wifi.rtt.RttStationRangingParams;
import android.util.Pair;

import com.android.ranging.flags.Flags;
import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingEngine.ConfigSelectionException;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.oob.packets.UnknownConfiguration;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_25Q4)
public class RttStationConfigSelector extends RangingEngine.ConfigSelector {

    public static int RTT_SUFFIX_SIZE = 6;
    private static int sSupportedBands = 0;
    private static final String BSSID = "AA:BB:CC:AA:BB:CC";
    private static final int BANDWIDTH = 0; //no preference
    private final SessionConfig mSessionConfig;

    private final OobInitiatorRangingConfig mOobConfig;

    private final Map<RangingDevice, RttStationDeviceConfig> mRangingDevices =
            new ConcurrentHashMap<>();
    public static ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration>
            RTT_UPDATE_RATE_DURATIONS;

    private static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttStationRangingCapabilities capabilities) {

        if (capabilities == null) return false;
        sSupportedBands = capabilities.getNumSupportedBands();
        if (RTT_UPDATE_RATE_DURATIONS == null) {
            getLazyUpdateRate();
        }

        if (getUpdateRateFromDurationRange(
                oobConfig.getRangingIntervalRange(), RTT_UPDATE_RATE_DURATIONS).isEmpty()
        ) {
            return false;
        }

        return true;
    }

    public RttStationConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttStationRangingCapabilities capabilities
    ) throws ConfigSelectionException {
        if (!isCapableOfConfig(oobConfig, capabilities)) {
            throw new ConfigSelectionException(
                    "Local device is incapable of provided 8011MC RTT config",
                    InternalReason.UNSUPPORTED);
        }

        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
    }

    @Override
    public boolean hasPeersToConfigure() {
        return !mRangingDevices.isEmpty();
    }


    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull Capabilities baseCapabilities
    ) throws ConfigSelectionException {
        //do nothing
    }

    @Override
    public @NonNull Pair<
            ImmutableSet<TechnologyConfig>,
            ImmutableMap<RangingDevice, Configuration>
    > selectConfigs() throws ConfigSelectionException {
        SelectedRttStationConfig configs = new SelectedRttStationConfig();
        return Pair.create(configs.getLocalConfigs(), configs.getPeerConfigs());
    }

    private class SelectedRttStationConfig {
        // TODO: Check whether this needs to be added to OOB.
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;

        SelectedRttStationConfig() throws ConfigSelectionException {
            mRangingUpdateRate = getUpdateRateFromDurationRange(
                    mOobConfig.getRangingIntervalRange(), RTT_UPDATE_RATE_DURATIONS)
                    .orElseThrow(() -> new ConfigSelectionException(
                            "Configured ranging interval range is incompatible with Wifi RTT",
                            InternalReason.UNSUPPORTED));
        }

        @NonNull
        public ImmutableSet<TechnologyConfig> getLocalConfigs() {
            return mRangingDevices.entrySet().stream()
                    .map((entry -> new RttConfig(DEVICE_ROLE_INITIATOR,
                            new RttStationRangingParams.Builder(entry.getValue().mBssid)
                                    .build(),
                            mSessionConfig,
                            entry.getKey())))
                    .collect(ImmutableSet.toImmutableSet());
        }

        @NonNull
        public ImmutableMap<RangingDevice, Configuration> getPeerConfigs() {
            return mRangingDevices.keySet().stream().collect(ImmutableMap.toImmutableMap(
                    Function.identity(),
                    peer -> {
                        return new UnknownConfiguration.Builder()
                                .setTechnology(Technology.Reserved((byte) 4))
                                .setPayload(new byte[]{})
                                .build();
//                        return RttStationOobConfig.builder()
//                                .setBandWidth(entry.getValue().mBandWidth)
//                                .build());
                    }));
        }
    }

    public static void getLazyUpdateRate() {
        RTT_UPDATE_RATE_DURATIONS = ImmutableMap.of(
                UPDATE_RATE_NORMAL, Duration.ofMillis(256),
                UPDATE_RATE_INFREQUENT, Duration.ofMillis(8192),
                UPDATE_RATE_FREQUENT, Duration.ofMillis(128));
    }

    private static class RttStationDeviceConfig {
        String mBssid;
        int mBandWidth;

        RttStationDeviceConfig(String bssid, int bandwidth) {
            mBssid = bssid;
            mBandWidth = bandwidth;
        }
    }

}
