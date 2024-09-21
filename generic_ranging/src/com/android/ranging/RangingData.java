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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public class RangingData {
    private final RangingTechnology mTechnology;
    private final double mRangeDistance;
    private final double mAzimuth;
    private final double mElevation;
    private final int mRssi;
    private final Duration mTimestamp;
    private final byte[] mPeerAddress;

    /**
     * @return the ranging technology that produced this data, or {@code Optional.empty()} if the
     * data was fused from multiple technologies.
     */
    public Optional<RangingTechnology> getTechnology() {
        return Optional.ofNullable(mTechnology);
    }

    /** @return range distance in meters */
    public double getRangeMeters() {
        return mRangeDistance;
    }

    /** @return azimuth angle in radians. */
    public OptionalDouble getAzimuthRadians() {
        if (Double.isNaN(mAzimuth)) {
            return OptionalDouble.empty();
        } else {
            return OptionalDouble.of(mAzimuth);
        }
    }

    /** @return elevation angle in degrees, if provided. */
    public OptionalDouble getElevationRadians() {
        if (Double.isNaN(mElevation)) {
            return OptionalDouble.empty();
        } else {
            return OptionalDouble.of(mElevation);
        }
    }

    /** @return rssi in dBm, if provided. */
    public OptionalInt getRssi() {
        if (mRssi == Integer.MIN_VALUE) {
            return OptionalInt.empty();
        } else {
            return OptionalInt.of(mRssi);
        }
    }

    /** @return the timestamp when this data was received, measured as duration since boot. */
    public @NonNull Duration getTimestamp() {
        return mTimestamp;
    }

    /** @return a copy of the sender's address. */
    public byte[] getPeerAddress() {
        return mPeerAddress.clone();
    }

    private RangingData(Builder builder) {
        Preconditions.checkArgument(builder.mRangeDistance != Integer.MIN_VALUE,
                "Range distance is required but was not provided");
        Preconditions.checkArgument(!builder.mTimestamp.isZero(),
                "Timestamp is required but was not provided");
        Preconditions.checkArgument(builder.mPeerAddress != null,
                "Peer address is required but was not provided");

        mTechnology = builder.mTechnology;
        mRangeDistance = builder.mRangeDistance;
        mRssi = builder.mRssi;
        mTimestamp = builder.mTimestamp;
        mPeerAddress = builder.mPeerAddress;
        mAzimuth = builder.mAzimuth;
        mElevation = builder.mElevation;
    }

    /**
     * Builder for {@link RangingData}.
     */
    public static class Builder {
        private RangingTechnology mTechnology = null;
        private double mRangeDistance = Double.NaN;
        private double mAzimuth = Double.NaN;
        private double mElevation = Double.NaN;
        private int mRssi = Integer.MIN_VALUE;
        private Duration mTimestamp = Duration.ZERO;
        private byte[] mPeerAddress = null;

        public Builder() {
        }

        /**
         * Construct a builder from ranging data that has already been built.
         * @param data to copy fields from.
         */
        public static Builder fromBuilt(RangingData data) {
            return new Builder()
                    .setTechnology(data.mTechnology).setRangeDistance(data.mRangeDistance)
                    .setRssi(data.mRssi).setTimestamp(data.mTimestamp)
                    .setPeerAddress(data.getPeerAddress()).setAzimuthRadians(data.mAzimuth)
                    .setElevationRadians(data.mElevation);
        }

        /** @return the built {@link RangingData}. */
        public RangingData build() {
            return new RangingData(this);
        }

        /** @param technology that produced this data. */
        public Builder setTechnology(@Nullable RangingTechnology technology) {
            mTechnology = technology;
            return this;
        }

        /** @param distance - measured distance in meters. */
        public Builder setRangeDistance(double distance) {
            mRangeDistance = distance;
            return this;
        }

        /** @param azimuth angle in radians */
        public Builder setAzimuthRadians(double azimuth) {
            mAzimuth = azimuth;
            return this;
        }

        /** @param elevation angle in radians. */
        public Builder setElevationRadians(double elevation) {
            mElevation = elevation;
            return this;
        }

        /** @param rssi in dBm. */
        public Builder setRssi(int rssi) {
            mRssi = rssi;
            return this;
        }

        /** @param timestamp measured as a duration since device boot. */
        public Builder setTimestamp(Duration timestamp) {
            mTimestamp = timestamp;
            return this;
        }

        /** @param peerAddress as a byte array. */
        public Builder setPeerAddress(byte[] peerAddress) {
            mPeerAddress = peerAddress;
            return this;
        }
    }
}
