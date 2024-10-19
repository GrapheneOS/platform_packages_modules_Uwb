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

package com.android.ranging.rtt.backend.internal;

import android.net.MacAddress;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderLocation;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * This class is transform class of RangingResult to fit with RttAdapter.
 */
public class RttRangingPosition {
    private static final String TAG = RttRangingPosition.class.getName();

    private int mDistanceMm;
    private int mDistanceStdDevMm;
    private byte[] mLci;
    private byte[] mLcr;
    private MacAddress mMacAddress;
    //    private long mMaxTimeBetweenNtbMeasurementsMicros;
    private int mMeasurementBandwidth;
    private int mMeasurementChannelFrequencyMHz;
    //    private long mMinTimeBetweenNtbMeasurementsMicros;
    private int mNumAttemptedMeasurements;
    private int mNumSuccessfulMeasurements;
    private PeerHandle mPeerHandle;
    private long mRangingTimestampMillis;
    private int mRssi;
    private int mStatus;
    private ResponderLocation mUnverifiedResponderLocation;
    boolean mIs80211azNtbMeasurement;
    boolean mIs80211mcMeasurement;

    Azimuth mAzimuth;
    Elevation mElevation;


    /**
     * Create empty Ranging Position for RTT.
     */
    public RttRangingPosition() {
        mDistanceMm = 0;
        mDistanceStdDevMm = 0;
        mLci = null;
        mLcr = null;
        mMacAddress = null;
        mMeasurementBandwidth = 0;
        mMeasurementChannelFrequencyMHz = 0;
        mNumAttemptedMeasurements = 0;
        mNumSuccessfulMeasurements = 0;
        mPeerHandle = null;
        mAzimuth = null;
        mElevation = null;
        mRangingTimestampMillis = System.currentTimeMillis();
    }

    /**
     * Create Ranging Position for RTT from RangingResult
     */
    public RttRangingPosition(@NonNull RangingResult rangingResult) {
        mDistanceMm = rangingResult.getDistanceMm();
        mDistanceStdDevMm = rangingResult.getDistanceStdDevMm();
        mLci = rangingResult.getLci();
        mLcr = rangingResult.getLcr();
        mMacAddress = rangingResult.getMacAddress();
//        mMaxTimeBetweenNtbMeasurementsMicros = rangingResult
//        .getMaxTimeBetweenNtbMeasurementsMicros();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mMeasurementBandwidth = rangingResult.getMeasurementBandwidth();
            mMeasurementChannelFrequencyMHz = rangingResult.getMeasurementChannelFrequencyMHz();
        }
//        mMinTimeBetweenNtbMeasurementsMicros = rangingResult
//        .getMinTimeBetweenNtbMeasurementsMicros();
        mNumAttemptedMeasurements = rangingResult.getNumAttemptedMeasurements();
        mNumSuccessfulMeasurements = rangingResult.getNumSuccessfulMeasurements();
        mPeerHandle = rangingResult.getPeerHandle();
        mAzimuth = null;
        mElevation = null;
        mRangingTimestampMillis = rangingResult.getRangingTimestampMillis();

    }

    /**
     * get Distamce(Unit : mm)
     */
    public double getDistance() {
        return mDistanceMm;
    }

    /**
     * get Rssi Dbm
     */
    public int getRssiDbm() {
        return mRssi;
    }

    /**
     * get Ranging Time stamp(Unit : ms)
     */
    public long getRangingTimestampMillis() {
        return mRangingTimestampMillis;
    }

    // WiFi RTT doesn't support Azimuth yet.

    /**
     * get Azumith(Not supported yet)
     */
    public Azimuth getAzimuth() {
        Log.w(TAG, "Azimuth feature is not yet supported in WiFi RTT");
        return mAzimuth;
    }

    // WiFi RTT doesn't support Elevation yet.

    /**
     * get Elevation(Not supported yet)
     */
    public Elevation getElevation() {
        Log.w(TAG, "Elevation feature is not yet supported in WiFi RTT");
        return mElevation;
    }

    /**
     * Azimuth data(Not supported yet)
     */
    public static class Azimuth {
        public int getValue() {
            return 0;
        }
    }

    /**
     * Elevation data(Not supported yet)
     */
    public static class Elevation {
        public int getValue() {
            return 0;
        }
    }
}
