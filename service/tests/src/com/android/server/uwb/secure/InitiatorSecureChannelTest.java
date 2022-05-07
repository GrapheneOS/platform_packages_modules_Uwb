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

import static com.android.server.uwb.secure.FiRaSecureChannel.CMD_INITIATE_TRANSACTION;
import static com.android.server.uwb.secure.FiRaSecureChannel.CMD_OPEN_CHANNEL;
import static com.android.server.uwb.secure.FiRaSecureChannel.CMD_SELECT_ADF;
import static com.android.server.uwb.secure.FiRaSecureChannel.CMD_SEND_OOB_DATA;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Message;
import android.os.test.TestLooper;

import com.android.server.uwb.pm.ControlleeInfo;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.DispatchCommand;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.csml.FiRaResponse;
import com.android.server.uwb.secure.csml.GetDoCommand;
import com.android.server.uwb.secure.csml.InitiateTransactionCommand;
import com.android.server.uwb.secure.csml.InitiateTransactionResponse;
import com.android.server.uwb.secure.csml.SelectAdfCommand;
import com.android.server.uwb.secure.csml.SwapInAdfCommand;
import com.android.server.uwb.secure.csml.TunnelCommand;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.omapi.OmapiConnection;
import com.android.server.uwb.transport.Transport;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Bytes;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Optional;

public class InitiatorSecureChannelTest {
    @Mock
    private SecureElementChannel mSecureElementChannel;
    @Mock
    private Transport mTransport;
    @Mock
    RunningProfileSessionInfo mRunningProfileSessionInfo;

    private TestLooper mTestLooper = new TestLooper();

    @Mock
    FiRaSecureChannel.SecureChannelCallback mSecureChannelCallback;

    private InitiatorSecureChannel mInitiatorSecureChannel;

    @Captor
    private ArgumentCaptor<OmapiConnection.InitCompletionCallback>
            mInitCompletionCallbackCaptor;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mInitiatorSecureChannel = new InitiatorSecureChannel(mSecureElementChannel,
                mTransport,
                mTestLooper.getLooper(),
                mRunningProfileSessionInfo);

