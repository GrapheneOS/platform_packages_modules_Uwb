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

package com.android.server.ranging.tests.rtt;

import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_DISABLE;
import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE;
import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.test.filters.SmallTest;

import com.android.ranging.rtt.backend.RttRangingDevice;
import com.android.ranging.rtt.backend.RttRangingParameters;
import com.android.ranging.rtt.backend.RttService;
import com.android.server.ranging.DeviceConfigFacade;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.rtt.RttAdapter;
import com.android.server.ranging.rtt.RttConfig;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
@SmallTest
public class RttAdapterTest {

    @Mock
    private Context mMockContext;
    @Mock
    private RangingInjector mMockRangingInjector;

    @Mock
    private RangingDevice mMockRangingDevice;
    @Mock
    private com.android.server.ranging.RangingAdapter.Callback mMockCallback;
    @Mock
    private AlarmManager mMockAlarmManager;

    @Mock
    private PackageManager mMockPackageManager;

    @Mock
    private WifiAwareManager mMockWifiAwareManager;
    @Mock
    private AttributionSource mMockAttributionSource;
    @Mock
    private SessionConfig mMockSessionConfig;

    private RttRangingParams mRttRangingParams = new RttRangingParams.Builder("unit_test_rtt")
            .setMatchFilter(new byte[]{0, 1})
            .build();

    @Mock
    private RttService mMockRttService;

    @Mock
    private RttRangingDevice mMockRttRangingDevice;

    @Mock
    private RttRangingParameters mMockRttRangingParameters;

    @Mock
    private DeviceConfigFacade mMockDeviceConfigFacade;

    private final DataNotificationConfig mDataNotificationConfig =
            new DataNotificationConfig.Builder().build();

    private RttAdapter mRttAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        when(mMockContext.getSystemService(PackageManager.class)).thenReturn(mMockPackageManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(
                PackageManager.FEATURE_WIFI_AWARE)).thenReturn(true);
        when(mMockPackageManager.hasSystemFeature(
                PackageManager.FEATURE_WIFI_RTT)).thenReturn(true);
        when(mMockContext.getSystemService(WifiAwareManager.class)).thenReturn(
                mMockWifiAwareManager);
        when(mMockSessionConfig.getDataNotificationConfig()).thenReturn(mDataNotificationConfig);
        when(mMockAttributionSource.getUid()).thenReturn(100);
        when(mMockAttributionSource.getPackageName()).thenReturn("TestPkgName");
        when(mMockRangingInjector.isForegroundAppOrService(anyInt(), anyString())).thenReturn(true);
        when(mMockRttService.getPublisher(mMockContext)).thenReturn(mMockRttRangingDevice);
        when(mMockRttService.getSubscriber(mMockContext)).thenReturn(mMockRttRangingDevice);
        when(mMockRangingInjector.getDeviceConfigFacade()).thenReturn(mMockDeviceConfigFacade);
        when(mMockDeviceConfigFacade.getRttRangingRequestDelay()).thenReturn(0);
        RangingInjector.setInstance(mMockRangingInjector);
        when(mMockRangingInjector.isRangingTechnologyEnabled(any())).thenReturn(true);
        mRttAdapter = new RttAdapter(mMockContext, mMockRangingInjector,
                MoreExecutors.newDirectExecutorService(),
                mMockRttService, DEVICE_ROLE_INITIATOR);
    }

    @Test
    public void testStartWithValidConfig() {
        RttConfig config = new RttConfig(
                DEVICE_ROLE_INITIATOR,
                mRttRangingParams,
                mMockSessionConfig,
                mMockRangingDevice
        );
        mRttAdapter.start(config, null, mMockCallback);

        verify(mMockRttRangingDevice, times(1)).startRanging(any(), any());
        verify(mMockSessionConfig).getRangingMeasurementsLimit();

        mRttAdapter.stop();
        verify(mMockRttRangingDevice, times(1)).stopRanging();
    }

    @Test
    public void testStartWithMeasurementsLimits() {
        RttConfig config = new RttConfig(
                DEVICE_ROLE_INITIATOR,
                mRttRangingParams,
                new SessionConfig.Builder().setRangingMeasurementsLimit(100).build(),
                mMockRangingDevice
        );
        when(mMockRttRangingDevice.getRttRangingParameters()).thenReturn(mMockRttRangingParameters);
        when(mMockRttRangingParameters.getUpdateRate()).thenReturn(1);
        mRttAdapter.start(config, null, mMockCallback);

        verify(mMockRttRangingDevice, times(1)).startRanging(any(), any());

        mRttAdapter.stop();
        verify(mMockRttRangingDevice, times(1)).stopRanging();
    }

    @Test
    public void testStop_WhenNotStarted() {
        mRttAdapter.stop();
        verify(mMockRttRangingDevice, never()).stopRanging();
    }

    @Test
    public void testAppMovingBackgroundForeground() {
        RttConfig config = new RttConfig(
                DEVICE_ROLE_INITIATOR,
                mRttRangingParams,
                mMockSessionConfig,
                mMockRangingDevice
        );
        mRttAdapter.start(config, mMockAttributionSource, mMockCallback);

        verify(mMockRttRangingDevice, times(1)).startRanging(any(), any());

        mRttAdapter.appMovedToBackground();

        assertEquals(mRttAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_DISABLE);

        mRttAdapter.appMovedToForeground();

        assertEquals(mRttAdapter.getDataNotificationManager().getCurrentConfig()
                .getNotificationConfigType(), NOTIFICATION_CONFIG_ENABLE);

        mRttAdapter.stop();
        verify(mMockRttRangingDevice, times(1)).stopRanging();
    }

    @Test
    public void testAppInBackgroundTimeout() {
        RttConfig config = new RttConfig(
                DEVICE_ROLE_RESPONDER,
                mRttRangingParams,
                mMockSessionConfig,
                mMockRangingDevice
        );
        mRttAdapter.start(config, mMockAttributionSource, mMockCallback);

        mRttAdapter.appInBackgroundTimeout();
        verify(mMockRttRangingDevice, times(1)).stopRanging();
    }
}
