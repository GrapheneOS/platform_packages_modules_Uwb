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
import androidx.core.uwb.backend.impl.internal.RangingCapabilities;
import androidx.core.uwb.backend.impl.internal.RangingParameters;
import androidx.core.uwb.backend.impl.internal.UwbAddress;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.EnumMap;

/** A multi-technology ranging session in the Android generic ranging service */
public interface RangingSession {

    /** Starts ranging with all technologies specified, providing results via the given callback. */
    void start(Callback callback);

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

    /** Sets UWB configuration. No op if UWB was not requested. */
    void setUwbConfig(RangingParameters rangingParameters);

    /** Returns CS capabilities if CS was requested. */
    void getCsCapabilities();

    /** Sets CS configuration. No op if CS was not requested. */
    void setCsConfig();

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
        /** Callback method for reporting when ranging has started. */
        void onStarted();

        /** Callback method for reporting when ranging has stopped. */
        void onStopped(@StoppedReason int reason);

        /** Callback for reporting ranging data. */
        void onData(RangingData data);

        /** Reason why ranging was stopped. */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef({
                /* Unexpected internal error. */
                StoppedReason.INTERNAL_ERROR,
                /* Stopped as a result of calling {@link RangingSession#stop()}. */
                StoppedReason.REQUESTED,
                /* Stopped because no ranging data was received before a timeout expired. */
                StoppedReason.NO_RANGES_TIMEOUT,
                /*
                 * Stopped because the fusion algorithm attempted to without ranging measurements
                 * for too long.
                 */
                StoppedReason.FUSION_DRIFT_TIMEOUT,
        })
        @interface StoppedReason {
            int INTERNAL_ERROR = 0;
            int REQUESTED = 1;
            int NO_RANGES_TIMEOUT = 2;
            int FUSION_DRIFT_TIMEOUT = 3;
        }
    }
}
