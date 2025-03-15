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

import static com.android.server.ranging.RangingUtils.getUpdateRateFromDurationRange;
import static com.android.server.ranging.cs.CsConfig.CS_UPDATE_RATE_DURATIONS;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingEngine.ConfigSelectionException;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.SetConfigurationMessage.TechnologyOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Set;
import java.util.function.Function;

public class CsConfigSelector implements RangingEngine.ConfigSelector {
    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final BiMap<RangingDevice, String> mPeerAddresses;

    private final Set<@BleCsRangingCapabilities.SecurityLevel Integer> mSecurityLevels;

    private static boolean isCapableOfConfig(
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
    ) throws ConfigSelectionException {
        if (!isCapableOfConfig(oobConfig, capabilities)) {
            throw new ConfigSelectionException(
                    "Local device CS capabilities is incompatible with provided config",
                    InternalReason.UNSUPPORTED);
        }
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mPeerAddresses = HashBiMap.create();
        mSecurityLevels = capabilities.getSupportedSecurityLevels();
    }

    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull CapabilityResponseMessage response
    ) throws ConfigSelectionException {
        CsOobCapabilities capabilities = response.getCsCapabilities();
        if (capabilities == null) {
            throw new ConfigSelectionException("Peer " + peer + " does not support CS",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        mPeerAddresses.put(peer, capabilities.getBluetoothAddress());
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
        SelectedCsConfig configs = new SelectedCsConfig();
        return Pair.create(configs.getLocalConfigs(), configs.getPeerConfigs());
    }

    private class SelectedCsConfig {
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;
        private final @BleCsRangingCapabilities.SecurityLevel int mSecurityLevel;

        SelectedCsConfig() throws ConfigSelectionException {
            mRangingUpdateRate = selectRangingUpdateRate();
            mSecurityLevel = selectSecurityLevel();
        }

        public @NonNull ImmutableSet<TechnologyConfig> getLocalConfigs() {
            return mPeerAddresses.entrySet().stream()
                    .map((entry) -> new CsConfig(
                            new BleCsRangingParams.Builder(entry.getValue())
                                    .setRangingUpdateRate(mRangingUpdateRate)
                                    .setSecurityLevel(mSecurityLevel)
                                    .build(),
                            mSessionConfig,
                            entry.getKey()))
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull ImmutableMap<RangingDevice, TechnologyOobConfig> getPeerConfigs() {
            CsOobConfig config = CsOobConfig.builder().build();
            return mPeerAddresses.keySet().stream()
                    .collect(ImmutableMap.toImmutableMap(Function.identity(), (unused) -> config));
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
