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

import android.util.Log;

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;

/** Abstract class for Transport Server Provider */
@WorkerThread
public abstract class TransportServerProvider {
    private static final String TAG = TransportServerProvider.class.getSimpleName();

    /** Callback for listening to transport server events. */
    @WorkerThread
    public interface TransportServerCallback {

        /** Called when the server started processing. */
        void onProcessingStarted();

        /** Called when the server stopped processing. */
        void onProcessingStopped();

        /**
         * Called when the server receive new capabilites from the remote device.
         *
         * @param capabilities new capabilities.
         */
        void onCapabilitesUpdated(FiraConnectorCapabilities capabilities);

        /**
         * Called when the server receive a new FiRa connector message from the remote device.
         *
         * @param secid destination SECID on this device.
         * @param message FiRa connector message.
         */
        void onMessageReceived(int secid, FiraConnectorMessage message);
    }

    /* Indicates whether the server has started.
     */
    protected boolean mStarted = false;

    /**
     * Checks if the server has started.
     *
     * @return indicates if the server has started.
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Starts the transport server.
     *
     * @return indicates if successfully started.
     */
    public boolean start() {
        if (isStarted()) {
            Log.i(TAG, "Transport server already started.");
            return false;
        }
        return true;
    }

    /**
     * Stops the transport server.
     *
     * @return indicates if successfully stopped.
     */
    public boolean stop() {
        if (!isStarted()) {
            Log.i(TAG, "Transport server already stopped.");
            return false;
        }
        return true;
    }

    /**
     * Send a FiRa connector message to the remote device through the transport server.
     *
     * @param secid destination SECID on remote device.
     * @param message message to be send.
     * @return indicates if successfully started.
     */
    public abstract boolean sendMessage(int secid, FiraConnectorMessage message);
}
