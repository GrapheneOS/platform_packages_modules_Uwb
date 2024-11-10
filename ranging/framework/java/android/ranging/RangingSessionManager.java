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

package android.ranging;

import android.content.AttributionSource;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * @hide
 */
public final class RangingSessionManager extends IRangingCallbacks.Stub {

    private static final String TAG = "RangingSessionManager";
    private static long sSessionIdCounter = 1;
    private final IRangingAdapter mRangingAdapter;
    private final Map<SessionHandle, RangingSession> mSessions = new ConcurrentHashMap<>();
    private boolean mOobListenerRegistered = false;

    public RangingSessionManager(IRangingAdapter rangingAdapter) {
        mRangingAdapter = rangingAdapter;
    }

    /**
     * Lazy registration of Oob data listener.
     */
    public void registerOobSendDataListener() {
        if (!mOobListenerRegistered) {
            try {
                mRangingAdapter.registerOobSendDataListener(new OobSendDataListener());
                mOobListenerRegistered = true;
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to register OobSendDataListener", e);
            }
        }
    }

    RangingSession createRangingSessionInstance(AttributionSource attributionSource,
            RangingSession.Callback callback, Executor executor) {
        SessionHandle sessionHandle = new SessionHandle(sSessionIdCounter++, attributionSource,
                Process.myPid());
        RangingSession rangingSession = new RangingSession(this, attributionSource, sessionHandle,
                mRangingAdapter, callback, executor);
        mSessions.put(sessionHandle, rangingSession);
        return rangingSession;
    }

    @Override
    public void onStarted(SessionHandle sessionHandle, RangingDevice peer, int technology) {
        if (!mSessions.containsKey(sessionHandle)) {
            Log.e(TAG, "SessionHandle not found");
            return;
        }
        mSessions.get(sessionHandle).onRangingStarted(technology);
    }

    @Override
    public void onClosed(SessionHandle sessionHandle, RangingDevice peer, int reason) {
        if (!mSessions.containsKey(sessionHandle)) {
            Log.e(TAG, "SessionHandle not found");
            return;
        }
        mSessions.get(sessionHandle).onRangingClosed(reason);
    }

    @Override
    public void onData(SessionHandle sessionHandle, RangingDevice device, RangingData data) {
        if (!mSessions.containsKey(sessionHandle)) {
            Log.e(TAG, "SessionHandle not found");
            return;
        }
        mSessions.get(sessionHandle).onData(device, data);
    }

    /**
     * Tells the service that OOB data has been received.
     *
     * @param oobHandle uniquely identifiers a session/device pair for OOB communication.
     * @param data      payload
     */
    public void oobDataReceived(OobHandle oobHandle, byte[] data) {
        try {
            mRangingAdapter.oobDataReceived(oobHandle, data);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed mRangingAdapter.oobDataReceived", e);
        }
    }

    /**
     * Tells the service that OOB channel has been reconnected.
     *
     * @param oobHandle uniquely identifiers a session/device pair for OOB communication.
     */
    public void deviceOobReconnected(OobHandle oobHandle) {
        try {
            mRangingAdapter.deviceOobReconnected(oobHandle);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed mRangingAdapter.deviceOobReconnected", e);
        }
    }

    /**
     * Tells the service that OOB channel has been disconnected.
     *
     * @param oobHandle uniquely identifiers a session/device pair for OOB communication.
     */
    public void deviceOobDisconnected(OobHandle oobHandle) {
        try {
            mRangingAdapter.deviceOobDisconnected(oobHandle);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed mRangingAdapter.deviceOobDisconnected", e);
        }

    }

    /**
     * Tells the service that OOB channel has been permanently closed.
     *
     * @param oobHandle uniquely identifiers a session/device pair for OOB communication.
     */
    public void deviceOobClosed(OobHandle oobHandle) {
        try {
            mRangingAdapter.deviceOobClosed(oobHandle);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed mRangingAdapter.deviceOobClosed", e);
        }
    }

    class OobSendDataListener extends IOobSendDataListener.Stub {

        @Override
        public void sendOobData(OobHandle oobHandle, byte[] data) throws RemoteException {
            SessionHandle session = oobHandle.getSessionHandle();
            if (!mSessions.containsKey(session)) {
                Log.e(TAG, "SessionHandle not found, session: " + session);
                return;
            }
            mSessions.get(session).sendOobData(oobHandle.getRangingDevice(),
                    data);
        }
    }
}
