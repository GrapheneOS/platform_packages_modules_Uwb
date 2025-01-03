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
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

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
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableBiMap;
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

        // TODO: Use other technologies
        UwbRangingCapabilities uwbCapabilities = myCapabilities.getUwbCapabilities();
        return CapabilityResponseMessage.builder()
                .setHeader(OobHeader.builder()
                        .setMessageType(MessageType.CAPABILITY_RESPONSE)
                        .setVersion(OobHeader.OobVersion.CURRENT)
                        .build())
                .setRangingTechnologiesPriority(ImmutableList.of(RangingTechnology.UWB))
                .setSupportedRangingTechnologies(ImmutableList.of(RangingTechnology.UWB))
                .setUwbCapabilities(UwbOobCapabilities.builder()
                        .setUwbAddress(mMyUwbAddress)
                        .setSupportedChannels(
                                ImmutableList.copyOf(
                                        uwbCapabilities.getSupportedChannels()))
                        .setSupportedPreambleIndexes(
                                ImmutableList.copyOf(
                                        uwbCapabilities.getSupportedPreambleIndexes()))
                        .setSupportedConfigIds(
                                ImmutableList.copyOf(uwbCapabilities.getSupportedConfigIds()))
                        .setMinimumRangingIntervalMs(
                                (int) uwbCapabilities.getMinimumRangingInterval().toMillis())
                        .setMinimumSlotDurationMs(uwbCapabilities
                                .getSupportedSlotDurations()
                                .stream()
                                .min(Integer::compare)
                                .get())
                        .setSupportedDeviceRole(
                                ImmutableList.of(UwbOobConfig.OobDeviceRole.RESPONDER))
                        .build())
                .build();
    }

    private ListenableFuture<ImmutableSet<TechnologyConfig>> handleSetConfiguration(
            ReceivedMessage message
    ) {
        Log.i(TAG, "Received set configuration message");
        SetConfigurationMessage body = SetConfigurationMessage.parseBytes(message.asBytes());
        UwbOobConfig uwbConfig = body.getUwbConfig();

        UwbConfig config = new UwbConfig.Builder(
                new UwbRangingParams.Builder(
                        uwbConfig.getSessionId(),
                        uwbConfig.getSelectedConfigId(),
                        mMyUwbAddress,
                        uwbConfig.getUwbAddress())
                        .setSessionKeyInfo(uwbConfig.getSessionKey())
                        .setComplexChannel(new UwbComplexChannel.Builder()
                                .setChannel(uwbConfig.getSelectedChannel())
                                .setPreambleIndex(uwbConfig.getSelectedPreambleIndex())
                                .build())
                        .setRangingUpdateRate(uwbConfig.getSelectedRangingIntervalMs())
                        .setSlotDuration(uwbConfig.getSelectedSlotDurationMs())
                        .build())
                .setPeerAddresses(
                        ImmutableBiMap.of(mPeer.getRangingDevice(), uwbConfig.getUwbAddress()))
                .setCountryCode(uwbConfig.getCountryCode())
                .setDeviceRole(DEVICE_ROLE_RESPONDER)
                .build();

        return Futures.immediateFuture(ImmutableSet.of(config));
    }
}
