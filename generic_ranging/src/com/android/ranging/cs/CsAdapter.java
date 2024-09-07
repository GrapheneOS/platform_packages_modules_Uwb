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

package com.android.ranging.cs;

import android.content.Context;

import com.android.ranging.RangingAdapter;
import com.android.ranging.RangingParameters.TechnologyParameters;
import com.android.ranging.RangingTechnology;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/** Channel Sounding adapter for ranging. */
public class CsAdapter implements RangingAdapter {

    public static boolean isSupported(Context context) {
        return false;
    }

    public CsAdapter() {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public RangingTechnology getType() {
        return RangingTechnology.CS;
    }

    @Override
    public ListenableFuture<Boolean> isEnabled() {
        return Futures.immediateFuture(false);
    }

    @Override
    public void start(TechnologyParameters parameters, Callback callback) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not implemented.");
    }
}
