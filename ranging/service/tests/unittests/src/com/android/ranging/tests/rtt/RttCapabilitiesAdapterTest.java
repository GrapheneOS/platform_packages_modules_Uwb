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

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.aware.WifiAwareManager;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.rtt.RttCapabilitiesAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
@SmallTest
public class RttCapabilitiesAdapterTest {
    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;

    @Mock
    private WifiAwareManager mMockWifiAwareManager;

    @Mock
    private CapabilitiesProvider.TechnologyAvailabilityListener mMockListener;

    private RttCapabilitiesAdapter mAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(PackageManager.class)).thenReturn(mMockPackageManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(
                PackageManager.FEATURE_WIFI_AWARE)).thenReturn(true);
        when(mMockPackageManager.hasSystemFeature(
                PackageManager.FEATURE_WIFI_RTT)).thenReturn(true);
        when(mMockContext.getSystemService(WifiAwareManager.class)).thenReturn(
                mMockWifiAwareManager);
        mAdapter = new RttCapabilitiesAdapter(mMockContext, mMockListener);
    }

    @Test
    public void testRttCapabilitiesAdapter_enabled() {
        when(mMockWifiAwareManager.isAvailable()).thenReturn(true);
        assertEquals(mAdapter.getAvailability(), ENABLED);
    }

    @Test
    public void testRttCapabilitiesAdapter_disabled() {
        assertEquals(mAdapter.getAvailability(), DISABLED_USER);
    }

    @Test
    public void testRttGetCapabilities() {
        when(mMockWifiAwareManager.isAvailable()).thenReturn(true);
        assertThat(mAdapter.getCapabilities()).isNotNull();
    }
}
