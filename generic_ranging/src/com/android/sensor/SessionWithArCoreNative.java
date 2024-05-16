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

import android.content.Context;

/** This class contains the Jave methods corresponding to Finder native APIs. */
public class SessionWithArCoreNative {
    /**
     * The native library is loaded in the constructor so that the load can be mocked out in tests.
     */
    public SessionWithArCoreNative() {
        System.loadLibrary("precisionfindingsessionwitharcorejni");
    }

    /**
     * Creates the session. The memory allocated by this method must be freed by calling
     * deleteSession.
     *
     * @return A non-zero value if the session was successfully created, zero otherwise.
     */
    public native long createSession(byte[] config);

    /**
     * Starts consuming sensor data streams. Once started, the session can be provided with range
     * measurements to produce estimates.
     *
     * <p>IMPORTANT: Before calling start, the user must ensure that ARCore is available, and all
     * prerequisites have been installed. This is done by using the checkAvailability and
     * requestInstall methods.
     * https://developers.google.com/ar/reference/java/com/google/ar/core/ArCoreApk
     */
    public native Status start(long sessionPointer, Context context);

    /**
     * Stops consuming all sensor data streams. Calling start after stop will start the session from
     * scratch.
     */
    public native Status stop(long sessionPointer);

    /**
     * Polls and uses the latest odometry from ARCore. The result of this call can be obtained by
     * calling getEstimate.
     */
    public native void pollAndProcessOdometryUpdate(long sessionPointer);

    /**
     * Forwards a UWB measurement to the underlying estimator. The result of this call can be
     * obtained
     * by calling getEstimate.
     */
    public native void updateWithUwbMeasurement(
            long sessionPointer, double range, long timestampNanos);

    /**
     * Forwards a Wifi-RTT measurement to the underlying estimator. The result of this call can be
     * obtained by calling getEstimate.
     */
    public native void updateWithWifiRttMeasurement(
            long sessionPointer, double rangeM, double stdDevM, double rssi, long timestampNanos);

    /**
     * Returns the result of the latest call to pollAndProcessOdometryUpdate,
     * updateWithUwbMeasurement, or updateWithWifiRttMeasurement.
     */
    public native void getEstimate(long sessionPointer, Estimate estimate);

    /** Frees all native memory allocated by this session. */
    public native void deleteSession(long sessionPointer);
}
