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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.UwbSessionManager.WaitObj;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class UwbSessionManagerTest {
    private static final int TEST_SESSION_ID = 100;
    private static final int MAX_SESSION_NUM = 8;

    @Mock
    private UwbConfigurationManager mUwbConfigurationManager;
    @Mock
    private NativeUwbManager mNativeUwbManager;
    @Mock
    private UwbMetrics mUwbMetrics;
    @Mock
    private UwbSessionNotificationManager mUwbSessionNotificationManager;
    private TestLooper mTestLooper = new TestLooper();

    private UwbSessionManager mUwbSessionManager;

    private MockitoSession mMockitoSession;

    @Mock
    private ExecutorService mExecutorService;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        when(mNativeUwbManager.getMaxSessionNumber()).thenReturn(MAX_SESSION_NUM);

        // TODO: Don't use spy.
        mUwbSessionManager = spy(new UwbSessionManager(
                mUwbConfigurationManager,
                mNativeUwbManager,
                mUwbMetrics,
                mUwbSessionNotificationManager,
                mTestLooper.getLooper()));

        // static mocking for executor service.
        mMockitoSession = ExtendedMockito.mockitoSession()
                .mockStatic(Executors.class, Mockito.withSettings().lenient())
                .strictness(Strictness.LENIENT)
                .startMocking();

        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(0);
            t.run();
            return t;
        }).when(mExecutorService).submit(any(Runnable.class));
        when(Executors.newSingleThreadExecutor()).thenReturn(mExecutorService);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void onRangeDataNotificationReceivedWithValidUwbSession() {
        UwbRangingData mockUwbRangingData = mock(UwbRangingData.class);
        when(mockUwbRangingData.getSessionId()).thenReturn((long) TEST_SESSION_ID);
        UwbSession mockUwbSession = mock(UwbSession.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mock(WaitObj.class));
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(mockUwbRangingData);

        verify(mUwbSessionNotificationManager)
                .onRangingResult(eq(mockUwbSession), eq(mockUwbRangingData));
        verify(mUwbMetrics).logRangingResult(anyInt(), eq(mockUwbRangingData));
    }

    @Test
    public void onRangeDataNotificationReceivedWithInvalidSession() {
        UwbRangingData mockUwbRangingData = mock(UwbRangingData.class);
        when(mockUwbRangingData.getSessionId()).thenReturn((long) TEST_SESSION_ID);
        doReturn(null)
                .when(mUwbSessionManager).getUwbSession(eq(TEST_SESSION_ID));

        mUwbSessionManager.onRangeDataNotificationReceived(mockUwbRangingData);

        verify(mUwbSessionNotificationManager, never())
                .onRangingResult(any(), eq(mockUwbRangingData));
        verify(mUwbMetrics, never()).logRangingResult(anyInt(), eq(mockUwbRangingData));
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
        verify(mUwbSessionNotificationManager).onRangingStopped(
                eq(mockUwbSession),
                eq(UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED));
    }

    @Test
    public void initSession_ExistedSession() throws RemoteException {
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        doReturn(true).when(mUwbSessionManager).isExistedSession(anyInt());

        mUwbSessionManager.initSession(mock(SessionHandle.class), TEST_SESSION_ID, "any",
                mock(Params.class), mockRangingCallbacks);

        verify(mockRangingCallbacks).onRangingOpenFailed(any(), anyInt(), any());
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void initSession_maxSession() throws RemoteException {
        doReturn(MAX_SESSION_NUM).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);

        mUwbSessionManager.initSession(mock(SessionHandle.class), TEST_SESSION_ID, "any",
                mock(Params.class), mockRangingCallbacks);

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
                mUwbSessionManager.new UwbSession(mockSessionHandle, TEST_SESSION_ID,
                        FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), anyInt(),
                anyString(), any(), any());
        doThrow(new RemoteException()).when(mockBinder).linkToDeath(any(), anyInt());

        mUwbSessionManager.initSession(mockSessionHandle, TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                mockParams, mockRangingCallbacks);

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
                mUwbSessionManager.new UwbSession(mockSessionHandle, TEST_SESSION_ID,
                        FiraParams.PROTOCOL_NAME, mockParams, mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), anyInt(),
                anyString(), any(), any());

        mUwbSessionManager.initSession(mockSessionHandle, TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                mockParams, mockRangingCallbacks);

        verify(uwbSession, never()).binderDied();
        verify(mockRangingCallbacks, never()).onRangingOpenFailed(any(), anyInt(), any());
        verify(mockBinder, never()).unlinkToDeath(any(), anyInt());
        assertThat(mUwbSessionManager.getUwbSession(TEST_SESSION_ID)).isEqualTo(uwbSession);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(1); // SESSION_OPEN_RANGING
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
        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_CLOSE
    }

    @Test
    public void startRanging_notExistedSession() {
        doReturn(false).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(PersistableBundle.class));

        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void startRanging_currentSessionStateIdle() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(PersistableBundle.class));

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

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(PersistableBundle.class));

        verify(mUwbSessionNotificationManager).onRangingStarted(any(), any());
    }

    @Test
    public void startRanging_currentSessiionStateInvalid() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        doReturn(TEST_SESSION_ID).when(mUwbSessionManager).getSessionId(any());
        doReturn(mock(UwbSession.class)).when(mUwbSessionManager).getUwbSession(anyInt());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(mUwbSessionManager).getCurrentSessionState(anyInt());

        mUwbSessionManager.startRanging(mock(SessionHandle.class), mock(PersistableBundle.class));

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
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession1);
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID + 100, mockUwbSession2);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID + 100)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopAllRanging();

        verify(mNativeUwbManager, times(2)).stopRanging(anyInt());
        verify(mockUwbSession1, never()).setSessionState(anyInt());
        verify(mockUwbSession2).setSessionState(eq(UwbUciConstants.UWB_SESSION_STATE_IDLE));
    }

    @Test
    public void deinitAllSession() {
        UwbSession mockUwbSession1 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID, mockUwbSession1);
        when(mockUwbSession1.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession1.getSessionId()).thenReturn(TEST_SESSION_ID);
        UwbSession mockUwbSession2 = mock(UwbSession.class);
        mUwbSessionManager.mSessionTable.put(TEST_SESSION_ID + 100, mockUwbSession2);
        when(mockUwbSession2.getBinder()).thenReturn(mock(IBinder.class));
        when(mockUwbSession2.getSessionId()).thenReturn(TEST_SESSION_ID + 100);

        mUwbSessionManager.deinitAllSession();

        verify(mUwbSessionNotificationManager, times(2))
                .onRangingClosed(any(),
                        eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
        verify(mUwbSessionManager, times(2)).removeSession(any());
        // TODO: enable it when the resetDevice is enabled.
        // verify(mNativeUwbManager).resetDevice(eq(UwbUciConstants.UWBS_RESET));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
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

        int actualStatus = mUwbSessionManager
                .reconfigure(mock(SessionHandle.class), mock(PersistableBundle.class));

        assertThat(actualStatus).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    @Test
    public void reconfigure_calledSuccess() {
        doReturn(true).when(mUwbSessionManager).isExistedSession(any());
        FiraRangingReconfigureParams params =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(10)
                        .setRangeDataNtfConfig(1)
                        .setRangeDataProximityFar(10)
                        .setRangeDataProximityNear(2)
                        .build();

        int actualStatus = mUwbSessionManager
                .reconfigure(mock(SessionHandle.class), params.toBundle());

        assertThat(actualStatus).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what)
                .isEqualTo(4); // SESSION_RECONFIG_RANGING
    }

    private UwbSession setUpUwbSessionForExecution() throws RemoteException {
        // setup message
        doReturn(0).when(mUwbSessionManager).getSessionCount();
        doReturn(false).when(mUwbSessionManager).isExistedSession(anyInt());
        IUwbRangingCallbacks mockRangingCallbacks = mock(IUwbRangingCallbacks.class);
        SessionHandle mockSessionHandle = mock(SessionHandle.class);
        Params params = new FiraOpenSessionParams.Builder()
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02 }))
                .setVendorId(new byte[] { (byte) 0x00, (byte) 0x01 })
                .setStaticStsIV(new byte[] { (byte) 0x01, (byte) 0x02, (byte) 0x03,
                        (byte) 0x04, (byte) 0x05, (byte) 0x06 })
                .setDestAddressList(Arrays.asList(
                        UwbAddress.fromBytes(new byte[] {(byte) 0x03, (byte) 0x04 })))
                .setProtocolVersion(new FiraProtocolVersion(1, 0))
                .setSessionId(10)
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .build();
        IBinder mockBinder = mock(IBinder.class);
        UwbSession uwbSession = spy(
                mUwbSessionManager.new UwbSession(mockSessionHandle, TEST_SESSION_ID,
                        FiraParams.PROTOCOL_NAME, params, mockRangingCallbacks));
        doReturn(mockBinder).when(uwbSession).getBinder();
        doReturn(uwbSession).when(mUwbSessionManager).createUwbSession(any(), anyInt(),
                anyString(), any(), any());
        doReturn(mock(WaitObj.class)).when(uwbSession).getWaitObj();

        return uwbSession;
    }

    @Test
    public void openRanging_success() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).initSession(eq(TEST_SESSION_ID), anyByte());
        verify(mUwbConfigurationManager).setAppConfigurations(eq(TEST_SESSION_ID), any());
        verify(mUwbSessionNotificationManager).onRangingOpened(eq(uwbSession));
        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void openRanging_timeout() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenThrow(new IllegalStateException());
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_nativeInitSessionFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);


        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_setAppConfigurationFailed() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_wrongInitState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR,
                UwbUciConstants.UWB_SESSION_STATE_IDLE).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    @Test
    public void openRanging_wrongIdleState() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        // stub for openRanging conditions
        when(mNativeUwbManager.initSession(anyInt(), anyByte()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        doReturn(UwbUciConstants.UWB_SESSION_STATE_INIT,
                UwbUciConstants.UWB_SESSION_STATE_ERROR).when(uwbSession).getSessionState();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);


        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.dispatchAll();

        verify(mUwbMetrics).logRangingInitEvent(eq(uwbSession),
                eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbSessionNotificationManager)
                .onRangingOpenFailed(eq(uwbSession),
                        eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mNativeUwbManager).deInitSession(eq(TEST_SESSION_ID));
    }

    private UwbSession prepareExistingUwbSession() throws Exception {
        UwbSession uwbSession = setUpUwbSessionForExecution();
        mUwbSessionManager.initSession(uwbSession.getSessionHandle(),
                TEST_SESSION_ID, FiraParams.PROTOCOL_NAME,
                uwbSession.getParams(), uwbSession.getIUwbRangingCallbacks());
        mTestLooper.nextMessage(); // remove the OPEN_RANGING msg;

        assertThat(mTestLooper.isIdle()).isFalse();

        return uwbSession;
    }

    @Test
    public void startRanging_sessionStateIdle() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());

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
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());

        assertThat(mTestLooper.isIdle()).isFalse();
        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
    }

    @Test
    public void startRanging_sessionStateError() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ERROR)
                .when(uwbSession).getSessionState();

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());

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
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStarted(eq(uwbSession), any());
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
    }

    @Test
    public void execStartRanging_executionException() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        // set up for start ranging
        doReturn(UwbUciConstants.UWB_SESSION_STATE_IDLE, UwbUciConstants.UWB_SESSION_STATE_ACTIVE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenThrow(new IllegalStateException());

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());
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
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());
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
        when(mNativeUwbManager.startRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.startRanging(
                uwbSession.getSessionHandle(), uwbSession.getParams().toBundle());
        mTestLooper.dispatchAll();

        verify(mUwbSessionNotificationManager).onRangingStartFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).longRangingStartEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
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
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager)
                .onRangingStopped(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void execStopRanging_exception() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        doReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE, UwbUciConstants.UWB_SESSION_STATE_IDLE)
                .when(uwbSession).getSessionState();
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
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
        when(mNativeUwbManager.stopRanging(eq(TEST_SESSION_ID)))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.stopRanging(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager)
                .onRangingStopFailed(eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics, never()).longRangingStopEvent(eq(uwbSession));
    }

    @Test
    public void reconfigure_notExistingSession() {
        int status = mUwbSessionManager.reconfigure(
                mock(SessionHandle.class), mock(PersistableBundle.class));

        assertThat(status).isEqualTo(UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST);
    }

    private FiraRangingReconfigureParams buildReconfigureParams() {
        FiraRangingReconfigureParams reconfigureParams =
                new FiraRangingReconfigureParams.Builder()
                        .setAddressList(new UwbAddress[] {
                                UwbAddress.fromBytes(new byte[] { (byte) 0x01, (byte) 0x02 }) })
                        .setAction(FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD)
                        .setSubSessionIdList(new int[] { 2 })
                        .build();

        return spy(reconfigureParams);
    }

    @Test
    public void reconfigure_existingSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        int status = mUwbSessionManager.reconfigure(
                uwbSession.getSessionHandle(), buildReconfigureParams().toBundle());

        assertThat(status).isEqualTo(0);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(4); // SESSION_RECONFIGURE_RANGING
    }

    @Test
    public void execReconfigure_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams.toBundle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigured(
                eq(uwbSession),
                eq(UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS));
    }

    @Test
    public void execReconfigure_nativeUpdateFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams.toBundle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_uwbSessionUpdateFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getNumOfControlee()).thenReturn(1);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_FAILED });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams.toBundle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void execReconfigure_setAppConfigurationsFailed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        FiraRangingReconfigureParams reconfigureParams =
                buildReconfigureParams();
        when(mNativeUwbManager
                .controllerMulticastListUpdate(anyInt(), anyInt(), anyInt(), any(), any()))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);
        UwbMulticastListUpdateStatus uwbMulticastListUpdateStatus =
                mock(UwbMulticastListUpdateStatus.class);
        when(uwbMulticastListUpdateStatus.getStatus()).thenReturn(
                new int[] { UwbUciConstants.STATUS_CODE_OK });
        doReturn(uwbMulticastListUpdateStatus).when(uwbSession).getMulticastListUpdateStatus();
        when(mUwbConfigurationManager.setAppConfigurations(anyInt(), any()))
                .thenReturn(UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.reconfigure(uwbSession.getSessionHandle(), reconfigureParams.toBundle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingReconfigureFailed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
    }

    @Test
    public void deInitSession() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());

        assertThat(mTestLooper.nextMessage().what).isEqualTo(5); // SESSION_CLOSE
    }

    @Test
    public void execCloseSession_success() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(TEST_SESSION_ID))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_OK);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_OK));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }

    @Test
    public void execCloseSession_failed() throws Exception {
        UwbSession uwbSession = prepareExistingUwbSession();
        when(mNativeUwbManager.deInitSession(TEST_SESSION_ID))
                .thenReturn((byte) UwbUciConstants.STATUS_CODE_FAILED);

        mUwbSessionManager.deInitSession(uwbSession.getSessionHandle());
        mTestLooper.dispatchNext();

        verify(mUwbSessionNotificationManager).onRangingClosed(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        verify(mUwbMetrics).logRangingCloseEvent(
                eq(uwbSession), eq(UwbUciConstants.STATUS_CODE_FAILED));
        assertThat(mUwbSessionManager.getSessionCount()).isEqualTo(0);
    }
}
