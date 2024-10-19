/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.core.uwb.backend.impl;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.uwb.backend.IUwb;
import androidx.core.uwb.backend.IUwbAvailabilityObserver;
import androidx.core.uwb.backend.IUwbClient;
import androidx.core.uwb.backend.impl.internal.UwbAvailabilityCallback;
import androidx.core.uwb.backend.impl.internal.UwbFeatureFlags;
import androidx.core.uwb.backend.impl.internal.UwbServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Uwb service entry point of the backend. */
public class UwbService extends Service {

    private UwbServiceImpl mUwbServiceImpl;
    private static final String TAG = "UwbService";

    private List<IUwbAvailabilityObserver> mUwbAvailabilityObservers = new ArrayList<>();
    @Override
    public void onCreate() {
        super.onCreate();
        UwbFeatureFlags uwbFeatureFlags = new UwbFeatureFlags.Builder()
                .setSkipRangingCapabilitiesCheck(Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2)
                .setReversedByteOrderFiraParams(
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU)
                .build();
        UwbAvailabilityCallback uwbAvailabilityCallback = (isUwbAvailable, reason) -> {
            for (IUwbAvailabilityObserver observer : mUwbAvailabilityObservers) {
                if (observer != null) {
                    try {
                        observer.onUwbStateChanged(isUwbAvailable, reason);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Availability observer error");
                    }
                }
            }
        };
        mUwbServiceImpl = new UwbServiceImpl(this, uwbFeatureFlags, uwbAvailabilityCallback);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mUwbServiceImpl.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }

    private final IUwb.Stub mBinder =
            new IUwb.Stub() {
                @Override
                public IUwbClient getControleeClient() {
                    Log.i(TAG, "Getting controleeClient");
                    return new UwbControleeClient(mUwbServiceImpl
                            .getControlee(UwbService.this.getApplicationContext()),
                            mUwbServiceImpl);
                }

                @Override
                public IUwbClient getControllerClient() {
                    Log.i(TAG, "Getting controllerClient");
                    Consumer<IUwbAvailabilityObserver> subscribeConsumer =
                            (observer) -> {
                                mUwbAvailabilityObservers.add(observer);
                                try {
                                    observer.onUwbStateChanged(mUwbServiceImpl.isAvailable(),
                                            mUwbServiceImpl.getLastStateChangeReason());
                                } catch (RemoteException e) {
                                    throw new RuntimeException(e);
                                }
                            };
                    Consumer<IUwbAvailabilityObserver> unsubscribeConsumer =
                            (observer) -> mUwbAvailabilityObservers.remove(observer);
                    UwbControllerClient client = new UwbControllerClient(mUwbServiceImpl
                            .getController(UwbService.this.getApplicationContext()),
                            mUwbServiceImpl);
                    client.setSubscribeConsumer(subscribeConsumer);
                    client.setUnsubscribeConsumer(unsubscribeConsumer);
                    return client;
                }

                @Override
                public int getInterfaceVersion() {
                    return this.VERSION;
                }

                @Override
                public String getInterfaceHash() {
                    return this.HASH;
                }
            };
}
