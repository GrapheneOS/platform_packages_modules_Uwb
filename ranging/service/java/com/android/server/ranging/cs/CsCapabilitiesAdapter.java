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

package com.android.server.ranging.cs;

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import android.annotation.NonNull;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.ranging.RangingCapabilities.RangingTechnologyAvailability;
import android.ranging.ble.cs.BleCsRangingCapabilities;

import androidx.annotation.Nullable;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.CapabilitiesProvider.TechnologyAvailabilityListener;

import java.util.ArrayList;
import java.util.List;

public class CsCapabilitiesAdapter extends CapabilitiesAdapter {

    private final Context mContext;

    private final BluetoothManager mBluetoothManager;

    /** @return true if CS is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING);
    }

    @Override
    public @RangingTechnologyAvailability int getAvailability() {
        if (mBluetoothManager == null) {
            return NOT_SUPPORTED;
        } else if (mBluetoothManager.getAdapter().getState() == BluetoothAdapter.STATE_ON) {
            return ENABLED;
        } else {
            return DISABLED_USER;
        }
    }

    @Override
    public @Nullable BleCsRangingCapabilities getCapabilities() {
        if (getAvailability() == ENABLED) {
            List<Integer> securityLevels = new ArrayList<>(
                mContext.getSystemService(BluetoothManager.class)
                    .getAdapter()
                    .getDistanceMeasurementManager()
                    .getChannelSoundingSupportedSecurityLevels());
            return new BleCsRangingCapabilities.Builder()
                    .setSupportedSecurityLevels(securityLevels)
                    .build();
        } else {
            return null;
        }
    }

    public CsCapabilitiesAdapter(
            @NonNull Context context, @NonNull TechnologyAvailabilityListener listener
    ) {
        super(listener);
        mContext = context;

        if (isSupported(mContext)) {
            mBluetoothManager = mContext.getSystemService(BluetoothManager.class);

            BluetoothStateChangeReceiver receiver = new BluetoothStateChangeReceiver();
            IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            mContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mBluetoothManager = null;
        }
    }

    private class BluetoothStateChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            TechnologyAvailabilityListener listener = getAvailabilityListener();
            if (listener != null) {
                listener.onAvailabilityChange(
                        getAvailability(),
                        CapabilitiesProvider.AvailabilityChangedReason.SYSTEM_POLICY);
            }
        }
    }
}
