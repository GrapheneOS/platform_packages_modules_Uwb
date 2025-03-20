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
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.blerssi.BleRssiConfigSelector;
import com.android.server.ranging.cs.CsConfigSelector;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.SetConfigurationMessage.TechnologyOobConfig;
import com.android.server.ranging.rtt.RttConfigSelector;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfigSelector;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RangingEngine {
    private static final String TAG = RangingEngine.class.getSimpleName();

    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final SessionHandle mSessionHandle;
    private final ImmutableSet<RangingTechnology> mRequestedTechnologies;
    private final Map<RangingDevice, EnumSet<RangingTechnology>> mPeerTechnologies;
    private final RangingInjector mInjector;

    private final EnumMap<RangingTechnology, ConfigSelector> mConfigSelectors;

    public static class ConfigSelectionException extends Exception {
        private final @InternalReason int mReason;

        public ConfigSelectionException(String message, @InternalReason int reason) {
            super(message);
            mReason = reason;
        }

        public @InternalReason int getReason() {
            return mReason;
        }
    }

    public interface ConfigSelector {
        void addPeerCapabilities(@NonNull RangingDevice peer,
                @NonNull CapabilityResponseMessage response) throws ConfigSelectionException;

        boolean hasPeersToConfigure();

        @NonNull Pair<
                ImmutableSet<TechnologyConfig>,
                ImmutableMap<RangingDevice, TechnologyOobConfig>
        > selectConfigs() throws ConfigSelectionException;
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
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mSessionHandle = sessionHandle;
        mPeerTechnologies = new HashMap<>();
        mConfigSelectors = Maps.newEnumMap(RangingTechnology.class);
        mInjector = injector;

        ImmutableSet.Builder<RangingTechnology> toRequest = ImmutableSet.builder();
        if (shouldRequest(RangingTechnology.UWB)) toRequest.add(RangingTechnology.UWB);
        if (oobConfig.getRangingMode() != RANGING_MODE_HIGH_ACCURACY) {
            for (RangingTechnology technology :
                    Set.of(RangingTechnology.CS, RangingTechnology.RTT, RangingTechnology.RSSI)) {
                if (shouldRequest(technology)) toRequest.add(technology);
            }
        }
        mRequestedTechnologies = toRequest.build();
        if (mRequestedTechnologies.isEmpty()) {
            throw new ConfigSelectionException(
                    "No locally supported technologies are compatible with the provided config",
                    InternalReason.UNSUPPORTED);
        }
    }

    public ImmutableSet<RangingTechnology> getRequestedTechnologies() {
        return mRequestedTechnologies;
    }

    public void addPeerCapabilities(
            RangingDevice device, CapabilityResponseMessage capabilities
    ) throws ConfigSelectionException {
        EnumSet<RangingTechnology> selectedTechnologies =
                selectTechnologiesToUseWithPeer(capabilities);
        Log.v(TAG, "Selected technologies " + selectedTechnologies + " for peer " + device);

        for (RangingTechnology technology : selectedTechnologies) {
            ConfigSelector selector = mConfigSelectors.get(technology);
            if (selector == null) {
                throw new IllegalStateException(
                        "Expected config selector to exist for mutually supported technology "
                                + technology);
            }
            selector.addPeerCapabilities(device, capabilities);
        }
        mPeerTechnologies.put(device, selectedTechnologies);
    }

    public SelectedConfig selectConfigs() throws ConfigSelectionException {
        ImmutableSet.Builder<TechnologyConfig> localConfigs = ImmutableSet.builder();
        Map<RangingDevice, SetConfigurationMessage.Builder> peerConfigs = new HashMap<>();

        for (RangingTechnology technology : mConfigSelectors.keySet()) {
            ConfigSelector selector = mConfigSelectors.get(technology);
            if (!selector.hasPeersToConfigure()) continue;

            Pair<ImmutableSet<TechnologyConfig>, ImmutableMap<RangingDevice, TechnologyOobConfig>>
                    configs = selector.selectConfigs();

            localConfigs.addAll(configs.first);
            configs.second.forEach((peer, config) ->
                    peerConfigs.computeIfAbsent(peer,
                            (unused) -> {
                                ImmutableList<RangingTechnology> peerTechnologies =
                                        ImmutableList.copyOf(mPeerTechnologies.get(peer));
                                return SetConfigurationMessage.builder()
                                        .setHeader(OobHeader.builder()
                                                .setMessageType(MessageType.SET_CONFIGURATION)
                                                .setVersion(OobHeader.OobVersion.CURRENT)
                                                .build())
                                        .setRangingTechnologiesSet(peerTechnologies)
                                        .setStartRangingList(peerTechnologies);
                            }
                    )
                    .setTechnologyConfig(configs.second.get(peer)));
        }

        return new SelectedConfig(
                localConfigs.build(),
                peerConfigs.keySet().stream().collect(
                        ImmutableMap.toImmutableMap(
                                Function.identity(),
                                (peer) -> peerConfigs.get(peer).build())));
    }

    private boolean shouldRequest(RangingTechnology technology) {
        @RangingCapabilities.RangingTechnologyAvailability int availability = mInjector
                .getCapabilitiesProvider()
                .getCapabilities()
                .getTechnologyAvailability().get(technology.getValue());

        return availability == RangingCapabilities.ENABLED
                || availability == RangingCapabilities.DISABLED_USER;
    }

    /**
     * If the {@param technology} is enabled and supported, create a config selector and add it to
     * {@code mConfigSelectors}
     * @return true if the {@param technology} is enabled and supported, false otherwise.
     */
    private boolean createConfigSelectorIfEnabledAndSupported(RangingTechnology technology) {
        if (mConfigSelectors.containsKey(technology)) return true;
        try {
            mConfigSelectors.put(technology, createConfigSelector(technology));
            return true;
        } catch (ConfigSelectionException ignored) {
            return false;
        }
    }

    private ConfigSelector createConfigSelector(
            RangingTechnology technology
    ) throws ConfigSelectionException {
        RangingCapabilities capabilities = mInjector.getCapabilitiesProvider().getCapabilities();
        return switch (technology) {
            case UWB -> new UwbConfigSelector(
                    mSessionConfig, mOobConfig, mSessionHandle, capabilities.getUwbCapabilities());
            case CS -> new CsConfigSelector(
                    mSessionConfig, mOobConfig, capabilities.getCsCapabilities());
            case RTT -> new RttConfigSelector(
                    mSessionConfig, mOobConfig, capabilities.getRttRangingCapabilities());
            case RSSI -> new BleRssiConfigSelector(
                    mSessionConfig, mOobConfig, capabilities.getBleRssiCapabilities());
        };
    }

    private List<RangingTechnology> getPreferredTechnologyList() {
        String[] prefTechnologiesStringArray = mInjector
                .getDeviceConfigFacade()
                .getTechnologyPreferenceList();
        return Arrays.stream(prefTechnologiesStringArray)
                .map(str -> RangingTechnology.fromName(str))
                .collect(Collectors.toUnmodifiableList());
    }

    private EnumSet<RangingTechnology> selectTechnologiesToUseWithPeer(
            CapabilityResponseMessage peerCapabilities
    ) throws ConfigSelectionException {
        EnumSet<RangingTechnology> selectable = EnumSet.noneOf(RangingTechnology.class);
        selectable.addAll(peerCapabilities.getSupportedRangingTechnologies());

        // Skip CS if supported by the remote device but no Bluetooth bond is established.
        if (selectable.contains(RangingTechnology.CS)
                && peerCapabilities.getCsCapabilities() != null
                && !mInjector.isRemoteDeviceBluetoothBonded(
                peerCapabilities.getCsCapabilities().getBluetoothAddress())
        ) {
            Log.v(TAG, RangingTechnology.CS + " is mutually supported, but skipping it because no "
                    + "Bluetooth bond exists with peer");
            selectable.remove(RangingTechnology.CS);
        }

        if (selectable.isEmpty()) {
            throw new ConfigSelectionException("Peer does not support any requested technologies",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        selectable = selectable
                .stream()
                .filter((technology -> mInjector
                        .getCapabilitiesProvider()
                        .getCapabilities()
                        .getTechnologyAvailability()
                        .get(technology.getValue()) == RangingCapabilities.ENABLED))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RangingTechnology.class)));

        if (selectable.isEmpty()) {
            throw new ConfigSelectionException(peerCapabilities.getSupportedRangingTechnologies()
                    + " are mutually supported, but they are all disabled by the user so we cannot "
                    + "proceed with ranging",
                    InternalReason.SYSTEM_POLICY);
        }

        selectable = selectable
                .stream()
                .filter(this::createConfigSelectorIfEnabledAndSupported)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RangingTechnology.class)));

        if (selectable.isEmpty()) {
            throw new ConfigSelectionException(
                    "No mutually supported technologies are compatible with the provided config",
                    InternalReason.UNSUPPORTED);
        }

        EnumSet<RangingTechnology> selected = EnumSet.noneOf(RangingTechnology.class);
        switch (mOobConfig.getRangingMode()) {
            case RANGING_MODE_AUTO:
            case RANGING_MODE_HIGH_ACCURACY_PREFERRED: {
                getPreferredTechnologyList()
                        .stream()
                        .filter(selectable::contains)
                        .findFirst()
                        .ifPresent(selected::add);
                break;
            }
            case RANGING_MODE_HIGH_ACCURACY: {
                if (selectable.contains(RangingTechnology.UWB)) {
                    selected.add(RangingTechnology.UWB);
                }
                break;
            }
            case RANGING_MODE_FUSED: {
                selected.addAll(selectable);
                break;
            }
        }

        return selected;
    }
}