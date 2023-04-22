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

import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_FAILED_TO_START;
import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_STOP_RANGING_CALLED;
import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_UNKNOWN;
import static androidx.core.uwb.backend.impl.internal.RangingSessionCallback.REASON_WRONG_PARAMETERS;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_MULTICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.INFREQUENT;
import static androidx.core.uwb.backend.impl.internal.Utils.INVALID_API_CALL;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_DISABLE;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE_PROXIMITY_LEVEL_TRIG;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class RangingControllerTest {
    @Mock
    private UwbManager mUwbManager;
    @Mock
    private RangingSessionCallback mRangingSessionCallback;
    @Mock
    private UwbComplexChannel mComplexChannel;
    private final OpAsyncCallbackRunner<Boolean> mOpAsyncCallbackRunner =
            new OpAsyncCallbackRunner<>();
    @Mock
    private ExecutorService mBackendCallbackExecutor;

    @Captor
    private ArgumentCaptor<PersistableBundle> mBundleArgumentCaptor;

    private RangingController mRangingController;
    private UwbAddress mRangingParamsKnownPeerAddress;

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
        doAnswer(
                invocation -> {
                    Runnable t = invocation.getArgument(0);
                    t.run();
                    return true;
                })
                .when(mBackendCallbackExecutor)
                .execute(any(Runnable.class));
        UwbRangeDataNtfConfig uwbRangeDataNtfConfig =
                new UwbRangeDataNtfConfig.Builder()
                        .setRangeDataConfigType(RANGE_DATA_NTF_ENABLE_PROXIMITY_LEVEL_TRIG)
                        .setNtfProximityNear(100)
                        .setNtfProximityFar(300)
                        .build();
        mRangingParamsKnownPeerAddress = UwbAddress.getRandomizedShortAddress();
        RangingParameters rangingParameters =
                new RangingParameters(
                        CONFIG_MULTICAST_DS_TWR,
                        1,
                        0,
                        new byte[]{1, 2},
                        new byte[]{1, 2},
                        mComplexChannel,
                        new ArrayList<>(List.of(mRangingParamsKnownPeerAddress)),
                        INFREQUENT,
                        uwbRangeDataNtfConfig);
        mRangingController =
                new RangingController(mUwbManager, getExecutor(), mOpAsyncCallbackRunner,
                        new UwbFeatureFlags.Builder().build());
        mRangingController.setRangingParameters(rangingParameters);
        mRangingController.setForTesting(true);
    }

    @Test
    public void testGetOpenSessionParams() {
        FiraOpenSessionParams params = mRangingController.getOpenSessionParams();
        assertEquals(params.getDeviceType(), FiraParams.RANGING_DEVICE_TYPE_CONTROLLER);
    }

    @Test
    public void testGetComplexChannel() {
        UwbComplexChannel channel = mRangingController.getComplexChannel();
        assertEquals(channel.getChannel(), Utils.channelForTesting);
        assertEquals(channel.getPreambleIndex(), Utils.preambleIndexForTesting);
    }

    @Test
    public void testSetComplexChannel() {
        UwbComplexChannel complexChannel = new UwbComplexChannel(9, 10);
        mRangingController.setComplexChannel(complexChannel);
        assertEquals(complexChannel.getChannel(), 9);
        assertEquals(complexChannel.getPreambleIndex(), 10);
    }

    @Test
    public void testSetRangingParameterWithSessionIdUnset() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        UwbComplexChannel complexChannel = mRangingController.getComplexChannel();
        UwbRangeDataNtfConfig uwbRangeDataNtfConfig =
                new UwbRangeDataNtfConfig.Builder()
                        .setRangeDataConfigType(RANGE_DATA_NTF_DISABLE)
                        .build();
        RangingParameters rangingParameters =
                new RangingParameters(
                        CONFIG_MULTICAST_DS_TWR,
                        0,
                        0,
                        new byte[]{1, 2},
                        new byte[]{1, 2},
                        mComplexChannel,
                        List.of(UwbAddress.fromBytes(new byte[]{3, 4})),
                        INFREQUENT,
                        uwbRangeDataNtfConfig);

        mRangingController.setRangingParameters(rangingParameters);

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor);

        verify(mUwbManager).openRangingSession(mBundleArgumentCaptor.capture(), any(), any());
        assertEquals(
                RangingDevice.calculateHashedSessionId(deviceAddress, complexChannel),
                mBundleArgumentCaptor.getValue().getInt("session_id"));
    }

    @Test
    public void testGetBestAvailableComplexChannel() {
        UwbComplexChannel channel = mRangingController.getBestAvailableComplexChannel();
        assertEquals(channel.getChannel(), Utils.channelForTesting);
    }

    @Test
    public void testStartRanging() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        mRangingController.getComplexChannel();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(pfRangingSession);
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
                    return true;
                })
                .when(pfRangingSession)
                .start(any(PersistableBundle.class));

        assertEquals(
                mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor),
                STATUS_OK);
        verify(mUwbManager).openRangingSession(any(), any(), any());
        verify(pfRangingSession).start(any());
        verify(rangingSessionCallback)
                .onRangingInitialized(UwbDevice.createForAddress(deviceAddress.toBytes()));
    }

    @Test
    public void testStartRanging_openSessionFailed_onRangingSuspendedInvoked() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        mRangingController.getComplexChannel();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpenFailed(REASON_UNKNOWN,
                            new PersistableBundle());
                    return new CancellationSignal();
                })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));

        assertEquals(
                mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor),
                STATUS_OK);
        verify(rangingSessionCallback)
                .onRangingSuspended(UwbDevice.createForAddress(deviceAddress.toBytes()),
                        REASON_FAILED_TO_START);
    }

    @Test
    public void testStartRanging_ranginStartFailed() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        mRangingController.getComplexChannel();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(pfRangingSession);
                    return new CancellationSignal();
                })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onStartFailed(REASON_WRONG_PARAMETERS,
                            new PersistableBundle());
                    return true;
                })
                .when(pfRangingSession)
                .start(any(PersistableBundle.class));

        assertEquals(
                mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor),
                STATUS_OK);
        verify(mUwbManager).openRangingSession(any(), any(), any());
        verify(pfRangingSession).start(any());
        verify(rangingSessionCallback)
                .onRangingSuspended(UwbDevice.createForAddress(deviceAddress.toBytes()),
                        REASON_FAILED_TO_START);
    }


    @Test
    public void testStopRanging() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        mRangingController.getComplexChannel();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(pfRangingSession);
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
                    return true;
                })
                .when(pfRangingSession)
                .start(any(PersistableBundle.class));

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onStopped(
                            RangingSession.Callback.REASON_LOCAL_REQUEST,
                            new PersistableBundle());
                    return true;
                })
                .when(pfRangingSession)
                .stop();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onClosed(
                            RangingSession.Callback.REASON_LOCAL_REQUEST,
                            new PersistableBundle());
                    return true;
                })
                .when(pfRangingSession)
                .close();

        mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor);
        assertEquals(mRangingController.stopRanging(), STATUS_OK);
        verify(pfRangingSession).stop();
        verify(pfRangingSession).close();
        verify(rangingSessionCallback)
                .onRangingSuspended(
                        UwbDevice.createForAddress(deviceAddress.toBytes()),
                        REASON_STOP_RANGING_CALLED);
    }

    @Test
    public void testAddControlee() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        UwbAddress peerAddress = UwbAddress.getRandomizedShortAddress();
        mRangingController.getComplexChannel();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(pfRangingSession);
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
                    return true;
                })
                .when(pfRangingSession)
                .start(any(PersistableBundle.class));

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onReconfigured(new PersistableBundle());
                    return true;
                })
                .when(pfRangingSession)
                .reconfigure(any(PersistableBundle.class));

        mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor);
        assertEquals(mRangingController.addControlee(peerAddress), STATUS_OK);
        verify(pfRangingSession).reconfigure(any(PersistableBundle.class));
        verify(rangingSessionCallback)
                .onRangingInitialized(UwbDevice.createForAddress(peerAddress.toBytes()));
    }

    @Test
    public void testRemoveControlee() {
        UwbAddress deviceAddress = mRangingController.getLocalAddress();
        UwbAddress peerAddress = UwbAddress.getRandomizedShortAddress();
        mRangingController.getComplexChannel();

        final RangingSessionCallback rangingSessionCallback = mock(RangingSessionCallback.class);
        final RangingSession pfRangingSession = mock(RangingSession.class);
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(pfRangingSession);
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
                    return true;
                })
                .when(pfRangingSession)
                .start(any(PersistableBundle.class));

        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onReconfigured(new PersistableBundle());
                    return true;
                })
                .when(pfRangingSession)
                .reconfigure(any(PersistableBundle.class));

        mRangingController.startRanging(rangingSessionCallback, mBackendCallbackExecutor);
        mRangingController.addControlee(peerAddress);
        assertEquals(mRangingController.removeControlee(peerAddress), STATUS_OK);
        assertEquals(mRangingController.removeControlee(mRangingParamsKnownPeerAddress), STATUS_OK);
        assertEquals(mRangingController.removeControlee(UwbAddress.getRandomizedShortAddress()),
                INVALID_API_CALL);
        verify(pfRangingSession, times(3)).reconfigure(any(PersistableBundle.class));
        verify(rangingSessionCallback)
                .onRangingSuspended(
                        UwbDevice.createForAddress(peerAddress.toBytes()),
                        REASON_STOP_RANGING_CALLED);
    }
}
