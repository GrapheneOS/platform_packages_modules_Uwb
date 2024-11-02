/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.ranging.tests;

import static android.ranging.params.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;

import static com.android.server.ranging.RangingTechnology.CS;
import static com.android.server.ranging.RangingTechnology.UWB;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.ranging.IRangingCallbacks;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.SessionHandle;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingConfig;
import com.android.server.ranging.RangingPeer;
import com.android.server.ranging.RangingSession;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.EnumMap;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(JUnit4.class)
@SmallTest
public class RangingPeerTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) Context mMockContext;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingConfig mMockConfig;
    private @Mock ScheduledExecutorService mMockTimeoutExecutor;
    private @Mock SessionHandle mMockSessionHandle;
    private @Mock IRangingCallbacks mMockCallback;
    private final EnumMap<RangingTechnology, RangingAdapter> mMockAdapters =
            new EnumMap<>(RangingTechnology.class);
    private RangingPeer mSession;

    /**
     * Starts a ranging session with the provided configs.
     *
     * @param configs to use for the session.
     * @return {@link RangingAdapter.Callback} for each of the provided technologies' adapters.
     * These callbacks are captured from underlying {@link RangingAdapter} mock for each technology.
     */
    private EnumMap<RangingTechnology, RangingAdapter.Callback> startSession(
            ImmutableMap<RangingTechnology, RangingConfig.TechnologyConfig> configs
    ) {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                new EnumMap<>(RangingTechnology.class);

        when(mMockConfig.getTechnologyConfigs()).thenReturn(configs);
        mSession.start();

        for (RangingTechnology technology : configs.keySet()) {
            ArgumentCaptor<RangingAdapter.Callback> callbackCaptor =
                    ArgumentCaptor.forClass(RangingAdapter.Callback.class);
            verify(mMockAdapters.get(technology)).start(any(), callbackCaptor.capture());
            callbackCaptor.getValue().onStarted();
            adapterCallbacks.put(technology, callbackCaptor.getValue());
        }

        return adapterCallbacks;
    }

    /** @param technology to generate data for */
    private RangingData generateData(RangingTechnology technology) {
        return new RangingData.Builder()
                .setRangingTechnology(technology.getValue())
                .setDistance(new RangingMeasurement.Builder().setMeasurement(123).build())
                .setTimestamp(1)
                .build();
    }

    private UwbRangingParams.Builder getUwbParams() {
        return new UwbRangingParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{1, 2}))
                .setComplexChannel(new UwbComplexChannel.Builder().setChannel(9).setPreambleIndex(
                        11).build())
                .setConfigId(CONFIG_UNICAST_DS_TWR)
                .setPeerAddress(UwbAddress.fromBytes(new byte[]{3, 4}))
                .setRangingUpdateRate(UPDATE_RATE_NORMAL);
    }

    private UwbConfig.Builder getUwbConfig(UwbRangingParams parameters) {
        return new UwbConfig.Builder(parameters)
                .setCountryCode("US");
    }

    @Before
    public void setup() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(true);
        when(mMockConfig.getNoInitialDataTimeout()).thenReturn(Duration.ZERO);
        when(mMockConfig.getNoUpdatedDataTimeout()).thenReturn(Duration.ZERO);
        when(mMockConfig.createConfiguredFusionEngine()).thenReturn(
                new RangingConfig.NoOpFusionEngine());

        mSession = new RangingPeer(
                mMockContext, MoreExecutors.newDirectExecutorService(), mMockTimeoutExecutor,
                mMockSessionHandle, mMockConfig, mMockCallback);

        for (RangingTechnology technology : RangingTechnology.values()) {
            RangingAdapter adapter = mock(RangingAdapter.class);
            mMockAdapters.put(technology, adapter);
            mSession.useAdapterForTesting(technology, adapter);
        }
    }

    @Test
    @Ignore("TODO: Add support for technologies other than UWB")
    public void start_startsMultipleTechnologies() throws RemoteException {
        startSession(ImmutableMap.of(
                UWB, getUwbConfig(getUwbParams().build()).build(),
                CS, mock(CsConfig.class))
        );

        verify(mMockCallback).onStarted(eq(mMockSessionHandle), eq(UWB.getValue()));
        verify(mMockCallback).onStarted(eq(mMockSessionHandle), eq(CS.getValue()));
    }

    @Test
    public void start_doesNotStartUnsupportedTechnologies() throws RemoteException {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(false);

        ImmutableMap<RangingTechnology, RangingConfig.TechnologyConfig> configs =
                ImmutableMap.of(UWB, getUwbConfig(getUwbParams().build()).build());
        when(mMockConfig.getTechnologyConfigs()).thenReturn(configs);

        mSession.start();

        verify(mMockAdapters.get(UWB), never()).start(any(), any());
        verify(mMockCallback, never()).onStarted(any(), any());
    }

    @Test
    public void start_doesNotStartUnusedTechnologies() throws RemoteException {
        startSession(ImmutableMap.of(UWB, getUwbConfig(getUwbParams().build()).build()));

        verify(mMockAdapters.get(CS), never()).start(any(), any());
        verify(mMockCallback, never()).onStarted(any(), eq(CS).getValue());
    }

    @Test
    public void stop_stopsSession() throws RemoteException {
        startSession(ImmutableMap.of(UWB, getUwbConfig(getUwbParams().build()).build()));

        mSession.stop();

        verify(mMockAdapters.get(UWB)).stop();
        verify(mMockCallback).onClosed(eq(mMockSessionHandle),
                eq(RangingAdapter.Callback.StoppedReason.REQUESTED));
    }

    @Test
    @Ignore("TODO: Add support for technologies other than UWB")
    public void stop_stopsMultipleTechnologies() throws RemoteException {
        startSession(ImmutableMap.of(
                UWB, getUwbConfig(getUwbParams().build()).build(),
                CS, mock(CsConfig.class))
        );

        mSession.stop();

        verify(mMockAdapters.get(UWB)).stop();
        verify(mMockAdapters.get(CS)).stop();
        verify(mMockCallback).onClosed(eq(mMockSessionHandle),
                eq(RangingAdapter.Callback.StoppedReason.REQUESTED));
    }

    @Test
    public void shouldStop_whenAdapterStops() throws RemoteException {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(ImmutableMap.of(UWB, getUwbConfig(getUwbParams().build()).build()));

        adapterCallbacks.get(UWB).onStopped(RangingAdapter.Callback.StoppedReason.LOST_CONNECTION);

        verify(mMockCallback).onClosed(eq(mMockSessionHandle),
                eq(RangingAdapter.Callback.StoppedReason.LOST_CONNECTION));
    }

    @Test
    public void shouldStop_whenNoInitialDataIsReported() throws RemoteException {
        startSession(ImmutableMap.of());

        ArgumentCaptor<Runnable> onTimeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockTimeoutExecutor).schedule(onTimeoutCaptor.capture(), anyLong(), any());

        onTimeoutCaptor.getValue().run();

        verify(mMockCallback).onClosed(mMockSessionHandle,
                eq(RangingSession.Callback.StoppedReason.NO_INITIAL_DATA_TIMEOUT));
    }

    @Test
    public void shouldReportData_fromAdapter() throws RemoteException {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(ImmutableMap.of(UWB, getUwbConfig(getUwbParams().build()).build()));

        adapterCallbacks.get(UWB).onRangingData(mock(RangingDevice.class), generateData(UWB));

        verify(mMockCallback).onData(eq(mMockSessionHandle),
                any(RangingDevice.class),
                any(android.ranging.RangingData.class));
    }
}
