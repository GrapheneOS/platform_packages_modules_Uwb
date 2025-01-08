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

package com.android.server.ranging.tests.cs;

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
import android.ranging.ble.cs.BleCsRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.session.RangingSessionConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
@SmallTest
public class CsAdapterTest {

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
    private CsConfig mMockCsConfig;

    @Mock
    private SessionConfig mMockSessionConfig;
    @Mock
    private BleCsRangingParams mMockRangingParams;

    private final DataNotificationConfig mDataNotificationConfig =
            new DataNotificationConfig.Builder().build();

    private CsAdapter mCsAdapter;

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
        when(mMockPackageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING)).thenReturn(true);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockBluetoothAdapter.getRemoteDevice(anyString())).thenReturn(mMockBluetoothDevice);
        when(mMockCsConfig.getRangingParams()).thenReturn(mMockRangingParams);
        when(mMockCsConfig.getPeerDevice()).thenReturn(mMockRangingDevice);
        when(mMockCsConfig.getSessionConfig()).thenReturn(mMockSessionConfig);
        when(mMockRangingParams.getSecurityLevel()).thenReturn(1);
        when(mMockSessionConfig.getDataNotificationConfig()).thenReturn(mDataNotificationConfig);
        when(mMockRangingParams.getPeerBluetoothAddress()).thenReturn("mockAddress");
        when(mMockAttributionSource.getUid()).thenReturn(100);
        when(mMockAttributionSource.getPackageName()).thenReturn("TestPkgName");
        when(mMockRangingInjector.isForegroundAppOrService(anyInt(), anyString())).thenReturn(true);
        mCsAdapter = new CsAdapter(mMockContext, mMockRangingInjector);
    }

    @Test
    public void testStartStop_ValidConfig() {
        mCsAdapter.start(mMockCsConfig, null, mMockCallback);

        verify(mMockDistanceMeasurementManager, times(1)).startMeasurementSession(any(), any(),
                any());
        verify(mMockCallback, times(1)).onStarted(mMockRangingDevice);

        mCsAdapter.setSession(mMockDistanceMeasurementSession);
        mCsAdapter.stop();

        verify(mMockDistanceMeasurementSession, times(1)).stopSession();
    }

    @Test
    public void testStart_InvalidConfig() {

        mCsAdapter.start(mMockTechnologyConfig, null, mMockCallback);

        verify(mMockCallback, never()).onStarted(any());
        verify(mMockDistanceMeasurementManager, never()).startMeasurementSession(any(), any(),
                any());
    }

    @Test
    public void testStop_WhenNotStarted() {
        mCsAdapter.stop();
        verify(mMockDistanceMeasurementSession, never()).stopSession();
    }

    @Test
    public void testAppMovingBackgroundForeground() {
        mCsAdapter.start(mMockCsConfig, mMockAttributionSource, mMockCallback);

        assertEquals(mCsAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_ENABLE);

        verify(mMockCallback, times(1)).onStarted(mMockRangingDevice);

        mCsAdapter.appMovedToBackground();

        assertEquals(mCsAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_DISABLE);

        mCsAdapter.appMovedToForeground();

        assertEquals(mCsAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_ENABLE);

        mCsAdapter.stop();
    }

    @Test
    public void testAppInBackgroundTimeout() {
        mCsAdapter.start(mMockCsConfig, mMockAttributionSource, mMockCallback);
        mCsAdapter.setSession(mMockDistanceMeasurementSession);

        verify(mMockCallback, times(1)).onStarted(mMockRangingDevice);

        mCsAdapter.appInBackgroundTimeout();
        verify(mMockDistanceMeasurementSession, times(1)).stopSession();
    }

    @Test
    public void testDistanceMeasurementCallback_OnResult() {
        DistanceMeasurementResult mockResult = mock(DistanceMeasurementResult.class);
        when(mockResult.getResultMeters()).thenReturn(1.0);
        //when(mockResult.getMeasurementTimestampNanos()).thenReturn(1000L);
        mCsAdapter.start(mMockCsConfig, null, mMockCallback);
        mCsAdapter.mDistanceMeasurementCallback.onResult(mMockBluetoothDevice, mockResult);

        verify(mMockCallback, times(1)).onRangingData(any(), any());
    }
}
