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

package com.android.ranging.generic.ranging;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import com.android.ranging.generic.RangingTechnology;

import com.google.common.util.concurrent.ListenableFuture;

/** Channel Sounding adapter for ranging. */
class CsAdapter implements RangingAdapter {

    @Override
    public RangingTechnology getType() {
        return RangingTechnology.CS;
    }

    @Override
    public boolean isPresent() {
        return false;
    }

    @Override
    public ListenableFuture<Boolean> isEnabled() {
        return immediateFuture(false);
    }

    @Override
    public void start(Callback callback) {
        throw new UnsupportedOperationException("Not implemented.");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Not implemented.");
    }
}