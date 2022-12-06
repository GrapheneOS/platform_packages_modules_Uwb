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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.os.test.TestLooper;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.util.DataTypeConversionUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ControlleeResponderSessionTest {
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;
    @Mock
    private RunningProfileSessionInfo mRunningProfileSessionInfo;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControlleeResponderSession mControlleeResponderSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mControlleeResponderSession = new ControlleeResponderSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                mRunningProfileSessionInfo);

        mControlleeResponderSession.startSession();

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

    @Test
    public void terminateSession() {
        mControlleeResponderSession.terminateSession();
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
    }

    @Test
    public void abortSessionNotification() {
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
    public void abortSessionWithWrongDispatchResponseStatusWord() {
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(new byte[0], 0x9032);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);
        mTestLooper.dispatchAll();

        verify(mFiRaSecureChannel).terminateLocally();
        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void rdsAvailableNotificationWithSessionData() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711B80018181029000E112800100810102820A010107BF780480020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        verify(mSecureSessionCallback).onSessionDataReady(anyInt(), any(), eq(false));
    }

    @Test
    public void rdsAvailableNotificationWithSessionDataInApplet() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(any(), externalRequestCbCaptor.capture());

        externalRequestCbCaptor.getValue().onSuccess(
                DataTypeConversionUtil.hexStringToByteArray("BF780480020101"));

        verify(mSecureSessionCallback).onSessionDataReady(anyInt(), any(), eq(false));
    }

    @Test
    public void failedToGetSessionDataFromApplet() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711380018181029000E10A80010081010282020101");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<FiRaSecureChannel.ExternalRequestCallback> externalRequestCbCaptor =
                ArgumentCaptor.forClass(FiRaSecureChannel.ExternalRequestCallback.class);
        verify(mFiRaSecureChannel).sendLocalFiRaCommand(any(), externalRequestCbCaptor.capture());

        externalRequestCbCaptor.getValue().onFailure();

        verify(mSecureSessionCallback).onSessionAborted();
    }

    @Test
    public void outboundDataToRemote() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "710780018081029000");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);

        verify(mFiRaSecureChannel).sendRawDataToRemote(dataCaptor.capture());
        assertThat(dataCaptor.getValue()).isEqualTo(new byte[] {(byte) 0x90, (byte) 0x00});
    }
}
