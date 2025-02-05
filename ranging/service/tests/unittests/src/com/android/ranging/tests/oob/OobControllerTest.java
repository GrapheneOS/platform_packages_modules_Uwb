/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.tests.oob;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.content.Context;
import android.os.RemoteException;
import android.ranging.oob.IOobSendDataListener;
import android.ranging.oob.OobHandle;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.oob.OobController.OobConnection;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("ConstantConditions")
@RunWith(JUnit4.class)
@SmallTest
public class OobControllerTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private @Mock RangingInjector mMockInjector;
    private @Mock AlarmManager mMockAlarmManager;
    private @Mock IOobSendDataListener mMockDataSender;
    private OobController mController;

    @Before
    public void setup() {
        Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(AlarmManager.class)).thenReturn(mMockAlarmManager);
        when(mMockInjector.getContext()).thenReturn(mockContext);
        mController = new OobController(mMockInjector);
        mController.registerDataSender(mMockDataSender);
    }

    @Test
    public void handleOobClosed_closesConnection() {
        OobHandle mockHandle = mock(OobHandle.class);
        OobConnection connection = mController.createConnection(mockHandle);
        mController.handleOobClosed(mockHandle);

        assertThat(connection.isClosed()).isTrue();
    }

    @Test
    public void handleOobDeviceDisconnected_disconnects() {
        OobHandle mockHandle = mock(OobHandle.class);
        OobConnection connection = mController.createConnection(mockHandle);
        mController.handleOobDeviceDisconnected(mockHandle);

        assertThat(connection.isConnected()).isFalse();
    }

    @Test
    public void shouldPreventSendsAndReceives_whenClosed() {
        OobConnection connection = mController.createConnection(mock(OobHandle.class));
        connection.close();

        assertThat(connection.isClosed()).isTrue();

        ListenableFuture<Void> pendingSend = connection.sendData(new byte[]{1, 2});
        ListenableFuture<byte[]> pendingReceive = connection.receiveData();

        assertThat(pendingSend.isDone()).isTrue();
        assertThat(pendingReceive.isDone()).isTrue();
        assertThrows(ExecutionException.class, pendingSend::get);
        assertThrows(ExecutionException.class, pendingReceive::get);
    }

    @Test
    public void shouldClose_whenDisconnectTimeoutExpires() {
        OobHandle mockHandle = mock(OobHandle.class);

        OobConnection connection = mController.createConnection(mockHandle);
        mController.handleOobDeviceDisconnected(mockHandle);


        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mMockAlarmManager)
                .setExact(anyInt(), anyLong(), anyString(), alarmCaptor.capture(), any());
        alarmCaptor.getValue().onAlarm();

        assertThat(connection.isClosed()).isTrue();
    }

    @Test
    public void sendData_notifiesDataSendListener()
            throws ExecutionException, InterruptedException, RemoteException {

        OobHandle mockHandle = mock(OobHandle.class);
        byte[] data = new byte[]{1, 2};

        try (OobConnection connection = mController.createConnection(mockHandle)) {

            FluentFuture<Void> pendingSend = connection.sendData(data);
            assertThat(pendingSend.isDone()).isTrue();
            pendingSend.get();
            verify(mMockDataSender).sendOobData(eq(mockHandle), aryEq(data));
        }
    }

    @Test
    public void sendData_notifiesSendDataListenerWithDataFromMultipleConnections()
            throws ExecutionException, InterruptedException, RemoteException {

        List<OobHandle> mockHandles = List.of(mock(OobHandle.class), mock(OobHandle.class));
        List<byte[]> data = List.of(new byte[]{1, 2}, new byte[]{3, 4});

        try (
                OobConnection connection0 = mController.createConnection(mockHandles.get(0));
                OobConnection connection1 = mController.createConnection(mockHandles.get(1))
        ) {
            List<FluentFuture<Void>> pendingSends = List.of(
                    connection0.sendData(data.get(0)),
                    connection1.sendData(data.get(1)));

            assertThat(pendingSends.get(0).isDone()).isTrue();
            pendingSends.get(0).get();
            verify(mMockDataSender).sendOobData(eq(mockHandles.get(0)), aryEq(data.get(0)));

            assertThat(pendingSends.get(1).isDone()).isTrue();
            pendingSends.get(1).get();
            verify(mMockDataSender).sendOobData(eq(mockHandles.get(1)), aryEq(data.get(1)));
        }
    }

    @Test
    public void sendData_queuesDataForSend_whenDisconnected()
            throws ExecutionException, InterruptedException, RemoteException {

        OobHandle mockHandle = mock(OobHandle.class);
        byte[] data = new byte[]{1, 2};

        try (OobConnection connection = mController.createConnection(mockHandle)) {
            mController.handleOobDeviceDisconnected(mockHandle);

            ListenableFuture<Void> pendingSend = connection.sendData(data);
            assertThat(pendingSend.isDone()).isFalse();

            mController.handleOobDeviceReconnected(mockHandle);

            assertThat(pendingSend.isDone()).isTrue();
            pendingSend.get();

            verify(mMockDataSender).sendOobData(eq(mockHandle), aryEq(data));
        }
    }

    @Test
    public void receiveData_getsLastDataReceived_whenAvailable()
            throws ExecutionException, InterruptedException {

        OobHandle mockHandle = mock(OobHandle.class);
        byte[] data = new byte[]{1, 2};

        try (OobConnection connection = mController.createConnection(mockHandle)) {
            mController.handleOobDataReceived(mockHandle, data);

            ListenableFuture<byte[]> pendingReceive = connection.receiveData();
            assertThat(pendingReceive.isDone()).isTrue();
            assertArrayEquals(data, pendingReceive.get());
        }
    }

    @Test
    public void receiveData_waitsForNewData_whenNoneAvailable()
            throws ExecutionException, InterruptedException {

        OobHandle mockHandle = mock(OobHandle.class);
        byte[] data = new byte[]{1, 2};

        try (OobConnection connection = mController.createConnection(mockHandle)) {
            ListenableFuture<byte[]> pendingReceive = connection.receiveData();
            assertThat(pendingReceive.isDone()).isFalse();

            mController.handleOobDataReceived(mockHandle, data);
            assertThat(pendingReceive.isDone()).isTrue();
            assertArrayEquals(data, pendingReceive.get());
        }
    }
}
