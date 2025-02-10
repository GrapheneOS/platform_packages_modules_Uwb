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

import android.annotation.Nullable;
import android.content.AttributionSource;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.raw.RawResponderRangingConfig;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.android.server.ranging.session.RangingSessionConfig;

import com.google.common.collect.ImmutableSet;

/** RangingAdapter representing a common ranging class for multiple ranging technologies. */
public interface RangingAdapter {

    /** Returns {@link RangingTechnology} of this adapter. */
    @NonNull
    RangingTechnology getTechnology();

    /**
     * Start ranging. Does nothing if the ranging technology is not enabled on device or if ranging
     * has already been started. In the latter case, this method will not overwrite the existing
     * callback.
     *
     * @param config   for the ranging session.
     * @param callback to be called on the occurrence of ranging events.
     */
    void start(@NonNull RangingSessionConfig.TechnologyConfig config,
            @Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callback);

    /** Stop ranging. */
    void stop();

    /** Dynamic add/remove peers. Defaults to unsupported.*/
    default boolean isDynamicUpdatePeersSupported() {
        return false;
    }

    default void addPeer(RawResponderRangingConfig params) {}

    default void removePeer(RangingDevice device) {}

    default void reconfigureRangingInterval(int intervalSkipCount) {}

    default void appForegroundStateUpdated(boolean appInForeground) {
        if (appInForeground) {
            appMovedToForeground();
        } else {
            appMovedToBackground();
        }
    }
    void appMovedToBackground();

    void appMovedToForeground();

    void appInBackgroundTimeout();

    /** Callback for getting notified when ranging starts or stops. */
    interface Callback {
        /**
         * Notifies the caller that ranging has started with a particular peer. onStarted will not
         * be called after start if API failed to initialize, in that case onClosed with an
         * appropriate error code will be called instead.
         *
         * @param peers that ranging was started with. Must be non-empty. Multicast technologies
         *              may start ranging with multiple peers at once.
         */
        void onStarted(@NonNull ImmutableSet<RangingDevice> peers);


        /**
         * Notifies the caller that ranging has stopped with a particular peer.
         *
         * @param peers that ranging was stopped with. Must be non-empty. Multicast technologies
         *              may stop ranging with multiple peers at once.
         */
        void onStopped(@NonNull ImmutableSet<RangingDevice> peers);

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
                ClosedReason.ERROR,
                ClosedReason.FAILED_TO_START,
                ClosedReason.LOCAL_REQUEST,
                ClosedReason.REMOTE_REQUEST,
                ClosedReason.LOST_CONNECTION,
                ClosedReason.SYSTEM_POLICY,
        })
        @interface ClosedReason {
            int UNKNOWN = 0;
            int ERROR = 1;
            int FAILED_TO_START = 2;
            int LOCAL_REQUEST = 3;
            int REMOTE_REQUEST = 4;
            int LOST_CONNECTION = 5;
            int SYSTEM_POLICY = 6;
        }

        /**
         * Notifies the caller that the ranging session was closed.
         *
         * @param reason why the session was closed.
         */
        void onClosed(@ClosedReason int reason);
    }
}
