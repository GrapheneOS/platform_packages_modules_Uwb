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

import static android.Manifest.permission.UWB_PRIVILEGED;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static com.android.server.uwb.UwbSettingsStore.SETTINGS_TOGGLE_STATE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbRangingCallbacks2;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.SessionHandle;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.multichip.ChipInfoParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Tests for {@link UwbServiceImpl}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbServiceImplTest {
    private static final int UID = 343453;
    private static final int UID_2 = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final String DEFAULT_CHIP_ID = "defaultChipId";
    private static final ChipInfoParams DEFAULT_CHIP_INFO_PARAMS =
            ChipInfoParams.createBuilder().setChipId(DEFAULT_CHIP_ID).build();
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final AttributionSource ATTRIBUTION_SOURCE_2 =
            new AttributionSource.Builder(UID_2).setPackageName(PACKAGE_NAME).build();

    @Mock private IUwbAdapter mVendorService;
    @Mock private IBinder mVendorServiceBinder;
    @Mock private Context mContext;
    @Mock private UwbInjector mUwbInjector;
    @Mock private UwbSettingsStore mUwbSettingsStore;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Captor private ArgumentCaptor<IUwbRangingCallbacks> mRangingCbCaptor;
    @Captor private ArgumentCaptor<IUwbRangingCallbacks> mRangingCbCaptor2;
    @Captor private ArgumentCaptor<IBinder.DeathRecipient> mClientDeathCaptor;
    @Captor private ArgumentCaptor<IBinder.DeathRecipient> mVendorServiceDeathCaptor;
    @Captor private ArgumentCaptor<BroadcastReceiver> mApmModeBroadcastReceiver;

    private UwbServiceImpl mUwbServiceImpl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbInjector.getVendorService()).thenReturn(mVendorService);
        when(mUwbInjector.isUciStackEnabled()).thenReturn(false);
        when(mUwbInjector.checkUwbRangingPermissionForDataDelivery(any(), any())).thenReturn(true);
        when(mVendorService.asBinder()).thenReturn(mVendorServiceBinder);
        when(mUwbInjector.getUwbSettingsStore()).thenReturn(mUwbSettingsStore);
        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(true);
        when(mNativeUwbManager.getChipInfos()).thenReturn(List.of(DEFAULT_CHIP_INFO_PARAMS));
        when(mNativeUwbManager.getDefaultChipId()).thenReturn(DEFAULT_CHIP_ID);
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        when(mUwbInjector.getNativeUwbManager()).thenReturn(mNativeUwbManager);

        mUwbServiceImpl = new UwbServiceImpl(mContext, mUwbInjector);

        verify(mContext).registerReceiver(
                mApmModeBroadcastReceiver.capture(),
                argThat(i -> i.getAction(0).equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)));
    }

    @Test
    public void testApiCallThrowsIllegalStateExceptionIfVendorServiceNotFound() throws Exception {
        when(mUwbInjector.getVendorService()).thenReturn(null);

        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        try {
            mUwbServiceImpl.registerAdapterStateCallbacks(cb);
            fail();
        } catch (IllegalStateException e) { /* pass */ }
    }

    @Test
    public void testRegisterAdapterStateCallbacks() throws Exception {
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        mUwbServiceImpl.registerAdapterStateCallbacks(cb);

        verify(mVendorService).registerAdapterStateCallbacks(cb);
    }

    @Test
    public void testUnregisterAdapterStateCallbacks() throws Exception {
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        mUwbServiceImpl.unregisterAdapterStateCallbacks(cb);

        verify(mVendorService).unregisterAdapterStateCallbacks(cb);
    }

    @Test
    public void testGetTimestampResolutionNanos() throws Exception {
        final long timestamp = 34L;
        when(mVendorService.getTimestampResolutionNanos()).thenReturn(timestamp);
        assertThat(mUwbServiceImpl.getTimestampResolutionNanos(/* chipId= */ null))
                .isEqualTo(timestamp);

        verify(mVendorService).getTimestampResolutionNanos();
    }

    @Test
    public void testGetTimestampResolutionNanos_validChipId() throws Exception {
        final long timestamp = 34L;
        when(mVendorService.getTimestampResolutionNanos()).thenReturn(timestamp);
        assertThat(mUwbServiceImpl.getTimestampResolutionNanos(DEFAULT_CHIP_ID))
                .isEqualTo(timestamp);

        verify(mVendorService).getTimestampResolutionNanos();
    }

    @Test
    public void testGetTimestampResolutionNanos_invalidChipId() {
        assertThrows(IllegalArgumentException.class,
                () -> mUwbServiceImpl.getTimestampResolutionNanos("invalidChipId"));
    }

    @Test
    public void testGetSpecificationInfo() throws Exception {
        final PersistableBundle specification = new PersistableBundle();
        when(mVendorService.getSpecificationInfo()).thenReturn(specification);
        assertThat(mUwbServiceImpl.getSpecificationInfo(/* chipId= */ null))
                .isEqualTo(specification);

        verify(mVendorService).getSpecificationInfo();
    }

    @Test
    public void testGetSpecificationInfo_validChipId() throws Exception {
        final PersistableBundle specification = new PersistableBundle();
        when(mVendorService.getSpecificationInfo()).thenReturn(specification);
        assertThat(mUwbServiceImpl.getSpecificationInfo(DEFAULT_CHIP_ID))
                .isEqualTo(specification);

        verify(mVendorService).getSpecificationInfo();
    }

    @Test
    public void testGetSpecificationInfo_invalidChipId() {
        assertThrows(IllegalArgumentException.class,
                () -> mUwbServiceImpl.getSpecificationInfo("invalidChipId"));
    }

    @Test
    public void testOpenRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mVendorService).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();
    }

    @Test
    public void testStartRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.startRanging(sessionHandle, parameters);

        verify(mVendorService).startRanging(sessionHandle, parameters);
    }


    @Test
    public void testReconfigureRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final PersistableBundle parameters = new PersistableBundle();

        mUwbServiceImpl.reconfigureRanging(sessionHandle, parameters);

        verify(mVendorService).reconfigureRanging(sessionHandle, parameters);
    }

    @Test
    public void testStopRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);

        mUwbServiceImpl.stopRanging(sessionHandle);

        verify(mVendorService).stopRanging(sessionHandle);
    }

    @Test
    public void testCloseRanging() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);

        mUwbServiceImpl.closeRanging(sessionHandle);

        verify(mVendorService).closeRanging(sessionHandle);
    }

    @Test
    public void testRangingCallbacks() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mVendorService).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();

        // Invoke vendor service callbacks and ensure that the corresponding app callback is
        // invoked.
        mRangingCbCaptor.getValue().onRangingOpened(sessionHandle);
        verify(cb).onRangingOpened(sessionHandle);

        mRangingCbCaptor.getValue().onRangingOpenFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
        verify(cb).onRangingOpenFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);

        mRangingCbCaptor.getValue().onRangingStarted(sessionHandle, parameters);
        verify(cb).onRangingStarted(sessionHandle, parameters);

        mRangingCbCaptor.getValue().onRangingStartFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
        verify(cb).onRangingStartFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);

        mRangingCbCaptor.getValue().onRangingReconfigured(sessionHandle, parameters);
        verify(cb).onRangingReconfigured(sessionHandle, parameters);

        mRangingCbCaptor.getValue().onRangingReconfigureFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
        verify(cb).onRangingReconfigureFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);

        mRangingCbCaptor.getValue().onRangingStopped(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
        verify(cb).onRangingStopped(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);

        mRangingCbCaptor.getValue().onRangingStopFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
        verify(cb).onRangingStopFailed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);

        final RangingReport rangingReport = new RangingReport.Builder().build();
        mRangingCbCaptor.getValue().onRangingResult(sessionHandle, rangingReport);
        verify(cb).onRangingResult(sessionHandle, rangingReport);

        mRangingCbCaptor.getValue().onRangingClosed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
        verify(cb).onRangingClosed(
                sessionHandle, RangingSession.Callback.REASON_GENERIC_ERROR, parameters);
    }

    @Test
    public void testRangingCallbacksFromDifferentUidWithSameSessionHandle() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb1 = mock(IUwbRangingCallbacks2.class);
        final IUwbRangingCallbacks2 cb2 = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder1 = mock(IBinder.class);
        final IBinder cbBinder2 = mock(IBinder.class);
        when(cb1.asBinder()).thenReturn(cbBinder1);
        when(cb2.asBinder()).thenReturn(cbBinder2);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb1, parameters, /* chipId= */ null);

        verify(mVendorService).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();
        verify(cb1).asBinder();
        verify(cbBinder1).linkToDeath(any(), anyInt());

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE_2, sessionHandle, cb2, parameters, /* chipId= */ null);

        verify(mVendorService, times(2)).openRanging(
                eq(ATTRIBUTION_SOURCE_2), eq(sessionHandle), mRangingCbCaptor2.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor2.getValue()).isNotNull();
        verify(cb2).asBinder();
        verify(cbBinder2).linkToDeath(any(), anyInt());

        // Invoke vendor service callbacks and ensure that the corresponding app callback is
        // invoked.
        mRangingCbCaptor.getValue().onRangingOpened(sessionHandle);
        verify(cb1).onRangingOpened(sessionHandle);
        verifyZeroInteractions(cb2);

        mRangingCbCaptor2.getValue().onRangingOpened(sessionHandle);
        verify(cb2).onRangingOpened(sessionHandle);
        verifyNoMoreInteractions(cb1);
    }

    @Test
    public void testHandleClientDeath() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mVendorService).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();

        verify(cbBinder).linkToDeath(mClientDeathCaptor.capture(), anyInt());
        assertThat(mClientDeathCaptor.getValue()).isNotNull();

        clearInvocations(cb);

        // Invoke cb, ensure it reaches the client.
        mRangingCbCaptor.getValue().onRangingOpened(sessionHandle);
        verify(cb).onRangingOpened(sessionHandle);

        // Trigger client death and ensure the session is stopped.
        mClientDeathCaptor.getValue().binderDied();
        verify(mVendorService).stopRanging(sessionHandle);
        verify(mVendorService).closeRanging(sessionHandle);

        // Invoke cb, it should be ignored.
        mRangingCbCaptor.getValue().onRangingStarted(sessionHandle, parameters);
        verify(cb, never()).onRangingStarted(any(), any());
    }

    @Test
    public void testHandleVendorServiceDeath() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mVendorServiceBinder).linkToDeath(mVendorServiceDeathCaptor.capture(), anyInt());
        assertThat(mVendorServiceDeathCaptor.getValue()).isNotNull();

        verify(mVendorService).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();

        clearInvocations(cb);

        // Invoke cb, ensure it reaches the client.
        mRangingCbCaptor.getValue().onRangingOpened(sessionHandle);
        verify(cb).onRangingOpened(sessionHandle);

        // Trigger vendor service death and ensure that the client is informed of session end.
        mVendorServiceDeathCaptor.getValue().binderDied();
        verify(cb).onRangingClosed(
                eq(sessionHandle), eq(RangingSession.Callback.REASON_UNKNOWN),
                argThat((p) -> p.isEmpty()));
    }

    @Test
    public void testThrowSecurityExceptionWhenCalledWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        final IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        try {
            mUwbServiceImpl.registerAdapterStateCallbacks(cb);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testThrowSecurityExceptionWhenSetUwbEnabledCalledWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.setEnabled(true);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testThrowSecurityExceptionWhenOpenRangingCalledWithoutUwbRangingPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mUwbInjector).enforceUwbRangingPermissionForPreflight(
                any());

        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);
        try {
            mUwbServiceImpl.openRanging(
                    ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testOnRangingResultCallbackNotSentWithoutUwbRangingPermission() throws Exception {
        final SessionHandle sessionHandle = new SessionHandle(5);
        final IUwbRangingCallbacks2 cb = mock(IUwbRangingCallbacks2.class);
        final PersistableBundle parameters = new PersistableBundle();
        final IBinder cbBinder = mock(IBinder.class);
        when(cb.asBinder()).thenReturn(cbBinder);

        mUwbServiceImpl.openRanging(
                ATTRIBUTION_SOURCE, sessionHandle, cb, parameters, /* chipId= */ null);

        verify(mVendorService).openRanging(
                eq(ATTRIBUTION_SOURCE), eq(sessionHandle), mRangingCbCaptor.capture(),
                eq(parameters));
        assertThat(mRangingCbCaptor.getValue()).isNotNull();

        when(mUwbInjector.checkUwbRangingPermissionForDataDelivery(any(), any())).thenReturn(false);

        // Ensure the ranging cb is not delivered to the client.
        final RangingReport rangingReport = new RangingReport.Builder().build();
        mRangingCbCaptor.getValue().onRangingResult(sessionHandle, rangingReport);
        verify(cb, never()).onRangingResult(sessionHandle, rangingReport);
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefs() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mVendorService, times(2)).setEnabled(true);

        when(mUwbSettingsStore.get(SETTINGS_TOGGLE_STATE)).thenReturn(false);
        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mVendorService).setEnabled(false);
    }

    @Test
    public void testToggleStatePersistenceToSharedPrefsWhenApmModeOn() throws Exception {
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);

        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mVendorService, times(2)).setEnabled(false);

        mUwbServiceImpl.setEnabled(false);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, false);
        verify(mVendorService, times(3)).setEnabled(false);
    }

    @Test
    public void testToggleStateReadFromSharedPrefsOnInitialization() throws Exception {
        when(mVendorService.getAdapterState()).thenReturn(STATE_ENABLED_ACTIVE);
        assertThat(mUwbServiceImpl.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);
        // First call to vendor service should be preceded by sending the persisted UWB toggle
        // state to the vendor stack.
        verify(mVendorService).setEnabled(true);
        verify(mVendorService).getAdapterState();

        when(mVendorService.getAdapterState()).thenReturn(STATE_ENABLED_INACTIVE);
        assertThat(mUwbServiceImpl.getAdapterState()).isEqualTo(STATE_ENABLED_INACTIVE);
        verify(mVendorService, times(2)).getAdapterState();

        // No new toggle state changes send to vendor stack.
        verify(mVendorService, times(1)).setEnabled(anyBoolean());
    }

    @Test
    public void testApmModeToggle() throws Exception {
        mUwbServiceImpl.setEnabled(true);
        verify(mUwbSettingsStore).put(SETTINGS_TOGGLE_STATE, true);
        verify(mVendorService, times(2)).setEnabled(true);

        // Toggle on
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(1);
        mApmModeBroadcastReceiver.getValue().onReceive(
                mContext, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        verify(mVendorService).setEnabled(false);

        // Toggle off
        when(mUwbInjector.getSettingsInt(Settings.Global.AIRPLANE_MODE_ON, 0)).thenReturn(0);
        mApmModeBroadcastReceiver.getValue().onReceive(
                mContext, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
        verify(mVendorService, times(3)).setEnabled(true);
    }

    @Test
    public void testGetDefaultChipId() {
        assertEquals(DEFAULT_CHIP_ID, mUwbServiceImpl.getDefaultChipId());
    }

    @Test
    public void testThrowSecurityExceptionWhenGetDefaultChipIdWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getDefaultChipId();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testGetChipIds() {
        List<String> chipIds = mUwbServiceImpl.getChipIds();
        assertThat(chipIds).containsExactly(DEFAULT_CHIP_ID);
    }

    @Test
    public void testThrowSecurityExceptionWhenGetChipIdsWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getChipIds();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }

    @Test
    public void testGetChipInfos() {
        List<PersistableBundle> chipInfos = mUwbServiceImpl.getChipInfos();
        assertThat(chipInfos).hasSize(1);
        ChipInfoParams chipInfoParams = ChipInfoParams.fromBundle(chipInfos.get(0));
        assertThat(chipInfoParams.getChipId()).isEqualTo(DEFAULT_CHIP_ID);
    }

    @Test
    public void testThrowSecurityExceptionWhenGetChipInfosWithoutUwbPrivilegedPermission()
            throws Exception {
        doThrow(new SecurityException()).when(mContext).enforceCallingOrSelfPermission(
                eq(UWB_PRIVILEGED), any());
        try {
            mUwbServiceImpl.getChipInfos();
            fail();
        } catch (SecurityException e) { /* pass */ }
    }
}
