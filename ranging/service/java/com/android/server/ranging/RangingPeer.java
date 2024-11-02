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

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;

import android.content.Context;
import android.os.RemoteException;
import android.ranging.IRangingCallbacks;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.ranging.uwb.backend.internal.RangingCapabilities;
import com.android.server.ranging.RangingConfig.TechnologyConfig;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.rtt.RttAdapter;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.server.ranging.uwb.UwbConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.DoNotCall;
import com.google.uwb.support.fira.FiraParams;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A peer device within a generic ranging session. */
public final class RangingPeer {

    private static final String TAG = RangingPeer.class.getSimpleName();

    private final Context mContext;

    /**
     * Parameters provided when the session was started.
     * <b>Invariant: Non-null while a session is ongoing</b>.
     */
    private volatile RangingConfig mConfig;

    /**
     * Callback for session events.
     * <b>Invariant: Non-null while a session is ongoing</b>.
     */
    private volatile IRangingCallbacks mCallback;

    /** Fusion engines to use for each device in this session. */
    private final Map<RangingDevice, FusionEngine> mFusionEngines;

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

    /** Executor for session timeout handlers. */
    private final ScheduledExecutorService mTimeoutExecutor;

    /** Future that stops the session due to a timeout. */
    private volatile ScheduledFuture<?> mPendingTimeout;

    private final SessionHandle mSessionHandle;

