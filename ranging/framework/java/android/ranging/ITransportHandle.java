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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * TransportHandle is used as the Out-Of-Band (OOB) transport mechanism for GRAPI. In cases where
 * GRAPI is used in a non-bypass mode, the user shall provide an implementation of the
 * TransportHandle, allowing GRAPI to do the necessary OOB communication with a peer device using
 * the provided TransportHandle.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public interface ITransportHandle {

    /** Send data to the peer device via the implemented OOB transport. */
    boolean sendData(byte[] data);

    /** Register to receive updates via the provided callback. */
    void registerReceiveCallback(@NonNull ReceiveCallback callback);

    /** TransportHandle callback. */
    interface ReceiveCallback {
        /** Notifies and provides data received from the peer device. */
        void onReceiveData(byte[] data);

        /**
         * Notifies the receiver that the TransportHandle instance can't be used to receive or send
         * data until {@see onReconnect()} is called.
         */
        void onDisconnect();

        /**
         * Notifies the receiver the TransportHandle instance can be used again to send and receive
         * data. Should only be called if {@see onDisconnect()} preceded it.
         */
        void onReconnect();

        /**
         * Notifies the receiver that the TransportHandle instance can't be used anymore to receive
         * or send data.
         */
        void onClose();
    }

}
