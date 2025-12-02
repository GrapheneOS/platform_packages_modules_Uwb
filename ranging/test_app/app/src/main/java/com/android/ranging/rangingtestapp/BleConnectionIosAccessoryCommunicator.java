/*
 * Copyright 2025 The Android Open Source Project
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

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.os.ParcelUuid;

import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.AccessoryConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AppleShareableConfigurationData;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class BleConnectionIosAccessoryCommunicator {
    // Standard UUID for the Client Characteristic Configuration Descriptor
    static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    static final UUID APPLICATION_SERVICE_UUID =
            UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    static final UUID APPLICATION_RX_CHARACTERISTIC_UUID =
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    static final UUID APPLICATION_TX_CHARACTERISTIC_UUID =
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    // The Nearby Interaction service is not discoverable by iOS apps directly.
    // It is intended to be used by the Apple device and its subsystems.
    static final UUID NEARBY_INTERACTION_SERVICE_UUID =
            UUID.fromString("48fe3e40-0817-4bb2-8633-3073689c2dba");
    static final UUID ACCESSORY_SINGLE_CONFIGURATION_DATA_UUID =
            UUID.fromString("95e8d9d5-d8ef-4721-9a4e-807375f53328");
    static final UUID ACCESSORY_MULTIPLE_CONFIGURATION_DATA_UUID =
            UUID.fromString("1176cf7b-bed2-4690-bd69-5f34001e820c");

    enum MessageId {
        // Messages from the accessory.
        // accessoryConfigurationData = 0x1
        ACCESSORY_CONFIGURATION_DATA((byte) 0x1),

        // accessoryUwbDidStart = 0x2
        ACCESSORY_UWB_DID_START((byte) 0x2),
        // accessoryUwbDidStop = 0x3
        ACCESSORY_UWB_DID_STOP((byte) 0x3),

        // Messages to the accessory.
        // initialize = 0xA
        INITIALIZE((byte) 0xA),
        // configureAndStart = 0xB
        CONFIGURE_AND_START((byte) 0xB),
        // stop = 0xC
        STOP((byte) 0xC);

        private final byte mValue;

        MessageId(byte value) {
            this.mValue = value;
        }

        byte getValue() {
            return mValue;
        }
    }

    // Callback for Bluetooth connection status
    abstract static class BluetoothCallback {
        abstract void onAdvertisingStarted();
        abstract void onAdvertisingStopped();
        abstract void onDeviceConnected(BluetoothDevice device);
        abstract void onDeviceDisconnected(BluetoothDevice device);
    }

    // Callback for messages received from the remote application
    abstract static class RemoteCallback {
        abstract void onInitialize(BluetoothDevice device);
        abstract void onConfigureAndStart(
                BluetoothDevice device,
                AppleShareableConfigurationData appleShareableConfigurationData);
        abstract void onStop(BluetoothDevice device);
    }

    private final BluetoothManager mBluetoothManager;
    private final BluetoothGattService mApplicationService;
    private final BluetoothGattCharacteristic mApplicationRxCharacteristic;
    private final BluetoothGattCharacteristic mApplicationTxCharacteristic;
    private final ServiceAdder mApplicationServiceAdder;
    private final BluetoothGattService mNearbyInteractionService;
    private final BluetoothGattCharacteristic mAccessorySingleCharacteristic;
    private final BluetoothGattCharacteristic mAccessoryMultipleCharacteristic;
    private final ServiceAdder mNearbyInteractionServiceAdder;
    private final BluetoothGattServer mBluetoothGattServer;

    private final BluetoothGattServerCallback mBluetoothGattServerCallback =
            new BluetoothGattServerCallback() {
                @Override
                public void onConnectionStateChange(
                        BluetoothDevice device, int status, int newState) {
                    super.onConnectionStateChange(device, status, newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        printLog("Device connected: "
                                + device.getAddress() + " (" + device.getName() + ")");
                        mBluetoothCallback.onDeviceConnected(device);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        printLog("Device disconnected: "
                                + device.getAddress() + " (" + device.getName() + ")");
                        mBluetoothCallback.onDeviceDisconnected(device);
                    }
                }

                @Override
                public void onServiceAdded(int status, BluetoothGattService service) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (service.getUuid().equals(mApplicationService.getUuid())) {
                            printLog("Service added: " + service.getUuid() + " (Application)");
                            mApplicationServiceAdder.notifyAdded();
                        } else if (service.getUuid().equals(mNearbyInteractionService.getUuid())) {
                            printLog("Service added: " + service.getUuid()
                                    + " (Nearby Interaction)");
                            mNearbyInteractionServiceAdder.notifyAdded();
                        } else {
                            printLog("Service added: " + service.getUuid());
                        }
                    } else {
                        printLog("Service add failed: " + service.getUuid() + " with " + status);
                    }
                }

                @Override
                public void onCharacteristicReadRequest(
                        BluetoothDevice device, int requestId, int offset,
                        BluetoothGattCharacteristic characteristic) {
                    printLog("Characteristics read request: " + characteristic.getUuid()
                            + " of service " + characteristic.getService().getUuid()
                            + " from device " + device.getAddress() + " (" + device.getName() + ")"
                            + " with request id: " + requestId + " for offset: " + offset);
                    mBluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[0]);
                }

                @Override
                public void onCharacteristicWriteRequest(
                        BluetoothDevice device, int requestId,
                        BluetoothGattCharacteristic characteristic,
                        boolean preparedWrite, boolean responseNeeded,
                        int offset, byte[] value) {
                    printLog("Characteristics write request: " + characteristic.getUuid()
                            + " of service " + characteristic.getService().getUuid()
                            + " from device " + device.getAddress() + " (" + device.getName() + ")"
                            + " with prepared write: " + preparedWrite
                            + " and response needed: " + responseNeeded
                            + " for offset: " + offset + " and value: " + bytesToHex(value));
                    mBluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[0]);
                    if (characteristic.getUuid().equals(mApplicationRxCharacteristic.getUuid())) {
                        printLog("Receiving application data...");
                        if (value.length > 0 && value[0] == MessageId.INITIALIZE.getValue()) {
                            printLog("Application is initialized");
                            mRemoteCallback.onInitialize(device);
                        } else if (value.length > 0
                                && value[0] == MessageId.CONFIGURE_AND_START.getValue()) {
                            printLog("Application is configured and starting ranging");
                            AppleShareableConfigurationData appleShareableConfigurationData =
                                    AppleShareableConfigurationData.deserialize(
                                            Arrays.copyOfRange(value, 1, value.length));
                            mRemoteCallback.onConfigureAndStart(
                                    device, appleShareableConfigurationData);
                        } else if (value.length > 0 && value[0] == MessageId.STOP.getValue()) {
                            printLog("Application is going to stop ranging");
                            mRemoteCallback.onStop(device);
                        }
                    }
                }

                @Override
                public void onDescriptorReadRequest(
                        BluetoothDevice device, int requestId, int offset,
                        BluetoothGattDescriptor descriptor) {
                    printLog("Descriptor read request: " + descriptor.getUuid()
                            + " of character: " + descriptor.getCharacteristic().getUuid()
                            + " of service: " + descriptor.getCharacteristic().getService()
                                    .getUuid()
                            + " from " + device.getAddress() + " (" + device.getName() + ")");
                    mBluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[0]);
                }

                @Override
                public void onDescriptorWriteRequest(
                        BluetoothDevice device, int requestId,
                        BluetoothGattDescriptor descriptor, boolean preparedWrite,
                        boolean responseNeeded, int offset, byte[] value) {
                    printLog("Descriptor write request: " + descriptor.getUuid()
                            + " of character: " + descriptor.getCharacteristic().getUuid()
                            + " of service: " + descriptor.getCharacteristic().getService()
                                    .getUuid()
                            + " from " + device.getAddress() + " (" + device.getName() + ")");
                    mBluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, offset, new byte[0]);
                }

                @Override
                public void onExecuteWrite(
                        BluetoothDevice device, int requestId, boolean execute) {
                    printLog("Execute write with request ID: " + requestId
                            + " and execute: " + execute
                            + " from device: " + device.getAddress()
                            + " (" + device.getName() + ")");
                    mBluetoothGattServer.sendResponse(
                            device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[0]);
                }
            };

    AdvertisingSetCallback mAdvertisingSetCallback =
            new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(
                        AdvertisingSet advertisingSet, int txPower, int status) {
                    printLog("onAdvertisingSetStarted():"
                            + " txPower: " + txPower
                            + " status: " + status);
                    if (status == ADVERTISE_SUCCESS) {
                        mBluetoothCallback.onAdvertisingStarted();
                    }
                }

                @Override
                public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
                    printLog("onAdvertisingDataSet(): status:" + status);
                }

                @Override
                public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
                    printLog("onScanResponseDataSet(): status:" + status);
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                    printLog("onAdvertisingSetStopped():");
                    mBluetoothCallback.onAdvertisingStopped();
                }
            };

    private final LoggingListener mLoggingListener;
    private final BluetoothCallback mBluetoothCallback;
    private final RemoteCallback mRemoteCallback;

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

    private static BluetoothGattCharacteristic createGeneralCharacteristic(UUID uuid) {
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(
                uuid,
                BluetoothGattCharacteristic.PROPERTY_READ
                        | BluetoothGattCharacteristic.PROPERTY_WRITE
                        | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
                        | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(
                CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ
                        | BluetoothGattDescriptor.PERMISSION_WRITE);
        characteristic.addDescriptor(descriptor);
        return characteristic;
    }

    BleConnectionIosAccessoryCommunicator(
            Application application, LoggingListener loggingListener,
            BluetoothCallback bluetoothCallback,
            RemoteCallback remoteCallback) {
        mBluetoothManager = application.getSystemService(BluetoothManager.class);
        mLoggingListener = loggingListener;

        // Application Service
        mApplicationService = new BluetoothGattService(
                APPLICATION_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mApplicationRxCharacteristic = createGeneralCharacteristic(
                APPLICATION_RX_CHARACTERISTIC_UUID);
        mApplicationTxCharacteristic = createGeneralCharacteristic(
                APPLICATION_TX_CHARACTERISTIC_UUID);
        mApplicationService.addCharacteristic(mApplicationRxCharacteristic);
        mApplicationService.addCharacteristic(mApplicationTxCharacteristic);

        // Nearby Interaction Service
        mNearbyInteractionService = new BluetoothGattService(
                NEARBY_INTERACTION_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mAccessorySingleCharacteristic = createGeneralCharacteristic(
                ACCESSORY_SINGLE_CONFIGURATION_DATA_UUID);
        mAccessoryMultipleCharacteristic = createGeneralCharacteristic(
                ACCESSORY_MULTIPLE_CONFIGURATION_DATA_UUID);
        mNearbyInteractionService.addCharacteristic(mAccessorySingleCharacteristic);
        mNearbyInteractionService.addCharacteristic(mAccessoryMultipleCharacteristic);

        mBluetoothGattServer = mBluetoothManager.openGattServer(
                application.getApplicationContext(), mBluetoothGattServerCallback);
        mApplicationServiceAdder = new ServiceAdder(mBluetoothGattServer, mApplicationService);
        mNearbyInteractionServiceAdder =
                new ServiceAdder(mBluetoothGattServer, mNearbyInteractionService);

        mBluetoothCallback = bluetoothCallback;
        mRemoteCallback = remoteCallback;
    }

    private void printLog(String message) {
        mLoggingListener.log(message);
    }

    private static class ServiceAdder {
        BluetoothGattServer mServer;
        BluetoothGattService mService;
        final Object mLock = new Object();
        boolean mAdding = false;
        boolean mAdded = false;

        ServiceAdder(BluetoothGattServer server, BluetoothGattService service) {
            mServer = server;
            mService = service;
        }

        boolean doAdd() {
            synchronized (mLock) {
                if (mAdding) {
                    return true;
                }
                if (mServer.addService(mService)) {
                    mAdding = true;
                    return true;
                }
                return false;
            }
        }

        void notifyAdded() {
            synchronized (mLock) {
                mAdded = true;
                mLock.notifyAll();
            }
        }

        synchronized boolean waitForAdded(long timeoutMs) {
            synchronized (mLock) {
                if (mAdded) {
                    return true;
                }
                try {
                    long now = System.currentTimeMillis();
                    long deadline = now + timeoutMs;
                    while (!mAdded && now < deadline) {
                        mLock.wait(deadline - now);
                    }
                } catch (InterruptedException e) {
                    // Restore the interrupted status
                    Thread.currentThread().interrupt();
                }
                return mAdded;
            }
        }
    }

    private void waitForBluetoothGattServicesAdded() {
        // BluetoothGattServices need to be added and wait for confirmation before adding another
        // one. Otherwise, the former relationship of attributes might be disrupted by later calls.
        mApplicationServiceAdder.doAdd();
        mApplicationServiceAdder.waitForAdded(2000);
        mNearbyInteractionServiceAdder.doAdd();
        mNearbyInteractionServiceAdder.waitForAdded(2000);
    }

    void startConnectableAdvertising() {
        waitForBluetoothGattServicesAdded();

        AdvertiseData advertiseData =
                new AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .addServiceUuid(new ParcelUuid(mApplicationService.getUuid()))
                        .addServiceUuid(new ParcelUuid(mNearbyInteractionService.getUuid()))
                        .build();

        AdvertisingSetParameters parameters =
                new AdvertisingSetParameters.Builder()
                        .setLegacyMode(false) // True by default, but set here as a reminder.
                        .setConnectable(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                        .build();

        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
        advertiser.startAdvertisingSet(
                parameters, advertiseData, null, null, null, 0, 0, mAdvertisingSetCallback);

        printLog("Start connectable advertising");
    }

    void stopAdvertising() {
        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        BluetoothLeAdvertiser advertiser = adapter.getBluetoothLeAdvertiser();
        advertiser.stopAdvertisingSet(mAdvertisingSetCallback);

        printLog("stop advertising");
    }

    boolean sendAccessoryConfigurationData(
            BluetoothDevice device,
            AccessoryConfigurationData accessoryConfigurationData) {
        if (device == null) {
            printLog("Failed to send accessory configuration data with null device");
            return false;
        }
        if (mBluetoothGattServer == null) {
            printLog("Failed to send accessory configuration data with null GATT server");
            return false;
        }

        // send accessory configuration data
        byte[] messageIdBytes = new byte[]{MessageId.ACCESSORY_CONFIGURATION_DATA.getValue()};
        byte[] messageDataBytes = accessoryConfigurationData.serialize();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(messageIdBytes, 0, messageIdBytes.length);
        stream.write(messageDataBytes, 0, messageDataBytes.length);
        printLog("Sending accessory configuration data: " + accessoryConfigurationData);
        int status = mBluetoothGattServer.notifyCharacteristicChanged(
                device,
                mApplicationTxCharacteristic,
                true,
                stream.toByteArray());
        return status == BluetoothStatusCodes.SUCCESS;
    }

    boolean sendAccessoryUwbDidStart(BluetoothDevice device) {
        if (device == null) {
            printLog("Failed to send accessory did start with null device");
            return false;
        }
        if (mBluetoothGattServer == null) {
            printLog("Failed to send accessory did start with null GATT server");
            return false;
        }

        // send accessory uwb did start
        byte[] messageIdBytes = new byte[]{MessageId.ACCESSORY_UWB_DID_START.getValue()};
        int status = mBluetoothGattServer.notifyCharacteristicChanged(
                device,
                mApplicationTxCharacteristic,
                true,
                ByteBuffer.allocate(1).put(messageIdBytes).array());
        return status == BluetoothStatusCodes.SUCCESS;
    }

    boolean sendAccessoryUwbDidStop(BluetoothDevice device) {
        if (device == null) {
            printLog("Failed to send accessory did stop with null device");
            return false;
        }
        if (mBluetoothGattServer == null) {
            printLog("Failed to send accessory did stop with null GATT server");
            return false;
        }

        // send accessory uwb did stop
        byte[] messageIdBytes = new byte[]{MessageId.ACCESSORY_UWB_DID_STOP.getValue()};
        int status = mBluetoothGattServer.notifyCharacteristicChanged(
                device,
                mApplicationTxCharacteristic,
                true,
                ByteBuffer.allocate(1).put(messageIdBytes).array());
        return status == BluetoothStatusCodes.SUCCESS;
    }
}
