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
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.DialogInterface;
import android.os.CancellationSignal;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.android.ranging.rangingtestapp.RangingParameters.Technology;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

class DistanceMeasurementManager {
    private final RangingManager mRangingManager;
    private final LoggingListener mLoggingListener;

    private final Activity mActivity;
    private final BleConnection mBleConnection;
    private final Executor mExecutor;
    private final Callback mCallback;
    private final boolean mIsResponder;
    @Nullable private RangingSession mSession = null;
    private AtomicReference<CancellationSignal> mCancellationSignal =
            new AtomicReference<>(null);
    private CountDownLatch mCapabilitiesCountDownLatch = new CountDownLatch(1);
    @Nullable private AtomicReference<RangingCapabilities> mRangingCapabilities =
            new AtomicReference<>();
    @Nullable private BluetoothDevice mTargetDevice = null;
    private AlertDialog mAlertDialog = null;

    DistanceMeasurementManager(
            Activity activity,
            BleConnection bleConnection,
            Callback distanceMeasurementCallback,
            com.android.ranging.rangingtestapp.LoggingListener loggingListener,
            boolean isResponder) {
        mActivity = activity;
        mBleConnection = bleConnection;
        mCallback = distanceMeasurementCallback;
        mLoggingListener = loggingListener;
        mIsResponder = isResponder;

        mRangingManager = mActivity.getApplication().getSystemService(RangingManager.class);
        mExecutor = Executors.newSingleThreadExecutor();

        mRangingManager.registerCapabilitiesCallback(mExecutor, (capabilities -> {
            mRangingCapabilities.set(capabilities);
            mCapabilitiesCountDownLatch.countDown();
        }));
    }

    void setTargetDevice(BluetoothDevice targetDevice) {
        mTargetDevice = targetDevice;
        if (mTargetDevice == null) {
            stop();
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

    private void showBondAlertDialog() {
        DialogInterface.OnDismissListener onDismissListener = (d) -> {
            mAlertDialog.dismiss();
        };
        DialogInterface.OnClickListener onClickListener = (d, b) -> {
            switch (b) {
                case DialogInterface.BUTTON_POSITIVE:
                    printLog("Initiating bond with " + mTargetDevice.getName());
                    if (!mTargetDevice.createBond()) {
                        printLog("Failed to initiate bond with " + mTargetDevice.getName());
                    }
                    break;
                default:
                    break;
            }
            mAlertDialog.dismiss();
        };
        mAlertDialog = new MaterialAlertDialogBuilder(mActivity, R.style.MaterialAlertDialog_rounded)
                .setTitle(R.string.bond_title)
                .setMessage(R.string.bond_message)
                .setCancelable(false)
                .setOnDismissListener(onDismissListener)
                .setNegativeButton(R.string.bond_ignore, onClickListener)
                .setPositiveButton(R.string.bond_create, onClickListener)
                .create();
        mAlertDialog.show();
    }

    @SuppressLint("MissingPermission") // permissions are checked upfront
    boolean start(
            String rangingTechnologyName, String freqName, int duration) {
        if (mTargetDevice == null) {
            printLog("Please connect the device over Gatt first");
            return false;
        }
        if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_CS)
             || Technology.fromName(rangingTechnologyName).equals(Technology.OOB)) {
            if (mTargetDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                printLog("Please bond the devices for channel sounding");
                printLog("Bonded Devices: " + mActivity.getApplication()
                        .getSystemService(BluetoothManager.class)
                        .getAdapter()
                        .getBondedDevices());
                showBondAlertDialog();
                return false;
            }
        }
        printLog("Start ranging with device: " + mTargetDevice.getName());
        mSession = mRangingManager.createRangingSession(
                Executors.newSingleThreadExecutor(), mRangingSessionCallback);
        // Don't block here to avoid making the UX unresponsive (especially for OOB handshaking)
        mExecutor.execute(() -> {
            RangingPreference rangingPreference = null;
            if (mIsResponder) {
                rangingPreference =
                        RangingParameters.createResponderRangingPreference(
                                mActivity.getApplication(), mBleConnection, mLoggingListener,
                                rangingTechnologyName, freqName,
                                ConfigurationParameters.restoreInstance(
                                        mActivity.getApplication(), mIsResponder),
                                duration, mTargetDevice);
            } else {
                rangingPreference =
                        RangingParameters.createInitiatorRangingPreference(
                                mActivity.getApplication(), mBleConnection, mLoggingListener,
                                rangingTechnologyName, freqName,
                                ConfigurationParameters.restoreInstance(
                                        mActivity.getApplication(), mIsResponder),
                                duration, mTargetDevice);
            }
            if (rangingPreference == null) {
                printLog("Failed to start ranging session");
                mCallback.onStartFail();
                return;
            }
            mCancellationSignal.set(mSession.start(rangingPreference));
        });
        return true;
    }

    void stop() {
        if (mCancellationSignal.get() != null) {
            printLog("Stop ranging with device: " + mTargetDevice.getName());
            mCancellationSignal.get().cancel();
            mCancellationSignal.set(null);
        }
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
        mSession = null;
    }

    private RangingSession.Callback mRangingSessionCallback =
            new RangingSession.Callback() {

                public void onOpened() {
                    printLog("DistanceMeasurementManager onOpened! ");
                }

                public void onOpenFailed(int reason) {
                    printLog("DistanceMeasurementManager onOpenFailed! " + reason);
                    mCallback.onStartFail();
                }

                public void onStarted(RangingDevice peer, int technology) {
                    printLog("DistanceMeasurementManager onStarted ! ");
                    mCallback.onStartSuccess();
                }

                public void onStopped(RangingDevice peer, int technology) {
                    printLog("DistanceMeasurementManager onStopped! " + technology);
                    mCallback.onStop();
                }

                public void onClosed(int reason) {
                    printLog("DistanceMeasurementManager onClosed! " + reason);
                    mCallback.onStop();
                }

                public void onResults(RangingDevice peer, RangingData data) {
                    printLog(
                            "DistanceMeasurementManager onResults ! " + peer + ": " + data);
                    mCallback.onDistanceResult(
                            data.getDistance().getMeasurement());
                }
            };

    interface Callback {

        void onStartSuccess();

        void onStartFail();

        void onStop();

        void onDistanceResult(double distanceMeters);
    }
}
