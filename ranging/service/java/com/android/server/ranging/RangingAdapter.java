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

import android.ranging.RangingData;
import android.ranging.RangingDevice;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

/** RangingAdapter representing a common ranging class for multiple ranging technologies. */
public interface RangingAdapter {

    /** Returns {@link RangingTechnology} of this adapter. */
    @NonNull RangingTechnology getTechnology();

    /**
     * Start ranging. Does nothing if the ranging technology is not enabled on device or if ranging
     * has already been started. In the latter case, this method will not overwrite the existing
     * callback.
     * @param config for the ranging session.
     * @param callback to be called on the occurrence of ranging events.
     */
    void start(@NonNull RangingSessionConfig.TechnologyConfig config, @NonNull Callback callback);

    /** Stop ranging. */
    void stop();

    /** Callback for getting notified when ranging starts or stops. */
    interface Callback {
        /**
         * Notifies the caller that ranging has started with a particular peer. onStarted will not
         * be called after start if API failed to initialize, in that case onClosed with an
         * appropriate error code will be called instead.
         *
         * @param peer that ranging was started with.
         */
        void onStarted(@NonNull RangingDevice peer);


        /**
         * Notifies the caller that ranging has stopped with a particular peer.
         *
         * @param peer that ranging was stopped with.
         */
        void onStopped(@NonNull RangingDevice peer);

        /**
         * Notifies the caller on each instance of ranging data received from the ranging
         * technology.
         *
         * @param peer device whose distance was measured.
         * @param data the distance measurement and other position-related data.
         */
        void onRangingData(@NonNull RangingDevice peer, @NonNull RangingData data);

        @IntDef({
                ClosedReason.UNKNOWN,
                ClosedReason.FAILED_TO_START,
                ClosedReason.REQUESTED,
                ClosedReason.LOST_CONNECTION,
                ClosedReason.SYSTEM_POLICY,
                ClosedReason.ERROR,
        })
        @interface ClosedReason {
            int UNKNOWN = 0;
            int ERROR = 1;
            int FAILED_TO_START = 2;
            int REQUESTED = 3;
            int LOST_CONNECTION = 4;
            int SYSTEM_POLICY = 5;
        }

        /**
         * Notifies the caller that the ranging session was closed.
         *
         * @param reason why the session was closed.
         */
        void onClosed(@ClosedReason int reason);
    }
}
