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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.ranging.rtt.backend.RttServiceImpl;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class RttServiceImplTest {

    @Mock
    private Context mMockContext;

    @Mock
    private PackageManager mMockPackageManager;

    @Mock
    private WifiAwareManager mMockAwareManager;

    @Mock
    private AlarmManager mMockAlarmManager;

    private RttServiceImpl mRttService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)).thenReturn(
                true);
        when(mMockContext.getSystemService(WifiAwareManager.class)).thenReturn(mMockAwareManager);
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        mRttService = new RttServiceImpl(mMockContext);
    }

    @Test
    public void testRttServiceImpl() {
        when(mMockAwareManager.isAvailable()).thenReturn(true);
        assertThat(mRttService.getPublisher(mMockContext)).isNotNull();
        assertThat(mRttService.getSubscriber(mMockContext)).isNotNull();
        assertTrue(mRttService.isAvailable());
    }
}
