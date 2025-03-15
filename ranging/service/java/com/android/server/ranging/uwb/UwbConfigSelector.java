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

package com.android.server.ranging.uwb;

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.oob.OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC;
import static android.ranging.oob.OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_5;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_25;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_32;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.util.Pair;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.RangingTimingParams;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingEngine.ConfigSelectionException;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.SetConfigurationMessage.TechnologyOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Selects a {@link UwbConfig} from local and peer device capabilities */
public class UwbConfigSelector implements RangingEngine.ConfigSelector {
    private static final String TAG = UwbConfigSelector.class.getSimpleName();

    private static final Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> HPRF_INDEXES =
            IntStream.rangeClosed(UWB_PREAMBLE_CODE_INDEX_25, UWB_PREAMBLE_CODE_INDEX_32)
                        .boxed()
                        .collect(Collectors.toSet());

    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final SessionHandle mSessionHandle;
    private final BiMap<RangingDevice, UwbAddress> mPeerAddresses;

    private final Set<@UwbRangingParams.ConfigId Integer> mConfigIds;
    private final Set<@UwbComplexChannel.UwbChannel Integer> mChannels;
    private final Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> mPreambleIndexes;
    private @UwbRangingParams.SlotDuration int mMinSlotDurationMs;
    private long mMinRangingIntervalMs;
    private final String mCountryCode;

