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

package com.android.server.ranging.session;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;

import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingParams;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.blerssi.BleRssiConfig;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class RangingSessionConfig {
    private final @RangingPreference.DeviceRole int mDeviceRole;
    private final SessionConfig mSessionConfig;

    /** A complete configuration for a session within a specific ranging technology's stack */
    public interface TechnologyConfig {
        @NonNull
        RangingTechnology getTechnology();

        @RangingPreference.DeviceRole int getDeviceRole();
    }

    /** A config for a technology that only supports 1 peer per session. */
    public interface UnicastTechnologyConfig extends TechnologyConfig {
        @NonNull
        RangingDevice getPeerDevice();
    }

    /** A config for a technology that supports multiple peers per session. */
    public interface MulticastTechnologyConfig extends TechnologyConfig {
        /**
         * @return the set of peers within this technology-specific session.
         */
        @NonNull
        ImmutableSet<RangingDevice> getPeerDevices();
    }

    private RangingSessionConfig(Builder builder) {
        mDeviceRole = builder.mDeviceRole;
        mSessionConfig = builder.mSessionConfig;
    }

    public @NonNull ImmutableSet<TechnologyConfig> getTechnologyConfigs(
            @NonNull Set<RawRangingDevice> peerParams
    ) {
        return ImmutableSet.copyOf(Sets.union(
                getConfigsForUnicastTechnologies(peerParams),
                getConfigsForMulticastTechnologies(peerParams)));
    }

    private @NonNull Set<MulticastTechnologyConfig> getConfigsForMulticastTechnologies(
            @NonNull Set<RawRangingDevice> peerParams
    ) {
        Set<MulticastTechnologyConfig> configs = new HashSet<>();

        Map<PeerIgnoringParamsHasher<UwbRangingParams>, BiMap<RangingDevice, UwbAddress>>
                uwbPeersByParams = PeerIgnoringParamsHasher.groupUwbPeersByParams(peerParams);

        // Create a config for each unique params. When multiple peers share the same params, this
        // config will specify a multicast session containing all of them.
        for (PeerIgnoringParamsHasher<UwbRangingParams> key : uwbPeersByParams.keySet()) {
            configs.add(new UwbConfig.Builder(key.mParams)
                    .setDeviceRole(mDeviceRole)
                    .setPeerAddresses(ImmutableBiMap.copyOf(uwbPeersByParams.get(key)))
                    // TODO(370077264): Set country code based on geolocation.
                    .setCountryCode("US")
                    .setSessionConfig(mSessionConfig)
                    .build());
        }

        return configs;
    }

    private @NonNull Set<UnicastTechnologyConfig> getConfigsForUnicastTechnologies(
            @NonNull Set<RawRangingDevice> peerParams
    ) {
        Set<UnicastTechnologyConfig> configs = new HashSet<>();

        for (RawRangingDevice peer : peerParams) {
            if (peer.getRttRangingParams() != null) {
                configs.add(new RttConfig(
                        mDeviceRole,
                        peer.getRttRangingParams(),
                        mSessionConfig,
                        peer.getRangingDevice()));
            }
            if (peer.getBleRssiRangingParams() != null) {
                configs.add(new BleRssiConfig(
                        mDeviceRole,
                        peer.getBleRssiRangingParams(),
                        mSessionConfig,
                        peer.getRangingDevice()));
            }
            // Only CS initiator needs to be configured.
            if (peer.getCsRangingParams() != null && mDeviceRole == DEVICE_ROLE_INITIATOR) {
                configs.add(new CsConfig(
                        peer.getCsRangingParams(),
                        mSessionConfig,
                        peer.getRangingDevice()));
            }
        }

        return configs;
    }

    public @RangingPreference.DeviceRole int getDeviceRole() {
        return mDeviceRole;
    }

    public SessionConfig getSessionConfig() {
        return mSessionConfig;
    }

    public static class Builder {
        private @RangingPreference.DeviceRole int mDeviceRole;
        private SessionConfig mSessionConfig;

        public RangingSessionConfig build() {
            return new RangingSessionConfig(this);
        }

        public Builder setDeviceRole(@RangingPreference.DeviceRole int role) {
            mDeviceRole = role;
            return this;
        }

        public Builder setSessionConfig(@NonNull SessionConfig config) {
            mSessionConfig = config;
            return this;
        }

    }

    private static class PeerIgnoringParamsHasher<P> {
        private final P mParams;

        /**
         * Group together UWB peer devices that share the same params so that they can be put into a
         * a multicast session.
         */
        public static Map<PeerIgnoringParamsHasher<UwbRangingParams>, BiMap<RangingDevice,
                UwbAddress>>
        groupUwbPeersByParams(@NonNull Set<RawRangingDevice> peerParams) {
            Map<PeerIgnoringParamsHasher<UwbRangingParams>, BiMap<RangingDevice, UwbAddress>>
                    peersByParams = new HashMap<>();
            for (RawRangingDevice peer : peerParams) {
                if (peer.getUwbRangingParams() == null) continue;

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
                return Objects.hash(
                        RangingManager.UWB,
                        params.getSessionId(),
                        params.getSubSessionId(),
                        params.getConfigId(),
                        params.getDeviceAddress(),
                        Arrays.hashCode(params.getSessionKeyInfo()),
                        Arrays.hashCode(params.getSubSessionKeyInfo()),
                        params.getComplexChannel(),
                        params.getRangingUpdateRate(),
                        params.getSlotDuration());
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
            ) {
                return me.getSessionId() == other.getSessionId()
                        && me.getSubSessionId() == other.getSubSessionId()
                        && me.getConfigId() == other.getConfigId()
                        && me.getDeviceAddress().equals(other.getDeviceAddress())
                        && Arrays.equals(me.getSessionKeyInfo(), other.getSessionKeyInfo())
                        && Arrays.equals(me.getSubSessionKeyInfo(), other.getSubSessionKeyInfo())
                        && me.getComplexChannel().equals(other.getComplexChannel())
                        && me.getRangingUpdateRate() == other.getRangingUpdateRate()
                        && me.getSlotDuration() == other.getSlotDuration();
            }

            return false;
        }
    }

    @Override
    public String toString() {
        return "RangingSessionConfig{"
                + "mDeviceRole="
                + mDeviceRole
                + ", mSessionConfig="
                + mSessionConfig
                + '}';
    }
}
