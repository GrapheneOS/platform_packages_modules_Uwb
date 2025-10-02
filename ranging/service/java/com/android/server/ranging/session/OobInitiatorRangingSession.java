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
import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingEngine;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.OobController.OobConnection;
import com.android.server.ranging.oob.OobInitiatorProtocol;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

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

public class OobInitiatorRangingSession extends BaseRangingSession implements RangingSession {
    private static final String TAG = OobInitiatorRangingSession.class.getSimpleName();

    private static final long MESSAGE_TIMEOUT_MS = 4000;

    private final ScheduledExecutorService mOobExecutor;
    private final ConcurrentHashMap<OobHandle, OobConnection> mOobConnections;
    private final Map<OobHandle, FluentFuture<byte[]>> mPendingResponses;

    private OobInitiatorProtocol mProtocol;

    public OobInitiatorRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull SessionConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService oobExecutor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, adapterExecutor);
        mOobExecutor = oobExecutor;
        mOobConnections = new ConcurrentHashMap<>();
        mPendingResponses = new ConcurrentHashMap<>();
    }

    @Override
    public void start(@NonNull RangingConfig rangingConfig) {
        if (!(rangingConfig instanceof OobInitiatorRangingConfig config)) {
            Log.e(TAG, "Unexpected configuration object for oob initiator session "
                    + rangingConfig.getClass());
            mSessionListener.onSessionClosed(InternalReason.INTERNAL_ERROR);
            return;
        }

        try {
            mProtocol = new OobInitiatorProtocol(mSessionConfig, config, mSessionHandle, mInjector);
        } catch (RangingEngine.ConfigSelectionException e) {
            Log.w(TAG, "Provided config incompatible with local capabilities: ", e);
            mSessionListener.onSessionClosed(InternalReason.UNSUPPORTED);
            return;
        }

        sendCapabilityRequestMessages(config.getDeviceHandles())
                .transformAsync(this::sendSetConfigMessages, mOobExecutor)
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
                                    mSessionListener.onSessionClosed(e.getReason());
                            case OobController.ConnectionClosedException e ->
                                    mSessionListener.onSessionClosed(InternalReason.NO_PEERS_FOUND);
                            case TimeoutException unused ->
                                    mSessionListener.onSessionClosed(InternalReason.NO_PEERS_FOUND);
                            default ->
                                    mSessionListener.onSessionClosed(InternalReason.INTERNAL_ERROR);
                        }
                    }
                }, mOobExecutor);
    }

    @Override
    public void stop() {
        Log.v(TAG, "Sending stop requests to " + mPendingResponses.keySet());

        // Only send stop request to peers that responded to our capabilities request.
        List<FluentFuture<Void>> pendingSends = mPendingResponses.entrySet().stream().map(
                (entry) -> entry.getValue().transformAsync(
                        (unused) -> sendStopRangingMessage(entry.getKey()), mOobExecutor))
                .toList();

        var unused = Futures.whenAllComplete(pendingSends)
                .run(OobInitiatorRangingSession.super::stop, mOobExecutor);
    }

    private FluentFuture<Map<OobHandle, byte[]>> sendCapabilityRequestMessages(
            List<DeviceHandle> deviceHandles
    ) {
        for (DeviceHandle deviceHandle : deviceHandles) {
            OobHandle handle = new OobHandle(mSessionHandle, deviceHandle.getRangingDevice());
            mOobConnections.put(handle, mInjector.getOobController().createConnection(handle));
        }

        byte[] request = mProtocol.getCapabilitiesRequest();
        mOobConnections.forEach((peer, connection) -> mPendingResponses.put(
                peer,
                connection
                        .sendData(request)
                        .transformAsync((unused) -> connection.receiveData(), mOobExecutor)
                        .withTimeout(MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS, mOobExecutor)));

        return FluentFuture.from(
                Futures.whenAllComplete(mPendingResponses.values())
                        .call(() -> handleFailedFutures(mPendingResponses), mOobExecutor));
    }

    private FluentFuture<ImmutableSet<TechnologyConfig>> sendSetConfigMessages(
            Map<OobHandle, byte[]> responses
    ) throws RangingEngine.ConfigSelectionException {
        RangingEngine.SelectedConfig configs = mProtocol.getConfigurations(responses);
        ImmutableSet<TechnologyConfig> localConfigs = configs.getLocalConfigs();
        Map<RangingDevice, ConfigurationRequest> remoteConfigs = configs.getRemoteConfigs();

        Map<OobHandle, FluentFuture<Void>> pendingSends = new HashMap<>(mOobConnections.size());
        mOobConnections.forEach((oobHandle, connection) -> {
            ConfigurationRequest request = remoteConfigs.get(oobHandle.getRangingDevice());

            if (request == null) {
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
                        mOobConnections.get(oobHandle).sendData(request.toBytes()));
            }
        });

        return FluentFuture.from(
                Futures.whenAllComplete(pendingSends.values())
                        .call(() -> {
                            handleFailedFutures(pendingSends);
                            return localConfigs;
                        }, mOobExecutor));
    }

    private FluentFuture<Void> sendStopRangingMessage(OobHandle handle) {
        byte[] request = mProtocol.getStopRequest(handle, OobInitiatorRangingSession.super
                .getTechnologiesUsedByPeer(handle.getRangingDevice()));

        FluentFuture<Void> pendingSend = mOobConnections.get(handle).sendData(request);
        pendingSend.addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(Void unused) {
                Log.i(TAG, "Sent stop request on handle " + handle);
            }

            @Override
            public void onFailure(Throwable t) {
                Log.w(TAG, "Failed to send stop request on handle " + handle + ": " + t);
            }
        }, mOobExecutor);

        return pendingSend;
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
