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

package com.android.ranging.uwb.backend.internal;

import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_FAILED_TO_START;
import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_STOP_RANGING_CALLED;
import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_UNKNOWN;
import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_WRONG_PARAMETERS;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_DT_TAG;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DL_TDOA;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.uwb.RangingSession;
import android.uwb.UwbManager;
import androidx.test.runner.AndroidJUnit4;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class RangingTagTest {

    @Mock
    private UwbManager mUwbManager;
    @Mock
    private RangingSession mRangingSession;
    @Mock
    private RangingSessionCallback mRangingSessionCallback;
    @Mock
    private UwbFeatureFlags mUwbFeatureFlags;

    private RangingTag mRangingTag;
    private UwbAddress mTestLocalAddress;
    private UwbComplexChannel mTestComplexChannel;

    private static class Mutable<E> {
        E value;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ExecutorService executor = MoreExecutors.newDirectExecutorService();
        OpAsyncCallbackRunner<Boolean> opAsyncCallbackRunner = new OpAsyncCallbackRunner<>();

        doAnswer(
                        invocation -> {
                            RangingSession.Callback callback = invocation.getArgument(2);
                            callback.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));

        mRangingTag = new RangingTag(mUwbManager, executor, opAsyncCallbackRunner,
                mUwbFeatureFlags);
        mRangingTag.setForTesting(true);
        mTestLocalAddress = mRangingTag.getLocalAddress();
        mTestComplexChannel = new UwbComplexChannel(9, 10);

        mRangingTag.setComplexChannel(mTestComplexChannel);
    }

    @Test
    public void testStartRanging_buildsCorrectFiraParams() {
        setupRangingParameters();

        mRangingTag.startRanging(mRangingSessionCallback);

        ArgumentCaptor<PersistableBundle> bundleCaptor =
                ArgumentCaptor.forClass(PersistableBundle.class);
        verify(mUwbManager).openRangingSession(bundleCaptor.capture(), any(Executor.class),
                any(RangingSession.Callback.class));

        FiraOpenSessionParams params = FiraOpenSessionParams.fromBundle(bundleCaptor.getValue());
        assertEquals(RANGING_DEVICE_TYPE_DT_TAG, params.getDeviceType());
        assertEquals(RANGING_ROUND_USAGE_DL_TDOA, params.getRangingRoundUsage());
        assertEquals(20, params.getSlotsPerRangingRound());
        assertEquals(180, params.getRangingIntervalMs());
    }

    @Test
    public void testHashSessionId_isCorrectForTag() {
        RangingParameters rangingParameters =
                new DtTagParameters(
                        0, /* sessionId */
                        new byte[]{1, 2}, /* sessionKeyInfo */
                        mTestComplexChannel,
                        Utils.DURATION_2_MS,
                        new UwbRangeLimitsConfig.Builder().build(), /* rangeLimitsConfig */
                        180, /* rangingIntervalMs */
                        20, /* slotsPerRangingRound */
                        new byte[]{0} /* rangingRoundIndexes */);

        int expectedSessionId = RangingDevice.calculateHashedSessionId(
                mTestLocalAddress, mTestComplexChannel);
        mRangingTag.setRangingParameters(rangingParameters);
        assertEquals(expectedSessionId, mRangingTag.getRangingParameters().getSessionId());
    }

    @Test
    public void testStartRanging_Success() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        mRangingTag.startRanging(mRangingSessionCallback);
        verify(mRangingSessionCallback).onRangingInitialized(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()));
    }

    @Test
    public void testStartRanging_OpenFailed() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpenFailed(
                                    REASON_UNKNOWN, new PersistableBundle());
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));

        mRangingTag.startRanging(mRangingSessionCallback);
        verify(mRangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()), REASON_FAILED_TO_START);
    }

    @Test
    public void testStartRanging_StartFailed() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStartFailed(
                                    REASON_WRONG_PARAMETERS, new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        mRangingTag.startRanging(mRangingSessionCallback);
        verify(mRangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()), REASON_FAILED_TO_START);
    }

    @Test
    public void testStopRanging() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();
        // First, successfully start the session.
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));
        mRangingTag.startRanging(mRangingSessionCallback);

        // Now, test stopping.
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStopped(
                                    RangingSession.Callback.REASON_LOCAL_REQUEST,
                                    new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .stop();
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onClosed(
                                    RangingSession.Callback.REASON_LOCAL_REQUEST,
                                    new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .close();

        mRangingTag.stopRanging();

        verify(mRangingSession).stop();
        verify(mRangingSession).close();
        verify(mRangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()),
                REASON_STOP_RANGING_CALLED);
    }

    private void setupRangingParameters() {
        RangingParameters rangingParameters =
                new DtTagParameters(
                        123, /* sessionId */
                        new byte[]{1, 2}, /* sessionKeyInfo */
                        mTestComplexChannel,
                        Utils.DURATION_2_MS,
                        new UwbRangeLimitsConfig.Builder().build(), /* rangeLimitsConfig */
                        180, /* rangingIntervalMs */
                        20, /* slotsPerRangingRound */
                        new byte[]{0} /* rangingRoundIndexes */);
        mRangingTag.setRangingParameters(rangingParameters);
    }
}
