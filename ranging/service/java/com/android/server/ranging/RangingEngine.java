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

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.DURATION_1_MS;

import android.ranging.RangingDevice;
import android.ranging.SessionHandle;
import android.ranging.oob.OobInitiatorRangingConfig.RangingMode;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;

public class RangingEngine {
    private final SessionHandle mSessionHandle;
    private final @RangingMode int mMode;
    private final CapabilitiesProvider mCapabilitiesProvider;
    private final Map<RangingDevice, CapabilityResponseMessage> mPeerCapabilities;

    public RangingEngine(
            SessionHandle sessionHandle,
            @RangingMode int mode,
            CapabilitiesProvider capabilitiesProvider
    ) {
        mSessionHandle = sessionHandle;
        mMode = mode;
        mCapabilitiesProvider = capabilitiesProvider;
        mPeerCapabilities = new HashMap<>();
    }

    public ImmutableList<RangingTechnology> getRequestedTechnologies() {
        // TODO: Determine based on my capabilities
        return ImmutableList.of(RangingTechnology.UWB);
    }

    public void addPeerCapabilities(RangingDevice peer, CapabilityResponseMessage capabilities) {
        mPeerCapabilities.put(peer, capabilities);
    }

    public ImmutableSet<TechnologyConfig> getConfigsSatisfyingCapabilities() {
        // TODO: Don't hardcode, use mMode and my capabilities to settle on a suitable
        //  configuration

        ImmutableSet.Builder<TechnologyConfig> configs = ImmutableSet.builder();

        for (RangingDevice peer : mPeerCapabilities.keySet()) {
            CapabilityResponseMessage capabilities = mPeerCapabilities.get(peer);

            UwbConfig config = new UwbConfig.Builder(
                    new UwbRangingParams.Builder(
                            mSessionHandle.hashCode(),
                            CONFIG_UNICAST_DS_TWR,
                            UwbAddress.createRandomShortAddress(),
                            capabilities.getUwbCapabilities().getUwbAddress())
                            .setSessionKeyInfo(
                                    new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1})
                            .setComplexChannel(new UwbComplexChannel.Builder()
                                    .setChannel(UWB_CHANNEL_9)
                                    .setPreambleIndex(UWB_PREAMBLE_CODE_INDEX_11)
                                    .build())
                            .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                            .setSlotDuration(DURATION_1_MS)
                            .build())
                    .setDeviceRole(DEVICE_ROLE_INITIATOR)
                    .setCountryCode("US")
                    .setPeerAddresses(ImmutableBiMap.of(
                            peer,
                            capabilities.getUwbCapabilities().getUwbAddress()
                    ))
                    .build();

            configs.add(config);
        }

        return configs.build();
    }
}
