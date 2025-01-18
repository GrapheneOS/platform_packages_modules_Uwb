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

import android.net.wifi.rtt.RangingResult;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * This class is transform class of RangingResult to fit with RttAdapter.
 */
public class RttRangingPosition {
    private static final String TAG = RttRangingPosition.class.getName();

    private double mDistanceMeters;
    private long mRangingTimestampMillis;
    private int mRssi;
    Azimuth mAzimuth;
    Elevation mElevation;

    /**
     * Create Ranging Position for RTT from RangingResult
     */
    public RttRangingPosition(@NonNull RangingResult rangingResult) {
        mDistanceMeters = rangingResult.getDistanceMm() / 1000.0;
        mRssi = rangingResult.getRssi();
        mAzimuth = null;
        mElevation = null;
        mRangingTimestampMillis = rangingResult.getRangingTimestampMillis();
    }

    /**
     * Gets distance in meter.
     */
    public double getDistanceMeters() {
        return mDistanceMeters;
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
