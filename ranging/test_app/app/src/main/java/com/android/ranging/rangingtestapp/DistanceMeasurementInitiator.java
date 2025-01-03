/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ranging.rangingtestapp;

import static android.ranging.RangingSession.*;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.CancellationSignal;
import android.ranging.RangingCapabilities;
import android.ranging.RangingConfig;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.SessionConfig;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.util.Pair;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

class DistanceMeasurementInitiator {
    private final RangingManager mRangingManager;
    private final LoggingListener mLoggingListener;

    private final Context mApplicationContext;
    private final Executor mExecutor;
    private final DistanceMeasurementCallback mDistanceMeasurementCallback;
    @Nullable private RangingSession mSession = null;
    @Nullable private CancellationSignal mCancellationSignal = null;
    @Nullable private AtomicReference<RangingCapabilities> mRangingCapabilities =
            new AtomicReference<>();
    @Nullable private BluetoothDevice mTargetDevice = null;

    DistanceMeasurementInitiator(
            Context applicationContext,
            DistanceMeasurementCallback distanceMeasurementCallback,
            com.android.ranging.rangingtestapp.LoggingListener loggingListener) {
        mApplicationContext = applicationContext;
        mDistanceMeasurementCallback = distanceMeasurementCallback;
        mLoggingListener = loggingListener;

        mRangingManager = mApplicationContext.getSystemService(RangingManager.class);
        mExecutor = Executors.newSingleThreadExecutor();

        mRangingManager.registerCapabilitiesCallback(mExecutor, (capabilities -> {
            mRangingCapabilities.set(capabilities);
        }));
    }

    void setTargetDevice(BluetoothDevice targetDevice) {
        mTargetDevice = targetDevice;
    }

    private void printLog(String log) {
        mLoggingListener.onLog(log);
    }

    private String getRangingTechnologyName(int technology) {
        for (RangingParameters.Technology tech: RangingParameters.Technology.values()) {
            if (tech.getTechnology() == technology) {
                return tech.toString();
            }
        }
        throw new IllegalArgumentException("unknown technology " + technology);
    }

    private int getRangingTechnologyId(String technology) {
        for (RangingParameters.Technology tech: RangingParameters.Technology.values()) {
            if (tech.toString().equals(technology)) {
                return tech.getTechnology();
            }
        }
        throw new IllegalArgumentException("unknown technology " + technology);
    }

    @SuppressLint("MissingPermission") // permissions are checked upfront
    List<String> getSupportedTechnologies() {
        List<String> methods = new ArrayList<>();
        if (mRangingCapabilities.get() == null) return methods;
        Map<Integer, Integer> technologyAvailability =
                mRangingCapabilities.get().getTechnologyAvailability();

        StringBuilder dbgMessage = new StringBuilder("getRangingTechnologys: ");
        for (Map.Entry<Integer, Integer> techAvailability : technologyAvailability.entrySet()) {
            if (techAvailability.getValue().equals(RangingCapabilities.ENABLED)) {
                String methodName = getRangingTechnologyName(techAvailability.getKey());
                dbgMessage.append(methodName).append(", ");
                methods.add(methodName);
            }
        }
        printLog(dbgMessage.toString());
        return methods;
    }

    List<String> getMeasurementFreqs() {
        return List.of(RangingParameters.Freq.MEDIUM.toString(),
                RangingParameters.Freq.HIGH.toString(),
                RangingParameters.Freq.LOW.toString());
    }

    List<String> getMeasureDurationsInIntervalRounds() {
        return List.of("10000", "1000", "100", "10", "5");
    }

    @SuppressLint("MissingPermission") // permissions are checked upfront
    void startDistanceMeasurement(
            String rangingTechnologyName, String freqName, int duration) {
        if (mTargetDevice == null) {
            printLog("Please connect the device over Gatt first");
            return;
        }
        printLog("Start ranging with device: " + mTargetDevice.getName());
        mSession = mRangingManager.createRangingSession(
                Executors.newSingleThreadExecutor(), mRangingSessionCallback);
        mCancellationSignal = mSession.start(
                RangingParameters.createInitiatorRangingPreference(
                        rangingTechnologyName, freqName, duration, mTargetDevice));
    }

    void stopDistanceMeasurement() {
        if (mSession == null || mCancellationSignal == null) {
            return;
        }
        mCancellationSignal.cancel();
        mSession = null;
        mCancellationSignal = null;
    }

    private RangingSession.Callback mRangingSessionCallback =
            new RangingSession.Callback() {

                public void onOpened() {
                    printLog("DistanceMeasurement onOpened! ");
                }

                public void onOpenFailed(int reason) {
                    printLog("DistanceMeasurement onOpenFailed! " + reason);
                    mDistanceMeasurementCallback.onStartFail();
                }

                public void onStarted(RangingDevice peer, int technology) {
                    printLog("DistanceMeasurement onStarted ! ");
                    mDistanceMeasurementCallback.onStartSuccess();
                }

                public void onStopped(RangingDevice peer, int technology) {
                    printLog("DistanceMeasurement onStopped! " + technology);
                    mDistanceMeasurementCallback.onStop();
                }

                public void onClosed(int reason) {
                    printLog("DistanceMeasurement onClosed! " + reason);
                    mDistanceMeasurementCallback.onStop();
                }

                public void onResults(RangingDevice peer, RangingData data) {
                    printLog(
                            "DistanceMeasurement onResults ! " + peer + ": " + data);
                    mDistanceMeasurementCallback.onDistanceResult(data.getDistance().getMeasurement());
                }
            };

    interface DistanceMeasurementCallback {

        void onStartSuccess();

        void onStartFail();

        void onStop();

        void onDistanceResult(double distanceMeters);
    }
}
