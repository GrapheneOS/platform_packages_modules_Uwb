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

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;

/** A multi-technology ranging session in the Android generic ranging service */
public class RangingSession {
    private static final String TAG = RangingSession.class.getSimpleName();

    /** Callback for {@link RangingSession} events. */
    public interface Callback {
        /**
         * Callback method for reporting when ranging has started for a particular technology or
         * for the entire session.
         * @param technology that was started, or {@code null} to indicate that the entire session
         *                   has started.
         */
        void onStarted(@Nullable RangingTechnology technology);

        /**
         * Callback method for reporting when ranging has stopped for a particular technology or for
         * the entire session.
         * @param technology that was stopped, or {@code null} to indicate that the entire session
         *                   has stopped.
         * @param reason     why the technology or session was stopped.
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
                StoppedReason.NO_INITIAL_DATA_TIMEOUT,
                StoppedReason.NO_UPDATED_DATA_TIMEOUT,
        })
        @interface StoppedReason {
            /** The session failed to report data before the initial data timeout expired. */
            int NO_INITIAL_DATA_TIMEOUT = 6;
            /** The session had no new data to report before the data update timeout expired. */
            int NO_UPDATED_DATA_TIMEOUT = 7;
        }
    }

    private final Context mContext;
    private final ListeningExecutorService mAdapterExecutor;
    private final ScheduledExecutorService mTimeoutExecutor;

    /**
     * Map of peers in the session. The key is the UUID of the peer and the value is a
     * {@link RangingPeer} object that encapsulates peer-specific session logic.
     */
    private final ConcurrentMap<UUID, RangingPeer> mPeers = new ConcurrentHashMap<>();

    public RangingSession(
            @NonNull Context context,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService timeoutExecutor
    ) {
        mContext = context;
        mAdapterExecutor = adapterExecutor;
        mTimeoutExecutor = timeoutExecutor;
    }

    /**
     * Starts ranging in the session.
     *
     * @param callback   to notify on session events.
     * @param configs for the session. Each key is the UUID of a peer, and each value is that peer's
     *                configuration.
     */
    public void start(
            @NonNull ImmutableMap<UUID, RangingConfig> configs, @NonNull Callback callback
    ) {
        for (Map.Entry<UUID, RangingConfig> entry : configs.entrySet()) {
            UUID peerId = entry.getKey();
            RangingConfig peerConfig = entry.getValue();

            synchronized (mPeers) {
                // Don't overwrite peers inserted for testing.
                if (!mPeers.containsKey(peerId)) {
                    mPeers.put(peerId,
                            new RangingPeer(mContext, mAdapterExecutor, mTimeoutExecutor));
                }

                mPeers.get(peerId).start(peerConfig, new PeerListener(peerId, callback));
            }
        }
    }

    /**
     * Stops ranging.
     */
    public void stop() {
        synchronized (mPeers) {
            for (RangingPeer peer : mPeers.values()) {
                peer.stop();
            }
            mPeers.clear();
        }
    }

    private class PeerListener implements Callback {
        private final UUID mPeerId;
        private final Callback mDelegate;

        /**
         * @param peerId   id of the peer this callback is associated with.
         * @param delegate wrapped callback to delegate to after managing peer logic.
         */
        public PeerListener(@NonNull UUID peerId, @NonNull Callback delegate) {
            mPeerId = peerId;
            mDelegate = delegate;
        }

        @Override
        public void onStarted(@Nullable RangingTechnology technology) {
            mDelegate.onStarted(technology);
        }

        @Override
        public void onStopped(@Nullable RangingTechnology technology, int reason) {
            if (technology == null) {
                // The session has stopped for this peer, so remove it.
                mPeers.remove(mPeerId);
            }
            mDelegate.onStopped(technology, reason);
        }

        @Override
        public void onData(@NonNull RangingData data) {
            mDelegate.onData(data);
        }
    }

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

    @VisibleForTesting
    public void usePeerForTesting(@NonNull UUID id, @NonNull RangingPeer peer) {
        mPeers.put(id, peer);
    }
}
