/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ranging.uwb;

import androidx.annotation.NonNull;

import com.android.ranging.RangingParameters;
import com.android.ranging.uwb.backend.internal.UwbAddress;
import com.android.ranging.uwb.backend.internal.UwbComplexChannel;
import com.android.ranging.uwb.backend.internal.UwbRangeDataNtfConfig;

import java.util.List;

/** Parameters for UWB ranging. */
public class UwbParameters
        extends com.android.ranging.uwb.backend.internal.RangingParameters
        implements RangingParameters.TechnologyParameters {

    public UwbParameters(int uwbConfigId, int sessionId, int subSessionId,
            byte[] sessionKeyInfo,
            byte[] subSessionKeyInfo,
            UwbComplexChannel complexChannel,
            List<UwbAddress> peerAddresses,
            int rangingUpdateRate,
            @NonNull UwbRangeDataNtfConfig uwbRangeDataNtfConfig,
            int slotDuration, boolean isAoaDisabled) {
        super(uwbConfigId, sessionId, subSessionId, sessionKeyInfo, subSessionKeyInfo,
                complexChannel,
                peerAddresses, rangingUpdateRate, uwbRangeDataNtfConfig, slotDuration,
                isAoaDisabled);
    }
}
