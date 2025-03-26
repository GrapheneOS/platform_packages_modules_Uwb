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
import com.google.uwb.support.rftest.RfTestLoopbackResult;
import com.google.uwb.support.rftest.RfTestParams;

public class UwbTestLoopbackResult implements RfNotificationEvent {
    private int mStatus;
    private long mTxTsInt;
    private int mTxTsFrac;
    private long mRxTsInt;
    private int mRxTsFrac;
    private int mAoaAzimuth;
    private int mAoaElevation;
    private int mPhr;
    private byte[] mPsduData;
    private byte[] mRawNotificationData;

    public UwbTestLoopbackResult(int status, long txTsInt, int txTsFrac, long rxTsInt,
                                 int rxTsFrac, int aoaAzimuth, int aoaElevation, int phr,
                                 byte[] psduData, byte[] rawNotificationData) {
        this.mStatus = status;
        this.mTxTsInt = txTsInt;
        this.mTxTsFrac = txTsFrac;
        this.mRxTsInt = rxTsInt;
        this.mRxTsFrac = rxTsFrac;
        this.mAoaAzimuth = aoaAzimuth;
        this.mAoaElevation = aoaElevation;
        this.mPhr = phr;
        this.mPsduData = psduData;
        this.mRawNotificationData = rawNotificationData;
    }

    @Override
    public int getStatus() {
        return mStatus;
    }

    public long getTxTsInt() {
        return mTxTsInt;
    }

    public int getTxTsFrac() {
        return mTxTsFrac;
    }

    public long getRxTsInt() {
        return mRxTsInt;
    }

    public int getRxTsFrac() {
        return mRxTsFrac;
    }

    public int getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public int getAoaElevation() {
        return mAoaElevation;
    }

    public int getPhr() {
        return mPhr;
    }

    public byte[] getPsduData() {
        return mPsduData;
    }

    @Override
    public byte[] getRawNotificationData() {
        return mRawNotificationData;
    }

    @Override
    public int getOperationType() {
        return RfTestParams.TEST_LOOPBACK;
    }

    @Override
    public PersistableBundle toBundle() {
        RfTestLoopbackResult.Builder loopbackResult = new RfTestLoopbackResult.Builder()
                .setOperationType(getOperationType())
                .setStatus(mStatus)
                .setTxTsInt(mTxTsInt)
                .setTxTsFrac(mTxTsFrac)
                .setRxTsInt(mRxTsInt)
                .setRxTsFrac(mRxTsFrac)
                .setAoaAzimuth(mAoaAzimuth)
                .setAoaElevation(mAoaElevation)
                .setPhr(mPhr)
                .setPsduData(mPsduData)
                .setRawNtfData(mRawNotificationData);
        return loopbackResult.build().toBundle();
    }

    @Override
    public String toString() {
        return "UwbTestLoopbackResult { "
                + " Status = " + mStatus
                + ", TxTsInt = " + mTxTsInt
                + ", TxTsFrac = " + mTxTsFrac
                + ", RxTsInt = " + mRxTsInt
                + ", RxTsFrac = " + mRxTsFrac
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaElevation = " + mAoaElevation
                + ", Phr = " + mPhr
                + ", PsduData = " + UwbUtil.toHexString(mPsduData)
                + ", RfOperationType = " + getOperationType()
                + ", RawNotificationData = " + UwbUtil.toHexString(mRawNotificationData)
                + '}';
    }
}
