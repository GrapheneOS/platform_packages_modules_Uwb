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

import static com.android.server.ranging.common.RangingUtils.bitset;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.util.Log;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.RangingTimingParams;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.oob.packets.UwbCapabilities;
import com.android.server.ranging.oob.packets.UwbConfiguration;
import com.android.server.ranging.oob.packets.UwbDeviceMode;
import com.android.server.ranging.oob.packets.UwbDeviceRole;
import com.android.server.ranging.session.ConfigurationManager;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Selects a {@link UwbConfig} from local and peer device capabilities */
public class UwbConfigSelector extends ConfigurationManager.ConfigSelector {
    private static final String TAG = UwbConfigSelector.class.getSimpleName();

    private static final Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> HPRF_INDEXES =
            IntStream.rangeClosed(UWB_PREAMBLE_CODE_INDEX_25, UWB_PREAMBLE_CODE_INDEX_32)
                        .boxed()
                        .collect(Collectors.toSet());

    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final BiMap<RangingDevice, UwbAddress> mPeerAddresses;
    private final DeviceType mLocalDeviceType;
    private final Map<RangingDevice, DeviceType> mPeerDeviceTypes;

    private final Set<@UwbRangingParams.ConfigId Integer> mConfigIds;
    private final Set<@UwbComplexChannel.UwbChannel Integer> mChannels;
    private final Set<@UwbComplexChannel.UwbPreambleCodeIndex Integer> mPreambleIndexes;
    private @UwbRangingParams.SlotDuration int mMinSlotDurationMs;
    private long mMinRangingIntervalMs;
    private final String mCountryCode;

    private @Nullable SelectedUwbConfig mSelectedConfig = null;

