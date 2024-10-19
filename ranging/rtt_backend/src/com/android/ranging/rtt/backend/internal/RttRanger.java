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

import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.concurrent.Executor;

/** Ranges to a given WiFi Aware Peer handle. */
public class RttRanger {
    private static final String TAG = RttRanger.class.getName();

    private final WifiRttManager mWifiRttManager;
    private final Executor mExecutor;

    private RttRangerListener mRttRangerListener;
    private PeerHandle mPeerHandle;

    private boolean mIsRunning;

    public RttRanger(WifiRttManager wiFiRttManager, Executor executor) {
        this.mExecutor = executor;
        this.mWifiRttManager = wiFiRttManager;
    }

    public void startRanging(@NonNull PeerHandle peerHandle,
            @NonNull RttRangerListener rttRangerListener) {
        if (mIsRunning) {
            Log.w(TAG, "startRanging - already running");
            return;
        }
        mIsRunning = true;
        this.mPeerHandle = peerHandle;
        this.mRttRangerListener = rttRangerListener;
        startRangingInternal();
    }

    private void startRangingInternal() {
        if (!mWifiRttManager.isAvailable()) {
            Log.w(TAG, "WifiRttManager is not available");
            stopRanging();
            mRttRangerListener.onRangingFailure(
                    RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
            return;
        }
        mWifiRttManager.startRanging(
                new RangingRequest.Builder().addWifiAwarePeer(mPeerHandle).build(),
                mExecutor,
                mRangingResultCallback);
    }

    public void stopRanging() {
        mIsRunning = false;
    }

    private final RangingResultCallback mRangingResultCallback = new RangingResultCallback() {
        @Override
        public void onRangingFailure(int code) {
            Log.w(TAG, "RTT ranging failed: " + code);
            mRttRangerListener.onRangingFailure(code);
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            if (results == null) {
                Log.w(TAG, "Rtt Ranging result is null");
                return;
            }
            Log.i(TAG, "RTT ranging results: " + results);
            if (mRttRangerListener == null) {
                Log.w(TAG, "Rtt Ranging Listener is null");
                return;
            }

            if (results.isEmpty()) {
                mRttRangerListener.onRangingFailure(
                        RttRangerListener.STATUS_CODE_FAIL_RESULT_EMPTY);
                return;
            }

            RangingResult result = results.get(0);
            int status = result.getStatus();

            if (status == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC) {
                Log.w(TAG, "Responder does not support 11mc");
                mRttRangerListener.onRangingFailure(
                        RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                return;
            } else if (status == RangingResult.UNSPECIFIED) {
                Log.w(TAG, "Unspecified failed.");
                mRttRangerListener.onRangingFailure(
                        RttRangerListener.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE);
                return;
            } else if (status == RangingResult.STATUS_FAIL) {
                mRttRangerListener.onRangingFailure(
                        RttRangerListener.STATUS_CODE_FAIL_RESULT_FAIL);
            } else if (status == RangingResult.STATUS_SUCCESS) {
                mRttRangerListener.onRangingResult(result);
            }

            if (mIsRunning) {
                startRangingInternal();
            }
        }
    };

    /** Listener for range results. */
    public interface RttRangerListener {
        int STATUS_CODE_FAIL = 1;
        int STATUS_CODE_FAIL_RTT_NOT_AVAILABLE = 2;
        int STATUS_CODE_FAIL_RESULT_EMPTY = 3;
        int STATUS_CODE_FAIL_RESULT_FAIL = 4;

        void onRangingFailure(int code);

        void onRangingResult(RangingResult results);
    }
}
