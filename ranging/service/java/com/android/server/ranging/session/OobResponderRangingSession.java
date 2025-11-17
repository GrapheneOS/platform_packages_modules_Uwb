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

import static com.android.server.ranging.common.RangingUtils.technologyBitset;

import android.content.AttributionSource;
import android.ranging.RangingConfig;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobResponderRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager.SessionListener;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.OobController.ConnectionClosedException;
import com.android.server.ranging.oob.OobResponderProtocol;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.ConfigurationRequest;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.StopRequest;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private OobResponderProtocol mProtocol;
    private AtomicBoolean mKeepAliveFlag;

    public OobResponderRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull SessionConfig config,
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
        mProtocol = new OobResponderProtocol(mInjector);
        mKeepAliveFlag = new AtomicBoolean(true);

        mOobConnection.receiveData().addCallback(mOobConnectionListener, mOobExecutor);
        mSessionListener.onSessionOpened();
    }

    @Override
    public void stop() {
        stopSessionForReason(InternalReason.LOCAL_REQUEST);
    }

    @Override
    protected void onSessionClosed(@InternalReason int reason) {
        if (!mKeepAliveFlag.getAndSet(true)) {
            mSessionListener.onSessionClosed(reason);
        }
    }

    private class OobConnectionListener implements FutureCallback<byte[]> {
        @Override
        public void onSuccess(byte[] data) {
            OobMessage message = OobMessage.fromBytes(data);
            Log.v(TAG, "Received " + message);
            (switch (message) {
                case CapabilitiesRequest request ->
                        sendCapabilityResponse(request).transformAsync(
                                unused -> mOobConnection.receiveData(), mOobExecutor);
                case ConfigurationRequest request -> {
                    OobResponderRangingSession.super.start(
                            mProtocol.getConfigurations(mPeer, request));
                    yield mOobConnection.receiveData();
                }
                case StopRequest request -> {
                    OobResponderRangingSession.super.stopTechnologies(
                            technologyBitset(request.getTechnologiesToStop()),
                            InternalReason.REMOTE_REQUEST);
                    yield mOobConnection.receiveData();
                }
                default -> {
                    Log.e(TAG, "Received unexpected OOB message with id " + message.getId());
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

    private FluentFuture<Void> sendCapabilityResponse(CapabilitiesRequest request) {
        return mOobConnection.sendData(mProtocol.getCapabilitiesResponse(request).toBytes());
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
