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

package com.android.server.uwb;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import static com.android.server.uwb.UwbSessionManager.SESSION_OPEN_RANGING;
import static com.android.server.uwb.UwbTestUtils.DATA_PAYLOAD;
import static com.android.server.uwb.UwbTestUtils.PEER_BAD_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_MAC_ADDRESS_2;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_SHORT_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_EXTENDED_UWB_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_SHORT_MAC_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PEER_SHORT_UWB_ADDRESS;
import static com.android.server.uwb.UwbTestUtils.PERSISTABLE_BUNDLE;
import static com.android.server.uwb.UwbTestUtils.RANGING_MEASUREMENT_TYPE_UNDEFINED;
import static com.android.server.uwb.UwbTestUtils.TEST_SESSION_ID;
import static com.android.server.uwb.UwbTestUtils.TEST_SESSION_ID_2;
import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_EXTENDED;
import static com.android.server.uwb.data.UwbUciConstants.MAC_ADDRESSING_MODE_SHORT;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_DEVICE_ROLE_ADVERTISER;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_DEVICE_ROLE_OBSERVER;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA;
import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_OWR_AOA_MEASUREMENT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_NAME;
import static com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_DISABLE;
import static com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_ENABLE;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityManager.OnUidImportanceListener;
import android.app.AlarmManager;
import android.content.AttributionSource;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.SessionHandle;
import android.uwb.StateChangeReason;
import android.uwb.UwbAddress;

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.UwbSessionManager.WaitObj;
import com.android.server.uwb.advertisement.UwbAdvertiseManager;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;

public class UwbSessionManagerTest {
    private static final String TEST_CHIP_ID = "testChipId";
    private static final int MAX_SESSION_NUM = 8;
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final int UID_2 = 67;
    private static final String PACKAGE_NAME_2 = "com.android.uwb.2";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final UwbAddress UWB_DEST_ADDRESS =
            UwbAddress.fromBytes(new byte[] {(byte) 0x03, (byte) 0x04 });
    private static final UwbAddress UWB_DEST_ADDRESS_2 =
            UwbAddress.fromBytes(new byte[] {(byte) 0x05, (byte) 0x06 });
    private static final UwbAddress UWB_DEST_ADDRESS_3 =
            UwbAddress.fromBytes(new byte[] {(byte) 0x07, (byte) 0x08 });
    private static final int TEST_RANGING_INTERVAL_MS = 200;
    private static final int DATA_SEQUENCE_NUM = 1;
    private static final int SOURCE_END_POINT = 100;
    private static final int DEST_END_POINT = 200;
    private static final int HANDLE_ID = 12;
    private static final int PID = Process.myPid();

    @Mock
    private UwbConfigurationManager mUwbConfigurationManager;
    @Mock
    private NativeUwbManager mNativeUwbManager;
    @Mock
    private UwbMetrics mUwbMetrics;
    @Mock
    private UwbAdvertiseManager mUwbAdvertiseManager;
    @Mock
    private UwbSessionNotificationManager mUwbSessionNotificationManager;
    @Mock
    private UwbInjector mUwbInjector;
    @Mock
    private ExecutorService mExecutorService;
    @Mock
    private AlarmManager mAlarmManager;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private UwbServiceCore mUwbServiceCore;
    private TestLooper mTestLooper = new TestLooper();
    private UwbSessionManager mUwbSessionManager;
    @Captor
    private ArgumentCaptor<OnUidImportanceListener> mOnUidImportanceListenerArgumentCaptor;
    private GenericSpecificationParams.Builder mSpecificationParamsBuilder;

