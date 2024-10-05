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

import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.uwb.UwbAdapter;

import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.Executors;

public class AdapterProvider {

    private final RangingInjector mRangingInjector;

    private @Nullable UwbAdapter mUwbAdapter;
    private @Nullable CsAdapter mCsAdapter;

    public AdapterProvider(RangingInjector rangingInjector) {
        mRangingInjector = rangingInjector;
        if (UwbAdapter.isSupported(mRangingInjector.getContext())) {
            mUwbAdapter = new UwbAdapter(mRangingInjector.getContext(),
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    RangingParameters.DeviceRole.INITIATOR);
        }
        if (CsAdapter.isSupported(mRangingInjector.getContext())) {
            mCsAdapter = new CsAdapter();
        }
    }

    public @Nullable UwbAdapter getUwbAdapter() {
        return mUwbAdapter;
    }

    public @Nullable CsAdapter getCsAdapter() {
        return mCsAdapter;
    }
}
