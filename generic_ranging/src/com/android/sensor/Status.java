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

package com.android.sensor;

public enum Status {
    /** An estimate was successfully computed. */
    OK,
    /** Could not produce an estimate. For example, no synchronized set of data is available. */
    ESTIMATE_NOT_AVAILABLE,
    /** The filter has diverged and is attempting to recover. */
    RECOVERING,
    /**
     * Tracking failed due to insufficient light. This can occur when using camera based odometry.
     * The
     * filter will automatically recover and produce an estimate when possible.
     */
    RECOVERING_FROM_FAILURE_DUE_TO_INSUFFICIENT_LIGHT,
    /**
     * Tracking failed due to excessive motion. The filter will automatically recover and produce an
     * estimate when possible.
     */
    RECOVERING_FROM_FAILURE_DUE_TO_EXCESSIVE_MOTION,
    /**
     * Tracking failed due to insufficient features in the camera images. This can occur when using
     * camera based odometry. The filter will automatically recover and produce an estimate when
     * possible.
     */
    RECOVERING_FROM_FAILURE_DUE_TO_INSUFFICIENT_FEATURES,
    /**
     * Tracking failed because something else is using the camera. Tracking will recover
     * automatically, but with a new origin.
     */
    RECOVERING_FROM_FAILURE_DUE_TO_CAMERA_UNAVAILABILITY,
    /**
     * Tracking failed due to a bad odometry state. The filter will automatically recover and
     * produce
     * an estimate when possible.
     */
    RECOVERING_FROM_FAILURE_DUE_TO_BAD_ODOMETRY_STATE,
    /** Odometry failed and cannot be recovered. */
    ODOMETRY_ERROR,
    /** The beacon is probably moving, and so cannot be tracked. */
    BEACON_MOVING_ERROR,
    /** The configuration file contains an error and Finder can't be started. */
    CONFIGURATION_ERROR,
    /** Permissions not granted to required sensors. */
    SENSOR_PERMISSION_DENIED_ERROR,
    UNKNOWN_ERROR,
}