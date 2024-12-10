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
import android.ranging.oob.IOobSendDataListener;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingParams;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.CapabilityRequestMessage;
import com.android.server.ranging.oob.OobHandler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;

public class OobInitiatorRangingSession
        extends BaseRangingSession
        implements RangingSession<OobInitiatorRangingParams>, OobHandler {

    private final IOobSendDataListener mOobDataSender;

    private ImmutableSet<OobHandle> mOobHandles;

    public OobInitiatorRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull RangingSessionConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull IOobSendDataListener oobDataSender,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        super(attributionSource, sessionHandle, injector, config, listener, adapterExecutor);
        mOobDataSender = oobDataSender;
        mOobHandles = null;
    }

    @Override
    public void start(@NonNull OobInitiatorRangingParams params) {

        mOobHandles = params.getDeviceHandles()
                .stream().map((handle) -> new OobHandle(mSessionHandle, handle.getRangingDevice()))
                .collect(ImmutableSet.toImmutableSet());

        // TODO
        CapabilityRequestMessage message = CapabilityRequestMessage.builder()
                .setRequestedRangingTechnologies(ImmutableList.of(RangingTechnology.UWB))
                .build();
    }

    @Override
    public void handleOobMessage(OobHandle oobHandle, byte[] data) {
        // TODO
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void handleOobDeviceDisconnected(OobHandle oobHandle) {
        // TODO
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void handleOobDeviceReconnected(OobHandle oobHandle) {
        // TODO
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void handleOobClosed(OobHandle oobHandle) {
        // TODO
        throw new UnsupportedOperationException("Not implemented");
    }
}