    @Before
    public void setup() throws ExecutionException, InterruptedException, TimeoutException {
        MockitoAnnotations.initMocks(this);
        when(mNativeUwbManager.getMaxSessionNumber()).thenReturn(MAX_SESSION_NUM);
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(true);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(true);
        when(mUwbInjector.getUwbServiceCore()).thenReturn(mUwbServiceCore);
        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(0);
            t.run();
            return t.get();
        }).when(mUwbInjector).runTaskOnSingleThreadExecutor(any(FutureTask.class), anyInt());
        mSpecificationParamsBuilder = new GenericSpecificationParams.Builder()
                .setCccSpecificationParams(mock(CccSpecificationParams.class))
                .setFiraSpecificationParams(
                        new FiraSpecificationParams.Builder()
                                .setSupportedChannels(List.of(9))
                                .setRangeDataNtfConfigCapabilities(
                                        EnumSet.of(
                                                HAS_RANGE_DATA_NTF_CONFIG_DISABLE,
                                                HAS_RANGE_DATA_NTF_CONFIG_ENABLE))
                                .build());
        when(mUwbServiceCore.getCachedSpecificationParams(any())).thenReturn(
                mSpecificationParamsBuilder.build());

        // TODO: Don't use spy.
        mUwbSessionManager = spy(new UwbSessionManager(
                mUwbConfigurationManager,
                mNativeUwbManager,
                mUwbMetrics,
                mUwbAdvertiseManager,
                mUwbSessionNotificationManager,
                mUwbInjector,
                mAlarmManager,
                mActivityManager,
                mTestLooper.getLooper()));

        verify(mActivityManager).addOnUidImportanceListener(
                mOnUidImportanceListenerArgumentCaptor.capture(), anyInt());
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() { }

    @Test
    public void onDataReceived_extendedMacAddressFormat() {
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);
        assertNotNull(mUwbSessionManager.getReceivedDataInfo(PEER_EXTENDED_MAC_ADDRESS));
    }

    @Test
    public void onDataReceived_unsupportedMacAddressLength() {
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_BAD_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);
        assertNull(mUwbSessionManager.getReceivedDataInfo(PEER_BAD_MAC_ADDRESS));
    }

    @Test
    public void onDataReceived_shortMacAddressFormat() {
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_SHORT_MAC_ADDRESS, SOURCE_END_POINT,
                DEST_END_POINT, DATA_PAYLOAD);
        assertNotNull(mUwbSessionManager.getReceivedDataInfo(PEER_EXTENDED_SHORT_MAC_ADDRESS));
    }

    @Test
    public void onRangeDataNotificationReceivedWithValidUwbSession_twoWay() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
    }

    @Test
    public void onRangeDataNotificationReceivedWithInvalidSession_twoWay() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager, never())
                .onRangingResult(any(), eq(uwbRangingData));
        verify(mUwbMetrics, never()).logRangingResult(anyInt(), eq(uwbRangingData));
    }

    // Test scenario for receiving Application payload data followed by a RANGE_DATA_NTF with an
    // OWR Aoa Measurement (such that the ExtendedMacAddress format is used for the remote device).
    @Test
    public void onRangeDataNotificationReceived_owrAoa_success_extendedMacAddress() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(true);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbSessionNotificationManager)
                .onDataReceived(eq(mockUwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
    }

    // Test scenario for receiving Application payload data followed by a RANGE_DATA_NTF with an
    // OWR Aoa Measurement (such that the ShortMacAddress format is used for the remote device).
    @Test
    public void onRangeDataNotificationReceived_owrAoa_success_shortMacAddress() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data. This should always have
        // the MacAddress (in 8 Bytes), even for a Short MacAddress (MSB are zeroed out).
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_SHORT_MAC_ADDRESS,
                SOURCE_END_POINT, DEST_END_POINT, DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_SHORT,
                UwbUciConstants.STATUS_CODE_OK);
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_SHORT_MAC_ADDRESS)).thenReturn(true);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbSessionNotificationManager)
                .onDataReceived(eq(mockUwbSession), eq(PEER_SHORT_UWB_ADDRESS),
                        isA(PersistableBundle.class), eq(DATA_PAYLOAD));
        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_SHORT_MAC_ADDRESS);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_missingUwbSession() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);

        // Setup the test scenario such that the UwbSession (from the RANGE_DATA_NTF) doesn't exist.
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verifyZeroInteractions(mUwbAdvertiseManager, mUwbSessionNotificationManager, mUwbMetrics);
    }

    @Test
    public void onRangeDataNotificationReceived_incorrectRangingMeasureType() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_UNDEFINED, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_incorrectRangingRoundUsage() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF (with an
        // incorrect RangingRoundUsage value).
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_DS_TWR_DEFERRED_MODE));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_incorrectDeviceRole() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_ADVERTISER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_receivedDataNotCalled() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // Skip call to mUwbSessionManager.onDataReceived(). This means there is no application
        // payload data, and so mUwbSessionNotificationManager.onDataReceived() shouldn't be called.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));

        verify(mUwbAdvertiseManager, never()).isPointedTarget(isA(byte[].class));
        verifyZeroInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_receivedDataDifferentMacAddress() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // onDataReceived() called for a different MacAddress, which should be equivalent to it
        // not being called.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS_2, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));

        verify(mUwbAdvertiseManager, never()).isPointedTarget(isA(byte[].class));
        verifyZeroInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_receivedDataDifferentUwbSession() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // onDataReceived() called for a different UwbSessionID, which should be equivalent to it
        // not being called.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID_2, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF.
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        when(mockUwbSession2.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession2)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID_2));

        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(mockUwbSession.getParams()).thenReturn(firaParams);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));

        verify(mUwbAdvertiseManager, never()).isPointedTarget(isA(byte[].class));
        verifyZeroInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onRangeDataNotificationReceived_owrAoa_notPointedTarget() {
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Setup isPointedTarget() to return false.
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(false);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(uwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(uwbRangingData));
        verify(mUwbAdvertiseManager, never()).removeAdvertiseTarget(isA(byte[].class));
        verifyZeroInteractions(mUwbSessionNotificationManager);
    }

    @Test
    public void onMulticastListUpdateNotificationReceivedWithValidSession() {
        UwbMulticastListUpdateStatus mockUwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(anyInt());

        mUwbSessionManager.onMulticastListUpdateNotificationReceived(
                mockUwbMulticastListUpdateStatus);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession)
                .setMulticastListUpdateStatus(eq(mockUwbMulticastListUpdateStatus));
    }

    @Test
    public void onSessionStatusNotificationReceived_max_retry() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
        verify(mUwbSessionNotificationManager).onRangingStoppedWithUciReasonCode(
                eq(mockUwbSession),
                eq(UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED));
    }

    @Test
    public void onSessionStatusNotificationReceived_session_mgmt_cmds() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                TEST_SESSION_ID,
                UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);

        verify(mockUwbSession, times(2)).getWaitObj();
        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
        verify(mUwbSessionNotificationManager, never()).onRangingStoppedWithUciReasonCode(
                any(), anyInt());
    }

    @Test
    public void initSession_ExistedSession() throws RemoteException {
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        doReturn(true).when(mUwbSessionManager).isExistedSession(anyInt());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, "any", mock(Params.class), mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(mockRangingCallbacks).onRangingOpenFailed(
                any(), eq(RangingChangeReason.BAD_PARAMETERS), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_maxSession() throws RemoteException {
        doReturn(MAX_SESSION_NUM).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mock(SessionHandle.class),
                TEST_SESSION_ID, "any", mock(Params.class), mockRangingCallbacks,
                TEST_CHIP_ID);

        verify(mockRangingCallbacks).onRangingOpenFailed(any(), anyInt(), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_UwbSession_RemoteException() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any(), anyString());
        doThrow(new RemoteException()).when(mockBinder).linkToDeath(any(), anyInt());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks, TEST_CHIP_ID);

        verify(uwbSession).binderDied();
        verify(mockRangingCallbacks).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, atLeast(1)).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_success() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params mockParams = mock(FiraParams.class);
        IBinder mockBinder = mock(IBinder.class);

        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any(), anyString());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks, TEST_CHIP_ID);

        verify(uwbSession, never()).binderDied();
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, never()).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(uwbSession);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(1); // SESSION_OPEN_RANGING
    }

    @Test
    public void initSession_controleeList() throws RemoteException {
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        FiraOpenSessionParams mockParams = mock(FiraOpenSessionParams.class);
        IBinder mockBinder = mock(IBinder.class);

        when(mockParams.getDestAddressList())
                .thenReturn(Collections.singletonList(UWB_DEST_ADDRESS));

        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, mockParams,
                        mockRangingCallbacks, TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any(), anyString());

        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, mockSessionHandle, TEST_SESSION_ID,
                FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks, TEST_CHIP_ID);

        assertThat(uwbSession.getControleeList().size() == 1
                && uwbSession.getControleeList().get(0).getUwbAddress().equals(UWB_DEST_ADDRESS))
                .isTrue();

        verify(uwbSession, never()).binderDied();
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, never()).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(uwbSession);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(1); // SESSION_OPEN_RANGING
    }

    @Test
    public void startRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void startRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession uwbSession = mock(UwbSession.class);
        when(uwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        doReturn(uwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        assertThat(mTestLooper.nextMessage().what).isEqualTo(2); // SESSION_START_RANGING
    }

    @Test
    public void startRanging_currentSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        UwbSession mockUwbSession = mock(UwbSession.class);
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        when(mockUwbSession.getProtocolName()).thenReturn(CccParams.PROTOCOL_NAME);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                any(), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_currentSessiionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(Params.class));

        verify(mUwbSessionNotificationManager)
                .onRangingStartFailed(any(), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void stopRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void stopRanging_currentSessionStateActive() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        assertThat(mTestLooper.nextMessage().what).isEqualTo(3); // SESSION_STOP_RANGING
    }

    @Test
    public void stopRanging_currentSessionStateActive_owrAoa() {
        UwbSession mockUwbSession = mock(UwbSession.class);

        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        doReturn(PROTOCOL_NAME).when(mockUwbSession).getProtocolName();
        doReturn(0).when(mockUwbSession).getCurrentFiraRangingIntervalMs();

        // Setup the UwbSession to have the peer device's MacAddress stored (which happens when
        // a valid RANGE_DATA_NTF with an OWR AoA Measurement is received).
        doReturn(PEER_EXTENDED_MAC_ADDRESS).when(mockUwbSession).getRemoteMacAddress();

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));
        mTestLooper.dispatchNext();

        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS);
    }

    @Test
    public void stopRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        verify(mUwbSessionNotificationManager).onRangingStopped(any(),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
    }

    @Test
    public void stopRanging_currentSessionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.stopRanging(mock(SessionHandle.class));

        verify(mUwbSessionNotificationManager).onRangingStopFailed(any(),
                eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void getUwbSession_success() {
        UwbSession expectedUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, expectedUwbSession);

        UwbSession actualUwbSession = mUwbSessionManager.getUwbSession(TEST_SESSION_ID);

        assertThat(actualUwbSession).isEqualTo(expectedUwbSession);
    }

    @Test
    public void getUwbSession_failed() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);

        UwbSession actualUwbSession = mUwbSessionManager.getUwbSession(TEST_SESSION_ID - 1);

        assertThat(actualUwbSession).isNull();
    }

    @Test
    public void getSessionId_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);

        int actualSessionId = mUwbSessionManager.getSessionId(mockSessionHandle);

        assertThat(actualSessionId).isEqualTo(TEST_SESSION_ID);
    }

    @Test
    public void getSessionId_failed() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        when(mockUwbSession.getSessionHandle()).thenReturn(mockSessionHandle);

        Integer actualSessionId = mUwbSessionManager.getSessionId(mock(SessionHandle.class));

        assertThat(actualSessionId).isNull();
    }

    @Test
    public void isExistedSession_sessionHandle_success() {
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());

        boolean result = mUwbSessionManager.isExistedSession(mock(SessionHandle.class));

        assertThat(result).isTrue();
    }

    @Test
    public void iexExistedSession_sessionHandle_failed() {
        doReturn(null).when(mUwbSessionManager).getSessionId(any());

        boolean result = mUwbSessionManager.isExistedSession(mock(SessionHandle.class));

        assertThat(result).isFalse();
    }

    @Test
    public void isExistedSession_sessionId_success() {
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mock(UwbSession.class));

        boolean result = mUwbSessionManager.isExistedSession(TEST_SESSION_ID);

        assertThat(result).isTrue();
    }

    @Test
    public void iexExistedSession_sessionId_failed() {
        boolean result = mUwbSessionManager.isExistedSession(TEST_SESSION_ID);

        assertThat(result).isFalse();
    }

    @Test
    public void stopAllRanging() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        when(mockUwbSession1.getChipId()).thenReturn(TEST_CHIP_ID);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession1);
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        when(mockUwbSession2.getChipId()).thenReturn(TEST_CHIP_ID);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID + 100, mockUwbSession2);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID + 100), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopAllRanging();

        verify(mNativeUwbManager, times(2))
                .stopRanging(anyInt(), anyString());
        verify(mockUwbSession1, never()).setSessionState(anyInt());
        verify(mockUwbSession2).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
    }

    @Test
    public void setCurrentSessionState() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);

        mUwbSessionManager.setCurrentSessionState(
                TEST_SESSION_ID, UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        verify(mockUwbSession).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_ACTIVE));
    }

    @Test
    public void getCurrentSessionState_nullSession() {
        int actualStatus = mUwbSessionManager.getCurrentSessionState(TEST_SESSION_ID);

        assertThat(actualStatus).isEqualTo(UwbUciConstants.UWB_SESSION_STATE_ERROR);
    }

    @Test
    public void getCurrentSessionState_success() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);

        int actualStatus = mUwbSessionManager.getCurrentSessionState(TEST_SESSION_ID);

        assertThat(actualStatus).isEqualTo(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);
    }

    @Test
    public void getSessionIdSet() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession);

        Set<Integer> actualSessionIds = mUwbSessionManager.getSessionIdSet();

        assertThat(actualSessionIds).hasSize(1);
        assertThat(actualSessionIds.contains(TEST_SESSION_ID)).isTrue();
    }

    @Test
    public void reconfigure_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());

        int actualStatus = mUwbSessionManager.reconfigure(
                mock(SessionHandle.class), mock(Params.class));

        assertThat(actualStatus).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    private UwbSession setUpUwbSessionForExecution(AttributionSource attributionSource) {
        // setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        IBinder mockBinder = mock(IBinder.class);
        Params params = setupFiraParams();
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(attributionSource, mockSessionHandle,
                        TEST_SESSION_ID, FiraParams.PROTOCOL_NAME, params, mockRangingCallbacks,
                        TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any(), anyString());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    private Params setupFiraParams() {
        return setupFiraParams(FiraParams.RANGING_DEVICE_ROLE_INITIATOR, Optional.empty());
    }

    private Params setupFiraParams(int deviceRole, Optional<Integer> rangingRoundUsageOptional) {
        FiraOpenSessionParams.Builder paramsBuilder = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UWB_DEST_ADDRESS))
                .setProtocolVersion(new FiraProtocolVersion(1, 0))
                .setSessionId(10)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(deviceRole)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setRangingIntervalMs(TEST_RANGING_INTERVAL_MS);

        if (rangingRoundUsageOptional.isPresent()) {
            paramsBuilder.setRangingRoundUsage(rangingRoundUsageOptional.get());
        }

        return paramsBuilder.build();
    }

    private UwbSession setUpCccUwbSessionForExecution() throws RemoteException {
        // setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params params = new CccOpenRangingParams.Builder()
                .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                .setUwbConfig(CccParams.UWB_CONFIG_0)
                .setPulseShapeCombo(
                        new CccPulseShapeCombo(
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                .setSessionId(1)
                .setRanMultiplier(4)
                .setChannel(CccParams.UWB_CHANNEL_9)
                .setNumChapsPerSlot(CccParams.CHAPS_PER_SLOT_3)
                .setNumResponderNodes(1)
                .setNumSlotsPerRound(CccParams.SLOTS_PER_ROUND_6)
                .setSyncCodeIndex(1)
                .setHoppingConfigMode(CccParams.HOPPING_CONFIG_MODE_NONE)
                .setHoppingSequence(CccParams.HOPPING_SEQUENCE_DEFAULT)
                .build();
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(ATTRIBUTION_SOURCE, mockSessionHandle,
                        TEST_SESSION_ID, CccParams.PROTOCOL_NAME, params, mockRangingCallbacks,
                        TEST_CHIP_ID));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), any(), anyInt(),
                anyString(), any(), any(), anyString());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    @Test
    public void openRanging_success() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).initSession(eq(TEST_SESSION_ID), anyByte(), eq(TEST_CHIP_ID));
        verify(mUwbConfigurationManager)
                .setAppConfigurations(eq(TEST_SESSION_ID), any(), eq(TEST_CHIP_ID));
        verify(mUwbSessionNotificationManager).onRangingOpened(eq(uwbSession));
        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void openRanging_timeout() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenThrow(new IllegalStateException());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_nativeInitSessionFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_setAppConfigurationFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_wrongInitState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void openRanging_wrongIdleState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession),
                        eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID), eq(TEST_CHIP_ID));
    }

    @Test
    public void testInitSessionWithNonSystemAppInFg() throws Exception {
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(true);

        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    @Test
    public void testInitSessionWithNonSystemAppNotInFg() throws Exception {
        when(mUwbInjector.isSystemApp(UID, PACKAGE_NAME)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID, PACKAGE_NAME)).thenReturn(false);

        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        verify(uwbSession.getIUwbRangingCallbacks()).onRangingOpenFailed(
                eq(uwbSession.getSessionHandle()), eq(StateChangeReason.SYSTEM_POLICY), any());
        // No OPEN_RANGING message scheduled.
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    private UwbSession initUwbSessionForNonSystemAppInFgInChain() throws Exception {
        when(mUwbInjector.isSystemApp(UID_2, PACKAGE_NAME_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID_2, PACKAGE_NAME_2))
                .thenReturn(true);

        // simulate system app triggered the request on behalf of a fg app in fg.
        AttributionSource attributionSource = new AttributionSource.Builder(UID)
                .setPackageName(PACKAGE_NAME)
                .setNext(new AttributionSource.Builder(UID_2)
                        .setPackageName(PACKAGE_NAME_2)
                        .build())
                .build();

        UwbSession uwbSession = setUpUwbSessionForExecution(attributionSource);
        mUwbSessionManager.initSession(attributionSource, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        return uwbSession;
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain() throws Exception {
        initUwbSessionForNonSystemAppInFgInChain();

        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain_MoveToBgAndStayThere() throws Exception {
        UwbSession uwbSession = initUwbSessionForNonSystemAppInFgInChain();

        // Verify that an OPEN_RANGING message was scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);

        // Start Ranging
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE,
                UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Move the non-privileged app to background, this should result in the session getting
        // reconfigured (to disable the ranging data notifications).
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_BACKGROUND);
        mTestLooper.dispatchAll();
        ArgumentCaptor<Params> paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID));
        FiraRangingReconfigureParams firaParams =
                (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));

        // Verify the appropriate timer is setup.
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).set(
                anyInt(), anyLong(), eq(UwbSession.NON_PRIVILEGED_BG_APP_TIMER_TAG),
                alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Now fire the timer callback.
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE,
                 UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        alarmListenerCaptor.getValue().onAlarm();

        // Expect session stop.
        mTestLooper.dispatchAll();
        verify(mUwbSessionNotificationManager).onRangingStoppedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void testOpenRangingWithNonSystemAppInFgInChain_MoveToBgAndFg() throws Exception {
        UwbSession uwbSession = initUwbSessionForNonSystemAppInFgInChain();
        // OPEN_RANGING message scheduled.
        assertThat(mTestLooper.nextMessage().what).isEqualTo(SESSION_OPEN_RANGING);
        mTestLooper.dispatchAll();

        // Move to background.
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_BACKGROUND);
        mTestLooper.dispatchAll();
        ArgumentCaptor<Params> paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID));
        FiraRangingReconfigureParams firaParams =
                (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE);

        // Move to foreground.
        mOnUidImportanceListenerArgumentCaptor.getValue().onUidImportance(
                UID_2, IMPORTANCE_FOREGROUND);
        mTestLooper.dispatchAll();
        paramsArgumentCaptor = ArgumentCaptor.forClass(Params.class);
        verify(mUwbConfigurationManager, times(2)).setAppConfigurations(
                eq(TEST_SESSION_ID), paramsArgumentCaptor.capture(), eq(TEST_CHIP_ID));
        firaParams = (FiraRangingReconfigureParams) paramsArgumentCaptor.getValue();
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(
                FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE);
    }

    @Test
    public void testOpenRangingWithNonSystemAppNotInFgInChain() throws Exception {
        when(mUwbInjector.isSystemApp(UID_2, PACKAGE_NAME_2)).thenReturn(false);
        when(mUwbInjector.isForegroundAppOrService(UID_2, PACKAGE_NAME_2))
                .thenReturn(false);

        // simulate system app triggered the request on behalf of a fg app not in fg.
        AttributionSource attributionSource = new AttributionSource.Builder(UID)
                .setPackageName(PACKAGE_NAME)
                .setNext(new AttributionSource.Builder(UID_2)
                        .setPackageName(PACKAGE_NAME_2)
                        .build())
                .build();
        UwbSession uwbSession = setUpUwbSessionForExecution(attributionSource);
        mUwbSessionManager.initSession(attributionSource, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);

        verify(uwbSession.getIUwbRangingCallbacks()).onRangingOpenFailed(
                eq(uwbSession.getSessionHandle()), eq(StateChangeReason.SYSTEM_POLICY), any());
        // No OPEN_RANGING message scheduled.
        assertThat(mTestLooper.isIdle()).isFalse();
    }

    private UwbSession prepareExistingUwbSession() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution(ATTRIBUTION_SOURCE);
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    private UwbSession prepareExistingCccUwbSession() throws Exception {
        UwbSession uwbSession = setUpCccUwbSessionForExecution();
        mUwbSessionManager.initSession(ATTRIBUTION_SOURCE, uwbSession.getSessionHandle(),
                TEST_SESSION_ID, CccParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks(), TEST_CHIP_ID);
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    @Test
    public void reconfigure_calledSuccess() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(10)
                        .setRangeDataNtfConfig(1)
                        .setRangeDataProximityFar(10)
                        .setRangeDataProximityNear(2)
                        .build();

        int actualStatus = mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), params);

        assertThat(actualStatus).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what)
                .isEqualTo(UwbSessionManager.SESSION_RECONFIG_RANGING);

        // Verify the cache has been updated.
        FiraOpenSessionParams firaParams = (FiraOpenSessionParams) uwbSession.getParams();
        assertThat(firaParams.getBlockStrideLength()).isEqualTo(10);
        assertThat(firaParams.getRangeDataNtfConfig()).isEqualTo(1);
        assertThat(firaParams.getRangeDataNtfProximityFar()).isEqualTo(10);
        assertThat(firaParams.getRangeDataNtfProximityNear()).isEqualTo(2);
    }

    @Test
    public void startRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isTrue();
        assertThat(mTestLooper.nextMessage().what).isEqualTo(2); // SESSION_START_RANGING
    }

    @Test
    public void startRanging_sessionStateActive() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void startRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void execStartRanging_onRangeDataNotification() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
    }

    @Test
    public void execStartRanging_twoWay_onRangeDataNotificationContinuousErrors() throws Exception {
        startRanging_onRangeDataNotificationContinuousErrors(RANGING_MEASUREMENT_TYPE_TWO_WAY);
    }

    @Test
    public void execStartRanging_owrAoa_onRangeDataNotificationContinuousErrors() throws Exception {
        startRanging_onRangeDataNotificationContinuousErrors(RANGING_MEASUREMENT_TYPE_OWR_AOA);
    }

    private void startRanging_onRangeDataNotificationContinuousErrors(
            int rangingMeasurementType) throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                rangingMeasurementType, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).set(
                anyInt(), anyLong(), anyString(), alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Send one more error and ensure that the timer is not cancelled.
        uwbRangingData = UwbTestUtils.generateRangingData(
                rangingMeasurementType, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);

        verify(mAlarmManager, never()).cancel(any(AlarmManager.OnAlarmListener.class));

        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        // Now fire the timer callback.
        alarmListenerCaptor.getValue().onAlarm();

        // Expect session stop.
        mTestLooper.dispatchNext();
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execStartRanging_onRangeDataNotificationErrorFollowedBySuccess() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));

        // Now send a range data notification with an error.
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_RANGING_RX_TIMEOUT);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);
        ArgumentCaptor<AlarmManager.OnAlarmListener> alarmListenerCaptor =
                ArgumentCaptor.forClass(AlarmManager.OnAlarmListener.class);
        verify(mAlarmManager).set(
                anyInt(), anyLong(), anyString(), alarmListenerCaptor.capture(), any());
        assertThat(alarmListenerCaptor.getValue()).isNotNull();

        // Send success and ensure that the timer is cancelled.
        uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_TWO_WAY, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);
        verify(mUwbSessionNotificationManager).onRangingResult(uwbSession, uwbRangingData);

        verify(mAlarmManager).cancel(any(AlarmManager.OnAlarmListener.class));
    }

    @Test
    public void execStartCccRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        CccStartRangingParams cccStartRangingParams = new CccStartRangingParams.Builder()
                .setSessionId(TEST_SESSION_ID)
                .setRanMultiplier(8)
                .build();
        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), cccStartRangingParams);
        mTestLooper.dispatchAll();

        // Verify the update logic.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getRanMultiplier()).isEqualTo(8);
    }

    @Test
    public void execStartCccRangingWithNoStartParams_success() throws Exception {
        UwbSession uwbSession = prepareExistingCccUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        mUwbSessionManager.startRanging(uwbSession.getSessionHandle(), null /* params */);
        mTestLooper.dispatchAll();

        // Verify that RAN multiplier from open is used.
        CccOpenRangingParams cccOpenRangingParams = (CccOpenRangingParams) uwbSession.getParams();
        assertThat(cccOpenRangingParams.getRanMultiplier()).isEqualTo(4);
    }

    @Test
    public void execStartRanging_executionException() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_nativeStartRangingFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execStartRanging_wrongSessionState() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void sendData_success_validUwbSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        // Setup the UwbSession to start ranging (and move it to active state).
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        // Send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD))).thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSent(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_missingSessionHandle() throws Exception {
        mUwbSessionManager.sendData(
                null /* sessionHandle */, PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE,
                DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(null), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_invalidUwbSessionHandle() throws Exception {
        // Setup a uwbSession and a different sessionHandle that doesn't map to the uwbSession.
        prepareExistingUwbSession();
        SessionHandle sessionHandle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);

        mUwbSessionManager.sendData(
                sessionHandle, PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(null), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_invalidUwbSessionState() throws Exception {
        // Setup a uwbSession and don't start ranging, so it remains in IDLE state.
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();

        // Attempt to send data on the UWB session.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE, null);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_FAILED), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_missingDataPayload() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        // Setup the UwbSession to start ranging (and move it to active state).
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        // Attempt to send data on the UWB session.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS, PERSISTABLE_BUNDLE, null);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_INVALID_PARAM), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_missingRemoteDevice() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        // Setup the UwbSession to start ranging (and move it to active state).
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        // Attempt to send data on the UWB session.
        mUwbSessionManager.sendData(
                uwbSession.getSessionHandle(), null, PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager, never()).sendData(
                eq(TEST_SESSION_ID), eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(null),
                eq(UwbUciConstants.STATUS_CODE_INVALID_PARAM), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void sendData_dataSendFailure() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        // Setup the UwbSession to start ranging (and move it to active state).
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams());
        mTestLooper.dispatchAll();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        // Attempt to send data on the UWB session.
        when(mNativeUwbManager.sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD))).thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.sendData(uwbSession.getSessionHandle(), PEER_EXTENDED_UWB_ADDRESS,
                PERSISTABLE_BUNDLE, DATA_PAYLOAD);
        mTestLooper.dispatchNext();

        verify(mNativeUwbManager).sendData(eq(TEST_SESSION_ID),
                eq(PEER_EXTENDED_UWB_ADDRESS.toBytes()),
                eq(UwbUciConstants.UWB_DESTINATION_END_POINT_HOST), eq(DATA_SEQUENCE_NUM),
                eq(DATA_PAYLOAD));
        verify(mUwbSessionNotificationManager).onDataSendFailed(
                eq(uwbSession), eq(PEER_EXTENDED_UWB_ADDRESS),
                eq(UwbUciConstants.STATUS_CODE_FAILED), eq(PERSISTABLE_BUNDLE));
    }

    @Test
    public void stopRanging_sessionStateActive() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(3); // SESSION_STOP_RANGING
    }

    @Test
    public void stopRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        verify(mUwbSessionNotificationManager).onRangingStopped(
                eq(uwbSession),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void stopRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for stop ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());

        verify(mUwbSessionNotificationManager).onRangingStopFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_REJECTED));
    }

    @Test
    public void execStopRanging_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbInjector).runTaskOnSingleThreadExecutor(
                any(), eq(IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS));
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.LOCAL_API));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execStopRanging_exception() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager, never()).onRangingStopped(any(), anyInt());
    }

    @Test
    public void execStopRanging_nativeFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager)
                .onRangingStopFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics, never()).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void reconfigure_notExistingSession() {
        int status = mUwbSessionManager.reconfigure(mock(SessionHandle.class), mock(Params.class));

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    private FiraRangingReconfigureParams buildReconfigureParams() {
        return buildReconfigureParams(FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD);
    }

    private FiraRangingReconfigureParams buildReconfigureParams(int action) {
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setAddressList(new UwbAddress[] {
                                UwbAddress.fromBytes(new byte[] { (byte) 0x01, (byte) 0x02 }) })
                        .setAction(action)
                        .setSubSessionIdList(new int[] { 2 })
                        .build();

        return spy(reconfigureParams);
    }

    private FiraRangingReconfigureParams buildReconfigureParamsV2() {
        return buildReconfigureParamsV2(
                FiraParams.P_STS_MULTICAST_LIST_UPDATE_ACTION_ADD_16_BYTE);
    }

    private FiraRangingReconfigureParams buildReconfigureParamsV2(int action) {
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setAddressList(new UwbAddress[] {
                                UwbAddress.fromBytes(new byte[] { (byte) 0x01, (byte) 0x02 }) })
                        .setAction(action)
                        .setSubSessionIdList(new int[] { 2 })
                        .setSubSessionKeyList(new byte[] {0, 0, 0, 0, 1, 1, 1, 1,
                                2, 2, 2, 2, 3, 3, 3, 3})
                        .build();

        return spy(reconfigureParams);
    }

    @Test
    public void reconfigure_existingSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        int status = mUwbSessionManager.reconfigure(
                uwbSession.getSessionHandle(), buildReconfigureParamsV2());

        assertThat(status).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(4); // SESSION_RECONFIGURE_RANGING
    }

    @Test
    public void execReconfigureAddControlee_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                        any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS_2});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the original address is still there.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        // Make sure this new address was added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isTrue();

        short dstAddress =
                ByteBuffer.wrap(reconfigureParams.getAddressList()[0].toBytes()).getShort(0);
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                new short[] {dstAddress}, reconfigureParams.getSubSessionIdList(), null,
                uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureRemoveControleeV1_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams(FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE);
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                        any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        // Make sure the address exists in the first place. This should have been set up by
        //  prepareExistingUwbSession
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the address was removed.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isFalse();

        short dstAddress =
                ByteBuffer.wrap(reconfigureParams.getAddressList()[0].toBytes()).getShort(0);
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                new short[] {dstAddress}, reconfigureParams.getSubSessionIdList(), null,
                uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeRemoved(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigureAddControleeV2_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses())
                .thenReturn(new UwbAddress[] {UWB_DEST_ADDRESS_2});
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Make sure the original address is still there.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS)))
                .isTrue();

        // Make sure this new address was added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isTrue();

        short dstAddress =
                ByteBuffer.wrap(reconfigureParams.getAddressList()[0].toBytes()).getShort(0);
        verify(mNativeUwbManager).controllerMulticastListUpdate(
                uwbSession.getSessionId(), reconfigureParams.getAction(), 1,
                new short[] {dstAddress}, reconfigureParams.getSubSessionIdList(),
                reconfigureParams.getSubSessionKeyList(), uwbSession.getChipId());
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));
        verify(mUwbSessionNotificationManager).onRangingReconfigured(eq(uwbSession));
    }

    @Test
    public void execReconfigure_nativeUpdateFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_uwbSessionUpdateMixedSuccess() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                        any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(2);
        when(uwbMulticastListUpdateStatus.getControleeUwbAddresses()).thenReturn(
                new UwbAddress[] { UWB_DEST_ADDRESS_2, UWB_DEST_ADDRESS_3 });
        // One fail, one success
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_FAILED, UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        // Fail callback for the first one.
        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        // Success callback for the second.
        verify(mUwbSessionNotificationManager).onControleeAdded(eq(uwbSession));

        // Make sure the failed address was not added.
        assertThat(uwbSession.getControleeList().stream()
                .anyMatch(e -> e.getUwbAddress().equals(UWB_DEST_ADDRESS_2)))
                .isFalse();

        // Overall reconfigure fail.
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_uwbSessionUpdateFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_FAILED });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onControleeAddFailed(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigureBlockStriding_success_stop() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(10)
                        .build();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigured(uwbSession);

        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbInjector).runTaskOnSingleThreadExecutor(
                any(), eq(TEST_RANGING_INTERVAL_MS * 2 * 11));
        verify(mUwbSessionNotificationManager)
                .onRangingStoppedWithApiReasonCode(eq(uwbSession),
                        eq(RangingChangeReason.LOCAL_API));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execReconfigure_setAppConfigurationsFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParamsV2();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any(),
                                any(), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any(), anyString()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void deInitSession_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());

        mUwbSessionManager.deInitSession(mock(SessionHandle.class));

        verify(mUwbSessionManager, never()).getSessionId(any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void deInitSession_success() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());

        mUwbSessionManager.deInitSession(mock(SessionHandle.class));

        verify(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));
        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_DEINIT

        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void deInitSession_success_afterOwrAoaMeasurement() {
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession).when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        // Setup the UwbSession to have the peer device's MacAddress stored (which happens when
        // a valid RANGE_DATA_NTF with an OWR AoA Measurement is received).
        doReturn(PEER_EXTENDED_MAC_ADDRESS).when(mockUwbSession).getRemoteMacAddress();

        // Call deInitSession().
        IBinder mockBinder = mock(IBinder.class);
        doReturn(mockBinder).when(mockUwbSession).getBinder();
        doReturn(FiraParams.PROTOCOL_NAME).when(mockUwbSession).getProtocolName();
        doReturn(null).when(mockUwbSession).getAnyNonPrivilegedAppInAttributionSource();
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        mUwbSessionManager.deInitSession(mock(SessionHandle.class));

        mTestLooper.dispatchNext();

        verify(mUwbAdvertiseManager).removeAdvertiseTarget(PEER_EXTENDED_MAC_ADDRESS);
    }

    @Test
    public void execDeInitSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_DEINIT
    }

    @Test
    public void execDeInitSession_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void execDeInitSession_failed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbAdvertiseManager, never()).removeAdvertiseTarget(isA(byte[].class));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
        verifyZeroInteractions(mUwbAdvertiseManager);
    }

    @Test
    public void deinitAllSession() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession1);
        when(mockUwbSession1.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession1.getSessionId()).thenReturn(TEST_SESSION_ID);
        when(mockUwbSession1.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID + 100, mockUwbSession2);
        when(mockUwbSession2.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession2.getSessionId()).thenReturn(TEST_SESSION_ID + 100);
        when(mockUwbSession2.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);

        mUwbSessionManager.deinitAllSession();

        verify(mUwbSessionNotificationManager, times(2))
                .onRangingClosedWithApiReasonCode(any(), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbSessionManager, times(2)).removeSession(any());
        // TODO: enable it when the resetDevice is enabled.
        // verify(mNativeUwbManager).resetDevice(eq(UwbUciConstants.UWBS_RESET));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void onSessionStatusNotification_session_deinit() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                uwbSession.getSessionId(), UwbUciConstants.UWB_SESSION_STATE_DEINIT,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void onSessionStatusNotification_session_deinit_owrAoa() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        UwbRangingData uwbRangingData = UwbTestUtils.generateRangingData(
                RANGING_MEASUREMENT_TYPE_OWR_AOA, MAC_ADDRESSING_MODE_EXTENDED,
                UwbUciConstants.STATUS_CODE_OK);

        // First call onDataReceived() to get the application payload data.
        mUwbSessionManager.onDataReceived(TEST_SESSION_ID, UwbUciConstants.STATUS_CODE_OK,
                DATA_SEQUENCE_NUM, PEER_EXTENDED_MAC_ADDRESS, SOURCE_END_POINT, DEST_END_POINT,
                DATA_PAYLOAD);

        // Next call onRangeDataNotificationReceived() to process the RANGE_DATA_NTF. Setup
        // isPointedTarget() to return "false", as in that scenario the stored AdvertiseTarget
        // is not removed.
        Params firaParams = setupFiraParams(
                RANGING_DEVICE_ROLE_OBSERVER, Optional.of(ROUND_USAGE_OWR_AOA_MEASUREMENT));
        when(uwbSession.getParams()).thenReturn(firaParams);
        when(mUwbAdvertiseManager.isPointedTarget(PEER_EXTENDED_MAC_ADDRESS)).thenReturn(false);
        mUwbSessionManager.onRangeDataNotificationReceived(uwbRangingData);

        verify(mUwbAdvertiseManager).updateAdvertiseTarget(uwbRangingData.mRangingOwrAoaMeasure);
        verify(mUwbAdvertiseManager).isPointedTarget(PEER_EXTENDED_MAC_ADDRESS);

        // Now call onSessionStatusNotificationReceived() on the same UwbSession, and verify that
        // removeAdvertiseTarget() is called to remove any stored OwR AoA Measurement(s).
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.onSessionStatusNotificationReceived(
                uwbSession.getSessionId(), UwbUciConstants.UWB_SESSION_STATE_DEINIT,
                UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosedWithApiReasonCode(
                eq(uwbSession), eq(RangingChangeReason.SYSTEM_POLICY));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);

        verify(mUwbAdvertiseManager).removeAdvertiseTarget(isA(byte[].class));
    }

    @Test
    public void testHandleClientDeath() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(eq(TEST_SESSION_ID), anyString()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        uwbSession.binderDied();

        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }
}
