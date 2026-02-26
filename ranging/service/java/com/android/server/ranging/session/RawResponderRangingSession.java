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

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import android.content.AttributionSource;
import android.ranging.RangingConfig;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.raw.RawResponderRangingConfig;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.common.ConfigurationUtils;
import com.android.server.ranging.common.RangingUtils;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Collections;

public class RawResponderRangingSession extends BaseRangingSession implements RangingSession {
    private static final String TAG = RawResponderRangingSession.class.getSimpleName();

    public RawResponderRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull SessionConfig sessionConfig,
            @NonNull RangingConfig rangingConfig,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        super(attributionSource, sessionHandle, injector, sessionConfig, rangingConfig,
                listener, adapterExecutor);
    }

    @Override
    public void start(@NonNull RangingConfig rangingConfig) {
        if (!(rangingConfig instanceof RawResponderRangingConfig config)) {
            Log.e(TAG, "Unexpected configuration object for raw responder session "
                    + rangingConfig.getClass());
            mSessionListener.onSessionClosed(RangingUtils.InternalReason.INTERNAL_ERROR);
            return;
        }

        ImmutableSet<TechnologyConfig> configs = ConfigurationUtils.groupByTechnology(
                Collections.singleton(config.getRawRangingDevice()), mSessionConfig,
                DEVICE_ROLE_RESPONDER);

        super.start(configs);
    }

    @Override
    public void appForegroundStateUpdated(boolean appInForeground) {
        super.appForegroundStateUpdated(appInForeground);
    }

    @Override
    public void close() { }
}
