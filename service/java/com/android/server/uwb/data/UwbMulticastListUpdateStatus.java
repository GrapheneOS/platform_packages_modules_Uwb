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
package com.android.server.uwb.data;

import android.util.Log;
import android.uwb.UwbAddress;

import com.google.common.primitives.Shorts;

import java.util.Arrays;

public class UwbMulticastListUpdateStatus {
    private static final String TAG = "UwbM*ListUpdateStatus";
    private long mSessionId;
    private int mRemainingSize;
    private int mNumOfControlees;
    private int [] mControleeMacAddresses;
    private long[] mSubSessionId;
    private int[] mStatus;
    private UwbAddress[] mControleeUwbAddresses;

    public UwbMulticastListUpdateStatus(long sessionID, int remainingSize, int numOfControlees,
            int[] controleeMacAddresses, long[] subSessionId, int[] status) {
        this.mSessionId = sessionID;
        this.mRemainingSize = remainingSize;
        this.mNumOfControlees = numOfControlees;
        this.mControleeMacAddresses = controleeMacAddresses;
        this.mSubSessionId = subSessionId;
        this.mStatus = status;

        // controleeMacAddresses is currently 4-byte integers. UWB addresses
        //  are 4-byte or 8-byte.  When 8-byte support is needed, controleeMacAddress
        //  will need to be updated.

        Log.d(TAG, "Controlee count: " + numOfControlees + " mac addresses: "
                + Arrays.toString(controleeMacAddresses));

        if (controleeMacAddresses != null) {
            // Precache mac addresses in a more usable and universal form.
            mControleeUwbAddresses = new UwbAddress[controleeMacAddresses.length];
            for (int i = 0; i < controleeMacAddresses.length; i++) {
                mControleeUwbAddresses[i] = UwbAddress.fromBytes(
                        Shorts.toByteArray((short) controleeMacAddresses[i]));
            }
        }
    }

    public long getSessionId() {
        return mSessionId;
    }

    public int getRemainingSize() {
        return mRemainingSize;
    }

    public int getNumOfControlee() {
        return mNumOfControlees;
    }

    // This should go obsolete as we shift to UwbAddresses.
    public int[] getControleeMacAddresses() {
        return mControleeMacAddresses;
    }

    public long[] getSubSessionId() {
        return mSubSessionId;
    }

    public int[] getStatus() {
        return mStatus;
    }

    public UwbAddress[] getControleeUwbAddresses() {
        return mControleeUwbAddresses;
    }

    @Override
    public String toString() {
        return "UwbMulticastListUpdateEvent { "
                + " SessionID =" + mSessionId
                + ", RemainingSize =" + mRemainingSize
                + ", NumOfControlee =" + mNumOfControlees
                + ", MacAddress =" + Arrays.toString(mControleeMacAddresses)
                + ", SubSessionId =" + Arrays.toString(mSubSessionId)
                + ", Status =" + Arrays.toString(mStatus)
                + '}';
    }
}
