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

package com.android.server.ranging;

import static android.Manifest.permission.RANGING;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.permission.PermissionManager;
import android.ranging.RangingPreference;

import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.blerssi.BleRssiAdapter;
import com.android.server.ranging.blerssi.BleRssiCapabilitiesAdapter;
import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.cs.CsCapabilitiesAdapter;
import com.android.server.ranging.rtt.RttAdapter;
import com.android.server.ranging.rtt.RttCapabilitiesAdapter;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.server.ranging.uwb.UwbCapabilitiesAdapter;

import com.google.common.util.concurrent.ListeningExecutorService;

public class RangingInjector {

    private static final String TAG = "RangingInjector";

    private final Context mContext;
    private final RangingServiceManager mRangingServiceManager;

    private final CapabilitiesProvider mCapabilitiesProvider;
    private final PermissionManager mPermissionManager;

    public RangingInjector(@NonNull Context context) {
        mContext = context;
        mCapabilitiesProvider = new CapabilitiesProvider(this);
        mRangingServiceManager = new RangingServiceManager(this);
        mPermissionManager = context.getSystemService(PermissionManager.class);
    }

    public Context getContext() {
        return mContext;
    }

    public CapabilitiesProvider getCapabilitiesProvider() {
        return mCapabilitiesProvider;
    }

    public RangingServiceManager getRangingServiceManager() {
        return mRangingServiceManager;
    }

    /**
     * Create a new adapter for a technology.
     */
    public @NonNull RangingAdapter createAdapter(
            @NonNull RangingSessionConfig.TechnologyConfig config,
            @RangingPreference.DeviceRole int role,
            @NonNull ListeningExecutorService executor
    ) {
        switch (config.getTechnology()) {
            case UWB:
                return new UwbAdapter(mContext, executor, role);
            case CS:
                return new CsAdapter(mContext);
            case RTT:
                return new RttAdapter(mContext, executor, role);
            case RSSI:
                return new BleRssiAdapter(mContext);
            default:
                throw new IllegalArgumentException(
                        "Adapter does not exist for technology " + config.getTechnology());
        }
    }

    public @NonNull CapabilitiesAdapter createCapabilitiesAdapter(
            @NonNull RangingTechnology technology,
            @NonNull CapabilitiesProvider.TechnologyAvailabilityListener listener
    ) {
        switch (technology) {
            case UWB:
                return new UwbCapabilitiesAdapter(mContext, listener);
            case CS:
                return new CsCapabilitiesAdapter(mContext, listener);
            case RTT:
                return new RttCapabilitiesAdapter(mContext, listener);
            case RSSI:
                return new BleRssiCapabilitiesAdapter(mContext, listener);
            default:
                throw new IllegalArgumentException(
                        "CapabilitiesAdapter does not exist for technology " + technology);
        }
    }


    public void enforceRangingPermissionForPreflight(
            @NonNull AttributionSource attributionSource) {
        if (!attributionSource.checkCallingUid()) {
            throw new SecurityException("Invalid attribution source " + attributionSource
                    + ", callingUid: " + Binder.getCallingUid());
        }
        int permissionCheckResult = mPermissionManager.checkPermissionForPreflight(
                RANGING, attributionSource);
        if (permissionCheckResult != PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold RANGING permission");
        }
    }

    public boolean checkUwbRangingPermissionForStartDataDelivery(
            @NonNull AttributionSource attributionSource, @NonNull String message) {
        int permissionCheckResult = mPermissionManager.checkPermissionForStartDataDelivery(
                RANGING, attributionSource, message);
        return permissionCheckResult == PERMISSION_GRANTED;
    }

}
