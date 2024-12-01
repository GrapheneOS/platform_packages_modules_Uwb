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
import android.os.IBinder;
import android.os.RemoteException;
import android.ranging.IOobSendDataListener;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.OobHandle;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.RangingSession.Callback;
import android.ranging.SessionHandle;
import android.ranging.raw.RawInitiatorRangingParams;
import android.ranging.raw.RawResponderRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.StartRangingMessage;
import com.android.server.ranging.oob.StopRangingMessage;

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
                attributionSource,
                handle,
                mRangingInjector,
                getSessionConfigFromPreference(preference),
                new SessionListener(handle, callbacks),
                mAdapterExecutor
        );
        mSessions.put(handle, session);
        session.start();
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
    public class SessionListener implements IBinder.DeathRecipient {
        private final SessionHandle mSessionHandle;
        private final IRangingCallbacks mRangingCallbacks;
        private final AtomicBoolean mIsSessionStarted;

        SessionListener(SessionHandle sessionHandle, IRangingCallbacks callbacks) {
            mSessionHandle = sessionHandle;
            mRangingCallbacks = callbacks;
            mIsSessionStarted = new AtomicBoolean(false);
            try {
                mRangingCallbacks.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to link to death: " + sessionHandle, e);
                stopRanging(mSessionHandle);
            }
        }

        @Override
        public void binderDied() {
            Log.i(TAG, "binderDied : Stopping session: " + mSessionHandle);
            stopRanging(mSessionHandle);
        }

        public void onTechnologyStarted(
                @NonNull RangingDevice peer, @NonNull RangingTechnology technology
        ) {
            if (!mIsSessionStarted.getAndSet(true)) {
                try {
                    mRangingCallbacks.onOpened(mSessionHandle);
                } catch (RemoteException e) {
                    Log.e(TAG, "onOpened callback failed: " + e);
                }
            }
            try {
                mRangingCallbacks.onStarted(mSessionHandle, peer, technology.getValue());
            } catch (RemoteException e) {
                Log.e(TAG, "onTechnologyStarted callback failed: " + e);
            }
        }

        public void onTechnologyStopped(
                @NonNull RangingDevice peer, @NonNull RangingTechnology technology
        ) {
            try {
                mRangingCallbacks.onStopped(mSessionHandle, peer, technology.getValue());
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

        /**
         * Signals that ranging in the session has stopped. Called by a {@link RangingSession} once
         * all of its constituent technology-specific sessions have stopped.
         */
        public void onSessionStopped(@RangingAdapter.Callback.ClosedReason int reason) {
            mSessions.remove(mSessionHandle);
            if (mIsSessionStarted.get()) {
                try {
                    mRangingCallbacks.onClosed(mSessionHandle, convertReason(reason));
                } catch (RemoteException e) {
                    Log.e(TAG, "onClosed callback failed: " + e);
                }
            } else {
                try {
                    mRangingCallbacks.onOpenFailed(mSessionHandle, convertReason(reason));
                } catch (RemoteException e) {
                    Log.e(TAG, "onOpenFailed callback failed: " + e);
                }
            }
        }

        private @Callback.Reason int convertReason(
                @RangingAdapter.Callback.ClosedReason int reason
        ) {
            switch (reason) {
                case RangingAdapter.Callback.ClosedReason.REQUESTED:
                    return REASON_LOCAL_REQUEST;
                case RangingAdapter.Callback.ClosedReason.FAILED_TO_START:
                    return REASON_UNSUPPORTED;
                case RangingAdapter.Callback.ClosedReason.LOST_CONNECTION:
                    return REASON_NO_PEERS_FOUND;
                case RangingAdapter.Callback.ClosedReason.SYSTEM_POLICY:
                    return REASON_SYSTEM_POLICY;
                default:
                    return REASON_UNKNOWN;
            }
        }
    }

    private @NonNull RangingSessionConfig getSessionConfigFromPreference(
            @NonNull RangingPreference preference) {
        RangingSessionConfig.Builder sessionConfigBuilder = new RangingSessionConfig.Builder()
                .setDeviceRole(preference.getDeviceRole())
                .setSensorFusionConfig(
                        preference.getSessionConfiguration().getSensorFusionParameters())
                .setDataNotificationConfig(
                        preference.getSessionConfiguration().getDataNotificationConfig())
                .setAoaNeeded(preference.getSessionConfiguration().isAngleOfArrivalNeeded());

        if (preference.getRangingParameters() instanceof RawInitiatorRangingParams params) {
            params.getRawRangingDevices().forEach(sessionConfigBuilder::addPeerDeviceParams);
        } else if (preference.getRangingParameters() instanceof RawResponderRangingParams params) {
            sessionConfigBuilder.addPeerDeviceParams(params.getRawRangingDevice());
        } else {
            // TODO(b/372106978): Negotiate configuration over OOB based on capabilities.
            throw new UnsupportedOperationException("OOB ranging not yet implemented");
        }
        return sessionConfigBuilder.build();
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
