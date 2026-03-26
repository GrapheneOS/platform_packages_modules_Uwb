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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(JUnit4.class)
@SmallTest
public class CsAdapterTest {
    private static final String MOCK_PSEUDO_ADDRESS = "11:22:33:44:55:66";

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private RangingInjector mMockRangingInjector;
    @Mock
    private BluetoothManager mMockBluetoothManager;
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    private BluetoothDevice mMockBluetoothDevice;
    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private RangingAdapter.Callback mMockCallback;
    @Mock
    private RangingDevice mMockRangingDevice;
    @Mock
    private BleCsRangingParams mMockRangingParams;
    @Mock
    private CsConfig mMockCsConfig;
    @Mock
    private SessionConfig mMockSessionConfig;
    @Mock
    private AttributionSource mMockAttributionSource;

    private final DataNotificationConfig mDataNotificationConfig =
            new DataNotificationConfig.Builder()
                    .setNotificationConfigType(DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE)
                    .build();

    private MockitoSession mStaticSession;
    private final Object mLock = new Object();
    private CsAdapter mCsAdapter;

    @Before
    public void setUp() throws Exception {
        mStaticSession = mockitoSession()
                .initMocks(this)
                .mockStatic(CsSession.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        RangingInjector.setInstance(mMockRangingInjector);

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING))
                        .thenReturn(true);
        when(mMockRangingInjector.isRangingTechnologyEnabled(RangingTechnology.CS))
                .thenReturn(true);

        when(mMockContext.getSystemService(BluetoothManager.class))
                .thenReturn(mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);

        when(mMockBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        when(mMockBluetoothAdapter.getRemoteDevice(anyString())).thenReturn(mMockBluetoothDevice);

        when(mMockCsConfig.getRangingParams()).thenReturn(mMockRangingParams);
        when(mMockCsConfig.getPeerDevices()).thenReturn(ImmutableSet.of(mMockRangingDevice));
        when(mMockCsConfig.getSessionConfig()).thenReturn(mMockSessionConfig);
        when(mMockCsConfig.getPeerBluetoothDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.getAddress()).thenReturn(MOCK_PSEUDO_ADDRESS);
        when(mMockRangingParams.getPeerBluetoothAddress()).thenReturn(MOCK_PSEUDO_ADDRESS);
        when(mMockSessionConfig.getDataNotificationConfig())
                .thenReturn(mDataNotificationConfig);

        when(mMockRangingInjector.isForegroundAppOrService(anyInt(), anyString())).thenReturn(true);
        when(mMockAttributionSource.getUid()).thenReturn(1234);
        when(mMockAttributionSource.getPackageName()).thenReturn("com.test");

        mCsAdapter = new CsAdapter(mMockContext, mMockRangingInjector, mLock);
    }

    @After
    public void tearDown() {
        if (mStaticSession != null) {
            mStaticSession.finishMocking();
        }
    }

    @Test
    public void testGetTechnology() {
        assertThat(mCsAdapter.getTechnology()).isEqualTo(RangingTechnology.CS);
    }

    @Test
    public void testGetId() {
        assertThat(mCsAdapter.getId()).isNotNull();
    }

    @Test
    public void testStart_Success() {
        mCsAdapter.start(mMockCsConfig, null, mMockCallback);

        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STARTED);
        verify(() -> CsSession.registerAdapter(
                eq(mCsAdapter),
                eq(mMockBluetoothAdapter),
                eq(mMockBluetoothDevice),
                eq(mMockAlarmManager),
                eq(mMockCsConfig),
                eq(mLock)));
    }

    @Test
    public void testStart_BluetoothOff() {
        when(mMockBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);

        mCsAdapter.start(mMockCsConfig, null, mMockCallback);

        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STOPPED);
        verify(mMockCallback).onClosed(InternalReason.UNSUPPORTED);
    }

    @Test
    public void testStart_BackgroundRangingNotAllowed() {
        when(mMockRangingInjector.isForegroundAppOrService(anyInt(), anyString()))
                .thenReturn(false);

        mCsAdapter.start(mMockCsConfig, mMockAttributionSource, mMockCallback);

        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STOPPED);
        verify(mMockCallback).onClosed(InternalReason.BACKGROUND_RANGING_POLICY);
    }

    @Test
    public void testStop() {
        mCsAdapter.start(mMockCsConfig, null, mMockCallback);
        mCsAdapter.stop();

        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STOPPED);
        verify(() -> CsSession.deregisterAdapter(eq(mCsAdapter), eq(MOCK_PSEUDO_ADDRESS)));
        verify(mMockCallback).onClosed(InternalReason.LOCAL_REQUEST);
    }

    @Test
    public void testAppInBackgroundTimeout() {
        mCsAdapter.start(mMockCsConfig, mMockAttributionSource, mMockCallback);
        mCsAdapter.appInBackgroundTimeout();

        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STOPPED);
        verify(mMockCallback).onClosed(InternalReason.LOCAL_REQUEST);
    }

    @Test
    public void testCloseForReason() {
        mCsAdapter.start(mMockCsConfig, null, mMockCallback);
        mCsAdapter.closeForReason(InternalReason.INTERNAL_ERROR);

        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STOPPED);
        verify(mMockCallback).onClosed(InternalReason.INTERNAL_ERROR);
        assertThat(mCsAdapter.getCallbacks()).isNull();
    }

    @Test
    public void testAppMovedToBackground() {
        mCsAdapter.start(mMockCsConfig, mMockAttributionSource, mMockCallback);
        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STARTED);

        mCsAdapter.appMovedToBackground();

        assertThat(mCsAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType())
                        .isEqualTo(DataNotificationConfig.NOTIFICATION_CONFIG_DISABLE);
    }

    @Test
    public void testAppMovedToForeground() {
        mCsAdapter.start(mMockCsConfig, mMockAttributionSource, mMockCallback);
        assertThat(mCsAdapter.getStateMachineState()).isEqualTo(CsAdapter.State.STARTED);

        mCsAdapter.appMovedToBackground();
        mCsAdapter.appMovedToForeground();

        assertThat(mCsAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType())
                        .isEqualTo(DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE);
    }
}
