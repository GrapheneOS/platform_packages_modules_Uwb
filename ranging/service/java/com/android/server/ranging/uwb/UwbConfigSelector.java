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

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
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
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.RangingTimingParams;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.server.ranging.RangingEngine.ConfigSelectionException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Selects a {@link UwbConfig} from local and peer device capabilities */
public class UwbConfigSelector {
    private static final String TAG = UwbConfigSelector.class.getSimpleName();

    private static final Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> HPRF_INDEXES =
            Set.copyOf(
                    IntStream.rangeClosed(UWB_PREAMBLE_CODE_INDEX_25, UWB_PREAMBLE_CODE_INDEX_32)
                            .boxed()
                            .toList());

    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final SessionHandle mSessionHandle;
    private final BiMap<RangingDevice, UwbAddress> mPeerAddresses;

    private final Set<@UwbRangingParams.ConfigId Integer> mConfigIds;
    private final Set<@UwbComplexChannel.UwbChannel Integer> mChannels;
    private final Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> mPreambleIndexes;
    private @UwbRangingParams.SlotDuration int mMinSlotDurationMs;
    private Range<Duration> mRangingIntervals;

    public static boolean isCapableOfConfig(
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

        if (capabilities.getMinimumRangingInterval()
                .compareTo(oobConfig.getSlowestRangingInterval()) > 0
        ) return false;

        // TODO: If we add support for AoA via ARCore in the future, this will need to be changed.
        if (sessionConfig.isAngleOfArrivalNeeded() && !capabilities.isAzimuthalAngleSupported())
            return false;

        return true;
    }

