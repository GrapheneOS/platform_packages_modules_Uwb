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

package com.android.ranging.rtt.backend;

import android.annotation.SuppressLint;
import android.net.wifi.rtt.RangingResult;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * This class is transform class of RangingResult to fit with RttAdapter.
 */
public class RttRangingPosition {
    private static final String TAG = RttRangingPosition.class.getName();

    private double mDistanceMeters;
    private double mDistanceStdDevMeters;
    private int mNumSuccessfulMeasurements;
    private long mRangingTimestampMillis;
    private int mRssi;
    Azimuth mAzimuth;
    Elevation mElevation;
    private int mNumAttemptedMeasurements;
    private int mMeasurementBandwidth;
    private int mMeasurementChannelFrequencyMHz;
    private byte[] mLci;
    private long mNtbMinMeasurementTime;
    private long mNtbMaxMeasurementTime;
    private int mI2rTxLtfRepetitions;
    private int mR2iTxLtfRepetitions;
    private int mNumTxSpatialStreams;
    private int mNumRxSpatialStreams;

    /**
     * Create Ranging Position for RTT from RangingResult
     */
    @SuppressLint("NewApi")
    public RttRangingPosition(@NonNull RangingResult rangingResult) {
        mDistanceMeters = rangingResult.getDistanceMm() / 1000.0;
        mDistanceStdDevMeters = rangingResult.getDistanceStdDevMm() / 1000.0;
        mNumSuccessfulMeasurements = rangingResult.getNumSuccessfulMeasurements();
        mRssi = rangingResult.getRssi();
        mAzimuth = null;
        mElevation = null;
        mRangingTimestampMillis = rangingResult.getRangingTimestampMillis();
        mNumAttemptedMeasurements = rangingResult.getNumAttemptedMeasurements();
        mMeasurementBandwidth = rangingResult.getMeasurementBandwidth();
        mMeasurementChannelFrequencyMHz = rangingResult.getMeasurementChannelFrequencyMHz();
        mLci = rangingResult.getLci();
        mNtbMinMeasurementTime = rangingResult.getMinTimeBetweenNtbMeasurementsMicros();
        mNtbMaxMeasurementTime = rangingResult.getMaxTimeBetweenNtbMeasurementsMicros();
        mI2rTxLtfRepetitions = rangingResult.get80211azInitiatorTxLtfRepetitionsCount();
        mR2iTxLtfRepetitions = rangingResult.get80211azResponderTxLtfRepetitionsCount();
        mNumTxSpatialStreams = rangingResult.get80211azNumberOfTxSpatialStreams();
        mNumRxSpatialStreams = rangingResult.get80211azNumberOfRxSpatialStreams();
    }

    /**
     * Gets distance in meter.
     */
    public double getDistanceMeters() {
        return mDistanceMeters;
    }

    /**
     * Gets distance standard deviation in meters.
     */
    public double getDistanceStdDevMeters() {
        return mDistanceStdDevMeters;
    }

    /**
     * Gets number of successful measurements. Must be at least 2 for distance std dev to be valid.
     */
    public int getNumSuccessfulMeasurements() {
        return mNumSuccessfulMeasurements;
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

    public int getMeasurementBandwidth() {
        return mMeasurementBandwidth;
    }

    public int getNumAttemptedMeasurements() {
        return mNumAttemptedMeasurements;
    }

    public int getMeasurementChannelFrequencyMHz() {
        return mMeasurementChannelFrequencyMHz;
    }

    public byte[] getLci() {
        return mLci;
    }

    public long getNtbMinMeasurementTime() {
        return mNtbMinMeasurementTime;
    }

    public long getNtbMaxMeasurementTime() {
        return mNtbMaxMeasurementTime;
    }

    public int getI2rTxLtfRepetitions() {
        return mI2rTxLtfRepetitions;
    }

    public int getR2iTxLtfRepetitions() {
        return mR2iTxLtfRepetitions;
    }

    public int getNumTxSpatialStreams() {
        return mNumTxSpatialStreams;
    }

    public int getNumRxSpatialStreams() {
        return mNumRxSpatialStreams;
    }
}