    public static boolean isCapableOfConfig(
            @NonNull SessionConfig sessionConfig, @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable UwbRangingCapabilities capabilities
    ) {
        if (capabilities == null) {
            Log.v(TAG, "Not capable of UWB");
            return false;
        }

        boolean isMulticast = oobConfig.getDeviceHandles().size() > 1;

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC
                && isMulticast
                && !capabilities.getSupportedConfigIds().contains(CONFIG_MULTICAST_DS_TWR)
        ) {
            Log.v(TAG, "Does not support CONFIG_ID necessary for multicast with basic security");
            return false;
        }

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC
                && !isMulticast
                && !capabilities.getSupportedConfigIds().contains(CONFIG_UNICAST_DS_TWR)
        ) {
            Log.v(TAG, "Does not support CONFIG_ID necessary for unicast with basic security");
            return false;
        }

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_SECURE
                && isMulticast
                && !capabilities
                        .getSupportedConfigIds().contains(CONFIG_PROVISIONED_MULTICAST_DS_TWR)
        ) {
            Log.v(TAG, "Does not support CONFIG_ID necessary for multicast with secure security");
            return false;
        }

        if (oobConfig.getSecurityLevel() == SECURITY_LEVEL_SECURE
                && !isMulticast
                && !capabilities
                        .getSupportedConfigIds().contains(CONFIG_PROVISIONED_UNICAST_DS_TWR)
        ) {
            Log.v(TAG, "Does not support CONFIG_ID necessary for unicast with secure security");
            return false;
        }

        return true;
    }

    public UwbConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @NonNull SessionHandle sessionHandle,
            @Nullable UwbRangingCapabilities capabilities,
            @NonNull DeviceType localDeviceType
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mLocalDeviceType = localDeviceType;
        mPeerDeviceTypes = new HashMap<>();
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
            @NonNull RangingDevice peer, @NonNull Capabilities baseCapabilities,
            @NonNull DeviceType deviceType
    ) throws ConfigSelectionException {
        if (!(baseCapabilities instanceof UwbCapabilities capabilities)) {
            throw new ConfigSelectionException(
                    "Peer " + peer + " expected UWB capabilities but got " + baseCapabilities,
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }
        if ((capabilities.getRoles() & UwbDeviceRole.Initiator.toByte()) != 1) {
            throw new ConfigSelectionException(
                    "Peer does not support initiator role",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        // If we've already selected a config, invalidate it
        mSelectedConfig = null;

        mPeerAddresses.put(peer, UwbAddress.fromBytes(capabilities.getAddress()));
        mConfigIds.retainAll(bitset(capabilities.getConfigIds()));
        mChannels.retainAll(bitset(capabilities.getChannels()));
        mPreambleIndexes.retainAll(bitset(capabilities.getPreambleIndexes(), i -> i + 1));
        mMinSlotDurationMs = Math.max(
                mMinSlotDurationMs, Byte.toUnsignedInt(capabilities.getMinSlotDuration()));
        mMinRangingIntervalMs = Math.max(
                mMinRangingIntervalMs, Short.toUnsignedLong(capabilities.getMinInterval()));
        mPeerDeviceTypes.put(peer, deviceType);
    }

    @Override
    public @NonNull Set<TechnologyConfig> selectLocalConfigs(
            @NonNull Set<RangingDevice> peers
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedUwbConfig(peers);
        return mSelectedConfig.getLocalConfigs(peers);
    }

    @Override
    public @NonNull Configuration selectRemoteConfig(
            @NonNull RangingDevice peer
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedUwbConfig(ImmutableSet.of(peer));
        return mSelectedConfig.getPeerConfig(peer);
    }

    private class SelectedUwbConfig {
        private final UwbAddress mLocalAddress;
        private final @UwbRangingParams.ConfigId int mConfigId;
        private final @UwbComplexChannel.UwbChannel int mChannel;
        private final @UwbComplexChannel.UwbPreambleCodeIndex int mPreambleIndex;
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;
        private final Map<RangingDevice, UwbDeviceRole> mPeerRoles;

        SelectedUwbConfig(Set<RangingDevice> peers) throws ConfigSelectionException {
            mLocalAddress = UwbAddress.createRandomShortAddress();
            mConfigId = selectConfigId();
            mChannel = selectChannel();
            mPreambleIndex = selectPreambleIndex();
            mRangingUpdateRate = selectRangingUpdateRate();
            mPeerRoles = selectPeerRole(peers);
        }

        // In the future we can look into combining these into a single multicast session somehow.

        public @NonNull ImmutableSet<TechnologyConfig> getLocalConfigs(Set<RangingDevice> peers) {
            return peers.stream().map(
                    peer -> new UwbConfig.Builder(
                            new UwbRangingParams.Builder(
                                    peer.hashCode(), mConfigId, mLocalAddress,
                                    mPeerAddresses.get(peer))
                                    .setSessionKeyInfo(selectSessionKeyInfo(peer))
                                    .setComplexChannel(new UwbComplexChannel.Builder()
                                            .setChannel(mChannel)
                                            .setPreambleIndex(mPreambleIndex)
                                            .build())
                                    .setRangingUpdateRate(mRangingUpdateRate)
                                    .setSlotDuration(mMinSlotDurationMs)
                                    .build())
                            .setSessionConfig(mSessionConfig)
                            .setDeviceRole(
                                    mPeerRoles.get(peer) == UwbDeviceRole.Initiator
                                            ? DEVICE_ROLE_RESPONDER : DEVICE_ROLE_INITIATOR)
                            .setPeerAddresses(ImmutableBiMap.of(peer, mPeerAddresses.get(peer)))
                            .build())
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull Configuration getPeerConfig(RangingDevice peer) {
            return new UwbConfiguration.Builder()
                    .setAddress(mLocalAddress.getAddressBytes())
                    .setSessionId(peer.hashCode())
                    .setConfigId((byte) mConfigId)
                    .setChannel((byte) mChannel)
                    .setPreambleIndex((byte) mPreambleIndex)
                    .setInterval((short) Utils.getRangingTimingParams((int) mConfigId)
                            .getRangingInterval((int) mRangingUpdateRate))
                    .setSlotDuration((byte) mMinSlotDurationMs)
                    .setSessionKey(selectSessionKeyInfo(peer))
                    .setCountryCode(mCountryCode.getBytes(StandardCharsets.US_ASCII))
                    .setDeviceRole(mPeerRoles.get(peer))
                    .setDeviceMode(UwbDeviceMode.Controller)
                    .build();
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

    private byte[] selectSessionKeyInfo(RangingDevice peer) {
        byte[] sessionKeyInfo;
        if (mOobConfig.getSecurityLevel() == SECURITY_LEVEL_BASIC) {
            sessionKeyInfo = new byte[8];
        } else {
            sessionKeyInfo = new byte[16];
        }
        new Random(peer.hashCode()).nextBytes(sessionKeyInfo);
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

    private Map<RangingDevice, UwbDeviceRole> selectPeerRole(Set<RangingDevice> peers)
            throws ConfigSelectionException {

        Map<RangingDevice, UwbDeviceRole> peerRoles = new HashMap<>();
        boolean isOneToMany = mOobConfig.getDeviceHandles().size() > 1;

        for (RangingDevice peer : peers) {
            if (isOneToMany) {
                peerRoles.put(peer, UwbDeviceRole.Responder);
            } else {
                int localRank = RangingInjector.getInstance()
                                    .getDeviceTypePowerRank(mLocalDeviceType);
                int remoteRank = RangingInjector.getInstance()
                                    .getDeviceTypePowerRank(mPeerDeviceTypes.get(peer));
                peerRoles.put(
                        peer,
                        localRank > remoteRank
                                ? UwbDeviceRole.Responder : UwbDeviceRole.Initiator);
            }
        }

        return peerRoles;
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
