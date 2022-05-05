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

import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider.DiscoveryAdvertiseCallback;
import com.android.server.uwb.discovery.info.DiscoveryInfo;

import java.util.concurrent.Executor;

/** This service manages the DiscoveryAdvertiseProvider. */
@WorkerThread
public class DiscoveryAdvertiseService {
    private static final String TAG = "DiscoveryAdvertiseService";

    private final DiscoveryAdvertiseProvider mDiscoveryAdvertiseProvider;

    public DiscoveryAdvertiseService(
            AttributionSource attributionSource,
            Context context,
            Executor executor,
            DiscoveryInfo discoveryInfo,
            DiscoveryAdvertiseCallback discoveryAdvertiseCallback)
            throws AssertionError {

        switch (discoveryInfo.transportType) {
            case BLE:
                mDiscoveryAdvertiseProvider = null;
                break;
            default:
                throw new AssertionError(
                        "Failed to create DiscoveryAdvertiseProvider due to invalid transport type:"
                                + " "
                                + discoveryInfo.transportType);
        }
    }

    /**
     * Start discoverying
     * @return indicates if succeefully started.
     */
    public boolean startDiscovery() {
        if (mDiscoveryAdvertiseProvider.isStarted()) {
            Log.i(TAG, "Discovery already started.");
            return false;
        }
        return mDiscoveryAdvertiseProvider.startAdvertise();
    }

    /**
     * Stop discoverying
     * @return indicates if succeefully stopped.
     */
    public boolean stopDiscovery() {
        if (!mDiscoveryAdvertiseProvider.isStarted()) {
            Log.i(TAG, "Discovery already stopped.");
            return false;
        }
        return mDiscoveryAdvertiseProvider.stopAdvertise();
    }
}
