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
import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionHandle;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager.SessionListener;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.blerssi.BleRssiOobCapabilities;
import com.android.server.ranging.cs.CsOobCapabilities;
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.OobController.ConnectionClosedException;
import com.android.server.ranging.oob.OobHeader;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.StopRangingMessage;
import com.android.server.ranging.rtt.RttOobCapabilities;
import com.android.server.ranging.rtt.RttOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OOB responder session. For this session, the callbacks have different semantics:
 * <ul>
 *     <li>{@link SessionListener#onSessionOpened()} indicates that the session has
 *     started listening for OOB messages. This will be called before ranging actually starts.</li>
 *     <li>{@link SessionListener#onSessionClosed(int)} indicates that the session
 *     is no longer listening for OOB messages. This will only be called if the session is stopped
 *     explicitly via {@code stop()} or if the underlying OOB transport closes.</li>
 * </ul>
 *
 */
public class OobResponderRangingSession extends BaseRangingSession implements RangingSession {
    private static final String TAG = OobResponderRangingSession.class.getSimpleName();

    private final ScheduledExecutorService mOobExecutor;
    private final OobConnectionListener mOobConnectionListener;

    private OobHandle mPeer;
    private OobController.OobConnection mOobConnection;
    private AtomicReference<ImmutableSet<TechnologyConfig>> mRestartingWithConfigs;
    private AtomicBoolean mKeepAliveFlag;
    private UwbAddress mMyUwbAddress;

    public OobResponderRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull RangingSessionConfig config,
            @NonNull SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService oobExecutor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, adapterExecutor);
        mOobExecutor = oobExecutor;
        mOobConnectionListener = new OobConnectionListener();
    }

    @Override
    public void start(@NonNull RangingConfig rangingConfig) {
        if (!(rangingConfig instanceof OobResponderRangingConfig config)) {
            Log.e(TAG, "Unexpected configuration object for oob responder session "
                    + rangingConfig.getClass());
            mSessionListener.onSessionClosed(InternalReason.INTERNAL_ERROR);
            return;
        }

        mPeer = new OobHandle(mSessionHandle, config.getDeviceHandle().getRangingDevice());
        mOobConnection = mInjector.getOobController().createConnection(mPeer);
        mRestartingWithConfigs = new AtomicReference<>(null);
        mKeepAliveFlag = new AtomicBoolean(true);
        mMyUwbAddress = UwbAddress.createRandomShortAddress();

        mOobConnection.receiveData().addCallback(mOobConnectionListener, mOobExecutor);
        mSessionListener.onSessionOpened();
    }

    @Override
    public void stop() {
        stopSessionForReason(InternalReason.LOCAL_REQUEST);
    }


    @Override
    protected void onTechnologyStopped(
            @NonNull RangingTechnology technology, @NonNull Set<RangingDevice> peers,
            @InternalReason int reason
    ) {
        // Don't send onTechnologyStopped if the technologies stopped due to a restart.
        if (mRestartingWithConfigs.get() == null) {
            mSessionListener.onTechnologyStopped(technology, peers, reason);
        }
    }

    @Override
    protected void onSessionClosed(@InternalReason int reason) {
        ImmutableSet<TechnologyConfig> configsForRestart = mRestartingWithConfigs.getAndSet(null);
        if (configsForRestart != null) {
            super.start(configsForRestart);
        } else if (!mKeepAliveFlag.getAndSet(true)) {
            mSessionListener.onSessionClosed(reason);
        }
    }

    private class OobConnectionListener implements FutureCallback<byte[]> {
        @Override
        public void onSuccess(byte[] data) {
            OobHeader header = OobHeader.parseBytes(data);
            (switch (header.getMessageType()) {
                case CAPABILITY_REQUEST ->
                        sendCapabilityResponse(data)
                                .transformAsync(unused ->
                                        mOobConnection.receiveData(), mOobExecutor);
                case SET_CONFIGURATION -> {
                    handleSetConfig(data);
                    yield mOobConnection.receiveData();
                }
                case STOP_RANGING -> {
                    handleStopRanging(data);
                    yield mOobConnection.receiveData();
                }
                default -> {
                    Log.e(TAG, "Received unexpected OOB message with type "
                            + header.getMessageType());
                    yield mOobConnection.receiveData();
                }
            }).addCallback(mOobConnectionListener, mOobExecutor);
        }

        @Override
        public void onFailure(Throwable t) {
            switch (t) {
                case ConnectionClosedException e
                    when e.getReason() == ConnectionClosedException.Reason.REQUESTED ->
                        Log.i(TAG, "No longer listening for OOB messages- OOB connection closed by"
                                + " local request");
                case ConnectionClosedException e -> {
                    Log.w(TAG, "Stopping session due to unexpected OOB connection closure with "
                            + "reason " + e.getReason());
                    stopSessionForReason(InternalReason.NO_PEERS_FOUND);
                }
                default -> {
                    Log.e(TAG, "Stopping session due to OOB connection failure " + t);
                    stopSessionForReason(InternalReason.INTERNAL_ERROR);
                }
            }
        }
    }

    private FluentFuture<Void> sendCapabilityResponse(byte[] data) {
        Log.i(TAG, "Received capabilities request message");

        CapabilityResponseMessage response =
                getCapabilityResponseForRequest(CapabilityRequestMessage.parseBytes(data));

        return mOobConnection.sendData(response.toBytes());
    }

    private void handleSetConfig(byte[] data) {
        Log.i(TAG, "Received set configuration message");

        ImmutableSet.Builder<TechnologyConfig> configs = ImmutableSet.builder();
        SetConfigurationMessage setConfigMessage = SetConfigurationMessage.parseBytes(data);

        Log.v(TAG, "Configured ranging for technologies "
                + setConfigMessage.getRangingTechnologiesSet());
        UwbOobConfig uwbConfig = setConfigMessage.getUwbConfig();
        if (uwbConfig != null) {
            try {
                configs.add(uwbConfig.toTechnologyConfig(mMyUwbAddress, mPeer.getRangingDevice()));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to convert uwb set config message to local uwb config");
            }
        }
        // Skip CS because the CS responder side does not need to be configured.
        RttOobConfig rttOobConfig = setConfigMessage.getRttConfig();
        if (rttOobConfig != null) {
            configs.add(rttOobConfig.toTechnologyConfig(
                    mPeer.getRangingDevice(), DEVICE_ROLE_RESPONDER));
        }

        // TODO: Only start for technologies who have the start ranging immediately
        //  bit set. Otherwise we need to wait for the start ranging message

        ImmutableSet<TechnologyConfig> technologyConfigs = configs.build();
        boolean sessionAlreadyActive = !super.startOrReAttach(technologyConfigs);
        if (sessionAlreadyActive) {
            Log.w(TAG, "Session already exists with active ranging. Restarting it with newly "
                    + "provided config...");
            mRestartingWithConfigs.set(technologyConfigs);
            super.stop(InternalReason.SYSTEM_POLICY);
        }
    }

    private void handleStopRanging(byte[] data) {
        StopRangingMessage message = StopRangingMessage.parseBytes(data);
        OobResponderRangingSession.super
                .stopTechnologies(
                        Set.copyOf(message.getRangingTechnologiesToStop()),
                        InternalReason.REMOTE_REQUEST);
    }

    private CapabilityResponseMessage getCapabilityResponseForRequest(
            CapabilityRequestMessage request) {

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
        if (request.getRequestedRangingTechnologies().contains(RangingTechnology.RSSI)) {
            BleRssiRangingCapabilities bleRssiCapabilities =
                    myCapabilities.getBleRssiCapabilities();
            if (bleRssiCapabilities != null) {
                supportedTechnologies.add(RangingTechnology.RSSI);
                response.setBleRssiCapabilities(
                        BleRssiOobCapabilities.fromRangingCapabilities(bleRssiCapabilities));
            }
        }

        return response
                .setRangingTechnologiesPriority(supportedTechnologies.build())
                .setSupportedRangingTechnologies(supportedTechnologies.build())
                .build();
    }

    @Override
    public void close() {
        mOobConnection.close();
    }

    private void stopSessionForReason(@InternalReason int reason) {
        mKeepAliveFlag.set(false);
        boolean existsAdaptersWithActiveRanging = super.stop(reason);
        // We want to trigger onSessionClosed even if there are no active adapters to close.
        if (!existsAdaptersWithActiveRanging) onSessionClosed(reason);
    }
}
