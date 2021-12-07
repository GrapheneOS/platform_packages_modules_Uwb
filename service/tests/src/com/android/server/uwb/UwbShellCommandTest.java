/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Binder;
import android.os.Process;
import android.uwb.UwbManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;

/**
 * Unit tests for {@link com.android.server.uwb.UwbShellCommand}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class UwbShellCommandTest {
    private static final String TEST_PACKAGE = "com.android.test";

    @Mock UwbInjector mUwbInjector;
    @Mock UwbServiceImpl mUwbService;
    @Mock UwbCountryCode mUwbCountryCode;
    @Mock Context mContext;

    UwbShellCommand mUwbShellCommand;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mUwbInjector.getUwbCountryCode()).thenReturn(mUwbCountryCode);

        mUwbShellCommand = new UwbShellCommand(mUwbInjector, mUwbService, mContext);

        // by default emulate shell uid.
        BinderUtil.setUid(Process.SHELL_UID);
    }

    @After
    public void tearDown() throws Exception {
        validateMockitoUsage();
    }

    @Test
    public void testStatus() throws Exception {
        when(mUwbService.getAdapterState())
                .thenReturn(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE);

        // unrooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"status"});
        verify(mUwbService).getAdapterState();
    }

    @Test
    public void testForceSetCountryCode() throws Exception {
        // not allowed for unrooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "enabled", "US"});
        verify(mUwbCountryCode, never()).setOverrideCountryCode(any());
        assertThat(mUwbShellCommand.getErrPrintWriter().toString().isEmpty()).isFalse();

        BinderUtil.setUid(Process.ROOT_UID);

        // rooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "enabled", "US"});
        verify(mUwbCountryCode).setOverrideCountryCode(any());

    }

    @Test
    public void testForceClearCountryCode() throws Exception {
        // not allowed for unrooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "disabled"});
        verify(mUwbCountryCode, never()).setOverrideCountryCode(any());
        assertThat(mUwbShellCommand.getErrPrintWriter().toString().isEmpty()).isFalse();

        BinderUtil.setUid(Process.ROOT_UID);

        // rooted shell.
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"force-country-code", "disabled"});
        verify(mUwbCountryCode).clearOverrideCountryCode();
    }

    @Test
    public void testGetCountryCode() throws Exception {
        mUwbShellCommand.exec(
                new Binder(), new FileDescriptor(), new FileDescriptor(), new FileDescriptor(),
                new String[]{"get-country-code"});
        verify(mUwbCountryCode).getCountryCode();
    }
}
