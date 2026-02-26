/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ranging.session;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.ranging.MotionState;
import android.ranging.RangingCapabilities;
import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.oob.TransportHandle;
import android.ranging.uwb.UwbRangingCapabilities;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.OobController.OobConnection;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Predicate;

@RunWith(JUnit4.class)
@SmallTest
public class OobInitiatorRangingSessionTest {
    @Mock
    private Context mMockContext;
    @Mock
    private AlarmManager mMockAlarmManager;
    @Mock
    private AttributionSource mMockAttributionSource;
    @Mock
    private SessionHandle mMockSessionHandle;
    @Mock
    private RangingInjector mMockInjector;
    @Mock
    private SessionConfig mMockSessionConfig;
    @Mock
    private CapabilitiesProvider mMockCapabilitiesProvider;
    @Mock
    private RangingCapabilities mMockRangingCapabilities;
    @Mock
    private UwbRangingCapabilities mMockUwbCapabilities;
    @Mock
    private RangingServiceManager.SessionListener mMockSessionListener;
    @Mock
    private OobController mMockOobController;
    @Mock
    private OobConnection mMockOobConnection;
    @Mock
    private RangingConfig mMockRangingConfig;

    private final ListeningExecutorService mExecutor = MoreExecutors.newDirectExecutorService();
    private final ScheduledExecutorService mScheduledExecutor =
            Executors.newSingleThreadScheduledExecutor();

    private OobInitiatorRangingSession mSession;
    private RangingDevice mDevice;
    private OobHandle mOobHandle;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mMockInjector.getOobController()).thenReturn(mMockOobController);
        when(mMockInjector.getCapabilitiesProvider()).thenReturn(mMockCapabilitiesProvider);
        when(mMockCapabilitiesProvider.getCapabilities()).thenReturn(mMockRangingCapabilities);
        when(mMockRangingCapabilities.getUwbCapabilities()).thenReturn(mMockUwbCapabilities);
        when(mMockInjector.getContext()).thenReturn(mMockContext);
        when(mMockInjector.getAlarmHandler()).thenReturn(mock(Handler.class));
        when(mMockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);

        mDevice = new RangingDevice.Builder().build();
        mOobHandle = new OobHandle(mMockSessionHandle, mDevice);

        when(mMockOobConnection.getHandle()).thenReturn(mOobHandle);
        when(mMockOobController.createConnection(any())).thenReturn(mMockOobConnection);
        when(mMockOobConnection.sendData(any())).thenReturn(
                FluentFuture.from(Futures.immediateVoidFuture()));

        mSession = new OobInitiatorRangingSession(
                mMockAttributionSource, mMockSessionHandle, mMockInjector, mMockSessionConfig,
                mMockRangingConfig, mMockSessionListener, mExecutor, mScheduledExecutor);
    }

    @Test
    public void onOobMessageReceived_reportsMotion() {
        // Prepare config
        OobInitiatorRangingConfig config = new OobInitiatorRangingConfig.Builder()
                .addDeviceHandle(new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(),
                        mock(TransportHandle.class)).build())
                .addDeviceHandles(new ArrayList<>())
                .build();

        when(mMockInjector.getTechnologyRanking())
                .thenReturn(ImmutableList.of(RangingTechnology.UWB));

        // Start session
        mSession.start(config);

        // Capture the listener
        ArgumentCaptor<Predicate<byte[]>> listenerCaptor = ArgumentCaptor.forClass(Predicate.class);
        verify(mMockOobConnection).registerAsyncMessageListener(listenerCaptor.capture());
        Predicate<byte[]> listener = listenerCaptor.getValue();

        // Construct MotionNotification packet (Version=3, Id=8, Motion=1(SLIGHT))
        byte[] motionNotificationBytes = new byte[]{0x03, 0x08, 0x01};

        // Simulate receiving message
        assertTrue(listener.test(motionNotificationBytes));

        // Use argThat to verify fields if MotionState doesn't implement equals well
        verify(mMockSessionListener).onMotionReceived(eq(mDevice), any(MotionState.class));
    }
}
