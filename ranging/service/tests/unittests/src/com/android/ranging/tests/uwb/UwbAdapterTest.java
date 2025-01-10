/*
 * Copyright (C) 2025 The Android Open Source Project
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

package uwb;

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.RangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import androidx.test.filters.SmallTest;

import com.android.ranging.uwb.backend.internal.RangingController;
import com.android.ranging.uwb.backend.internal.RangingPosition;
import com.android.ranging.uwb.backend.internal.RangingSessionCallback;
import com.android.ranging.uwb.backend.internal.UwbDevice;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
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

@RunWith(JUnit4.class)
@SmallTest
@SuppressWarnings("ConstantConditions")
public class UwbAdapterTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mMockContext;
    @Mock
    private RangingController mMockUwbClient;

    @Mock
    private AttributionSource mMockAttributionSource;

    @Mock
    private RangingInjector mMockRangingInjector;

    @Mock
    private RangingAdapter.Callback mMockCallback;

    @Mock
    private UwbDevice mMockLocalDevice;

    /** Class under test */
    private UwbAdapter mUwbAdapter;

    private UwbConfig generateConfig(Map<RangingDevice, UwbAddress> peers) {
        return new UwbConfig.Builder(
                new UwbRangingParams.Builder(
                        10,
                        peers.size() == 1 ? CONFIG_UNICAST_DS_TWR : CONFIG_MULTICAST_DS_TWR,
                        UwbAddress.fromBytes(new byte[]{1, 2}),
                        mock(UwbAddress.class))
                        .setComplexChannel(
                                new UwbComplexChannel.Builder()
                                        .setChannel(9)
                                        .setPreambleIndex(11)
                                        .build())
                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                        .build())
                .setCountryCode("US")
                .setPeerAddresses(ImmutableBiMap.copyOf(peers))
                .build();
    }

    @Before
    public void setup() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(true);
        mUwbAdapter = new UwbAdapter(mMockContext, mMockRangingInjector, mMockAttributionSource,
                MoreExecutors.newDirectExecutorService(),
                MoreExecutors.newDirectExecutorService(), mMockUwbClient);
    }

    @Test
    public void getTechnology_returnsUwb() {
        Assert.assertEquals(RangingTechnology.UWB, mUwbAdapter.getTechnology());
    }

    @Test
    public void start_failsWhenParamsInvalid() {
        mUwbAdapter.start(mock(CsConfig.class), null, mMockCallback);
        verify(mMockCallback, never()).onStarted(any());
        verify(mMockCallback).onClosed(eq(RangingAdapter.Callback.ClosedReason.ERROR));
        verify(mMockCallback, never()).onStopped(any());
    }

    @Test
    public void start_startsPeer() {
        RangingDevice peer = mock(RangingDevice.class);
        mUwbAdapter.start(
                generateConfig(Map.of(peer, UwbAddress.fromBytes(new byte[]{1, 2}))),
                null,
                mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callback =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callback.capture(), any());

        callback.getValue().onRangingInitialized(mMockLocalDevice);
        verify(mMockCallback).onStarted(eq(peer));
    }

    @Test
    public void start_startsMultiplePeers() {
        List<RangingDevice> peers = List.of(mock(RangingDevice.class), mock(RangingDevice.class));
        mUwbAdapter.start(
                generateConfig(Map.of(
                        peers.get(0), UwbAddress.fromBytes(new byte[]{1, 2}),
                        peers.get(1), UwbAddress.fromBytes(new byte[]{3, 4}))),
                null,
                mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callback =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callback.capture(), any());

        callback.getValue().onRangingInitialized(mMockLocalDevice);
        verify(mMockCallback).onStarted(eq(peers.get(0)));
        verify(mMockCallback).onStarted(eq(peers.get(1)));
    }

    @Test
    public void stop_stopsMultiplePeersAndClosesAdapter() {
        List<RangingDevice> peers = List.of(mock(RangingDevice.class), mock(RangingDevice.class));
        mUwbAdapter.start(
                generateConfig(Map.of(
                        peers.get(0), UwbAddress.fromBytes(new byte[]{1, 2}),
                        peers.get(1), UwbAddress.fromBytes(new byte[]{3, 4}))),
                null,
                mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callback =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callback.capture(), any());

        callback.getValue().onRangingInitialized(mMockLocalDevice);

        mUwbAdapter.stop();
        verify(mMockUwbClient).stopRanging();

        callback.getValue().onRangingSuspended(
                mMockLocalDevice,
                RangingSessionCallback.REASON_STOP_RANGING_CALLED);
        verify(mMockCallback).onStopped(eq(peers.get(0)));
        verify(mMockCallback).onStopped(eq(peers.get(1)));
        verify(mMockCallback).onClosed(RangingAdapter.Callback.ClosedReason.REQUESTED);
    }

    @Test
    public void shouldClose_whenLastPeerDisconnects() {
        RangingDevice peerDevice = mock(RangingDevice.class);
        byte[] peerAddress = new byte[]{1, 2};

        mUwbAdapter.start(
                generateConfig(Map.of(peerDevice, UwbAddress.fromBytes(peerAddress))),
                null,
                mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callback =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callback.capture(), any());

        callback.getValue().onRangingInitialized(mMockLocalDevice);
        callback.getValue().onPeerDisconnected(
                UwbDevice.createForAddress(peerAddress),
                RangingSessionCallback.PeerDisconnectedReason.SYSTEM_POLICY);
        // UWB stack will suspend the session after an error streak timeout occurs.
        callback.getValue().onRangingSuspended(
                UwbDevice.createForAddress(peerAddress),
                RangingSessionCallback.REASON_MAX_RANGING_ROUND_RETRY_REACHED);

        verify(mMockCallback).onStopped(eq(peerDevice));
        verify(mMockCallback).onClosed(eq(RangingAdapter.Callback.ClosedReason.LOST_CONNECTION));
    }

    @Test
    public void shouldReportData_onRangingResult() {
        RangingDevice peerDevice = mock(RangingDevice.class);
        byte[] peerAddress = new byte[]{1, 2};

        mUwbAdapter.start(
                generateConfig(Map.of(peerDevice, UwbAddress.fromBytes(peerAddress))),
                null,
                mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callback =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callback.capture(), any());

        callback.getValue().onRangingInitialized(mMockLocalDevice);

        RangingPosition mockPosition = mock(RangingPosition.class, Answers.RETURNS_DEEP_STUBS);
        when(mockPosition.getDistance().getValue()).thenReturn(12F);
        when(mockPosition.getElapsedRealtimeNanos()).thenReturn(1234L);
        callback.getValue().onRangingResult(UwbDevice.createForAddress(peerAddress), mockPosition);

        verify(mMockCallback).onRangingData(
                eq(peerDevice),
                argThat((arg) -> arg.getRangingTechnology() == RangingTechnology.UWB.getValue()));
    }
}
