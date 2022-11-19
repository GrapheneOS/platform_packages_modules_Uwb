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

package com.android.server.uwb.secure;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.pm.SessionData;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.util.DataTypeConversionUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

public class ControllerInitiatorSessionTest {
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;
    @Mock
    private RunningProfileSessionInfo mRunningProfileSessionInfo;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControllerInitiatorSession mControllerInitiatorSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mControllerInitiatorSession = new ControllerInitiatorSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                mRunningProfileSessionInfo);

        mControllerInitiatorSession.startSession();

        verify(mFiRaSecureChannel).init(mSecureChannelCallbackCaptor.capture());
    }

    private void getControlleeInfo() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        when(mFiRaSecureChannel.isEstablished()).thenReturn(true);

        mSecureChannelCallbackCaptor.getValue().onEstablished();

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();

        // TODO: capture and assert get controlleeInfo command data.
        externalRequestCallbackCaptor.getValue().onSuccess();
    }

    @Test
    public void onSecureChannelEstablishedPutControlleeInfoSuccess() {
        getControlleeInfo();
        mTestLooper.moveTimeForward(2000);

        // timeout callback
        assertThat(mTestLooper.nextMessage().getCallback()).isNotNull();
    }

    @Test
    public void onSecureChannelEstablishedPutControlleeInfoTimeOut() {
        getControlleeInfo();

        mTestLooper.moveTimeForward(2000);
        mTestLooper.dispatchNext(); // timeout callback

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    private byte[] getTerminateRemoteSessionData() {
        TlvDatum terminateSessionDo = CsmlUtil.constructTerminateSessionGetDoTlv();
        return GetDoCommand.build(terminateSessionDo).getCommandApdu().getEncoded();
    }

    @Test
    public void onSecureChannelEstablishedGetControlleeInfoFail() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        mSecureChannelCallbackCaptor.getValue().onEstablished();

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();

        // TODO: capture and assert get controlleeInfo command data.
        externalRequestCallbackCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void getControlleeInfoErrorResponse() {
        getControlleeInfo();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018181029000"); // outbound to host without 'controllee info' notification
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(dataCaptor.capture(), any());

        assertThat(dataCaptor.getValue()).isEqualTo(getTerminateRemoteSessionData());
        verify(mSecureSessionCallback).onSessionAborted();
    }

    private void handleGetControlleeInfoResponse() {
        getControlleeInfo();
        // Response of getControlleeInfo
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010382020A0B");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        externalRequestCallbackCaptor.getValue().onSuccess();
    }

    private void sendPutControlleeSessionData() {
        SessionData mockSessionData = mock(SessionData.class);
        when(mRunningProfileSessionInfo.getSessionDataForControllee(any()))
                .thenReturn(Optional.of(mockSessionData));
        when(mockSessionData.toBytes())
                .thenReturn(DataTypeConversionUtil.hexStringToByteArray("0C0D"));
        handleGetControlleeInfoResponse();
    }

    @Test
    public void getControlleeInfoSuccessResponse() {
        sendPutControlleeSessionData();

        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        // TODO: verify the true getSessionData
        assertThat(tunnelDataCaptor.getValue())
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0C0D"));
    }

    @Test
    public void getControlleeInfoWithInvalidSessionData() {
        when(mRunningProfileSessionInfo.getSessionDataForControllee(any()))
                .thenReturn(Optional.empty());
        handleGetControlleeInfoResponse();

        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        assertThat(tunnelDataCaptor.getValue()).isEqualTo(getTerminateRemoteSessionData());
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void putControlleeSessionDataSuccessResponse() {
        sendPutControlleeSessionData();
        // response of put ControlleeSessionData
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711680018181029000E10D80010081010282050101020C0D");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mSecureSessionCallback).onSessionDataReady(eq(1), any(), eq(false));
    }

    @Test
    public void putControlleeSessionDataResponseDataToRemote() {
        sendPutControlleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018081029000"); // outbound to remote
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel).sendRawDataToRemote(dataCaptor.capture());
        assertThat(dataCaptor.getValue())
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("9000"));
    }

    @Test
    public void putControlleeSessionDataErrorResponse() {
        sendPutControlleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018181029000"); // outbound to host without 'session data ready' notification
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(3)).tunnelToRemoteDevice(dataCaptor.capture(), any());

        assertThat(dataCaptor.getValue()).isEqualTo(getTerminateRemoteSessionData());
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void abortSessionNotification() {
        sendPutControlleeSessionData();

        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "71038001FF"); // transaction complete with errors
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).cleanUpTerminatedOrAbortedSession();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void dispatchCommandFailureFromApplet() {
        sendPutControlleeSessionData();

        mSecureChannelCallbackCaptor.getValue().onDispatchCommandFailure();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }
}
