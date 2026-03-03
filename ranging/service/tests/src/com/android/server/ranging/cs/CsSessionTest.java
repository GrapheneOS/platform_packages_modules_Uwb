/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ranging.cs;

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.le.ChannelSoundingParams;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.os.CancellationSignal;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.common.DataNotificationManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.Map;

@RunWith(JUnit4.class)
@SmallTest
public class CsSessionTest {
    private static final String MOCK_IDENTITY_ADDRESS = "11:22:33:44:55";

    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private RangingDevice mMockRangingDevice;
    @Mock
    private BleCsRangingParams mMockRangingParams;
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    private BluetoothDevice mMockBluetoothDevice;
    @Mock
    private CancellationSignal mMockCancellationSignal;
    @Mock
    private DataNotificationManager mMockDataNotificationManager1;
    @Mock
    private DataNotificationManager mMockDataNotificationManager2;
    @Mock
    private DistanceMeasurementManager mMockDistanceMeasurementManager;
    @Mock
    private DistanceMeasurementSession mMockDistanceMeasurementSession;
    @Mock
    private CsConfig mMockCsConfig;
    @Mock
    private SessionConfig mMockSessionConfig;
    @Mock
    private CsAdapter mMockCsAdapter1;
    @Mock
    private CsAdapter mMockCsAdapter2;
    @Mock
    private RangingAdapter.Callback mMockCsAdapterCallback1;
    @Mock
    private RangingAdapter.Callback mMockCsAdapterCallback2;

    private final Object mLock = new Object();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        when(mMockCsConfig.getRangingParams()).thenReturn(mMockRangingParams);
        when(mMockBluetoothAdapter.getDistanceMeasurementManager())
                .thenReturn(mMockDistanceMeasurementManager);
        when(mMockBluetoothDevice.getIdentityAddress()).thenReturn(MOCK_IDENTITY_ADDRESS);
        when(mMockDistanceMeasurementManager.startMeasurementSession(any(), any(), any()))
                .thenReturn(mMockCancellationSignal);

        when(mMockCsAdapter1.getCallbacks()).thenReturn(mMockCsAdapterCallback1);
        when(mMockCsAdapter2.getCallbacks()).thenReturn(mMockCsAdapterCallback2);
        when(mMockCsAdapter1.getDataNotificationManager())
                .thenReturn(mMockDataNotificationManager1);
        when(mMockCsAdapter2.getDataNotificationManager())
                .thenReturn(mMockDataNotificationManager2);
        when(mMockCsAdapter1.getId()).thenReturn("adapter1");
        when(mMockCsAdapter2.getId()).thenReturn("adapter2");

        when(mMockCsConfig.getSessionConfig()).thenReturn(mMockSessionConfig);
        when(mMockSessionConfig.getRangingMeasurementsLimit()).thenReturn(0);
        when(mMockRangingParams.getRangingUpdateRate()).thenReturn(UPDATE_RATE_NORMAL);

        // Provide valid parameters for ChannelSoundingParams.Builder
        when(mMockRangingParams.getLocationType())
                .thenReturn(ChannelSoundingParams.LOCATION_TYPE_UNKNOWN);
        when(mMockRangingParams.getSecurityLevel())
                .thenReturn(ChannelSoundingParams.CS_SECURITY_LEVEL_ONE);
        when(mMockRangingParams.getSightType())
                .thenReturn(ChannelSoundingParams.SIGHT_TYPE_UNKNOWN);

