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

import static com.android.server.ranging.common.RangingUtils.technologyBitset;

import android.ranging.RangingCapabilities;
import android.ranging.RangingDevice;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV1;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV2;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequestV1;
import com.android.server.ranging.oob.packets.ConfigurationRequestV3;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.oob.packets.MotionIndicator;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.StopRequest;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.TechnologyTransitioning;
import com.android.server.ranging.oob.packets.UnknownCapabilities;
import com.android.server.ranging.oob.packets.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OobInitiatorProtocol {

    private static final String TAG = OobInitiatorProtocol.class.getSimpleName();

    private final RangingInjector mInjector;
    @VisibleForTesting
    public final Map<RangingDevice, Version> mPeerVersions;
    private final Map<RangingDevice, DeviceType> mPeerTypes;

    public OobInitiatorProtocol(RangingInjector injector) {
        mInjector = injector;
        mPeerVersions = new HashMap<>();
        mPeerTypes = new HashMap<>();
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
            Map<Technology, Capabilities> byTechnology) {
    }

    public PeerCapabilities getCapabilitiesFromResponse(RangingDevice peer, byte[] responseBytes) {
        OobMessage message = OobMessage.fromBytes(responseBytes);
        Log.v(TAG, "Received " + message);

        TechnologyTransitioning transitioning;
        Capabilities[] responseCapabilities;
        switch (message) {
            case CapabilitiesResponseV1 v1 -> {
                responseCapabilities = v1.getCapabilities();
                mPeerTypes.put(peer, DeviceType.Unknown);
                transitioning = null;
            }
            case CapabilitiesResponseV2 v2 -> {
                responseCapabilities = v2.getCapabilities();
                mPeerTypes.put(peer, v2.getDeviceType());
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
                Log.v(TAG, "Skipping " + capabilities.getTechnology() + " supported by " + peer
                        + " because its availability is " + availability);
                continue;
            }

            if (capabilities instanceof UnknownCapabilities) {
                Log.w(TAG, "Capabilities response with unknown capabilities " + capabilities);
            } else {
                capsByTech.put(capabilities.getTechnology(), capabilities);
            }
        }

        mPeerVersions.put(peer, message.getVersion());
        return new PeerCapabilities(transitioning, capsByTech);
    }

    /**
     * Creates a configuration request message to be sent to a peer device.
     *
     * @param peer The remote device to which this request is being sent.
     * @param configurations The set of technology-specific configurations.
     * @param supportedMotion Indicates if the local device supports motion detection.
     * @return A {@link OobMessage} message.
     */
    public OobMessage getConfigurationRequest(
            RangingDevice peer, Set<Configuration> configurations, MotionIndicator supportedMotion
    ) {
        TechnologySet technologies = technologyBitset(configurations.stream().map(
                c -> RangingTechnology.fromByte(c.getTechnology().toByte())).toList());
        Version peerVersion = mPeerVersions.get(peer);
        // V3 adds motion indicator support.
        if (Objects.equals(peerVersion, Version.Current)) {
            return new ConfigurationRequestV3.Builder()
                    .setVersion(peerVersion)
                    .setTechnologiesToConfigure(technologies)
                    .setTechnologiesToStart(technologies)
                    .setConfigs(configurations.toArray(new Configuration[0]))
                    .setSupportedMotion(supportedMotion)
                    .build();
        } else {
            return new ConfigurationRequestV1.Builder()
                    .setTechnologiesToConfigure(technologies)
                    .setTechnologiesToStart(technologies)
                    .setConfigs(configurations.toArray(new Configuration[0]))
                    .build();
        }
    }

    public byte[] getStopRequest(RangingDevice peer, Set<RangingTechnology> technologies) {
        return new StopRequest.Builder()
                .setVersion(mPeerVersions.get(peer))
                .setTechnologiesToStop(technologyBitset(technologies))
                .build()
                .toBytes();
    }

    public DeviceType getPeerType(RangingDevice peer) {
        return mPeerTypes.get(peer);
    }
}
