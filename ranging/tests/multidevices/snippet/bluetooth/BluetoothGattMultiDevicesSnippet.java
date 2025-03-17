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

import static android.bluetooth.BluetoothDevice.BOND_NONE;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.rpc.Rpc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

public class BluetoothGattMultiDevicesSnippet implements Snippet {
    private static final String TAG = "BluetoothGattMultiDevicesSnippet";

    private BluetoothGattMultiDevicesServer mGattServer;
    private BluetoothGattMultiDevicesClient mGattClient;

    private Context mContext;
    private BluetoothManager mBluetoothManager;

    public BluetoothGattMultiDevicesSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
    }

    @Rpc(description = "Reset the state of client + server")
    public void reset() throws Throwable {
        mGattServer = new BluetoothGattMultiDevicesServer(mContext, mBluetoothManager);
        mGattClient = new BluetoothGattMultiDevicesClient(mContext, mBluetoothManager);
        // Reset all bonded devices to clear device state.
        runWithShellPermission(
                () -> {
                    for (BluetoothDevice bluetoothDevice :
                            mBluetoothManager.getAdapter().getBondedDevices()) {
                        removeBondImplBtDevice(bluetoothDevice);
                    }
                });
    }

    @Rpc(description = "Creates Bluetooth GATT server with a given UUID and advertises it.")
    public void createAndAdvertiseServer(String uuid) throws Throwable {
        runWithShellPermission(() -> mGattServer.createAndAdvertiseServer(uuid));
    }

    @Rpc(
            description =
                    "Creates Bluetooth GATT server with a given UUID and ties it to an"
                            + " advertisement.")
    public void createAndAdvertiseIsolatedServer(String uuid) throws Throwable {
        runWithShellPermission(() -> mGattServer.createAndAdvertiseIsolatedServer(uuid));
    }

    @Rpc(description = "Connect to the peer device advertising the specified UUID")
    public String connectGatt(String uuid) throws Throwable {
        return runWithShellPermission(() -> Utils.convertBtDeviceToJson(mGattClient.connect(uuid)));
    }

    @Rpc(description = "Disconnect to the peer device advertising the specified UUID")
    public boolean disconnectGatt(String uuid) throws Throwable {
        return runWithShellPermission(() -> mGattClient.disconnect(uuid));
    }

    @Rpc(description = "Get all the devices connected to the GATT server")
    public JSONArray getConnectedDevices() throws Throwable {
        return runWithShellPermission(
                () -> Utils.convertBtDevicesToJson(mGattServer.getConnectedDevices()));
    }

    @Rpc(description = "Generate local OOB data to used for bonding with the server")
    public JSONObject generateServerLocalOobData() throws Throwable {
        return runWithShellPermission(
                () -> Utils.convertOobDataToJson(mGattServer.generateLocalOObData()));
    }

    @Rpc(description = "Create a bond with the server using local OOB data generated on the server")
    public String createBondOob(String uuid, JSONObject jsonObject) throws Throwable {
        return runWithShellPermission(
                () ->
                        Utils.convertBtDeviceToJson(
                                mGattClient.createBondOob(
                                        uuid, Utils.convertJsonToOobData(jsonObject))));
    }

    @Rpc(description = "Remove bond with the remote device")
    public boolean removeBond(String remoteAddress) throws Throwable {
        return runWithShellPermission(() -> removeBondImpl(remoteAddress));
    }

    @Rpc(description = "Enables Bluetooth")
    public void enableBluetooth() throws Throwable {
        runWithShellPermission(() -> mBluetoothManager.getAdapter().enable());
    }

    @Rpc(description = "Disable Bluetooth")
    public void disableBluetooth() throws Throwable {
        runWithShellPermission(() -> mBluetoothManager.getAdapter().disable());
    }

    @Rpc(description = "Checks Bluetooth state")
    public boolean isBluetoothOn() {
        return mBluetoothManager.getAdapter().isEnabled();
    }

    @Rpc(description = "Whether the connected peer has a service of the given UUID")
    public boolean containsService(String uuid) throws Throwable {
        return runWithShellPermission(() -> mGattClient.containsService(uuid));
    }

    private boolean removeBondImpl(String remoteAddress) {
        BluetoothDevice bluetoothDevice =
                mBluetoothManager.getAdapter().getRemoteDevice(remoteAddress);
        if (bluetoothDevice == null) {
            Log.e(TAG, "Failed to find remove device: " + bluetoothDevice);
            return false;
        }
        return removeBondImplBtDevice(bluetoothDevice);
    }

    private static final int BOND_REMOVAL_CALLBACK_TIMEOUT_SEC = 5;

    private boolean removeBondImplBtDevice(BluetoothDevice bluetoothDevice) {
        CountDownLatch bondingBlocker = new CountDownLatch(1);
        IntentFilter bondIntentFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        BroadcastReceiver bondBroadcastReceiver =
                new Utils.BondStateBroadcastReceiverImpl(
                        BOND_NONE, bluetoothDevice, bondingBlocker);
        mContext.registerReceiver(bondBroadcastReceiver, bondIntentFilter);
        if (!bluetoothDevice.removeBond()) {
            Log.e(TAG, "Failed to remove bond");
            return false;
        }
        boolean timeout = false;
        try {
            timeout = !bondingBlocker.await(BOND_REMOVAL_CALLBACK_TIMEOUT_SEC, SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for bond removal", e);
            timeout = true;
        }
        mContext.unregisterReceiver(bondBroadcastReceiver);
        if (timeout) {
            Log.e(TAG, "Did not remove bond");
            return false;
        }
        return true;
    }

    private void runWithShellPermission(Runnable action) throws Throwable {
        Utils.adoptShellPermission();
        try {
            action.run();
        } finally {
            Utils.dropShellPermission();
        }
    }

    private <T> T runWithShellPermission(ThrowingSupplier<T> action) throws Throwable {
        Utils.adoptShellPermission();
        try {
            return action.get();
        } finally {
            Utils.dropShellPermission();
        }
    }

    /**
     * Similar to {@link Supplier} but has {@code throws Exception}.
     *
     * @param <T> type of the value produced
     */
    private interface ThrowingSupplier<T> {
        /** Similar to {@link Supplier#get} but has {@code throws Exception}. */
        T get() throws Exception;
    }
}
