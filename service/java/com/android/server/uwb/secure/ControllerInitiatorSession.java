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

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_CONTROLLEE_INFO_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_SECURE_SESSION_AUTO_TERMINATED;
import static com.android.server.uwb.secure.csml.DispatchResponse.OUTBOUND_TARGET_HOST;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.ControlleeInfo;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.pm.SessionData;
import com.android.server.uwb.secure.csml.DispatchResponse;

import java.util.Optional;

/**
 * The initiator of dynamic STS session managed by the UWB controller.
 */
public class ControllerInitiatorSession extends InitiatorSession {
    private static final String LOG_TAG = "ControllerInitiator";

    public ControllerInitiatorSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    private void sendGetControlleeInfoCommand() {
        // TODO: construct command data
        byte[] data = new byte[] {};
        tunnelData(MSG_ID_GET_CONTROLLEE_INFO, data);
    }

    @Override
    protected void handleFiRaSecureChannelEstablished() {
        sendGetControlleeInfoCommand();
    }

    @Override
    protected void handleTunnelDataFailure(int msgId, @NonNull TunnelDataFailReason failReason) {
        switch (msgId) {
            case MSG_ID_GET_CONTROLLEE_INFO:
                // fall through
            case MSG_ID_PUT_SESSION_DATA:
                // simply abort the session.
                logw("terminate session as tunnel data was failed: "
                        + failReason + " for msg: " + msgId);
                mFiRaSecureChannel.terminateLocally();
                mSessionCallback.onSessionAborted();
                break;
            default:
                logd("unknown failure response for tunnel message: " + msgId);
                break;
        }
    }

    @Override
    protected boolean handleTunnelDataResponseReceived(
            int msgId, @NonNull DispatchResponse response) {
        switch (msgId) {
            case MSG_ID_GET_CONTROLLEE_INFO:
                return handleGetControlleeInfoResponse(response);
            case MSG_ID_PUT_SESSION_DATA:
                return handlePutSessionDataResponse(response);
            default:
                logd("unknown response for tunnel message: " + msgId);
                break;
        }
        return false;
    }

    private boolean handleGetControlleeInfoResponse(@NonNull DispatchResponse response) {
        for (DispatchResponse.Notification notification : response.notifications) {
            if (notification.notificationEventId
                    == NOTIFICATION_EVENT_ID_CONTROLLEE_INFO_AVAILABLE) {
                byte[] controlleeInfoData =
                        ((DispatchResponse.ControlleeInfoAvailableNotification) notification)
                                .controlleeInfo;
                ControlleeInfo controlleeInfo = ControlleeInfo.fromBytes(controlleeInfoData);
                if (controlleeInfo == null) {
                    logw("received controllee info is not expected.");
                    break;
                }
                Optional<SessionData> sessionData =
                        mRunningProfileSessionInfo.getSessionDataForControllee(controlleeInfo);
                if (sessionData.isEmpty()) {
                    logw("session data must be provided for controller");
                    break;
                }
                // TODO: construct a PUT_DATA command for put session data
                tunnelData(MSG_ID_PUT_SESSION_DATA, sessionData.get().toBytes());
                return true;
            }
        }
        if (response.getOutboundData().isPresent()
                && response.getOutboundData().get().target == OUTBOUND_TARGET_HOST) {
            logw("unexpected response for getControlleeInfo");
            terminateSession();
            mSessionCallback.onSessionAborted();
            return true;
        }
        return false;
    }

    private boolean handlePutSessionDataResponse(@NonNull DispatchResponse response) {
        boolean isSessionTerminated = false;
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        // The outboundData to host supposed to be 0x9000 if the session is not terminated,
        // ignore it as NOTIFICATION_EVENT_ID_RDS_AVAILABLE is enough.
        for (DispatchResponse.Notification notification : response.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_SECURE_SESSION_AUTO_TERMINATED:
                    isSessionTerminated = true;
                    break;
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    // Responder notification
                    rdsAvailable =
                            (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                default:
                    logw("Unexpected nofitication from dispatch response: "
                            + notification.notificationEventId);
            }
        }
        if (rdsAvailable != null) {
            // TODO: is the session ID for the sub session if it is 1 to m case?
            // Or the applet shouldn't update the sessionId, sub session ID assigned by FW.
            mSessionCallback.onSessionDataReady(rdsAvailable.sessionId, null,
                    isSessionTerminated);
            return true;
        }
        if (response.getOutboundData().isPresent()
                && response.getOutboundData().get().target == OUTBOUND_TARGET_HOST) {
            logw("unexpected response for getControlleeInfo");
            terminateSession();
            mSessionCallback.onSessionAborted();
            return true;
        }
        return false;
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
