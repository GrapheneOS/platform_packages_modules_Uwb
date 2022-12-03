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

import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.DispatchResponse;
import com.android.server.uwb.util.DataTypeConversionUtil;

/**
 * The responder of dynamic STS session managed by the UWB controllee.
 */
public class ControlleeResponderSession extends ResponderSession {
    private static final String LOG_TAG = "ControlleeResponder";

    public ControlleeResponderSession(
            @NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        super(workLooper, fiRaSecureChannel, sessionCallback, runningProfileSessionInfo);
    }

    @Override
    protected boolean onDispatchResponseReceived(@NonNull DispatchResponse dispatchResponse) {
        boolean isSessionTerminated = false;
        DispatchResponse.RdsAvailableNotification rdsAvailable = null;
        for (DispatchResponse.Notification notification : dispatchResponse.notifications) {
            switch (notification.notificationEventId) {
                case NOTIFICATION_EVENT_ID_RDS_AVAILABLE:
                    // Responder notification
                    rdsAvailable = (DispatchResponse.RdsAvailableNotification) notification;
                    break;
                default:
                    logw(
                            "Unexpected notification from dispatch response: "
                                    + notification.notificationEventId);
            }
        }
        if (rdsAvailable != null) {
            mSessionCallback.onSessionDataReady(
                    rdsAvailable.sessionId, rdsAvailable.arbitraryData, isSessionTerminated);
            return true;
        }
        return false;
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
