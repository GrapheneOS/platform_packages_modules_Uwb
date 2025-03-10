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
import android.ranging.SessionHandle;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.OobController.OobConnection;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.StopRangingMessage;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class OobInitiatorRangingSession
        extends BaseRangingSession
        implements RangingSession<OobInitiatorRangingConfig> {

    private static final String TAG = OobInitiatorRangingSession.class.getSimpleName();

    private static final long MESSAGE_TIMEOUT_MS = 4000;

    private final ScheduledExecutorService mOobExecutor;
    private final ConcurrentHashMap<OobHandle, OobConnection> mOobConnections;

    private RangingEngine mRangingEngine;

    public OobInitiatorRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull RangingSessionConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService oobExecutor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, adapterExecutor);
        mOobExecutor = oobExecutor;
        mOobConnections = new ConcurrentHashMap<>();
    }

    @Override
    public void start(@NonNull OobInitiatorRangingConfig config) {
        try {
            mRangingEngine = new RangingEngine(
                    mConfig.getSessionConfig(), config, mSessionHandle, mInjector);
        } catch (RangingEngine.ConfigSelectionException e) {
            Log.w(TAG, "Provided config incompatible with local capabilities: ", e);
            mSessionListener.onSessionStopped(InternalReason.UNSUPPORTED);
            return;
        }

        sendCapabilityRequestMessages(config.getDeviceHandles())
                .transformAsync((unused) -> sendSetConfigMessages(), mOobExecutor)
                .addCallback(new FutureCallback<>() {
                    @Override
                    public void onSuccess(ImmutableSet<TechnologyConfig> localConfigs) {
                        // TODO: Send start ranging message to peers who don't have all active
                        //  technologies in their start ranging list
                        OobInitiatorRangingSession.super.start(localConfigs);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "Oob failed: ", t);
                        switch (t) {
                            case RangingEngine.ConfigSelectionException e ->
                                    mSessionListener.onSessionStopped(e.getReason());
                            case TimeoutException unused ->
                                    mSessionListener.onSessionStopped(
                                            InternalReason.NO_PEERS_FOUND);
                            default ->
                                    mSessionListener.onSessionStopped(
                                            InternalReason.INTERNAL_ERROR);
                        }
                    }
                }, mOobExecutor);
    }

    @Override
    public void stop() {
        Map<OobHandle, FluentFuture<Void>> pendingSends = new HashMap<>(mOobConnections.size());

        mOobConnections.forEach((oobHandle, connection) -> {
            ImmutableSet<RangingTechnology> technologies = OobInitiatorRangingSession.super
                    .getTechnologiesUsedByPeer(oobHandle.getRangingDevice());

            Log.v(TAG, "Sending stop ranging to peer " + oobHandle + " with technologies "
                    + technologies);

            pendingSends.put(oobHandle, mOobConnections.get(oobHandle)
                    .sendData(StopRangingMessage.builder()
                            .setOobHeader(OobHeader.builder()
                                    .setMessageType(MessageType.STOP_RANGING)
                                    .setVersion(OobHeader.OobVersion.CURRENT)
                                    .build())
                            .setRangingTechnologiesToStop(ImmutableList.copyOf(technologies))
                            .build()
                            .toBytes()));
        });

        FluentFuture.from(
                        Futures.whenAllComplete(pendingSends.values())
                                .call(() -> handleFailedFutures(pendingSends), mOobExecutor))
                .addCallback(new FutureCallback<>() {
                    @Override
                    public void onSuccess(Map<OobHandle, Void> result) {
                        OobInitiatorRangingSession.super.stop();
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        Log.e(TAG, "Failed to send stop ranging message over oob", t);
                        OobInitiatorRangingSession.super.stop();
                    }
                }, mOobExecutor);
    }

    private FluentFuture<Void> sendCapabilityRequestMessages(List<DeviceHandle> deviceHandles) {
        Map<OobHandle, FluentFuture<byte[]>> pendingResponses = new HashMap<>();
        for (DeviceHandle deviceHandle : deviceHandles) {
            OobHandle handle = new OobHandle(mSessionHandle, deviceHandle.getRangingDevice());
            mOobConnections.put(handle, mInjector.getOobController().createConnection(handle));
        }
        ImmutableSet<RangingTechnology> requestedTechnologies =
                mRangingEngine.getRequestedTechnologies();
        Log.v(TAG, "Requesting technologies " + requestedTechnologies
                + " based on local capabilities");

        byte[] message = CapabilityRequestMessage.builder()
                .setHeader(OobHeader.builder()
                        .setMessageType(MessageType.CAPABILITY_REQUEST)
                        .setVersion(OobHeader.OobVersion.CURRENT)
                        .build())
                .setRequestedRangingTechnologies(mRangingEngine.getRequestedTechnologies())
                .build()
                .toBytes();

        mOobConnections.forEach((peer, connection) ->
                pendingResponses.put(
                        peer,
                        connection
                                .sendData(message)
                                .transformAsync((unused) -> connection.receiveData(), mOobExecutor)
                                .withTimeout(MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS,
                                        mOobExecutor)));

        return FluentFuture.from(
                Futures.whenAllComplete(pendingResponses.values())
                        .call(() -> {
                            handleCapabilitiesResponses(handleFailedFutures(pendingResponses));
                            return null;
                        }, mOobExecutor));
    }

    private FluentFuture<ImmutableSet<TechnologyConfig>> sendSetConfigMessages()
            throws RangingEngine.ConfigSelectionException {

        RangingEngine.SelectedConfig configs = mRangingEngine.selectConfigs();

        Map<OobHandle, FluentFuture<Void>> pendingSends = new HashMap<>(mOobConnections.size());
        mOobConnections.forEach((oobHandle, connection) -> {
            SetConfigurationMessage message = configs
                    .getPeerConfigMessages()
                    .get(oobHandle.getRangingDevice());

            if (message == null) {
                pendingSends.put(
                        oobHandle,
                        FluentFuture.from(Futures.immediateFailedFuture(
                                new RangingEngine.ConfigSelectionException(
                                        "No set configuration message was selected to send on "
                                                + "handle " + oobHandle,
                                        InternalReason.NO_PEERS_FOUND))));
            } else {
                pendingSends.put(
                        oobHandle,
                        mOobConnections.get(oobHandle).sendData(message.toBytes()));
            }
        });

        return FluentFuture.from(
                Futures.whenAllComplete(pendingSends.values())
                        .call(() -> {
                            handleFailedFutures(pendingSends);
                            return configs.getLocalConfigs();
                        }, mOobExecutor));
    }

    private void handleCapabilitiesResponses(
            Map<OobHandle, byte[]> responses
    ) throws RangingEngine.ConfigSelectionException {
        Log.i(TAG, "Received capabilities response messages");

        for (OobHandle oobHandle : responses.keySet()) {
            CapabilityResponseMessage response =
                    CapabilityResponseMessage.parseBytes(responses.get(oobHandle));
            mRangingEngine.addPeerCapabilities(oobHandle.getRangingDevice(), response);
        }
    }

    private <T> Map<OobHandle, T> handleFailedFutures(Map<OobHandle, FluentFuture<T>> futures) {
        Map<OobHandle, T> unwrapped = new HashMap<>(futures.size());
        for (OobHandle handle : futures.keySet()) {
            try {
                unwrapped.put(handle, futures.get(handle).get());
            } catch (Exception e) {
                Log.w(TAG, "Peer " + handle + " dropped from ongoing OOB", e);
                mOobConnections.remove(handle).close();
            }
        }
        if (mOobConnections.isEmpty()) {
            throw new IllegalStateException("All peers dropped from OOB");
        }
        return unwrapped;
    }

    @Override
    public void close() {
        mOobConnections.values().forEach(OobController.OobConnection::close);
        mOobConnections.clear();
    }
}