    public UwbConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @NonNull UwbRangingCapabilities capabilities,
            @NonNull SessionHandle sessionHandle
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mSessionHandle = sessionHandle;
        mPeerAddresses = HashBiMap.create();
        mConfigIds = new HashSet<>(capabilities.getSupportedConfigIds());
        mChannels = new HashSet<>(capabilities.getSupportedChannels());
        mPreambleIndexes = new HashSet<>(capabilities.getSupportedPreambleIndexes());
        mMinSlotDurationMs = Collections.min(capabilities.getSupportedSlotDurations());
        mRangingIntervals = mOobConfig.getRangingIntervalRange().intersect(
                capabilities.getMinimumRangingInterval(),
                mOobConfig.getSlowestRangingInterval());
    }

    /**
     * @throws ConfigSelectionException if the provided capabilities are incompatible with the
     *                                  configuration
     */
    public void restrictConfigToCapabilities(
            @NonNull RangingDevice peer, @NonNull UwbOobCapabilities capabilities
    ) throws ConfigSelectionException {
        if (!capabilities.getSupportedDeviceRole().contains(UwbOobConfig.OobDeviceRole.RESPONDER)) {
            throw new ConfigSelectionException("Peer does not support responder role");
        }

        mPeerAddresses.put(peer, capabilities.getUwbAddress());
        mConfigIds.retainAll(capabilities.getSupportedConfigIds());
        mChannels.retainAll(capabilities.getSupportedChannels());
        mPreambleIndexes.retainAll(capabilities.getSupportedPreambleIndexes());
        mMinSlotDurationMs = Math.max(
                mMinSlotDurationMs, capabilities.getMinimumSlotDurationMs());
        try {
            mRangingIntervals = mRangingIntervals.intersect(
                    Duration.ofMillis(capabilities.getMinimumRangingIntervalMs()),
                    mRangingIntervals.getUpper());
        } catch (IllegalArgumentException unused) {
            throw new ConfigSelectionException("Peer " + peer
                    + " does not support a compatible ranging interval");
        }
    }

    public boolean hasPeersToConfigure() {
        return !mPeerAddresses.isEmpty();
    }

    public @NonNull SelectedUwbConfig selectConfig() throws ConfigSelectionException {
        return new SelectedUwbConfig();
    }

    public class SelectedUwbConfig {
        private final int mSessionId;
        private final UwbAddress mLocalAddress;
        private final @UwbRangingParams.ConfigId int mConfigId;
        private final @UwbComplexChannel.UwbChannel int mChannel;
        private final @UwbComplexChannel.UwbPreambleCodeIndex int mPreambleIndex;
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;
        private final byte[] mSessionKeyInfo;
        private final String mCountryCode;

        SelectedUwbConfig() throws ConfigSelectionException {
            mSessionId = mSessionHandle.hashCode();
            mLocalAddress = UwbAddress.createRandomShortAddress();
            mConfigId = selectConfigId();
            mChannel = selectChannel();
            mPreambleIndex = selectPreambleIndex();
            mRangingUpdateRate = selectRangingUpdateRate();
            mSessionKeyInfo = selectSessionKeyInfo();
            // TODO: Set based on geolocation
            mCountryCode = "US";
        }

        public @NonNull UwbConfig getLocalConfig() {
            return new UwbConfig.Builder(
                    new UwbRangingParams.Builder(
                            mSessionId, mConfigId, mLocalAddress,
                            mPeerAddresses.values().iterator().next())
                            .setSessionKeyInfo(mSessionKeyInfo)
                            .setComplexChannel(new UwbComplexChannel.Builder()
                                    .setChannel(mChannel)
                                    .setPreambleIndex(mPreambleIndex)
                                    .build())
                            .setRangingUpdateRate(mRangingUpdateRate)
                            .setSlotDuration(mMinSlotDurationMs)
                            .build())
                    .setSessionConfig(mSessionConfig)
                    .setDeviceRole(DEVICE_ROLE_INITIATOR)
                    .setCountryCode(mCountryCode)
                    .setPeerAddresses(ImmutableBiMap.copyOf(mPeerAddresses))
                    .build();
        }

        public @NonNull ImmutableMap<RangingDevice, UwbOobConfig> getPeerConfigs() {
            UwbOobConfig config = UwbOobConfig.builder()
                    .setUwbAddress(mLocalAddress)
                    .setSessionId(mSessionId)
                    .setSelectedConfigId(mConfigId)
                    .setSelectedChannel(mChannel)
                    .setSelectedPreambleIndex(mPreambleIndex)
                    .setSelectedRangingIntervalMs(mRangingUpdateRate)
                    .setSelectedSlotDurationMs(mMinSlotDurationMs)
                    .setSessionKey(mSessionKeyInfo)
                    .setCountryCode(mCountryCode)
                    .setDeviceRole(UwbOobConfig.OobDeviceRole.RESPONDER)
                    .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLEE)
                    .build();

            return mPeerAddresses.keySet().stream()
                    .collect(ImmutableMap.toImmutableMap(Function.identity(), (unused) -> config));
        }
    }

    private @UwbRangingParams.ConfigId int selectConfigId() throws ConfigSelectionException {

        boolean isMulticast = mPeerAddresses.size() > 1;

        // Fastest update rate (96 ms) is a special case because it requires its own config id.
        boolean supportsVeryFastConfigId =
                mOobConfig.getSecurityLevel() == SECURITY_LEVEL_SECURE
                        && !isMulticast
                        && mRangingIntervals.contains(Duration.ofMillis(
                        Utils.getRangingTimingParams(
                                        (int) CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST)
                                .getRangingIntervalFast()))
                        && mConfigIds.contains(CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST);
        if (supportsVeryFastConfigId) {
            return CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST;
        }

        int configId;
        if (mOobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC) {
            configId = isMulticast
                    ? CONFIG_MULTICAST_DS_TWR
                    : CONFIG_UNICAST_DS_TWR;
        } else {
            configId = isMulticast
                    ? CONFIG_PROVISIONED_MULTICAST_DS_TWR
                    : CONFIG_PROVISIONED_UNICAST_DS_TWR;
        }
        if (!mConfigIds.contains(configId)) {
            throw new ConfigSelectionException(
                    "Not all peers support configured uwb config id " + configId);
        }
        return configId;
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
            throw new ConfigSelectionException("Not all peers support uwb channel 9 or 5 ");
        }
    }

    private @UwbComplexChannel.UwbPreambleCodeIndex int selectPreambleIndex()
            throws ConfigSelectionException {

        if (mPreambleIndexes.isEmpty()) {
            throw new ConfigSelectionException(
                    "Peers do not share support for any uwb preamble indexes");
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

        // Prioritize faster update rates
        if (mRangingIntervals.contains(
                Duration.ofMillis(timings.getRangingIntervalFast()))
        ) {
            return UPDATE_RATE_FREQUENT;
        } else if (mRangingIntervals.contains(
                Duration.ofMillis(timings.getRangingIntervalNormal()))
        ) {
            return UPDATE_RATE_NORMAL;
        } else if (mRangingIntervals.contains(
                Duration.ofMillis(timings.getRangingIntervalInfrequent()))
        ) {
            return UPDATE_RATE_INFREQUENT;
        } else {
            throw new ConfigSelectionException(
                    "Could not find update rate within the "
                            + "requested range that satisfies all peer capabilities");
        }
    }
}
