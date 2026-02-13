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

package com.android.server.uwb.timesync;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothDevice.BluetoothAddress;
import android.hardware.bluetooth.lmp_event.IBluetoothLmpEvent;
import android.hardware.bluetooth.lmp_event.IBluetoothLmpEventCallback;
import android.hardware.bluetooth.lmp_event.Timestamp;
import android.os.ServiceManager;
import android.uwb.UwbManager;
import android.uwb.timesync.ITimesyncCallbackListener;
import android.uwb.timesync.TimesyncEvent;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.uwb.DeviceConfigFacade;
import com.android.server.uwb.UwbContext;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.UwbServiceCore;
import com.android.server.uwb.data.UwbVendorUciResponse;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.multchip.UwbMultichipData;

import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.lang.reflect.Field;
import java.util.Map;

public class UwbTimesyncManagerTest {
    @Mock
    private UwbContext mContext;
    @Mock
    private NativeUwbManager mNativeUwbManager;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private IBluetoothLmpEvent mHal;
    @Mock
    private ITimesyncCallbackListener mCallbackListener;
    @Mock
    private DeviceConfigFacade mDeviceConfigFacade;

    private UwbTimesyncManager mUwbTimesyncManager;
    private MockitoSession mSession;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(ServiceManager.class)
                .startMocking();
        when(ServiceManager.waitForDeclaredService(any())).thenReturn(null);

        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);

        clearAddressCallbackMap();

        mUwbTimesyncManager = new UwbTimesyncManager(mContext, mNativeUwbManager, mUwbInjector);
        mUwbTimesyncManager.mockCreate(mHal);
    }

    private void clearAddressCallbackMap() throws Exception {
        Field field = UwbTimesyncManager.class.getDeclaredField("sAddressCallbackMap");
        field.setAccessible(true);
        Map<?, ?> map = (Map<?, ?>) field.get(null);
        map.clear();
    }

    @After
    public void tearDown() {
        mSession.finishMocking();
    }

    @Test
    public void testRegisterEventCallback() throws Exception {
        String address = "00:11:22:33:44:55";
        BluetoothAddress bluetoothAddress = new BluetoothAddress(address, 0);

        mUwbTimesyncManager.registerEventCallback(mCallbackListener, bluetoothAddress);

        verify(mHal).registerForLmpEvents(any(IBluetoothLmpEventCallback.class), eq((byte) 0),
                any(byte[].class), any(byte[].class));
    }

    @Test
    public void testUnregisterEventCallback() throws Exception {
        String addressStr = "00:11:22:33:44:55";
        BluetoothAddress bluetoothAddress = new BluetoothAddress(addressStr, 0);

        mUwbTimesyncManager.registerEventCallback(mCallbackListener, bluetoothAddress);
        mUwbTimesyncManager.unregisterEventCallback(mCallbackListener, bluetoothAddress);

        verify(mHal).unregisterLmpEvents(eq((byte) 0), any(byte[].class));
    }

    @Test
    public void testOnEventGenerated_ComboChip() throws Exception {
        String addressStr = "00:11:22:33:44:55";
        BluetoothAddress bluetoothAddress = new BluetoothAddress(addressStr, 0);
        byte[] addressBytes = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};

        mUwbTimesyncManager.registerEventCallback(mCallbackListener, bluetoothAddress);

        ArgumentCaptor<IBluetoothLmpEventCallback> callbackCaptor =
                ArgumentCaptor.forClass(IBluetoothLmpEventCallback.class);
        verify(mHal).registerForLmpEvents(callbackCaptor.capture(), anyByte(), any(), any());

        IBluetoothLmpEventCallback halCallback = callbackCaptor.getValue();

        Timestamp timestamp = new Timestamp();
        timestamp.systemTimeUs = 1000;
        timestamp.bluetoothTimeUs = 1000; // Triggers combo chip logic

        when(mDeviceConfigFacade.getTimesyncUncertainty()).thenReturn(100);
        when(mDeviceConfigFacade.getTimesyncClockSkewPpm()).thenReturn(50);
        when(mDeviceConfigFacade.getTimesyncUncertainty()).thenReturn(255);

        halCallback.onEventGenerated(timestamp, (byte) 0, addressBytes, (byte) 1, (byte) 0,
                (char) 123);

        ArgumentCaptor<TimesyncEvent> eventCaptor = ArgumentCaptor.forClass(TimesyncEvent.class);
        verify(mCallbackListener).onTimesyncEvent(eventCaptor.capture());

        TimesyncEvent event = eventCaptor.getValue();
        assertThat(event.getMacAddress()).isEqualTo(addressStr);
        assertThat(event.getUwbTimestampUs()).isEqualTo(1000);
        assertThat(event.getDeviceTimeUncertaintyUs()).isEqualTo(3938502376L);
        assertThat(event.getMaxClockSkewPpm()).isEqualTo(50);
        assertThat(event.getEventCounter()).isEqualTo(123);
    }

    @Test
    public void testOnEventGenerated_AndroidSpecific() throws Exception {
        String addressStr = "00:11:22:33:44:55";
        BluetoothAddress bluetoothAddress = new BluetoothAddress(addressStr, 0);
        byte[] addressBytes = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};

        mUwbTimesyncManager.registerEventCallback(mCallbackListener, bluetoothAddress);

        ArgumentCaptor<IBluetoothLmpEventCallback> callbackCaptor =
                ArgumentCaptor.forClass(IBluetoothLmpEventCallback.class);
        verify(mHal).registerForLmpEvents(callbackCaptor.capture(), anyByte(), any(), any());

        IBluetoothLmpEventCallback halCallback = callbackCaptor.getValue();

        Timestamp timestamp = new Timestamp();
        timestamp.systemTimeUs = 1000;
        timestamp.bluetoothTimeUs = 2000; // Not equal

        when(mDeviceConfigFacade.isAndroidSpecificTimesyncSupported()).thenReturn(true);

        // Mock UCI response
        // status (1), timestamp (8), uncertainty (4) = 13 bytes
        byte[] payload = new byte[13];
        payload[0] = 0x00; // status (UCI_STATUS_OK)
        // timestamp = 8 (little endian)
        payload[1] = 0x08;
        // uncertainty = 100 (little endian)
        payload[9] = 0x64;

        UwbVendorUciResponse uciResponse = new UwbVendorUciResponse(
                (byte) UwbManager.SEND_VENDOR_UCI_SUCCESS,
                0, 0, // gid, oid
                payload);

        when(mUwbInjector.runTaskOnSingleThreadExecutorUci(any(), anyInt())).thenReturn(
                uciResponse);
        when(mDeviceConfigFacade.getTimesyncUncertainty()).thenReturn(10);
        when(mDeviceConfigFacade.getTimesyncClockSkewPpm()).thenReturn(50);

        halCallback.onEventGenerated(timestamp, (byte) 0, addressBytes, (byte) 1, (byte) 0,
                (char) 123);

        ArgumentCaptor<TimesyncEvent> eventCaptor = ArgumentCaptor.forClass(TimesyncEvent.class);
        verify(mCallbackListener).onTimesyncEvent(eventCaptor.capture());

        TimesyncEvent event = eventCaptor.getValue();
        // uwbsTimeOffsetUs = result.mTimestampUs - timestamp.systemTimeUs = 8 - 1000 = -992
        // uwbTimestamp = timestamp.systemTimeUs + uwbsTimeOffsetUs = 1000 - 992 = 8
        assertThat(event.getUwbTimestampUs()).isEqualTo(8);
    }

    @Test
    public void testOnEventGenerated_Default() throws Exception {
        String addressStr = "00:11:22:33:44:55";
        BluetoothAddress bluetoothAddress = new BluetoothAddress(addressStr, 0);
        byte[] addressBytes = new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55};

        mUwbTimesyncManager.registerEventCallback(mCallbackListener, bluetoothAddress);

        ArgumentCaptor<IBluetoothLmpEventCallback> callbackCaptor =
                ArgumentCaptor.forClass(IBluetoothLmpEventCallback.class);
        verify(mHal).registerForLmpEvents(callbackCaptor.capture(), anyByte(), any(), any());

        IBluetoothLmpEventCallback halCallback = callbackCaptor.getValue();

        Timestamp timestamp = new Timestamp();
        timestamp.systemTimeUs = 1000;
        timestamp.bluetoothTimeUs = 2000;

        when(mDeviceConfigFacade.isAndroidSpecificTimesyncSupported()).thenReturn(false);

        // Mocking Fira specification params to be less than 2
        UwbServiceCore uwbServiceCore = mock(UwbServiceCore.class);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(uwbServiceCore);
        UwbMultichipData multichipData = mock(UwbMultichipData.class);
        when(mUwbInjector.getMultichipData()).thenReturn(multichipData);
        when(multichipData.getDefaultChipId()).thenReturn("defaultChipId");
        GenericSpecificationParams genericParams = mock(GenericSpecificationParams.class);
        when(uwbServiceCore.getCachedSpecificationParams(anyString())).thenReturn(genericParams);
        FiraSpecificationParams firaParams = mock(FiraSpecificationParams.class);
        when(genericParams.getFiraSpecificationParams()).thenReturn(firaParams);
        when(firaParams.getUciVersionSupported()).thenReturn(1);

        when(mDeviceConfigFacade.getTimesyncDeviceOffset()).thenReturn(500);
        when(mDeviceConfigFacade.getTimesyncUncertainty()).thenReturn(10);
        when(mDeviceConfigFacade.getTimesyncClockSkewPpm()).thenReturn(50);
        when(mDeviceConfigFacade.getTimesyncBleTimeUncertainty()).thenReturn(20);

        halCallback.onEventGenerated(timestamp, (byte) 0, addressBytes, (byte) 1, (byte) 0,
                (char) 123);

        ArgumentCaptor<TimesyncEvent> eventCaptor = ArgumentCaptor.forClass(TimesyncEvent.class);
        verify(mCallbackListener).onTimesyncEvent(eventCaptor.capture());

        TimesyncEvent event = eventCaptor.getValue();
        // uwbTimestamp = timestamp.systemTimeUs + getTimesyncDeviceOffset() = 1000 + 500 = 1500
        assertThat(event.getUwbTimestampUs()).isEqualTo(1500);
    }

    @Test
    public void testOnRegistered() throws Exception {
        String addressStr = "00:11:22:33:44:55";
        BluetoothAddress bluetoothAddress = new BluetoothAddress(addressStr, 0);

        mUwbTimesyncManager.registerEventCallback(mCallbackListener, bluetoothAddress);

        ArgumentCaptor<IBluetoothLmpEventCallback> callbackCaptor =
                ArgumentCaptor.forClass(IBluetoothLmpEventCallback.class);
        verify(mHal).registerForLmpEvents(callbackCaptor.capture(), anyByte(), any(), any());

        IBluetoothLmpEventCallback halCallback = callbackCaptor.getValue();
        halCallback.onRegistered(true);
        verify(mCallbackListener).onRegistered();

        halCallback.onRegistered(false);
        verify(mCallbackListener).onRegisterFailed();
    }
}
