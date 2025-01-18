/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging;

import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_AUTO;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_FUSED;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY_PREFERRED;

import android.ranging.RangingCapabilities;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.uwb.UwbRangingCapabilities;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfigSelector;
import com.android.server.ranging.uwb.UwbConfigSelector.SelectedUwbConfig;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RangingEngine {
    private static final String TAG = RangingEngine.class.getSimpleName();

    private final OobInitiatorRangingConfig mOobConfig;
    private final EnumSet<RangingTechnology> mRequestedTechnologies;
    private final Map<RangingDevice, EnumSet<RangingTechnology>> mPeerTechnologies;

    private final @Nullable UwbConfigSelector mUwbConfigSelector;

    public static class ConfigSelectionException extends Exception {
        public ConfigSelectionException(String message) {
            super(message);
        }
    }

    public static class SelectedConfig {
        private final ImmutableSet<TechnologyConfig> mLocalConfigs;
        private final ImmutableMap<RangingDevice, SetConfigurationMessage> mPeerConfigMessages;

        private SelectedConfig(
                ImmutableSet<TechnologyConfig> localConfigs,
                ImmutableMap<RangingDevice, SetConfigurationMessage> peerConfigMessages
        ) {
            mLocalConfigs = localConfigs;
            mPeerConfigMessages = peerConfigMessages;
        }

        public ImmutableSet<TechnologyConfig> getLocalConfigs() {
            return mLocalConfigs;
        }

        public ImmutableMap<RangingDevice, SetConfigurationMessage> getPeerConfigMessages() {
            return mPeerConfigMessages;
        }
    }

    public RangingEngine(
            SessionConfig sessionConfig, OobInitiatorRangingConfig oobConfig,
            SessionHandle sessionHandle, RangingInjector injector
    ) throws ConfigSelectionException {
        mOobConfig = oobConfig;
        mPeerTechnologies = new HashMap<>();
        mRequestedTechnologies = EnumSet.noneOf(RangingTechnology.class);

        RangingCapabilities localCapabilities = injector.getCapabilitiesProvider()
                .getCapabilities();

        UwbRangingCapabilities uwbCapabilities = localCapabilities.getUwbCapabilities();
        if (UwbConfigSelector.isCapableOfConfig(sessionConfig, oobConfig, uwbCapabilities)) {
            mRequestedTechnologies.add(RangingTechnology.UWB);
            mUwbConfigSelector = new UwbConfigSelector(
                    sessionConfig, oobConfig, uwbCapabilities, sessionHandle);
        } else {
            mUwbConfigSelector = null;
        }

        if (oobConfig.getRangingMode() != RANGING_MODE_HIGH_ACCURACY) {
            // TODO: Other technologies
        }

        if (mRequestedTechnologies.isEmpty()) {
            throw new ConfigSelectionException(
                    "No locally supported technologies are compatible with the provided config");
        }
    }

    public ImmutableSet<RangingTechnology> getRequestedTechnologies() {
        return ImmutableSet.copyOf(mRequestedTechnologies);
    }

    public void addPeerCapabilities(
            RangingDevice device, CapabilityResponseMessage capabilities
    ) throws ConfigSelectionException {

        EnumSet<RangingTechnology> selectedTechnologies =
                selectTechnologiesToUseWithPeer(capabilities);
        UwbOobCapabilities uwbCapabilities = capabilities.getUwbCapabilities();
        for (RangingTechnology technology : selectedTechnologies) {
            // TODO: Other technologies
            if (technology == RangingTechnology.UWB
                    && uwbCapabilities != null
                    && mUwbConfigSelector != null
            ) {
                mUwbConfigSelector.restrictConfigToCapabilities(device, uwbCapabilities);
            } else {
                Log.e(TAG, "Technology " + technology + " was selected by us and peer " + device
                        + ", but one of us does not actually support it");
                throw new IllegalStateException("Unsupported technology " + technology);
            }
        }

        mPeerTechnologies.put(device, selectedTechnologies);
    }

    public SelectedConfig selectConfigs() throws ConfigSelectionException {
        ImmutableSet.Builder<TechnologyConfig> localConfigs = ImmutableSet.builder();
        ImmutableMap.Builder<RangingDevice, SetConfigurationMessage> peerConfigs =
                ImmutableMap.builder();

        Map<RangingDevice, UwbOobConfig> uwbConfigsByPeer = new HashMap<>();
        if (mUwbConfigSelector != null) {
            SelectedUwbConfig uwbConfig = mUwbConfigSelector.selectConfig();
            localConfigs.add(uwbConfig.getLocalConfig());
            uwbConfigsByPeer.putAll(uwbConfig.getPeerConfigs());
        }

        for (RangingDevice peer : mPeerTechnologies.keySet()) {
            SetConfigurationMessage.Builder configMessage = SetConfigurationMessage.builder()
                    .setHeader(OobHeader.builder()
                            .setMessageType(MessageType.SET_CONFIGURATION)
                            .setVersion(OobHeader.OobVersion.CURRENT)
                            .build())
                    .setRangingTechnologiesSet(ImmutableList.copyOf(mRequestedTechnologies))
                    .setStartRangingList(ImmutableList.copyOf(mRequestedTechnologies));

            if (uwbConfigsByPeer.containsKey(peer)) {
                configMessage.setUwbConfig(uwbConfigsByPeer.get(peer));
            }

            peerConfigs.put(peer, configMessage.build());
        }

        return new SelectedConfig(localConfigs.build(), peerConfigs.build());
    }

    private EnumSet<RangingTechnology> selectTechnologiesToUseWithPeer(
            CapabilityResponseMessage peerCapabilities
    ) throws ConfigSelectionException {
        Set<RangingTechnology> technologiesSupportedByPeer =
                Set.copyOf(peerCapabilities.getSupportedRangingTechnologies());

        EnumSet<RangingTechnology> technologies = EnumSet.noneOf(RangingTechnology.class);
        switch (mOobConfig.getRangingMode()) {
            case RANGING_MODE_AUTO:
            case RANGING_MODE_HIGH_ACCURACY_PREFERRED: {
                RangingTechnology.TECHNOLOGIES
                        .stream()
                        .filter(technologiesSupportedByPeer::contains)
                        .findFirst()
                        .ifPresent(technologies::add);
                break;
            }
            case RANGING_MODE_HIGH_ACCURACY: {
                if (technologiesSupportedByPeer.contains(RangingTechnology.UWB)) {
                    technologies.add(RangingTechnology.UWB);
                }
                break;
            }
            case RANGING_MODE_FUSED: {
                technologies.addAll(technologiesSupportedByPeer);
                break;
            }
        }

        if (technologies.isEmpty()) {
            throw new ConfigSelectionException("Peer does not support any selected technologies");
        }
        return technologies;
    }
}