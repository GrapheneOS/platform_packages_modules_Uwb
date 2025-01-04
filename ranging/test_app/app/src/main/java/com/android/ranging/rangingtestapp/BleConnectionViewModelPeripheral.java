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
public class BleConnectionViewModelPeripheral extends AndroidViewModel {
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothManager mBluetoothManager;
    @Nullable private Set<BluetoothDevice> mConnectedDevices = new ArraySet<>();
    private BluetoothGattServer mBluetoothGattServer = null;
    private MutableLiveData<Boolean> mIsAdvertising = new MutableLiveData<>(false);
    private MutableLiveData<String> mLogText = new MutableLiveData<>();
    private MutableLiveData<BluetoothDevice> mTargetDevice = new MutableLiveData<>();
    // scanner
    private final MutableLiveData<List<String>> mConnectedDeviceAddresses = new MutableLiveData<>();
    private String mTargetBtAddress = "";

    /** Constructor */
    public BleConnectionViewModelPeripheral(@NonNull Application application) {
        super(application);
        mBluetoothManager = application.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    LiveData<Boolean> getIsAdvertising() {
        return mIsAdvertising;
    }

    LiveData<String> getLogText() {
        return mLogText;
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

    /******** Peripheral functions ********************/
    void toggleAdvertising() {
        if (mIsAdvertising.getValue()) {
            stopAdvertising();
        } else {
            startConnectableAdvertising();
        }
    }

    AdvertisingSetCallback mAdvertisingSetCallback =
            new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(
                        AdvertisingSet advertisingSet, int txPower, int status) {
                    printLog(
                            "onAdvertisingSetStarted(): txPower:"
                                    + txPower
                                    + " , status: "
                                    + status);
                    if (status == 0) {
                        mIsAdvertising.postValue(true);
                    }
                }

                @Override
                public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
                    printLog("onAdvertisingDataSet() :status:" + status);
                }

                @Override
                public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
                    printLog("onScanResponseDataSet(): status:" + status);
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                    printLog("onAdvertisingSetStopped():");
                    mIsAdvertising.postValue(false);
                }
            };

    private void startConnectableAdvertising() {
        if (mIsAdvertising.getValue()) {
            return;
        }
        BluetoothLeAdvertiser advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertisingSetParameters parameters =
                new AdvertisingSetParameters.Builder()
                        .setLegacyMode(false) // True by default, but set here as a reminder.
                        .setConnectable(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                        .build();

        BluetoothGattServerCallback gattServerCallback =
                new BluetoothGattServerCallback() {
                    @Override
                    public void onConnectionStateChange(
                            BluetoothDevice device, int status, int newState) {
                        super.onConnectionStateChange(device, status, newState);
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            printLog("Device connected: " + device.getName());
                            if (mTargetBtAddress.equals(device.getAddress())) {
                                mTargetDevice.postValue(device);
                            }
                            mConnectedDevices.add(device);
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            printLog("Device disconnected: " + device.getName());
                            if (mTargetBtAddress.equals(device.getAddress())) {
                                mTargetDevice.postValue(null);
                            }
                            mConnectedDevices.remove(device);
                        }
                        updateConnectedDevices();
                    }
                };

        mBluetoothGattServer =
                mBluetoothManager.openGattServer(
                        getApplication().getApplicationContext(), gattServerCallback);
        AdvertiseData advertiseData =
                new AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .addServiceUuid(new ParcelUuid(Constants.RANGING_TEST_SERVICE_UUID))
                        .build();

        printLog("Start connectable advertising");

        advertiser.startAdvertisingSet(
                parameters, advertiseData, null, null, null, 0, 0, mAdvertisingSetCallback);
    }

    private void stopAdvertising() {
        BluetoothLeAdvertiser advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        advertiser.stopAdvertisingSet(mAdvertisingSetCallback);
        printLog("stop advertising");
    }

    private void printLog(@NonNull String logMsg) {
        mLogText.postValue("BT Log: " + logMsg);
    }
}
