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
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingParams;
import android.util.Range;

import com.android.server.ranging.RangingEngine;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RttConfigSelector {

    public static int RTT_SUFFIX_SIZE = 6;
    private static boolean sLocalPeriodicRangingSupport = false;
    private static final String SERVICE_NAME_PREFIX = "rtt_ranging";

    private final SessionConfig mSessionConfig;

    private final OobInitiatorRangingConfig mOobConfig;

    private final Map<RangingDevice, RttDeviceConfig> mRangingDevices = new ConcurrentHashMap<>();
    public static ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration>
            RTT_UPDATE_RATE_DURATIONS;

    public RttConfigSelector(SessionConfig sessionConfig, OobInitiatorRangingConfig oobConfig) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
    }

    public boolean hasPeersToConfigure() {
        return !mRangingDevices.isEmpty();
    }

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttRangingCapabilities capabilities) {

        if (capabilities == null) return false;

        sLocalPeriodicRangingSupport = capabilities.hasPeriodicRangingHardwareFeature();
        if (RTT_UPDATE_RATE_DURATIONS == null) {
            getLazyUpdateRate();
        }

        if (getDurationFromUpdateRate(oobConfig.getRangingIntervalRange()).isEmpty()) return false;

        return true;
    }

    public void restrictConfigToCapabilities(@androidx.annotation.NonNull RangingDevice peer,
            @androidx.annotation.NonNull RttOobCapabilities capabilities) {
        mRangingDevices.put(peer, new RttDeviceConfig(getServiceName(peer),
                capabilities.hasPeriodicRangingSupport() && sLocalPeriodicRangingSupport));
    }

    public @NonNull SelectedRttConfig selectConfig() throws RangingEngine.ConfigSelectionException {
        return new SelectedRttConfig();
    }

    public class SelectedRttConfig {
        // TODO: Check whether this needs to be added to OOB.
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;

        SelectedRttConfig() throws RangingEngine.ConfigSelectionException {
            mRangingUpdateRate = getDurationFromUpdateRate(
                    mOobConfig.getRangingIntervalRange()).orElseThrow(
                        () -> new RangingEngine.ConfigSelectionException(
                            "Configured ranging interval range is incompatible with Wifi RTT"));
        }

        @NonNull
        public ImmutableSet<RttConfig> getLocalConfigs() {
            return mRangingDevices.entrySet().stream()
                    .map((entry -> new RttConfig(DEVICE_ROLE_INITIATOR,
                            new RttRangingParams.Builder(entry.getValue().mServiceName)
                                    .setPeriodicRangingHwFeatureEnabled(
                                            entry.getValue().mUsePeriodicRangingFeature)
                                    .build(),
                            mSessionConfig,
                            entry.getKey())))
                    .collect(ImmutableSet.toImmutableSet());
        }

        @NonNull
        public ImmutableMap<RangingDevice, RttOobConfig> getPeerConfigs() {
            return mRangingDevices.entrySet().stream()
                    .collect(ImmutableMap.toImmutableMap(
                            Map.Entry::getKey,
                            entry -> RttOobConfig.builder()
                                    .setDeviceRole(DEVICE_ROLE_RESPONDER)
                                    .setServiceName(entry.getValue().mServiceName)
                                    .setUsePeriodicRanging(
                                            entry.getValue().mUsePeriodicRangingFeature)
                                    .build()));
        }
    }

    private static Optional<@RawRangingDevice.RangingUpdateRate Integer> getDurationFromUpdateRate(
            Range<Duration> configuredRates
    ) {
        if (configuredRates.contains(RTT_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_FREQUENT))) {
            return Optional.of(UPDATE_RATE_FREQUENT);
        } else if (configuredRates.contains(RTT_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_NORMAL))) {
            return Optional.of(UPDATE_RATE_NORMAL);
        } else if (configuredRates.contains(
                RTT_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_INFREQUENT))) {
            return Optional.of(UPDATE_RATE_INFREQUENT);
        } else {
            return Optional.empty();
        }
    }

    public static void getLazyUpdateRate() {
        if (sLocalPeriodicRangingSupport) {
            RTT_UPDATE_RATE_DURATIONS = ImmutableMap.of(
                    UPDATE_RATE_NORMAL, Duration.ofMillis(256),
                    UPDATE_RATE_INFREQUENT, Duration.ofMillis(8192),
                    UPDATE_RATE_FREQUENT, Duration.ofMillis(128));
        } else {
            RTT_UPDATE_RATE_DURATIONS = ImmutableMap.of(
                    UPDATE_RATE_NORMAL, Duration.ofMillis(512),
                    UPDATE_RATE_INFREQUENT, Duration.ofMillis(8192),
                    UPDATE_RATE_FREQUENT, Duration.ofMillis(256));
        }
    }

    private static String getServiceName(RangingDevice device) {
        return SERVICE_NAME_PREFIX + device.getUuid().toString().replace("-",
                "").substring(0, RTT_SUFFIX_SIZE);
    }

    private static class RttDeviceConfig {
        String mServiceName;
        boolean mUsePeriodicRangingFeature;

        RttDeviceConfig(String serviceName, boolean usePeriodicRangingFeature) {
            mServiceName = serviceName;
            mUsePeriodicRangingFeature = usePeriodicRangingFeature;
        }
    }

}
