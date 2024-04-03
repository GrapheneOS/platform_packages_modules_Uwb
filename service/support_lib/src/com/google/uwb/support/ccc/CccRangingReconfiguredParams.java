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

package com.google.uwb.support.ccc;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.RangingSession;

import androidx.annotation.FloatRange;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Defines parameters for CCC reconfigure operation
 *
 * <p>This is passed as a bundle to the client callback
 * {@link RangingSession.Callback#onReconfigured}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccRangingReconfiguredParams extends CccParams {

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    @Nullable @CccParams.RangeDataNtfConfig
    private final Integer mRangeDataNtfConfig;
    @Nullable private final Integer mRangeDataProximityNear;
    @Nullable private final Integer mRangeDataProximityFar;
    @Nullable private final Double mRangeDataAoaAzimuthLower;
    @Nullable private final Double mRangeDataAoaAzimuthUpper;
    @Nullable private final Double mRangeDataAoaElevationLower;
    @Nullable private final Double mRangeDataAoaElevationUpper;


    private static final String KEY_UPDATE_RANGE_DATA_NTF_CONFIG = "update_range_data_ntf_config";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR =
            "update_range_data_proximity_near";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR =
            "update_range_data_proximity_far";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER =
            "range_data_aoa_azimuth_lower";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER =
            "range_data_aoa_azimuth_upper";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_LOWER =
            "range_data_aoa_elevation_lower";
    private static final String KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_UPPER =
            "range_data_aoa_elevation_upper";

    public CccRangingReconfiguredParams(
            @Nullable Integer rangeDataNtfConfig,
            @Nullable Integer rangeDataProximityNear,
            @Nullable Integer rangeDataProximityFar,
            @Nullable Double rangeDataAoaAzimuthLower,
            @Nullable Double rangeDataAoaAzimuthUpper,
            @Nullable Double rangeDataAoaElevationLower,
            @Nullable Double rangeDataAoaElevationUpper) {
        mRangeDataNtfConfig = rangeDataNtfConfig;
        mRangeDataProximityNear = rangeDataProximityNear;
        mRangeDataProximityFar = rangeDataProximityFar;
        mRangeDataAoaAzimuthLower = rangeDataAoaAzimuthLower;
        mRangeDataAoaAzimuthUpper = rangeDataAoaAzimuthUpper;
        mRangeDataAoaElevationLower = rangeDataAoaElevationLower;
        mRangeDataAoaElevationUpper = rangeDataAoaElevationUpper;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        if (mRangeDataNtfConfig != null) {
            bundle.putInt(KEY_UPDATE_RANGE_DATA_NTF_CONFIG, mRangeDataNtfConfig);
        }

        if (mRangeDataProximityNear != null) {
            bundle.putInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR, mRangeDataProximityNear);
        }

        if (mRangeDataProximityFar != null) {
            bundle.putInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR, mRangeDataProximityFar);
        }

        if (mRangeDataAoaAzimuthLower != null) {
            bundle.putDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER,
                    mRangeDataAoaAzimuthLower);
        }

        if (mRangeDataAoaAzimuthUpper != null) {
            bundle.putDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER,
                    mRangeDataAoaAzimuthUpper);
        }

        if (mRangeDataAoaElevationLower != null) {
            bundle.putDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_LOWER,
                    mRangeDataAoaElevationLower);
        }

        if (mRangeDataAoaElevationUpper != null) {
            bundle.putDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_UPPER,
                    mRangeDataAoaElevationUpper);
        }
        return bundle;
    }


    public static CccRangingReconfiguredParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static CccRangingReconfiguredParams parseVersion1(PersistableBundle bundle) {
        CccRangingReconfiguredParams.Builder builder = new CccRangingReconfiguredParams.Builder();

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_CONFIG)) {
            builder.setRangeDataNtfConfig(bundle.getInt(KEY_UPDATE_RANGE_DATA_NTF_CONFIG));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR)) {
            builder.setRangeDataProximityNear(
                    bundle.getInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_NEAR));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR)) {
            builder.setRangeDataProximityFar(
                    bundle.getInt(KEY_UPDATE_RANGE_DATA_NTF_PROXIMITY_FAR));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER)) {
            builder.setRangeDataAoaAzimuthLower(
                    bundle.getDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_LOWER));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER)) {
            builder.setRangeDataAoaAzimuthUpper(
                    bundle.getDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_AZIMUTH_UPPER));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_LOWER)) {
            builder.setRangeDataAoaElevationLower(
                    bundle.getDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_LOWER));
        }

        if (bundle.containsKey(KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_UPPER)) {
            builder.setRangeDataAoaElevationUpper(
                    bundle.getDouble(KEY_UPDATE_RANGE_DATA_NTF_AOA_ELEVATION_UPPER));
        }

        return builder.build();
    }

    /** Builder */
    public static class Builder {

        @Nullable private Integer mRangeDataNtfConfig = null;
        @Nullable private Integer mRangeDataProximityNear = null;
        @Nullable private Integer mRangeDataProximityFar = null;
        @Nullable private Double mRangeDataAoaAzimuthLower = null;
        @Nullable private Double mRangeDataAoaAzimuthUpper = null;
        @Nullable private Double mRangeDataAoaElevationLower = null;
        @Nullable private Double mRangeDataAoaElevationUpper = null;

        public CccRangingReconfiguredParams.Builder setRangeDataNtfConfig(int rangeDataNtfConfig) {
            mRangeDataNtfConfig = rangeDataNtfConfig;
            return this;
        }

        public CccRangingReconfiguredParams.Builder setRangeDataProximityNear(
                int rangeDataProximityNear) {
            mRangeDataProximityNear = rangeDataProximityNear;
            return this;
        }

        public CccRangingReconfiguredParams.Builder setRangeDataProximityFar(
                int rangeDataProximityFar) {
            mRangeDataProximityFar = rangeDataProximityFar;
            return this;
        }

        public CccRangingReconfiguredParams.Builder setRangeDataAoaAzimuthLower(
                @FloatRange(from = RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT)
                double rangeDataAoaAzimuthLower) {
            mRangeDataAoaAzimuthLower = rangeDataAoaAzimuthLower;
            return this;
        }

        public CccRangingReconfiguredParams.Builder setRangeDataAoaAzimuthUpper(
                @FloatRange(from = RANGE_DATA_NTF_AOA_AZIMUTH_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_AZIMUTH_UPPER_DEFAULT)
                double rangeDataAoaAzimuthUpper) {
            mRangeDataAoaAzimuthUpper = rangeDataAoaAzimuthUpper;
            return this;
        }

        public CccRangingReconfiguredParams.Builder setRangeDataAoaElevationLower(
                @FloatRange(from = RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT)
                double rangeDataAoaElevationLower) {
            mRangeDataAoaElevationLower = rangeDataAoaElevationLower;
            return this;
        }

        public CccRangingReconfiguredParams.Builder setRangeDataAoaElevationUpper(
                @FloatRange(from = RANGE_DATA_NTF_AOA_ELEVATION_LOWER_DEFAULT,
                        to = RANGE_DATA_NTF_AOA_ELEVATION_UPPER_DEFAULT)
                double rangeDataAoaElevationUpper) {
            mRangeDataAoaElevationUpper = rangeDataAoaElevationUpper;
            return this;
        }

        public CccRangingReconfiguredParams build() {
            return new CccRangingReconfiguredParams(
                    mRangeDataNtfConfig,
                    mRangeDataProximityNear,
                    mRangeDataProximityFar,
                    mRangeDataAoaAzimuthLower,
                    mRangeDataAoaAzimuthUpper,
                    mRangeDataAoaElevationLower,
                    mRangeDataAoaElevationUpper);
        }
    }

    @Nullable
    public Integer getRangeDataNtfConfig() {
        return mRangeDataNtfConfig;
    }

    @Nullable
    public Integer getRangeDataProximityNear() {
        return mRangeDataProximityNear;
    }

    @Nullable
    public Integer getRangeDataProximityFar() {
        return mRangeDataProximityFar;
    }

    @Nullable
    public Double getRangeDataAoaAzimuthLower() {
        return mRangeDataAoaAzimuthLower;
    }

    @Nullable
    public Double getRangeDataAoaAzimuthUpper() {
        return mRangeDataAoaAzimuthUpper;
    }

    @Nullable
    public Double getRangeDataAoaElevationLower() {
        return mRangeDataAoaElevationLower;
    }

    @Nullable
    public Double getRangeDataAoaElevationUpper() {
        return mRangeDataAoaElevationUpper;
    }
}
