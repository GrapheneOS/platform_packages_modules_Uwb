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

package com.android.ranging;

import android.os.RemoteException;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.RangingCapabilities;
import com.android.ranging.uwb.backend.internal.UwbAddress;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.EnumMap;

/** A multi-technology ranging session in the Android generic ranging service */
public interface RangingSession {

    /** Starts ranging with all technologies specified, providing results via the given callback. */
    void start(@NonNull RangingParameters parameters, @NonNull Callback callback);

    /** Stops ranging. */
    void stop();

    /**
     * Returns a map that describes the {@link TechnologyStatus} of every {@link RangingTechnology}
     */
    ListenableFuture<EnumMap<RangingTechnology, Integer>> getTechnologyStatus();

    /** Returns UWB capabilities if UWB was requested. */
    ListenableFuture<RangingCapabilities> getUwbCapabilities();

    /** Returns UWB address if UWB was requested. */
    ListenableFuture<UwbAddress> getUwbAddress() throws RemoteException;

    /** Returns CS capabilities if CS was requested. */
    void getCsCapabilities();

    /** State of an individual {@link RangingTechnology}. */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            /* Ranging technology is not part of this session. */
            TechnologyStatus.UNUSED,
            /* Ranging technology is disabled due to a device condition or user switch. */
            TechnologyStatus.DISABLED,
            /* Ranging technology is enabled. */
            TechnologyStatus.ENABLED,
    })
    @interface TechnologyStatus {
        int UNUSED = 0;
        int DISABLED = 1;
        int ENABLED = 2;
    }

    /** Callback for {@link RangingSession} events. */
    interface Callback {
        /**
         * Callback method for reporting when ranging has started for a particular technology or
         * for the entire session.
         * @param technology that was started, or {@code null} to indicate that the entire session
         *                   has started.
         */
        void onStarted(@Nullable RangingTechnology technology);

        /**
         * Callback method for reporting when ranging has stopped for a particular technology or for
         * @param technology that was stopped, or {@code null} to indicate that the entire session
         *                   has stopped.
         * @param reason why the technology or session was stopped.
         */
        void onStopped(@Nullable RangingTechnology technology, @StoppedReason int reason);

        /**
         * Callback for reporting ranging data.
         * @param data to be reported.
         */
        void onData(@NonNull RangingData data);

        /** Reason why ranging was stopped. */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
            RangingAdapter.Callback.StoppedReason.UNKNOWN,
            RangingAdapter.Callback.StoppedReason.FAILED_TO_START,
            RangingAdapter.Callback.StoppedReason.REQUESTED,
            RangingAdapter.Callback.StoppedReason.LOST_CONNECTION,
            RangingAdapter.Callback.StoppedReason.SYSTEM_POLICY,
            RangingAdapter.Callback.StoppedReason.ERROR,
            StoppedReason.EMPTY_SESSION_TIMEOUT,
        })
        @interface StoppedReason {
            /**
             * Stopped because no ranging data was received before a timeout expired. While the
             * fusion algorithm can continue to produce data without ranging reports, this
             * causes inaccuracies and thus a "fusion drift timeout" is enforced.
             */
            int EMPTY_SESSION_TIMEOUT = 6;
        }
    }
}
