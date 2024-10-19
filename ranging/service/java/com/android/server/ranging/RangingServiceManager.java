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

import android.content.AttributionSource;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.ranging.IOobSendDataListener;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.OobHandle;
import android.ranging.RangingCapabilities;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;
import android.util.Log;

import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.StartRangingMessage;
import com.android.server.ranging.oob.StopRangingMessage;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class RangingServiceManager {

    private static final String TAG = RangingServiceManager.class.getSimpleName();
    private final RangingInjector mRangingInjector;
    private final OobController mOobController;

    private final ListeningExecutorService mAdapterExecutor;
    private final ScheduledExecutorService mTimeoutExecutor;

    private final ConcurrentHashMap<SessionHandle, RangingPeer> mSessions;
    private final RemoteCallbackList<IRangingCapabilitiesCallback> mCapabilitiesCallbackList =
            new RemoteCallbackList<>();

    public RangingServiceManager(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        mAdapterExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());
        mTimeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        mSessions = new ConcurrentHashMap<>();
        mOobController = new OobController(new OobDataReceiveCallback());
    }

    public void registerCapabilitiesCallback(IRangingCapabilitiesCallback capabilitiesCallback)
            throws RemoteException {
        synchronized (mCapabilitiesCallbackList) {
            mCapabilitiesCallbackList.register(capabilitiesCallback);
        }
        Log.w(TAG, "Registering ranging capabilities callback");
        getRangingCapabilities(capabilitiesCallback);
    }

    public void unregisterCapabilitiesCallback(IRangingCapabilitiesCallback capabilitiesCallback)
            throws RemoteException {
        synchronized (mCapabilitiesCallbackList) {
            mCapabilitiesCallbackList.unregister(capabilitiesCallback);
        }
    }

    private void getRangingCapabilities(IRangingCapabilitiesCallback callback)
            throws RemoteException {
        RangingCapabilities rangingCapabilities =
                mRangingInjector.getCapabilitiesProvider().getCapabilities();
        callback.onRangingCapabilities(rangingCapabilities);
    }

    public void startRanging(AttributionSource attributionSource, SessionHandle sessionHandle,
            RangingPreference rangingPreference, IRangingCallbacks callbacks) {
        // TODO android.permission.RANGING permission check here
//        Context context = mRangingInjector.getContext()
//                .createContext(new ContextParams
//                        .Builder()
//                        .setNextAttributionSource(attributionSource)
//                        .build());

        if (rangingPreference.getRangingParameters() != null) {
            startPassthroughRanging(sessionHandle, callbacks, rangingPreference);
        } else {
            throw new UnsupportedOperationException("Smart ranging is not yet supported");
        }
    }

    /**
     *
     * @param sessionHandle
     * @param callbacks
     * @param preference
     */
    public void startPassthroughRanging(SessionHandle sessionHandle, IRangingCallbacks callbacks,
            RangingPreference preference) {
        RangingConfig config = new RangingConfig.Builder(preference).build();
        RangingPeer session = new RangingPeer(mRangingInjector.getContext(), mAdapterExecutor,
                mTimeoutExecutor, sessionHandle);
        mSessions.put(sessionHandle, session);
        session.start(config, callbacks);
    }

    public void stopRanging(SessionHandle handle) {
        mSessions.get(handle).stop();
        mSessions.remove(handle);
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
}
