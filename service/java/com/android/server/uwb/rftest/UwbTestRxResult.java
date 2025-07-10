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

package com.android.server.uwb.rftest;

import android.os.PersistableBundle;

import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.rftest.RfTestParams;
import com.google.uwb.support.rftest.RfTestRxResult;

/**
 * Represents the notification of RF RX test in the UWB testing framework.
 */
public class UwbTestRxResult implements RfNotificationEvent {
    private int mStatus;
    private long mRxDoneTsInt;
    private int mRxDoneTsFrac;
    private double mAoaAzimuth;
    private double mAoaElevation;
    private int mToaGap;
    private int mPhr;
    private byte[] mPsduData;
    private byte[] mRawNotificationData;

    public UwbTestRxResult(int status, long rxDoneTsInt, int rxDoneTsFrac, int aoaAzimuth,
            int aoaElevation, int toaGap, int phr, byte[] psduData, byte[] rawNotificationData) {
        this.mStatus = status;
        this.mRxDoneTsInt = rxDoneTsInt;
        this.mRxDoneTsFrac = rxDoneTsFrac;
        this.mAoaAzimuth = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaAzimuth, 16), 9, 7);
        this.mAoaElevation = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaElevation, 16), 9, 7);
        this.mToaGap = toaGap;
        this.mPhr = phr;
        this.mPsduData = psduData;
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
        return RfTestParams.TEST_RX;
    }

    @Override
    public PersistableBundle toBundle() {
        RfTestRxResult.Builder builder = new RfTestRxResult.Builder(getOperationType(), mStatus,
                mRxDoneTsInt, mRxDoneTsFrac, mAoaAzimuth, mAoaElevation, mToaGap, mPhr, mPsduData,
                mRawNotificationData);
        return builder.build().toBundle();
    }

    @Override
    public String toString() {
        return " UwbTestRxResult { "
                + " Status = " + mStatus
                + ", RxDoneTsInteger = " + mRxDoneTsInt
                + ", RxDoneTsFractional = " + mRxDoneTsFrac
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaElevation = " + mAoaElevation
                + ", ToaGap = " + mToaGap
                + ", Phr = " + mPhr
                + ", PsduData = " + UwbUtil.toHexString(mPsduData)
                + ", RfOperationType = " + getOperationType()
                + ", RawNotificationData = " + UwbUtil.toHexString(mRawNotificationData)
                + '}';
    }
}
