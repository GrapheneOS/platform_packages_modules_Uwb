/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.server.ranging.oob;

import android.os.RemoteException;
import android.ranging.IOobSendDataListener;
import android.ranging.OobHandle;

import java.util.Arrays;

public class OobController {

    private final IOobDataReceiveCallback mDataReceiveCallback;
    private IOobSendDataListener mOobSendDataListener;

    public OobController(IOobDataReceiveCallback callback) {
        mDataReceiveCallback = callback;
    }

    /**
     * Send Capability Request Message to the peer device.
     *
     * @param oobHandle uniquely identifies the session/device pair for OOB communication.
     * @param message   payload.
     * @return false if there was no listener, true otherwise.
     * @throws RemoteException if failed to send the IPC request.
     */
    public boolean sendCapabilityRequest(OobHandle oobHandle, CapabilityRequestMessage message)
            throws RemoteException {
        if (mOobSendDataListener == null) {
            return false;

        }
        mOobSendDataListener.sendOobData(oobHandle, message.toBytes());
        return true;
    }

    /**
     * Send Ranging Configuration Message to the peer device.
     *
     * @param oobHandle uniquely identifies the session/device pair for OOB communication.
     * @param message   payload.
     * @return false if there was no listener, true otherwise.
     * @throws RemoteException if failed to send the IPC request.
     */
    public boolean sendRangingConfiguration(OobHandle oobHandle, SetConfigurationMessage message)
            throws RemoteException {
        if (mOobSendDataListener == null) {
            return false;

        }
        mOobSendDataListener.sendOobData(oobHandle, message.toBytes());
        return true;
    }

    /**
     * Send Start Ranging Message to the peer device.
     *
     * @param oobHandle uniquely identifies the session/device pair for OOB communication.
     * @param message   payload.
     * @return false if there was no listener, true otherwise.
     * @throws RemoteException if failed to send the IPC request.
     */
    public boolean sendStartRanging(OobHandle oobHandle, StartRangingMessage message)
            throws RemoteException {
        if (mOobSendDataListener == null) {
            return false;

        }
        mOobSendDataListener.sendOobData(oobHandle, new byte[]{message.toByte()});
        return true;
    }

    /**
     * Send Stop Ranging Message to the peer device.
     *
     * @param oobHandle uniquely identifies the session/device pair for OOB communication.
     * @param message   payload.
     * @return false if there was no listener, true otherwise.
     * @throws RemoteException if failed to send the IPC request.
     */
    public boolean sendStopRanging(OobHandle oobHandle, StopRangingMessage message)
            throws RemoteException {
        if (mOobSendDataListener == null) {
            return false;

        }
        mOobSendDataListener.sendOobData(oobHandle, new byte[]{message.toByte()});
        return true;
    }

    /**
     * Receive the data from the peer device.
     *
     * @param oobHandle uniquely identifies the session/device pair for OOB communication.
     * @param data      payload.
     */
    public void receiveData(OobHandle oobHandle, byte[] data) {
        MessageType type = MessageType.fromOobData(data);
        byte[] message = Arrays.copyOfRange(data, 1, data.length);
        // TODO: parse vendor data
        switch (type) {
            case RANGING_CAPABILITY_REQUEST -> mDataReceiveCallback.onCapabilityRequestMessage(
                    oobHandle, CapabilityRequestMessage.parseBytes(message));
            case RANGING_CAPABILITY_RESPONSE -> mDataReceiveCallback.onCapabilityResponseMessage(
                    oobHandle, CapabilityResponseMessage.parseBytes(message));
            case RANGING_CONFIGURATION -> mDataReceiveCallback.onConfigurationMessage(oobHandle,
                    SetConfigurationMessage.parseBytes(message));
            case START_RANGING -> mDataReceiveCallback.onStartRangingMessage(oobHandle,
                    StartRangingMessage.parseBytes(message));
            case STOP_RANGING -> mDataReceiveCallback.onStopRangingMessage(oobHandle,
                    StopRangingMessage.parseBytes(message));
        }
    }

    /**
     * Set the OOB Send Data Listener.
     *
     * @param oobSendDataListener listener.
     */
    public void setOobSendDataListener(IOobSendDataListener oobSendDataListener) {
        mOobSendDataListener = oobSendDataListener;
    }

    /**
     * Interface for receiving data callbacks.
     */
    public interface IOobDataReceiveCallback {
        /**
         * On Capability Request Message
         * @param oobHandle uniquely identifies the session/device pair for OOB communication.
         * @param message payload.
         */
        void onCapabilityRequestMessage(OobHandle oobHandle, CapabilityRequestMessage message);

        /**
         * On Capability Response Message
         * @param oobHandle uniquely identifies the session/device pair for OOB communication.
         * @param message payload.
         */
        void onCapabilityResponseMessage(OobHandle oobHandle, CapabilityResponseMessage message);

        /**
         * On Configuration Message
         * @param oobHandle uniquely identifies the session/device pair for OOB communication.
         * @param message payload.
         */
        void onConfigurationMessage(OobHandle oobHandle, SetConfigurationMessage message);

        /**
         * On Start Ranging Message
         * @param oobHandle uniquely identifies the session/device pair for OOB communication.
         * @param message payload.
         */
        void onStartRangingMessage(OobHandle oobHandle, StartRangingMessage message);

        /**
         * On Capability Request Message
         * @param oobHandle uniquely identifies the session/device pair for OOB communication.
         * @param message payload.
         */
        void onStopRangingMessage(OobHandle oobHandle, StopRangingMessage message);
    }
}
