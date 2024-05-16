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
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.ranging.generic.proto.MultiSensorFinderConfig;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * A MultiSensorFinder implementation that uses ARCore as the source of odometry.
 *
 * <p>The ArCoreMultiSensorFinder can be in two states: stopped and started.
 *
 * <p>Finder can transition between the started and stopped state via the start() and stop()
 * methods. Any unrecoverable error or calling the delete method will transition finder to the
 * stopped state. Every time finder enters the stopped state it wipes the state and the algorithm
 * starts from scratch on a subsequent start. If finder encounters a recoverable error, it will
 * return a RECOVERING_FROM_* status, in which case finder will not enter the stopped state and will
 * continually provide feedback to the client on how to help it recover (e.g. the room is too dark).
 * Once finder recovers, it will start finding the beacon from scratch.
 *
 * <p>All algo work and listener processing (except ARCore odometry which has its own thread)
 * happens in a single thread, which can be user provided.
 */
public class ArCoreMultiSensorFinder implements AndroidMultiSensorFinderInterface {

    private static final String TAG = ArCoreMultiSensorFinder.class.getSimpleName();
    private static final int DEFAULT_ODOMETRY_POLLING_RATE_HZ = 10;

    private static final long INVALID_POINTER_ADDRESS = 0;

    /**
     * ARCore's checkAvailability method can return UNKNOWN_CHECKING, meaning that it is still
     * waiting
     * on network to figure out if ARCore is available for a particular device. In that case, we
     * will
     * wait 200 milliseconds before trying again.
     */
    private static final long CHECK_AVAILABILITY_RETRY_DELAY_DURATION = 200;

    /**
     * How long we will wait for ARCore's checkAvailability to return a result before we give up
     * .
     */
    private static final Duration CHECK_AVAILABILITY_RETRY_TIMEOUT_DURATION = Duration.ofSeconds(2);

    /**
     * The thread in which all work (except ARCore work) is done if the user did not provide a
     * handler.
     */
    @Nullable
    private HandlerThread mHandlerThread;

    /**
     * Handler to the thread in which all work (except ARCore work) is done. This can be provided by
     * the user during construction.
     */
    private Handler mProcessingThreadHandler;

    /**
     * A variable for controlling ARCore's requestInstall behaviour: On first call, ask the user to
     * install preqrequisites if required, and do not ask again on subsequent calls.
     */
    private boolean userRequestedInstall = false;

    private final MultiSensorFinderConfig mConfig;

    // Used to indicate whether this class should run its own thread for processing, or use a
    // handler
    // that was provided by the client.
    private final boolean processInOwnThread;

    /**
     * Although all processing is done in a single thread, the user can still call stop/delete
     * methods
     * from any threads they wish. This mutex is used to guard against that.
     */
    private final Object mutex = new Object();

    private final long odometryPollingDelayMs;

    private final SessionWithArCoreNative sessionWithArCoreNative;

    // Tracks whether the odometry polling runnable is running or not. This is used to guard against
    // the case where a stop and start are called in rapid succession, and the previous odometry
    // polling runnable did not have time to stop.
    @GuardedBy("mutex")
    private boolean odometryPollingRunnableIsActive = false;

    /**
     * To avoid generating garbage, native methods will populate their results in this member
     * variable.
     */
    @GuardedBy("mutex")
    private Estimate latestEstimate = new Estimate();

    /** Check whether finder has been started. */
    @GuardedBy("mutex")
    private boolean started = false;

    @GuardedBy("mutex")
    private long nativeSessionPointer = INVALID_POINTER_ADDRESS;

    @GuardedBy("mutex")
    private final List<MultiSensorFinderListener> listeners = new ArrayList<>();

    // A runnable that polls odometry and feeds it to the algorithm to generate an Estimate. This
    // runnable is executed repeatedly at odometryPollingDelayMs.
    private final Runnable pollAndProcessOdometryInLooperRunnable =
            this::pollAndProcessOdometryInLooper;

    // This executor is used to execute the checkAvailability method, which may take up to
    // CHECK_AVAILABILITY_RETRY_TIMEOUT_DURATION to execute.
    private final ExecutorService executorService;

    /**
     * Constructs ArCoreMultiSensorFinder with a user provided handler to which all algorithm and
     * listener processing tasks will be submitted.
     */
    public ArCoreMultiSensorFinder(
            MultiSensorFinderConfig config,
            Handler processingThreadHandler,
            SessionWithArCoreNative sessionWithArCoreNative) {
        this.mConfig = config;
        this.executorService = Executors.newSingleThreadExecutor();
        this.sessionWithArCoreNative = sessionWithArCoreNative;

        if (processingThreadHandler == null) {
            processInOwnThread = true;
        } else {
            processInOwnThread = false;
            this.mHandlerThread = null;
            this.mProcessingThreadHandler = processingThreadHandler;
        }

        if (config.getOdometryPollingRateHz() > 0.0) {
            this.odometryPollingDelayMs = (long) (1000.0 / config.getOdometryPollingRateHz());
        } else {
            this.odometryPollingDelayMs = (long) (1000.0 / DEFAULT_ODOMETRY_POLLING_RATE_HZ);
        }
    }

