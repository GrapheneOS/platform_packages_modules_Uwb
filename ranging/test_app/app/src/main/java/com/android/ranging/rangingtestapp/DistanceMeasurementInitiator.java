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

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.CancellationSignal;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;

import androidx.annotation.Nullable;

import com.android.ranging.rangingtestapp.RangingParameters.Technology;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class DistanceMeasurementInitiator {
    private final RangingManager mRangingManager;
    private final LoggingListener mLoggingListener;

    private final Context mApplicationContext;
    private final BleConnectionCentralViewModel mBleConnectionCentralViewModel;
    private final Executor mExecutor;
    private final DistanceMeasurementCallback mDistanceMeasurementCallback;
    @Nullable private RangingSession mSession = null;
    private AtomicReference<CancellationSignal> mCancellationSignal =
            new AtomicReference<>(null);
    private CountDownLatch mCapabilitiesCountDownLatch = new CountDownLatch(1);
    @Nullable private AtomicReference<RangingCapabilities> mRangingCapabilities =
            new AtomicReference<>();
    @Nullable private BluetoothDevice mTargetDevice = null;

    DistanceMeasurementInitiator(
            Context applicationContext,
            BleConnectionCentralViewModel bleConnectionCentralViewModel,
            DistanceMeasurementCallback distanceMeasurementCallback,
            com.android.ranging.rangingtestapp.LoggingListener loggingListener) {
        mApplicationContext = applicationContext;
        mBleConnectionCentralViewModel = bleConnectionCentralViewModel;
        mDistanceMeasurementCallback = distanceMeasurementCallback;
        mLoggingListener = loggingListener;

        mRangingManager = mApplicationContext.getSystemService(RangingManager.class);
        mExecutor = Executors.newSingleThreadExecutor();

        mRangingManager.registerCapabilitiesCallback(mExecutor, (capabilities -> {
            mRangingCapabilities.set(capabilities);
            mCapabilitiesCountDownLatch.countDown();
        }));
    }

    void setTargetDevice(BluetoothDevice targetDevice) {
        mTargetDevice = targetDevice;
        if (mTargetDevice == null) {
            stopDistanceMeasurement();
        }
    }

    private void printLog(String log) {
        mLoggingListener.log(log);
    }

    private String getRangingTechnologyName(int technology) {
        for (Technology tech: Technology.values()) {
            if (tech.getTechnology() == technology) {
                return tech.toString();
            }
        }
        throw new IllegalArgumentException("unknown technology " + technology);
    }

    @SuppressLint("MissingPermission") // permissions are checked upfront
    List<String> getSupportedTechnologies() {
        List<String> techs = new ArrayList<>();
        try {
            mCapabilitiesCountDownLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) { }
        if (mRangingCapabilities.get() == null) return techs;
        Map<Integer, Integer> technologyAvailability =
                mRangingCapabilities.get().getTechnologyAvailability();

        StringBuilder dbgMessage = new StringBuilder("getRangingTechnologies: ");
        for (Map.Entry<Integer, Integer> techAvailability : technologyAvailability.entrySet()) {
            if (techAvailability.getValue().equals(RangingCapabilities.ENABLED)) {
                String techName = getRangingTechnologyName(techAvailability.getKey());
                dbgMessage.append(techName).append(", ");
                techs.add(techName);
            }
        }
        // Always add OOB
        String techName = Technology.OOB.toString();
        dbgMessage.append(techName).append(", ");
        techs.add(techName);
        printLog(dbgMessage.toString());
        return techs;
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
    boolean startDistanceMeasurement(
            String rangingTechnologyName, String freqName, int duration) {
        if (mTargetDevice == null) {
            printLog("Please connect the device over Gatt first");
            return false;
        }
        if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_CS)) {
            if (mTargetDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                printLog("Please bond the devices for channel sounding");
                return false;
            }
            printLog("Bonded Devices: " + mApplicationContext.getSystemService(BluetoothManager.class).getAdapter().getBondedDevices());
        }
        printLog("Start ranging with device: " + mTargetDevice.getName());
        mSession = mRangingManager.createRangingSession(
                Executors.newSingleThreadExecutor(), mRangingSessionCallback);
        // Don't block here to avoid making the UX unresponsive (especially for OOB handshaking)
        mExecutor.execute(() -> {
            RangingPreference rangingPreference =
                    RangingParameters.createInitiatorRangingPreference(
                            mApplicationContext, mBleConnectionCentralViewModel, mLoggingListener,
                            rangingTechnologyName, freqName,
                            ConfigurationParameters.restoreInstance(mApplicationContext, false),
                            duration, mTargetDevice);
            if (rangingPreference == null) {
                printLog("Failed to start ranging session");
                mDistanceMeasurementCallback.onStartFail();
                return;
            }
            mCancellationSignal.set(mSession.start(rangingPreference));
        });
        return true;
    }

    void stopDistanceMeasurement() {
        if (mSession == null || mCancellationSignal.get() == null) {
            return;
        }
        mCancellationSignal.get().cancel();
        mSession = null;
        mCancellationSignal.set(null);
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
                    mDistanceMeasurementCallback.onDistanceResult(
                            data.getDistance().getMeasurement());
                }
            };

    interface DistanceMeasurementCallback {

        void onStartSuccess();

        void onStartFail();

        void onStop();

        void onDistanceResult(double distanceMeters);
    }
}
