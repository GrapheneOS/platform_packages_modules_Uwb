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

import static android.ranging.RangingSession.Callback.REASON_UNSUPPORTED;

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
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.MessageType;
import com.android.server.ranging.oob.OobController.ReceivedMessage;
import com.android.server.ranging.oob.OobHeader;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OobInitiatorRangingSession
        extends BaseRangingSession
        implements RangingSession<OobInitiatorRangingConfig> {

    private static final String TAG = OobInitiatorRangingSession.class.getSimpleName();

    private static final long MESSAGE_TIMEOUT_MS = 2000;

    private final Map<OobHandle, FluentFuture<ReceivedMessage>> mPeers =
            new ConcurrentHashMap<>();
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
        try {
            mRangingEngine = new RangingEngine(
                    mConfig.getSessionConfig(), config, mSessionHandle, mInjector);
        } catch (RangingEngine.ConfigSelectionException e) {
            Log.w(TAG, "Provided config incompatible with local capabilities: ", e);
            mSessionListener.onSessionStopped(REASON_UNSUPPORTED);
            return;
        }

        sendCapabilitiesRequest(config.getDeviceHandles());

        ListenableFuture<RangingEngine.SelectedConfig> capabilitiesResponsesFuture =
                Futures.whenAllComplete(mPeers.values())
                        .callAsync(this::selectConfigFromPeerCapabilities, mOobExecutor);

        Futures.addCallback(
                capabilitiesResponsesFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(RangingEngine.SelectedConfig config) {
                        config.getPeerConfigMessages()
                                .forEach((peer, message) ->
                                        mInjector.getOobController().sendMessage(
                                                new OobHandle(mSessionHandle, peer),
                                                message.toBytes()));

                        // TODO: Send start ranging message to peers who don't have all active
                        //  technologies in their start ranging list
                        OobInitiatorRangingSession.super.start(config.getLocalConfigs());
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(TAG, "OOB failed: ", t);
                        mSessionListener.onSessionStopped(REASON_UNSUPPORTED);
                    }
                },
                mOobExecutor);
    }

    private void sendCapabilitiesRequest(List<DeviceHandle> deviceHandles) {
        for (DeviceHandle deviceHandle : deviceHandles) {
            OobHandle handle = new OobHandle(mSessionHandle, deviceHandle.getRangingDevice());
            mPeers.put(
                    handle,
                    mInjector.getOobController()
                            .registerMessageListener(handle)
                            .withTimeout(MESSAGE_TIMEOUT_MS, TimeUnit.MILLISECONDS, mOobExecutor));
        }
        byte[] message = CapabilityRequestMessage.builder()
                .setHeader(OobHeader.builder()
                        .setMessageType(MessageType.CAPABILITY_REQUEST)
                        .setVersion(OobHeader.OobVersion.CURRENT)
                        .build())
                .setRequestedRangingTechnologies(mRangingEngine.getRequestedTechnologies())
                .build()
                .toBytes();

        mPeers.keySet().forEach(
                (handle) -> mInjector.getOobController().sendMessage(handle, message));
    }

    private ListenableFuture<RangingEngine.SelectedConfig> selectConfigFromPeerCapabilities()
            throws RangingEngine.ConfigSelectionException {

        Log.i(TAG, "Received capabilities response message");
        for (OobHandle handle : Set.copyOf(mPeers.keySet())) {
            CapabilityResponseMessage body;
            try {
                byte[] responseData = Futures.getDone(mPeers.get(handle)).asBytes();
                body = CapabilityResponseMessage.parseBytes(responseData);
            } catch (Exception e) {
                Log.w(TAG, "Peer with handle " + handle + " dropped from ongoing OOB: ", e);
                mPeers.remove(handle);
                continue;
            }
            mRangingEngine.addPeerCapabilities(handle.getRangingDevice(), body);
        }

        if (mPeers.isEmpty()) {
            throw new IllegalStateException("All peers dropped from OOB");
        } else {
            return Futures.immediateFuture(mRangingEngine.selectConfigs());
        }
    }
}
