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

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.ControlleeInfo;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.pm.SessionData;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.Optional;

/**
 * The responder of dynamic STS session managed by the UWB controller.
 */
public class ControllerResponderSession extends ResponderSession {
    private static final String LOG_TAG = "ControllerResponder";

    public ControllerResponderSession(@NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    @Override
    protected boolean onDispatchResponseReceived(@NonNull DispatchResponse dispatchResponse) {
        boolean isSessionTerminated = false;
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        DispatchResponse.ControlleeInfoAvailableNotification controlleeInfoAvailable = null;
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_SECURE_SESSION_AUTO_TERMINATED:
                    isSessionTerminated = true;
                    break;
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    // Responder notification
                    rdsAvailable =
                            (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                case NOTIFICATION_EVENT_ID_CONTROLLEE_INFO_AVAILABLE:
                    controlleeInfoAvailable =
                            (DispatchResponse.ControlleeInfoAvailableNotification) notification;
                    break;
                default:
                    logw("Unexpected nofitication from dispatch response: "
                            + notification.notificationEventId);
            }
        }
        if (controlleeInfoAvailable != null) {
            handleControlleeInfoAvailable(ControlleeInfo.fromBytes(
                    controlleeInfoAvailable.controlleeInfo));
            return true;
        }
        if (rdsAvailable != null) {
            mSessionCallback.onSessionDataReady(rdsAvailable.sessionId,
                    rdsAvailable.arbitraryData.get(), isSessionTerminated);
            return true;
        }
        return false;
    }

    private void handleControlleeInfoAvailable(@NonNull ControlleeInfo controlleeInfo) {
        Optional<SessionData> sessionData =
                mRunningProfileSessionInfo.getSessionDataForControllee(controlleeInfo);
        if (sessionData.isEmpty()) {
            logw("session data is not available.");
            terminateSession();
        }
        // send session data to the applet.
        // TODO: construct put session data.
        CommandApdu commandApdu = null;
        mFiRaSecureChannel.sendLocalCommandApdu(commandApdu,
                new FiRaSecureChannel.ExternalRequestCallback() {
                    @Override
                    public void onSuccess() {
                        // do nothing, wait for request from the controllee.
                    }

                    @Override
                    public void onFailure() {
                        logw("failed to put session data to applet.");
                        terminateSession();
                    }
                });
    }

    @Override
    protected void onUnsolicitedDataToHostReceived(@NonNull byte[] data) {
        logd("unsolicited data received: "
                + DataTypeConversionUtil.byteArrayToHexString(data));
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
