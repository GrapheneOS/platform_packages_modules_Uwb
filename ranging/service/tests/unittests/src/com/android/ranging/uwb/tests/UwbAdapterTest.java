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

package com.android.server.ranging.uwb.tests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import androidx.test.filters.SmallTest;

import com.android.ranging.uwb.backend.internal.RangingController;
import com.android.ranging.uwb.backend.internal.RangingPosition;
import com.android.ranging.uwb.backend.internal.RangingSessionCallback;
import com.android.ranging.uwb.backend.internal.UwbDevice;
import com.android.ranging.uwb.backend.internal.UwbServiceImpl;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingData;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;
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

import java.util.concurrent.ExecutionException;

@RunWith(JUnit4.class)
@SmallTest
public class UwbAdapterTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mMockContext;
    @Mock
    private UwbServiceImpl mMockUwbService;
    @Mock
    private RangingController mMockUwbClient;

    @Mock
    private RangingAdapter.Callback mMockCallback;

    /** Class under test */
    private UwbAdapter mUwbAdapter;

    private UwbConfig.Builder generateConfig() {
        return new UwbConfig.Builder(
                new UwbRangingParams.Builder()
                        .setConfigId(UwbRangingParams.ConfigId.UNICAST_DS_TWR)
                        .setDeviceRole(UwbRangingParams.DeviceRole.INITIATOR)
                        .setDeviceAddress(UwbAddress.fromBytes(new byte[]{1, 2}))
                        .setComplexChannel(new UwbComplexChannel.Builder().setChannel(
                                9).setPreambleIndex(11).build())
                        .setPeerAddresses(ImmutableMap.of())
                        .setRangingUpdateRate(UwbRangingParams.RangingUpdateRate.NORMAL)
                        .build()
        )
                .setCountryCode("US");
    }

    @Before
    public void setup() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(true);
        when(mMockUwbService.getController(any())).thenReturn(mMockUwbClient);
        mUwbAdapter = new UwbAdapter(mMockContext, MoreExecutors.newDirectExecutorService(),
                mMockUwbService, UwbRangingParams.DeviceRole.INITIATOR);
    }

    @Test
    public void getType_returnsUwb() {
        Assert.assertEquals(RangingTechnology.UWB, mUwbAdapter.getType());
    }

    @Test
    public void isEnabled_checksServiceIsAvailable()
            throws InterruptedException, ExecutionException {
        when(mMockUwbService.isAvailable()).thenReturn(true);
        Assert.assertTrue(mUwbAdapter.isEnabled().get());
    }

    @Test
    public void start_failsWhenParamsInvalid() {
        mUwbAdapter.start(mock(CsConfig.class), mMockCallback);
        verify(mMockCallback).onStopped(eq(RangingAdapter.Callback.StoppedReason.FAILED_TO_START));
        verify(mMockCallback, never()).onStarted();
    }

    @Test
    public void start_startsUwbClientWithCallbacks() {
        mUwbAdapter.start(generateConfig().build(), mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callbackCaptor =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callbackCaptor.capture(), any());

        UwbDevice mockUwbdevice = mock(UwbDevice.class, Answers.RETURNS_DEEP_STUBS);
        callbackCaptor.getValue().onRangingInitialized(mockUwbdevice);
        verify(mMockCallback).onStarted();

        callbackCaptor.getValue().onRangingSuspended(mockUwbdevice, anyInt());
        verify(mMockCallback).onStopped(anyInt());
    }

    @Test
    public void stop_stopsUwbClient() {
        mUwbAdapter.start(generateConfig().build(), mMockCallback);
        mUwbAdapter.stop();
        verify(mMockUwbClient).stopRanging();
    }

    @Test
    public void shouldReportData_onRangingResult() {
        mUwbAdapter.start(generateConfig().build(), mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callbackCaptor =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callbackCaptor.capture(), any());

        UwbDevice mockDevice = mock(UwbDevice.class, Answers.RETURNS_DEEP_STUBS);
        when(mockDevice.getAddress().toBytes()).thenReturn(new byte[]{0x1, 0x2});

        RangingPosition mockPosition = mock(RangingPosition.class, Answers.RETURNS_DEEP_STUBS);
        when(mockPosition.getDistance().getValue()).thenReturn(12F);
        when(mockPosition.getElapsedRealtimeNanos()).thenReturn(1234L);

        callbackCaptor.getValue().onRangingInitialized(mockDevice);
        verify(mMockCallback).onStarted();

        ArgumentCaptor<RangingData> dataCaptor = ArgumentCaptor.forClass(RangingData.class);
        callbackCaptor.getValue().onRangingResult(mockDevice, mockPosition);
        verify(mMockCallback).onRangingData(dataCaptor.capture());

        RangingData data = dataCaptor.getValue();
        Assert.assertEquals(RangingTechnology.UWB, data.getTechnology().get());
        Assert.assertEquals(mockPosition.getDistance().getValue(), data.getRangeMeters(), 0.1);
        Assert.assertArrayEquals(mockDevice.getAddress().toBytes(), data.getPeerAddress());
        Assert.assertEquals(mockPosition.getElapsedRealtimeNanos(), data.getTimestamp().getNano());
    }
}
