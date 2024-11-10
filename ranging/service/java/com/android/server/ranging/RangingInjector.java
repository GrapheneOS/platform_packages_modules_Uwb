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

import android.annotation.NonNull;
import android.content.Context;
import android.ranging.RangingPreference;

import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.rtt.RttAdapter;
import com.android.server.ranging.uwb.UwbAdapter;

import com.google.common.util.concurrent.ListeningExecutorService;

public class RangingInjector {

    private static final String TAG = "RangingInjector";

    private final Context mContext;
    private final RangingServiceManager mRangingServiceManager;

    private final CapabilitiesProvider mCapabilitiesProvider;

    public RangingInjector(@NonNull Context context) {
        mContext = context;
        mCapabilitiesProvider = new CapabilitiesProvider(this);
        mRangingServiceManager = new RangingServiceManager(this);
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
            @NonNull RangingTechnology technology, @RangingPreference.DeviceRole int role,
            @NonNull ListeningExecutorService executor
    ) {
        switch (technology) {
            case UWB:
                return new UwbAdapter(mContext, executor, role);
            case CS:
                return new CsAdapter();
            case RTT:
                return new RttAdapter(mContext, executor, role);
            default:
                throw new IllegalArgumentException("Adapter does not exist for technology " + this);
        }
    }

}