    private static boolean isCapableOfConfig(
            @NonNull SessionConfig sessionConfig, @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable UwbRangingCapabilities capabilities
    ) {
        if (capabilities == null) return false;

        boolean isMulticast = oobConfig.getDeviceHandles().size() > 1;

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC
                && isMulticast
                && !capabilities.getSupportedConfigIds().contains(CONFIG_MULTICAST_DS_TWR)
        ) return false;

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC
                && !isMulticast
                && !capabilities.getSupportedConfigIds().contains(CONFIG_UNICAST_DS_TWR)
        ) return false;

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_SECURE
                && isMulticast
                && !capabilities
                        .getSupportedConfigIds().contains(CONFIG_PROVISIONED_MULTICAST_DS_TWR)
        ) return false;

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_SECURE
                && !isMulticast
                && !capabilities
                        .getSupportedConfigIds().contains(CONFIG_PROVISIONED_UNICAST_DS_TWR)
        ) return false;

        // TODO: If we add support for AoA via ARCore in the future, this will need to be changed.
        if (sessionConfig.isAngleOfArrivalNeeded() && !capabilities.isAzimuthalAngleSupported())
            return false;

        return true;
    }

    public UwbConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @NonNull SessionHandle sessionHandle,
            @Nullable UwbRangingCapabilities capabilities
    ) throws ConfigSelectionException {
        if (!isCapableOfConfig(sessionConfig, oobConfig, capabilities)) {
            throw new ConfigSelectionException("Local device is incapable of provided UWB config",
                    InternalReason.UNSUPPORTED);
        }

        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mSessionHandle = sessionHandle;
        mPeerAddresses = HashBiMap.create();
        mConfigIds = new HashSet<>(capabilities.getSupportedConfigIds());
        mChannels = new HashSet<>(capabilities.getSupportedChannels());
        mPreambleIndexes = new HashSet<>(capabilities.getSupportedPreambleIndexes());
        mMinSlotDurationMs = Collections.min(capabilities.getSupportedSlotDurations());
        mMinRangingIntervalMs = capabilities.getMinimumRangingInterval().toMillis();
        mCountryCode = capabilities.getCountryCode();
    }

    /**
     * @throws ConfigSelectionException if the provided capabilities are incompatible with the
     *                                  configuration
     */
    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull CapabilityResponseMessage response
    ) throws ConfigSelectionException {
        UwbOobCapabilities capabilities = response.getUwbCapabilities();
        if (capabilities == null) {
            throw new ConfigSelectionException("Peer " + peer + " does not support UWB",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }
        if (!capabilities.getSupportedDeviceRole().contains(UwbOobConfig.OobDeviceRole.INITIATOR)) {
            throw new ConfigSelectionException("Peer does not support initiator role",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        mPeerAddresses.put(peer, capabilities.getUwbAddress());
        mConfigIds.retainAll(capabilities.getSupportedConfigIds());
        mChannels.retainAll(capabilities.getSupportedChannels());
        mPreambleIndexes.retainAll(capabilities.getSupportedPreambleIndexes());
        mMinSlotDurationMs = Math.max(
                mMinSlotDurationMs, capabilities.getMinimumSlotDurationMs());
        mMinRangingIntervalMs = Math.max(
                mMinRangingIntervalMs, capabilities.getMinimumRangingIntervalMs());
    }

    @Override
    public boolean hasPeersToConfigure() {
        return !mPeerAddresses.isEmpty();
    }

    @Override
    public @NonNull Pair<
            ImmutableSet<TechnologyConfig>,
            ImmutableMap<RangingDevice, TechnologyOobConfig>
    > selectConfigs() throws ConfigSelectionException {
        SelectedUwbConfig configs = new SelectedUwbConfig();
        return Pair.create(configs.getLocalConfigs(), configs.getPeerConfigs());
    }

    private class SelectedUwbConfig {
        private final int mSessionId;
        private final UwbAddress mLocalAddress;
        private final @UwbRangingParams.ConfigId int mConfigId;
        private final @UwbComplexChannel.UwbChannel int mChannel;
        private final @UwbComplexChannel.UwbPreambleCodeIndex int mPreambleIndex;
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;
        private final byte[] mSessionKeyInfo;

        SelectedUwbConfig() throws ConfigSelectionException {
            mSessionId = mSessionHandle.hashCode();
            mLocalAddress = UwbAddress.createRandomShortAddress();
            mConfigId = selectConfigId();
            mChannel = selectChannel();
            mPreambleIndex = selectPreambleIndex();
            mRangingUpdateRate = selectRangingUpdateRate();
            mSessionKeyInfo = selectSessionKeyInfo();
        }

        // For now, each GRAPI responder will be a UWB initiator for a unicast session. In the
        // future we can look into combining these into a single multicast session somehow.

        public @NonNull ImmutableSet<TechnologyConfig> getLocalConfigs() {
            return mPeerAddresses.keySet().stream().map(
                    (device) -> new UwbConfig.Builder(
                            new UwbRangingParams.Builder(
                                    mSessionId, mConfigId, mLocalAddress,
                                    mPeerAddresses.get(device))
                                    .setSessionKeyInfo(mSessionKeyInfo)
                                    .setComplexChannel(new UwbComplexChannel.Builder()
                                            .setChannel(mChannel)
                                            .setPreambleIndex(mPreambleIndex)
                                            .build())
                                    .setRangingUpdateRate(mRangingUpdateRate)
                                    .setSlotDuration(mMinSlotDurationMs)
                                    .build())
                            .setSessionConfig(mSessionConfig)
                            .setDeviceRole(DEVICE_ROLE_RESPONDER)
                            .setPeerAddresses(ImmutableBiMap.of(device, mPeerAddresses.get(device)))
                            .build())
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull ImmutableMap<RangingDevice, TechnologyOobConfig> getPeerConfigs() {
            UwbOobConfig config = UwbOobConfig.builder()
                    .setUwbAddress(mLocalAddress)
                    .setSessionId(mSessionId)
                    .setSelectedConfigId(mConfigId)
                    .setSelectedChannel(mChannel)
                    .setSelectedPreambleIndex(mPreambleIndex)
                    .setSelectedRangingIntervalMs(Utils.getRangingTimingParams((int) mConfigId)
                            .getRangingInterval((int) mRangingUpdateRate))
                    .setSelectedSlotDurationMs(mMinSlotDurationMs)
                    .setSessionKey(mSessionKeyInfo)
                    .setCountryCode(mCountryCode)
                    .setDeviceRole(UwbOobConfig.OobDeviceRole.INITIATOR)
                    .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLLER)
                    .build();

            return mPeerAddresses.keySet().stream()
                    .collect(ImmutableMap.toImmutableMap(Function.identity(), (unused) -> config));
        }
    }

    private @UwbRangingParams.ConfigId int selectConfigId() throws ConfigSelectionException {
        if (mOobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC) {
            if (mConfigIds.contains(CONFIG_UNICAST_DS_TWR)) {
                return CONFIG_UNICAST_DS_TWR;
            }
        } else if (mOobConfig.getSecurityLevel() == SECURITY_LEVEL_SECURE) {
            if (mConfigIds.contains(CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST)) {
                return CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST;
            } else if (mConfigIds.contains(CONFIG_PROVISIONED_UNICAST_DS_TWR)) {
                return CONFIG_PROVISIONED_UNICAST_DS_TWR;
            }
        }

        throw new ConfigSelectionException("Failed to find agreeable config id",
                InternalReason.PEER_CAPABILITIES_MISMATCH);
    }

    private byte[] selectSessionKeyInfo() {
        byte[] sessionKeyInfo;
        if (mOobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC) {
            sessionKeyInfo = new byte[8];
        } else {
            sessionKeyInfo = new byte[16];
        }
        new Random().nextBytes(sessionKeyInfo);
        return sessionKeyInfo;
    }

    private @UwbComplexChannel.UwbChannel int selectChannel() throws ConfigSelectionException {
        if (mChannels.contains(UWB_CHANNEL_9)) {
            return UWB_CHANNEL_9;
        } else if (mChannels.contains(UWB_CHANNEL_5)) {
            return UWB_CHANNEL_5;
        } else {
            throw new ConfigSelectionException("Not all peers support uwb channel 9 or 5",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }
    }

    private @UwbComplexChannel.UwbPreambleCodeIndex int selectPreambleIndex()
            throws ConfigSelectionException {

        if (mPreambleIndexes.isEmpty()) {
            throw new ConfigSelectionException(
                    "Peers do not share support for any uwb preamble indexes",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }
        Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> supportedHprfIndexes =
                Sets.intersection(mPreambleIndexes, HPRF_INDEXES);

        // Prioritize HPRF indexes
        if (!supportedHprfIndexes.isEmpty()) {
            return List.copyOf(supportedHprfIndexes).get(
                    new Random().nextInt(supportedHprfIndexes.size()));
        } else {
            return List.copyOf(mPreambleIndexes).get(
                    new Random().nextInt(mPreambleIndexes.size()));
        }
    }

    private @RawRangingDevice.RangingUpdateRate int selectRangingUpdateRate()
            throws ConfigSelectionException {

        @UwbRangingParams.ConfigId int configId = selectConfigId();
        RangingTimingParams timings = Utils.getRangingTimingParams((int) configId);

        Range<Long> intervalsMs;
        try {
            intervalsMs = Range.create(
                    Math.max(mMinRangingIntervalMs, timings.getRangingIntervalFast()),
                    (long) timings.getRangingIntervalInfrequent());
        } catch (IllegalArgumentException unused) {
            throw new ConfigSelectionException("Timings supported by selected config id " + configId
                    + " are incompatible with local or peer ranging interval capabilities",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        // The code below is a little hard to read, but there are 3 cases:
        //   1. If configured range overlaps with the intervals that devices are capable of, select
        //      fastest supported.
        //   2. If configured range lies entirely above the intervals that devices are capable of,
        //      select UPDATE_RATE_INFREQUENT.
        //   3. If configured range lies entirely below the intervals that devices are capable of,
        //      select fastest supported.
        try {
            intervalsMs = intervalsMs.intersect(
                    mOobConfig.getFastestRangingInterval().toMillis(),
                    mOobConfig.getSlowestRangingInterval().toMillis());
        } catch (IllegalArgumentException ignored) {
            if (mOobConfig.getFastestRangingInterval().toMillis() > intervalsMs.getUpper()) {
                return UPDATE_RATE_INFREQUENT;
            }
        }

        return getFastestUpdateRateInRange(intervalsMs, timings);
    }

    private @RawRangingDevice.RangingUpdateRate int getFastestUpdateRateInRange(
            Range<Long> rangeMs, RangingTimingParams timings
    ) throws ConfigSelectionException {
        if (rangeMs.contains((long) timings.getRangingIntervalFast())) {
            return UPDATE_RATE_FREQUENT;
        } else if (rangeMs.contains((long) timings.getRangingIntervalNormal())) {
            return UPDATE_RATE_NORMAL;
        } else if (rangeMs.contains((long) timings.getRangingIntervalInfrequent())) {
            return UPDATE_RATE_INFREQUENT;
        } else {
            throw new ConfigSelectionException(
                    "Could not find update rate within the "
                            + "requested range that satisfies all peer capabilities",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }
    }
}
