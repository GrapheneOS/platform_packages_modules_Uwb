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
package com.android.ranging.rtt.backend.internal;

import android.app.AlarmManager;
import android.content.Context;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Ranges to a given WiFi Aware Peer handle.
 * Rtt Ranger is only used for legacy RTT sessions on devices that do not support the new periodic
 * rtt API.
 */
public class RttRanger {
    private static final String TAG = RttRanger.class.getName();

    private final WifiRttManager mWifiRttManager;
    private final Executor mExecutor;

    private RttRangerListener mRttRangerListener;
    private PeerHandle mPeerHandle;

    private boolean mIsRunning;

    private final AlarmManager mAlarmManager;
    private int mBaseUpdateRateMs = 512;
    private int mCurrentUpdateRateMs = 512;
    private AlarmManager.OnAlarmListener mAlarmListener;

    public RttRanger(WifiRttManager wiFiRttManager, Executor executor, Context context) {
        this.mExecutor = executor;
        this.mWifiRttManager = wiFiRttManager;
        mAlarmManager = context.getSystemService(AlarmManager.class);
        Objects.requireNonNull(mAlarmManager);
    }

    public void startRanging(@NonNull PeerHandle peerHandle,
            @NonNull RttRangerListener rttRangerListener, int updateRateMs) {
        if (mIsRunning) {
            Log.w(TAG, "startRanging - already running");
            return;
        }
        mIsRunning = true;
        this.mPeerHandle = peerHandle;
        this.mRttRangerListener = rttRangerListener;
        mBaseUpdateRateMs = updateRateMs;
        mCurrentUpdateRateMs = mBaseUpdateRateMs;
        startRangingInternal();
    }

    public void reconfigureInterval(int intervalSkipCount) {
        mCurrentUpdateRateMs = (mBaseUpdateRateMs * intervalSkipCount) + mBaseUpdateRateMs;
    }

    private void startRangingInternal() {
        if (!mWifiRttManager.isAvailable()) {
            Log.w(TAG, "WifiRttManager is not available");
            stopRanging();
            mRttRangerListener.onRangingFailure(
                    RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
            return;
        }
        setPeriodicAlarm();
        mWifiRttManager.startRanging(
                new RangingRequest.Builder().addWifiAwarePeer(mPeerHandle).build(),
                mExecutor,
                mRangingResultCallback);
    }

    private void setPeriodicAlarm() {
        if (mAlarmListener != null) {
            mAlarmManager.cancel(mAlarmListener);
        }
        mAlarmListener = () -> {
            mExecutor.execute(this::startRangingInternal);
        };
        mAlarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + mCurrentUpdateRateMs,
                "RttRangingInterval",
                mAlarmListener,
                null
        );
    }

    public void stopRanging() {
        mIsRunning = false;
        if (mAlarmListener != null) {
            mAlarmManager.cancel(mAlarmListener);
            mAlarmListener = null;
        }
    }

    private final RangingResultCallback mRangingResultCallback = new RangingResultCallback() {
        @Override
        public void onRangingFailure(int code) {
            Log.w(TAG, "RTT ranging failed: " + code);
            mRttRangerListener.onRangingFailure(code);
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            Log.i(TAG, "RTT ranging results: " + results);
            if (mRttRangerListener == null) {
                Log.w(TAG, "Rtt Ranging Listener is null");
                return;
            }
            mRttRangerListener.onRangingResults(results);
        }
    };

    /** Listener for range results. */
    public interface RttRangerListener {
        int STATUS_CODE_FAIL = 1;
        int STATUS_CODE_FAIL_RTT_NOT_AVAILABLE = 2;
        int STATUS_CODE_FAIL_RESULT_EMPTY = 3;
        int STATUS_CODE_FAIL_RESULT_FAIL = 4;

        void onRangingFailure(int code);

        void onRangingResults(List<RangingResult> results);
    }
}
