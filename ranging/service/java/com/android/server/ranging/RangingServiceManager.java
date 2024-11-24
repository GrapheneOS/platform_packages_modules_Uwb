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

package com.android.server.ranging;

import static android.ranging.RangingSession.Callback.REASON_LOCAL_REQUEST;
import static android.ranging.RangingSession.Callback.REASON_NO_PEERS_FOUND;
import static android.ranging.RangingSession.Callback.REASON_SYSTEM_POLICY;
import static android.ranging.RangingSession.Callback.REASON_UNKNOWN;
import static android.ranging.RangingSession.Callback.REASON_UNSUPPORTED;

import android.content.AttributionSource;
import android.os.RemoteException;
import android.ranging.IOobSendDataListener;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.OobHandle;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.RangingSession.Callback;
import android.ranging.SessionHandle;
import android.ranging.raw.RawInitiatorRangingParams;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.StartRangingMessage;
import com.android.server.ranging.oob.StopRangingMessage;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class RangingServiceManager {
    private static final String TAG = RangingServiceManager.class.getSimpleName();

    private final RangingInjector mRangingInjector;
    private final OobController mOobController;
    private final ListeningExecutorService mAdapterExecutor;

    private final Map<SessionHandle, RangingSession> mSessions = new ConcurrentHashMap<>();

    public RangingServiceManager(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        mAdapterExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        mOobController = new OobController(new OobDataReceiveCallback());
    }

    public void registerCapabilitiesCallback(IRangingCapabilitiesCallback capabilitiesCallback) {
        Log.w(TAG, "Registering ranging capabilities callback");
        mRangingInjector
                .getCapabilitiesProvider()
                .registerCapabilitiesCallback(capabilitiesCallback);
    }

    public void unregisterCapabilitiesCallback(IRangingCapabilitiesCallback capabilitiesCallback) {
        mRangingInjector
                .getCapabilitiesProvider()
                .unregisterCapabilitiesCallback(capabilitiesCallback);
    }

    public void startRanging(
            AttributionSource attributionSource, SessionHandle handle, RangingPreference preference,
            IRangingCallbacks callbacks
    ) {
        // TODO android.permission.RANGING permission check here
//        Context context = mRangingInjector.getContext()
//                .createContext(new ContextParams
//                        .Builder()
//                        .setNextAttributionSource(attributionSource)
//                        .build());

        RangingSession session = new RangingSession(
                mRangingInjector,
                new SessionListener(handle, callbacks),
                mAdapterExecutor
        );
        mSessions.put(handle, session);
        session.start(getPeerConfigs(preference));
    }

    public void stopRanging(SessionHandle handle) {
        RangingSession session = mSessions.get(handle);
        if (session == null) {
            Log.e(TAG, "stopRanging for nonexistent session");
            return;
        }
        session.stop();
    }

    /**
     * Received data from the peer device.
     *
     * @param oobHandle unique session/device pair identifier.
     * @param data      payload
     */
    public void oobDataReceived(OobHandle oobHandle, byte[] data) {
        mOobController.receiveData(oobHandle, data);
    }

    /**
     * Device disconnected from the OOB channel.
     *
     * @param oobHandle unique session/device pair identifier.
     */
    public void deviceOobDisconnected(OobHandle oobHandle) {
        // Call OobController
    }

    /**
     * Device reconnected to the OOB channel
     *
     * @param oobHandle unique session/device pair identifier.
     */
    public void deviceOobReconnected(OobHandle oobHandle) {
        // Call OobController
    }

    /**
     * Device closed the OOB channel.
     *
     * @param oobHandle unique session/device pair identifier.
     */
    public void deviceOobClosed(OobHandle oobHandle) {
        // Call OobController
    }

    /**
     * Register send data listener.
     *
     * @param oobSendDataListener listener for sending the data via OOB.
     */
    public void registerOobSendDataListener(IOobSendDataListener oobSendDataListener) {
        mOobController.setOobSendDataListener(oobSendDataListener);
    }

    class OobDataReceiveCallback implements OobController.IOobDataReceiveCallback {

        @Override
        public void onCapabilityRequestMessage(OobHandle oobHandle,
                CapabilityRequestMessage message) {
            // Do stuff
        }

        @Override
        public void onCapabilityResponseMessage(OobHandle oobHandle,
                CapabilityResponseMessage message) {
            // Do stuff
        }

        @Override
        public void onConfigurationMessage(OobHandle oobHandle, SetConfigurationMessage message) {
            // Do stuff
        }

        @Override
        public void onStartRangingMessage(OobHandle oobHandle, StartRangingMessage message) {
            // Do stuff
        }

        @Override
        public void onStopRangingMessage(OobHandle oobHandle, StopRangingMessage message) {
            // Do stuff
        }
    }

    /**
     * Listens for peer-specific events within a session and translates them to
     * {@link IRangingCallbacks} calls.
     */
    public class SessionListener {
        private final SessionHandle mSessionHandle;
        private final IRangingCallbacks mRangingCallbacks;
        private final AtomicBoolean mIsSessionStarted;

        SessionListener(SessionHandle sessionHandle, IRangingCallbacks callbacks) {
            mSessionHandle = sessionHandle;
            mRangingCallbacks = callbacks;
            mIsSessionStarted = new AtomicBoolean(false);
        }

        public void onPeerStarted() {
            if (!mIsSessionStarted.getAndSet(true)) {
                try {
                    mRangingCallbacks.onOpened(mSessionHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "onOpened callback failed: " + e);
                }
            }
        }

        /**
         * Signals that ranging with the provided peer has stopped in this session. Called by a
         * {@link RangingPeer} once all technologies used with that peer have stopped.
         */
        public void onPeerStopped(
                @NonNull RangingDevice peer, @RangingAdapter.Callback.StoppedReason int reason
        ) {
            RangingSession session = mSessions.get(mSessionHandle);
            if (session == null) {
                Log.e(TAG, "onPeerStopped for nonexistent session");
                return;
            }

            if (session.removePeerAndCheckEmpty(peer)) {
                try {
                    mSessions.remove(mSessionHandle);
                    // If the session is empty, notify framework callback that it has closed (or
                    // that it failed to open in the first place).
                    if (mIsSessionStarted.get()) {
                        mRangingCallbacks.onClosed(mSessionHandle, convertReason(reason));
                    } else {
                        mRangingCallbacks.onOpenFailed(mSessionHandle, convertReason(reason));
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "onClosed callback failed: " + e);
                }
            }
        }

        public void onTechnologyStarted(
                @NonNull RangingDevice peer, @RangingManager.RangingTechnology int technology
        ) {
            try {
                mRangingCallbacks.onStarted(mSessionHandle, peer, technology);
            } catch (RemoteException e) {
                Log.e(TAG, "onTechnologyStarted callback failed: " + e);
            }
        }

        public void onTechnologyStopped(
                @NonNull RangingDevice peer, @RangingManager.RangingTechnology int technology
        ) {
            try {
                mRangingCallbacks.onStopped(mSessionHandle, peer, technology);
            } catch (RemoteException e) {
                Log.e(TAG, "onTechnologyStopped callback failed: " + e);
            }
        }

        public void onResults(
                @NonNull RangingDevice peer, @NonNull RangingData data
        ) {
            try {
                mRangingCallbacks.onResults(mSessionHandle, peer, data);
            } catch (RemoteException e) {
                Log.e(TAG, "onData callback failed: " + e);
            }
        }

        private @Callback.Reason int convertReason(
                @RangingAdapter.Callback.StoppedReason int reason
        ) {
            switch (reason) {
                case RangingAdapter.Callback.StoppedReason.REQUESTED:
                    return REASON_LOCAL_REQUEST;
                case RangingAdapter.Callback.StoppedReason.FAILED_TO_START:
                    return REASON_UNSUPPORTED;
                case RangingAdapter.Callback.StoppedReason.LOST_CONNECTION:
                    return REASON_NO_PEERS_FOUND;
                case RangingAdapter.Callback.StoppedReason.SYSTEM_POLICY:
                    return REASON_SYSTEM_POLICY;
                default:
                    return REASON_UNKNOWN;
            }
        }
    }

    private @NonNull ImmutableList<RangingPeerConfig> getPeerConfigs(
            @NonNull RangingPreference preference
    ) {
        if (preference.getRangingParameters() instanceof RawInitiatorRangingParams params) {
            return params.getRawRangingDevices()
                    .stream()
                    .map((device) -> getPeerConfig(preference, device))
                    .collect(ImmutableList.toImmutableList());
        } else if (preference.getRangingParameters() instanceof RawResponderRangingParams params) {
            return ImmutableList.of(getPeerConfig(preference, params.getRawRangingDevice()));
        } else {
            // TODO(b/372106978): Negotiate configuration over OOB based on capabilities.
            throw new UnsupportedOperationException("OOB ranging not yet implemented");
        }
    }

    private @NonNull RangingPeerConfig getPeerConfig(
            @NonNull RangingPreference preference, @NonNull RawRangingDevice peer
    ) {
        return new RangingPeerConfig.Builder(peer)
                .setDeviceRole(preference.getDeviceRole())
                .setSensorFusionConfig(
                        preference.getSessionConfiguration().getSensorFusionParameters())
                .setDataNotificationConfig(
                        preference.getSessionConfiguration().getDataNotificationConfig())
                .setAoaNeeded(preference.getSessionConfiguration().isAngleOfArrivalNeeded())
                .build();
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of RangingServiceManager ----");
        for (RangingSession session : mSessions.values()) {
            session.dump(fd, pw, args);
        }
        pw.println("---- Dump of RangingServiceManager ----");
        mRangingInjector.getCapabilitiesProvider().dump(fd, pw, args);
    }
}
