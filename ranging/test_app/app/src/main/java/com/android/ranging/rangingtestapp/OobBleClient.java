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

package com.android.ranging.rangingtestapp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.ranging.oob.TransportHandle;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * OOB BLE client.
 */
public class OobBleClient implements TransportHandle {
    private static final String TAG = "OobBleClient";
    private final BleConnection mBleConnection;
    private final LoggingListener mLoggingListener;
    private final BluetoothDevice mBluetoothDevice;
    private final BluetoothManager mBluetoothManager;
    private final BluetoothAdapter mBluetoothAdapter;
    private final OobBleClientConnectionThread mOobBleClientConnectionThread;
    private CountDownLatch mSocketCountDownLatch = new CountDownLatch(1);
    private BluetoothSocket mSocket;
    private ReceiveCallback mReceiveCallback;
    private Executor mReceiveExecutor;

    public OobBleClient(
            Context context, BleConnection bleConnection,
            BluetoothDevice device, LoggingListener loggingListener) {
        mBleConnection = bleConnection;
        mLoggingListener = loggingListener;
        mBluetoothDevice = device;
        mBluetoothManager = context.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mOobBleClientConnectionThread = new OobBleClientConnectionThread();
        mOobBleClientConnectionThread.start();
    }

    @Override
    public void sendData(byte[] data) {
        try {
            mSocket.getOutputStream().write(data);
        } catch (IOException e) {
            printLog("Failed to send data " + e);
            mReceiveExecutor.execute(() -> {
                mReceiveCallback.onSendFailed();
            });
        }
    }

    @Override
    public void registerReceiveCallback(Executor executor, ReceiveCallback callback) {
        mReceiveExecutor = executor;
        mReceiveCallback = callback;
    }

    public boolean waitForSocketCreation() {
        boolean success = false;
        try {
            success = mSocketCountDownLatch.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            printLog("Failed to wait for socket " + e);
        }
        if (!success) printLog("Timed out waiting for socket creation");
        return success;
    }

    public class OobBleClientConnectionThread extends Thread {
        private static final int MAX_DATA_SIZE = 1024;
        @Override
        public void run() {
            try {
                int psm = mBleConnection.waitForPsm();
                if (psm == -1) return;
                printLog("Creating L2cap socket on " + psm);
                OobBleClient.this.mSocket =
                        OobBleClient.this.mBluetoothDevice.createInsecureL2capChannel(psm);
                printLog("Connecting on L2cap socket");
                OobBleClient.this.mSocket.connect();
                mSocketCountDownLatch.countDown();
                printLog("Connected on L2cap socket " + mSocket.getRemoteDevice().getAddress());
            } catch (IOException e) {
                printLog("Failed to connect on L2cap" + e);
            }
            while (!isInterrupted()) {
                try {
                    byte[] data = new byte[MAX_DATA_SIZE];
                    int dataSize = OobBleClient.this.mSocket.getInputStream().read(data);
                    printLog("Received data size: " + dataSize);
                    if (dataSize > 0) {
                        mReceiveExecutor.execute(() -> {
                            mReceiveCallback.onReceiveData(Arrays.copyOf(data, dataSize));
                        });
                    }
                } catch (IOException e) {
                    printLog("Failed to read data " + e);
                }
            }
            printLog("Server connection thread interrupted");
            try {
                OobBleClient.this.mSocket.close();
            } catch (IOException e) {
                printLog("Failed to close socket " + e);
            }
            mReceiveExecutor.execute(() -> {
                mReceiveCallback.onClose();
            });
        }
    }

    @Override
    public void close() {
        mOobBleClientConnectionThread.interrupt();
    }

    private void printLog(String log) {
        mLoggingListener.log(log);
    }
}
