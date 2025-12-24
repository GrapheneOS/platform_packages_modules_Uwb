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

import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobInitiatorRangingConfig;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.oob.packets.Technology;

import com.google.common.collect.ImmutableSet;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public class ConfigurationManager {
    private static final String TAG = ConfigurationManager.class.getSimpleName();

    /** A complete configuration for a session within a specific ranging technology's stack */
    public interface TechnologyConfig {
        @NonNull
        RangingTechnology getTechnology();

        @RangingPreference.DeviceRole int getDeviceRole();

        Duration getRangingInterval();
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

    /**
     * For a particular technology, selects local and remote configs given capabilities from a set
     * of peers.
     */
    public abstract static class ConfigSelector {
        public abstract void addPeerCapabilities(
                @NonNull RangingDevice peer,
                @NonNull Capabilities capabilities,
                @NonNull DeviceType deviceType
        ) throws ConfigSelectionException;

        public abstract @NonNull Set<TechnologyConfig> selectLocalConfigs(
                @NonNull Set<RangingDevice> peers
        ) throws ConfigSelectionException;

        public abstract @NonNull Configuration selectRemoteConfig(@NonNull RangingDevice peer)
                throws ConfigSelectionException;
    }

    /** Indicates that something went wrong when attempting to select a config. */
    public static class ConfigSelectionException extends Exception {
        private final @RangingUtils.InternalReason int mReason;

        public ConfigSelectionException(String message, @InternalReason int reason) {
            super(message);
            mReason = reason;
        }

        public @InternalReason int getReason() {
            return mReason;
        }
    }

    private final Map<RangingTechnology, ConfigSelector> mConfigSelectors;
    private final SessionHandle mSessionHandle;
    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final RangingInjector mInjector;

    public ConfigurationManager(
            @NonNull SessionHandle sessionHandle,
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @NonNull RangingInjector injector
    ) {
        mConfigSelectors = new EnumMap<>(RangingTechnology.class);
        mSessionHandle = sessionHandle;
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mInjector = injector;
    }

    public void addPeerCapabilities(
            RangingDevice device,  Map<Technology, Capabilities> capabilities,
            DeviceType deviceType
    ) throws ConfigSelectionException {
        if (capabilities.isEmpty()) {
            throw new ConfigSelectionException(
                    "Failed to find any technologies to use with " + device,
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        for (Technology t : capabilities.keySet()) {
            mConfigSelectors
                    .computeIfAbsent(
                            RangingTechnology.fromByte(t.toByte()),
                            technology -> mInjector.createConfigSelector(
                                    technology, mSessionHandle, mSessionConfig, mOobConfig))
                    .addPeerCapabilities(device, capabilities.get(t), deviceType);
        }
    }

    ImmutableSet<TechnologyConfig> getLocalConfigs(
            Map<RangingTechnology, Set<RangingDevice>> peersByTechnology
    ) throws ConfigSelectionException {
        ImmutableSet.Builder<TechnologyConfig> configs = new ImmutableSet.Builder<>();
        for (RangingTechnology technology : peersByTechnology.keySet()) {
            if (mConfigSelectors.containsKey(technology)) {
                configs.addAll(mConfigSelectors.get(technology)
                        .selectLocalConfigs(peersByTechnology.get(technology)));
            }
        }
        return configs.build();
    }

    ImmutableSet<Configuration> getRemoteConfigs(
            RangingDevice peer, Set<RangingTechnology> technologies
    ) throws ConfigSelectionException {
        ImmutableSet.Builder<Configuration> configs = new ImmutableSet.Builder<>();
        for (RangingTechnology technology : technologies) {
            if (mConfigSelectors.containsKey(technology)) {
                configs.add(mConfigSelectors.get(technology).selectRemoteConfig(peer));
            }
        }
        return configs.build();
    }
}
