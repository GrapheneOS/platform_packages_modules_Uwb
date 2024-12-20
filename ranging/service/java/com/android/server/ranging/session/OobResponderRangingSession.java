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
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobController.ReceivedMessage;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.uwb.UwbConfig;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
        ListenableFuture<ReceivedMessage> future =
                mInjector.getOobController().registerMessageListener(
                        new OobHandle(mSessionHandle, config.getDeviceHandle().getRangingDevice()));

        Futures.addCallback(future, new CapabilitiesRequestHandler(), mOobExecutor);
    }

    private class CapabilitiesRequestHandler implements FutureCallback<ReceivedMessage> {

        @Override
        public void onSuccess(ReceivedMessage request) {
            OobHandle peer = request.getOobHandle();
            byte[] bytes = request.asBytes();

            OobHeader header = OobHeader.parseBytes(bytes);
            if (header.getMessageType() != MessageType.CAPABILITY_REQUEST) {
                Log.e(TAG, "OOB with handle " + peer + " failed: expected message with type "
                        + MessageType.CAPABILITY_REQUEST + " but got " + header.getMessageType()
                        + " instead");
                return;
            }
            RangingCapabilities myCapabilities = mInjector
                    .getCapabilitiesProvider()
                    .getCapabilities();

            // TODO: Use other technologies
            UwbRangingCapabilities uwbCapabilities = myCapabilities.getUwbCapabilities();
            CapabilityResponseMessage response = CapabilityResponseMessage.builder()
                    .setHeader(OobHeader.builder()
                            .setMessageType(MessageType.CAPABILITY_RESPONSE)
                            .setVersion(OobHeader.OobVersion.CURRENT)
                            .build())
                    .setRangingTechnologiesPriority(ImmutableList.of(RangingTechnology.UWB))
                    .setSupportedRangingTechnologies(ImmutableList.of(RangingTechnology.UWB))
                    .setUwbCapabilities(UwbOobCapabilities.builder()
                            .setUwbAddress(UwbAddress.fromBytes(new byte[]{3, 4}))
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

            ListenableFuture<ReceivedMessage> future =
                    mInjector.getOobController().registerMessageListener(peer);
            Futures.addCallback(future, new SetConfigurationHandler(), mOobExecutor);
            mInjector.getOobController().sendMessage(peer, response.toBytes());
        }


        @Override
        public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to receive capabilities request over OOB", t);
        }
    }

    private class SetConfigurationHandler implements FutureCallback<ReceivedMessage> {

        @Override
        public void onSuccess(ReceivedMessage request) {
            OobHandle peer = request.getOobHandle();
            byte[] bytes = request.asBytes();

            OobHeader header = OobHeader.parseBytes(bytes);
            if (header.getMessageType() != MessageType.SET_CONFIGURATION) {
                Log.e(TAG, "OOB with handle " + peer + " failed: expected message with type "
                        + MessageType.SET_CONFIGURATION + " but got " + header.getMessageType()
                        + " instead");
                return;
            }

            UwbOobConfig message = SetConfigurationMessage.parseBytes(bytes).getUwbConfig();

            UwbConfig config = new UwbConfig.Builder(
                    new UwbRangingParams.Builder(
                            message.getSessionId(),
                            message.getSelectedConfigId(),
                            UwbAddress.fromBytes(new byte[]{3, 4}),
                            message.getUwbAddress())
                            .setSessionKeyInfo(message.getSessionKey())
                            .setComplexChannel(new UwbComplexChannel.Builder()
                                    .setChannel(message.getSelectedChannel())
                                    .setPreambleIndex(message.getSelectedPreambleIndex())
                                    .build())
                            .setRangingUpdateRate(message.getSelectedRangingIntervalMs())
                            .setSlotDuration(message.getSelectedSlotDurationMs())
                            .build())
                    .setPeerAddresses(
                            ImmutableBiMap.of(peer.getRangingDevice(), message.getUwbAddress()))
                    .setCountryCode(message.getCountryCode())
                    .setDeviceRole(DEVICE_ROLE_RESPONDER)
                    .build();

            // TODO: Only start for technologies who have the start ranging immediately bit set.
            //  Otherwise we need to wait for the start ranging message
            OobResponderRangingSession.super.start(ImmutableSet.of(config));
        }

        @Override
        public void onFailure(@NonNull Throwable t) {
            Log.e(TAG, "Failed to receive set configuration message over OOB", t);
        }
    }
}
