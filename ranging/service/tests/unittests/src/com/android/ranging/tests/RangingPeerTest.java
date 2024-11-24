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

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;

import static com.android.server.ranging.RangingTechnology.CS;
import static com.android.server.ranging.RangingTechnology.RTT;
import static com.android.server.ranging.RangingTechnology.UWB;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.ranging.DataNotificationConfig;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.SensorFusionParams;
import android.ranging.SessionHandle;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingAdapter.Callback.StoppedReason;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingPeer;
import com.android.server.ranging.RangingPeerConfig;
import com.android.server.ranging.RangingPeerConfig.TechnologyConfig;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;
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

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
@SmallTest
public class RangingPeerTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingInjector mMockInjector;
    private @Mock(answer = Answers.RETURNS_DEEP_STUBS) RangingPeerConfig mMockConfig;
    private @Mock RangingServiceManager.SessionListener mMockSessionListener;
    private final EnumMap<RangingTechnology, RangingAdapter> mMockAdapters =
            new EnumMap<>(RangingTechnology.class);
    private @Mock SessionHandle mMockSessionHandle;
    private @Mock RangingDevice mMockDevice;
    private RangingPeer mPeer;

    /**
     * Starts a ranging session with the provided configs.
     *
     * @param technologies to use for the session.
     * @return {@link RangingAdapter.Callback} for each of the provided technologies' adapters.
     * These callbacks are captured from underlying {@link RangingAdapter} mock for each technology.
     */
    private EnumMap<RangingTechnology, RangingAdapter.Callback> startSession(
            Set<RangingTechnology> technologies
    ) {
        EnumMap<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                new EnumMap<>(RangingTechnology.class);

        useTechnologies(technologies);
        mPeer.start();

        for (RangingTechnology technology : technologies) {
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
                .setTimestampMillis(1)
                .build();
    }

    /** Generate a test config with the provided technologies. */
    private void useTechnologies(Set<RangingTechnology> technologies) {
        ImmutableMap.Builder<RangingTechnology, TechnologyConfig> builder =
                new ImmutableMap.Builder<>();

        if (technologies.contains(RangingTechnology.UWB)) {
            UwbConfig config = new UwbConfig.Builder(
                    new UwbRangingParams.Builder(
                            10, CONFIG_UNICAST_DS_TWR, UwbAddress.fromBytes(new byte[]{1, 2}),
                            UwbAddress.fromBytes(new byte[]{3, 4}))
                            .setComplexChannel(new UwbComplexChannel.Builder()
                                    .setChannel(9)
                                    .setPreambleIndex(11)
                                    .build())
                            .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                            .build())
                    .setPeerDevice(mMockDevice)
                    .setCountryCode("US")
                    .setDeviceRole(DEVICE_ROLE_INITIATOR)
                    .setAoaNeeded(true)
                    .setDataNotificationConfig(new DataNotificationConfig.Builder().build())
                    .build();
            builder.put(RangingTechnology.UWB, config);
        }
        if (technologies.contains(RangingTechnology.CS)) {
            throw new UnsupportedOperationException("CS not yet implemented");
        }
        if (technologies.contains(RTT)) {
            RttConfig config = new RttConfig(
                    DEVICE_ROLE_INITIATOR,
                    new RttRangingParams.Builder("servicename")
                            .build(),
                    new DataNotificationConfig.Builder().build(),
                    mMockDevice
            );
            builder.put(RTT, config);
        }
        when(mMockConfig.getTechnologyConfigs()).thenReturn(builder.build());
    }

    @Before
    public void setup() {
        when(mMockConfig.getSensorFusionConfig()).thenReturn(
                new SensorFusionParams.Builder().setSensorFusionEnabled(true).build());
        when(mMockConfig.getDevice()).thenReturn(mMockDevice);

        mPeer = new RangingPeer(
                mMockInjector, mMockConfig, mMockSessionListener,
                MoreExecutors.newDirectExecutorService());

        for (RangingTechnology technology : RangingTechnology.TECHNOLOGIES) {
            RangingAdapter adapter = mock(RangingAdapter.class);
            mMockAdapters.put(technology, adapter);
            when(mMockInjector.createAdapter(eq(technology), anyInt(), any())).thenReturn(adapter);
        }
    }

    @Test
    public void start_startsPeerAndTechnology() {
        startSession(Set.of(UWB));
        verify(mMockSessionListener).onPeerStarted();
        verify(mMockSessionListener).onTechnologyStarted(eq(mMockDevice), eq(UWB.getValue()));
    }

    @Test
    public void start_startsMultipleTechnologies() {
        startSession(Set.of(UWB, RTT));

        verify(mMockSessionListener).onPeerStarted();
        verify(mMockSessionListener).onTechnologyStarted(eq(mMockDevice), eq(UWB.getValue()));
        verify(mMockSessionListener).onTechnologyStarted(eq(mMockDevice), eq(RTT.getValue()));
    }

    @Test
    public void start_doesNotStartUnusedTechnologies() {
        startSession(Set.of(UWB));

        verify(mMockSessionListener).onPeerStarted();
        verify(mMockAdapters.get(CS), never()).start(any(), any());
        verify(mMockSessionListener, never()).onTechnologyStarted(any(), eq(CS.getValue()));
    }

    @Test
    public void stop_stopsPeerAndTechnology() {
        Map<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(Set.of(UWB));

        mPeer.stop();

        verify(mMockAdapters.get(UWB)).stop();

        adapterCallbacks.get(UWB).onStopped(StoppedReason.REQUESTED);

        verify(mMockSessionListener).onTechnologyStopped(eq(mMockDevice), eq(UWB.getValue()));
        verify(mMockSessionListener).onPeerStopped(eq(mMockDevice), eq(StoppedReason.REQUESTED));
    }

    @Test
    public void stop_stopsMultipleTechnologies() {
        Map<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(Set.of(UWB, RTT));

        mPeer.stop();

        verify(mMockAdapters.get(UWB)).stop();
        verify(mMockAdapters.get(RTT)).stop();

        adapterCallbacks.get(UWB).onStopped(StoppedReason.REQUESTED);
        adapterCallbacks.get(RTT).onStopped(StoppedReason.REQUESTED);

        verify(mMockSessionListener).onTechnologyStopped(eq(mMockDevice), eq(UWB.getValue()));
        verify(mMockSessionListener).onTechnologyStopped(eq(mMockDevice), eq(RTT.getValue()));
        verify(mMockSessionListener).onPeerStopped(eq(mMockDevice), eq(StoppedReason.REQUESTED));
    }

    @Test
    public void shouldStop_whenAdapterStops() {
        Map<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(Set.of(UWB));

        adapterCallbacks.get(UWB).onStopped(StoppedReason.LOST_CONNECTION);

        verify(mMockSessionListener).onTechnologyStopped(eq(mMockDevice), eq(UWB.getValue()));
        verify(mMockSessionListener).onPeerStopped(
                eq(mMockDevice), eq(StoppedReason.LOST_CONNECTION));
    }

    @Test
    public void shouldStop_whenAdapterFailsToStart() {
        useTechnologies(Set.of(UWB));
        mPeer.start();

        ArgumentCaptor<RangingAdapter.Callback> adapterCallback =
                ArgumentCaptor.forClass(RangingAdapter.Callback.class);
        verify(mMockAdapters.get(UWB)).start(any(), adapterCallback.capture());
        adapterCallback.getValue().onStopped(StoppedReason.FAILED_TO_START);

        verify(mMockSessionListener).onPeerStopped(
                eq(mMockDevice), eq(StoppedReason.FAILED_TO_START));
    }

    @Test
    public void shouldReportData_fromAdapter() {
        Map<RangingTechnology, RangingAdapter.Callback> adapterCallbacks =
                startSession(Set.of(UWB));

        adapterCallbacks.get(UWB).onRangingData(mMockDevice, generateData(UWB));

        verify(mMockSessionListener).onResults(eq(mMockDevice), any(RangingData.class));
    }
}
