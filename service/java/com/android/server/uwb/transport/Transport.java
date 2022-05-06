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

package com.android.server.uwb.transport;

import androidx.annotation.NonNull;

/**
 * The interface of the physical data communication channel.
 */
public interface Transport {

    /**
     * Send data to the remote peer device.
     */
    void sendData(@NonNull byte[] data, SendingDataCallback sendingDataCallback);

    /**
     * Register the data receiver, only one receiver is kept.
     */
    void registerDataReceiver(DataReceiver dataReceiver);

    /**
     * Unregister the current registered data receiver;
     */
    void unregisterDataReceiver();

    /**
     * The receiver handles the incoming data from the remote peer device.
     */
    interface DataReceiver {
        void onDataReceived(@NonNull byte[] data);
    }

    /**
     * The callback to notify if the data is sent out or not.
     */
    interface SendingDataCallback {
        /**
         * The data is sent out.
         */
        void onSuccess();

        /**
         * The data is failed to send out.
         */
        void onFailure();
    }
}
