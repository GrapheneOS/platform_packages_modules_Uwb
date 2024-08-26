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

package com.android.ranging;

import java.util.OptionalDouble;

/** Ranging Data class contains data received from a ranging technology such as UWB or CS. */
public class RangingReport {
    private final RangingTechnology mRangingTechnology;
    private final double mRangeDistance;
    private final float mAzimuth;
    private final float mElevation;
    private final int mRssi;
    private final long mTimestamp;
    private final byte[] mPeerAddress;

    /** Returns the ranging technology this data is for. */
    public RangingTechnology getRangingTechnology() {
        return mRangingTechnology;
    }

    /** Returns range distance in meters. */
    public double getRangeDistance() {
        return mRangeDistance;
    }

    /** Gets the azimuth in degrees. */
    public OptionalDouble getAzimuth() {
        if (mAzimuth != Float.MAX_VALUE) {
            return OptionalDouble.of(mAzimuth);
        }
        return OptionalDouble.empty();
    }

    /** Gets the elevation in degrees. */
    public OptionalDouble getElevation() {
        if (mElevation != Float.MAX_VALUE) {
            return OptionalDouble.of(mElevation);
        }
        return OptionalDouble.empty();
    }

    /** Returns rssi. */
    public int getRssi() {
        return mRssi;
    }

    /** Returns timestamp in nanos. */
    public long getTimestamp() {
        return mTimestamp;
    }

    /** Returns a copy of the sender's address */
    public byte[] getPeerAddress() {
        return mPeerAddress.clone();
    }

    private RangingReport(Builder builder) {
        mRangingTechnology = builder.mRangingTechnology;
        mRangeDistance = builder.mRangeDistance;
        mRssi = builder.mRssi;
        mTimestamp = builder.mTimestamp;
        mPeerAddress = builder.mPeerAddress;
        mAzimuth = builder.mAzimuth;
        mElevation = builder.mElevation;
    }

    /** Builder for {@link RangingReport}. */
    public static class Builder {
        private RangingTechnology mRangingTechnology;
        private double mRangeDistance;
        private float mAzimuth = Float.MAX_VALUE;
        private float mElevation = Float.MAX_VALUE;
        private int mRssi;
        private long mTimestamp;
        private byte[] mPeerAddress;

        /** Set the ranging technology that produced this data */
        public Builder setRangingTechnology(RangingTechnology rangingTechnology) {
            mRangingTechnology = rangingTechnology;
            return this;
        }

        /** Set the measured distance in meters */
        public Builder setRangeDistance(double rangeDistance) {
            mRangeDistance = rangeDistance;
            return this;
        }

        /** Sets the azimuth in degrees. */
        public Builder setAzimuth(float azimuth) {
            mAzimuth = azimuth;
            return this;
        }

        /** Sets the elevation in degrees. */
        public Builder setElevation(float elevation) {
            mElevation = elevation;
            return this;
        }

        /** Set the measured RSSI in dBm */
        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        /** Set the timestamp of the measurement */
        public Builder setTimestamp(long timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        /** Set the peer address as a byte array */
        public Builder setPeerAddress(byte[] peerAddress) {
            mPeerAddress = peerAddress;
            return this;
        }

        /** Build the ranging data */
        public RangingReport build() {
            return new RangingReport(this);
        }
    }
}
