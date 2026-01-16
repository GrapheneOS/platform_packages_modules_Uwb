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

import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_AUTO;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY;

import static com.android.server.ranging.oob.OobUtils.fromOobMotion;
import static com.android.server.ranging.common.RangingUtils.macAddressToString;

import android.bluetooth.BluetoothDevice;
import android.content.AttributionSource;
import android.ranging.MotionState;
import android.ranging.RangingCapabilities;
import android.ranging.RangingConfig;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.blerssi.BleRssiConfigSelector;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.cs.CsConfigSelector;
import com.android.server.ranging.engine.RangingEngine;
import com.android.server.ranging.engine.StaticRangingEngine;
import com.android.server.ranging.engine.UwbBreakBeforeMakeEngine;
import com.android.server.ranging.engine.UwbMakeBeforeBreakEngine;
import com.android.server.ranging.oob.OobController.ConnectionClosedException;
import com.android.server.ranging.oob.OobController.OobConnection;
import com.android.server.ranging.oob.OobInitiatorProtocol;
import com.android.server.ranging.oob.OobInitiatorProtocol.PeerCapabilities;
import com.android.server.ranging.oob.packets.BleCsCapabilities;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.oob.packets.MotionNotification;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.oob.packets.TechnologyTransitioning;
import com.android.server.ranging.rtt.RttConfigSelector;
import com.android.server.ranging.rtt.RttStationConfigSelector;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfigSelector;
import com.android.server.ranging.wifipd.WifiPdConfigSelector;

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
    private final ConcurrentHashMap<RangingDevice, Peer> mPeers;

    private OobInitiatorRangingConfig mConfig;
    private ConfigurationManager mConfigManager;
    private OobInitiatorProtocol mProtocol;

    private class Peer implements AutoCloseable, RangingEngine.EngineListener {
        final RangingDevice mDevice;
        final OobConnection mConnection;
        final SettableFuture<Void> mOobCompleted;
        /**
         * <b>Invariant</b>: Non-null after
         * {@link Peer#createRangingEngine(TechnologyTransitioning, Map)}
         */
        RangingEngine mEngine;

        Peer(OobConnection connection) {
            mDevice = connection.getHandle().getRangingDevice();
            mConnection = connection;
            mOobCompleted = SettableFuture.create();
        }

        public void createRangingEngine(
                @Nullable TechnologyTransitioning transitioningSupport,
                Map<Technology, Capabilities> capabilities
        ) {

            if (RangingInjector.isFlagEnabled("rangingTechnologyTransitioning")
                    && mConfig.getRangingMode() == RANGING_MODE_AUTO
                    && transitioningSupport != null
                    && capabilities.size() >= 2 && capabilities.containsKey(Technology.Uwb)
            ) {
                RangingTechnology alternate = mInjector.getTechnologyRanking().stream()
                        .filter(technology -> technology != RangingTechnology.UWB
                                && capabilities.containsKey(
                                Technology.fromByte(technology.toByte())))
                        .findFirst()
                        .get();

                mEngine = switch (transitioningSupport) {
                    case TechnologyTransitioning.MakeBeforeBreak unused -> {
                        Log.i(TAG, "Using make-before-break transitioning UWB <-> " + alternate);
                        yield new UwbMakeBeforeBreakEngine(
                                RangingTechnology.fromByte(alternate.toByte()), this,
                                mOobExecutor, mInjector);
                    }
                    case TechnologyTransitioning.NotSupported unused -> {
                        Log.i(TAG, "Using break-before-make transitioning UWB <-> " + alternate);
                        yield new UwbBreakBeforeMakeEngine(
                                RangingTechnology.fromByte(alternate.toByte()), this,
                                mOobExecutor, mInjector);
                    }
                    case TechnologyTransitioning.Reserved other -> throw new IllegalStateException(
                            "Unexpected technology transitioning " + other);
                };
            } else {
                mEngine = new StaticRangingEngine(capabilities.keySet(), mConfig, mInjector);
            }
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
            ConfigurationRequest request = mProtocol.getConfigurationRequest(mDevice, remote);
            Log.v(TAG, "Sending " + request);
            var unused = mConnection.sendData(request.toBytes())
                    .transform(unused1 -> {
                        OobInitiatorRangingSession.super.start(local);
                        return null;
                    }, mOobExecutor);
        }

        @Override
        public synchronized void stopTechnologies(Set<RangingTechnology> technologies) {
            var unused = sendStopRangingMessage(this, technologies)
                    .transform(unused1 -> {
                        OobInitiatorRangingSession.super.stopTechnologies(
                                technologies, InternalReason.ENGINE_REQUEST);
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

        for (DeviceHandle deviceHandle : config.getDeviceHandles()) {
            OobHandle handle = new OobHandle(mSessionHandle, deviceHandle.getRangingDevice());
            mPeers.put(
                    deviceHandle.getRangingDevice(),
                    new Peer(mInjector.getOobController().createConnection(handle)));
        }

        mConfig = config;
        mConfigManager = new ConfigurationManager(
                mSessionHandle, mSessionConfig, config, mInjector);
        mProtocol = new OobInitiatorProtocol(mInjector);

        sendCapabilityRequest()
                .transformAsync(this::sendConfigurationRequest, mOobExecutor)
                .addCallback(new FutureCallback<>() {
                    @Override
                    public void onSuccess(ImmutableSet<TechnologyConfig> localConfigs) {
                        // TODO: Send start ranging message to peers who don't have all active
                        //  technologies in their start ranging list

                        // Start engine before calling session start to ensure the necessary
                        // initialization completed to interact with technology changes.
                        mPeers.values().forEach(peer -> peer.mEngine.start(localConfigs));
                        OobInitiatorRangingSession.super.start(localConfigs);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.w(TAG, "Oob failed: ", t);
                        switch (t) {
                            case ConfigSelectionException e -> mSessionListener.onSessionClosed(
                                    e.getReason());
                            case ConnectionClosedException unused ->
                                    mSessionListener.onSessionClosed(InternalReason.NO_PEERS_FOUND);
                            case TimeoutException unused -> mSessionListener.onSessionClosed(
                                    InternalReason.NO_PEERS_FOUND);
                            default -> mSessionListener.onSessionClosed(
                                    InternalReason.INTERNAL_ERROR);
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
                        mPeers.get(peer),
                        getTechnologiesUsedByPeer(peer)))
                .toList();
        var unused = Futures.whenAllComplete(pendingSends)
                .run(OobInitiatorRangingSession.super::stop, mOobExecutor);
    }

    @Override
    public DeviceType getPeerType(RangingDevice peer) {
        return mProtocol.getPeerType(peer);
    }

    private FluentFuture<Map<RangingDevice, byte[]>> sendCapabilityRequest() {
        EnumSet<RangingTechnology> technologies = getTechnologiesToRequest();
        if (technologies.isEmpty()) {
            return FluentFuture.from(Futures.immediateFailedFuture(new ConfigSelectionException(
                    "No technologies to request", InternalReason.UNSUPPORTED)));
        }
        byte[] request = mProtocol.getCapabilitiesRequest(technologies);

        Map<RangingDevice, FluentFuture<byte[]>> pendingResponses = new HashMap<>();
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
            Map<RangingDevice, byte[]> responses
    ) throws ConfigSelectionException {
        for (RangingDevice peer : responses.keySet()) {
            PeerCapabilities capabilities =
                    mProtocol.getCapabilitiesFromResponse(peer, responses.get(peer));
            capabilities = filterPeerBtCapabilities(peer, capabilities);
            mConfigManager
                    .addPeerCapabilities(peer, capabilities.byTechnology(),
                            mProtocol.getPeerType(peer));
            mPeers.get(peer)
                    .createRangingEngine(capabilities.transitioning(), capabilities.byTechnology());
        }

        Map<RangingTechnology, Set<RangingDevice>> peersByTechnology =
                new EnumMap<>(RangingTechnology.class);
        Map<RangingDevice, FluentFuture<Void>> pendingSends = new HashMap<>(mPeers.size());
        for (RangingDevice peerDevice : mPeers.keySet()) {
            Peer peer = mPeers.get(peerDevice);
            Set<RangingTechnology> starting = peer.mEngine.getTechnologiesToStart();

            ConfigurationRequest request = mProtocol.getConfigurationRequest(
                    peerDevice,
                    mConfigManager.getRemoteConfigs(peerDevice, starting));
            pendingSends.put(
                    peerDevice,
                    peer.mConnection.sendData(request.toBytes())
                            .transformAsync(unused -> {
                                peer.mOobCompleted.set(null);
                                // TODO: Exchange OOB capability and enabling param.
                                peer.mConnection.receiveData().addCallback(
                                        new OobConnectionListener(peer), mOobExecutor);
                                return Futures.immediateFuture(null);
                            }, mOobExecutor));

            starting.forEach(technology -> peersByTechnology
                    .computeIfAbsent(technology, unused -> new HashSet<>())
                    .add(peerDevice));
        }

        return FluentFuture.from(
                Futures.whenAllComplete(pendingSends.values())
                        .call(() -> {
                            handleFailedFutures(pendingSends);
                            return mConfigManager.getLocalConfigs(peersByTechnology);
                        }, mOobExecutor));
    }

    // Remove BleCsCapabilities for devices that are not bonded.
    private PeerCapabilities filterPeerBtCapabilities(
            RangingDevice peer, PeerCapabilities capabilities) {
        Map<Technology, Capabilities> filteredCapabilities = new HashMap<>(
                capabilities.byTechnology());
        if (filteredCapabilities.containsKey(Technology.BleCs)) {

            BluetoothDevice peerBluetoothDevice = null;
            if (RangingInjector.isFlagEnabled("rangingStackUpdates26Q2")) {
                peerBluetoothDevice = mConfig.getDeviceHandles().stream().filter(dh -> {
                    return dh.getRangingDevice().equals(peer);
                }).findFirst().map(DeviceHandle::getBluetoothDevice).orElse(null);
            }
            if (peerBluetoothDevice != null) {
                Log.v(TAG, "Checking BLE bond based on the provided BluetoothDevice");
            }
            BleCsCapabilities bleCsCapabilities = (BleCsCapabilities) filteredCapabilities.get(
                    Technology.BleCs);

            if (!mInjector.isRemoteDeviceBluetoothBonded(
                    macAddressToString(bleCsCapabilities.getAddress()))
                    && !mInjector.isRemoteDeviceBluetoothBonded(peerBluetoothDevice)) {
                Log.v(TAG, "Skipping " + Technology.BleCs
                        + " because no Bluetooth bond exists with peer " + peer);
                filteredCapabilities.remove(Technology.BleCs);
            }
            // If BleCs is present, remove BleRssi
            filteredCapabilities.remove(Technology.BleRssi);
        }
        return new PeerCapabilities(capabilities.transitioning(), filteredCapabilities);
    }

    private FluentFuture<Void> sendStopRangingMessage(
            Peer peer, Set<RangingTechnology> technologies
    ) {
        FluentFuture<Void> pendingSend = FluentFuture.from(peer.mOobCompleted)
                .transformAsync(unused -> {
                    byte[] request = mProtocol.getStopRequest(peer.mDevice, technologies);
                    return peer.mConnection.sendData(request);
                }, mOobExecutor);

        pendingSend.addCallback(new FutureCallback<>() {
            @Override
            public void onSuccess(Void unused) {
                Log.i(TAG, "Sent stop request to " + peer.mDevice);
            }

            @Override
            public void onFailure(Throwable t) {
                Log.w(TAG, "Failed to send stop request to " + peer.mDevice + ": " + t);
            }
        }, mOobExecutor);

        return pendingSend;
    }

    private EnumSet<RangingTechnology> getTechnologiesToRequest() {
        Log.v(TAG, "Capabilities: " + mInjector.getCapabilitiesProvider().getCapabilities());

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

        RangingCapabilities capabilities = mInjector.getCapabilitiesProvider().getCapabilities();

        return switch (technology) {
            case RangingTechnology.UWB -> UwbConfigSelector.isCapableOfConfig(
                    mSessionConfig, mConfig, capabilities.getUwbCapabilities());
            case RangingTechnology.CS -> CsConfigSelector.isCapableOfConfig(
                    mConfig, capabilities.getCsCapabilities());
            case RangingTechnology.RTT -> RttConfigSelector.isCapableOfConfig(
                    mConfig, capabilities.getRttRangingCapabilities());
            case RangingTechnology.RSSI -> BleRssiConfigSelector.isCapableOfConfig(
                    mConfig, capabilities.getBleRssiCapabilities());
            case RangingTechnology.RTT_STATION -> RttStationConfigSelector.isCapableOfConfig(
                    mConfig, capabilities.getRttStationRangingCapabilities());
            case RangingTechnology.WIFI_PD -> WifiPdConfigSelector.isCapableOfConfig(
                    mConfig, capabilities.getWifiPdRangingCapabilities());
        };
    }

    private <T> Map<RangingDevice, T> handleFailedFutures(
            Map<RangingDevice, FluentFuture<T>> futures
    ) {
        Map<RangingDevice, T> unwrapped = new HashMap<>(futures.size());
        for (RangingDevice peer : futures.keySet()) {
            try {
                unwrapped.put(peer, futures.get(peer).get());
            } catch (Exception e) {
                Log.w(TAG, "Peer " + peer + " dropped from ongoing OOB", e);
                mPeers.remove(peer).close();
            }
        }
        if (mPeers.isEmpty()) {
            throw new IllegalStateException("All peers dropped from OOB");
        }
        return unwrapped;
    }

    @Override
    protected void onTechnologyStarted(
            @NonNull RangingTechnology technology, @NonNull Set<RangingDevice> peerDevices
    ) {
        peerDevices.forEach(peerDevice -> {
            Peer peer = mPeers.get(peerDevice);
            if (peer != null) {
                peer.mEngine.onTechnologyStarted(technology);
            }
        });
        super.onTechnologyStarted(technology, peerDevices);
    }

    @Override
    protected void onTechnologyStopped(
            @NonNull RangingTechnology technology, @NonNull Set<RangingDevice> peerDevices,
            @InternalReason int reason
    ) {
        peerDevices.forEach(peerDevice -> {
            Peer peer = mPeers.get(peerDevice);
            if (peer != null) {
                peer.mEngine.onTechnologyStopped(technology, reason);
            }
        });
        super.onTechnologyStopped(technology, peerDevices, reason);
    }

    @Override
    protected void onResults(@NonNull RangingDevice peerDevice, @NonNull RangingData data) {
        synchronized (mPeers) {
            Peer peer = mPeers.get(peerDevice);
            if (peer != null) {
                peer.mEngine.onData(data);
            }
        }
        super.onResults(peerDevice, data);
    }

    @Override
    public void close() {
        mPeers.values().forEach(Peer::close);
        mPeers.clear();
    }

    // A Listener to handle async message from responder.
    private class OobConnectionListener implements FutureCallback<byte[]> {
        private final Peer mPeer;

        OobConnectionListener(Peer peer) {
            mPeer = peer;
        }

        @Override
        public void onSuccess(byte[] data) {
            if (!mPeers.containsKey(mPeer.mDevice)) {
                Log.d(TAG, "Peer " + mPeer.mDevice
                        + " removed or session closed. Stopping read loop.");
                return;
            }
            OobMessage message = OobMessage.fromBytes(data);
            Log.v(TAG, "Received " + message + " from " + mPeer.mDevice);
            switch (message) {
                case MotionNotification notification -> {
                    reportPeerMotion(
                            mPeer.mDevice,
                            new MotionState(fromOobMotion(notification.getMotion())));
                }
                default -> {
                    Log.w(TAG, "Received unexpected OOB message with id " + message.getId());
                }
            }
            mPeer.mConnection.receiveData().addCallback(this, mOobExecutor);
        }

        @Override
        public void onFailure(Throwable t) {
            if (t instanceof ConnectionClosedException
                    && ((ConnectionClosedException) t).getReason()
                    == ConnectionClosedException.Reason.REQUESTED) {
                Log.i(TAG, "OOB connection with " + mPeer.mDevice + " closed by local request");
                return;
            }
            Log.w(TAG, "OOB connection with " + mPeer.mDevice + " failed", t);
            if (mPeers.remove(mPeer.mDevice) != null) {
                mPeer.close();
            }
            if (mPeers.isEmpty()) {
                mSessionListener.onSessionClosed(InternalReason.NO_PEERS_FOUND);
            }
        }
    }
}
