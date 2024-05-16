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

import android.app.Activity;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;

import java.util.concurrent.FutureTask;

/**
 * Precision Finder fuses range measurements from UWB, Wifi-RTT, and BLE HADM etc. with odometry to
 * provide a range and bearing to the beacon.
 *
 * <p>Sample Usage:
 *
 * <p><code>
 * // --- To create finder ---
 * MultiSensorFinder finder = new MultiSensorFinder(config, args, ...);
 * finder.SubscribeToEstimates("name_of_user", listener);
 *
 * // --- Checking availability of finder ---
 * // IMPORTANT: Before calling start, please make that it is possible to run a particular finder
 * // implementation on the device, and that the finder implementation has all prerequisites met:
 * if (!finder.checkAvailability(context)) {
 * return;  // Can't use finder here.
 * }
 *
 * switch (finder.requestInstall(activity)) {
 * case USER_PROMPTED_TO_INSTALL_DEPENDENCIES:
 * // The activity was switched and the user was requested to install dependencies.
 * return;
 * case USER_DECLINED_TO_INSTALL_DEPENDENCIES:
 * // User declined to install dependencies.
 * return;
 * case DEVICE_INCOMPATIBLE:
 * break;
 * case OK:
 * // Everything is good to go!
 * break;
 * }
 *
 * // --- Starting finder --- //
 * finder.Start();
 *
 * // --- Providing data to finder --- //
 * finder.updateWithUwbMeasurement(rangeMeters, timestampNanos);
 * finder.updateWithWifiRTTMeasurement(rangeMeters, errorStdDevMeters, rssiDbm, timestampNanos);
 * ...
 *
 * // --- Destroying finder --- //
 * finder.stop();
 * finder.delete();
 * </code>
 *
 * <p>Please note that finder should be stopped when the activity in which it is running is switched
 * to the background, so that all sensor streams that finder is using are stopped. This can be done
 * in two ways:
 *
 * <ul>
 *   <li>Manually call finder.stop() in your activity's onPause method.
 *   <li>Call getApplication().registerActivityLifecycleCallbacks(finder) in your activity's
 *       onCreate, and finder will automatically call stop when the application goes to bg.
 * </ul>
 *
 * Additionally, finder.delete() should be called when the application is destroyed. Again, this can
 * be manually done in your applications onDestroy, or you can register finder to the activity's
 * ActivityLifecycleCallbacks to do this for you automatically.
 */
public interface AndroidMultiSensorFinderInterface extends ActivityLifecycleCallbacks {

    /**
     * Checks if the device meets the requirements for running precision finder e.g. it has all the
     * sensors etc.
     */
    FutureTask<Boolean> checkAvailability(Context context);

    /**
     * Checks if all dependencies have been installed, and if not, switch the activity and prompt
     * the
     * user to install them.
     */
    InstallStatus requestInstall(Activity activity);

    /**
     * Resets underlying variables, and starts odometry. Once started, MultiSensorFinder will accept
     * measurements.
     *
     * <p>If MultiSensorFinder is already started, this method is a no-op.
     *
     * @return Status.OK if successful, and Status.ERROR_* otherwise.
     */
    Status start(Context context);

    /**
     * Stops producing estimates. Once stopped, MultiSensorFinder cannot accept measurements.
     *
     * <p>If already stopped, this method is a no-op.
     *
     * <p>@return Status.OK if successful, and Status.ERROR_* otherwise.
     */
    Status stop();

    /**
     * Adds a UWB measurement, which will be fused with other data to produce an estimate.
     *
     * <p>If MultiSensorFinder is stopped, this method is a no-op.
     *
     * <p>Note: The Android stack does not provide low level information on the received UWB
     * signal,
     * e.g. the number of peaks in the impulse response, or how much multipath is in the
     * environment.
     * It's not clear if the Fira API exposes this information, but it might be useful.
     *
     * @param rangeMeters    The range measurement from UWB.
     * @param timestampNanos The timestamp in nanoseconds associated with the measurement.
     */
    void updateWithUwbMeasurement(double rangeMeters, long timestampNanos);

    /**
     * Adds a Wifi-RTT measurement, which will be fused with other data to produce an estimate.
     *
     * <p>If MultiSensorFinder is stopped, this method is a no-op.
     *
     * @param rangeMeters       The range measurement from Wifi-RTT.
     * @param errorStdDevMeters The error bounds on the range measurement.
     * @param rssiDbm           Beacon to finder signal strength.
     * @param timestampNanos    The timestamp in nanoseconds associated with the measurement.
     */
    void updateWithWifiRttMeasurement(
            double rangeMeters, double errorStdDevMeters, double rssiDbm, long timestampNanos);

    /**
     * Adds a subscriber that will be notified when a new Estimate is available. Note that Finder
     * will
     * not generate any estimates if it is stopped.
     *
     * @param listener The subscriber that will be registered.
     */
    void subscribeToEstimates(MultiSensorFinderListener listener);

    /**
     * Frees all native resources. This should be called when the application is destroyed, unless
     * this class is registered to ActivityLifecycleCallbacks of the application using it, in which
     * case it will be called automatically on the application's onDestroy.
     */
    void delete();
}
