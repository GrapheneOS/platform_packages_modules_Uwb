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

package com.android.server.ranging.blerssi;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;

import static com.android.server.ranging.RangingUtils.getUpdateRateFromDurationRange;
import static com.android.server.ranging.blerssi.BleRssiConfig.BLE_RSSI_UPDATE_RATE_DURATIONS;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingEngine.ConfigSelectionException;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.SetConfigurationMessage.TechnologyOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.function.Function;

public class BleRssiConfigSelector implements RangingEngine.ConfigSelector {
    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final String mLocalAddress;
    private final BiMap<RangingDevice, String> mPeerAddresses;

    private static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig, BleRssiRangingCapabilities capabilities
    ) {
        if (capabilities == null) return false;
        return getUpdateRateFromDurationRange(
                oobConfig.getRangingIntervalRange(), BLE_RSSI_UPDATE_RATE_DURATIONS).isPresent();
    }

    public BleRssiConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable BleRssiRangingCapabilities capabilities
    ) throws ConfigSelectionException {
        if (!isCapableOfConfig(oobConfig, capabilities)) {
            throw new ConfigSelectionException(
                    "Local device is incapable of provided BLE RSSI config");
        }
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mLocalAddress = capabilities.getBluetoothAddress();
        mPeerAddresses = HashBiMap.create();
    }

    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull CapabilityResponseMessage response
    ) throws ConfigSelectionException {
        BleRssiOobCapabilities capabilities = response.getBleRssiCapabilities();
        if (capabilities == null) throw new ConfigSelectionException(
                "Peer " + peer + " does not support UWB");

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
        SelectedBleRssiConfig configs = new SelectedBleRssiConfig();
        return Pair.create(configs.getLocalConfigs(), configs.getPeerConfigs());
    }

    private class SelectedBleRssiConfig {
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;

        SelectedBleRssiConfig() throws ConfigSelectionException {
            mRangingUpdateRate = selectRangingUpdateRate();
        }

        public @NonNull ImmutableSet<TechnologyConfig> getLocalConfigs() {
            return mPeerAddresses.entrySet().stream()
                    .map((entry) -> new BleRssiConfig(
                            DEVICE_ROLE_INITIATOR,
                            new BleRssiRangingParams.Builder(entry.getValue())
                                    .setRangingUpdateRate(mRangingUpdateRate)
                                    .build(),
                            mSessionConfig,
                            entry.getKey()))
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull ImmutableMap<RangingDevice, TechnologyOobConfig> getPeerConfigs() {
            BleRssiOobConfig config = BleRssiOobConfig.builder()
                    .setBluetoothAddress(mLocalAddress)
                    .build();
            return mPeerAddresses.keySet().stream().collect(
                    ImmutableMap.toImmutableMap(Function.identity(), (unused) -> config));
        }
    }

    private @RawRangingDevice.RangingUpdateRate int selectRangingUpdateRate()
            throws ConfigSelectionException {

        return getUpdateRateFromDurationRange(
                mOobConfig.getRangingIntervalRange(), BLE_RSSI_UPDATE_RATE_DURATIONS)
                .orElseThrow(() -> new ConfigSelectionException(
                        "Configured ranging interval range is incompatible with BLE CS"));
    }
}
