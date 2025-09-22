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

import static com.android.server.ranging.RangingUtils.technologyBitset;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Log;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.CapabilitiesResponse;
import com.android.server.ranging.oob.packets.StopRequest;
import com.android.server.ranging.oob.packets.Version;

import com.google.common.collect.ImmutableSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OobInitiatorProtocol {

    private static final String TAG = OobInitiatorProtocol.class.getSimpleName();

    private final RangingEngine mEngine;
    private final Map<RangingDevice, Version> mPeerVersions;

    public OobInitiatorProtocol(
            SessionConfig sessionConfig, OobInitiatorRangingConfig oobConfig,
            SessionHandle sessionHandle, RangingInjector injector
    ) throws RangingEngine.ConfigSelectionException {
        mPeerVersions = new HashMap<>();
        mEngine = new RangingEngine(
                sessionConfig, oobConfig, sessionHandle, injector, mPeerVersions);
    }

    public byte[] getCapabilitiesRequest() {
        ImmutableSet<RangingTechnology> technologies = mEngine.getRequestedTechnologies();
        Log.v(TAG, "Requesting technologies " + technologies + " based on local capabilities");

        return new CapabilitiesRequest.Builder()
                .setVersion(Version.Current)
                .setRequestedTechnologies(technologyBitset(technologies))
                .build()
                .toBytes();
    }

    public RangingEngine.SelectedConfig getConfigurations(
            Map<OobHandle, byte[]> responses
    ) throws RangingEngine.ConfigSelectionException {
        for (OobHandle handle : responses.keySet()) {
            CapabilitiesResponse response = CapabilitiesResponse.fromBytes(responses.get(handle));
            Log.v(TAG, "Received " + response + " from " + handle.getRangingDevice());
            mPeerVersions.put(handle.getRangingDevice(), response.getVersion());
            mEngine.addPeerCapabilities(handle.getRangingDevice(), response);
        }

        return mEngine.selectConfigs();
    }

    public byte[] getStopRequest(OobHandle handle, Set<RangingTechnology> technologies) {
        return new StopRequest.Builder()
                .setVersion(mPeerVersions.get(handle.getRangingDevice()))
                .setTechnologiesToStop(technologyBitset(technologies))
                .build()
                .toBytes();
    }
}
