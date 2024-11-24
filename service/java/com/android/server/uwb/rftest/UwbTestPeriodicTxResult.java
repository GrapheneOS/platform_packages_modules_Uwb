/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.uwb.rftest;

import android.os.PersistableBundle;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.rftest.RfTestParams;
import com.google.uwb.support.rftest.RfTestPeriodicTxResult;

public class UwbTestPeriodicTxResult implements RfNotificationEvent {
    private int mStatus;
    private byte[] mRawNotificationData;

    public UwbTestPeriodicTxResult(int status, byte[] rawNotificationData) {
        this.mStatus = status;
        this.mRawNotificationData = rawNotificationData;
    }

    @Override
    public int getStatus() {
        return mStatus;
    }

    @Override
    public byte[] getRawNotificationData() {
        return mRawNotificationData;
    }

    @Override
    public int getOperationType() {
        return RfTestParams.TEST_PERIODIC_TX;
    }

    @Override
    public PersistableBundle toBundle() {
        RfTestPeriodicTxResult.Builder periodicRxResult = new RfTestPeriodicTxResult.Builder()
                .setOperationType(getOperationType())
                .setStatus(mStatus);

        return periodicRxResult.build().toBundle();
    }

    @Override
    public String toString() {
        return "UwbTestPeriodicTxResult { "
                + " Status = " + mStatus
                + ", RfOperationType = " + getOperationType()
                + ", RawNotificationData = " + UwbUtil.toHexString(mRawNotificationData)
                + '}';
    }
}
