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
import android.ranging.IOobSendDataListener;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.OobHandle;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;
import android.ranging.params.RawInitiatorRangingParams;
import android.ranging.params.RawRangingDevice;
import android.ranging.params.RawResponderRangingParams;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

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
                mRangingInjector, handle, callbacks, mAdapterExecutor);
        mSessions.put(handle, session);
        session.start(getPeerConfigs(preference));
    }

    public void stopRanging(SessionHandle handle) {
        mSessions.remove(handle).stop();
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
                .setSensorFusionConfig(preference.getSensorFusionParameters())
                .setDataNotificationConfig(preference.getDataNotificationConfig())
                .setAoaNeeded(preference.isAoaNeeded())
                .build();
    }
}
