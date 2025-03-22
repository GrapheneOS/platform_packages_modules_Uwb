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

package com.google.snippet.bluetooth;

import android.app.UiAutomation;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.OobData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Utils {
    private static final String TAG = "Utils";

    private Utils() {}

    public static void adoptShellPermission() {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        // Need to drop the UI Automation to allow other snippets to get access
        // to global UI automation.
        // Using reflection here since the method is not public.
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to cleaup Ui Automation", e);
        }
    }

    public static void dropShellPermission() {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.dropShellPermissionIdentity();
        // Need to drop the UI Automation to allow other snippets to get access
        // to global UI automation.
        // Using reflection here since the method is not public.
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to cleaup Ui Automation", e);
        }
    }

    private static byte[] convertJSONArrayToByteArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        byte[] bArray = new byte[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            bArray[i] = (byte) jArray.getInt(i);
        }
        return bArray;
    }

    private static JSONArray convertByteArrayToJSONArray(byte[] array) throws JSONException {
        if (array == null) {
            return null;
        }
        return new JSONArray(array);
    }

    public static JSONObject convertOobDataToJson(OobData oobData) throws JSONException {
        if (oobData == null) return null;
        return new JSONObject()
                .put("device_address_with_type",
                convertByteArrayToJSONArray(oobData.getDeviceAddressWithType()))
                .put("confirmation_hash",
                        convertByteArrayToJSONArray(oobData.getConfirmationHash()))
                .put("randomizer_hash",
                        convertByteArrayToJSONArray(oobData.getRandomizerHash()))
                .put("device_name", convertByteArrayToJSONArray(oobData.getDeviceName()))
                .put("classic_length",
                        convertByteArrayToJSONArray(oobData.getClassicLength()))
                .put("class_of_device",
                        convertByteArrayToJSONArray(oobData.getClassOfDevice()))
                .put("le_temporary_key",
                        convertByteArrayToJSONArray(oobData.getLeTemporaryKey()))
                .put("le_temporary_appearance",
                        convertByteArrayToJSONArray(oobData.getLeAppearance()))
                .put("le_flags", oobData.getLeFlags())
                .put("le_device_role", oobData.getLeDeviceRole());
    }

    public static OobData convertJsonToOobData(JSONObject jsonObj) throws JSONException {
        if (jsonObj == null) return null;
        return new OobData.LeBuilder(
                convertJSONArrayToByteArray(
                        jsonObj.getJSONArray("confirmation_hash")),
                convertJSONArrayToByteArray(
                        jsonObj.getJSONArray("device_address_with_type")),
                jsonObj.getInt("le_device_role"))
                .setLeTemporaryKey(convertJSONArrayToByteArray(
                        jsonObj.getJSONArray("le_temporary_key")))
                .setRandomizerHash(convertJSONArrayToByteArray(
                        jsonObj.getJSONArray("randomizer_hash")))
                .setLeFlags(jsonObj.getInt("le_flags"))
                .setDeviceName(convertJSONArrayToByteArray(
                        jsonObj.getJSONArray("device_name")))
                .build();
    }

    public static String convertBtDeviceToJson(BluetoothDevice btDevice) throws JSONException {
        if (btDevice == null) return null;
        return btDevice.getAddress();
    }

    public static JSONArray convertBtDevicesToJson(List<BluetoothDevice> btDevices) throws JSONException {
        if (btDevices == null) return null;
        JSONArray jsonArray = new JSONArray();
        for (BluetoothDevice device: btDevices) {
            jsonArray.put(device.getAddress());
        }
        return jsonArray;
    }

    // Helper class to wait for bond state changed intents.
    public static class BondStateBroadcastReceiverImpl extends BroadcastReceiver {
        private final int mWaitForBondState;
        private final BluetoothDevice mRemoteAddress;
        private final CountDownLatch mBondingBlocker;

        BondStateBroadcastReceiverImpl(
                int waitForBondState,
                BluetoothDevice remoteAddress,
                CountDownLatch bondingBlocker) {
            mWaitForBondState = waitForBondState;
            mRemoteAddress = remoteAddress;
            mBondingBlocker = bondingBlocker;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive: " + intent.getAction());
            if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, 0);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.i(TAG, "onReceive: bondState=" + bondState);
                if (device.equals(mRemoteAddress)
                        && bondState == mWaitForBondState
                        && mBondingBlocker != null) {
                    mBondingBlocker.countDown();
                }
            }
        }
    }
    ;
}
