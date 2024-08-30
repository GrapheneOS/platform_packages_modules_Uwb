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
import com.android.ranging.RangingReport;
import com.android.ranging.RangingSession;
import com.android.ranging.RangingSessionImpl;
import com.android.ranging.RangingTechnology;
import com.android.ranging.cs.CsParameters;
import com.android.ranging.uwb.UwbParameters;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
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
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
@SmallTest
public class RangingSessionTest {
    @Rule public final MockitoRule mMockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Context mMockContext;
    @Mock private RangingConfig mMockConfig;
    @Mock private ScheduledExecutorService mMockUpdateExecutor;

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

    @Before
    public void setup() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(true);
        when(mMockConfig.getMaxUpdateInterval()).thenReturn(Duration.ZERO);

        mSession = new RangingSessionImpl(mMockContext, mMockConfig, mMockUpdateExecutor,
                MoreExecutors.newDirectExecutorService());

        for (RangingTechnology technology : RangingTechnology.values()) {
            RangingAdapter adapter = mock(RangingAdapter.class);
            mMockAdapters.put(technology, adapter);
            mSession.useAdapterForTesting(technology, adapter);
        }
    }

    @Test
    public void start_startsSessionAndTechnology() {
        InOrder inOrder = Mockito.inOrder(mMockCallback);

        startSession(new RangingParameters.Builder().useUwb(mock(UwbParameters.class)).build());

        inOrder.verify(mMockCallback).onStarted(eq(null));
        inOrder.verify(mMockCallback).onStarted(eq(UWB));
    }

    @Test
    @Ignore("TODO: Add support for technologies other than UWB")
    public void start_startsMultipleTechnologies() {
        startSession(new RangingParameters.Builder()
                        .useUwb(mock(UwbParameters.class))
                        .useCs(mock(CsParameters.class))
                        .build());

        verify(mMockCallback).onStarted(eq(null));
        verify(mMockCallback).onStarted(eq(UWB));
        verify(mMockCallback).onStarted(eq(CS));
    }

    @Test
    public void start_doesNotStartUnsupportedTechnologies() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(false);

        mSession.start(new RangingParameters.Builder().useUwb(mock(UwbParameters.class)).build(),
                mMockCallback);

        verify(mMockAdapters.get(UWB), never()).start(any(), any());
        verify(mMockCallback, never()).onStarted(any());
    }

    @Test
    public void start_doesNotStartUnusedTechnologies() {
        startSession(new RangingParameters.Builder().useUwb(mock(UwbParameters.class)).build());

        verify(mMockAdapters.get(CS), never()).start(any(), any());
        verify(mMockCallback, never()).onStarted(eq(CS));
    }

    @Test
    public void stop_stopsTechnologyAndSession() {
        InOrder inOrder = Mockito.inOrder(mMockCallback);

        startSession(new RangingParameters.Builder().useUwb(mock(UwbParameters.class)).build());

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
        startSession(new RangingParameters.Builder()
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
                startSession(new RangingParameters.Builder()
                        .useUwb(mock(UwbParameters.class))
                        .build());

        adapterCallbacks.get(UWB).onStopped(RangingAdapter.Callback.StoppedReason.LOST_CONNECTION);

        verify(mMockCallback).onStopped(UWB, RangingAdapter.Callback.StoppedReason.LOST_CONNECTION);
    }

    @Test
    public void shouldStop_whenSessionIsEmpty() {
        startSession(new RangingParameters.Builder().build());

        ArgumentCaptor<Runnable> periodicUpdateCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockUpdateExecutor).scheduleWithFixedDelay(periodicUpdateCaptor.capture(),
                anyLong(), anyLong(), any());

        periodicUpdateCaptor.getValue().run();

        verify(mMockCallback).onStopped(eq(null),
                eq(RangingSession.Callback.StoppedReason.EMPTY_SESSION_TIMEOUT));
    }

    @Test
    public void shouldReportDataImmediately_whenUpdateIntervalIsZero() {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(new RangingParameters.Builder()
                        .useUwb(mock(UwbParameters.class))
                        .build());

        RangingReport report = mock(RangingReport.class);
        when(report.getRangingTechnology()).thenReturn(UWB);
        adapterCallbacks.get(UWB).onRangingData(report);

        ArgumentCaptor<RangingData> dataCaptor = ArgumentCaptor.forClass(RangingData.class);
        verify(mMockCallback).onData(dataCaptor.capture());

        Assert.assertTrue(dataCaptor.getValue().getRangingReports().isPresent());
        Assert.assertEquals(1, dataCaptor.getValue().getRangingReports().get().size());
        Assert.assertTrue(dataCaptor.getValue().getRangingReports().get().contains(report));
    }

    @Test
    @Ignore("TODO: Add support for technologies other than UWB")
    public void shouldAggregateReportsFromAdapters_whenUpdateIntervalIsNotZero() {
        Duration updateInterval = Duration.ofMillis(234);
        when(mMockConfig.getUseFusingAlgorithm()).thenReturn(false);
        when(mMockConfig.getMaxUpdateInterval()).thenReturn(updateInterval);
        when(mMockConfig.getInitTimeout()).thenReturn(Duration.ofSeconds(3));

        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(new RangingParameters.Builder()
                        .useUwb(mock(UwbParameters.class))
                        .useCs(mock(CsParameters.class))
                        .build());

        ArgumentCaptor<Runnable> periodicUpdateCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mMockUpdateExecutor).scheduleWithFixedDelay(periodicUpdateCaptor.capture(),
                anyLong(), eq(updateInterval.toMillis()), eq(TimeUnit.MILLISECONDS));

        RangingReport uwbReport = mock(RangingReport.class);
        when(uwbReport.getRangingTechnology()).thenReturn(UWB);
        adapterCallbacks.get(UWB).onRangingData(uwbReport);

        RangingReport csReport = mock(RangingReport.class);
        when(csReport.getRangingTechnology()).thenReturn(CS);
        adapterCallbacks.get(CS).onRangingData(csReport);

        periodicUpdateCaptor.getValue().run();

        ArgumentCaptor<RangingData> dataCaptor = ArgumentCaptor.forClass(RangingData.class);
        verify(mMockCallback).onData(dataCaptor.capture());

        Assert.assertTrue(dataCaptor.getValue().getRangingReports().isPresent());
        Assert.assertEquals(2, dataCaptor.getValue().getRangingReports().get().size());
        Assert.assertTrue(dataCaptor.getValue().getRangingReports().get().contains(uwbReport));
        Assert.assertTrue(dataCaptor.getValue().getRangingReports().get().contains(csReport));
    }
}
