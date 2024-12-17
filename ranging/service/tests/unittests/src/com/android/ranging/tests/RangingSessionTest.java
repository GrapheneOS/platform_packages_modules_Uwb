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

import static com.android.server.ranging.RangingTechnology.CS;
import static com.android.server.ranging.RangingTechnology.RTT;
import static com.android.server.ranging.RangingTechnology.UWB;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.SensorFusionParams;
import android.ranging.SessionHandle;
import android.ranging.raw.RawInitiatorRangingConfig;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingAdapter.Callback.ClosedReason;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.session.RangingSessionConfig.MulticastTechnologyConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.session.RangingSessionConfig.UnicastTechnologyConfig;
import com.android.server.ranging.session.RawInitiatorRangingSession;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("ConstantConditions")
@RunWith(JUnit4.class)
@SmallTest
public class RangingSessionTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock AttributionSource mMockAttributionSource;
    private @Mock SessionHandle mMockSessionHandle;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingInjector mMockInjector;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingSessionConfig mMockConfig;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RawInitiatorRangingConfig mMockParams;
    private @Mock RangingServiceManager.SessionListener mMockSessionListener;
    private Map<TechnologyConfig, RangingAdapter> mMockAdapters;
    private RawInitiatorRangingSession mSession;

    private void configureSession(Set<TechnologyConfig> technologyConfigs) {
        // Create some mock adapters for this session.
        for (TechnologyConfig config : technologyConfigs) {
            RangingAdapter adapter = mock(RangingAdapter.class);
            mMockAdapters.put(config, adapter);
            when(mMockInjector.createAdapter(eq(config), anyInt(), any())).thenReturn(adapter);
        }

        // Start the session
        when(mMockConfig.getTechnologyConfigs(any())).thenReturn(
                ImmutableSet.copyOf(technologyConfigs)
        );
    }

    /**
     * Starts adapters for the session with the provided configs.
     *
     * @param technologyConfigs to use for the session.
     * @return {@link RangingAdapter.Callback} for each of the provided configs.
     * These callbacks are captured from underlying {@link RangingAdapter} mock for each config.
     */
    private Map<TechnologyConfig, RangingAdapter.Callback> mockStartAdapters(
            Set<TechnologyConfig> technologyConfigs
    ) {
        ImmutableMap.Builder<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                ImmutableMap.builder();

        for (TechnologyConfig config : technologyConfigs) {
            ArgumentCaptor<RangingAdapter.Callback> callbackCaptor =
                    ArgumentCaptor.forClass(RangingAdapter.Callback.class);

            verify(mMockAdapters.get(config)).start(eq(config), any(), callbackCaptor.capture());

            if (config instanceof MulticastTechnologyConfig c) {
                c.getPeerDevices().forEach(callbackCaptor.getValue()::onStarted);
            } else if (config instanceof UnicastTechnologyConfig c) {
                callbackCaptor.getValue().onStarted(c.getPeerDevice());
            }

            adapterCallbacks.put(config, callbackCaptor.getValue());
        }

        return adapterCallbacks.build();
    }

    private void mockStopAdapters(Map<TechnologyConfig, RangingAdapter.Callback> callbacks) {
        for (TechnologyConfig config : callbacks.keySet()) {
            verify(mMockAdapters.get(config)).stop();

            callbacks.get(config).onClosed(ClosedReason.REQUESTED);
            if (config instanceof MulticastTechnologyConfig c) {
                c.getPeerDevices().forEach(callbacks.get(config)::onStopped);
            } else if (config instanceof UnicastTechnologyConfig c) {
                callbacks.get(config).onStopped(c.getPeerDevice());
            }
        }
    }

    private MulticastTechnologyConfig mockTechnologyConfig(
            RangingTechnology technology, Set<RangingDevice> peers
    ) {
        MulticastTechnologyConfig config = mock(MulticastTechnologyConfig.class);
        when(config.getTechnology()).thenReturn(technology);
        when(config.getPeerDevices()).thenReturn(ImmutableSet.copyOf(peers));
        return config;
    }

    private UnicastTechnologyConfig mockTechnologyConfig(
            RangingTechnology technology, RangingDevice peer
    ) {
        UnicastTechnologyConfig config = mock(UnicastTechnologyConfig.class);
        when(config.getTechnology()).thenReturn(technology);
        when(config.getPeerDevice()).thenReturn(peer);
        return config;
    }


    /** @param technology to generate data for */
    private RangingData generateData(RangingTechnology technology) {
        return new RangingData.Builder()
                .setRangingTechnology(technology.getValue())
                .setDistance(new RangingMeasurement.Builder().setMeasurement(123).build())
                .setTimestampMillis(1)
                .build();
    }

    @Before
    public void setup() {
        when(mMockConfig.getSessionConfig().getSensorFusionParams()).thenReturn(
                new SensorFusionParams.Builder().setSensorFusionEnabled(true).build()
        );

        mSession = new RawInitiatorRangingSession(
                mMockAttributionSource, mMockSessionHandle, mMockInjector, mMockConfig,
                mMockSessionListener, MoreExecutors.newDirectExecutorService());

        mMockAdapters = Maps.newHashMap();
    }

    @Test
    public void start_startsUnicastTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        Set<TechnologyConfig> configs = Set.of(mockTechnologyConfig(UWB, peer));

        configureSession(configs);
        mSession.start(mMockParams);
        mockStartAdapters(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(peer), eq(UWB));
    }

    @Test
    public void start_startsMultipleUnicastTechnologies() {
        RangingDevice peer = mock(RangingDevice.class);
        Set<TechnologyConfig> configs = Set.of(
                mockTechnologyConfig(UWB, peer),
                mockTechnologyConfig(RTT, peer));

        configureSession(configs);
        mSession.start(mMockParams);
        mockStartAdapters(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(peer), eq(UWB));
        verify(mMockSessionListener).onTechnologyStarted(eq(peer), eq(RTT));
    }

    @Test
    public void start_startsMultipleOfTheSameTechnology() {
        List<RangingDevice> peers = List.of(mock(RangingDevice.class), mock(RangingDevice.class));
        Set<TechnologyConfig> configs = Set.of(
                mockTechnologyConfig(UWB, peers.get(0)),
                mockTechnologyConfig(UWB, peers.get(1)));

        configureSession(configs);
        mSession.start(mMockParams);
        mockStartAdapters(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(peers.get(0)), eq(UWB));
        verify(mMockSessionListener).onTechnologyStarted(eq(peers.get(1)), eq(UWB));
    }

    @Test
    public void start_startsMulticastTechnology() {
        List<RangingDevice> peers = List.of(mock(RangingDevice.class), mock(RangingDevice.class));
        Set<TechnologyConfig> configs = Set.of(mockTechnologyConfig(UWB, Set.copyOf(peers)));

        configureSession(configs);
        mSession.start(mMockParams);
        mockStartAdapters(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(peers.get(0)), eq(UWB));
        verify(mMockSessionListener).onTechnologyStarted(eq(peers.get(1)), eq(UWB));
    }

    @Test
    public void start_doesNotStartUnusedTechnology() {
        Set<TechnologyConfig> configs = Set.of(
                mockTechnologyConfig(UWB, mock(RangingDevice.class)));

        configureSession(configs);
        mSession.start(mMockParams);
        mockStartAdapters(configs);

        verify(mMockSessionListener, never()).onTechnologyStarted(any(), eq(CS));
    }

    @Test
    public void stop_stopsUnicastTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        Set<TechnologyConfig> configs = Set.of(mockTechnologyConfig(UWB, peer));

        configureSession(configs);
        mSession.start(mMockParams);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                mockStartAdapters(configs);

        mSession.stop();
        mockStopAdapters(adapterCallbacks);

        verify(mMockSessionListener).onTechnologyStopped(eq(peer), eq(UWB));
        verify(mMockSessionListener).onSessionStopped(ClosedReason.REQUESTED);
    }

    @Test
    public void stop_stopsMultipleUnicastTechnologies() {
        RangingDevice peer = mock(RangingDevice.class);
        Set<TechnologyConfig> configs = Set.of(
                mockTechnologyConfig(UWB, peer),
                mockTechnologyConfig(RTT, peer));

        configureSession(configs);
        mSession.start(mMockParams);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                mockStartAdapters(configs);

        mSession.stop();
        mockStopAdapters(adapterCallbacks);

        verify(mMockSessionListener).onTechnologyStopped(eq(peer), eq(UWB));
        verify(mMockSessionListener).onTechnologyStopped(eq(peer), eq(UWB));
        verify(mMockSessionListener).onSessionStopped(eq(ClosedReason.REQUESTED));
    }

    @Test
    public void stop_stopsMulticastTechnology() {
        List<RangingDevice> peers = List.of(mock(RangingDevice.class), mock(RangingDevice.class));
        Set<TechnologyConfig> configs = Set.of(mockTechnologyConfig(UWB, Set.copyOf(peers)));

        configureSession(configs);
        mSession.start(mMockParams);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                mockStartAdapters(configs);

        mSession.stop();
        mockStopAdapters(adapterCallbacks);

        verify(mMockSessionListener).onTechnologyStopped(eq(peers.get(0)), eq(UWB));
        verify(mMockSessionListener).onTechnologyStopped(eq(peers.get(1)), eq(UWB));
        verify(mMockSessionListener).onSessionStopped(eq(ClosedReason.REQUESTED));
    }

    @Test
    public void shouldStop_whenTechnologyStops() {
        RangingDevice peer = mock(RangingDevice.class);
        UnicastTechnologyConfig config = mockTechnologyConfig(UWB, peer);

        configureSession(Set.of(config));
        mSession.start(mMockParams);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                mockStartAdapters(Set.of(config));

        adapterCallbacks.get(config).onStopped(peer);
        adapterCallbacks.get(config).onClosed(ClosedReason.LOST_CONNECTION);

        verify(mMockSessionListener).onTechnologyStopped(eq(peer), eq(UWB));
        verify(mMockSessionListener).onSessionStopped(eq(ClosedReason.LOST_CONNECTION));
    }

    @Test
    public void shouldStop_whenTechnologyFailsToStart() {
        RangingDevice peer = mock(RangingDevice.class);
        UnicastTechnologyConfig config = mockTechnologyConfig(UWB, peer);

        configureSession(Set.of(config));
        mSession.start(mMockParams);

        ArgumentCaptor<RangingAdapter.Callback> adapterCallbacks =
                ArgumentCaptor.forClass(RangingAdapter.Callback.class);
        verify(mMockAdapters.get(config)).start(eq(config), any(), adapterCallbacks.capture());

        adapterCallbacks.getValue().onClosed(ClosedReason.FAILED_TO_START);

        verify(mMockSessionListener).onSessionStopped(eq(ClosedReason.FAILED_TO_START));
    }

    @Test
    public void shouldReportData_fromTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        RangingData data = generateData(UWB);

        UnicastTechnologyConfig config = mockTechnologyConfig(UWB, peer);

        configureSession(Set.of(config));
        mSession.start(mMockParams);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                mockStartAdapters(Set.of(config));

        adapterCallbacks.get(config).onRangingData(peer, data);
        verify(mMockSessionListener).onResults(
                eq(peer),
                argThat((arg) -> arg.getRangingTechnology() == UWB.getValue())
        );
        verify(mMockSessionListener).onTechnologyStarted(eq(peer), eq(UWB));
    }
}
