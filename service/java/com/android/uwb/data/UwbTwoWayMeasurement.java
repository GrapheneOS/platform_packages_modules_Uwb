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

public class UwbTwoWayMeasurement {
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

    public UwbTwoWayMeasurement(byte[] macAddress, int status, int nLoS, int distance,
            int aoaAzimuth, int aoaAzimuthFom, int aoaElevation,
            int aoaElevationFom, int aoaDestAzimuth, int aoaDestAzimuthFom,
            int aoaDestElevation, int aoaDestElevationFom, int slotIndex) {

        this.mMacAddress = macAddress;
        this.mStatus = status;
        this.mNLoS = nLoS;
        this.mDistance = distance;
        this.mAoaAzimuth = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaAzimuth, 16), 9, 7);
        this.mAoaAzimuthFom = (byte) aoaAzimuthFom;
        this.mAoaElevation = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaElevation, 16), 9, 7);
        this.mAoaElevationFom = (byte) aoaElevationFom;
        this.mAoaDestAzimuth = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaDestAzimuth, 16), 9, 7);
        this.mAoaDestAzimuthFom = (byte) aoaDestAzimuthFom;
        this.mAoaDestElevation = UwbUtil.convertQFormatToFloat(
                UwbUtil.twos_compliment(aoaDestElevation, 16), 9, 7);
        this.mAoaDestElevationFom = (byte) aoaDestElevationFom;
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
