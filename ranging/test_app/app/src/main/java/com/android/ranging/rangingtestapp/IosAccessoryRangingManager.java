/*
 * Copyright 2025 The Android Open Source Project
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

import android.app.Activity;
import android.os.CancellationSignal;
import android.os.CancellationSignal.OnCancelListener;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingManager.RangingCapabilitiesCallback;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;

import androidx.annotation.NonNull;

import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AccessoryConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AppleShareableConfigurationData;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class IosAccessoryRangingManager {
    // Callback for ranging status
    abstract static class RangingCallback {
        abstract void onRangingCapabilities(RangingCapabilities rangingCapabilities);
        abstract void onRangingStarted();
        abstract void onRangingStopped();
        abstract void onRangingResult(int technology, double distanceMeters);
    }

    private final RangingManager mRangingManager;
    private final RangingCallback mRangingCallback;
    private final LoggingListener mLoggingListener;
    private final Executor mExecutor;
    private final RangingCapabilitiesCallback mCapabilitiesCallback =
            new RangingCapabilitiesCallback() {
                @Override
                public void onRangingCapabilities(
                        @NonNull RangingCapabilities rangingCapabilities) {
                    printLog("RangingCapabilities: " + rangingCapabilities);
                    mRangingCallback.onRangingCapabilities(rangingCapabilities);
                }
            };

    private final RangingSession.Callback mSessionCallbackHandler = new RangingSession.Callback() {
        @Override
        public void onOpened() {
            printLog("RangingSession onOpened");
        }

        @Override
        public void onOpenFailed(int reason) {
            printLog("RangingSession onOpenFailed: " + reason);
        }

        @Override
        public void onClosed(int reason) {
            printLog("RangingSession onClosed: " + reason);
        }

        @Override
        public void onStarted(RangingDevice peer, int technology) {
            printLog("RangingSession onStarted: " + peer + " with " + technology);
            mRangingCallback.onRangingStarted();
        }

        @Override
        public void onStopped(RangingDevice peer, int technology) {
            printLog("RangingSession onStopped: " + peer + " with " + technology);
            mRangingCallback.onRangingStopped();
        }

        public void onResults(RangingDevice peer, RangingData data) {
            printLog("RangingSession onResults: " + peer + " with " + data);
            mRangingCallback.onRangingResult(
                    data.getRangingTechnology(), data.getDistance().getMeasurement());
        }
    };

    private RangingSession mRangingSession = null;
    private final Object mRangingSessionGuard = new Object();
    private CancellationSignal mCancellationSignal = null;
    private final OnCancelListener mOnCancelListener = new OnCancelListener() {
        @Override
        public void onCancel() {
            printLog("RangingSession onCancel");
        }
    };

    IosAccessoryRangingManager(
            Activity activity,
            RangingCallback rangingCallback,
            LoggingListener loggingListener) {
        mRangingManager = activity.getApplication().getSystemService(RangingManager.class);
        mRangingCallback = rangingCallback;
        mLoggingListener = loggingListener;
        mExecutor = Executors.newSingleThreadExecutor();
        mRangingManager.registerCapabilitiesCallback(mExecutor, mCapabilitiesCallback);
    }

    private void printLog(String log) {
        mLoggingListener.log(log);
    }

    private void printStartRangingAdbCommand(
            AccessoryConfigurationData accessoryConfigurationData,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        String command = IosAccessoryRangingParameters.createStartRangingAdbCommand(
                accessoryConfigurationData, appleShareableConfigurationData);
        printLog("ADB equivalent command: " + command);
    }

    boolean startRanging(
            AccessoryConfigurationData accessoryConfigurationData,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        if (accessoryConfigurationData == null) {
            printLog("Ranging session is not started as AccessoryConfigurationData is null");
            return false;
        }
        if (appleShareableConfigurationData == null) {
            printLog("Ranging session is not started as AppleShareableConfigurationData is null");
            return false;
        }
        printStartRangingAdbCommand(accessoryConfigurationData, appleShareableConfigurationData);
        RangingPreference rangingPreference =
                IosAccessoryRangingParameters.createRangingPreference(
                        accessoryConfigurationData, appleShareableConfigurationData);
        synchronized (mRangingSessionGuard) {
            stopRanging();
            mRangingSession = mRangingManager.createRangingSession(
                    mExecutor, mSessionCallbackHandler);
            printLog("Ranging session is created");
            mCancellationSignal = mRangingSession.start(rangingPreference);
            mCancellationSignal.setOnCancelListener(mOnCancelListener);
            printLog("Ranging session is starting");
        }
        return true;
    }

    boolean stopRanging() {
        synchronized (mRangingSessionGuard) {
            if (mCancellationSignal != null) {
                printLog("Canceling previous ranging session");
                mCancellationSignal.cancel();
            } else {
                printLog("Ranging session is empty and not cancelable");
            }
            if (mRangingSession != null) {
                printLog("Stopping previous ranging session");
                mRangingSession.stop();
                mRangingSession.close();
            } else {
                printLog("Ranging session is empty and not closable");
            }
            mRangingSession = null;
            mCancellationSignal = null;
        }
        return true;
    }

    boolean setSkipRound(int skipRound) {
        synchronized (mRangingSessionGuard) {
            if (mRangingSession == null) {
                printLog("Ranging session is empty and not reconfigurable");
                return false;
            }
            mRangingSession.reconfigureRangingInterval(skipRound);
            printLog("Ranging session is reconfiguring with skip round: " + skipRound);
        }
        return true;
    }
}
