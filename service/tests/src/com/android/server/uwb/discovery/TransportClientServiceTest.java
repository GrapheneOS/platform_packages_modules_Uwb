/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.discovery;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.ScanResult;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.TransportClientProvider.TransportClientCallback;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo.TransportType;
import com.android.server.uwb.discovery.info.TransportClientInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.concurrent.Executor;

/** Unit test for {@link TransportClientService} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TransportClientServiceTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock TransportClientCallback mMockTransportClientCallback;
    @Mock ScanResult mMockScanResult;
    @Mock BluetoothDevice mMockBluetoothDevice;
    @Mock BluetoothGatt mMockBluetoothGatt;

    private DiscoveryInfo mDiscoveryInfo;
    private TransportClientService mTransportClientService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);
        when(mMockScanResult.getDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt()))
                .thenReturn(mMockBluetoothGatt);

        mDiscoveryInfo =
                new DiscoveryInfo(
                        TransportType.BLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new TransportClientInfo(mMockScanResult)));
        mTransportClientService =
                new TransportClientService(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        mDiscoveryInfo,
                        mMockTransportClientCallback);
    }

    @Test
    public void testConstruct_illegalArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TransportClientService(
                                mMockAttributionSource,
                                mMockContext,
                                EXECUTOR,
                                new DiscoveryInfo(
                                        TransportType.BLE,
                                        Optional.empty(),
                                        Optional.empty(),
                                        Optional.empty()),
                                mMockTransportClientCallback));
    }

    @Test
    public void testStart_succeed() {
        assertThat(mTransportClientService.start()).isTrue();
        verify(mMockBluetoothDevice, times(1))
                .connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt());
        verifyZeroInteractions(mMockBluetoothGatt);
    }

    @Test
    public void testStart_successAndRejectRestart() {
        assertThat(mTransportClientService.start()).isTrue();
        assertThat(mTransportClientService.start()).isFalse();
        verify(mMockBluetoothDevice, times(1))
                .connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt());
        verifyZeroInteractions(mMockBluetoothGatt);
    }

    @Test
    public void testStop_successAndRejectRestop() {
        assertThat(mTransportClientService.start()).isTrue();
        verify(mMockBluetoothDevice, times(1))
                .connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt());
        assertThat(mTransportClientService.stop()).isTrue();
        verify(mMockBluetoothGatt, times(1)).disconnect();
        assertThat(mTransportClientService.stop()).isFalse();
        verify(mMockBluetoothGatt, times(1)).disconnect();
    }

    @Test
    public void testStartStopStart() {
        when(mMockBluetoothGatt.connect()).thenReturn(false, true);
        assertThat(mTransportClientService.start()).isTrue();
        verify(mMockBluetoothDevice, times(1))
                .connectGatt(eq(mMockContext), anyBoolean(), any(), anyInt());
        assertThat(mTransportClientService.stop()).isTrue();
        verify(mMockBluetoothGatt, times(1)).disconnect();
        assertThat(mTransportClientService.start()).isFalse();
        verify(mMockBluetoothGatt, times(1)).connect();
        assertThat(mTransportClientService.start()).isTrue();
        verify(mMockBluetoothGatt, times(2)).connect();
    }
}
