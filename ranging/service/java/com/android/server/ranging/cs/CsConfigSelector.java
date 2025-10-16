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

package com.android.server.ranging.cs;

import static android.ranging.ble.cs.BleCsRangingCapabilities.CS_SECURITY_LEVEL_FOUR;
import static android.ranging.ble.cs.BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE;

import static com.android.server.ranging.common.ConfigurationUtils.getUpdateRateFromDurationRange;
import static com.android.server.ranging.common.RangingUtils.macAddressToBytes;
import static com.android.server.ranging.common.RangingUtils.macAddressToString;
import static com.android.server.ranging.cs.CsConfig.CS_UPDATE_RATE_DURATIONS;

import android.os.Build;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.BleCsCapabilities;
import com.android.server.ranging.oob.packets.BleCsConfiguration;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.session.ConfigurationManager;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class CsConfigSelector extends ConfigurationManager.ConfigSelector {
    private static final String FAKE_BLE_ADDRESS = "00:00:00:00:00:00";
    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final BiMap<RangingDevice, String> mPeerAddresses;

    private final Set<@BleCsRangingCapabilities.SecurityLevel Integer> mSecurityLevels;

    private @Nullable SelectedCsConfig mSelectedConfig = null;

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable BleCsRangingCapabilities capabilities
    ) {
        if (capabilities == null) return false;

        if (!(capabilities.getSupportedSecurityLevels().contains(CS_SECURITY_LEVEL_ONE)
                || capabilities.getSupportedSecurityLevels().contains(CS_SECURITY_LEVEL_FOUR))
        ) return false;

        if (getUpdateRateFromDurationRange(
                oobConfig.getRangingIntervalRange(), CS_UPDATE_RATE_DURATIONS).isEmpty()
        ) return false;

        return true;
    }

    public CsConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable BleCsRangingCapabilities capabilities
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mPeerAddresses = HashBiMap.create();
        mSecurityLevels = capabilities.getSupportedSecurityLevels();
    }

    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull Capabilities baseCapabilities
    ) throws ConfigSelectionException {
        if (!(baseCapabilities instanceof BleCsCapabilities capabilities)) {
            throw new ConfigSelectionException(
                    "Peer " + peer + " expected CS capabilities but got " + baseCapabilities,
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        mPeerAddresses.put(peer, macAddressToString(capabilities.getAddress()));
    }

    @Override
    public @NonNull Set<TechnologyConfig> selectLocalConfigs(
            @NonNull Set<RangingDevice> peers
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedCsConfig();
        return mSelectedConfig.getLocalConfigs(peers);
    }

    @Override
    public @NonNull Configuration selectRemoteConfig(
            @NonNull RangingDevice peer
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedCsConfig();
        return mSelectedConfig.getPeerConfig();
    }

    private class SelectedCsConfig {
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;
        private final @BleCsRangingCapabilities.SecurityLevel int mSecurityLevel;

        SelectedCsConfig() throws ConfigSelectionException {
            mRangingUpdateRate = selectRangingUpdateRate();
            mSecurityLevel = selectSecurityLevel();
        }

        public @NonNull ImmutableSet<TechnologyConfig> getLocalConfigs(Set<RangingDevice> peers) {
            return peers.stream()
                    .map((peer) -> {
                        String bleAddress = mPeerAddresses.get(peer);
                        if ("user".equals(Build.TYPE)) {
                            bleAddress = FAKE_BLE_ADDRESS;
                        }
                        return new CsConfig(
                                new BleCsRangingParams.Builder(bleAddress)
                                        .setRangingUpdateRate(mRangingUpdateRate)
                                        .setSecurityLevel(mSecurityLevel)
                                        .setLocationType(BleCsRangingParams.LOCATION_TYPE_UNKNOWN)
                                        .setSightType(
                                                BleCsRangingParams.SIGHT_TYPE_NON_LINE_OF_SIGHT)
                                        .build(),
                                mSessionConfig,
                                peer);
                    })
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull Configuration getPeerConfig() {
            return new BleCsConfiguration.Builder()
                    .setSecurityLevel((byte) mSecurityLevel)
                    .setAddress(macAddressToBytes(FAKE_BLE_ADDRESS))
                    .build();
        }
    }

    private @BleCsRangingCapabilities.SecurityLevel int selectSecurityLevel() {
        if (mOobConfig.getSecurityLevel() == OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE
                && mSecurityLevels.contains(CS_SECURITY_LEVEL_FOUR)
        ) return CS_SECURITY_LEVEL_FOUR;

        return CS_SECURITY_LEVEL_ONE;
    }

    private @RawRangingDevice.RangingUpdateRate int selectRangingUpdateRate()
            throws ConfigSelectionException {

        return getUpdateRateFromDurationRange(
                mOobConfig.getRangingIntervalRange(), CS_UPDATE_RATE_DURATIONS)
                .orElseThrow(() -> new ConfigSelectionException(
                        "Configured ranging interval range is incompatible with BLE CS",
                        InternalReason.UNSUPPORTED));
    }
}
