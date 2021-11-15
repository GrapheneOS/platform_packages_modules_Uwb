/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import static org.mockito.Mockito.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.test.TestLooper;
import android.telephony.TelephonyManager;

import androidx.test.filters.SmallTest;

import com.android.uwb.jni.NativeUwbManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;

/**
 * Unit tests for {@link com.android.server.uwb.UwbCountryCode}.
 */
@SmallTest
public class UwbCountryCodeTest {
    private static final String TEST_COUNTRY_CODE = "JP";

    @Mock Context mContext;
    @Mock TelephonyManager mTelephonyManager;
    @Mock NativeUwbManager mNativeUwbManager;
    @Mock UwbInjector mUwbInjector;
    private TestLooper mTestLooper;
    private UwbCountryCode mUwbCountryCode;

    @Captor
    private ArgumentCaptor<BroadcastReceiver> mTelephonyCountryCodeReceiverCaptor;

    /**
     * Setup test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();

        when(mContext.getSystemService(TelephonyManager.class))
                .thenReturn(mTelephonyManager);
        mUwbCountryCode = new UwbCountryCode(
                mContext, mNativeUwbManager, new Handler(mTestLooper.getLooper()), mUwbInjector);
    }

    @Test
    public void testInitializeCountryCodeFromTelephony() {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(TEST_COUNTRY_CODE);
        mUwbCountryCode.initialize();
        verify(mNativeUwbManager).setCountryCode(
                TEST_COUNTRY_CODE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testSetCountryCodeFromTelephony() {
        when(mTelephonyManager.getNetworkCountryIso()).thenReturn(TEST_COUNTRY_CODE);
        mUwbCountryCode.initialize();
        clearInvocations(mNativeUwbManager);
        mUwbCountryCode.setCountryCode();
        verify(mNativeUwbManager).setCountryCode(
                TEST_COUNTRY_CODE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testSetCountryCodeFromOemWhenTelephonyNotAvailable() {
        when(mUwbInjector.getOemDefaultCountryCode()).thenReturn(TEST_COUNTRY_CODE);
        mUwbCountryCode.initialize();
        clearInvocations(mNativeUwbManager);
        mUwbCountryCode.setCountryCode();
        verify(mNativeUwbManager).setCountryCode(
                TEST_COUNTRY_CODE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testChangeInTelephonyCountryCode() {
        mUwbCountryCode.initialize();
        verify(mContext).registerReceiver(
                mTelephonyCountryCodeReceiverCaptor.capture(), any(), any(), any());
        Intent intent = new Intent(TelephonyManager.ACTION_NETWORK_COUNTRY_CHANGED)
                .putExtra(TelephonyManager.EXTRA_NETWORK_COUNTRY, TEST_COUNTRY_CODE);
        mTelephonyCountryCodeReceiverCaptor.getValue().onReceive(mock(Context.class), intent);
        verify(mNativeUwbManager).setCountryCode(
                TEST_COUNTRY_CODE.getBytes(StandardCharsets.UTF_8));
    }
}
