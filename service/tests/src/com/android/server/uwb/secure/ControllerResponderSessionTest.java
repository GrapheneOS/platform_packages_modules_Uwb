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

import static org.mockito.ArgumentMatchers.any;
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

public class ControllerResponderSessionTest {
    @Mock
    private FiRaSecureChannel mFiRaSecureChannel;
    @Mock
    private SecureSession.Callback mSecureSessionCallback;
    @Mock
    private RunningProfileSessionInfo mRunningProfileSessionInfo;

    @Captor
    private ArgumentCaptor<FiRaSecureChannel.SecureChannelCallback> mSecureChannelCallbackCaptor;

    private ControllerResponderSession mControllerResponderSession;

    private final TestLooper mTestLooper = new TestLooper();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mControllerResponderSession = new ControllerResponderSession(
                mTestLooper.getLooper(), mFiRaSecureChannel, mSecureSessionCallback,
                mRunningProfileSessionInfo);

        mControllerResponderSession.startSession();

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
        mControllerResponderSession.terminateSession();
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
    public void controleeInfoAvailableNotification() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711480018181029000E10B8001008101038203020A0B");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        // TODO: compare the session data.
        verify(mFiRaSecureChannel).sendLocalCommandApdu(any(), any());
    }

    @Test
    public void rdsAvailableNotification() {
        byte[] data = DataTypeConversionUtil.hexStringToByteArray(
                "711680018181029000E10D80010081010282050101020C0D");
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(data, 0x9000);
        DispatchResponse dispatchResponse = DispatchResponse.fromResponseApdu(responseApdu);

        mSecureChannelCallbackCaptor.getValue().onDispatchResponseAvailable(dispatchResponse);

        verify(mSecureSessionCallback).onSessionDataReady(eq(1), any(), eq(false));
    }
}
