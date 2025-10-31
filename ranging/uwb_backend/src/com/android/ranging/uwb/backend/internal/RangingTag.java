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

import static com.android.ranging.uwb.backend.internal.Utils.INVALID_API_CALL;
import static com.android.ranging.uwb.backend.internal.Utils.RANGING_ALREADY_STARTED;
import static com.android.ranging.uwb.backend.internal.Utils.STATUS_OK;
import static com.android.ranging.uwb.backend.internal.Utils.TAG;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.util.Log;
import android.uwb.UwbManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdate;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import java.util.concurrent.ExecutorService;

/** Represents a UWB ranging DT-TAG for DL-TDoA sessions. */
@RequiresApi(api = VERSION_CODES.S)
public class RangingTag extends RangingDevice {

    RangingTag(UwbManager manager, ExecutorService executor,
            OpAsyncCallbackRunner<Boolean> opAsyncCallbackRunner, UwbFeatureFlags uwbFeatureFlags) {
        super(manager, executor, opAsyncCallbackRunner, uwbFeatureFlags);
    }

    @Override
    protected FiraOpenSessionParams getOpenSessionParams() {
        requireNonNull(mRangingParameters);
        FiraOpenSessionParams.Builder builder =
                ConfigurationManager.createOpenSessionParams(
                        FiraParams.RANGING_DEVICE_TYPE_DT_TAG,
                        getLocalAddress(),
                        mRangingParameters,
                        mUwbFeatureFlags)
                .toBuilder();

        return builder.build();
    }

    //TODO: aryamarda - Needs cleanup, remove hashSessionId from RangingDevice
    @Override
    protected int hashSessionId(RangingParameters rangingParameters) {
        return RangingDevice.calculateHashedSessionId(getLocalAddress(), mComplexChannel);
    }

    /**
     * Gets complex channel. If it's the first time that this function is called, it will try to
     * get the best-available settings.
     */
    @SuppressLint("WrongConstant")
    public UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    /** Sets complex channel. */
    public void setComplexChannel(UwbComplexChannel complexChannel) {
        mComplexChannel = complexChannel;
    }

    public RangingParameters getRangingParameters() {
        return mRangingParameters;
    }

    @Override
    public synchronized int startRanging(RangingSessionCallback callback) {
        if (mComplexChannel == null) {
            return Utils.INVALID_API_CALL;
        }
        if (isAlive()) {
            return RANGING_ALREADY_STARTED;
        }

        if (getLocalAddress() == null) {
            return INVALID_API_CALL;
        }

        mLastNtfConfig = mRangingParameters.getUwbRangeDataNtfConfig();
        FiraOpenSessionParams openSessionParams = getOpenSessionParams();
        printStartRangingParameters(openSessionParams.toBundle());

        boolean success =
                mOpAsyncCallbackRunner.execOperation(
                        () -> {
                            if (mChipId != null) {
                                mUwbManager.openRangingSession(
                                        openSessionParams.toBundle(),
                                        mSystemCallbackExecutor,
                                        convertCallback(callback),
                                        mChipId);
                            } else {
                                mUwbManager.openRangingSession(
                                        openSessionParams.toBundle(),
                                        mSystemCallbackExecutor,
                                        convertCallback(callback));
                            }
                        },
                        "Open session");

        Boolean result = mOpAsyncCallbackRunner.getResult();
        if (!success || result == null || !result) {
            return STATUS_OK;
        }

        byte[] rangingRoundIndexes = ((DtTagParameters) mRangingParameters)
                                       .getRangingRoundIndexes();
        if (rangingRoundIndexes == null || rangingRoundIndexes.length == 0) {
            rangingRoundIndexes = new byte[]{0};
        }
        DlTDoARangingRoundsUpdate rangingRounds =
                new DlTDoARangingRoundsUpdate.Builder()
                        .setSessionId(openSessionParams.getSessionId())
                        .setNoOfRangingRounds(rangingRoundIndexes.length)
                        .setRangingRoundIndexes(rangingRoundIndexes)
                        .build();
        success =
                mOpAsyncCallbackRunner.execOperation(
                        () -> mRangingSession.updateRangingRoundsDtTag(
                                rangingRounds.toBundle()),
                        "Update ranging rounds for DT Tag");
        if (!success) {
            Log.w(TAG, "Failed to update ranging rounds for DT Tag.");
        }

        success =
                mOpAsyncCallbackRunner.execOperation(
                        () -> mRangingSession.start(new PersistableBundle()), "Start ranging");

        result = mOpAsyncCallbackRunner.getResult();
        if (success && result != null && result) {
            mRangingReportedAllowed = true;
        }
        return STATUS_OK;
    }

    @Override
    public synchronized int stopRanging() {
        int status = super.stopRanging();
        return status;
    }
}
