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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.content.AttributionSource;
import android.content.Context;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.TransportServerProvider.TransportServerCallback;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo.TransportType;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/** Unit test for {@link TransportServerService} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class TransportServerServiceTest {

    private static final DiscoveryInfo DISCOVERY_INFO =
            new DiscoveryInfo(TransportType.BLE, Optional.empty(), Optional.empty());

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothGattServer mMockBluetoothGattServer;
    @Mock TransportServerCallback mMockTransportServerCallback;

    private TransportServerService mTransportServerService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothManager.openGattServer(
                        eq(mMockContext), any(BluetoothGattServerCallback.class)))
                .thenReturn(mMockBluetoothGattServer);

        mTransportServerService =
                new TransportServerService(
                        mMockAttributionSource,
                        mMockContext,
                        DISCOVERY_INFO,
                        mMockTransportServerCallback);
    }

    @Test
    public void testStart_failed() {
        when(mMockBluetoothGattServer.addService(any())).thenReturn(false);
        assertThat(mTransportServerService.start()).isFalse();
        verify(mMockBluetoothGattServer, times(1)).addService(any());
    }

    @Test
    public void testStart_successAndRejectRestart() {
        when(mMockBluetoothGattServer.addService(any())).thenReturn(true);
        assertThat(mTransportServerService.start()).isTrue();
        verify(mMockBluetoothGattServer, times(1)).addService(any());
        assertThat(mTransportServerService.start()).isFalse();
        verify(mMockBluetoothGattServer, times(1)).addService(any());
    }

    @Test
    public void testStop_failed() {
        when(mMockBluetoothGattServer.addService(any())).thenReturn(true);
        when(mMockBluetoothGattServer.removeService(any())).thenReturn(false);
        assertThat(mTransportServerService.start()).isTrue();
        verify(mMockBluetoothGattServer, times(1)).addService(any());
        assertThat(mTransportServerService.stop()).isFalse();
        verify(mMockBluetoothGattServer, times(1)).removeService(any());
    }

    @Test
    public void testStop_successAndRejectRestop() {
        when(mMockBluetoothGattServer.addService(any())).thenReturn(true);
        when(mMockBluetoothGattServer.removeService(any())).thenReturn(true);
        assertThat(mTransportServerService.start()).isTrue();
        verify(mMockBluetoothGattServer, times(1)).addService(any());
        assertThat(mTransportServerService.stop()).isTrue();
        verify(mMockBluetoothGattServer, times(1)).removeService(any());
        assertThat(mTransportServerService.stop()).isFalse();
        verify(mMockBluetoothGattServer, times(1)).removeService(any());
    }
}
