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

/**
 * Estimate of range and bearing returned by Finder. This is in 1:1 correspondence with
 * location.bluemoon.Estimate proto. This class is usually populated from the native side.
 */
public class Estimate {

    private Status status;

    private double rangeM;

    private double rangeErrorStdDevM;

    // The bearing is with respect to the device Y-axis, positive ccw.
    private double bearingRad;

    private double bearingErrorStdDevRad;

    private double estimatedBeaconPositionErrorStdDevM;

    private long timestampNanos;

    /** Create an "empty" estimate. */
    public Estimate() {
        status = Status.UNKNOWN_ERROR;
        rangeM = 0.0;
        rangeErrorStdDevM = 0.0;
        bearingRad = 0.0;
        bearingErrorStdDevRad = 0.0;
        estimatedBeaconPositionErrorStdDevM = 0.0;
        timestampNanos = 0;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setRangeM(double rangeM) {
        this.rangeM = rangeM;
    }

    public void setRangeErrorStdDevM(double rangeErrorStdDevM) {
        this.rangeErrorStdDevM = rangeErrorStdDevM;
    }

    public void setBearingRad(double bearingRad) {
        this.bearingRad = bearingRad;
    }

    public void setBearingErrorStdDevRad(double bearingErrorStdDevRad) {
        this.bearingErrorStdDevRad = bearingErrorStdDevRad;
    }

    public void setEstimatedBeaconPositionErrorStdDevM(double estimatedBeaconPositionErrorStdDevM) {
        this.estimatedBeaconPositionErrorStdDevM = estimatedBeaconPositionErrorStdDevM;
    }

    public void setTimestampNanos(long timestampNanos) {
        this.timestampNanos = timestampNanos;
    }

    public Status getStatus() {
        return status;
    }

    public double getRangeM() {
        return rangeM;
    }

    public double getRangeErrorStdDevM() {
        return rangeErrorStdDevM;
    }

    /** The bearing is with respect to the device Y-axis, positive ccw. */
    public double getBearingRad() {
        return bearingRad;
    }

    public double getBearingErrorStdDevRad() {
        return bearingErrorStdDevRad;
    }

    public double getEstimatedBeaconPositionErrorStdDevM() {
        return estimatedBeaconPositionErrorStdDevM;
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }
}