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

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.ranging.rangingtestapp.Constants.GattState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** The ViewModel for the BLE GATT connection. */
@SuppressLint("MissingPermission") // permissions are checked upfront
public class BleConnectionViewModelCentral extends AndroidViewModel {
    private static final int GATT_MTU_SIZE = 512;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothManager mBluetoothManager;
    @Nullable private Set<BluetoothDevice> mConnectedDevices = new ArraySet<>();
    @Nullable private Set<BluetoothGatt> mConnectedGatts = new ArraySet<>();
    private MutableLiveData<String> mLogText = new MutableLiveData<>();
    private MutableLiveData<BluetoothDevice> mTargetDevice = new MutableLiveData<>();
    // scanner
    private final MutableLiveData<List<String>> mConnectedDeviceAddresses = new MutableLiveData<>();
    private final MutableLiveData<GattState> mGattState = new MutableLiveData<>(GattState.DISCONNECTED);
    private String mTargetBtAddress = "";

    /** Constructor */
    public BleConnectionViewModelCentral(@NonNull Application application) {
        super(application);
        mBluetoothManager = application.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    LiveData<String> getLogText() {
        return mLogText;
    }

    LiveData<GattState> getGattState() {
        return mGattState;
    }

    LiveData<List<String>> getConnectedDeviceAddresses() {
        return mConnectedDeviceAddresses;
    }

    LiveData<BluetoothDevice> getTargetDevice() {
        return mTargetDevice;
    }

    private void updateConnectedDevices() {
        List<String> connectedDevices =
                // Append name to the address for better readability
                mConnectedDevices.stream()
                        .map(d -> d.getAddress() + "(" + d.getName() + ")")
                        .collect(Collectors.toList());
        mConnectedDeviceAddresses.postValue(connectedDevices);
    }

    void setTargetDevice(String deviceAddressAndName) {
        printLog("set target address: " + deviceAddressAndName);
        if (!TextUtils.isEmpty(deviceAddressAndName)) {
            mTargetBtAddress = deviceAddressAndName.substring(0, 17); // Remove the name appended
            mTargetDevice.postValue(mBluetoothAdapter.getRemoteDevice(mTargetBtAddress));
        } else {
            mTargetBtAddress = "";
            mTargetDevice.postValue(null);
        }
    }

    void toggleScanConnect() {
        if (mGattState.getValue() == GattState.DISCONNECTED) {
            connectGattByScanning();
        } else if (mGattState.getValue() == GattState.SCANNING) {
            stopScanning();
        } else if (mGattState.getValue() == GattState.CONNECTED) {
            disconnectGatt();
        }
    }

    private BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    printLog("onConnectionStateChange status:" + status + ", newState:" + newState);
                    BluetoothDevice device = gatt.getDevice();
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        printLog(device.getName() + " is connected");
                        gatt.requestMtu(GATT_MTU_SIZE);
                        if (mTargetBtAddress.equals(device.getAddress())) {
                            mTargetDevice.postValue(device);
                        }
                        mConnectedDevices.add(device);
                        mConnectedGatts.add(gatt);
                        mGattState.postValue(GattState.CONNECTED);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        printLog("disconnected from " + device.getName());
                        gatt.close();
                        if (mTargetBtAddress.equals(device.getAddress())) {
                            mTargetDevice.postValue(null);
                        }
                        mConnectedDevices.remove(device);
                        mConnectedGatts.remove(gatt);
                        if (mConnectedDevices.isEmpty()) {
                            mGattState.postValue(GattState.DISCONNECTED);
                        }
                    }
                    updateConnectedDevices();
                }

                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        printLog("MTU changed to: " + mtu);
                    } else {
                        printLog("MTU change failed: " + status);
                    }
                }
            };

    private ScanCallback mScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
                    if (serviceUuids != null) {
                        for (ParcelUuid parcelUuid : serviceUuids) {
                            BluetoothDevice btDevice = result.getDevice();
                            printLog("found device - " + btDevice.getName());
                            if (parcelUuid.getUuid().equals(Constants.RANGING_TEST_SERVICE_UUID)) {
                                stopScanning();
                                printLog("connect GATT to: " + btDevice.getName());
                                // Connect to the GATT server
                                btDevice.connectGatt(
                                        getApplication().getApplicationContext(),
                                        false,
                                        mGattCallback,
                                        BluetoothDevice.TRANSPORT_LE);
                            }
                        }
                    }
                }
            };

    private void connectGattByScanning() {
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter =
                new ScanFilter.Builder()
                        .setServiceUuid(
                                new ParcelUuid(
                                        Constants.RANGING_TEST_SERVICE_UUID)) // Filter by service UUID
                        .build();
        filters.add(filter);

        ScanSettings settings =
                new ScanSettings.Builder()
                        .setLegacy(false)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build();

        printLog("start scanning...");

        // Start scanning
        bluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mGattState.setValue(GattState.SCANNING);
    }

    private void stopScanning() {
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(mScanCallback);
            mGattState.setValue(GattState.DISCONNECTED);
        }
    }

    private void disconnectGatt() {
        for (BluetoothGatt gatt: mConnectedGatts) {
            printLog("disconnect from " + gatt.getDevice().getName());
            gatt.disconnect();
        }
    }

    private void printLog(@NonNull String logMsg) {
        mLogText.postValue("BT Log: " + logMsg);
    }
}
