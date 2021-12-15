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
import android.util.Pair;

import com.android.uwb.data.UwbUciConstants;
import com.android.uwb.jni.NativeUwbManager;
import com.android.uwb.params.TlvBuffer;
import com.android.uwb.params.TlvDecoder;
import com.android.uwb.params.TlvDecoderBuffer;
import com.android.uwb.params.TlvEncoder;
import com.android.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;

import java.util.Arrays;

public class UwbConfigurationManager {
    private static final String TAG = "UwbConfManager";

    NativeUwbManager mNativeUwbManager;

    public UwbConfigurationManager(NativeUwbManager nativeUwbManager) {
        mNativeUwbManager = nativeUwbManager;
    }

    public int setAppConfigurations(int sessionId, Params params) {
        int status = UwbUciConstants.STATUS_CODE_FAILED;
        TlvBuffer tlvBuffer = null;

        Log.d(TAG, "setAppConfigurations for protocol: " + params.getProtocolName());
        TlvEncoder encoder = TlvEncoder.getEncoder(params.getProtocolName());
        if (encoder == null) {
            Log.d(TAG, "unsupported encoder protocol type");
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

    /**
     * Retrieve app configurations from UWBS.
     */
    public <T extends Params> Pair<Integer, T> getAppConfigurations(int sessionId,
            String protocolName, byte[] appConfigIds, Class<T> paramType) {
        int status;
        Log.d(TAG, "getAppConfigurations for protocol: " + protocolName);

        byte[] getAppConfig = mNativeUwbManager.getAppConfigurations(sessionId,
                appConfigIds.length, appConfigIds.length, appConfigIds);
        Log.i(TAG, "getAppConfigurations respData: " + UwbUtil.toHexString(getAppConfig));
        if ((getAppConfig != null) && (getAppConfig.length > 0)) {
            status = getAppConfig[0];
        } else {
            Log.e(TAG, "getAppConfigList is null or size of getAppConfigList is zero");
            return Pair.create(UwbUciConstants.STATUS_CODE_FAILED, null);
        }
        TlvDecoder decoder = TlvDecoder.getDecoder(protocolName);
        if (decoder == null) {
            Log.d(TAG, "unsupported decoder protocol type");
            return Pair.create(status, null);
        }

        int numOfConfigs = getAppConfig[1];
        TlvDecoderBuffer tlvs =
                new TlvDecoderBuffer(Arrays.copyOfRange(getAppConfig, 2, getAppConfig.length),
                        numOfConfigs);
        if (!tlvs.parse()) {
            Log.e(TAG, "Failed to parse getAppConfigList tlvs");
            return Pair.create(UwbUciConstants.STATUS_CODE_FAILED, null);
        }
        T params = null;
        try {
            params = decoder.getParams(tlvs, paramType);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Failed to decode", e);
        }
        if (params == null) {
            Log.d(TAG, "Failed to get params from getAppConfigList tlvs");
            return Pair.create(UwbUciConstants.STATUS_CODE_FAILED, null);
        }
        return Pair.create(UwbUciConstants.STATUS_CODE_OK, params);
    }
}
