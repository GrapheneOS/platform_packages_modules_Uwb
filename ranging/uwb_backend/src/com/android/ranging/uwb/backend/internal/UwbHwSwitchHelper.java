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

package com.android.ranging.uwb.backend.internal;

import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_HW_IDLE;

import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import android.uwb.UwbManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for enabling or disabling UWB hardware. Each client can vote to have the UWB
 * hardware enabled or disabled. If there are no enable votes from any client, then the hardware
 * will be disabled to conserve power.
 *
 * <p>Since most of the clients are coming directly from GMSCore, a distinct attribution tag has to
 * be provided to correctly identify between different callers.
 */
public final class UwbHwSwitchHelper {

    private UwbHwSwitchHelper() {
    }

    private static final String TAG = UwbHwSwitchHelper.class.getSimpleName();
    private static final long TIMEOUT_MS = 2_000;

    /**
     * Requests UWB hardware to be enabled.
     *
     * <p>Note: This call will block if the UWB hardware needs to be brought up from "hw_idle"
     * state.
     *
     * @param attributionSource attribution tag used to identify the caller setting the enable vote.
     * @return true if vote was successful, false otherwise.
     */
    public static boolean enable(Context context, AttributionSource attributionSource) {
        return toggleUwbHw(context, attributionSource, true);
    }

    /**
     * Requests UWB hardware to be disabled.
     *
     * @param attributionSource attribution tag used to identify the caller setting the disable
     *                          vote.
     * @return true if vote was successful, false otherwise.
     */
    public static boolean disable(Context context, AttributionSource attributionSource) {
        return toggleUwbHw(context, attributionSource, false);
    }

    private static class AdapterStateCallback implements UwbManager.AdapterStateCallback {
        private final CountDownLatch mCountDownLatch;
        private final Integer mWaitForState;

        AdapterStateCallback(CountDownLatch countDownLatch, Integer waitForState) {
            mCountDownLatch = countDownLatch;
            mWaitForState = waitForState;
        }

        @Override
        public void onStateChanged(int state, int reason) {
            Log.v(TAG, "Uwb adapter state = " + state + " reason = " + reason);
            if (mWaitForState != null) {
                if (mWaitForState == state) {
                    mCountDownLatch.countDown();
                }
            } else {
                mCountDownLatch.countDown();
            }
        }
    }

    private static boolean toggleUwbHw(Context context, AttributionSource attributionSource,
                                       boolean enable) {
        // TODO: Remove after updating min version to 35
        if (VERSION.SDK_INT < VERSION_CODES.VANILLA_ICE_CREAM) {
            return true;
        }
        Context contextWithAttrSource =
                context.createContext(
                        new ContextParams.Builder()
                            .setNextAttributionSource(attributionSource)
                            .build());
        UwbManager uwbManagerWithAttrSource =
                contextWithAttrSource.getSystemService(UwbManager.class);
        int prevState = uwbManagerWithAttrSource.getAdapterState();
        if (prevState == UwbManager.AdapterStateCallback.STATE_DISABLED) {
            Log.w(TAG, "User has disabled UWB");
            return false;
        }
        AdapterStateCallback adapterStateCallback = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            boolean isHwIdleTurnOffEnabled = uwbManagerWithAttrSource
                    .isUwbHwIdleTurnOffEnabled();
            if (!isHwIdleTurnOffEnabled) {
                Log.w(TAG, "Device does not support hw_idle turn off");
                return false;
            }
            // If the state is in hw_idle, then wait for the request to turn the hardware on.
            if (prevState == STATE_ENABLED_HW_IDLE) {
                adapterStateCallback =
                        new AdapterStateCallback(
                                countDownLatch,
                                UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE);
                uwbManagerWithAttrSource.registerAdapterStateCallback(
                        Executors.newSingleThreadExecutor(), adapterStateCallback);
            }
            uwbManagerWithAttrSource.requestUwbHwEnabled(enable);
            if (prevState == STATE_ENABLED_HW_IDLE) {
                if (!countDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.w(TAG, "Failed to toggle UWB hardware");
                    return false;
                }
            }
        } catch (IllegalArgumentException
                 | InterruptedException e) {
            Log.w(TAG, "Failed to toggle UWB hardware");
            return false;
        } finally {
            if (prevState == STATE_ENABLED_HW_IDLE) {
                uwbManagerWithAttrSource.unregisterAdapterStateCallback(adapterStateCallback);
            }
        }
        return true;
    }
}
