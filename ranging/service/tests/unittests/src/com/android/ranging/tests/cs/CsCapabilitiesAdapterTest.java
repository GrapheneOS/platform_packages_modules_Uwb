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

import static android.ranging.RangingCapabilities.DISABLED_USER;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;

import static junit.framework.Assert.assertEquals;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.blerssi.BleRssiCapabilitiesAdapter;
import com.android.server.ranging.cs.CsCapabilitiesAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
@SmallTest
public class CsCapabilitiesAdapterTest {

    @Mock
    private Context mMockContext;

    @Mock
    private CapabilitiesProvider.TechnologyAvailabilityListener mMockListener;
    @Mock
    private PackageManager mMockPackageManager;
    @Mock
    private BluetoothManager mMockBluetoothManager;
    private CsCapabilitiesAdapter mAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING)).thenReturn(true);
        when(mMockContext.getSystemService(BluetoothManager.class)).thenReturn(
                mMockBluetoothManager);
        mAdapter = new CsCapabilitiesAdapter(mMockContext, mMockListener);
    }

    @Test
    public void testBleCsEnabled() {
        BluetoothAdapter bluetoothAdapter = mock(BluetoothAdapter.class);
        when(mMockBluetoothManager.getAdapter()).thenReturn(bluetoothAdapter);
        when(bluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);

        int availability = mAdapter.getAvailability();
        assertEquals(availability, ENABLED);
    }

    @Test
    public void testBleCsDisabled() {
        BluetoothAdapter bluetoothAdapter = mock(BluetoothAdapter.class);
        when(mMockBluetoothManager.getAdapter()).thenReturn(bluetoothAdapter);
        when(bluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);

        int availability = mAdapter.getAvailability();
        assertEquals(availability, DISABLED_USER);
    }

    @Test
    public void testBleCsNotSupported() {
        when(mMockContext.getSystemService(BluetoothManager.class)).thenReturn(null);
        BleRssiCapabilitiesAdapter adapter = new BleRssiCapabilitiesAdapter(mMockContext,
                mMockListener);

        int availability = adapter.getAvailability();
        assertEquals(availability, NOT_SUPPORTED);
    }
}
