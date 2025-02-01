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

package com.android.server.ranging.session;

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import android.content.AttributionSource;
import android.ranging.RangingCapabilities;
import android.ranging.SessionHandle;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.cs.CsOobCapabilities;
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobController.ReceivedMessage;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.rtt.RttOobCapabilities;
import com.android.server.ranging.rtt.RttOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.concurrent.ScheduledExecutorService;

public class OobResponderRangingSession
        extends BaseRangingSession
        implements RangingSession<OobResponderRangingConfig> {

    private static final String TAG = OobResponderRangingSession.class.getSimpleName();

    private final ScheduledExecutorService mOobExecutor;

    private OobHandle mPeer;
    private UwbAddress mMyUwbAddress;

    public OobResponderRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull RangingSessionConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningScheduledExecutorService executor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, executor);
        mOobExecutor = executor;
    }

    @Override
    public void start(@NonNull OobResponderRangingConfig config) {
        mPeer = new OobHandle(mSessionHandle, config.getDeviceHandle().getRangingDevice());
        mMyUwbAddress = UwbAddress.createRandomShortAddress();
        FluentFuture<ReceivedMessage> capabilitiesRequestFuture =
                mInjector.getOobController().registerMessageListener(mPeer);

        capabilitiesRequestFuture
                .transformAsync(this::handleCapabilitiesRequest, mOobExecutor)
                .transformAsync(this::handleSetConfiguration, mOobExecutor)
                .addCallback(new FutureCallback<>() {
                    @Override
                    public void onSuccess(ImmutableSet<TechnologyConfig> configs) {
                        // TODO: Only start for technologies who have the start ranging immediately
                        //  bit set. Otherwise we need to wait for the start ranging message
                        OobResponderRangingSession.super.start(configs);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(TAG, "Oob failed: ", t);
                    }
                }, mOobExecutor);
    }

    private ListenableFuture<ReceivedMessage> handleCapabilitiesRequest(ReceivedMessage message) {
        Log.i(TAG, "Received capabilities request message");
        CapabilityResponseMessage response = getCapabilitiesResponse(
                CapabilityRequestMessage.parseBytes(message.asBytes()));

        ListenableFuture<ReceivedMessage> setConfigurationFuture =
                mInjector.getOobController().registerMessageListener(mPeer);

        mInjector.getOobController().sendMessage(message.getOobHandle(), response.toBytes());
        return setConfigurationFuture;
    }

    private CapabilityResponseMessage getCapabilitiesResponse(CapabilityRequestMessage request) {
        RangingCapabilities myCapabilities = mInjector
                .getCapabilitiesProvider()
                .getCapabilities();

        CapabilityResponseMessage.Builder response = CapabilityResponseMessage.builder()
                .setHeader(OobHeader.builder()
                        .setMessageType(MessageType.CAPABILITY_RESPONSE)
                        .setVersion(OobHeader.OobVersion.CURRENT)
                        .build());

        ImmutableList.Builder<RangingTechnology> supportedTechnologies = ImmutableList.builder();

        if (request.getRequestedRangingTechnologies().contains(RangingTechnology.UWB)) {
            UwbRangingCapabilities uwbCapabilities = myCapabilities.getUwbCapabilities();
            if (uwbCapabilities != null) {
                supportedTechnologies.add(RangingTechnology.UWB);
                response.setUwbCapabilities(
                        UwbOobCapabilities.fromRangingCapabilities(uwbCapabilities, mMyUwbAddress));
            }
        }
        if (request.getRequestedRangingTechnologies().contains(RangingTechnology.CS)) {
            BleCsRangingCapabilities csCapabilities = myCapabilities.getCsCapabilities();
            if (csCapabilities != null) {
                supportedTechnologies.add(RangingTechnology.CS);
                response.setCsCapabilities(
                        CsOobCapabilities.fromRangingCapabilities(csCapabilities));
            }
        }

        if (request.getRequestedRangingTechnologies().contains(RangingTechnology.RTT)) {
            RttRangingCapabilities rttRangingCapabilities =
                    myCapabilities.getRttRangingCapabilities();
            if (rttRangingCapabilities != null) {
                supportedTechnologies.add(RangingTechnology.RTT);
                response.setRttCapabilities(
                        RttOobCapabilities.fromRangingCapabilities(rttRangingCapabilities));
            }
        }
        // TODO: Other technologies

        return response
                .setRangingTechnologiesPriority(supportedTechnologies.build())
                .setSupportedRangingTechnologies(supportedTechnologies.build())
                .build();
    }

    private ListenableFuture<ImmutableSet<TechnologyConfig>> handleSetConfiguration(
            ReceivedMessage message
    ) throws RangingEngine.ConfigSelectionException {
        Log.i(TAG, "Received set configuration message");

        ImmutableSet.Builder<TechnologyConfig> configs = ImmutableSet.builder();
        SetConfigurationMessage body = SetConfigurationMessage.parseBytes(message.asBytes());

        UwbOobConfig uwbConfig = body.getUwbConfig();
        if (uwbConfig != null) {
            configs.add(uwbConfig.toTechnologyConfig(mMyUwbAddress, mPeer.getRangingDevice()));
        }
        RttOobConfig rttOobConfig = body.getRttConfig();
        if (rttOobConfig != null) {
            configs.add(rttOobConfig.toTechnologyConfig(mPeer.getRangingDevice(),
                    DEVICE_ROLE_RESPONDER));
        }
        // Skip CS because the CS responder side does not need to be configured.

        return Futures.immediateFuture(configs.build());
    }
}
