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

package com.android.server.ranging.blerssi;

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.util.Log;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.CapabilitiesProvider.TechnologyAvailabilityListener;

public class BleRssiCapabilitiesAdapter extends CapabilitiesProvider.CapabilitiesAdapter {
    private static final String TAG = BleRssiCapabilitiesAdapter.class.getSimpleName();

    private final Context mContext;
    private final BluetoothManager mBluetoothManager;

    public BleRssiCapabilitiesAdapter(
            @NonNull Context context,
            @NonNull TechnologyAvailabilityListener listener
    ) {
        super(listener);
        mContext = context;
        if (isSupported(mContext)) {
            mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        } else {
            mBluetoothManager = null;
        }
    }

    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    @Override
    public @RangingCapabilities.RangingTechnologyAvailability int getAvailability() {
        if (mBluetoothManager == null) {
            return NOT_SUPPORTED;
        } else if (isAvailable()) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    public boolean isAvailable() {
        return mBluetoothManager.getAdapter().getState() == BluetoothAdapter.STATE_ON;
    }

    @Nullable
    @Override
    public BleRssiRangingCapabilities getCapabilities() {
        if (getAvailability() != ENABLED) return null;

        try {
            return new BleRssiRangingCapabilities(
                    ((BluetoothManager) mContext.getSystemService(BluetoothManager.class))
                            .getAdapter()
                            .getAddress());
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "Failed to get ble rssi capabilities: " + e);
            return null;
        }
    }
}
