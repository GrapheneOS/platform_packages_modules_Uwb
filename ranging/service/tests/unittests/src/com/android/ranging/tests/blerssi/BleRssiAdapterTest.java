/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging.tests.blerssi;


import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_DISABLE;
import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.rssi.BleRssiRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.blerssi.BleRssiAdapter;
import com.android.server.ranging.blerssi.BleRssiConfig;
import com.android.server.ranging.session.RangingSessionConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
@SmallTest
public class BleRssiAdapterTest {

    @Mock
    private Context mMockContext;
    @Mock
    private RangingInjector mMockRangingInjector;
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    private DistanceMeasurementManager mMockDistanceMeasurementManager;
    @Mock
    private DistanceMeasurementSession mMockDistanceMeasurementSession;
    @Mock
    private RangingDevice mMockRangingDevice;
    @Mock
    private RangingSessionConfig.TechnologyConfig mMockTechnologyConfig;
    @Mock
    private com.android.server.ranging.RangingAdapter.Callback mMockCallback;
    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private BluetoothManager mMockBluetoothManager;

    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private AttributionSource mMockAttributionSource;
    @Mock
    private BluetoothDevice mMockBluetoothDevice;

    @Mock
    private BleRssiConfig mMockBleRssiConfig;

    @Mock
    private SessionConfig mMockSessionConfig;
    @Mock
    private BleRssiRangingParams mMockRangingParams;

    private final DataNotificationConfig mDataNotificationConfig =
            new DataNotificationConfig.Builder().build();

    private BleRssiAdapter mBleRssiAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(BluetoothManager.class)).thenReturn(
                mMockBluetoothManager);
        when(mMockContext.getSystemService(BluetoothAdapter.class)).thenReturn(
                mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getDistanceMeasurementManager()).thenReturn(
                mMockDistanceMeasurementManager);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        when(mMockContext.getSystemService(PackageManager.class)).thenReturn(mMockPackageManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)).thenReturn(
                true);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getRemoteDevice(anyString())).thenReturn(mMockBluetoothDevice);
        when(mMockBleRssiConfig.getRangingParams()).thenReturn(mMockRangingParams);
        when(mMockBleRssiConfig.getPeerDevice()).thenReturn(mMockRangingDevice);
        when(mMockBleRssiConfig.getSessionConfig()).thenReturn(mMockSessionConfig);
        when(mMockSessionConfig.getDataNotificationConfig()).thenReturn(mDataNotificationConfig);
        when(mMockRangingParams.getPeerBluetoothAddress()).thenReturn("mockAddress");
        when(mMockAttributionSource.getUid()).thenReturn(100);
        when(mMockAttributionSource.getPackageName()).thenReturn("TestPkgName");
        when(mMockRangingInjector.isForegroundAppOrService(anyInt(), anyString())).thenReturn(true);
        mBleRssiAdapter = new BleRssiAdapter(mMockContext, mMockRangingInjector);
    }

    @Test
    public void testStartStop_ValidConfig() {
        mBleRssiAdapter.start(mMockBleRssiConfig, null, mMockCallback);

        verify(mMockDistanceMeasurementManager, times(1)).startMeasurementSession(any(), any(),
                any());
        verify(mMockCallback, times(1)).onStarted(mMockRangingDevice);

        mBleRssiAdapter.setSession(mMockDistanceMeasurementSession);
        mBleRssiAdapter.stop();

        verify(mMockDistanceMeasurementSession, times(1)).stopSession();
    }

    @Test
    public void testStart_InvalidConfig() {

        // When
        mBleRssiAdapter.start(mMockTechnologyConfig, null, mMockCallback);

        // Then
        verify(mMockCallback, never()).onStarted(any());
        verify(mMockDistanceMeasurementManager, never()).startMeasurementSession(any(), any(),
                any());
    }

    @Test
    public void testStop_WhenNotStarted() {
        // Given
        // Not starting the adapter

        // When
        mBleRssiAdapter.stop();

        // Then
        verify(mMockDistanceMeasurementSession, never()).stopSession();
    }

    @Test
    public void testAppMovingBackgroundForeground() {
        mBleRssiAdapter.start(mMockBleRssiConfig, mMockAttributionSource, mMockCallback);

        assertEquals(mBleRssiAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_ENABLE);

        verify(mMockCallback, times(1)).onStarted(mMockRangingDevice);

        mBleRssiAdapter.appMovedToBackground();

        assertEquals(mBleRssiAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_DISABLE);

        mBleRssiAdapter.appMovedToForeground();

        assertEquals(mBleRssiAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_ENABLE);

        mBleRssiAdapter.stop();
    }

    @Test
    public void testAppInBackgroundTimeout() {
        // Given
        mBleRssiAdapter.start(mMockBleRssiConfig, mMockAttributionSource, mMockCallback);
        mBleRssiAdapter.setSession(mMockDistanceMeasurementSession);

        verify(mMockCallback, times(1)).onStarted(mMockRangingDevice);
        // When
        mBleRssiAdapter.appInBackgroundTimeout();

        // Then
        verify(mMockDistanceMeasurementSession, times(1)).stopSession();
    }

    @Test
    public void testDistanceMeasurementCallback_OnResult() {
        // Given
        DistanceMeasurementResult mockResult = mock(DistanceMeasurementResult.class);
        when(mockResult.getResultMeters()).thenReturn(1.0);
        //when(mockResult.getMeasurementTimestampNanos()).thenReturn(1000L);
        mBleRssiAdapter.start(mMockBleRssiConfig, null, mMockCallback);

        // When
        mBleRssiAdapter.mDistanceMeasurementCallback.onResult(mMockBluetoothDevice, mockResult);

        // Then
        verify(mMockCallback, times(1)).onRangingData(any(), any());
    }

}
