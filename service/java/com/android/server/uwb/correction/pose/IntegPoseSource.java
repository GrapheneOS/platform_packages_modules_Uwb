/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.uwb.correction.pose;

import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;

import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.signum;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.Vector3;

import java.security.InvalidParameterException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Provides poses by double-integrating the accelerometer.  It is hilariously bad with ordinary
 * accelerometers.
 * The rotation sensor is used for rotation.
 *
 * Use this pose source if your device has a military-grade accelerometer, or build upon this
 * class to research better double-integration technology such as AI-based double integration, or
 * ARCore pose tracking.
 */
public class IntegPoseSource extends PoseSourceBase implements SensorEventListener {
    private static final String TAG = "IntegPoseSource";
    private final SensorManager mSensorManager;
    private final Sensor mRotationSensor;
    private final Sensor mAccelSensor;
    private final int mInterval;
    private Vector3 mAccelCal = new Vector3(0, 0, 0);
    private Vector3 mPosition = new Vector3(0, 0, 0);
    private Vector3 mSpeed = new Vector3(0, 0, 0);
    private long mLastUpdate;

    /** how much drift from origin before position is reset to the origin. In meters. */
    final int mPosResetDistance = 20;
    final float mSpeedDamp = 0.95F; // Speed coefficient (1=no slowing, 0.999F=some slowing)
    final float mPosDamp = 0.999F; // Position recentering (1=no recentering, 0.999F=some)
    final float mCalRate = 0.002F; // How quickly calibration adjusts to accel changes.

    // The local system is oriented with Y up.  The Android rotation vector has Z up. Pitching down
    // will correct this.
    private final Quaternion mRotator = Quaternion.yawPitchRoll(0, -F_HALF_PI, 0);

    /**
     * Creates a new instance of the IntegPoseSource
     * @param intervalMs How frequently to update the pose.
     */
    public IntegPoseSource(@NonNull Context context, int intervalMs) {
        Objects.requireNonNull(context);
        if (intervalMs < MIN_INTERVAL_MS || intervalMs > MAX_INTERVAL_MS) {
            throw new InvalidParameterException("Invalid interval.");
        }
        mSensorManager = context.getSystemService(SensorManager.class);
        mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (mRotationSensor == null) {
            throw new UnsupportedOperationException(
                    "Device does not support the required rotation vector sensors.");
        }
        mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (mAccelSensor == null) {
            throw new UnsupportedOperationException(
                    "Device does not support the required linear acceleration sensors."
            );
        }
        mInterval = intervalMs * 1000;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void start() {
        mSensorManager.registerListener(this, mRotationSensor, mInterval);
        mSensorManager.registerListener(this, mAccelSensor, mInterval);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void stop() {
        mSensorManager.unregisterListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            if (mLastUpdate == 0) {
                mLastUpdate = Instant.now().toEpochMilli();
                return;
            }
            long now = Instant.now().toEpochMilli();
            float dur =  (now - mLastUpdate) / 1000.0F;
            mLastUpdate = now;
            Vector3 accel = new Vector3(
                    event.values[0] - mAccelCal.x,
                    event.values[1] - mAccelCal.y,
                    event.values[2] - mAccelCal.z
            );
            mAccelCal = new Vector3(
                    mAccelCal.x + min(abs(accel.x), mCalRate) * signum(accel.x),
                    mAccelCal.y + min(abs(accel.y), mCalRate) * signum(accel.y),
                    mAccelCal.z + min(abs(accel.z), mCalRate) * signum(accel.z)
            );
            mSpeed = new Vector3(
                    (mSpeed.x + accel.x * dur) * mSpeedDamp,
                    (mSpeed.y + accel.y * dur) * mSpeedDamp,
                    (mSpeed.z + accel.z * dur) * mSpeedDamp
            );
            mPosition = new Vector3(
                    (mPosition.x + mSpeed.x * dur) * mPosDamp,
                    (mPosition.y + mSpeed.y * dur) * mPosDamp,
                    (mPosition.z + mSpeed.z * dur) * mPosDamp
            );
            if (mPosition.lengthSquared() > mPosResetDistance * mPosResetDistance) {
                mPosition = Vector3.ORIGIN;
            }
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            Quaternion base = new Quaternion(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.values[3]
            );
            publish(new Pose(mPosition, Quaternion.multiply(mRotator, base)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged() $sensor");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public EnumSet<Capabilities> getCapabilities() {
        return Capabilities.ALL;
    }
}
