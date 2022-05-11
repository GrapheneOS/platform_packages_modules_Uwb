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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
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

public class ControlleeInitiatorSessionTest {
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;
    @Mock
    private RunningProfileSessionInfo mRunningProfileSessionInfo;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControlleeInitiatorSession mControlleeInitiatorSession;

    @Mock
    private Context mContext;


    private final TestLooper mTestLooper = new TestLooper();
    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mControlleeInitiatorSession = new ControlleeInitiatorSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                mRunningProfileSessionInfo);

        mControlleeInitiatorSession.startSession();

        verify(mFiRaSecureChannel).init(mSecureChannelCallbackCaptor.capture());
    }

    @Test
    public void onSetupError() {
        mSecureChannelCallbackCaptor.getValue()
                .onSetUpError(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL);

        verify(mFiRaSecureChannel).cleanUpTerminatedOrAbortedSession();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void onTerminated() {
        mSecureChannelCallbackCaptor.getValue()
                .onTerminated(/*withError=*/ false);

        verify(mSecureSessionCallback).onSessionTerminated();
    }

    private void putControlleeInfo() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        when(mFiRaSecureChannel.isEstablished()).thenReturn(true);

        mSecureChannelCallbackCaptor.getValue().onEstablished();

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();

        // TODO: capture and assert put controlleeInfo command data.
        externalRequestCallbackCaptor.getValue().onSuccess();
    }

    @Test
    public void onSecureChannelEstablishedPutControlleeInfoSuccess() {
        putControlleeInfo();
        mTestLooper.moveTimeForward(2000);

        // timeout callback
        assertThat(mTestLooper.nextMessage().getCallback()).isNotNull();
    }

    @Test
    public void onSecureChannelEstablishedPutControlleeInfoTimeOut() {
        putControlleeInfo();

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
    public void onSecureChannelEstablishedPutControlleeInfoFail() {
        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCallbackCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);

        mSecureChannelCallbackCaptor.getValue().onEstablished();

        verify(mFiRaSecureChannel).tunnelToRemoteDevice(
                any(), externalRequestCallbackCaptor.capture());
        assertThat(externalRequestCallbackCaptor.getValue()).isNotNull();

        // TODO: capture and assert put controlleeInfo command data.
        externalRequestCallbackCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    private void getControlleeSessionData() {
        putControlleeInfo();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray("710780018181029000");
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

    @Test
    public void putControlleeInfoSuccessResponse() {
        getControlleeSessionData();

        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        // TODO: verify the true getSessionData
        assertThat(tunnelDataCaptor.getValue())
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0C0D"));
    }

    @Test
    public void putControlleeInfoErrorResponse() {
        putControlleeInfo();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray("710780018181029090");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();
        ArgumentCaptor<byte[]> tunnelDataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2))
                .tunnelToRemoteDevice(tunnelDataCaptor.capture(), any());

        assertThat(tunnelDataCaptor.getValue()).isEqualTo(getTerminateRemoteSessionData());
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void getControlleeSessionDataSuccessResponse() {
        getControlleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711680018181029000E10D80010081010282050101020C0D");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mSecureSessionCallback).onSessionDataReady(eq(1), any(), eq(false));
    }

    @Test
    public void getControlleeSessionDataErrorResponse() {
        getControlleeSessionData();
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
    public void getControlleeSessionDataRetry() {
        getControlleeSessionData();
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "71088001818103870100"); // session data not available.
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mFiRaSecureChannel, times(2)).tunnelToRemoteDevice(dataCaptor.capture(), any());
        assertThat(dataCaptor.getValue())
                .isEqualTo(DataTypeConversionUtil.hexStringToByteArray("0C0D"));
    }

    @Test
    public void abortSessionNotification() {
        getControlleeSessionData();

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
        getControlleeSessionData();

        mSecureChannelCallbackCaptor.getValue().onDispatchCommandFailure();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }
}
