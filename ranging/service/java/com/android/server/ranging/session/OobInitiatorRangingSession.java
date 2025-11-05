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

import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY;

import android.content.AttributionSource;
import android.ranging.RangingCapabilities;
import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.engine.RangingEngine;
import com.android.server.ranging.engine.StaticRangingEngine;
import com.android.server.ranging.oob.OobController.ConnectionClosedException;
import com.android.server.ranging.oob.OobController.OobConnection;
import com.android.server.ranging.oob.OobInitiatorProtocol;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OobInitiatorRangingSession extends BaseRangingSession implements RangingSession {
    private static final String TAG = OobInitiatorRangingSession.class.getSimpleName();

    private static final long MESSAGE_TIMEOUT_MS = 4000;

    private final ScheduledExecutorService mOobExecutor;
    private final ConcurrentHashMap<OobHandle, Peer> mPeers;

    private OobInitiatorRangingConfig mConfig;
    private ConfigurationManager mConfigManager;
    private OobInitiatorProtocol mProtocol;

    private class Peer implements AutoCloseable, RangingEngine.EngineListener {
        final RangingDevice mDevice;
        final OobConnection mConnection;
        final SettableFuture<Void> mOobCompleted;
        /** <b>Invariant</b>: Non-null after receiving capabilities response */
        RangingEngine mEngine;

        Peer(OobConnection connection) {
            mDevice = connection.getHandle().getRangingDevice();
            mConnection = connection;
            mOobCompleted = SettableFuture.create();
        }

        @Override
        public synchronized void startTechnologies(Set<RangingTechnology> technologies) {
            ImmutableSet<TechnologyConfig> local;
            ImmutableSet<Configuration> remote;
            try {
                local = mConfigManager.getLocalConfigs(technologies.stream().collect(
                        Collectors.toMap(
                                Function.identity(),
                                unused -> Set.of(mDevice))));
                remote = mConfigManager.getRemoteConfigs(mDevice, technologies);
            } catch (ConfigSelectionException e) {
                Log.w(TAG, "RangingEngine wanted " + technologies + " on " + mDevice + " but we "
                        + "failed to agree on a configuration");
                return;
            }
            ConfigurationRequest request = mProtocol
                    .getConfigurationRequest(mConnection.getHandle(), remote);
            var unused = mConnection.sendData(request.toBytes())
                    .transform(unused1 -> {
                        OobInitiatorRangingSession.super.start(local);
                        return null;
                    }, mOobExecutor);
        }

        @Override
        public synchronized void stopTechnologies(Set<RangingTechnology> technologies) {
            var unused = sendStopRangingMessage(mConnection.getHandle(), technologies)
                    .transform(unused1 -> {
                        OobInitiatorRangingSession.super.stopTechnologies(
                                technologies, InternalReason.LOCAL_REQUEST);
                        return null;
                    }, mOobExecutor);
        }

        @Override
        public void close() {
            mConnection.close();
            mOobCompleted.setException(new ConnectionClosedException(
                    ConnectionClosedException.Reason.REQUESTED));
        }
    }

    public OobInitiatorRangingSession(
            @NonNull AttributionSource attributionSource, @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector, @NonNull SessionConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService oobExecutor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, adapterExecutor);
        mOobExecutor = oobExecutor;
        mPeers = new ConcurrentHashMap<>();
    }

    @Override
    public void start(@NonNull RangingConfig rangingConfig) {
        if (!(rangingConfig instanceof OobInitiatorRangingConfig config)) {
            Log.e(TAG, "Unexpected configuration object for oob initiator session "
                    + rangingConfig.getClass());
            mSessionListener.onSessionClosed(InternalReason.INTERNAL_ERROR);
            return;
        }
        if (!mInjector.isLocalDeviceCapableOfConfig(mSessionConfig, config)) {
            Log.e(TAG, "Provided config incompatible with local capabilities");
            mSessionListener.onSessionClosed(InternalReason.UNSUPPORTED);
            return;
        }

        for (DeviceHandle deviceHandle : config.getDeviceHandles()) {
            OobHandle handle = new OobHandle(mSessionHandle, deviceHandle.getRangingDevice());
            mPeers.put(
                    handle,
                    new Peer(mInjector.getOobController().createConnection(handle)));
        }

        mConfig = config;
        mConfigManager = new ConfigurationManager(
                mSessionHandle, mSessionConfig, config, mInjector);
        mProtocol = new OobInitiatorProtocol(mInjector);

        sendCapabilityRequest(mProtocol.getCapabilitiesRequest(getTechnologiesToRequest()))
                .transformAsync(this::sendConfigurationRequest, mOobExecutor)
                .addCallback(new FutureCallback<>() {
                    @Override
                    public void onSuccess(ImmutableSet<TechnologyConfig> localConfigs) {
                        // TODO: Send start ranging message to peers who don't have all active
                        //  technologies in their start ranging list
                        OobInitiatorRangingSession.super.start(localConfigs);
                        mPeers.values().forEach(peer -> peer.mEngine.start(localConfigs));
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "Oob failed: ", t);
                        switch (t) {
                            case ConfigSelectionException e ->
                                    mSessionListener.onSessionClosed(e.getReason());
                            case ConnectionClosedException unused ->
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
        Log.v(TAG, "Sending stop requests to " + mPeers.keySet());

        // Only send stop request to peers that responded to our capabilities request.
        List<FluentFuture<Void>> pendingSends = mPeers.keySet().stream()
                .map(peer -> sendStopRangingMessage(
                        peer,
                        getTechnologiesUsedByPeer(peer.getRangingDevice())))
                .toList();
        var unused = Futures.whenAllComplete(pendingSends)
                .run(OobInitiatorRangingSession.super::stop, mOobExecutor);
    }

    private FluentFuture<Map<OobHandle, byte[]>> sendCapabilityRequest(
            byte[] request
    ) {
        Map<OobHandle, FluentFuture<byte[]>> pendingResponses = new HashMap<>();
        mPeers.forEach((handle, peer) -> pendingResponses.put(
                handle,
                peer.mConnection
                        .sendData(request)
                        .transformAsync((unused) -> peer.mConnection.receiveData(), mOobExecutor)
                        .withTimeout(MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS, mOobExecutor)));

        return FluentFuture.from(
                Futures.whenAllComplete(pendingResponses.values())
                        .call(() -> handleFailedFutures(pendingResponses), mOobExecutor));
    }

    private FluentFuture<ImmutableSet<TechnologyConfig>> sendConfigurationRequest(
            Map<OobHandle, byte[]> responses
    ) throws ConfigSelectionException {
        for (OobHandle handle : responses.keySet()) {
            Map<Technology, Capabilities> capabilities =
                    mProtocol.getCapabilitiesFromResponse(handle, responses.get(handle));
            mConfigManager.addPeerCapabilities(handle.getRangingDevice(), capabilities);
            mPeers.get(handle).mEngine =
                    new StaticRangingEngine(capabilities.keySet(), mConfig, mInjector);
        }

        Map<RangingTechnology, Set<RangingDevice>> peersByTechnology =
                new EnumMap<>(RangingTechnology.class);
        Map<OobHandle, FluentFuture<Void>> pendingSends = new HashMap<>(mPeers.size());
        for (OobHandle handle : mPeers.keySet()) {
            Peer peer = mPeers.get(handle);
            Set<RangingTechnology> starting = peer.mEngine.getTechnologiesToStart();

            ConfigurationRequest request = mProtocol.getConfigurationRequest(
                    handle,
                    mConfigManager.getRemoteConfigs(handle.getRangingDevice(), starting));
            pendingSends.put(
                    handle,
                    peer.mConnection.sendData(request.toBytes())
                            .transformAsync(unused -> {
                                peer.mOobCompleted.set(null);
                                return Futures.immediateFuture(null);
                            }, mOobExecutor));

            starting.forEach(technology -> peersByTechnology
                    .computeIfAbsent(technology, unused -> new HashSet<>())
                    .add(handle.getRangingDevice()));
        }

        return FluentFuture.from(
                Futures.whenAllComplete(pendingSends.values())
                        .call(() -> {
                            handleFailedFutures(pendingSends);
                            return mConfigManager.getLocalConfigs(peersByTechnology);
                        }, mOobExecutor));
    }

    private FluentFuture<Void> sendStopRangingMessage(
            OobHandle handle, Set<RangingTechnology> technologies
    ) {
        Peer peer = mPeers.get(handle);
        FluentFuture<Void> pendingSend = FluentFuture.from(peer.mOobCompleted)
                .transformAsync(unused -> {
                    byte[] request = mProtocol.getStopRequest(handle, technologies);
                    return peer.mConnection.sendData(request);
                }, mOobExecutor);

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

    private EnumSet<RangingTechnology> getTechnologiesToRequest() {
        List<RangingTechnology> technologyRanking = mInjector.getTechnologyRanking();
        if (mConfig.getRangingMode() == RANGING_MODE_HIGH_ACCURACY
                && shouldRequest(technologyRanking.getFirst())
        ) {
            return EnumSet.of(technologyRanking.getFirst());
        } else {
            return technologyRanking.stream()
                    .filter(this::shouldRequest)
                    .collect(Collectors.toCollection(
                            () -> EnumSet.noneOf(RangingTechnology.class)));
        }
    }

    private boolean shouldRequest(RangingTechnology technology) {
        Set<Integer> technologyFilter = mConfig.getRangingTechnologyFilter();
        if (!technologyFilter.isEmpty() && !technologyFilter.contains(technology.getValue())) {
            return false;
        }

        @RangingCapabilities.RangingTechnologyAvailability int availability = mInjector
                .getCapabilitiesProvider()
                .getCapabilities()
                .getTechnologyAvailability()
                .get(technology.getValue());
        return availability == RangingCapabilities.ENABLED
                || availability == RangingCapabilities.DISABLED_USER;
    }

    private <T> Map<OobHandle, T> handleFailedFutures(Map<OobHandle, FluentFuture<T>> futures) {
        Map<OobHandle, T> unwrapped = new HashMap<>(futures.size());
        for (OobHandle handle : futures.keySet()) {
            try {
                unwrapped.put(handle, futures.get(handle).get());
            } catch (Exception e) {
                Log.w(TAG, "Peer " + handle + " dropped from ongoing OOB", e);
                mPeers.remove(handle).close();
            }
        }
        if (mPeers.isEmpty()) {
            throw new IllegalStateException("All peers dropped from OOB");
        }
        return unwrapped;
    }

    @Override
    public void close() {
        mPeers.values().forEach(Peer::close);
        mPeers.clear();
    }
}
