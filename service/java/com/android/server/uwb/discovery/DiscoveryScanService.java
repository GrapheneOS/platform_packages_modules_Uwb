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

import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryScanCallback;
import com.android.server.uwb.discovery.ble.BleDiscoveryScanProvider;
import com.android.server.uwb.discovery.info.DiscoveryInfo;

import java.util.concurrent.Executor;

/** This service manages the DiscoveryScanProvider. */
@WorkerThread
public class DiscoveryScanService {
    private static final String TAG = "DiscoveryScanService";

    private final DiscoveryScanProvider mDiscoveryScanProvider;

    public DiscoveryScanService(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            DiscoveryInfo discoveryInfo,
            DiscoveryScanCallback discoveryScanCallback)
            throws AssertionError {

        switch (discoveryInfo.transportType) {
            case BLE:
                mDiscoveryScanProvider =
                        new BleDiscoveryScanProvider(
                                attributionSource,
                                context,
                                executor,
                                discoveryInfo.scanInfo,
                                discoveryScanCallback);
                break;
            default:
                throw new AssertionError(
                        "Failed to create DiscoveryScanProvider due to invalid transport type: "
                                + discoveryInfo.transportType);
        }
    }

    /**
     * Start discoverying
     * @return indicates if succeefully started.
     */
    public boolean startDiscovery() {
        if (mDiscoveryScanProvider.isStarted()) {
            Log.i(TAG, "Discovery already started.");
            return false;
        }
        return mDiscoveryScanProvider.startScan();
    }

    /**
     * Stop discoverying
     * @return indicates if succeefully stopped.
     */
    public boolean stopDiscovery() {
        if (!mDiscoveryScanProvider.isStarted()) {
            Log.i(TAG, "Discovery already stopped.");
            return false;
        }
        return mDiscoveryScanProvider.stopScan();
    }
}
