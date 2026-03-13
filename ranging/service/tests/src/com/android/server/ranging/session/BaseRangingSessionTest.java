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

package com.android.server.ranging.session;

import static com.android.server.ranging.RangingTechnology.CS;
import static com.android.server.ranging.RangingTechnology.RTT;
import static com.android.server.ranging.RangingTechnology.UWB;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.content.Context;
import android.ranging.RangingConfig;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

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
public class BaseRangingSessionTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock AttributionSource mMockAttributionSource;
    private @Mock SessionHandle mMockSessionHandle;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingInjector mMockInjector;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) SessionConfig mMockSessionConfig;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingConfig mMockRangingConfig;
    private @Mock RangingServiceManager.SessionListener mMockSessionListener;
    private Map<TechnologyConfig, RangingAdapter> mMockAdapters;
    private BaseRangingSession mSession;
    private @Mock Context mMockContext;
    private @Mock AlarmManager mMockAlarmManager;

    private void setupAdapterMocks(Set<TechnologyConfig> technologyConfigs) {
        // Create some mock adapters for the session.
        for (TechnologyConfig config : technologyConfigs) {
            RangingAdapter adapter = mock(RangingAdapter.class);
            when(adapter.isDynamicUpdatePeersSupported()).thenReturn(true);
            mMockAdapters.put(config, adapter);
            when(mMockInjector.createAdapter(any(), eq(config), any(), any())).thenReturn(adapter);
        }
    }

    /**
     * Starts adapters for the session with the provided configs.
     *
     * @param technologyConfigs to use for the session.
     * @return {@link RangingAdapter.Callback} for each of the provided configs.
     * These callbacks are captured from underlying {@link RangingAdapter} mock for each config.
     */
    private Map<TechnologyConfig, RangingAdapter.Callback> verifyAdaptersStarted(
            Set<TechnologyConfig> technologyConfigs
    ) {
        ImmutableMap.Builder<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                ImmutableMap.builder();

        for (TechnologyConfig config : technologyConfigs) {
            ArgumentCaptor<RangingAdapter.Callback> callbackCaptor =
                    ArgumentCaptor.forClass(RangingAdapter.Callback.class);

            verify(mMockAdapters.get(config)).start(eq(config), any(), callbackCaptor.capture());

            callbackCaptor.getValue().onStarted(config.getPeerDevices());

            adapterCallbacks.put(config, callbackCaptor.getValue());
        }

        return adapterCallbacks.build();
    }

    private void mockStopAdapters(Map<TechnologyConfig, RangingAdapter.Callback> callbacks) {
        for (TechnologyConfig config : callbacks.keySet()) {
            verify(mMockAdapters.get(config)).stop();

            callbacks.get(config).onClosed(InternalReason.LOCAL_REQUEST);
            callbacks.get(config).onStopped(config.getPeerDevices(), InternalReason.LOCAL_REQUEST);
        }
    }

    private TechnologyConfig mockTechnologyConfig(
            RangingTechnology technology, Set<RangingDevice> peers
    ) {
        TechnologyConfig config = mock(TechnologyConfig.class);
        when(config.getTechnology()).thenReturn(technology);
        when(config.getPeerDevices()).thenReturn(ImmutableSet.copyOf(peers));
        return config;
    }

    private TechnologyConfig mockTechnologyConfig(
            RangingTechnology technology, RangingDevice peer
    ) {
        return mockTechnologyConfig(technology, Set.of(peer));
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
        when(mMockSessionConfig.getSensorFusionParams()).thenReturn(
                new SensorFusionParams.Builder().setSensorFusionEnabled(true).build()
        );
        when(mMockInjector.getContext()).thenReturn(mMockContext);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);

        mSession = new BaseRangingSession(
                mMockAttributionSource, mMockSessionHandle, mMockInjector,
                mMockSessionConfig, mMockRangingConfig,
                mMockSessionListener, MoreExecutors.newDirectExecutorService());

        mMockAdapters = Maps.newHashMap();
    }

    @Test
    public void start_startsUnicastTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(mockTechnologyConfig(UWB, peer));

        setupAdapterMocks(configs);
        mSession.start(configs);
        verifyAdaptersStarted(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(Set.of(peer)));
    }

    @Test
    public void start_startsMultipleUnicastTechnologies() {
        RangingDevice peer = mock(RangingDevice.class);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(
                mockTechnologyConfig(UWB, peer),
                mockTechnologyConfig(RTT, peer));

        setupAdapterMocks(configs);
        mSession.start(configs);
        verifyAdaptersStarted(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(Set.of(peer)));
        verify(mMockSessionListener).onTechnologyStarted(eq(RTT), eq(Set.of(peer)));
    }

    @Test
    public void start_startsMultipleOfTheSameTechnology() {
        List<RangingDevice> peers = List.of(mock(RangingDevice.class), mock(RangingDevice.class));
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(
                mockTechnologyConfig(UWB, peers.get(0)),
                mockTechnologyConfig(UWB, peers.get(1)));

        setupAdapterMocks(configs);
        mSession.start(configs);
        verifyAdaptersStarted(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(Set.of(peers.get(0))));
        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(Set.of(peers.get(1))));
    }

    @Test
    public void start_startsMulticastTechnology() {
        Set<RangingDevice> peers = Set.of(mock(RangingDevice.class), mock(RangingDevice.class));
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(
                mockTechnologyConfig(UWB, peers));

        setupAdapterMocks(configs);
        mSession.start(configs);
        verifyAdaptersStarted(configs);

        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(peers));
    }

    @Test
    public void start_doesNotStartUnusedTechnology() {
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(
                mockTechnologyConfig(UWB, mock(RangingDevice.class)));

        setupAdapterMocks(configs);
        mSession.start(configs);
        verifyAdaptersStarted(configs);

        verify(mMockSessionListener, never()).onTechnologyStarted(eq(CS), any());
    }

    @Test
    public void start_whileAlreadyActive_startsNewTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        TechnologyConfig uwbConfig = mockTechnologyConfig(UWB, peer);
        TechnologyConfig csConfig = mockTechnologyConfig(CS, peer);

        setupAdapterMocks(Set.of(uwbConfig, csConfig));

        mSession.start(ImmutableSet.of(uwbConfig));
        verifyAdaptersStarted(Set.of(uwbConfig));
        verify(mMockSessionListener).onConfigurationComplete(argThat(s -> s.contains(uwbConfig)));
        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(Set.of(peer)));

        mSession.start(ImmutableSet.of(csConfig));
        verifyAdaptersStarted(Set.of(csConfig));
        verify(mMockSessionListener).onTechnologyStarted(eq(CS), eq(Set.of(peer)));
    }

    @Test
    public void stop_stopsUnicastTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(mockTechnologyConfig(UWB, peer));

        setupAdapterMocks(configs);
        mSession.start(configs);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                verifyAdaptersStarted(configs);

        mSession.stop();
        mockStopAdapters(adapterCallbacks);

        verify(mMockSessionListener).onTechnologyStopped(
                eq(UWB), eq(Set.of(peer)), eq(InternalReason.LOCAL_REQUEST));
        verify(mMockSessionListener).onSessionClosed(InternalReason.LOCAL_REQUEST);
    }

    @Test
    public void stop_stopsMultipleUnicastTechnologies() {
        RangingDevice peer = mock(RangingDevice.class);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(
                mockTechnologyConfig(UWB, peer),
                mockTechnologyConfig(RTT, peer));

        setupAdapterMocks(configs);
        mSession.start(configs);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                verifyAdaptersStarted(configs);

        mSession.stop();
        mockStopAdapters(adapterCallbacks);

        verify(mMockSessionListener).onTechnologyStopped(
                eq(UWB), eq(Set.of(peer)), eq(InternalReason.LOCAL_REQUEST));
        verify(mMockSessionListener).onTechnologyStopped(
                eq(UWB), eq(Set.of(peer)), eq(InternalReason.LOCAL_REQUEST));
        verify(mMockSessionListener).onSessionClosed(eq(InternalReason.LOCAL_REQUEST));
    }

    @Test
    public void stop_stopsMulticastTechnology() {
        Set<RangingDevice> peers = Set.of(mock(RangingDevice.class), mock(RangingDevice.class));
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(mockTechnologyConfig(UWB, peers));

        setupAdapterMocks(configs);
        mSession.start(configs);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                verifyAdaptersStarted(configs);

        mSession.stop();
        mockStopAdapters(adapterCallbacks);

        verify(mMockSessionListener).onTechnologyStopped(
                eq(UWB), eq(peers), eq(InternalReason.LOCAL_REQUEST));
        verify(mMockSessionListener).onSessionClosed(eq(InternalReason.LOCAL_REQUEST));
    }

    @Test
    public void shouldStop_whenTechnologyStops() {
        RangingDevice peer = mock(RangingDevice.class);
        TechnologyConfig config = mockTechnologyConfig(UWB, peer);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(config);

        setupAdapterMocks(configs);
        mSession.start(configs);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                verifyAdaptersStarted(configs);

        adapterCallbacks.get(config).onStopped(
                ImmutableSet.of(peer), InternalReason.NO_PEERS_FOUND);
        adapterCallbacks.get(config).onClosed(InternalReason.NO_PEERS_FOUND);

        verify(mMockSessionListener).onTechnologyStopped(
                eq(UWB), eq(Set.of(peer)), eq(InternalReason.NO_PEERS_FOUND));
        verify(mMockSessionListener).onSessionClosed(eq(InternalReason.NO_PEERS_FOUND));
    }

    @Test
    public void shouldStop_whenTechnologyFailsToStart() {
        RangingDevice peer = mock(RangingDevice.class);
        TechnologyConfig config = mockTechnologyConfig(UWB, peer);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(config);

        setupAdapterMocks(configs);
        mSession.start(configs);

        ArgumentCaptor<RangingAdapter.Callback> adapterCallbacks =
                ArgumentCaptor.forClass(RangingAdapter.Callback.class);
        verify(mMockAdapters.get(config)).start(eq(config), any(), adapterCallbacks.capture());

        adapterCallbacks.getValue().onClosed(InternalReason.UNSUPPORTED);

        verify(mMockSessionListener).onSessionClosed(eq(InternalReason.UNSUPPORTED));
    }

    @Test
    public void shouldReportData_fromTechnology() {
        RangingDevice peer = mock(RangingDevice.class);
        RangingData data = generateData(UWB);

        TechnologyConfig config = mockTechnologyConfig(UWB, peer);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(config);

        setupAdapterMocks(configs);
        mSession.start(configs);
        Map<TechnologyConfig, RangingAdapter.Callback> adapterCallbacks =
                verifyAdaptersStarted(configs);

        adapterCallbacks.get(config).onRangingData(peer, data);
        verify(mMockSessionListener).onResults(
                eq(peer),
                argThat((arg) -> arg.getRangingTechnology() == UWB.getValue())
        );
        verify(mMockSessionListener).onTechnologyStarted(eq(UWB), eq(Set.of(peer)));
    }

    @Test
    public void dynamicAddOrRemovePeer_callsAdapterAddOrRemovePeer() {
        RangingDevice peer = mock(RangingDevice.class);
        TechnologyConfig config = mockTechnologyConfig(UWB, peer);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(config);

        setupAdapterMocks(configs);
        mSession.start(configs);
        verify(mMockAdapters.get(config)).start(eq(config), any(), any());

        RangingDevice peerToAdd = mock(RangingDevice.class);
        RawResponderRangingConfig configToAdd = mock(RawResponderRangingConfig.class);
        when(configToAdd.getRawRangingDevice()).thenReturn(
                new RawRangingDevice.Builder()
                        .setRangingDevice(peerToAdd)
                        .setUwbRangingParams(mock(UwbRangingParams.class, RETURNS_DEEP_STUBS))
                        .build());

        mSession.addPeer(configToAdd);
        verify(mMockAdapters.get(config)).addPeer(eq(configToAdd));

        mSession.removePeer(peerToAdd);
        verify(mMockAdapters.get(config)).removePeer(peerToAdd);

        mSession.stop();
        verify(mMockAdapters.get(config)).stop();
    }

    @Test
    public void fgStateUpdate_callsAdapterFgStateUpdate() {
        RangingDevice peer = mock(RangingDevice.class);
        TechnologyConfig config = mockTechnologyConfig(UWB, peer);
        ImmutableSet<TechnologyConfig> configs = ImmutableSet.of(config);

        setupAdapterMocks(configs);
        mSession.start(configs);
        verify(mMockAdapters.get(config)).start(eq(config), any(), any());

        mSession.appForegroundStateUpdated(true);
        verify(mMockAdapters.get(config)).appForegroundStateUpdated(eq(true));

        mSession.appForegroundStateUpdated(false);
        verify(mMockAdapters.get(config)).appForegroundStateUpdated(eq(false));

        mSession.appInBackgroundTimeout();
        verify(mMockAdapters.get(config)).appInBackgroundTimeout();

        mSession.stop();
        verify(mMockAdapters.get(config)).stop();
    }
}
