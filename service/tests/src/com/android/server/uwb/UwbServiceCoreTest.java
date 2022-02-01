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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link UwbServiceCore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbServiceCoreTest {
    @Mock private Context mContext;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Mock private UwbMetrics mUwbMetrics;
    @Mock private UwbCountryCode mUwbCountryCode;
    @Mock private UwbSessionManager mUwbSessionManager;
    @Mock private UwbConfigurationManager mUwbConfigurationManager;
    private TestLooper mTestLooper;

    private UwbServiceCore mUwbServiceCore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        when(mContext.getSystemService(PowerManager.class)).thenReturn(mock(PowerManager.class));
        mUwbServiceCore = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, mUwbConfigurationManager,
                mTestLooper.getLooper());
    }

    private void verifyGetSpecificationInfoSuccess() throws Exception {
        FiraSpecificationParams firaSpecificationParams = mock(FiraSpecificationParams.class);
        PersistableBundle firaSpecificationBundle = mock(PersistableBundle.class);
        when(firaSpecificationParams.toBundle()).thenReturn(firaSpecificationBundle);
        CccSpecificationParams cccSpecificationParams = mock(CccSpecificationParams.class);
        PersistableBundle cccSpecificationBundle = mock(PersistableBundle.class);
        when(cccSpecificationParams.toBundle()).thenReturn(cccSpecificationBundle);

        when(mUwbConfigurationManager.getCapsInfo(eq(FiraParams.PROTOCOL_NAME), any()))
                .thenReturn(Pair.create(UwbUciConstants.STATUS_CODE_OK, firaSpecificationParams));
        when(mUwbConfigurationManager.getCapsInfo(eq(CccParams.PROTOCOL_NAME), any()))
                .thenReturn(Pair.create(UwbUciConstants.STATUS_CODE_OK, cccSpecificationParams));

        PersistableBundle specifications = mUwbServiceCore.getIUwbAdapter().getSpecificationInfo();
        assertThat(specifications).isNotNull();
        assertThat(specifications.getPersistableBundle(FiraParams.PROTOCOL_NAME))
                .isEqualTo(firaSpecificationBundle);
        assertThat(specifications.getPersistableBundle(CccParams.PROTOCOL_NAME))
                .isEqualTo(cccSpecificationBundle);
        verify(mUwbConfigurationManager).getCapsInfo(eq(FiraParams.PROTOCOL_NAME), any());
        verify(mUwbConfigurationManager).getCapsInfo(eq(CccParams.PROTOCOL_NAME), any());
    }

    @Test
    public void testGetSpecificationInfoSuccess() throws Exception {
        verifyGetSpecificationInfoSuccess();
    }

    @Test
    public void testGetSpecificationInfoUsesCache() throws Exception {
        verifyGetSpecificationInfoSuccess();
        clearInvocations(mUwbConfigurationManager);

        PersistableBundle specifications = mUwbServiceCore.getIUwbAdapter().getSpecificationInfo();
        assertThat(specifications).isNotNull();
        assertThat(specifications.getPersistableBundle(FiraParams.PROTOCOL_NAME)).isNotNull();
        assertThat(specifications.getPersistableBundle(CccParams.PROTOCOL_NAME)).isNotNull();

        verifyNoMoreInteractions(mUwbConfigurationManager);
    }
}
