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

import android.annotation.Nullable;
import android.os.RemoteException;
import android.ranging.RangingCapabilities;
import android.ranging.RangingManager;
import android.ranging.uwb.UwbRangingCapabilities;
import android.util.Log;

import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.uwb.UwbAdapter;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.uwb.support.fira.FiraParams;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CapabilitiesProvider {

    private static final int AVAILABILITY_TIMEOUT = 3;
    private static final String TAG = CapabilitiesProvider.class.getSimpleName();
    private final RangingInjector mRangingInjector;
    @Nullable
    private UwbAdapter mUwbAdapter;
    @Nullable
    private CsAdapter mCsAdapter;

    private final ExecutorService mExecutorService;


    //TODO: Add support for registering state changes for each ranging technologies and update
    // all callbacks registered.
    public CapabilitiesProvider(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        mExecutorService = Executors.newSingleThreadExecutor();
        if (UwbAdapter.isSupported(mRangingInjector.getContext())) {
            mUwbAdapter = new UwbAdapter(mRangingInjector.getContext(),
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    FiraParams.RANGING_DEVICE_TYPE_CONTROLLER);
        }
        if (CsAdapter.isSupported(mRangingInjector.getContext())) {
            mCsAdapter = new CsAdapter();
        }
    }

    public RangingCapabilities getCapabilities() throws RemoteException {
        RangingCapabilities.Builder builder = new RangingCapabilities.Builder();
        FutureTask<Void> uwbFutureTask = new FutureTask<>(() -> {
            if (mUwbAdapter == null) {
                builder.addAvailability(RangingManager.RangingTechnology.UWB,
                        RangingManager.RangingTechnologyAvailability.NOT_SUPPORTED);
            } else {
                ListenableFuture<com.android.ranging.uwb.backend.internal.RangingCapabilities>
                        future =
                        mUwbAdapter.getCapabilities();
                try {
                    com.android.ranging.uwb.backend.internal.RangingCapabilities capabilities =
                            future.get(AVAILABILITY_TIMEOUT, TimeUnit.SECONDS);
                    UwbRangingCapabilities uwbRangingCapabilities =
                            new UwbRangingCapabilities.Builder()
                                    .setSupportsDistance(capabilities.supportsDistance())
                                    .setSupportsAzimuthalAngle(
                                            capabilities.supportsAzimuthalAngle())
                                    .setSupportsElevationAngle(
                                            capabilities.supportsElevationAngle())
                                    .setSupportsRangingIntervalReconfigure(
                                            capabilities.supportsRangingIntervalReconfigure())
                                    .setMinRangingInterval(capabilities.getMinRangingInterval())
                                    .setSupportedChannels(capabilities.getSupportedChannels())
                                    .setSupportedNtfConfigs(capabilities.getSupportedNtfConfigs())
                                    .setSupportedConfigIds(capabilities.getSupportedConfigIds())
                                    .setSupportedSlotDurations(
                                            capabilities.getSupportedSlotDurations())
                                    .setSupportedRangingUpdateRates(
                                            capabilities.getSupportedRangingUpdateRates())
                                    .setHasBackgroundRangingSupport(
                                            capabilities.hasBackgroundRangingSupport())
                                    .build();

                    builder.addAvailability(RangingManager.RangingTechnology.UWB,
                                    RangingManager.RangingTechnologyAvailability.ENABLED)
                            .setUwbRangingCapabilities(uwbRangingCapabilities);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                }
            }
            return null;
        });

        FutureTask<Void> csFutureTask = new FutureTask<>(() -> {
            if (mCsAdapter == null) {
                builder.addAvailability(RangingManager.RangingTechnology.BT_CS,
                        RangingManager.RangingTechnologyAvailability.NOT_SUPPORTED);
            } else {
                // TODO add CS support
            }
            return null;
        });

        mExecutorService.submit(uwbFutureTask);
        mExecutorService.submit(csFutureTask);

        try {
            // Wait for both tasks to complete (or timeout)
            uwbFutureTask.get(AVAILABILITY_TIMEOUT, TimeUnit.SECONDS);
            csFutureTask.get(AVAILABILITY_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            Log.e(TAG, "Timed out while fetching ranging capabilities");
        }
        return builder.build();
    }
}