        setAdapterConfig(mMockCsAdapter1, mMockCsConfig);
        setAdapterConfig(mMockCsAdapter2, mMockCsConfig);
        setAdapterRangingDevice(mMockCsAdapter1, mMockRangingDevice);
        setAdapterRangingDevice(mMockCsAdapter2, mMockRangingDevice);
    }

    @After
    public void tearDown() throws Exception {
        Field field = CsSession.class.getDeclaredField("sCsSessions");
        field.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) field.get(null);
        map.clear();
    }

    private void setAdapterConfig(CsAdapter adapter, CsConfig config) throws Exception {
        Field field = CsAdapter.class.getDeclaredField("mConfig");
        field.setAccessible(true);
        field.set(adapter, config);
    }

    private void setAdapterRangingDevice(CsAdapter adapter, RangingDevice device) throws Exception {
        Field field = CsAdapter.class.getDeclaredField("mRangingDevice");
        field.setAccessible(true);
        field.set(adapter, device);
    }

    @Test
    public void testRegisterAdapter_CreatesNewSession() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        verify(mMockDistanceMeasurementManager).startMeasurementSession(any(), any(), any());
    }

    @Test
    public void testRegisterAdapter_JoinsExistingSession() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        CsSession.registerAdapter(
                mMockCsAdapter2,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        // Should only start once
        verify(mMockDistanceMeasurementManager, times(1))
                .startMeasurementSession(any(), any(), any());
    }

    @Test
    public void testDeregisterAdapter_RemovesAdapterButKeepsSessionIfOthersExist() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        CsSession.registerAdapter(
                mMockCsAdapter2,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());
        callbackCaptor.getValue().onStarted(mMockDistanceMeasurementSession);

        CsSession.deregisterAdapter(mMockCsAdapter1, MOCK_IDENTITY_ADDRESS);

        // Session should NOT be stopped because mMockCsAdapter2 is still there
        verify(mMockDistanceMeasurementSession, never()).stopSession();
    }

    @Test
    public void testDeregisterAdapter_ClosesSessionWhenLastAdapterRemoved() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());
        callbackCaptor.getValue().onStarted(mMockDistanceMeasurementSession);

        CsSession.deregisterAdapter(mMockCsAdapter1, MOCK_IDENTITY_ADDRESS);

        verify(mMockDistanceMeasurementSession).stopSession();
    }

    @Test
    public void testDeregisterAdapter_CancelsDuringStart() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        // Session hasn't called onStarted yet, so mSession is null
        CsSession.deregisterAdapter(mMockCsAdapter1, MOCK_IDENTITY_ADDRESS);

        verify(mMockCancellationSignal).cancel();
    }

    @Test
    public void testOnResult_PropagatesToStartedAdapters() {
        when(mMockCsAdapter1.getStateMachineState()).thenReturn(CsAdapter.State.STARTED);
        when(mMockDataNotificationManager1.shouldSendResult(any(Double.class))).thenReturn(true);

        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());
        callbackCaptor.getValue().onStarted(mMockDistanceMeasurementSession);

        DistanceMeasurementResult mockResult = mock(DistanceMeasurementResult.class);
        when(mockResult.getResultMeters()).thenReturn(5.0);
        when(mockResult.getConfidenceLevel()).thenReturn(0.9);
        when(mockResult.getMeasurementTimestampNanos()).thenReturn(1000000L);

        callbackCaptor.getValue().onResult(mMockBluetoothDevice, mockResult);

        ArgumentCaptor<RangingData> dataCaptor = ArgumentCaptor.forClass(RangingData.class);
        verify(mMockCsAdapterCallback1).onRangingData(eq(mMockRangingDevice), dataCaptor.capture());

        RangingData data = dataCaptor.getValue();
        assertThat(data.getDistance().getMeasurement()).isEqualTo(5.0);
    }

    @Test
    public void testOnResult_FiltersBasedOnDataNotificationManager() {
        when(mMockCsAdapter1.getStateMachineState()).thenReturn(CsAdapter.State.STARTED);
        when(mMockDataNotificationManager1.shouldSendResult(any(Double.class))).thenReturn(false);

        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());
        callbackCaptor.getValue().onStarted(mMockDistanceMeasurementSession);

        DistanceMeasurementResult mockResult = mock(DistanceMeasurementResult.class);
        when(mockResult.getResultMeters()).thenReturn(5.0);

        callbackCaptor.getValue().onResult(mMockBluetoothDevice, mockResult);

        verify(mMockCsAdapterCallback1, never()).onRangingData(any(), any());
    }

    @Test
    public void testOnStartFail_ClosesAllAdapters() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());

        callbackCaptor.getValue().onStartFail(BluetoothStatusCodes.FEATURE_NOT_SUPPORTED);

        verify(mMockCsAdapter1).closeForReason(anyInt());
    }

    @Test
    public void testOnStopped_ClosesAllAdapters() {
        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());
        callbackCaptor.getValue().onStarted(mMockDistanceMeasurementSession);

        callbackCaptor.getValue().onStopped(mMockDistanceMeasurementSession,
                BluetoothStatusCodes.REASON_REMOTE_REQUEST);

        verify(mMockCsAdapter1).closeForReason(anyInt());
    }

    @Test
    public void testMeasurementsLimit_SetsAlarm() {
        when(mMockSessionConfig.getRangingMeasurementsLimit()).thenReturn(10);

        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        verify(mMockAlarmManager).setExact(anyInt(), anyLong(), any(), any(), any());
    }

    @Test
    public void testDeregisterAdapter_SetsMeasurementLimitWhenOneAdapterLeft() {
        when(mMockSessionConfig.getRangingMeasurementsLimit()).thenReturn(10);

        CsSession.registerAdapter(
                mMockCsAdapter1,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        CsSession.registerAdapter(
                mMockCsAdapter2,
                mMockBluetoothAdapter,
                mMockBluetoothDevice,
                mMockAlarmManager,
                mMockCsConfig,
                mLock);

        ArgumentCaptor<DistanceMeasurementSession.Callback> callbackCaptor =
                ArgumentCaptor.forClass(DistanceMeasurementSession.Callback.class);
        verify(mMockDistanceMeasurementManager)
                .startMeasurementSession(any(), any(), callbackCaptor.capture());
        callbackCaptor.getValue().onStarted(mMockDistanceMeasurementSession);

        // Initially when joined, alarm should be cancelled (if it was set)
        // or at least not set for the second one.
        // registerAdapter for Adapter1 might have set it.
        // registerAdapter for Adapter2 calls currentSession.cancelMeasurementsLimit().

        CsSession.deregisterAdapter(mMockCsAdapter1, MOCK_IDENTITY_ADDRESS);

        // Now Adapter2 is alone, it should set the limit.
        verify(mMockAlarmManager, times(2)).setExact(anyInt(), anyLong(), any(), any(), any());
    }
}
