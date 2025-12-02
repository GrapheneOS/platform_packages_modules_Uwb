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

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.ranging.rangingtestapp.BleConnectionIosAccessoryCommunicator.BluetoothCallback;
import com.android.ranging.rangingtestapp.BleConnectionIosAccessoryCommunicator.RemoteCallback;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AccessoryConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AppleShareableConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryRangingHandler.AccessoryCallback;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** The ViewModel for the BLE GATT connection. */
@SuppressLint("MissingPermission") // permissions are checked upfront
public class BleConnectionIosAccessoryViewModel extends AndroidViewModel
        implements BleConnection {
    private final BleConnectionIosAccessoryCommunicator mIosCommunicator;
    private final BluetoothCallback mBluetoothCallbackHandler = new BluetoothCallback() {
        @Override
        void onAdvertisingStarted() {
            mIsAdvertising.postValue(true);
        }

        @Override
        void onAdvertisingStopped() {
            mIsAdvertising.postValue(false);
        }

        @Override
        void onDeviceConnected(BluetoothDevice device) {
            mConnectedDeviceSet.add(device);
            updateConnectedDevices();
        }

        @Override
        void onDeviceDisconnected(BluetoothDevice device) {
            if (device.equals(mTargetDevice.get())) {
                setTargetDevice(null);
            }
            mConnectedDeviceSet.remove(device);
            updateConnectedDevices();
        }
    };

    private final RemoteCallback mRemoteCallbackHandler = new RemoteCallback() {
        @Override
        void onInitialize(BluetoothDevice device) {
            // remote application has been initialized
            // send our accessory configuration data
            sendConfigurationRequest(device);
        }

        @Override
        void onConfigureAndStart(
                BluetoothDevice device,
                AppleShareableConfigurationData appleShareableConfigurationData) {
            // the ranging session has been started by remote application
            // start our accessory ranging
            handleConfigurationResponse(device, appleShareableConfigurationData);
        }

        @Override
        void onStop(BluetoothDevice device) {
            // the ranging session has been stopped by remote application
            // stop our accessory ranging
            stopAccessoryRanging();
        }
    };

    private final LoggingListener mLoggingListener;
    private MutableLiveData<Boolean> mIsAdvertising = new MutableLiveData<>(false);
    private Set<BluetoothDevice> mConnectedDeviceSet = new ArraySet<>();
    // scanner
    private MutableLiveData<List<BluetoothDevice>> mConnectedDevices = new MutableLiveData<>();
    private AtomicReference<BluetoothDevice> mTargetDevice = new AtomicReference<>(null);

    private ConcurrentHashMap<String, AccessoryConfigurationData>
            mAccessoryConfigurations = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, AppleShareableConfigurationData>
            mAppleConfigurations = new ConcurrentHashMap<>();

    private IosAccessoryRangingHandler mRangingHandler;
    private final AccessoryCallback mAccessoryCallbackHandler = new AccessoryCallback() {
        @Override
        void onRangingStarted() {
            // send accessory uwb did start
            boolean success = mIosCommunicator.sendAccessoryUwbDidStart(mTargetDevice.get());
            if (!success) {
                printLog("Failed to notify accessory ranging started");
            }
        }

        @Override
        void onRangingStopped() {
            // send accessory uwb did stop
            boolean success = mIosCommunicator.sendAccessoryUwbDidStop(mTargetDevice.get());
            if (!success) {
                printLog("Failed to notify accessory ranging stopped");
            }
        }
    };

    static class Factory implements ViewModelProvider.Factory {
        private Application mApplication;
        private LoggingListener mLoggingListener;
        private IosAccessoryRangingHandler mRangingHandler;

        Factory(Application application, LoggingListener loggingListener,
                IosAccessoryRangingHandler rangingHandler) {
            mApplication = application;
            mLoggingListener = loggingListener;
            mRangingHandler = rangingHandler;
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new BleConnectionIosAccessoryViewModel(
                    mApplication, mLoggingListener, mRangingHandler);
        }
    }

    /** Constructor */
    BleConnectionIosAccessoryViewModel(
            @NonNull Application application, LoggingListener loggingListener,
            IosAccessoryRangingHandler rangingHandler) {
        super(application);
        mIosCommunicator = new BleConnectionIosAccessoryCommunicator(
                application, loggingListener, mBluetoothCallbackHandler, mRemoteCallbackHandler);


        mLoggingListener = loggingListener;
        mRangingHandler = rangingHandler;
        mRangingHandler.setAccessoryCallback(mAccessoryCallbackHandler);
    }

    LiveData<Boolean> getIsAdvertising() {
        return mIsAdvertising;
    }

    LiveData<List<BluetoothDevice>> getConnectedDevices() {
        return mConnectedDevices;
    }

    private void updateConnectedDevices() {
        List<BluetoothDevice> connectedDevices =
                mConnectedDeviceSet.stream().collect(Collectors.toList());
        mConnectedDevices.postValue(connectedDevices);
    }

    void setTargetDevice(BluetoothDevice device) {
        if (device != null) {
            printLog("set target device: " + device.getAddress()
                    + ", " + mAccessoryConfigurations.get(device.getAddress())
                    + ", " + mAppleConfigurations.get(device.getAddress()));
            mTargetDevice.set(device);
        } else {
            printLog("clear target device");
            mTargetDevice.set(null);
        }
    }

    void setAccessoryConfiguration(BluetoothDevice device,
            AccessoryConfigurationData accessoryConfigurationData) {
        printLog("Cache accessory configuration data " + accessoryConfigurationData
                + " for " + device.getAddress());
        mAccessoryConfigurations.put(device.getAddress(), accessoryConfigurationData);
    }

    void setAppleConfiguration(BluetoothDevice device,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        printLog("Cache apple shareable configuration data " + appleShareableConfigurationData
                + " for " + device.getAddress());
        mAppleConfigurations.put(device.getAddress(), appleShareableConfigurationData);
    }

    /******** Peripheral functions ********************/
    void toggleAdvertising() {
        if (mIsAdvertising.getValue()) {
            mIosCommunicator.stopAdvertising();
        } else {
            mIosCommunicator.startConnectableAdvertising();
        }
    }

    void requestRangingForcibly() {
        // Generally the ranging request would be sent after receiving the initialize message.
        // We can still send the ranging request actively anyway.
        BluetoothDevice device = mTargetDevice.get();
        if (device == null) {
            printLog("Avoid starting ranging forcibly with null device");
            return;
        }
        printLog("Try to start ranging forcibly with device: " + device.getAddress());
        sendConfigurationRequest(device);
    }

    private void sendConfigurationRequest(BluetoothDevice device) {
        AccessoryConfigurationData accessoryConfigurationData =
                mRangingHandler.createAccessoryConfigurationData();
        setAccessoryConfiguration(device, accessoryConfigurationData);
        mIosCommunicator.sendAccessoryConfigurationData(device, accessoryConfigurationData);
    }

    private void handleConfigurationResponse(
            BluetoothDevice device,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        setAppleConfiguration(device, appleShareableConfigurationData);
        startAccessoryRanging(device, appleShareableConfigurationData);
    }

    private boolean startAccessoryRanging(BluetoothDevice device,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        AccessoryConfigurationData accessoryConfigurationData =
                mAccessoryConfigurations.get(device.getAddress());
        boolean success = mRangingHandler.startAccessoryRanging(
                accessoryConfigurationData, appleShareableConfigurationData);
        if (!success) {
            printLog("Failed to start accessory ranging");
            return false;
        }
        return true;
    }

    private boolean stopAccessoryRanging() {
        boolean success = mRangingHandler.stopAccessoryRanging();
        if (!success) {
            printLog("Failed to stop accessory ranging");
            return false;
        }
        return true;
    }

    private void printLog(@NonNull String logMsg) {
        mLoggingListener.log(logMsg);
    }
}
