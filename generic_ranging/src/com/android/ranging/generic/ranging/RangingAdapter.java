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

package com.android.ranging.generic.ranging;

import android.os.RemoteException;

import com.android.ranging.generic.RangingTechnology;

import com.google.common.util.concurrent.ListenableFuture;

/** RangingAdapter representing a common ranging interface for different ranging technologies. */
interface RangingAdapter {

    /** Returns {@link RangingTechnology} of this adapter. */
    RangingTechnology getType();

    /**
     * Returns true if this device is capable (has supporting hardware) to range using the ranging
     * technology it represents, false otherwise.
     */
    boolean isPresent();

    /**
     * Returns true if ranging with this ranging technology is currently enabled, or false
     * otherwise.
     * When this returns false it's most likely because of not being enabled in the settings,
     * airplane
     * mode being on, etc.
     */
    ListenableFuture<Boolean> isEnabled() throws RemoteException;

    /**
     * Initiate start ranging. The provided callback will notify once ranging has started or
     * stopped.
     * Ranging data will be provided via the callback. In case start is called while the API has
     * previously been started then this is a no op and the previously provided callback will still
     * be
     * used instead of the new one if they're different.
     */
    void start(Callback callback);

    /** Stop ranging. */
    void stop();

    /** Callback for getting notified when ranging starts or stops. */
    public interface Callback {
        /**
         * Notifies the caller that ranging has started on this device. onStarted will not be called
         * after start if API failed to initialize, in that case onStopped with an appropriate error
         * code will be called.
         */
        void onStarted();

        /** Notifies the caller that ranging has stopped on this device. */
        void onStopped(StoppedReason reason);

        /**
         * Notifies the caller on each instance of ranging data received from the ranging
         * technology.
         */
        void onRangingData(RangingData rangingData);

        /** Stopped reason for this ranging adapter. */
        public enum StoppedReason {
            REQUESTED,
            NO_PARAMS,
            ERROR,
        }
    }
}
