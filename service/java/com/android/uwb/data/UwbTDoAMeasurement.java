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
package com.android.uwb.data;

import com.android.uwb.util.UwbUtil;

//temp
public class UwbTDoAMeasurement {
    public byte[] mMacAddress;
    public int mStatus;
    public int mNLoS;
    public int mDistance;
    public float mAoaAzimuth;
    public byte mAoaAzimuthFom;
    public float mAoaElevation;
    public byte mAoaElevationFom;
    public float mAoaDestAzimuth;
    public byte mAoaDestAzimuthFom;
    public float mAoaDestElevation;
    public byte mAoaDestElevationFom;
    public int mSlotIndex;

    public UwbTDoAMeasurement(byte[] macAddress, int status, int nLoS, int distance,
            float aoaAzimuth, byte aoaAzimuthFom, float aoaElevation,
            byte aoaElevationFom, float aoaDestAzimuth, byte aoaDestAzimuthFom,
            float aoaDestElevation, byte aoaDestElevationFom, int slotIndex) {
        /* Fira Spec */
        this.mMacAddress = macAddress;
        this.mStatus = status;
        this.mNLoS = nLoS;
        this.mDistance = distance;
        this.mAoaAzimuth = aoaAzimuth;
        this.mAoaAzimuthFom = aoaAzimuthFom;
        this.mAoaElevation = aoaElevation;
        this.mAoaElevationFom = aoaElevationFom;
        this.mAoaDestAzimuth = aoaDestAzimuth;
        this.mAoaDestAzimuthFom = aoaDestAzimuthFom;
        this.mAoaDestElevation = aoaDestElevation;
        this.mAoaDestElevationFom = aoaDestElevationFom;
        this.mSlotIndex = slotIndex;
    }

    public byte[] getMacAddress() {
        return mMacAddress;
    }

    public int getRangingStatus() {
        return mStatus;
    }

    public int getNLoS() {
        return mNLoS;
    }

    public int getDistance() {
        return mDistance;
    }

    public float getAoaAzimuth() {
        return mAoaAzimuth;
    }

    public byte getAoaAzimuthFom() {
        return mAoaAzimuthFom;
    }

    public float getAoaElevation() {
        return mAoaElevation;
    }

    public byte getAoaElevationFom() {
        return mAoaElevationFom;
    }

    public float getAoaDestAzimuth() {
        return mAoaDestAzimuth;
    }

    public float getAoaDestAzimuthFom() {
        return mAoaDestAzimuthFom;
    }

    public float getAoaDestElevation() {
        return mAoaDestElevation;
    }

    public float getAoaDestElevationFom() {
        return mAoaDestElevationFom;
    }

    public int getSlotIndex() {
        return mSlotIndex;
    }

    public String toString() {
        return "UwbTwoWayMeasurement { "
                + " MacAddress = " + UwbUtil.toHexString(mMacAddress)
                + ", RangingStatus = " + mStatus
                + ", NLoS = " + mNLoS
                + ", Distance = " + mDistance
                + ", AoaAzimuth = " + mAoaAzimuth
                + ", AoaAzimuthFom = " + mAoaAzimuthFom
                + ", AoaElevation = " + mAoaElevation
                + ", AoaElevationFom = " + mAoaElevationFom
                + ", AoaDestAzimuth = " + mAoaDestAzimuth
                + ", AoaDestAzimuthFom = " + mAoaDestAzimuthFom
                + ", AoaDestElevation = " + mAoaDestElevation
                + ", AoaDestElevationFom = " + mAoaDestElevationFom
                + ", SlotIndex = 0x" + UwbUtil.toHexString(mSlotIndex)
                + '}';
    }
}
