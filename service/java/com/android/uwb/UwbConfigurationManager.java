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

import android.util.Log;

import com.android.uwb.data.UwbUciConstants;
import com.android.uwb.jni.NativeUwbManager;
import com.android.uwb.params.TlvBuffer;
import com.android.uwb.params.TlvEncoder;
import com.android.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;

public class UwbConfigurationManager {
    private static final String TAG = "UwbConfManager";

    NativeUwbManager mNativeUwbManager;

    public UwbConfigurationManager(NativeUwbManager nativeUwbManager) {
        mNativeUwbManager = nativeUwbManager;
    }

    public int setAppConfigurations(int sessionId, Params params) {
        int status = UwbUciConstants.STATUS_CODE_FAILED;
        TlvBuffer tlvBuffer = null;

        Log.d(TAG, "protocol: " + params.getProtocolName());
        TlvEncoder encoder = TlvEncoder.getEncoder(params.getProtocolName());
        if (encoder == null) {
            Log.d(TAG, "unsupported parameter type");
            return status;
        }

        tlvBuffer = encoder.getTlvBuffer(params);

        if (tlvBuffer.getNoOfParams() != 0) {
            byte[] tlvByteArray = tlvBuffer.getByteArray();
            byte[] appConfig = mNativeUwbManager.setAppConfigurations(sessionId,
                    tlvBuffer.getNoOfParams(),
                    tlvByteArray.length, tlvByteArray);
            Log.i(TAG, "setAppConfigurations respData: " + UwbUtil.toHexString(appConfig));
            if ((appConfig != null) && (appConfig.length > 0)) {
                status = appConfig[0];
            } else {
                Log.e(TAG, "appConfigList is null or size of appConfigList is zero");
                status = UwbUciConstants.STATUS_CODE_FAILED;
            }
        } else {
            // Number of reconfig params FiraRangingReconfigureParams can be null
            status = UwbUciConstants.STATUS_CODE_OK;
        }

        return status;
    }
}
