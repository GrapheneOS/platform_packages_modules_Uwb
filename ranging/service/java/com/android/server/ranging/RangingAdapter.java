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

package com.android.server.ranging;

import androidx.annotation.IntDef;

import com.android.server.ranging.RangingParameters.TechnologyParameters;

import com.google.common.util.concurrent.ListenableFuture;

/** RangingAdapter representing a common ranging class for multiple ranging technologies. */
public interface RangingAdapter {

    /** Returns {@link RangingTechnology} of this adapter. */
    RangingTechnology getType();

    /**
     * @return true if ranging with this ranging technology is currently enabled, or false
     * otherwise. When this returns false it's most likely because of not being enabled in settings,
     * airplane mode being on, etc.
     */
    ListenableFuture<Boolean> isEnabled();

    /**
     * Start ranging. Does nothing if the ranging technology is not enabled on device or if ranging
     * has already been started. In the latter case, this method will not overwrite the existing
     * callback.
     * @param parameters to range with.
     * @param callback to be called on the occurrence of ranging events.
     */
    void start(TechnologyParameters parameters, Callback callback);

    /** Stop ranging. */
    void stop();

    /** Callback for getting notified when ranging starts or stops. */
    interface Callback {
        /**
         * Notifies the caller that ranging has started on this device. onStarted will not be called
         * after start if API failed to initialize, in that case onStopped with an appropriate error
         * code will be called.
         */
        void onStarted();

        /** Notifies the caller that ranging has stopped on this device. */
        void onStopped(@StoppedReason int reason);

        /**
         * Notifies the caller on each instance of ranging data received from the ranging
         * technology.
         */
        void onRangingData(RangingData data);

        @IntDef({
                StoppedReason.UNKNOWN,
                StoppedReason.FAILED_TO_START,
                StoppedReason.REQUESTED,
                StoppedReason.LOST_CONNECTION,
                StoppedReason.SYSTEM_POLICY,
                StoppedReason.ERROR,
        })
        @interface StoppedReason {
            int UNKNOWN = 0;
            int ERROR = 1;
            int FAILED_TO_START = 2;
            int REQUESTED = 3;
            int LOST_CONNECTION = 4;
            int SYSTEM_POLICY = 5;
        }
    }
}
