/*
 * Copyright 2023 The Android Open Source Project
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

package com.google.snippet.bluetooth;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.OobData;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public final class BluetoothGattMultiDevicesServer {
    private static final String TAG = "BluetoothGattMultiDevicesServer";
    private static final int CALLBACK_TIMEOUT_SEC = 1;

    private Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private OobData mOobData;

    public BluetoothGattMultiDevicesServer(Context context, BluetoothManager manager) {
        mContext = context;
        mBluetoothManager = manager;
        mBluetoothAdapter = manager.getAdapter();
    }

    public BluetoothGattServer createGattServer(String uuid) {
        var bluetoothGattServer =
                mBluetoothManager.openGattServer(mContext, new BluetoothGattServerCallback() {});
        var service = new BluetoothGattService(UUID.fromString(uuid), SERVICE_TYPE_PRIMARY);
        bluetoothGattServer.addService(service);
        return bluetoothGattServer;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
    }

    public void createAndAdvertiseServer(String uuid) {
        createGattServer(uuid);

        var bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        var params = new AdvertisingSetParameters.Builder().setConnectable(true).build();
        var data =
                new AdvertiseData.Builder()
                        .addServiceUuid(new ParcelUuid(UUID.fromString(uuid)))
                        .build();

        bluetoothLeAdvertiser.startAdvertisingSet(
                params, data, null, null, null, new AdvertisingSetCallback() {});
    }

    public void createAndAdvertiseIsolatedServer(String uuid) {
        var gattServer = createGattServer(uuid);

        var bluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        var params =
                new AdvertisingSetParameters.Builder()
                        .setConnectable(true)
                        .setOwnAddressType(
                                AdvertisingSetParameters.ADDRESS_TYPE_RANDOM_NON_RESOLVABLE)
                        .build();
        var data =
                new AdvertiseData.Builder()
                        .addServiceUuid(new ParcelUuid(UUID.fromString(uuid)))
                        .build();

        bluetoothLeAdvertiser.startAdvertisingSet(
                params,
                data,
                null,
                null,
                null,
                0,
                0,
                gattServer,
                new AdvertisingSetCallback() {},
                new Handler(Looper.getMainLooper()));
    }

    private class OobDataCallbackImpl implements BluetoothAdapter.OobDataCallback {
        private final CountDownLatch mCountDownLatch;

        OobDataCallbackImpl(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onOobData(int transport, OobData oobData) {
            Log.i(TAG, "OobDataCallback: onOobData: " + transport + ", " + oobData);
            mOobData = oobData;
            mCountDownLatch.countDown();

        }

        @Override
        public void onError(int errorCode) {
            Log.i(TAG, "OobDataCallback: onError: " + errorCode);
            mOobData = null;
            mCountDownLatch.countDown();

        }
    }

    public OobData generateLocalOObData() {
        CountDownLatch oobLatch = new CountDownLatch(1);
        mBluetoothAdapter.generateLocalOobData(
                TRANSPORT_LE,
                Executors.newSingleThreadExecutor(),
                new OobDataCallbackImpl(oobLatch));
        boolean timeout;
        try {
            timeout = !oobLatch.await(CALLBACK_TIMEOUT_SEC, SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "", e);
            timeout = true;
        }
        if (timeout || mOobData == null) {
            Log.e(TAG, "Did not generate local oob data");
            return null;
        }
        return mOobData;
    }

}
