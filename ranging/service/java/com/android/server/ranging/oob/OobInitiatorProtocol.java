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
import android.ranging.oob.OobHandle;
import android.util.Log;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.packets.BleCsCapabilities;
import com.android.server.ranging.oob.packets.BleRssiCapabilities;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV1;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.StopRequest;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.UnknownCapabilities;
import com.android.server.ranging.oob.packets.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OobInitiatorProtocol {

    private static final String TAG = OobInitiatorProtocol.class.getSimpleName();

    private final RangingInjector mInjector;
    private final Map<OobHandle, Version> mPeerVersions;

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

    public Map<Technology, Capabilities> getCapabilitiesFromResponse(
            OobHandle handle, byte[] responseBytes
    ) {
        CapabilitiesResponseV1 response = CapabilitiesResponseV1.fromBytes(responseBytes);
        Map<Technology, Capabilities> capabilities =
                new HashMap<>(response.getCapabilities().length);

        for (Capabilities caps : response.getCapabilities()) {
            @RangingCapabilities.RangingTechnologyAvailability int availability = mInjector
                    .getCapabilitiesProvider().getCapabilities().getTechnologyAvailability()
                    .get(Byte.toUnsignedInt(caps.getTechnology().toByte()));
            if (availability != RangingCapabilities.ENABLED) {
                Log.v(TAG, "Skipping " + caps.getTechnology() +  " supported by "
                        + handle.getRangingDevice() + " because its availability is "
                        + availability);
                continue;
            }

            switch (caps) {
                case BleCsCapabilities csCaps -> {
                    if (mInjector.isRemoteDeviceBluetoothBonded(
                            macAddressToString(csCaps.getAddress()))) {
                        capabilities.remove(Technology.BleRssi);
                        capabilities.put(Technology.BleCs, csCaps);
                    } else {
                        Log.v(TAG, "Skipping " + Technology.BleCs
                                + " because no Bluetooth bond exists with peer");
                    }
                }
                case BleRssiCapabilities bleRssiCaps -> {
                    if (!capabilities.containsKey(Technology.BleCs)) {
                        capabilities.put(Technology.BleRssi, bleRssiCaps);
                    }
                }
                case UnknownCapabilities unknown ->
                        Log.w(TAG, "Capabilities response with unknown capabilities " + unknown);
                default -> capabilities.put(caps.getTechnology(), caps);
            }
        }

        mPeerVersions.put(handle, response.getVersion());
        return capabilities;
    }

    public ConfigurationRequest getConfigurationRequest(
            OobHandle handle, Set<Configuration> configurations
    ) {
        TechnologySet technologies = technologyBitset(configurations.stream().map(
                c -> RangingTechnology.fromByte(c.getTechnology().toByte())).toList());
        return new ConfigurationRequest.Builder()
                .setVersion(mPeerVersions.get(handle))
                .setTechnologiesToConfigure(technologies)
                .setTechnologiesToStart(technologies)
                .setConfigs(configurations.toArray(new Configuration[0]))
                .build();
    }

    public byte[] getStopRequest(OobHandle handle, Set<RangingTechnology> technologies) {
        return new StopRequest.Builder()
                .setVersion(mPeerVersions.get(handle))
                .setTechnologiesToStop(technologyBitset(technologies))
                .build()
                .toBytes();
    }
}