    /**
     * Constructs ArCoreMultiSensorFinder which will create and use its own thread and associated
     * handler to which all algorithm and listener processing tasks will be submitted.
     */
    public ArCoreMultiSensorFinder(MultiSensorFinderConfig config) {
        this(config, null, new SessionWithArCoreNative());
    }

    // For testing purposes only. A constructor for passing in a mocked SessionWithArCoreNative.
    @VisibleForTesting
    public ArCoreMultiSensorFinder(
            MultiSensorFinderConfig config,
            SessionWithArCoreNative sessionWithArCoreNative) {
        this(config, null, sessionWithArCoreNative);
    }

    @Override
    public FutureTask<Boolean> checkAvailability(Context context) {
        FutureTask<Boolean> future =
                new FutureTask<>(
                        () -> {
                            Instant startTime = Instant.now();
                            Instant deadline = startTime.plus(
                                    CHECK_AVAILABILITY_RETRY_TIMEOUT_DURATION);

//                            try {
//                                while (Instant.now().isBefore(deadline)) {
//                                    ArCoreApk.Availability availability =
//                                            ArCoreApk.getInstance().checkAvailability(context);
//                                    if (availability.isTransient()) {
//                                        // Wait until we get a response.
//                                        TimeUnit.MILLISECONDS.sleep
//                                        (CHECK_AVAILABILITY_RETRY_DELAY_DURATION);
//                                    } else {
//                                        return availability.isSupported();
//                                    }
//                                }
//                            } catch (InterruptedException e) {
//                                return false;
//                            }

                            Log.w(TAG, "Deadline expired while checking for availability");
                            return false;
                        });

        executorService.execute(future);
        return future;
    }

    @Override
    public InstallStatus requestInstall(Activity activity) {
//        try {
//            ArCoreApk.InstallStatus installStatus =
//                    ArCoreApk.getInstance().requestInstall(activity, !userRequestedInstall);
//            switch (installStatus) {
//                case INSTALLED:
//                    return InstallStatus.OK;
//                case INSTALL_REQUESTED:
//                    userRequestedInstall = true;
//                    return InstallStatus.USER_PROMPTED_TO_INSTALL_DEPENDENCIES;
//            }
//            return InstallStatus.UNKNOWN_ERROR;
//        } catch (UnavailableUserDeclinedInstallationException e) {
//            return InstallStatus.USER_DECLINED_TO_INSTALL_DEPENDENCIES;
//        } catch (UnavailableDeviceNotCompatibleException e) {
//            return InstallStatus.DEVICE_INCOMPATIBLE;
//        } catch (FatalException e) {
//            return InstallStatus.UNKNOWN_ERROR;
//        }
        return InstallStatus.UNKNOWN_ERROR;
    }

    @Override
    public Status start(Context context) {
        synchronized (mutex) {
            if (started) {
                return Status.OK;
            }

            // Create and start the processing thread if it has not already been started.
            if (processInOwnThread && this.mHandlerThread == null) {
                this.mHandlerThread = new HandlerThread("MultiSensorFinder");
                this.mHandlerThread.start();
                this.mProcessingThreadHandler = new Handler(this.mHandlerThread.getLooper());
            }

            // Create the session if it hasn't already been created.
            if (nativeSessionPointer == INVALID_POINTER_ADDRESS) {
                nativeSessionPointer = sessionWithArCoreNative.createSession(mConfig.toByteArray());
            }

            if (nativeSessionPointer == INVALID_POINTER_ADDRESS) {
                Log.w(TAG, "Could not create session.");
                return Status.UNKNOWN_ERROR;
            }

            // Start the session.
            Status status = sessionWithArCoreNative.start(nativeSessionPointer, context);
            if (status != Status.OK) {
                Log.w(TAG, "Could not start session: " + status);
                return status;
            }
            started = true;

            // Start odometry polling loop if it is not already running.
            if (!odometryPollingRunnableIsActive) {
                mProcessingThreadHandler.post(pollAndProcessOdometryInLooperRunnable);
                odometryPollingRunnableIsActive = true;
            }
        }

        return Status.OK;
    }

    @Override
    public void updateWithUwbMeasurement(double rangeMeters, long timestampNanos) {
        mProcessingThreadHandler.post(
                () -> updateWithUwbMeasurementInLooper(rangeMeters, timestampNanos));
    }

    @Override
    public void updateWithWifiRttMeasurement(
            double rangeMeters, double errorStdDevMeters, double rssiDbm, long timestampNanos) {
        mProcessingThreadHandler.post(
                () ->
                        updateWithWifiRttMeasurementInLooper(
                                rangeMeters, errorStdDevMeters, rssiDbm, timestampNanos));
    }

    @Override
    public void subscribeToEstimates(MultiSensorFinderListener listener) {
        synchronized (mutex) {
            listeners.add(listener);
        }
    }

