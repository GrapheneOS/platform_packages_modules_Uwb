/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ranging.uwb.tests;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.filters.SmallTest;

import com.android.ranging.RangingAdapter;
import com.android.ranging.RangingParameters.DeviceRole;
import com.android.ranging.RangingTechnology;
import com.android.ranging.cs.CsParameters;
import com.android.ranging.uwb.UwbAdapter;
import com.android.ranging.uwb.UwbParameters;
import com.android.ranging.uwb.backend.internal.RangingController;
import com.android.ranging.uwb.backend.internal.RangingPosition;
import com.android.ranging.uwb.backend.internal.RangingSessionCallback;
import com.android.ranging.uwb.backend.internal.UwbDevice;
import com.android.ranging.uwb.backend.internal.UwbServiceImpl;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.ExecutionException;

@RunWith(JUnit4.class)
@SmallTest
public class UwbAdapterTest {
    @Rule public final MockitoRule mMockito = MockitoJUnit.rule();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private Context mMockContext;
    @Mock private UwbServiceImpl mMockUwbService;
    @Mock private RangingController mMockUwbClient;

    @Mock private RangingAdapter.Callback mMockCallback;

    /** Class under test */
    private UwbAdapter mUwbAdapter;

    @Before
    public void setup() {
        when(mMockContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB))
                .thenReturn(true);
        when(mMockUwbService.getController(any())).thenReturn(mMockUwbClient);
        mUwbAdapter = new UwbAdapter(mMockContext, MoreExecutors.newDirectExecutorService(),
                mMockUwbService, DeviceRole.CONTROLLER);
    }

    @Test
    public void getType_returnsUwb() {
        Assert.assertEquals(RangingTechnology.UWB, mUwbAdapter.getType());
    }

    @Test
    public void isEnabled_checksServiceIsAvailable()
            throws InterruptedException, ExecutionException {
        when(mMockUwbService.isAvailable()).thenReturn(true);
        Assert.assertTrue(mUwbAdapter.isEnabled().get());
    }

    @Test
    public void start_failsWhenParamsInvalid() {
        mUwbAdapter.start(mock(CsParameters.class), mMockCallback);
        verify(mMockCallback).onStopped(eq(RangingAdapter.Callback.StoppedReason.FAILED_TO_START));
        verify(mMockCallback, never()).onStarted();
    }

    @Test
    public void start_startsUwbClientWithCallbacks() {
        mUwbAdapter.start(mock(UwbParameters.class), mMockCallback);

        ArgumentCaptor<RangingSessionCallback> callbackCaptor =
                ArgumentCaptor.forClass(RangingSessionCallback.class);
        verify(mMockUwbClient).startRanging(callbackCaptor.capture(), any());

        UwbDevice mockUwbdevice = mock(UwbDevice.class, Answers.RETURNS_DEEP_STUBS);
        callbackCaptor.getValue().onRangingInitialized(mockUwbdevice);
        verify(mMockCallback).onStarted();

        callbackCaptor.getValue().onRangingResult(
                mockUwbdevice, mock(RangingPosition.class, Answers.RETURNS_DEEP_STUBS));
        verify(mMockCallback).onRangingData(any());

        callbackCaptor.getValue().onRangingSuspended(mockUwbdevice, anyInt());
        verify(mMockCallback).onStopped(anyInt());
    }

    @Test
    public void stop_stopsUwbClient() {
        mUwbAdapter.start(mock(UwbParameters.class), mMockCallback);
        mUwbAdapter.stop();
        verify(mMockUwbClient).stopRanging();
    }
}
