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

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_SESSION_AUTO_TERMINATED;
import static com.android.server.uwb.secure.csml.DispatchResponse.OUTBOUND_TARGET_HOST;
import static com.android.server.uwb.secure.csml.DispatchResponse.OUTBOUND_TARGET_REMOTE;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.util.DataTypeConversionUtil;

/**
 * The initiator of dynamic STS session managed by the UWB controllee.
 */
public class ControlleeInitiatorSession extends InitiatorSession {
    private static final String LOG_TAG = "ControlleeInitiater";
    private static final int GET_SESSION_DATA_RETRY_DELAY_MILLS = 100;

    public ControlleeInitiatorSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    private void sendPutControlleeInfoCommand() {
        // TODO: construct data
        byte[] data = new byte[] {(byte) 0x0A, (byte) 0x0B};
        tunnelData(MSG_ID_PUT_CONTROLLEE_INFO, data);
    }

    private void sendGetControlleeSessionData() {
        logd("send get controllee session data msg.");
        // TODO: construct data
        byte[] data = new byte[] {(byte) 0x0C, (byte) 0x0D};
        tunnelData(MSG_ID_GET_SESSION_DATA, data);
    }

    @Override
    protected void handleFiRaSecureChannelEstablished() {
        sendPutControlleeInfoCommand();
    }

    @Override
    protected boolean handleTunnelDataResponseReceived(
            int msgId, @NonNull DispatchResponse response) {
        switch (msgId) {
            case MSG_ID_PUT_CONTROLLEE_INFO:
                return handlePutControlleeInfoResponse(response);
            case MSG_ID_GET_SESSION_DATA:
                return handleGetSessionDataResponse(response);
            default:
                logw("Unknown tunnel message: " + msgId);
                break;
        }
        return false;
    }

    private boolean handlePutControlleeInfoResponse(@NonNull DispatchResponse dispatchResponse) {
        if (dispatchResponse.getOutboundData().isPresent()) {
            DispatchResponse.OutboundData outboundData = dispatchResponse.getOutboundData().get();
            if (outboundData.target == OUTBOUND_TARGET_HOST
                    && outboundData.data != null
                    && outboundData.data.length < 5) {
                StatusWord statusWord =
                        StatusWord.fromInt(
                                DataTypeConversionUtil.arbitraryByteArrayToI32(outboundData.data));
                logd("dispatch response sw: " + statusWord);
                if (statusWord.equals(StatusWord.SW_NO_ERROR)) {
                    mWorkHandler.post(() -> sendGetControlleeSessionData());
                } else {
                    // abort the current session
                    terminateSession();
                    mSessionCallback.onSessionAborted();
                }
                return true;
            }
            logw("unexpected outbound data for controllee info." + outboundData);
        }
        logw("Unexpected response for controllee info.");
        return false;
    }

    private boolean handleGetSessionDataResponse(@NonNull DispatchResponse dispatchResponse) {
        boolean isSessionTerminated = false;
        if (dispatchResponse.getOutboundData().isEmpty()
                || dispatchResponse.getOutboundData().get().target == OUTBOUND_TARGET_REMOTE) {
            logw("unexpected dispatch response for getSessionData");
            return false;
        }
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_SECURE_SESSION_AUTO_TERMINATED:
                    isSessionTerminated = true;
                    break;
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    rdsAvailable = (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                default:
                    logw(
                            "Unexpected notification from dispatch response: "
                                    + notification.notificationEventId);
            }
        }

        if (rdsAvailable != null) {
            // TODO: is the session ID for the sub session if it is 1 to m case?
            mSessionCallback.onSessionDataReady(
                    rdsAvailable.sessionId, rdsAvailable.arbitraryData.get(), isSessionTerminated);
            return true;
        } else if (CsmlUtil.isSessionDataNotAvailable(
                dispatchResponse.getOutboundData().get().data)) {
            mWorkHandler.postDelayed(
                    () -> sendGetControlleeSessionData(), GET_SESSION_DATA_RETRY_DELAY_MILLS);
            return true;
        }
        logw("unexpected dispatch response for get session data");
        return false;
    }

    @Override
    protected void handleTunnelDataFailure(int msgId, @NonNull TunnelDataFailReason failReason) {
        switch (msgId) {
            case MSG_ID_PUT_CONTROLLEE_INFO:
                // fall through
            case MSG_ID_GET_SESSION_DATA:
                // simply abort the session.
                logw("terminate session as tunnel data was failed: " + failReason);
                mFiRaSecureChannel.terminateLocally();
                mSessionCallback.onSessionAborted();
                break;
            default:
                logw("unknown failure response for tunnel message: " + msgId);
                break;
        }
    }

    private void logd(@NonNull String debugMsg) {
        Log.d(LOG_TAG, debugMsg);
    }

    private void logw(@NonNull String debugMsg) {
        Log.w(LOG_TAG, debugMsg);
    }
}
