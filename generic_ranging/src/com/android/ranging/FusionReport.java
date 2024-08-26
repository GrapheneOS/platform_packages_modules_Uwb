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

import com.android.sensor.Estimate;
import com.android.sensor.Status;

import com.google.auto.value.AutoValue;

/**
 * Fusion data represents a fusion of data received from ranging technologies and data received from
 * other sensors such as ArCore and IMU.
 */
@AutoValue
public abstract class FusionReport {

    /** Returns distance result from fusion in meters. */
    public abstract double getFusionRange();

    /** Returns standard dev error for distance range. */
    public abstract double getFusionRangeErrorStdDev();

    /**
     * Returns the std dev of the error in the estimate of the beacon's position relative to the
     * user.
     */
    public abstract double getFusionEstimatedBeaconPositionErrorStdDevM();

    /** Returns bearing result from fusion in radians. */
    public abstract double getFusionBearing();

    /** Returns standard dev error for bearing. */
    public abstract double getFusionBearingErrorStdDev();

    /** Returns the state of ArCore. */
    public abstract ArCoreState getArCoreState();

    /** Returns a builder for {@link FusionReport}. */
    public static Builder builder() {
        return new AutoValue_FusionReport.Builder();
    }

    /** Builder for {@link FusionReport}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setFusionRange(double value);

        public abstract Builder setFusionRangeErrorStdDev(double value);

        public abstract Builder setFusionBearing(double value);

        public abstract Builder setFusionBearingErrorStdDev(double value);

        public abstract Builder setFusionEstimatedBeaconPositionErrorStdDevM(double value);

        public abstract Builder setArCoreState(ArCoreState arCoreState);

        public abstract FusionReport build();
    }

    public static FusionReport fromFusionAlgorithmEstimate(Estimate estimate) {
        return FusionReport.builder()
                .setFusionRange(estimate.getRangeM())
                .setFusionRangeErrorStdDev(estimate.getRangeErrorStdDevM())
                .setFusionBearing(estimate.getBearingRad())
                .setFusionBearingErrorStdDev(estimate.getBearingErrorStdDevRad())
                .setArCoreState(convertToArCoreStateFromStatus(estimate.getStatus()))
                .setFusionEstimatedBeaconPositionErrorStdDevM(
                        estimate.getEstimatedBeaconPositionErrorStdDevM())
                .build();
    }

    private static ArCoreState convertToArCoreStateFromStatus(Status status) {
        switch (status) {
            case OK:
                return ArCoreState.OK;
            case RECOVERING_FROM_FAILURE_DUE_TO_INSUFFICIENT_LIGHT:
                return ArCoreState.POOR_LIGHTNING;
            case RECOVERING_FROM_FAILURE_DUE_TO_EXCESSIVE_MOTION:
                return ArCoreState.EXCESSIVE_MOTION;
            case RECOVERING_FROM_FAILURE_DUE_TO_INSUFFICIENT_FEATURES:
                return ArCoreState.INSUFFICIENT_FEATURES;
            case RECOVERING_FROM_FAILURE_DUE_TO_CAMERA_UNAVAILABILITY:
                return ArCoreState.CAMERA_UNAVAILABLE;
            case ESTIMATE_NOT_AVAILABLE:
            case RECOVERING:
            case RECOVERING_FROM_FAILURE_DUE_TO_BAD_ODOMETRY_STATE:
            case ODOMETRY_ERROR:
            case BEACON_MOVING_ERROR:
            case CONFIGURATION_ERROR:
            case SENSOR_PERMISSION_DENIED_ERROR:
            case UNKNOWN_ERROR:
                return ArCoreState.BAD_STATE;
        }
        return ArCoreState.BAD_STATE;
    }

    /** State of ArCore */
    public enum ArCoreState {
        OK,
        BAD_STATE,
        POOR_LIGHTNING,
        EXCESSIVE_MOTION,
        INSUFFICIENT_FEATURES,
        CAMERA_UNAVAILABLE,
        NOT_ENABLED
    }
}
