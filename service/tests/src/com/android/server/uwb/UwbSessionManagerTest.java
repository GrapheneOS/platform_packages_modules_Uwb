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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

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
    }

    @Test
    public void onRangeDataNotificationReceivedWithValidUwbSession() {
        UwbRangingData mockUwbRangingData = mock(UwbRangingData.class);
        when(mockUwbRangingData.getSessionId()).thenReturn((long) TEST_SESSION_ID);
        UwbSession mockUwbSession = mock(UwbSession.class);
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
        doReturn(mockUwbSession)
                .when(mUwbSessionManager).getUwbSession(anyInt());
        Object mockWaitObj = mock(Object.class);
        when(mockUwbSession.getWaitObj()).thenReturn(mockWaitObj);

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
        when(mockUwbSession.getSessionState()).thenReturn(UwbUciConstants.UWB_SESSION_STATE_ACTIVE);
        Object mockWaitObj = spy(new Object());
        when(mockUwbSession.getWaitObj()).thenReturn(mockWaitObj);

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
}
