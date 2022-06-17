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

import com.android.server.uwb.discovery.TransportServerProvider.TransportServerCallback;
import com.android.server.uwb.discovery.ble.GattTransportServerProvider;
import com.android.server.uwb.discovery.info.DiscoveryInfo;

/** This service manages the TransportServerProvider. */
@WorkerThread
public class TransportServerService {
    private static final String TAG = TransportServerService.class.getSimpleName();

    private final TransportServerProvider mTransportServerProvider;

    public TransportServerService(
            AttributionSource attributionSource,
            Context context,
            DiscoveryInfo discoveryInfo,
            TransportServerCallback transportServerCallback)
            throws AssertionError {

        switch (discoveryInfo.transportType) {
            case BLE:
                mTransportServerProvider =
                        new GattTransportServerProvider(
                                attributionSource, context, transportServerCallback);
                break;
            default:
                throw new AssertionError(
                        "Failed to create TransportServerProvider due to invalid transport type:"
                                + " "
                                + discoveryInfo.transportType);
        }
    }

    /**
     * Start the transport server
     *
     * @return indicates if succeefully started.
     */
    public boolean start() {
        if (mTransportServerProvider.isStarted()) {
            Log.i(TAG, "Transport server already started.");
            return false;
        }
        return mTransportServerProvider.start();
    }

    /**
     * Stop the transport server
     *
     * @return indicates if succeefully stopped.
     */
    public boolean stop() {
        if (!mTransportServerProvider.isStarted()) {
            Log.i(TAG, "Transport server already stopped.");
            return false;
        }
        return mTransportServerProvider.stop();
    }
}
