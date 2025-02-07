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
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.uwb.UwbRangingCapabilities;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.server.ranging.blerssi.BleRssiConfigSelector;
import com.android.server.ranging.blerssi.BleRssiOobCapabilities;
import com.android.server.ranging.blerssi.BleRssiOobConfig;
import com.android.server.ranging.cs.CsConfigSelector;
import com.android.server.ranging.cs.CsConfigSelector.SelectedCsConfig;
import com.android.server.ranging.cs.CsOobCapabilities;
import com.android.server.ranging.cs.CsOobConfig;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.rtt.RttConfigSelector;
import com.android.server.ranging.rtt.RttOobCapabilities;
import com.android.server.ranging.rtt.RttOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfigSelector;
import com.android.server.ranging.uwb.UwbConfigSelector.SelectedUwbConfig;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RangingEngine {
    private static final String TAG = RangingEngine.class.getSimpleName();

    private final OobInitiatorRangingConfig mOobConfig;
    private final EnumSet<RangingTechnology> mRequestedTechnologies;
    private final Map<RangingDevice, EnumSet<RangingTechnology>> mPeerTechnologies;
    private final DeviceConfigFacade mDeviceConfigFacade;

    private @Nullable UwbConfigSelector mUwbConfigSelector = null;
    private @Nullable CsConfigSelector mCsConfigSelector = null;
    private @Nullable RttConfigSelector mRttConfigSelector = null;
    private @Nullable BleRssiConfigSelector mBleRssiConfigSelector = null;

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
        mDeviceConfigFacade = injector.getDeviceConfigFacade();

        RangingCapabilities localCapabilities = injector.getCapabilitiesProvider()
                .getCapabilities();

        UwbRangingCapabilities uwbCapabilities = localCapabilities.getUwbCapabilities();
        if (UwbConfigSelector.isCapableOfConfig(sessionConfig, oobConfig, uwbCapabilities)) {
            mRequestedTechnologies.add(RangingTechnology.UWB);
            mUwbConfigSelector = new UwbConfigSelector(
                    sessionConfig, oobConfig, uwbCapabilities, sessionHandle);
        }

        if (oobConfig.getRangingMode() != RANGING_MODE_HIGH_ACCURACY) {
            BleCsRangingCapabilities csCapabilities = localCapabilities.getCsCapabilities();
            if (CsConfigSelector.isCapableOfConfig(oobConfig, csCapabilities)) {
                mRequestedTechnologies.add(RangingTechnology.CS);
                mCsConfigSelector = new CsConfigSelector(sessionConfig, oobConfig);
            }
            if (RttConfigSelector.isCapableOfConfig(oobConfig,
                    localCapabilities.getRttRangingCapabilities())) {
                mRequestedTechnologies.add(RangingTechnology.RTT);
                mRttConfigSelector = new RttConfigSelector(sessionConfig, oobConfig);
            }
            BleRssiRangingCapabilities bleRssiCapabilities =
                    localCapabilities.getBleRssiCapabilities();
            if (BleRssiConfigSelector.isCapableOfConfig(oobConfig, bleRssiCapabilities)) {
                mRequestedTechnologies.add(RangingTechnology.RSSI);
                mBleRssiConfigSelector = new BleRssiConfigSelector(
                        sessionConfig, oobConfig, bleRssiCapabilities);
            }
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
        Log.v(TAG, "Selected technologies " + selectedTechnologies + " for peer " + device);

        UwbOobCapabilities uwbCapabilities = capabilities.getUwbCapabilities();
        CsOobCapabilities csCapabilities = capabilities.getCsCapabilities();
        RttOobCapabilities rttCapabilities = capabilities.getRttCapabilities();
        BleRssiOobCapabilities bleRssiCapabilities = capabilities.getBleRssiCapabilities();

        for (RangingTechnology technology : selectedTechnologies) {
            if (technology == RangingTechnology.UWB
                    && uwbCapabilities != null
                    && mUwbConfigSelector != null
            ) {
                mUwbConfigSelector.restrictConfigToCapabilities(device, uwbCapabilities);
            } else if (technology == RangingTechnology.CS
                    && csCapabilities != null
                    && mCsConfigSelector != null
            ) {
                mCsConfigSelector.restrictConfigToCapabilities(device, csCapabilities);
            } else if (technology == RangingTechnology.RTT && rttCapabilities != null
                    && mRttConfigSelector != null) {
                mRttConfigSelector.restrictConfigToCapabilities(device, rttCapabilities);
            } else if (technology == RangingTechnology.RSSI
                    && bleRssiCapabilities != null
                    && mBleRssiConfigSelector != null
            ) {
                mBleRssiConfigSelector.restrictConfigToCapabilities(device, bleRssiCapabilities);
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
        ImmutableMap.Builder<RangingDevice, SetConfigurationMessage> configMessages =
                ImmutableMap.builder();

        Map<RangingDevice, UwbOobConfig> uwbConfigsByPeer = new HashMap<>();
        if (mUwbConfigSelector != null && mUwbConfigSelector.hasPeersToConfigure()) {
            SelectedUwbConfig uwbConfig = mUwbConfigSelector.selectConfig();
            localConfigs.addAll(uwbConfig.getLocalConfigs());
            uwbConfigsByPeer.putAll(uwbConfig.getPeerConfigs());
        }

        Map<RangingDevice, CsOobConfig> csConfigsByPeer = new HashMap<>();
        if (mCsConfigSelector != null && mCsConfigSelector.hasPeersToConfigure()) {
            SelectedCsConfig csConfig = mCsConfigSelector.selectConfig();
            localConfigs.addAll(csConfig.getLocalConfigs());
            csConfigsByPeer.putAll(csConfig.getPeerConfigs());
        }

        Map<RangingDevice, RttOobConfig> rttConfigByPeer = new HashMap<>();
        if (mRttConfigSelector != null && mRttConfigSelector.hasPeersToConfigure()) {
            RttConfigSelector.SelectedRttConfig rttConfig = mRttConfigSelector.selectConfig();
            localConfigs.addAll(rttConfig.getLocalConfigs());
            rttConfigByPeer.putAll(rttConfig.getPeerConfigs());
        }

        Map<RangingDevice, BleRssiOobConfig> bleRssiConfigsByPeer = new HashMap<>();
        if (mBleRssiConfigSelector != null && mBleRssiConfigSelector.hasPeersToConfigure()) {
            BleRssiConfigSelector.SelectedBleRssiConfig bleRssiConfig =
                    mBleRssiConfigSelector.selectConfig();
            localConfigs.addAll(bleRssiConfig.getLocalConfigs());
            bleRssiConfigsByPeer.putAll(bleRssiConfig.getPeerConfigs());
        }

        for (RangingDevice peer : mPeerTechnologies.keySet()) {
            ImmutableList<RangingTechnology> peerTechnologies =
                    ImmutableList.copyOf(mPeerTechnologies.get(peer));

            SetConfigurationMessage.Builder configMessage = SetConfigurationMessage.builder()
                    .setHeader(OobHeader.builder()
                            .setMessageType(MessageType.SET_CONFIGURATION)
                            .setVersion(OobHeader.OobVersion.CURRENT)
                            .build())
                    .setRangingTechnologiesSet(peerTechnologies)
                    .setStartRangingList(peerTechnologies);

            configMessage.setUwbConfig(uwbConfigsByPeer.get(peer));
            configMessage.setCsConfig(csConfigsByPeer.get(peer));
            configMessage.setRttConfig(rttConfigByPeer.get(peer));
            configMessage.setBleRssiConfig(bleRssiConfigsByPeer.get(peer));

            configMessages.put(peer, configMessage.build());
        }

        return new SelectedConfig(localConfigs.build(), configMessages.build());
    }

    private List<RangingTechnology> getPreferredTechnologyList() {
        String[] prefTechnologiesStringArray = mDeviceConfigFacade.getTechnologyPreferenceList();
        return Arrays.stream(prefTechnologiesStringArray)
                .map(str -> RangingTechnology.fromName(str))
                .collect(Collectors.toUnmodifiableList());
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
                getPreferredTechnologyList()
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