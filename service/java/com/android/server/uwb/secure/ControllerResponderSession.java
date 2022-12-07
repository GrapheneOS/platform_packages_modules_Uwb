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

import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_CONTROLEE_INFO_AVAILABLE;
import static com.android.server.uwb.secure.csml.DispatchResponse.NOTIFICATION_EVENT_ID_RDS_AVAILABLE;

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.ControleeInfo;
import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.Optional;

/**
 * The responder of dynamic STS session managed by the UWB controller.
 */
public class ControllerResponderSession extends ResponderSession {
    private static final String LOG_TAG = "ControllerResponder";

    public ControllerResponderSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    @Override
    protected boolean onDispatchResponseReceived(@NonNull DispatchResponse dispatchResponse) {
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        DispatchResponse.ControleeInfoAvailableNotification controleeInfoAvailable = null;
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    // Responder notification
                    rdsAvailable = (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                case NOTIFICATION_EVENT_ID_CONTROLEE_INFO_AVAILABLE:
                    controleeInfoAvailable =
                            (DispatchResponse.ControleeInfoAvailableNotification) notification;
                    break;
                default:
                    logw(
                            "Unexpected nofitication from dispatch response: "
                                    + notification.notificationEventId);
            }
        }
        if (controleeInfoAvailable != null) {
            handleControleeInfoAvailable(
                    ControleeInfo.fromBytes(controleeInfoAvailable.controleeInfo));
            return true;
        }
        if (rdsAvailable != null) {
            mSessionCallback.onSessionDataReady(rdsAvailable.sessionId,
                    Optional.empty(), /*isSessionTerminated=*/ false);
            return true;
        }
        return false;
    }

    private void handleControleeInfoAvailable(@NonNull ControleeInfo controleeInfo) {
        // TODO: remove the placeHolder for mUniqueSessionId
        mUniqueSessionId = Optional.of(1);
        mSessionData = CsmlUtil.generateSessionData(
                mRunningProfileSessionInfo.getUwbCapability(),
                controleeInfo,
                mRunningProfileSessionInfo.getSharedPrimarySessionId(),
                mUniqueSessionId.get(),
                mIsDefaultUniqueSessionId);
        // send session data to the applet.
        // TODO: construct put session data.
        CommandApdu commandApdu = null;
        mFiRaSecureChannel.sendLocalCommandApdu(
                commandApdu,
                new FiRaSecureChannel.ExternalRequestCallback() {
                    @Override
                    public void onSuccess(byte[] responseData) {
                        // do nothing, wait for request from the controlee.
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
        logd("unsolicited data received: " + DataTypeConversionUtil.byteArrayToHexString(data));
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
