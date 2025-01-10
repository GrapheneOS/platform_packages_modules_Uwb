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

package com.android.ranging.tests.rtt.backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.rtt.WifiRttManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.ranging.rtt.backend.RttRangingDevice;
import com.android.ranging.rtt.backend.RttRangingParameters;
import com.android.ranging.rtt.backend.RttRangingSessionCallback;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutorService;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RttRangingDeviceTest {

    @Mock
    private Context mMockContext;

    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private WifiRttManager mMockRttManager;

    @Mock
    private WifiAwareManager mMockAwareManager;

    @Mock
    private RttRangingSessionCallback mMockRttListener;


    private RttRangingDevice mRangingDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        when(mMockContext.getSystemService(WifiAwareManager.class)).thenReturn(mMockAwareManager);
        when(mMockContext.getSystemService(WifiRttManager.class)).thenReturn(mMockRttManager);
        when(mMockAwareManager.isAvailable()).thenReturn(true);
        when(mMockRttManager.isAvailable()).thenReturn(true);

        mRangingDevice = new RttRangingDevice(mMockContext, RttRangingDevice.DeviceType.SUBSCRIBER);
    }

    @Test
    public void testStartStopRanging() throws Exception {
        ExecutorService executor = MoreExecutors.newDirectExecutorService();
        mRangingDevice.setRangingParameters(new RttRangingParameters.Builder().build());
        mRangingDevice.startRanging(mMockRttListener, executor);

        verify(mMockAwareManager, times(1)).attach(any(), any());
    }
}
