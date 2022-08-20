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

import android.annotation.IntRange;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.discovery.info.FiraConnectorMessage;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.InstructionCode;
import com.android.server.uwb.discovery.info.FiraConnectorMessage.MessageType;

/** Abstract class for Transport Provider */
public abstract class TransportProvider implements Transport {
    private static final String TAG = TransportProvider.class.getSimpleName();

    private DataReceiver mDataReceiver;

    /** Assigned SECID value (unsigned integer in the range 2..127, values 0 and 1 are reserved). */
    private int mSecid = 2;

    /**
     * Remote device SECID value (unsigned integer in the range 2..127, values 0 and 1 are
     * reserved).
     */
    private int mDestinationSecid = 2;

    protected TransportProvider(@IntRange(from = 2, to = 127) int secid) {
        mSecid = secid;
    }

    /**
     * Set the Destination SECID on the Remote device.
     *
     * @param secid 7-bit secure component ID.
     */
    public void setDestinationSecid(@IntRange(from = 2, to = 127) int secid) {
        mDestinationSecid = secid;
    }

    @Override
    public void sendData(@NonNull byte[] data, SendingDataCallback sendingDataCallback) {
        sendData(MessageType.COMMAND, data, sendingDataCallback);
    }

    @Override
    public void sendData(
            MessageType messageType,
            @NonNull byte[] data,
            SendingDataCallback sendingDataCallback) {
        if (sendMessage(
                mDestinationSecid,
                new FiraConnectorMessage(
                        messageType,
                        /*Default instrcution code for message exchange.*/
                        InstructionCode.DATA_EXCHANGE,
                        data))) {
            sendingDataCallback.onSuccess();
        } else {
            sendingDataCallback.onFailure();
        }
    }

    @Override
    public void registerDataReceiver(DataReceiver dataReceiver) {
        if (mDataReceiver != null) {
            Log.w(TAG, "Already has a registered data receiver.");
            return;
        }
        mDataReceiver = dataReceiver;
    }

    @Override
    public void unregisterDataReceiver() {
        mDataReceiver = null;
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
     * Starts the transport.
     *
     * @return indicates if successfully started.
     */
    public boolean start() {
        if (isStarted()) {
            Log.i(TAG, "Transport already started.");
            return false;
        }
        return true;
    }

    /**
     * Stops the transport.
     *
     * @return indicates if successfully stopped.
     */
    public boolean stop() {
        if (!isStarted()) {
            Log.i(TAG, "Transport already stopped.");
            return false;
        }
        return true;
    }

    /**
     * Send a FiRa connector message to the remote device through the transport.
     *
     * @param secid destination SECID on remote device.
     * @param message message to be send.
     * @return indicates if successfully started.
     */
    public abstract boolean sendMessage(int secid, FiraConnectorMessage message);

    /**
     * Called when the client received a new FiRa connector message from the remote device.
     *
     * @param secid destination SECID on this device.
     * @param message FiRa connector message.
     */
    protected void onMessageReceived(int secid, FiraConnectorMessage message) {
        if (secid != mSecid) {
            Log.w(
                    TAG,
                    "onMessageReceived rejected due to invalid SECID. Expect:"
                            + mSecid
                            + " Received:"
                            + secid);
            return;
        }
        if (mDataReceiver != null) {
            mDataReceiver.onDataReceived(message.payload);
        }
    }
}
