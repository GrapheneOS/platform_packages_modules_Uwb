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

import static com.android.server.ranging.common.ConfigurationUtils.getUpdateRateFromDurationRange;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.WifiDeviceRole;
import com.android.server.ranging.oob.packets.WifiNanRttCapabilitiesV1;
import com.android.server.ranging.oob.packets.WifiNanRttCapabilitiesV2;
import com.android.server.ranging.oob.packets.WifiNanRttConfigurationV1;
import com.android.server.ranging.oob.packets.WifiNanRttConfigurationV2;
import com.android.server.ranging.session.ConfigurationManager;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RttConfigSelector extends ConfigurationManager.ConfigSelector {

    public static int RTT_SUFFIX_SIZE = 6;
    private static boolean sLocalPeriodicRangingSupport = false;
    private static final String SERVICE_NAME_PREFIX = "rtt_ranging";

    private final SessionConfig mSessionConfig;

    private final OobInitiatorRangingConfig mOobConfig;

    private final Map<RangingDevice, RttDeviceConfig> mRangingDevices = new ConcurrentHashMap<>();
    public static ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration>
            RTT_UPDATE_RATE_DURATIONS;

    private @Nullable SelectedRttConfig mSelectedConfig = null;

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttRangingCapabilities capabilities) {

        if (capabilities == null) return false;

        sLocalPeriodicRangingSupport = capabilities.hasPeriodicRangingHardwareFeature();
        if (RTT_UPDATE_RATE_DURATIONS == null) {
            getLazyUpdateRate();
        }

        if (getUpdateRateFromDurationRange(
                oobConfig.getRangingIntervalRange(), RTT_UPDATE_RATE_DURATIONS).isEmpty()
        ) return false;

        return true;
    }

    public RttConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttRangingCapabilities capabilities
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
    }


    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull Capabilities baseCapabilities
    ) throws ConfigSelectionException {
        switch (baseCapabilities) {
            case WifiNanRttCapabilitiesV1 capabilities -> mRangingDevices.put(
                    peer,
                    new RttDeviceConfig(getServiceName(peer),
                            sLocalPeriodicRangingSupport && capabilities.getPeriodic(),
                            1));
            // TODO: Correctly handle V2
            case WifiNanRttCapabilitiesV2 capabilities -> mRangingDevices.put(
                    peer,
                    new RttDeviceConfig(getServiceName(peer),
                            sLocalPeriodicRangingSupport && capabilities.getPeriodic(),
                            2));
            default -> throw new ConfigSelectionException(
                    "Peer " + peer + " expected Wifi RTT capabilities but got " + baseCapabilities,
                    InternalReason.PEER_CAPABILITIES_MISMATCH);

        };
    }

    @Override
    public @NonNull Set<TechnologyConfig> selectLocalConfigs(
            @NonNull Set<RangingDevice> peers
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedRttConfig();
        return mSelectedConfig.getLocalConfigs(peers);
    }

    @Override
    public @NonNull Configuration selectRemoteConfig(
            @NonNull RangingDevice peer
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedRttConfig();
        return mSelectedConfig.getPeerConfig(peer);
    }

    private class SelectedRttConfig {
        // TODO: Check whether this needs to be added to OOB.
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;

        SelectedRttConfig() throws ConfigSelectionException {
            mRangingUpdateRate = getUpdateRateFromDurationRange(
                    mOobConfig.getRangingIntervalRange(), RTT_UPDATE_RATE_DURATIONS)
                    .orElseThrow(() -> new ConfigSelectionException(
                            "Configured ranging interval range is incompatible with Wifi RTT",
                            InternalReason.UNSUPPORTED));
        }

        @NonNull
        public ImmutableSet<TechnologyConfig> getLocalConfigs(Set<RangingDevice> peers) {
            return peers.stream().map(peer -> {
                RttDeviceConfig config = mRangingDevices.get(peer);
                return new RttConfig(
                        DEVICE_ROLE_INITIATOR,
                        new RttRangingParams.Builder(config.mServiceName)
                                .setPeriodicRangingHwFeatureEnabled(
                                        config.mUsePeriodicRangingFeature)
                                .build(),
                        mSessionConfig,
                        peer);
            }).collect(ImmutableSet.toImmutableSet());
        }

        @NonNull
        public Configuration getPeerConfig(RangingDevice peer) {
            RttDeviceConfig config = mRangingDevices.get(peer);
            if (config.mOobVersion == 1) {
                return new WifiNanRttConfigurationV1.Builder()
                        .setDeviceRole(WifiDeviceRole.Responder)
                        .setServiceName(
                                config.mServiceName.getBytes(StandardCharsets.UTF_8))
                        .setPeriodic(config.mUsePeriodicRangingFeature)
                        .build();
            } else {
                // TODO: Correctly handle V2
                return new WifiNanRttConfigurationV2.Builder()
                        .setDeviceRole(WifiDeviceRole.Responder)
                        .setServiceName(
                                config.mServiceName.getBytes(StandardCharsets.UTF_8))
                        .setPeriodic(config.mUsePeriodicRangingFeature)
                        .build();
            }
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
        int mOobVersion;

        RttDeviceConfig(String serviceName, boolean usePeriodicRangingFeature, int oobVersion) {
            mServiceName = serviceName;
            mUsePeriodicRangingFeature = usePeriodicRangingFeature;
            mOobVersion = oobVersion;
        }
    }

}
