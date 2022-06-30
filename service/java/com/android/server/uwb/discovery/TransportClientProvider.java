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

import androidx.annotation.WorkerThread;

import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.FiraConnectorMessage;

/** Abstract class for Transport Client Provider */
@WorkerThread
public abstract class TransportClientProvider {

    public enum TerminationReason {
        /** Disconnection of the remote GATT service. */
        REMOTE_DISCONNECTED,
        /** remote GATT service discovery failure. */
        SERVICE_DISCOVERY_FAILURE,
        /** Characterstic read failure */
        CHARACTERSTIC_READ_FAILURE,
        /** Characterstic write failure */
        CHARACTERSTIC_WRITE_FAILURE,
        /** Descriptor write failure */
        DESCRIPTOR_WRITE_FAILURE,
    }

    public @interface NotifyCharacteristicReturnValues {}

    /** Callback for listening to transport client events. */
    @WorkerThread
    public interface TransportClientCallback {

        /** Called when the client started processing. */
        void onProcessingStarted();

        /** Called when the client stopped processing. */
        void onProcessingStopped();

        /**
         * Called when the client terminated the connection due to an unrecoverable errors.
         *
         * @param reason indicates the termination reason.
         */
        void onTerminated(TerminationReason reason);

        /**
         * Called when the client received a new FiRa connector message from the remote device.
         *
         * @param secid destination SECID on this device.
         * @param message FiRa connector message.
         */
        void onMessageReceived(int secid, FiraConnectorMessage message);
    }

    /* Indicates whether the client has started.
     */
    protected boolean mStarted = false;

    /**
     * Checks if the client has started.
     *
     * @return indicates if the client has started.
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Starts the transport client.
     *
     * @return indicates if succeefully started.
     */
    public abstract boolean start();

    /**
     * Stops the transport client.
     *
     * @return indicates if succeefully stopped.
     */
    public abstract boolean stop();

    /**
     * Send a FiRa connector message to the remote device.
     *
     * @param secid destination SECID on remote device.
     * @param message message to be send.
     * @return indicates if succeefully started.
     */
    public abstract boolean sendMessage(int secid, FiraConnectorMessage message);

    /**
     * Set and sent new FiRa connector capabilites.
     *
     * @param capabilities new capabilities.
     * @return indicates if succeefully set.
     */
    public abstract boolean setCapabilites(FiraConnectorCapabilities capabilities);
}
