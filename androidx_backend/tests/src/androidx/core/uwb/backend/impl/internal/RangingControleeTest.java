/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.uwb.backend.impl.internal;

import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_STOP_RANGING_CALLED;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_1;
import static androidx.core.uwb.backend.impl.internal.Utils.INFREQUENT;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.RangingSession;
import android.uwb.UwbManager;

import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class RangingControleeTest {
    @Mock private UwbManager mUwbManager;
    @Mock private UwbComplexChannel mComplexChannel;
    @Mock private OpAsyncCallbackRunner mOpAsyncCallbackRunner;
    @Mock private ExecutorService mBackendCallbackExecutor;
    private RangingControlee mRangingControlee;

    private static Executor getExecutor() {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private static class Mutable<E> {
        public E value;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        doAnswer(invocation -> {
            Runnable t = invocation.getArgument(0);
            t.run();
            return true;
        }).when(mOpAsyncCallbackRunner).execOperation(any(Runnable.class), anyString());

        doAnswer(invocation -> {
            Runnable t = invocation.getArgument(0);
            t.run();
            return true;
        }).when(mBackendCallbackExecutor).execute(any(Runnable.class));

        mRangingControlee = new RangingControlee(
                mUwbManager, getExecutor(), mOpAsyncCallbackRunner);
        RangingParameters rangingParameters = new RangingParameters(CONFIG_ID_1, 1,
                new byte[]{1, 2}, mComplexChannel,
                new ArrayList<>(List.of(UwbAddress.getRandomizedShortAddress())), INFREQUENT);
        mRangingControlee.setRangingParameters(rangingParameters);
    }

    @Test
    public void testGetOpenSessionParams() {
        FiraOpenSessionParams params = mRangingControlee.getOpenSessionParams();
        assertEquals(params.getDeviceType(), FiraParams.RANGING_DEVICE_TYPE_CONTROLEE);
    }

    @Test
    public void testStartRangingSession() {
        UwbAddress deviceAddress = mRangingControlee.getLocalAddress();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(invocation -> {
            pfRangingSessionCallback.value = invocation.getArgument(2);
            pfRangingSessionCallback.value.onOpened(pfRangingSession);
            return new CancellationSignal();
        }).when(mUwbManager).openRangingSession(
                any(PersistableBundle.class), any(Executor.class),
                any(RangingSession.Callback.class));

        doAnswer(invocation -> {
            pfRangingSessionCallback.value.onStarted(new PersistableBundle());
            return true;
        }).when(pfRangingSession).start(any(PersistableBundle.class));

        assertEquals(mRangingControlee.startRanging(
                rangingSessionCallback, mBackendCallbackExecutor), STATUS_OK);
        verify(mUwbManager).openRangingSession(any(), any(), any());
        verify(pfRangingSession).start(any());
        verify(rangingSessionCallback).onRangingInitialized(
                UwbDevice.createForAddress(deviceAddress.toBytes()));
    }

    @Test
    public void testStopRanging() {
        UwbAddress deviceAddress = mRangingControlee.getLocalAddress();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(invocation -> {
            pfRangingSessionCallback.value = invocation.getArgument(2);
            pfRangingSessionCallback.value.onOpened(pfRangingSession);
            return new CancellationSignal();
        }).when(mUwbManager).openRangingSession(
                any(PersistableBundle.class), any(Executor.class),
                any(RangingSession.Callback.class));

        doAnswer(invocation -> {
            pfRangingSessionCallback.value.onStarted(new PersistableBundle());
            return true;
        }).when(pfRangingSession).start(any(PersistableBundle.class));

        doAnswer(invocation -> {
            pfRangingSessionCallback.value.onStopped(
                    RangingSession.Callback.REASON_LOCAL_REQUEST, new PersistableBundle());
            return true;
        }).when(pfRangingSession).stop();

        doAnswer(invocation -> {
            pfRangingSessionCallback.value.onClosed(
                    RangingSession.Callback.REASON_LOCAL_REQUEST, new PersistableBundle());
            return true;
        }).when(pfRangingSession).close();

        mRangingControlee.startRanging(rangingSessionCallback, mBackendCallbackExecutor);
        assertEquals(mRangingControlee.stopRanging(), STATUS_OK);
        verify(pfRangingSession).stop();
        verify(pfRangingSession).close();
        verify(rangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(deviceAddress.toBytes()), REASON_STOP_RANGING_CALLED);
    }
}
