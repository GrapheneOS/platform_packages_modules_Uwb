/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.uwb;

import android.os.PersistableBundle;
import android.uwb.RangingChangeReason;

import com.android.uwb.data.UwbUciConstants;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingError;
import com.google.uwb.support.fira.FiraStatusCode;

public class UwbSessionNotificationHelper {

    /**
     * convert reason code
     *
     * @param reason : reason codes in {@link UwbUciConstants}
     * @return : {@link RangingChangeReason} change reason.
     */
    public static int convertReasonCode(int reason) {
        /* set default */
        int rangingChangeReason = RangingChangeReason.UNKNOWN;

        switch (reason) {
            case UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS:
                rangingChangeReason = RangingChangeReason.LOCAL_API;
                break;
            case UwbUciConstants.REASON_ERROR_INSUFFICIENT_SLOTS_PER_RR:
            case UwbUciConstants.REASON_ERROR_SLOT_LENGTH_NOT_SUPPORTED:
            case UwbUciConstants.REASON_ERROR_MAC_ADDRESS_MODE_NOT_SUPPORTED:
            case UwbUciConstants.REASON_ERROR_INVALID_RANGING_INTERVAL:
            case UwbUciConstants.REASON_ERROR_INVALID_STS_CONFIG:
            case UwbUciConstants.REASON_ERROR_INVALID_RFRAME_CONFIG:
                rangingChangeReason = RangingChangeReason.BAD_PARAMETERS;
                break;
        }
        return rangingChangeReason;
    }

    /**
     * convert status code
     *
     * @param status : status codes in {@link UwbUciConstants}
     * @return : {@link RangingChangeReason}  change reason.
     */
    public static int convertStatusCode(int status) {
        /* set default */
        int rangingChangeReason = RangingChangeReason.UNKNOWN;

        switch (status) {
            case UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED:
                rangingChangeReason = RangingChangeReason.MAX_SESSIONS_REACHED;
                break;
            case UwbUciConstants.STATUS_CODE_INVALID_PARAM:
            case UwbUciConstants.STATUS_CODE_INVALID_RANGE:
            case UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE:
                rangingChangeReason = RangingChangeReason.BAD_PARAMETERS;
                break;
            // TODO : Convert more status to proper RangingChangeReason..
        }

        return rangingChangeReason;
    }

    public static PersistableBundle convertStatusToParam(String protocolName, int status) {
        Params c;
        if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
            c = new CccRangingError.Builder().setError(status).build();
        } else {
            c = new FiraStatusCode.Builder().setStatusCode(status).build();
        }
        return c.toBundle();
    }

    public static PersistableBundle convertReasonToParam(String protocolName, int reason) {
        Params c;
        if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
            c = new CccRangingError.Builder().setError(reason).build();
        } else {
            c = new FiraStatusCode.Builder().setStatusCode(reason).build();
        }
        return c.toBundle();
    }

    static String getSessionStateString(int state) {
        String ret = "";
        switch (state) {
            case UwbUciConstants.UWB_SESSION_STATE_INIT:
                ret = "INIT";
                break;
            case UwbUciConstants.UWB_SESSION_STATE_DEINIT:
                ret = "DEINIT";
                break;
            case UwbUciConstants.UWB_SESSION_STATE_ACTIVE:
                ret = "ACTIVE";
                break;
            case UwbUciConstants.UWB_SESSION_STATE_IDLE:
                ret = "IDLE";
                break;
            case UwbUciConstants.UWB_SESSION_STATE_ERROR:
                ret = "ERROR";
                break;
        }
        return ret;
    }
}
