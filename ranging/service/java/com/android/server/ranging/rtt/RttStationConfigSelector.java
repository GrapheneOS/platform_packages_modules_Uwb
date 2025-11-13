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

import android.annotation.FlaggedApi;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.wifi.rtt.RttStationRangingCapabilities;
import android.ranging.wifi.rtt.RttStationRangingParams;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.flags.Flags;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.oob.packets.UnknownConfiguration;
import com.android.server.ranging.session.ConfigurationManager;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_25Q4)
public class RttStationConfigSelector extends ConfigurationManager.ConfigSelector {
    private static final String TAG = RttStationConfigSelector.class.getSimpleName();

    public static int RTT_SUFFIX_SIZE = 6;
    private static int sSupportedBands = 0;
    private static final String BSSID = "AA:BB:CC:AA:BB:CC";
    private static final int BANDWIDTH = 0; //no preference
    private final SessionConfig mSessionConfig;

    private final OobInitiatorRangingConfig mOobConfig;

    private @Nullable SelectedRttStationConfig mSelectedConfig = null;

    private final Map<RangingDevice, RttStationDeviceConfig> mRangingDevices =
            new ConcurrentHashMap<>();
    public static ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration>
            RTT_UPDATE_RATE_DURATIONS;

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttStationRangingCapabilities capabilities) {

        if (capabilities == null) {
            Log.v(TAG, "Not capable of RTT station");
            return false;
        }
        sSupportedBands = capabilities.getNumSupportedBands();
        if (RTT_UPDATE_RATE_DURATIONS == null) {
            getLazyUpdateRate();
        }

        if (getUpdateRateFromDurationRange(
                oobConfig.getRangingIntervalRange(), RTT_UPDATE_RATE_DURATIONS).isEmpty()
        ) {
            Log.v(TAG, "Not capable of configured ranging interval");
            return false;
        }

        return true;
    }

    public RttStationConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable RttStationRangingCapabilities capabilities
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
    }

    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull Capabilities baseCapabilities
    ) throws ConfigSelectionException {
        //do nothing
    }

    @Override
    public @NonNull Set<TechnologyConfig> selectLocalConfigs(
            @NonNull Set<RangingDevice> peers
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedRttStationConfig();
        return mSelectedConfig.getLocalConfigs(peers);
    }

    @Override
    public @NonNull Configuration selectRemoteConfig(
            @NonNull RangingDevice peer
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedRttStationConfig();
        return mSelectedConfig.getPeerConfig(peer);
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
        public ImmutableSet<TechnologyConfig> getLocalConfigs(Set<RangingDevice> peers) {
            return peers.stream()
                    .map((peer -> new RttConfig(DEVICE_ROLE_INITIATOR,
                            new RttStationRangingParams.Builder(mRangingDevices.get(peer).mBssid)
                                    .build(),
                            mSessionConfig,
                            peer)))
                    .collect(ImmutableSet.toImmutableSet());
        }

        @NonNull
        public Configuration getPeerConfig(RangingDevice unused) {
            // TODO
            return new UnknownConfiguration.Builder()
                    .setTechnology(Technology.Reserved((byte) 4))
                    .setPayload(new byte[]{})
                    .build();
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
