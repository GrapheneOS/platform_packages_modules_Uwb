/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.ranging.uwb.backend.internal;

import androidx.annotation.Nullable;


/** DL-TDoA ranging parameters. */
public class DtTagParameters extends RangingParameters {
    public static final int DT_TAG_CONFIG_ID = 7;

    // DL-TDoA Specific Params
    private final int mRangingIntervalMs;
    private final int mSlotsPerRangingRound;
    private final int mMeasurementVersion;
    @Nullable
    private final byte[] mRangingRoundIndexes;

    public DtTagParameters(
            int sessionId,
            byte[] sessionKeyInfo,
            UwbComplexChannel complexChannel,
            @Utils.SlotDuration int slotDuration,
            @Nullable UwbRangeLimitsConfig rangeLimitsConfig,
            int rangingIntervalMs,
            int slotsPerRangingRound,
            int measurementVersion,
            @Nullable byte[] rangingRoundIndexes) {
        super(
                DT_TAG_CONFIG_ID,
                sessionId,
                0,
                sessionKeyInfo,
                null,
                complexChannel,
                java.util.Collections.emptyList(),
                Utils.NORMAL,
                new UwbRangeDataNtfConfig.Builder().build(),
                slotDuration,
                true,
                rangeLimitsConfig,
                Utils.ANTENNA_MODE_UNSET);
        mRangingIntervalMs = rangingIntervalMs;
        mSlotsPerRangingRound = slotsPerRangingRound;
        mMeasurementVersion = measurementVersion;
        mRangingRoundIndexes = rangingRoundIndexes;
    }

    public int getRangingIntervalMs() {
        return mRangingIntervalMs;
    }

    public int getSlotsPerRangingRound() {
        return mSlotsPerRangingRound;
    }

    public int getMeasurementVersion() {
        return mMeasurementVersion;
    }

    @Nullable
    public byte[] getRangingRoundIndexes() {
        return mRangingRoundIndexes;
    }
}
