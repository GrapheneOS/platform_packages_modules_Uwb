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

import android.ranging.RangingDevice;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A multi-technology ranging session in the Android generic ranging service */
public class RangingSession {
    private static final String TAG = RangingSession.class.getSimpleName();

    private final RangingInjector mInjector;
    private final RangingServiceManager.SessionListener mSessionListener;
    private final ListeningExecutorService mAdapterExecutor;

    /** Peers in the session */
    private final ConcurrentMap<RangingDevice, RangingPeer> mPeers = new ConcurrentHashMap<>();

    public RangingSession(
            @NonNull RangingInjector injector,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        mInjector = injector;
        mSessionListener = listener;
        mAdapterExecutor = adapterExecutor;
    }

    /** Start ranging in this session. */
    public void start(@NonNull ImmutableList<RangingPeerConfig> peerConfigs) {
        for (RangingPeerConfig config : peerConfigs) {
            RangingPeer peer = new RangingPeer(mInjector, config, mSessionListener,
                    mAdapterExecutor);
            synchronized (mPeers) {
                mPeers.put(config.getDevice(), peer);
                peer.start();
            }
        }
    }

    /** Stops ranging. */
    public void stop() {
        synchronized (mPeers) {
            for (RangingPeer peer : mPeers.values()) {
                peer.stop();
            }
            mPeers.clear();
        }
    }

    /**
     * Remove a peer from the session.
     *
     * @param peer to remove.
     * @return whether or not the session is empty after removing the peer.
     */
    public boolean removePeerAndCheckEmpty(@NonNull RangingDevice peer) {
        synchronized (mPeers) {
            mPeers.remove(peer);
            return mPeers.isEmpty();
        }
    }
}
