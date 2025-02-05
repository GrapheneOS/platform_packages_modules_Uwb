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

import static com.android.server.ranging.RangingUtils.getDurationFromUpdateRate;
import static com.android.server.ranging.cs.CsConfig.CS_UPDATE_RATE_DURATIONS;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingEngine.ConfigSelectionException;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CsConfigSelector {
    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final BiMap<RangingDevice, String> mPeerAddresses;
    private final Map<RangingDevice, @BleCsRangingCapabilities.SecurityLevel Integer>
            mPeerSecurityLevels;

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable BleCsRangingCapabilities capabilities
    ) {
        if (capabilities == null) return false;

        if (!(capabilities.getSupportedSecurityLevels().contains(CS_SECURITY_LEVEL_ONE)
                || capabilities.getSupportedSecurityLevels().contains(CS_SECURITY_LEVEL_FOUR))
        ) return false;

        if (getDurationFromUpdateRate(
                oobConfig.getRangingIntervalRange(), CS_UPDATE_RATE_DURATIONS).isEmpty()
        ) return false;

        return true;
    }

    public CsConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mPeerAddresses = HashBiMap.create();
        mPeerSecurityLevels = new HashMap<>();
    }

    public void restrictConfigToCapabilities(
            @NonNull RangingDevice peer, @NonNull CsOobCapabilities capabilities
    ) throws ConfigSelectionException {
        mPeerAddresses.put(peer, capabilities.getBluetoothAddress());

        if (mOobConfig.getSecurityLevel() == OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC) {
            if (capabilities.getSupportedSecurityTypes()
                    .contains(CsOobConfig.CsSecurityType.LEVEL_ONE)
            ) {
                mPeerSecurityLevels.put(peer, CS_SECURITY_LEVEL_ONE);
            } else {
                throw new ConfigSelectionException("Configured security level "
                        + OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC + " but " + peer
                        + " only supports " + capabilities.getSupportedSecurityTypes());
            }
        } else {
            if (capabilities.getSupportedSecurityTypes()
                    .contains(CsOobConfig.CsSecurityType.LEVEL_FOUR)
            ) {
                mPeerSecurityLevels.put(peer, CS_SECURITY_LEVEL_FOUR);
            } else if (capabilities.getSupportedSecurityTypes()
                    .contains(CsOobConfig.CsSecurityType.LEVEL_ONE)
            ) {
                mPeerSecurityLevels.put(peer, CS_SECURITY_LEVEL_ONE);
            } else {
                throw new ConfigSelectionException("Configured security level "
                        + OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE + " but " + peer
                        + " only supports " + capabilities.getSupportedSecurityTypes());
            }
        }
    }

    public boolean hasPeersToConfigure() {
        return !mPeerAddresses.isEmpty();
    }

    public @NonNull SelectedCsConfig selectConfig() throws ConfigSelectionException {
        return new SelectedCsConfig();
    }

    public class SelectedCsConfig {
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;

        SelectedCsConfig() throws ConfigSelectionException {
            mRangingUpdateRate = selectRangingUpdateRate();
        }

        public @NonNull ImmutableSet<CsConfig> getLocalConfigs() {
            return mPeerAddresses.entrySet().stream()
                    .map((entry) -> new CsConfig(
                            new BleCsRangingParams.Builder(entry.getValue())
                                    .setRangingUpdateRate(mRangingUpdateRate)
                                    .setSecurityLevel(mPeerSecurityLevels.get(entry.getKey()))
                                    .build(),
                            mSessionConfig,
                            entry.getKey()))
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull ImmutableMap<RangingDevice, CsOobConfig> getPeerConfigs() {
            CsOobConfig config = CsOobConfig.builder().build();
            return mPeerAddresses.keySet().stream()
                    .collect(ImmutableMap.toImmutableMap(Function.identity(), (unused) -> config));
        }
    }

    private @RawRangingDevice.RangingUpdateRate int selectRangingUpdateRate()
            throws ConfigSelectionException {

        return getDurationFromUpdateRate(
                mOobConfig.getRangingIntervalRange(), CS_UPDATE_RATE_DURATIONS)
                .orElseThrow(() -> new ConfigSelectionException(
                        "Configured ranging interval range is incompatible with BLE CS"));
    }
}
