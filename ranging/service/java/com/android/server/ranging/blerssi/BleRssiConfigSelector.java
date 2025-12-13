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

import static com.android.server.ranging.blerssi.BleRssiConfig.BLE_RSSI_UPDATE_RATE_DURATIONS;
import static com.android.server.ranging.common.ConfigurationUtils.getUpdateRateFromDurationRange;
import static com.android.server.ranging.common.RangingUtils.macAddressToBytes;
import static com.android.server.ranging.common.RangingUtils.macAddressToString;

import android.bluetooth.BluetoothDevice;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.BleRssiCapabilities;
import com.android.server.ranging.oob.packets.BleRssiConfiguration;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.session.ConfigurationManager;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;

public class BleRssiConfigSelector extends ConfigurationManager.ConfigSelector {
    private static final String TAG = BleRssiConfigSelector.class.getSimpleName();

    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;
    private final String mLocalAddress;
    private final BiMap<RangingDevice, String> mPeerAddresses;

    private @Nullable SelectedBleRssiConfig mSelectedConfig = null;

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig, BleRssiRangingCapabilities capabilities
    ) {
        if (capabilities == null) {
            Log.v(TAG, "Not capable of BLE RSSI");
            return false;
        }

        if (getUpdateRateFromDurationRange(
                oobConfig.getRangingIntervalRange(), BLE_RSSI_UPDATE_RATE_DURATIONS).isEmpty()
        ) {
            Log.v(TAG, "Not capable of configured ranging interval");
            return false;
        }

        return true;
    }

    public BleRssiConfigSelector(
            @NonNull SessionConfig sessionConfig,
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable BleRssiRangingCapabilities capabilities
    ) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mLocalAddress = capabilities.getBluetoothAddress();
        mPeerAddresses = HashBiMap.create();
    }

    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull Capabilities baseCapabilities
    ) throws ConfigSelectionException {
        if (!(baseCapabilities instanceof BleRssiCapabilities capabilities)) {
            throw new ConfigSelectionException(
                    "Peer " + peer + " expected BLE RSSI capabilities " + "but got "
                            + baseCapabilities,
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        mPeerAddresses.put(peer, macAddressToString(capabilities.getAddress()));
    }

    @Override
    public @NonNull Set<TechnologyConfig> selectLocalConfigs(
            @NonNull Set<RangingDevice> peers
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedBleRssiConfig();
        return mSelectedConfig.getLocalConfigs(peers);
    }

    @Override
    public @NonNull Configuration selectRemoteConfig(
            @NonNull RangingDevice peer
    ) throws ConfigSelectionException {
        if (mSelectedConfig == null) mSelectedConfig = new SelectedBleRssiConfig();
        return mSelectedConfig.getPeerConfig();
    }

    private class SelectedBleRssiConfig {
        private final @RawRangingDevice.RangingUpdateRate int mRangingUpdateRate;

        SelectedBleRssiConfig() throws ConfigSelectionException {
            mRangingUpdateRate = selectRangingUpdateRate();
        }

        public @NonNull ImmutableSet<TechnologyConfig> getLocalConfigs(Set<RangingDevice> peers) {
            return peers.stream()
                    .filter(mPeerAddresses::containsKey)
                    .map((peer) -> {
                        String address = mPeerAddresses.get(peer);
                        BluetoothDevice peerBluetoothDevice = null;

                        if (RangingInjector.isFlagEnabled("rangingStackUpdates26Q2")) {
                            peerBluetoothDevice = mOobConfig.getDeviceHandles().stream().filter(
                                    dh -> {
                                        return dh.getRangingDevice().equals(peer);
                                    }).findFirst().map(DeviceHandle::getBluetoothDevice).orElse(
                                    null);
                        }

                        return new BleRssiConfig(
                                DEVICE_ROLE_INITIATOR,
                                new BleRssiRangingParams.Builder(
                                        Objects.requireNonNull(mPeerAddresses.get(peer)))
                                        .setRangingUpdateRate(mRangingUpdateRate)
                                        .build(),
                                mSessionConfig,
                                peer,
                                peerBluetoothDevice);
                    })
                    .collect(ImmutableSet.toImmutableSet());
        }

        public @NonNull Configuration getPeerConfig() {
            return new BleRssiConfiguration.Builder()
                    .setAddress(macAddressToBytes(mLocalAddress))
                    .build();
        }
    }

    private @RawRangingDevice.RangingUpdateRate int selectRangingUpdateRate()
            throws ConfigSelectionException {

        return getUpdateRateFromDurationRange(
                mOobConfig.getRangingIntervalRange(), BLE_RSSI_UPDATE_RATE_DURATIONS)
                .orElseThrow(() -> new ConfigSelectionException(
                        "Configured ranging interval range is incompatible with BLE RSSI",
                        InternalReason.UNSUPPORTED));
    }
}
