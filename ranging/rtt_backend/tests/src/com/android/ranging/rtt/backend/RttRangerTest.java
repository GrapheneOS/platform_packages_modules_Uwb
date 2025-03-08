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
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.WifiRttManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.ranging.rtt.backend.RttRanger;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RttRangerTest {

    @Mock
    private Context mMockContext;

    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private WifiRttManager mMockRttManager;

    @Mock
    private PeerHandle mMockPeerHandle;

    @Mock
    private RttRanger.RttRangerListener mMockListener;

    private RttRanger mRttRanger;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        when(mMockRttManager.isAvailable()).thenReturn(true);

        mRttRanger = new RttRanger(mMockRttManager, MoreExecutors.newDirectExecutorService(),
                mMockContext);
    }

    @Test
    public void testStartStopRanging() {
        mRttRanger.startRanging(mMockPeerHandle, mMockListener, 200, 0);

        verify(mMockRttManager, times(1)).startRanging(any(), any(), any());

        mRttRanger.stopRanging();
        verify(mMockAlarmManager, times(1)).cancel(any(AlarmManager.OnAlarmListener.class));
    }

    @Test
    public void testStartRanging_whenDisabled() {
        when(mMockRttManager.isAvailable()).thenReturn(false);
        mRttRanger.startRanging(mMockPeerHandle, mMockListener, 200, 0);

        verify(mMockRttManager, times(0)).startRanging(any(), any(), any());
        verify(mMockListener).onRangingFailure(
                RttRanger.RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);

        mRttRanger.stopRanging();
        verify(mMockAlarmManager, times(0)).cancel(any(AlarmManager.OnAlarmListener.class));
    }
}
