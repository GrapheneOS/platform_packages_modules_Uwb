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
import com.google.uwb.support.rftest.RfTestPerRxResult;

public class UwbTestPerRxResult implements RfNotificationEvent {
    private int mStatus;
    private long mAttempts;
    private long mAcqDetect;
    private long mAcqReject;
    private long mRxFail;
    private long mSyncCirReady;
    private long mSfdFail;
    private long mSfdFound;
    private long mPhrDecError;
    private long mPhrBitError;
    private long mPsduDecError;
    private long mPsduBitError;
    private long mStsFound;
    private long mEof;
    private byte[] mRawNotificationData;

    public UwbTestPerRxResult(int status, byte[] rawNotificationData) {
        this.mStatus = status;
        this.mRawNotificationData = rawNotificationData;
    }

    public UwbTestPerRxResult(int status, long attempts, long acqDetect, long acqReject,
                              long rxFail,  long syncCirReady, long sfdFail, long sfdFound,
                              long phrDecError, long phrBitError,
                              long psduDecError, long psduBitError, long stsFound, long eof,
                              byte[] rawNotificationData) {
        this.mStatus = status;
        this.mAttempts = attempts;
        this.mAcqDetect = acqDetect;
        this.mAcqReject = acqReject;
        this.mRxFail = rxFail;
        this.mSyncCirReady = syncCirReady;
        this.mSfdFail = sfdFail;
        this.mSfdFound = sfdFound;
        this.mPhrDecError = phrDecError;
        this.mPhrBitError = phrBitError;
        this.mPsduDecError = psduDecError;
        this.mPsduBitError = psduBitError;
        this.mStsFound = stsFound;
        this.mEof = eof;
        this.mRawNotificationData = rawNotificationData;
    }

    @Override
    public int getStatus() {
        return mStatus;
    }

    public long getAttempts() {
        return mAttempts;
    }

    public long getAcqDetect() {
        return mAcqDetect;
    }

    public long getAcqReject() {
        return mAcqReject;
    }

    public long getRxFail() {
        return mRxFail;
    }

    public long getSyncCirReady() {
        return mSyncCirReady;
    }

    public long getSfdFail() {
        return mSfdFail;
    }

    public long getSfdFound() {
        return mSfdFound;
    }

    public long getPhrDecError() {
        return mPhrDecError;
    }

    public long getPhrBitError() {
        return mPhrBitError;
    }

    public long getPsduDecError() {
        return mPsduDecError;
    }

    public long getPsduBitError() {
        return mPsduBitError;
    }

    public long getStsFound() {
        return mStsFound;
    }

    public long getEof() {
        return mEof;
    }

    @Override
    public byte[] getRawNotificationData() {
        return mRawNotificationData;
    }

    @Override
    public int getOperationType() {
        return RfTestParams.TEST_PER_RX;
    }

    @Override
    public PersistableBundle toBundle() {
        RfTestPerRxResult.Builder periodicRxResult = new RfTestPerRxResult.Builder()
                .setOperationType(getOperationType())
                .setStatus(mStatus)
                .setAttempts(mAttempts)
                .setAcqDetect(mAcqDetect)
                .setAcqReject(mAcqReject)
                .setRxFail(mRxFail)
                .setSyncCirReady(mSyncCirReady)
                .setSfdFail(mSfdFail)
                .setSfdFound(mSfdFound)
                .setPhrDecError(mPhrDecError)
                .setPhrBitError(mPhrBitError)
                .setPsduDecError(mPsduDecError)
                .setPsduBitError(mPsduBitError)
                .setStsFound(mStsFound)
                .setRawNtfData(mRawNotificationData)
                .setEof(mEof);
        return periodicRxResult.build().toBundle();
    }

    @Override
    public String toString() {
        return "UwbTestPerRxResult { "
                + " Status = " + mStatus
                + ", Attempts = " + mAttempts
                + ", AcqDetect = " + mAcqDetect
                + ", AcqReject = " + mAcqReject
                + ", RxFail = " + mRxFail
                + ", SyncCirReady = " + mSyncCirReady
                + ", SfdFail = " + mSfdFail
                + ", SfdFound = " + mSfdFound
                + ", PhrDecError = " + mPhrDecError
                + ", PhrBitError = " + mPhrBitError
                + ", PsduDecError = " + mPsduDecError
                + ", PsduBitError = " + mPsduBitError
                + ", StsFound = " + mStsFound
                + ", Eof = " + mEof
                + ", RfOperationType = " + getOperationType()
                + ", RawNotificationData = " + UwbUtil.toHexString(mRawNotificationData)
                + '}';
    }
}
