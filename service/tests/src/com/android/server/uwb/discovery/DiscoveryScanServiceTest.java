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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.AttributionSource;
import android.content.Context;
import android.uwb.UwbTestUtils;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.discovery.DiscoveryScanProvider.DiscoveryScanCallback;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo.TransportType;
import com.android.server.uwb.discovery.info.ScanInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Executor;

/** Unit test for {@link DiscoveryScanService} */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DiscoveryScanServiceTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final DiscoveryInfo DISCOVERY_INFO =
            new DiscoveryInfo(
                    TransportType.BLE,
                    Optional.of(
                            new ScanInfo(
                                    new ArrayList<ScanFilter>(),
                                    new ScanSettings.Builder().build())),
                    Optional.empty(),
                    Optional.empty());

    @Mock AttributionSource mMockAttributionSource;
    @Mock Context mMockContext;
    @Mock BluetoothManager mMockBluetoothManager;
    @Mock BluetoothAdapter mMockBluetoothAdapter;
    @Mock BluetoothLeScanner mMockBluetoothLeScanner;
    @Mock DiscoveryScanCallback mMockDiscoveryScanCallback;

    private DiscoveryScanService mDiscoveryScanService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockContext.createContext(any())).thenReturn(mMockContext);

        mDiscoveryScanService =
                new DiscoveryScanService(
                        mMockAttributionSource,
                        mMockContext,
                        EXECUTOR,
                        DISCOVERY_INFO,
                        mMockDiscoveryScanCallback);
    }

    @Test
    public void testStartDiscovery_successAndRejectRestart() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);

        assertThat(mDiscoveryScanService.startDiscovery()).isTrue();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        assertThat(mDiscoveryScanService.startDiscovery()).isFalse();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
    }

    @Test
    public void testStopDiscovery_successAndRejectRestop() {
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);

        assertThat(mDiscoveryScanService.startDiscovery()).isTrue();
        verify(mMockBluetoothLeScanner, times(1)).startScan(any(), any(), any(ScanCallback.class));
        assertThat(mDiscoveryScanService.stopDiscovery()).isTrue();
        verify(mMockBluetoothLeScanner, times(1)).stopScan(any(ScanCallback.class));
        assertThat(mDiscoveryScanService.stopDiscovery()).isFalse();
        verify(mMockBluetoothLeScanner, times(1)).stopScan(any(ScanCallback.class));
    }
}
