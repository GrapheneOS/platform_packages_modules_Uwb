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

package com.android.server.ranging.session;

import android.content.AttributionSource;
import android.ranging.SessionHandle;
import android.ranging.raw.RawInitiatorRangingParams;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Set;

public class RawInitiatorRangingSession
        extends BaseRangingSession
        implements RangingSession<RawInitiatorRangingParams> {

    public RawInitiatorRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull RangingSessionConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, adapterExecutor);
    }

    @Override
    public void start(@NonNull RawInitiatorRangingParams params) {
        super.start(mConfig.getTechnologyConfigs(Set.copyOf(params.getRawRangingDevices())));
    }
}