    public RangingPeer(
            @NonNull Context context,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService timeoutExecutor,
            @NonNull SessionHandle sessionHandle,
            RangingConfig rangingConfig,
            IRangingCallbacks rangingCallbacks
    ) {
        mContext = context;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mCallback = null;
        mFusionEngines = new HashMap<>();
        mAdapters = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));
        mTimeoutExecutor = timeoutExecutor;
        mAdapterExecutor = adapterExecutor;
        mPendingTimeout = null;
        mSessionHandle = sessionHandle;
        mConfig = rangingConfig;
        mCallback = rangingCallbacks;
    }

    private @NonNull RangingAdapter newAdapter(
            @NonNull RangingTechnology technology, @NonNull TechnologyConfig config
    ) {
        switch (technology) {
            case UWB:
                return new UwbAdapter(
                        mContext, mAdapterExecutor,
                        ((UwbConfig) config).getDeviceRole()
                                == RangingPreference.DEVICE_ROLE_INITIATOR
                                ? FiraParams.RANGING_DEVICE_TYPE_CONTROLLER
                                : FiraParams.RANGING_DEVICE_TYPE_CONTROLEE);
            case CS:
                return new CsAdapter();
            case RTT:
                return new RttAdapter(mContext, mAdapterExecutor,
                        ((RttConfig) config).getDeviceRole());
            default:
                throw new IllegalArgumentException(
                        "Tried to create adapter for unknown technology" + technology);
        }
    }

    /** Start a ranging session with this peer */
    public void start() {
        if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
            Log.w(TAG, "Failed transition STOPPED -> STARTING");
            return;
        }

        ImmutableMap<RangingTechnology, TechnologyConfig> techConfigs =
                mConfig.getTechnologyConfigs();
        mAdapters.keySet().retainAll(techConfigs.keySet());

        for (Map.Entry<RangingTechnology, TechnologyConfig> entry : techConfigs.entrySet()) {
            RangingTechnology technology = entry.getKey();
            TechnologyConfig technologyConfig = entry.getValue();

            if (!technology.isSupported(mContext)) {
                Log.w(TAG, "Attempted to range with unsupported technology " + technology
                        + ", skipping");
                continue;
            }

            synchronized (mAdapters) {
                // Do not overwrite any adapters that were supplied for testing
                if (!mAdapters.containsKey(technology)) {
                    mAdapters.put(technology, newAdapter(technology, technologyConfig));
                }
                mAdapters.get(technology).start(technologyConfig, new AdapterListener(technology));
            }
        }

        scheduleTimeout(mConfig.getNoInitialDataTimeout(),
                RangingSession.Callback.StoppedReason.NO_INITIAL_DATA_TIMEOUT);
    }

    /** Stop the active session with this peer */
    public void stop() {
        stopForReason(RangingAdapter.Callback.StoppedReason.REQUESTED);
    }

    /**
     * Stop all ranging adapters and reset internal state.
     *
     * @param reason why the session was stopped.
     */
    private void stopForReason(@RangingSession.Callback.StoppedReason int reason) {
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

    /** Returns UWB capabilities if UWB was requested. */
    public ListenableFuture<RangingCapabilities> getUwbCapabilities() {
        if (!mAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) mAdapters.get(RangingTechnology.UWB);
        try {
            return uwbAdapter.getCapabilities();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to get Uwb capabilities");
            return null;
        }
    }

    /** Returns CS capabilities if CS was requested. */
    @DoNotCall("Not implemented")
    public void getCsCapabilities() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Returns a map that describes the {@link RangingSession.TechnologyStatus} of every
     * {@link RangingTechnology}
     */
    public ListenableFuture<EnumMap<RangingTechnology, Integer>> getTechnologyStatus() {
        // Combine all isEnabled futures for each technology into a single future. The resulting
        // future contains a list of technologies grouped with their corresponding
        // enabled state.
        ListenableFuture<List<Map.Entry<RangingTechnology, Boolean>>> enabledStatesFuture;
        synchronized (mAdapters) {
            enabledStatesFuture = Futures.allAsList(mAdapters.entrySet().stream()
                    .map((var entry) -> Futures.transform(
                            entry.getValue().isEnabled(),
                            (Boolean isEnabled) -> Map.entry(entry.getKey(), isEnabled),
                            mAdapterExecutor)
                    )
                    .collect(Collectors.toList())
            );
        }

        // Transform the list of enabled states into a technology status map.
        return Futures.transform(
                enabledStatesFuture,
                (List<Map.Entry<RangingTechnology, Boolean>> enabledStates) -> {
                    EnumMap<RangingTechnology, Integer> statuses =
                            new EnumMap<>(RangingTechnology.class);
                    for (RangingTechnology technology : RangingTechnology.values()) {
                        statuses.put(technology, RangingSession.TechnologyStatus.UNUSED);
                    }

                    for (Map.Entry<RangingTechnology, Boolean> enabledState : enabledStates) {
                        RangingTechnology technology = enabledState.getKey();
                        if (enabledState.getValue()) {
                            statuses.put(technology, RangingSession.TechnologyStatus.ENABLED);
                        } else {
                            statuses.put(technology, RangingSession.TechnologyStatus.DISABLED);
                        }
                    }
                    return statuses;
                },
                mAdapterExecutor
        );
    }

    /** If there is a pending timeout, cancel it. */
    private synchronized void cancelScheduledTimeout() {
        if (mPendingTimeout != null) {
            mPendingTimeout.cancel(false);
            mPendingTimeout = null;
        }
    }

    /**
     * Schedule a future that stops the session.
     *
     * @param timeout after which the session should be stopped.
     * @param reason  for stopping the session.
     */
    private synchronized void scheduleTimeout(
            @NonNull Duration timeout, @RangingSession.Callback.StoppedReason int reason
    ) {
        cancelScheduledTimeout();
        mPendingTimeout = mTimeoutExecutor.schedule(
                () -> {
                    Log.w(TAG, "Reached scheduled timeout of " + timeout.toMillis());
                    stopForReason(reason);
                },
                timeout.toMillis(), TimeUnit.MILLISECONDS
        );
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
                try {
                    mCallback.onStarted(mSessionHandle, mTechnology.ordinal());
                } catch (RemoteException e) {
                    Log.e(TAG, "onStarted failed " + e);
                }
            }
        }

        @Override
        public void onStopped(@RangingAdapter.Callback.StoppedReason int reason) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STOPPING) {
                    mAdapters.get(mTechnology).stop();
                }
                mAdapters.remove(mTechnology);
                mFusionEngines.values().forEach(engine -> engine.removeDataSource(mTechnology));
                if (mAdapters.isEmpty()) {
                    mStateMachine.setState(State.STOPPED);
                    // The last technology in the session has stopped, so signal that the entire
                    // session has stopped.
                    try {
                        mCallback.onClosed(mSessionHandle, reason);
                    } catch (RemoteException e) {
                        Log.e(TAG, "onClosed failed " + e);
                    }
                    // Reset internal state.
                    mConfig = null;
                    mFusionEngines.values().forEach(FusionEngine::stop);
                    mFusionEngines.clear();
                    mCallback = null;
                }
            }
        }

        @Override
        public void onRangingData(RangingDevice peer, RangingData data) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STOPPED) {
                    FusionEngine engine = mFusionEngines.get(peer);
                    if (engine == null) {
                        // Create and start engines lazily
                        engine = mConfig.createConfiguredFusionEngine();
                        mFusionEngines.put(peer, engine);
                        engine.start(new FusionEngineListener(peer));
                    }
                    if (!engine.getDataSources().contains(mTechnology)) {
                        engine.addDataSource(mTechnology);
                    }
                    engine.feed(data);
                }
            }
        }
    }

    /** Listens for fusion engine events. */
    private class FusionEngineListener implements FusionEngine.Callback {

        private final RangingDevice mPeer;

        FusionEngineListener(RangingDevice peer) {
            mPeer = peer;
        }

        @Override
        public void onData(@NonNull RangingData data) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STOPPED) {
                    return;
                }
                cancelScheduledTimeout();
                try {
                    mCallback.onData(mSessionHandle, mPeer, data);
                } catch (RemoteException e) {
                    Log.e(TAG, "onData failed: " + e);
                }
                scheduleTimeout(
                        mConfig.getNoUpdatedDataTimeout(),
                        RangingSession.Callback.StoppedReason.NO_UPDATED_DATA_TIMEOUT);
            }
        }
    }


    @VisibleForTesting
    public void useAdapterForTesting(RangingTechnology technology, RangingAdapter adapter) {
        mAdapters.put(technology, adapter);
    }

    private enum State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED,
    }
}
