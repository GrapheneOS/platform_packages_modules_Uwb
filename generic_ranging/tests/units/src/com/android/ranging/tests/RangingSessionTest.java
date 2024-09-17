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

package com.android.ranging.tests;

import static com.android.ranging.RangingTechnology.CS;
import static com.android.ranging.RangingTechnology.UWB;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.filters.SmallTest;

import com.android.ranging.RangingAdapter;
import com.android.ranging.RangingConfig;
import com.android.ranging.RangingData;
import com.android.ranging.RangingParameters;
import com.android.ranging.RangingParameters.DeviceRole;
import com.android.ranging.RangingSession;
import com.android.ranging.RangingSessionImpl;
import com.android.ranging.RangingTechnology;
import com.android.ranging.cs.CsParameters;
import com.android.ranging.fusion.DataFusers;
import com.android.ranging.fusion.FilteringFusionEngine;
import com.android.ranging.uwb.UwbParameters;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.EnumMap;
import java.util.concurrent.ScheduledExecutorService;

@RunWith(JUnit4.class)
@SmallTest
public class RangingSessionTest {
    @Rule public final MockitoRule mMockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Context mMockContext;
    @Mock private RangingConfig mMockConfig;
    @Mock
    private ScheduledExecutorService mMockTimeoutExecutor;

    @Mock private RangingSession.Callback mMockCallback;
    private final EnumMap<RangingTechnology, RangingAdapter> mMockAdapters =
            new EnumMap<>(RangingTechnology.class);

    private RangingSessionImpl mSession;

    /**
     * Starts a ranging session with the provided parameters.
     * @param params to use for the session.
     * @return {@link RangingAdapter.Callback} for each of the provided technologies' adapters.
     * These callbacks are captured from underlying {@link RangingAdapter} mock for each technology.
     */
    private EnumMap<RangingTechnology, RangingAdapter.Callback> startSession(
            RangingParameters params
    ) {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                new EnumMap<>(RangingTechnology.class);

        mSession.start(params, mMockCallback);

        for (RangingTechnology technology : params.asMap().keySet()) {
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
                .setTechnology(technology)
                .setRangeDistance(123)
                .setTimestamp(Duration.ofSeconds(1))
                .setPeerAddress(new byte[]{0x1, 0x2})
                .build();
    }

    @Before
    public void setup() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(true);
        when(mMockConfig.getInitTimeout()).thenReturn(Duration.ZERO);
        when(mMockConfig.getNoUpdateTimeout()).thenReturn(Duration.ZERO);
        when(mMockConfig.getUseFusingAlgorithm()).thenReturn(true);

        mSession = new RangingSessionImpl(
                mMockContext, mMockConfig,
                new FilteringFusionEngine(new DataFusers.PassthroughDataFuser()),
                mMockTimeoutExecutor,
                MoreExecutors.newDirectExecutorService());

        for (RangingTechnology technology : RangingTechnology.values()) {
            RangingAdapter adapter = mock(RangingAdapter.class);
            mMockAdapters.put(technology, adapter);
            mSession.useAdapterForTesting(technology, adapter);
        }
    }

    @Test
    public void start_startsTechnologyThenSession() {
        InOrder inOrder = Mockito.inOrder(mMockCallback);

        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                        .useUwb(mock(UwbParameters.class))
                        .build());

        inOrder.verify(mMockCallback).onStarted(eq(UWB));
        verify(mMockCallback, never()).onStarted(eq(null));

        adapterCallbacks.get(UWB).onRangingData(generateData(UWB));
        inOrder.verify(mMockCallback).onStarted(eq(null));
    }

    @Test
    @Ignore("TODO: Add support for technologies other than UWB")
    public void start_startsMultipleTechnologies() {
        startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                        .useUwb(mock(UwbParameters.class))
                        .useCs(mock(CsParameters.class))
                        .build());

//        verify(mMockCallback).onStarted(eq(null));
        verify(mMockCallback).onStarted(eq(UWB));
        verify(mMockCallback).onStarted(eq(CS));
    }

    @Test
    public void start_doesNotStartUnsupportedTechnologies() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(false);

        mSession.start(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                        .useUwb(mock(UwbParameters.class))
                        .build(),
                mMockCallback);

        verify(mMockAdapters.get(UWB), never()).start(any(), any());
        verify(mMockCallback, never()).onStarted(any());
    }

    @Test
    public void start_doesNotStartUnusedTechnologies() {
        startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                .useUwb(mock(UwbParameters.class))
                .build());

        verify(mMockAdapters.get(CS), never()).start(any(), any());
        verify(mMockCallback, never()).onStarted(eq(CS));
    }

    @Test
    public void stop_stopsTechnologyAndSession() {
        InOrder inOrder = Mockito.inOrder(mMockCallback);

        startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                .useUwb(mock(UwbParameters.class))
                .build());

        mSession.stop();

        verify(mMockAdapters.get(UWB)).stop();
        inOrder.verify(mMockCallback).onStopped(UWB,
                RangingAdapter.Callback.StoppedReason.REQUESTED);
        inOrder.verify(mMockCallback).onStopped(null,
                RangingAdapter.Callback.StoppedReason.REQUESTED);
    }

    @Test
    @Ignore("TODO: Add support for technologies other than UWB")
    public void stop_stopsMultipleTechnologies() {
        startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                .useUwb(mock(UwbParameters.class))
                .useCs(mock(CsParameters.class))
                .build());

        mSession.stop();

        verify(mMockAdapters.get(UWB)).stop();
        verify(mMockAdapters.get(CS)).stop();
        verify(mMockCallback).onStopped(UWB, RangingAdapter.Callback.StoppedReason.REQUESTED);
        verify(mMockCallback).onStopped(CS, RangingAdapter.Callback.StoppedReason.REQUESTED);
        verify(mMockCallback).onStopped(null, RangingAdapter.Callback.StoppedReason.REQUESTED);
    }

    @Test
    public void shouldStop_whenAdapterStops() {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                        .useUwb(mock(UwbParameters.class))
                        .build());

        adapterCallbacks.get(UWB).onStopped(RangingAdapter.Callback.StoppedReason.LOST_CONNECTION);

        verify(mMockCallback).onStopped(UWB, RangingAdapter.Callback.StoppedReason.LOST_CONNECTION);
    }

    @Test
    public void shouldStop_whenNoInitialDataIsReported() {
        startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER).build());

        ArgumentCaptor<Runnable> onTimeoutCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockTimeoutExecutor).scheduleWithFixedDelay(onTimeoutCaptor.capture(),
                anyLong(), anyLong(), any());

        onTimeoutCaptor.getValue().run();

        verify(mMockCallback).onStopped(eq(null),
                eq(RangingSession.Callback.StoppedReason.NO_INITIAL_DATA_TIMEOUT));
    }

    @Test
    public void shouldReportData_fromAdapter() {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(new RangingParameters.Builder(DeviceRole.CONTROLLER)
                        .useUwb(mock(UwbParameters.class))
                        .build());

        adapterCallbacks.get(UWB).onRangingData(generateData(UWB));

        verify(mMockCallback).onData(any(RangingData.class));
    }
}
