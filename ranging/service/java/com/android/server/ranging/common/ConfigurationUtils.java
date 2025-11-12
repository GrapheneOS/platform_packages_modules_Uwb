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

package com.android.server.ranging.common;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingParams;
import android.util.Range;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;
import com.android.ranging.uwb.backend.internal.RangingTimingParams;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.server.ranging.blerssi.BleRssiConfig;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.session.ConfigurationManager.MulticastTechnologyConfig;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;
import com.android.server.ranging.session.ConfigurationManager.UnicastTechnologyConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class ConfigurationUtils {
    private ConfigurationUtils() {
        throw new IllegalStateException();
    }

    public static @NonNull ImmutableSet<TechnologyConfig> groupByTechnology(
            @NonNull Collection<RawRangingDevice> deviceConfigs,
            @NonNull SessionConfig sessionConfig,
            @RangingPreference.DeviceRole int role
    ) {
        return ImmutableSet.copyOf(Sets.union(
                extractUnicastTechnologies(deviceConfigs, sessionConfig, role),
                extractMulticastTechnologies(deviceConfigs, sessionConfig, role)));
    }

    private static @NonNull Set<MulticastTechnologyConfig> extractMulticastTechnologies(
            @NonNull Collection<RawRangingDevice> peerParams,
            SessionConfig sessionConfig, @RangingPreference.DeviceRole int role
    ) {
        Set<MulticastTechnologyConfig> configs = new HashSet<>();

        Map<PeerIgnoringParamsHasher<UwbRangingParams>, BiMap<RangingDevice, UwbAddress>>
                uwbPeersByParams = PeerIgnoringParamsHasher.groupUwbPeersByParams(peerParams);

        // Create a config for each unique params. When multiple peers share the same params, this
        // config will specify a multicast session containing all of them.
        for (PeerIgnoringParamsHasher<UwbRangingParams> key : uwbPeersByParams.keySet()) {
            configs.add(new UwbConfig.Builder(key.mParams)
                    .setDeviceRole(role)
                    .setPeerAddresses(ImmutableBiMap.copyOf(uwbPeersByParams.get(key)))
                    .setSessionConfig(sessionConfig)
                    .build());
        }

        return configs;
    }

    private static @NonNull Set<UnicastTechnologyConfig> extractUnicastTechnologies(
            @NonNull Collection<RawRangingDevice> peerParams,
            @NonNull SessionConfig sessionConfig, @RangingPreference.DeviceRole int role
    ) {
        Set<UnicastTechnologyConfig> configs = new HashSet<>();

        for (RawRangingDevice peer : peerParams) {
            if (peer.getRangingDevice() == null) continue;

            if (peer.getRttRangingParams() != null) {
                configs.add(new RttConfig(
                        role, peer.getRttRangingParams(), sessionConfig, peer.getRangingDevice()));
            }
            if (peer.getBleRssiRangingParams() != null) {
                configs.add(new BleRssiConfig(
                        role, peer.getBleRssiRangingParams(), sessionConfig,
                        peer.getRangingDevice()));
            }
            // Only CS initiator needs to be configured.
            if (peer.getCsRangingParams() != null && role == DEVICE_ROLE_INITIATOR) {
                configs.add(new CsConfig(
                        peer.getCsRangingParams(), sessionConfig, peer.getRangingDevice()));
            }
            if (Flags.rangingStackUpdates25q4() && peer.getRttStationRangingParams() != null) {
                configs.add(new RttConfig(
                        role, peer.getRttStationRangingParams(), sessionConfig,
                        peer.getRangingDevice()));
            }
        }

        return configs;
    }

    public static Optional<@RawRangingDevice.RangingUpdateRate Integer>
    getUpdateRateFromDurationRange(
            Range<Duration> preferred,
            ImmutableMap<@RawRangingDevice.RangingUpdateRate Integer, Duration> allowed
    ) {
        if (preferred.getLower().compareTo(allowed.get(UPDATE_RATE_INFREQUENT)) > 0) {
            // Range of preferred durations lies entirely above allowed durations
            return Optional.of(UPDATE_RATE_INFREQUENT);
        }
        if (preferred.getUpper().compareTo(allowed.get(UPDATE_RATE_FREQUENT)) < 0) {
            // Range of preferred durations lies entirely below allowed durations
            return Optional.of(UPDATE_RATE_FREQUENT);
        }
        // Otherwise, the intervals overlap. Pick fastest we can.
        if (preferred.contains(allowed.get(UPDATE_RATE_FREQUENT))) {
            return Optional.of(UPDATE_RATE_FREQUENT);
        } else if (preferred.contains(allowed.get(UPDATE_RATE_NORMAL))) {
            return Optional.of(UPDATE_RATE_NORMAL);
        } else if (preferred.contains(allowed.get(UPDATE_RATE_INFREQUENT))) {
            return Optional.of(UPDATE_RATE_INFREQUENT);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Throws {@link IllegalArgumentException} if the ranging interval does not correspond to an
     * update rate.
     */
    public static @RawRangingDevice.RangingUpdateRate int getUpdateRateFromIntervalMs(
            int intervalMs, @Utils.UwbConfigId int configId
    ) {
        RangingTimingParams timings = Utils.getRangingTimingParams(configId);

        if (intervalMs == timings.getRangingIntervalFast()) {
            return UPDATE_RATE_FREQUENT;
        } else if (intervalMs == timings.getRangingIntervalNormal()) {
            return UPDATE_RATE_NORMAL;
        } else if (intervalMs == timings.getRangingIntervalInfrequent()) {
            return UPDATE_RATE_INFREQUENT;
        } else {
            throw new IllegalArgumentException("Unsupported ranging interval ms " + intervalMs);
        }
    }

    private static class PeerIgnoringParamsHasher<P> {
        private final P mParams;

        /**
         * Group together UWB peer devices that share the same params so that they can be put into a
         * multicast session.
         */
        public static Map<
                PeerIgnoringParamsHasher<UwbRangingParams>,
                BiMap<RangingDevice, UwbAddress>
        > groupUwbPeersByParams(@NonNull Collection<RawRangingDevice> peerParams) {
            Map<PeerIgnoringParamsHasher<UwbRangingParams>, BiMap<RangingDevice, UwbAddress>>
                    peersByParams = new HashMap<>();
            for (RawRangingDevice peer : peerParams) {
                if (peer.getUwbRangingParams() == null || peer.getRangingDevice() == null) continue;

                PeerIgnoringParamsHasher<UwbRangingParams> key =
                        new PeerIgnoringParamsHasher<>(peer.getUwbRangingParams());

                if (peersByParams.containsKey(key)) {
                    peersByParams.get(key).put(
                            peer.getRangingDevice(),
                            key.mParams.getPeerAddress());
                } else {
                    peersByParams.put(key, HashBiMap.create(Map.of(
                            peer.getRangingDevice(),
                            key.mParams.getPeerAddress())));
                }
            }
            return peersByParams;
        }

        PeerIgnoringParamsHasher(P params) {
            mParams = params;
        }

        @Override
        public int hashCode() {
            if (mParams instanceof UwbRangingParams params) {
                return Objects.hash(RangingManager.UWB, params.peerIgnoringHashCode());
            } else {
                throw new IllegalArgumentException("Provided params object is not supported");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PeerIgnoringParamsHasher<?> hasher)) return false;

            if (mParams instanceof UwbRangingParams me
                    && hasher.mParams instanceof UwbRangingParams other
            ) return me.peerIgnoringEquals(other);

            return false;
        }
    }
}
