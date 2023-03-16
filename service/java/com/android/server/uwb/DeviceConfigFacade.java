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

package com.android.server.uwb;

import android.content.Context;
import android.os.Handler;
import android.provider.DeviceConfig;
import android.util.Log;

import com.android.uwb.resources.R;

/**
 * This class allows getting all configurable flags from DeviceConfig.
 */
public class DeviceConfigFacade {
    private static final String LOG_TAG = DeviceConfigFacade.class.getSimpleName();

    /**
     */
    private static final int MAX_FOV = 180;

    public static final int DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS = 5_000;
    private static final int MS_IN_HOUR = 60 * 60 * 1000;
    public static final int DEFAULT_BUG_REPORT_MIN_INTERVAL_MS = 24 * MS_IN_HOUR;
    private static final String TAG = "DeviceConfigFacadeUwb";

    public enum PoseSourceType {
        NONE,
        ROTATION_VECTOR,
        GYRO,
        SIXDOF,
        DOUBLE_INTEGRATE,
    }

    private final Context mContext;

    // Cached values of fields updated via updateDeviceConfigFlags()
    private int mRangingResultLogIntervalMs;
    private boolean mDeviceErrorBugreportEnabled;
    private int mBugReportMinIntervalMs;
    private boolean mEnableFilters;
    private int mFilterDistanceInliersPercent;
    private int mFilterDistanceWindow;
    private int mFilterAngleInliersPercent;
    private int mFilterAngleWindow;
    private PoseSourceType mPoseSourceType;
    private boolean mEnablePrimerEstElevation;
    private boolean mEnablePrimerAoA;
    private boolean mEnablePrimerFov;
    private int mPrimerFovDegree;
    private int mPredictionTimeoutSeconds;

    public DeviceConfigFacade(Handler handler, Context context) {
        mContext = context;

        updateDeviceConfigFlags();
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_UWB,
                command -> handler.post(command),
                properties -> {
                    updateDeviceConfigFlags();
                });
    }

    private void updateDeviceConfigFlags() {
        String poseSourceName;
        mRangingResultLogIntervalMs = DeviceConfig.getInt(DeviceConfig.NAMESPACE_UWB,
                "ranging_result_log_interval_ms", DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS);
        mDeviceErrorBugreportEnabled = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_UWB,
                "device_error_bugreport_enabled", false);
        mBugReportMinIntervalMs = DeviceConfig.getInt(DeviceConfig.NAMESPACE_UWB,
                "bug_report_min_interval_ms", DEFAULT_BUG_REPORT_MIN_INTERVAL_MS);

        // Default values come from the overlay file.
        mEnableFilters = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_UWB,
                "enable_filters",
                mContext.getResources().getBoolean(R.bool.enable_filters)
        );
        mFilterDistanceInliersPercent = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_UWB,
                "filter_distance_inliers_percent",
                mContext.getResources().getInteger(R.integer.filter_distance_inliers_percent)
        );
        mFilterDistanceWindow = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_UWB,
                "filter_distance_window",
                mContext.getResources().getInteger(R.integer.filter_distance_window)
        );
        mFilterAngleInliersPercent = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_UWB,
                "filter_angle_inliers_percent",
                mContext.getResources().getInteger(R.integer.filter_angle_inliers_percent)
        );
        mFilterAngleWindow = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_UWB,
                "filter_angle_window",
                mContext.getResources().getInteger(R.integer.filter_angle_window)
        );
        poseSourceName = DeviceConfig.getString(
                DeviceConfig.NAMESPACE_UWB,
                "pose_source_type",
                mContext.getResources().getString(R.string.pose_source_type)
        );
        mEnablePrimerEstElevation = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_UWB,
                "enable_primer_est_elevation",
                mContext.getResources().getBoolean(R.bool.enable_primer_est_elevation)
        );
        mEnablePrimerAoA = DeviceConfig.getBoolean(
                DeviceConfig.NAMESPACE_UWB,
                "enable_primer_aoa",
                mContext.getResources().getBoolean(R.bool.enable_primer_aoa)
        );
        mPrimerFovDegree = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_UWB,
                "primer_fov_degrees",
                mContext.getResources().getInteger(R.integer.primer_fov_degrees)
        );
        mPredictionTimeoutSeconds = DeviceConfig.getInt(
                DeviceConfig.NAMESPACE_UWB,
                "prediction_timeout_seconds",
                mContext.getResources().getInteger(R.integer.prediction_timeout_seconds)
        );

        // A little parsing and cleanup:
        try {
            mPoseSourceType = PoseSourceType.valueOf(poseSourceName);
        } catch (IllegalArgumentException e) {
            mPoseSourceType = PoseSourceType.ROTATION_VECTOR;
            Log.e(LOG_TAG, "UWB pose source '" + poseSourceName + "' defined in flags or"
                    + "overlay file is invalid. Defaulting to " + mPoseSourceType.name());
        }
        mEnablePrimerFov = mPrimerFovDegree > 0 && mPrimerFovDegree < MAX_FOV;
    }

    /**
     * Gets ranging result logging interval in ms
     */
    public int getRangingResultLogIntervalMs() {
        return mRangingResultLogIntervalMs;
    }

    /**
     * Gets the feature flag for reporting device error
     */
    public boolean isDeviceErrorBugreportEnabled() {
        return mDeviceErrorBugreportEnabled;
    }

    /**
     * Gets minimum wait time between two bug report captures
     */
    public int getBugReportMinIntervalMs() {
        return mBugReportMinIntervalMs;
    }

    /**
     * Gets the flag for enabling UWB filtering.
     */
    public boolean isEnableFilters() {
        return mEnableFilters;
    }

    /**
     * Gets the percentage (0-100) of inliers to be used in the distance filter cut.
     */
    public int getFilterDistanceInliersPercent() {
        return mFilterDistanceInliersPercent;
    }

    /**
     * Gets the size of the distance filter moving window.
     */
    public int getFilterDistanceWindow() {
        return mFilterDistanceWindow;
    }

    /**
     * Gets the percentage (0-100) of inliers to be used inthe angle filter cut.
     */
    public int getFilterAngleInliersPercent() {
        return mFilterAngleInliersPercent;
    }

    /**
     * Gets the size of the angle filter moving window.
     */
    public int getFilterAngleWindow() {
        return mFilterAngleWindow;
    }

    /**
     * Gets the type of pose source that should be used by default.
     */
    public PoseSourceType getPoseSourceType() {
        return mPoseSourceType;
    }

    /**
     * Gets the flag that enables the elevation estimation primer.
     */
    public boolean isEnablePrimerEstElevation() {
        return mEnablePrimerEstElevation;
    }

    /**
     * Gets the flag that enables the primer that converts AoA to spherical coordinates.
     */
    public boolean isEnablePrimerAoA() {
        return mEnablePrimerAoA;
    }

    /**
     * Gets a value indicating if the FOV primer should be enabled.
     */
    public boolean isEnablePrimerFov() {
        return mEnablePrimerFov;
    }

    /**
     * Gets the configured field of view.
     */
    public int getPrimerFovDegree() {
        return mPrimerFovDegree;
    }

    /**
     * Gets how long to replace reports with an error status with predicted reports in seconds.
     */
    public int getPredictionTimeoutSeconds() {
        return mPredictionTimeoutSeconds;
    }
}
