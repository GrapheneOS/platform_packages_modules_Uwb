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

package com.android.server.ranging.oob;

import static com.android.server.ranging.common.RangingUtils.macAddressToString;
import static com.android.server.ranging.common.RangingUtils.technologyBitset;

import android.ranging.RangingCapabilities;
import android.ranging.RangingDevice;
import android.util.Log;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.packets.BleCsCapabilities;
import com.android.server.ranging.oob.packets.BleRssiCapabilities;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV1;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV2;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.StopRequest;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.TechnologyTransitioning;
import com.android.server.ranging.oob.packets.UnknownCapabilities;
import com.android.server.ranging.oob.packets.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OobInitiatorProtocol {

    private static final String TAG = OobInitiatorProtocol.class.getSimpleName();

    private final RangingInjector mInjector;
    private final Map<RangingDevice, Version> mPeerVersions;

    public OobInitiatorProtocol(RangingInjector injector) {
        mInjector = injector;
        mPeerVersions = new HashMap<>();
    }

    public byte[] getCapabilitiesRequest(Set<RangingTechnology> technologies) {
        Log.v(TAG, "Requesting technologies " + technologies + " based on local capabilities");

        return new CapabilitiesRequest.Builder()
                .setVersion(Version.Current)
                .setRequestedTechnologies(technologyBitset(technologies))
                .build()
                .toBytes();
    }

    public record PeerCapabilities(
            TechnologyTransitioning transitioning,
            Map<Technology, Capabilities> byTechnology) {}

    public PeerCapabilities getCapabilitiesFromResponse(RangingDevice peer, byte[] responseBytes) {
        OobMessage message = OobMessage.fromBytes(responseBytes);
        Log.v(TAG, "Received " + message);

        TechnologyTransitioning transitioning;
        Capabilities[] responseCapabilities;
        switch (message) {
            case CapabilitiesResponseV1 v1 -> {
                responseCapabilities = v1.getCapabilities();
                transitioning = null;
            }
            case CapabilitiesResponseV2 v2 -> {
                responseCapabilities = v2.getCapabilities();
                transitioning = v2.getSupportedTransitioning();
            }
            case OobMessage other -> throw new IllegalArgumentException(
                    "Expected CapabilitiesResponse but got " + other);
        }

        Map<Technology, Capabilities> capsByTech = new HashMap<>(responseCapabilities.length);
        for (Capabilities capabilities : responseCapabilities) {
            @RangingCapabilities.RangingTechnologyAvailability int availability = mInjector
                    .getCapabilitiesProvider().getCapabilities().getTechnologyAvailability()
                    .get(Byte.toUnsignedInt(capabilities.getTechnology().toByte()));
            if (availability != RangingCapabilities.ENABLED) {
                Log.v(TAG, "Skipping " + capabilities.getTechnology() +  " supported by " + peer
                        + " because its availability is " + availability);
                continue;
            }

            switch (capabilities) {
                case BleCsCapabilities csCaps -> {
                    if (mInjector.isRemoteDeviceBluetoothBonded(
                            macAddressToString(csCaps.getAddress()))) {
                        capsByTech.remove(Technology.BleRssi);
                        capsByTech.put(Technology.BleCs, csCaps);
                    } else {
                        Log.v(TAG, "Skipping " + Technology.BleCs
                                + " because no Bluetooth bond exists with peer");
                    }
                }
                case BleRssiCapabilities bleRssiCaps -> {
                    if (!capsByTech.containsKey(Technology.BleCs)) {
                        capsByTech.put(Technology.BleRssi, bleRssiCaps);
                    }
                }
                case UnknownCapabilities unknown ->
                        Log.w(TAG, "Capabilities response with unknown capabilities " + unknown);
                default -> capsByTech.put(capabilities.getTechnology(), capabilities);
            }
        }

        mPeerVersions.put(peer, message.getVersion());
        return new PeerCapabilities(transitioning, capsByTech);
    }

    public ConfigurationRequest getConfigurationRequest(
            RangingDevice peer, Set<Configuration> configurations
    ) {
        TechnologySet technologies = technologyBitset(configurations.stream().map(
                c -> RangingTechnology.fromByte(c.getTechnology().toByte())).toList());
        return new ConfigurationRequest.Builder()
                .setVersion(mPeerVersions.get(peer))
                .setTechnologiesToConfigure(technologies)
                .setTechnologiesToStart(technologies)
                .setConfigs(configurations.toArray(new Configuration[0]))
                .build();
    }

    public byte[] getStopRequest(RangingDevice peer, Set<RangingTechnology> technologies) {
        return new StopRequest.Builder()
                .setVersion(mPeerVersions.get(peer))
                .setTechnologiesToStop(technologyBitset(technologies))
                .build()
                .toBytes();
    }
}
