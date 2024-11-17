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
import android.os.Binder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.RangingPeerConfig.TechnologyConfig;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** A peer device within a generic ranging session. */
public final class RangingPeer {

    private static final String TAG = RangingPeer.class.getSimpleName();

    private final RangingInjector mInjector;

    /**
     * Parameters provided when the session was started.
     * <b>Invariant: Non-null while a session is ongoing</b>.
     */
    private final RangingPeerConfig mConfig;

    /**
     * Listener for session events.
     */
    private final RangingServiceManager.SessionListener mSessionListener;

    /** Fusion engine to use for this device */
    private final FusionEngine mFusionEngine;

    /**
     * Keeps track of state of the ranging session.
     * <b>Must be synchronized</b>.
     */
    private final StateMachine<State> mStateMachine;

    /**
     * Ranging adapters used for this session.
     * <b>Must be synchronized</b>.
     * {@code mStateMachine} lock must be acquired first if mutual synchronization is necessary.
     */
    private final Map<RangingTechnology, RangingAdapter> mAdapters;

    /** Executor for ranging technology adapters. */
    private final ListeningExecutorService mAdapterExecutor;

    /** Lock for the ranging peer. */
    private final Object mLock = new Object();

    public RangingPeer(
            @NonNull RangingInjector injector,
            @NonNull RangingPeerConfig config,
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        mInjector = injector;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mAdapters = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));
        mAdapterExecutor = adapterExecutor;
        mConfig = config;
        mSessionListener = listener;

        if (mConfig.getSensorFusionConfig().isSensorFusionEnabled()) {
            mFusionEngine = new FilteringFusionEngine(
                    new DataFusers.PreferentialDataFuser(RangingTechnology.UWB)
            );
        } else {
            mFusionEngine = new NoOpFusionEngine();
        }
    }

    /** Start a ranging session with this peer */
    public void start() {
        synchronized (mLock) {
            if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
                Log.w(TAG, "Failed transition STOPPED -> STARTING");
                return;
            }
            ImmutableMap<RangingTechnology, TechnologyConfig> configs =
                    mConfig.getTechnologyConfigs();

            mFusionEngine.start(new FusionEngineListener());
            for (Map.Entry<RangingTechnology, TechnologyConfig> entry : configs.entrySet()) {
                RangingTechnology technology = entry.getKey();
                TechnologyConfig config = entry.getValue();
                // Any calls to the corresponding technology stacks must be
                // done with a clear calling identity.
                long token = Binder.clearCallingIdentity();
                RangingAdapter adapter = mInjector.createAdapter(
                        technology, mConfig.getDeviceRole(), mAdapterExecutor);
                mAdapters.put(technology, adapter);
                adapter.start(config, new AdapterListener(technology));
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    /** Stop the active session. */
    public void stop() {
        synchronized (mLock) {
            if (mStateMachine.getState() == State.STOPPING
                    || mStateMachine.getState() == State.STOPPED
            ) {
                Log.v(TAG, "Ranging already stopped, skipping");
                return;
            }
            mStateMachine.setState(State.STOPPING);

            // Stop all ranging technologies.
            for (RangingTechnology technology : mAdapters.keySet()) {
                // Any calls to the corresponding technology stacks must be
                // done with a clear calling identity.
                long token = Binder.clearCallingIdentity();
                mAdapters.get(technology).stop();
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    /** Listens for ranging adapter events. */
    private class AdapterListener implements RangingAdapter.Callback {
        private final RangingTechnology mTechnology;

        AdapterListener(RangingTechnology technology) {
            mTechnology = technology;
        }

        @Override
        public void onStarted() {
            synchronized (mLock) {
                if (mStateMachine.getState() == State.STOPPED) {
                    Log.w(TAG, "Received adapter onStarted but ranging session is stopped");
                    return;
                }
                if (mStateMachine.transition(State.STARTING, State.STARTED)) {
                    mSessionListener.onPeerStarted();
                }

                mFusionEngine.addDataSource(mTechnology);
                mSessionListener.onTechnologyStarted(mConfig.getDevice(), mTechnology.getValue());
            }
        }


        @Override
        public void onStopped(@StoppedReason int reason) {
            synchronized (mLock) {
                if (mStateMachine.getState() != State.STOPPING) {
                    mAdapters.get(mTechnology).stop();
                }
                mAdapters.remove(mTechnology);
                mFusionEngine.removeDataSource(mTechnology);
                mSessionListener.onTechnologyStopped(mConfig.getDevice(),
                    mTechnology.getValue());
                if (mAdapters.isEmpty()) {
                    // The last technology has stopped, so signal that ranging has completely
                    // stopped with this peer.
                    mStateMachine.setState(State.STOPPED);
                    mSessionListener.onPeerStopped(mConfig.getDevice(), reason);
                    mFusionEngine.stop();
                }

            }
        }

        @Override
        public void onRangingData(RangingDevice peer, RangingData data) {
            synchronized (mLock) {
                if (mStateMachine.getState() != State.STOPPED) {
                    mFusionEngine.feed(data);
                }
            }
        }
    }

    /** Listens for fusion engine events. */
    private class FusionEngineListener implements FusionEngine.Callback {

        @Override
        public void onData(@NonNull RangingData data) {
            synchronized (mLock) {
                if (mStateMachine.getState() == State.STOPPED) {
                    return;
                }
                mSessionListener.onResults(mConfig.getDevice(), data);
            }
        }
    }

    public static class NoOpFusionEngine extends FusionEngine {
        @VisibleForTesting
        public NoOpFusionEngine() {
            super(new DataFusers.PassthroughDataFuser());
        }

        @NonNull
        protected Set<RangingTechnology> getDataSources() {
            return Set.of();
        }

        public void addDataSource(@NonNull RangingTechnology technology) {
        }

        public void removeDataSource(@NonNull RangingTechnology technology) {
        }
    }

    private enum State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED,
    }
}
