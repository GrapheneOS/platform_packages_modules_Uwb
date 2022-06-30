/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.uwb.discovery;

import android.content.AttributionSource;
import android.content.Context;
import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.TransportClientProvider.TransportClientCallback;
import com.android.server.uwb.discovery.ble.GattTransportClientProvider;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;

import java.util.concurrent.Executor;

/** This service manages the TransportClientProvider. */
@WorkerThread
public class TransportClientService {
    private static final String TAG = TransportClientService.class.getSimpleName();

    private final TransportClientProvider mTransportClientProvider;

    public TransportClientService(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            DiscoveryInfo discoveryInfo,
            TransportClientCallback transportClientCallback)
            throws IllegalArgumentException, AssertionError {

        switch (discoveryInfo.transportType) {
            case BLE:
                if (!discoveryInfo.transportClientInfo.isPresent()) {
                    throw new IllegalArgumentException(
                            "Failed to create GattTransportClientProvider due to empty"
                                    + " transportClientInfo in discoveryInfo:"
                                    + discoveryInfo);
                }
                mTransportClientProvider =
                        new GattTransportClientProvider(
                                attributionSource,
                                context,
                                executor,
                                discoveryInfo.transportClientInfo.get(),
                                transportClientCallback);
                break;
            default:
                throw new AssertionError(
                        "Failed to create TransportClientProvider due to invalid transport type:"
                                + " "
                                + discoveryInfo.transportType);
        }
    }

    /**
     * Start the transport client
     *
     * @return indicates if succeefully started.
     */
    public boolean start() {
        if (mTransportClientProvider.isStarted()) {
            Log.i(TAG, "Transport client already started.");
            return false;
        }
        return mTransportClientProvider.start();
    }

    /**
     * Stop the transport client
     *
     * @return indicates if succeefully stopped.
     */
    public boolean stop() {
        if (!mTransportClientProvider.isStarted()) {
            Log.i(TAG, "Transport client already stopped.");
            return false;
        }
        return mTransportClientProvider.stop();
    }

    /**
     * Send a FiRa connector message to the remote device through the transport client.
     *
     * @param secid destination SECID on remote device.
     * @param message message to be send.
     * @return indicates if succeefully started.
     */
    public boolean sendMessage(int secid, FiraConnectorMessage message) {
        return mTransportClientProvider.sendMessage(secid, message);
    }

    /**
     * Set and sent new FiRa connector capabilites to the remote server device through the transport
     * client.
     *
     * @param capabilities new capabilities.
     * @return indicates if succeefully set.
     */
    public boolean setCapabilites(FiraConnectorCapabilities capabilities) {
        return mTransportClientProvider.setCapabilites(capabilities);
    }
}