        doInit();
    }

    private void doInit() {
        doNothing().when(mSecureElementChannel).init(mInitCompletionCallbackCaptor.capture());

        mInitiatorSecureChannel.init(mSecureChannelCallback);
        mTestLooper.dispatchNext();
        mInitCompletionCallbackCaptor.getValue().onInitCompletion();
    }

    @Test
    public void init() {
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_OPEN_CHANNEL);
        assertThat(mInitiatorSecureChannel.getStatus())
                .isEqualTo(FiRaSecureChannel.Status.INITIALIZED);
    }

    private void sendAndDispatchMessage(int what, Optional<Object> obj) {
        Message msg = mInitiatorSecureChannel.mWorkHandler.obtainMessage(what);
        if (obj.isPresent()) {
            msg = mInitiatorSecureChannel.mWorkHandler.obtainMessage(what, obj.get());
        }
        mInitiatorSecureChannel.mWorkHandler.sendMessage(msg);
        mTestLooper.dispatchNext();
    }

    private void doOpenChannel() {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.isOpened()).thenReturn(true);
        when(mRunningProfileSessionInfo.getSecureBlob()).thenReturn(Optional.empty());

        mTestLooper.dispatchNext();
    }
    @Test
    public void openChannelGeneralSuccess() {
        doOpenChannel();

        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.CHANNEL_OPENED);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SELECT_ADF);
    }

    @Test
    public void openChannelGeneralFailed() {
        when(mSecureElementChannel.openChannel()).thenReturn(false);

        mTestLooper.dispatchNext(); // OPEN_CHANNEL

        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.INITIALIZED);
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL));
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    @Test
    public void openChannelSwapInAdfSuccess() throws IOException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mRunningProfileSessionInfo.getSecureBlob()).thenReturn(Optional.of(new byte[0]));
        when(mRunningProfileSessionInfo.getOidOfProvisionedAdf())
                .thenReturn(ObjectIdentifier.INVALID_OID);
        ControlleeInfo mockControlleeInfo = mock(ControlleeInfo.class);
        when(mockControlleeInfo.toBytes()).thenReturn(new byte[0]);
        when(mRunningProfileSessionInfo.getControlleeInfo()).thenReturn(mockControlleeInfo);
        when(mSecureElementChannel.transmit(any(SwapInAdfCommand.class))).thenReturn(
                ResponseApdu.fromResponse(
                        DataTypeConversionUtil.hexStringToByteArray("0604000000019000")));

        mTestLooper.dispatchNext(); // OPEN_CHANNEL

        assertThat(mInitiatorSecureChannel.getStatus())
                .isEqualTo(FiRaSecureChannel.Status.CHANNEL_OPENED);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SELECT_ADF);
    }

    @Test
    public void openChannelSwapInAdfFailed() throws IOException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mRunningProfileSessionInfo.getSecureBlob()).thenReturn(Optional.of(new byte[0]));
        ControlleeInfo mockControlleeInfo = mock(ControlleeInfo.class);
        when(mockControlleeInfo.toBytes()).thenReturn(new byte[0]);
        when(mRunningProfileSessionInfo.getControlleeInfo()).thenReturn(mockControlleeInfo);
        when(mRunningProfileSessionInfo.getOidOfProvisionedAdf())
                .thenReturn(ObjectIdentifier.INVALID_OID);
        when(mSecureElementChannel.transmit(any(SwapInAdfCommand.class))).thenReturn(
                ResponseApdu.fromStatusWord(StatusWord.SW_WARNING_STATE_UNCHANGED));

        mTestLooper.dispatchNext(); // OPEN_CHANNEL

        assertThat(mInitiatorSecureChannel.getStatus())
                .isEqualTo(FiRaSecureChannel.Status.INITIALIZED);
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.OPEN_SE_CHANNEL));
        assertThat(mTestLooper.nextMessage()).isNull();
    }

    private void doSelectAdf() throws IOException {
        when(mSecureElementChannel.transmit(any(SelectAdfCommand.class)))
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);

        when(mRunningProfileSessionInfo.getOidOfProvisionedAdf())
                .thenReturn(ObjectIdentifier.INVALID_OID);

        doOpenChannel();

        mTestLooper.dispatchNext();
    }

    @Test
    public void selectAdfSuccess() throws IOException {
        doSelectAdf();

        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_INITIATE_TRANSACTION);
        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.ADF_SELECTED);
    }

    @Test
    public void selectAdfFailed() throws IOException {
        when(mSecureElementChannel.transmit(any(SelectAdfCommand.class)))
                .thenReturn(ResponseApdu.SW_FILE_NOT_FOUND_APDU);
        doOpenChannel();

        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage()).isNull();
        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.CHANNEL_OPENED);
        verify(mSecureChannelCallback).onSetUpError(eq(FiRaSecureChannel.SetupError.SELECT_ADF));
    }

    private ResponseApdu constructSuccessInitiateTransactionResponse() {
        TlvDatum statusTlv = new TlvDatum(InitiateTransactionResponse.STATUS_TAG,
                DataTypeConversionUtil.hexStringToByteArray("80"));
        TlvDatum dataTlv = new TlvDatum(InitiateTransactionResponse.DATA_TAG,
                DataTypeConversionUtil.hexStringToByteArray("0A0B"));
        TlvDatum responseTlv = new TlvDatum(FiRaResponse.PROPRIETARY_RESPONSE_TAG,
                Bytes.concat(statusTlv.toBytes(), dataTlv.toBytes()));
        return ResponseApdu.fromDataAndStatusWord(responseTlv.toBytes(),
                StatusWord.SW_NO_ERROR.toInt());
    }

    @Test
    public void unicastInitiateTransactionSuccess() throws IOException {
        doSelectAdf();
        when(mRunningProfileSessionInfo.isUnicast()).thenReturn(true);
        when(mRunningProfileSessionInfo.getSelectableOidsOfPeerDevice())
                .thenReturn(ImmutableList.of(
                        ObjectIdentifier.fromBytes(new byte[] { (byte) 0x01 })));
        when(mSecureElementChannel.transmit(any(InitiateTransactionCommand.class)))
                .thenReturn(constructSuccessInitiateTransactionResponse());

        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
    }

    @Test
    public void unicastInitiateTransactionFail() throws IOException {
        doSelectAdf();

        when(mRunningProfileSessionInfo.isUnicast()).thenReturn(true);
        when(mRunningProfileSessionInfo.getSelectableOidsOfPeerDevice())
                .thenReturn(ImmutableList.of(
                        ObjectIdentifier.fromBytes(new byte[] { (byte) 0x01 })));
        when(mSecureElementChannel.transmit(any(InitiateTransactionCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);

        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage()).isNull();
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.INITIATE_TRANSACTION));
    }

    @Test
    public void multicastInitiateTransactionSuccess() throws IOException {
        doSelectAdf();
        when(mRunningProfileSessionInfo.isUnicast()).thenReturn(false);
        when(mRunningProfileSessionInfo.getSharedPrimarySessionId()).thenReturn(Optional.of(1));
        when(mRunningProfileSessionInfo.getSelectableOidsOfPeerDevice())
                .thenReturn(ImmutableList.of(
                        ObjectIdentifier.fromBytes(new byte[] { (byte) 0x01 })));
        when(mSecureElementChannel.transmit(any(InitiateTransactionCommand.class)))
                .thenReturn(constructSuccessInitiateTransactionResponse());

        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
    }

    @Test
    public void multicastInitiateTransactionFail() throws IOException {
        doSelectAdf();

        when(mRunningProfileSessionInfo.isUnicast()).thenReturn(false);
        when(mRunningProfileSessionInfo.getSharedPrimarySessionId()).thenReturn(Optional.of(1));
        when(mRunningProfileSessionInfo.getSelectableOidsOfPeerDevice())
                .thenReturn(ImmutableList.of(
                        ObjectIdentifier.fromBytes(new byte[] { (byte) 0x01 })));
        when(mSecureElementChannel.transmit(any(InitiateTransactionCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);

        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage()).isNull();
        verify(mSecureChannelCallback)
                .onSetUpError(eq(FiRaSecureChannel.SetupError.INITIATE_TRANSACTION));
    }

    private void doPrepareSC() throws IOException {
        doSelectAdf();
        when(mRunningProfileSessionInfo.isUnicast()).thenReturn(true);
        when(mRunningProfileSessionInfo.getSelectableOidsOfPeerDevice())
                .thenReturn(ImmutableList.of(
                        ObjectIdentifier.fromBytes(new byte[] { (byte) 0x01 })));
        when(mSecureElementChannel.transmit(any(InitiateTransactionCommand.class)))
                .thenReturn(constructSuccessInitiateTransactionResponse());

        mTestLooper.dispatchAll();
    }
    private void doEstablishSC() throws IOException {
        doPrepareSC();
        // response: status-81,data-9000, notification format-00, notification id-01
        ResponseApdu responseApdu = ResponseApdu.fromDataAndStatusWord(
                DataTypeConversionUtil.hexStringToByteArray("710F80018181029000E106800100810101"),
                StatusWord.SW_NO_ERROR.toInt());
        when(mSecureElementChannel.transmit(any(DispatchCommand.class))).thenReturn(responseApdu);

        mInitiatorSecureChannel.processRemoteCommandOrResponse(new byte[0]);
    }

    @Test
    public void receiveResponseOfScSetupSuccess() throws IOException {
        doEstablishSC();

        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.ESTABLISHED);
        verify(mSecureChannelCallback).onEstablished();
    }

    @Test
    public void receiveResponseOfScSetupChannelWasNotOpen() {
        mTestLooper.dispatchAll();

        mInitiatorSecureChannel.processRemoteCommandOrResponse(new byte[0]);

        verify(mSecureChannelCallback).onSetUpError(FiRaSecureChannel.SetupError.DISPATCH);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
    }

    @Test
    public void receiveResponseOfScSetupDispatchFailure() throws IOException {
        doPrepareSC();
        when(mSecureElementChannel.transmit(any(DispatchCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);

        mInitiatorSecureChannel.processRemoteCommandOrResponse(new byte[0]);

        verify(mSecureChannelCallback).onSetUpError(FiRaSecureChannel.SetupError.DISPATCH);
        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
    }

    @Test
    public void receiveResponseAfterScSetupSuccess() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(DispatchCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);

        mInitiatorSecureChannel.processRemoteCommandOrResponse(new byte[0]);

        verify(mSecureChannelCallback).onDispatchResponseAvailable(any(DispatchResponse.class));
    }

    @Test
    public void receiveResponseAfterScSetupFail() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(DispatchCommand.class)))
                .thenThrow(new IOException());

        mInitiatorSecureChannel.processRemoteCommandOrResponse(new byte[0]);

        verify(mSecureChannelCallback).onDispatchCommandFailure();
    }

    @Test
    public void cleanupTerminatedOrAbortedSession() throws IOException {
        doEstablishSC();
        when(mSecureElementChannel.closeChannel()).thenReturn(true);

        mInitiatorSecureChannel.cleanUpTerminatedOrAbortedSession();
        mTestLooper.dispatchAll();

        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.INITIALIZED);
        verify(mSecureChannelCallback).onSeChannelClosed(eq(/*withError=*/ false));
    }

    @Test
    public void cleanupTerminatedOrAbortedSessionFailToCloseSEChannel() throws IOException {
        doEstablishSC();
        when(mSecureElementChannel.closeChannel()).thenReturn(false);

        mInitiatorSecureChannel.cleanUpTerminatedOrAbortedSession();
        mTestLooper.dispatchAll();

        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.ABNORMAL);
        verify(mSecureChannelCallback).onSeChannelClosed(eq(/*withError=*/ true));
    }

    @Test
    public void sendLocalCommandApduSuccess() throws IOException {
        when(mSecureElementChannel.isOpened()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(CommandApdu.class)))
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);
        CommandApdu commandApdu =
                CommandApdu.builder(0xCa, 0x84, 0x00, 0x00).build();

        mInitiatorSecureChannel.sendLocalCommandApdu(commandApdu, externalRequestCallback);
        mTestLooper.dispatchAll();

        verify(externalRequestCallback).onSuccess();
    }

    @Test
    public void sendLocalCommamdApduFail() throws IOException {
        when(mSecureElementChannel.isOpened()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(CommandApdu.class)))
                .thenReturn(ResponseApdu.SW_CLA_NOT_SUPPORTED_APDU);
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);
        CommandApdu commandApdu =
                CommandApdu.builder(0xCa, 0x84, 0x00, 0x00).build();

        mInitiatorSecureChannel.sendLocalCommandApdu(commandApdu, externalRequestCallback);
        mTestLooper.dispatchAll();

        verify(externalRequestCallback).onFailure();
    }

    @Test
    public void sendLocalCommandApduFailAsSeWasNotOpen() throws IOException {
        when(mSecureElementChannel.isOpened()).thenReturn(false);
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);
        CommandApdu commandApdu =
                CommandApdu.builder(0xCa, 0x84, 0x00, 0x00).build();

        mInitiatorSecureChannel.sendLocalCommandApdu(commandApdu, externalRequestCallback);
        mTestLooper.dispatchAll();

        verify(externalRequestCallback).onFailure();
    }

    @Test
    public void sendLocalCommandApduFailAsException() throws IOException {
        when(mSecureElementChannel.isOpened()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(CommandApdu.class)))
                .thenThrow(new IOException());
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);
        CommandApdu commandApdu =
                CommandApdu.builder(0xCa, 0x84, 0x00, 0x00).build();

        mInitiatorSecureChannel.sendLocalCommandApdu(commandApdu, externalRequestCallback);
        mTestLooper.dispatchAll();

        verify(externalRequestCallback).onFailure();
    }

    @Test
    public void terminateLocallySuccess() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(GetDoCommand.class)))
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);

        mInitiatorSecureChannel.terminateLocally();
        mTestLooper.dispatchAll();

        assertThat(mInitiatorSecureChannel.getStatus()).isEqualTo(
                FiRaSecureChannel.Status.TERMINATED);
        verify(mSecureChannelCallback).onTerminated(eq(/*withError=*/ false));
    }

    @Test
    public void terminateUnestablishedChannelLocally() throws IOException {

        mInitiatorSecureChannel.terminateLocally();
        mTestLooper.dispatchAll();

        verify(mSecureChannelCallback).onTerminated(eq(/*withError=*/ false));
    }

    @Test
    public void terminateLocallyWithException() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(GetDoCommand.class))).thenThrow(new IOException());

        mInitiatorSecureChannel.terminateLocally();
        mTestLooper.dispatchAll();

        assertThat(mInitiatorSecureChannel.getStatus())
                .isEqualTo(FiRaSecureChannel.Status.ABNORMAL);
        verify(mSecureChannelCallback).onTerminated(eq(/*withError=*/ true));
    }

    @Test
    public void terminateLocallyFail() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(GetDoCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);

        mInitiatorSecureChannel.terminateLocally();
        mTestLooper.dispatchAll();

        assertThat(mInitiatorSecureChannel.getStatus())
                .isEqualTo(FiRaSecureChannel.Status.ABNORMAL);
        verify(mSecureChannelCallback).onTerminated(/*withError=*/ true);
    }

    @Test
    public void tunnelToRemoteDeviceSuccess() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(TunnelCommand.class)))
                .thenReturn(ResponseApdu.fromDataAndStatusWord(
                        DataTypeConversionUtil.hexStringToByteArray("7103810101"), 0x9000));
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);

        mInitiatorSecureChannel.tunnelToRemoteDevice(new byte[0], externalRequestCallback);
        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage().what).isEqualTo(CMD_SEND_OOB_DATA);
        verify(externalRequestCallback).onSuccess();
    }

    @Test
    public void tunnelToRemoteDeviceWrongSW() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(TunnelCommand.class)))
                .thenReturn(ResponseApdu.SW_CONDITIONS_NOT_SATISFIED_APDU);
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);

        mInitiatorSecureChannel.tunnelToRemoteDevice(new byte[0], externalRequestCallback);
        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage()).isNull();
        verify(externalRequestCallback).onFailure();
    }

    @Test
    public void tunnelToRemoteDeviceEmptyData() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(TunnelCommand.class)))
                .thenReturn(ResponseApdu.SW_SUCCESS_APDU);
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);

        mInitiatorSecureChannel.tunnelToRemoteDevice(new byte[0], externalRequestCallback);
        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage()).isNull();
        verify(externalRequestCallback).onFailure();
    }

    @Test
    public void tunnelToRemoteDeviceEmptyWithException() throws IOException {
        doEstablishSC();
        mTestLooper.dispatchAll();
        when(mSecureElementChannel.transmit(any(TunnelCommand.class)))
                .thenThrow(new IOException());
        FiRaSecureChannel.ExternalRequestCallback externalRequestCallback =
                mock(FiRaSecureChannel.ExternalRequestCallback.class);

        mInitiatorSecureChannel.tunnelToRemoteDevice(new byte[0], externalRequestCallback);
        mTestLooper.dispatchNext();

        assertThat(mTestLooper.nextMessage()).isNull();
        verify(externalRequestCallback).onFailure();
    }
}
