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
import android.content.AttributionSource;
import android.content.Context;
import android.ranging.IRangingAdapter;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;

public class RangingServiceImpl extends IRangingAdapter.Stub {

    private static final String TAG = "RangingServiceImpl";
    private final RangingInjector mRangingInjector;
    private final Context mContext;

    RangingServiceImpl(@NonNull Context context, @NonNull RangingInjector rangingInjector) {
        mContext = context;
        mRangingInjector = rangingInjector;
    }

    @Override
    public void getRangingCapabilities(IRangingCapabilitiesCallback callback) {
        mRangingInjector.getRangingServiceManager().getRangingCapabilities(callback);
    }

    @Override
    public void startRanging(AttributionSource attributionSource, SessionHandle sessionHandle,
            RangingPreference rangingPreference, IRangingCallbacks callbacks) {
        mRangingInjector.getRangingServiceManager().startRanging(attributionSource, sessionHandle,
                rangingPreference, callbacks);
    }

    @Override
    public void stopRanging(SessionHandle sessionHandle) {
        mRangingInjector.getRangingServiceManager().stopRanging(sessionHandle);
    }
}
