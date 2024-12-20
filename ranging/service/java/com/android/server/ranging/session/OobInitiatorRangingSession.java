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

import android.content.AttributionSource;
import android.ranging.RangingDevice;
import android.ranging.SessionHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobController.ReceivedMessage;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfig;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class OobInitiatorRangingSession
        extends BaseRangingSession
        implements RangingSession<OobInitiatorRangingConfig> {

    private static final String TAG = OobInitiatorRangingSession.class.getSimpleName();

    private static final long MESSAGE_TIMEOUT_MS = 2000;

    private final ScheduledExecutorService mOobExecutor;
    private RangingEngine mRangingEngine;

    public OobInitiatorRangingSession(
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
    public void start(@NonNull OobInitiatorRangingConfig config) {
        mRangingEngine = new RangingEngine(
                mSessionHandle, config.getRangingMode(), mInjector.getCapabilitiesProvider());

        Set<OobHandle> mOobHandles = config.getDeviceHandles()
                .stream().map((handle) -> new OobHandle(mSessionHandle, handle.getRangingDevice()))
                .collect(Collectors.toSet());

        List<ListenableFuture<ReceivedMessage>> futures = mOobHandles
                .stream()
                .map((handle) -> Futures.withTimeout(
                        mInjector.getOobController().registerMessageListener(handle),
                        MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS, mOobExecutor)
                )
                .toList();

        Futures.addCallback(
                Futures.successfulAsList(futures), new CapabilitiesResponseHandler(), mOobExecutor);

        byte[] message = CapabilityRequestMessage.builder()
                // TODO: Select requested technologies based on params#getRangingMode()
                .setHeader(OobHeader.builder()
                        .setMessageType(MessageType.CAPABILITY_REQUEST)
                        .setVersion(OobHeader.OobVersion.CURRENT)
                        .build())
                .setRequestedRangingTechnologies(mRangingEngine.getRequestedTechnologies())
                .build()
                .toBytes();

        mOobHandles.forEach((handle) -> mInjector.getOobController().sendMessage(handle, message));
    }

    private class CapabilitiesResponseHandler implements FutureCallback<List<ReceivedMessage>> {

        @Override
        public void onSuccess(List<ReceivedMessage> responses) {
            for (ReceivedMessage response : responses) {
                OobHeader header = OobHeader.parseBytes(response.asBytes());
                if (header.getMessageType() != MessageType.CAPABILITY_RESPONSE) {
                    Log.e(TAG, "OOB with handle " + response.getOobHandle()
                            + " failed: expected message with type "
                            + MessageType.CAPABILITY_RESPONSE + " but got "
                            + header.getMessageType() + " instead");
                    continue;
                }

                mRangingEngine.addPeerCapabilities(
                        response.getOobHandle().getRangingDevice(),
                        CapabilityResponseMessage.parseBytes(response.asBytes()));
            }

            ImmutableSet<TechnologyConfig> configs =
                    mRangingEngine.getConfigsSatisfyingCapabilities();

            for (TechnologyConfig technologyConfig : configs) {
                // TODO: Handle other technologies
                UwbConfig config = (UwbConfig) technologyConfig;

                for (RangingDevice peer : config.getPeerDevices()) {
                    SetConfigurationMessage response = SetConfigurationMessage.builder()
                            .setHeader(OobHeader.builder()
                                    .setMessageType(MessageType.SET_CONFIGURATION)
                                    .setVersion(OobHeader.OobVersion.CURRENT)
                                    .build())
                            .setRangingTechnologiesSet(ImmutableList.of(RangingTechnology.UWB))
                            .setStartRangingList(ImmutableList.of(RangingTechnology.UWB))
                            .setUwbConfig(UwbOobConfig.builder()
                                    .setSessionId(mSessionHandle.hashCode())
                                    .setSelectedConfigId(
                                            config.getParameters().getConfigId())
                                    .setUwbAddress(
                                            config.getParameters().getDeviceAddress())
                                    .setSelectedChannel(config.getParameters()
                                            .getComplexChannel()
                                            .getChannel())
                                    .setSessionKey(config.getParameters().getSessionKeyInfo())
                                    .setSelectedPreambleIndex(config.getParameters()
                                            .getComplexChannel()
                                            .getPreambleIndex())
                                    .setSelectedRangingIntervalMs(
                                            config.getParameters().getRangingUpdateRate())
                                    .setSelectedSlotDurationMs(
                                            config.getParameters().getSlotDuration())
                                    .setCountryCode(config.getCountryCode())
                                    .setDeviceRole(UwbOobConfig.OobDeviceRole.RESPONDER)
                                    .setDeviceMode(UwbOobConfig.OobDeviceMode.CONTROLEE)
                                    .build())
                            .build();

                    mInjector.getOobController().sendMessage(
                            new OobHandle(mSessionHandle, peer),
                            response.toBytes());
                }
            }

            OobInitiatorRangingSession.super.start(configs);
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to receive capabilities response over OOB", t);
        }
    }
}