    @Override
    public Status stop() {
        synchronized (mutex) {
            if (!started) {
                return Status.OK;
            }

            Status status = sessionWithArCoreNative.stop(nativeSessionPointer);
            if (status != Status.OK) {
                Log.w(TAG, "Could not stop session: " + status);
            }
            started = false;
            return status;
        }
    }

    @Override
    public void delete() {
        synchronized (mutex) {
            if (nativeSessionPointer != INVALID_POINTER_ADDRESS) {
                Status status = stop();
                if (status != Status.OK) {
                    Log.w(TAG, "Could not stop session: " + status);
                }
                sessionWithArCoreNative.deleteSession(nativeSessionPointer);
                nativeSessionPointer = INVALID_POINTER_ADDRESS;
            }

            if (mHandlerThread != null) {
                mHandlerThread.quit();
                mHandlerThread = null;
            }
        }
    }

    @GuardedBy("mutex")
    private void publishEstimate(Estimate estimate) {
        for (MultiSensorFinderListener listener : listeners) {
            listener.onUpdatedEstimate(estimate);
        }
    }

    // A method that polls odometry at regular intervals and uses to it generate Estimates. This
    // is intended to be called with other processing functions in one looper to avoid issues with
    // multi-threading.
    private void pollAndProcessOdometryInLooper() {
        synchronized (mutex) {
            if (!started) {
                odometryPollingRunnableIsActive = false;
                return;
            }

            // Attempt to update the estimate with new odometry data.
            sessionWithArCoreNative.pollAndProcessOdometryUpdate(nativeSessionPointer);
            sessionWithArCoreNative.getEstimate(nativeSessionPointer, latestEstimate);

            // Publish estimate to all listeners.
            if (latestEstimate.getStatus() != Status.ESTIMATE_NOT_AVAILABLE) {
                publishEstimate(latestEstimate);
            }

            checkForErrors(latestEstimate);
        }

        mProcessingThreadHandler.postDelayed(
                pollAndProcessOdometryInLooperRunnable, odometryPollingDelayMs);
    }

    private void updateWithUwbMeasurementInLooper(double rangeMeters, long timestampNanos) {
        synchronized (mutex) {
            if (!started) {
                return;
            }
            sessionWithArCoreNative.updateWithUwbMeasurement(
                    nativeSessionPointer, rangeMeters, timestampNanos);

            sessionWithArCoreNative.getEstimate(nativeSessionPointer, latestEstimate);
            if (latestEstimate.getStatus() != Status.ESTIMATE_NOT_AVAILABLE) {
                publishEstimate(latestEstimate);
            }

            checkForErrors(latestEstimate);
        }
    }

    private void updateWithWifiRttMeasurementInLooper(
            double rangeMeters, double errorStdDevMeters, double rssiDbm, long timestampNanos) {
        synchronized (mutex) {
            if (!started) {
                return;
            }
            sessionWithArCoreNative.updateWithWifiRttMeasurement(
                    nativeSessionPointer, rangeMeters, errorStdDevMeters, rssiDbm, timestampNanos);

            sessionWithArCoreNative.getEstimate(nativeSessionPointer, latestEstimate);
            if (latestEstimate.getStatus() != Status.ESTIMATE_NOT_AVAILABLE) {
                publishEstimate(latestEstimate);
            }

            checkForErrors(latestEstimate);
        }
    }

    /**
     * Checks if the latest estimate indicated an error, and transitions finder to stopped state if
     * so.
     */
    @GuardedBy("mutex")
    private void checkForErrors(Estimate estimate) {
        // If we got anything but OK, ESTIMATE_NOT_AVAILABLE, or RECOVERING_*, that means there
        // was an
        // error, and we should quit.
        if (estimate.getStatus() == Status.OK
                || estimate.getStatus() == Status.ESTIMATE_NOT_AVAILABLE
                || estimate.getStatus() == Status.RECOVERING_FROM_FAILURE_DUE_TO_INSUFFICIENT_LIGHT
                || estimate.getStatus() == Status.RECOVERING_FROM_FAILURE_DUE_TO_EXCESSIVE_MOTION
                || estimate.getStatus()
                == Status.RECOVERING_FROM_FAILURE_DUE_TO_INSUFFICIENT_FEATURES
                || estimate.getStatus()
                == Status.RECOVERING_FROM_FAILURE_DUE_TO_CAMERA_UNAVAILABILITY
                || estimate.getStatus()
                == Status.RECOVERING_FROM_FAILURE_DUE_TO_BAD_ODOMETRY_STATE) {
            return;
        }
        Status unused = stop();
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    public void onActivityResumeFragments(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
        Status unused = stop();
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        delete();
    }

    // For test purposes only. The estimate object is updated on the native side, which is mocked
    // out
    // for tests.
    @VisibleForTesting
    public void setEstimate(Estimate estimate) {
        synchronized (mutex) {
            latestEstimate = estimate;
        }
    }

    @VisibleForTesting
    public boolean isStarted() {
        synchronized (mutex) {
            return started;
        }
    }
}
