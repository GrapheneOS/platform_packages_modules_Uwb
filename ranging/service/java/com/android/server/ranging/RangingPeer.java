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

import static android.ranging.RangingSession.Callback.REASON_LOCAL_REQUEST;
import static android.ranging.RangingSession.Callback.REASON_SYSTEM_POLICY;
import static android.ranging.RangingSession.Callback.REASON_UNKNOWN;
import static android.ranging.RangingSession.Callback.REASON_UNSUPPORTED;

import android.os.RemoteException;
import android.ranging.IRangingCallbacks;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingSession;
import android.ranging.SessionHandle;
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
     * Callback for session events.
     * <b>Invariant: Non-null while a session is ongoing</b>.
     */
    private final IRangingCallbacks mCallback;

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

    /** Handle for the session this peer belongs to. */
    private final SessionHandle mSessionHandle;

    /** Executor for ranging technology adapters. */
    private final ListeningExecutorService mAdapterExecutor;

    public RangingPeer(
            @NonNull RangingInjector injector,
            @NonNull RangingPeerConfig config,
            @NonNull IRangingCallbacks callbacks,
            @NonNull SessionHandle handle,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        mInjector = injector;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mAdapters = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));
        mAdapterExecutor = adapterExecutor;
        mConfig = config;
        mCallback = callbacks;
        mSessionHandle = handle;

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

            synchronized (mAdapters) {
                RangingAdapter adapter = mInjector.createAdapter(
                        technology, mConfig.getDeviceRole(), mAdapterExecutor);
                mAdapters.put(technology, adapter);
                adapter.start(config, new AdapterListener(technology));
            }
        }
    }

    /** Stop the active session. */
    public void stop() {
        synchronized (mStateMachine) {
            if (mStateMachine.getState() == State.STOPPING
                    || mStateMachine.getState() == State.STOPPED
            ) {
                Log.v(TAG, "Ranging already stopped, skipping");
                return;
            }
            mStateMachine.setState(State.STOPPING);

            // Stop all ranging technologies.
            synchronized (mAdapters) {
                for (RangingTechnology technology : mAdapters.keySet()) {
                    mAdapters.get(technology).stop();
                }
            }
        }
    }

    /** Listens for ranging adapter events. */
    private class AdapterListener implements RangingAdapter.Callback {
        private final RangingTechnology mTechnology;

        AdapterListener(RangingTechnology technology) {
            this.mTechnology = technology;
        }

        @Override
        public void onStarted() {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STOPPED) {
                    Log.w(TAG, "Received adapter onStarted but ranging session is stopped");
                    return;
                }
                mFusionEngine.addDataSource(mTechnology);
                try {
                    mCallback.onStarted(
                            mSessionHandle, mConfig.getDevice(), mTechnology.getValue());
                } catch (RemoteException e) {
                    Log.e(TAG, "onStarted failed " + e);
                }
            }
        }

        private @RangingSession.Callback.Reason int convertReason(@StoppedReason int reason) {
            switch (reason) {
                case StoppedReason.REQUESTED:
                    return REASON_LOCAL_REQUEST;
                case StoppedReason.FAILED_TO_START:
                    return REASON_UNSUPPORTED;
                case StoppedReason.LOST_CONNECTION:
                case StoppedReason.SYSTEM_POLICY:
                    return REASON_SYSTEM_POLICY;
                default:
                    return REASON_UNKNOWN;
            }
        }

        @Override
        public void onStopped(@StoppedReason int reason) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STOPPING) {
                    mAdapters.get(mTechnology).stop();
                }
                mAdapters.remove(mTechnology);
                mFusionEngine.removeDataSource(mTechnology);
                if (mAdapters.isEmpty()) {
                    mStateMachine.setState(State.STOPPED);
                    // The last technology in the session has stopped, so signal that the entire
                    // session has stopped.
                    try {
                        mCallback.onClosed(
                                mSessionHandle, mConfig.getDevice(), convertReason(reason));
                    } catch (RemoteException e) {
                        Log.e(TAG, "onClosed failed " + e);
                    }
                    mFusionEngine.stop();
                }
            }
        }

        @Override
        public void onRangingData(RangingDevice peer, RangingData data) {
            synchronized (mStateMachine) {
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
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STOPPED) {
                    return;
                }
                try {
                    mCallback.onData(mSessionHandle, mConfig.getDevice(), data);
                } catch (RemoteException e) {
                    Log.e(TAG, "onData failed: " + e);
                }
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
