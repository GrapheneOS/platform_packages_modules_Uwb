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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.server.ranging.RangingParameters.DeviceRole;
import com.android.server.ranging.RangingParameters.TechnologyParameters;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.ranging.uwb.backend.internal.RangingCapabilities;
import com.android.ranging.uwb.backend.internal.UwbAddress;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.errorprone.annotations.DoNotCall;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private volatile RangingParameters mParameters;

    /**
     * Callback for session events.
     * <b>Invariant: Non-null while a session is ongoing</b>.
     */
    private RangingSession.Callback mCallback;

    /**
     * Fusion engine to use for this session.
     * <b>Invariant: Non-null while a session is ongoing</b>.
     */
    private volatile FusionEngine mFusionEngine;

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

    public RangingPeer(
            @NonNull Context context,
            @NonNull ListeningExecutorService adapterExecutor,
            @NonNull ScheduledExecutorService timeoutExecutor
    ) {
        mContext = context;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mCallback = null;
        mFusionEngine = null;
        mAdapters = Collections.synchronizedMap(new EnumMap<>(RangingTechnology.class));
        mTimeoutExecutor = timeoutExecutor;
        mAdapterExecutor = adapterExecutor;
        mPendingTimeout = null;
    }

    private @NonNull RangingAdapter newAdapter(
            @NonNull RangingTechnology technology, DeviceRole role
    ) {
        switch (technology) {
            case UWB:
                return new UwbAdapter(mContext, mAdapterExecutor, role);
            case CS:
                return new CsAdapter();
            default:
                throw new IllegalArgumentException(
                        "Tried to create adapter for unknown technology" + technology);
        }
    }

    /** Start a ranging session with this peer */
    public void start(
            @NonNull RangingParameters parameters, @NonNull RangingSession.Callback callback
    ) {
        Log.i(TAG, "Start Precision Ranging called.");
        if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
            Log.w(TAG, "Failed transition STOPPED -> STARTING");
            return;
        }
        mCallback = callback;
        mParameters = parameters;

        if (mParameters.getDataFuser().isPresent()) {
            mFusionEngine = new FilteringFusionEngine(parameters.getDataFuser().get());
        } else {
            mFusionEngine = new NoOpFusionEngine();
        }

        ImmutableMap<RangingTechnology, TechnologyParameters> techParams =
                parameters.getTechnologyParams();
        mAdapters.keySet().retainAll(techParams.keySet());
        mFusionEngine.start(new FusionEngineListener());
        for (RangingTechnology technology : techParams.keySet()) {
            if (!technology.isSupported(mContext)) {
                Log.w(TAG, "Attempted to range with unsupported technology " + technology
                        + ", skipping");
                continue;
            }

            synchronized (mAdapters) {
                // Do not overwrite any adapters that were supplied for testing
                if (!mAdapters.containsKey(technology)) {
                    mAdapters.put(technology, newAdapter(technology, parameters.getRole()));
                }
                mAdapters.get(technology)
                        .start(techParams.get(technology), new AdapterListener(technology));
            }
        }

        scheduleTimeout(parameters.getNoInitialDataTimeout(),
                RangingSession.Callback.StoppedReason.NO_INITIAL_DATA_TIMEOUT);
    }

    /** Stop the active sessionw with this peer */
    public void stop() {
        stopForReason(RangingAdapter.Callback.StoppedReason.REQUESTED);
    }

    /**
     * Stop all ranging adapters and reset internal state.
     * @param reason why the session was stopped.
     */
    private void stopForReason(@RangingSession.Callback.StoppedReason int reason) {
        Log.i(TAG, "stopPrecisionRanging with reason: " + reason);
        synchronized (mStateMachine) {
            if (mStateMachine.getState() == State.STOPPED) {
                Log.v(TAG, "Ranging already stopped, skipping");
                return;
            }
            mStateMachine.setState(State.STOPPED);

            // Stop all ranging technologies.
            synchronized (mAdapters) {
                for (RangingTechnology technology : mAdapters.keySet()) {
                    mAdapters.get(technology).stop();
                    mCallback.onStopped(technology, reason);
                }
            }

            // Reset internal state.
            mParameters = null;
            mFusionEngine.stop();
            mFusionEngine = null;
            mAdapters.clear();
            mCallback.onStopped(null, reason);
            mCallback = null;
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

    /** Returns UWB address if UWB was requested. */
    public ListenableFuture<UwbAddress> getUwbAddress() throws RemoteException {
        if (!mAdapters.containsKey(RangingTechnology.UWB)) {
            return immediateFailedFuture(
                    new IllegalStateException("UWB was not requested for this session."));
        }
        UwbAdapter uwbAdapter = (UwbAdapter) mAdapters.get(RangingTechnology.UWB);
        return uwbAdapter.getLocalAddress();
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
                mFusionEngine.addDataSource(mTechnology);
                mCallback.onStarted(mTechnology);
            }
        }

        @Override
        public void onStopped(@RangingAdapter.Callback.StoppedReason int reason) {
            synchronized (mStateMachine) {
                if (mStateMachine.getState() != State.STOPPED) {
                    mAdapters.remove(mTechnology);
                    mFusionEngine.removeDataSource(mTechnology);
                    mCallback.onStopped(mTechnology, reason);
                }
            }
        }

        @Override
        public void onRangingData(RangingData data) {
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
                cancelScheduledTimeout();
                if (mStateMachine.transition(State.STARTING, State.STARTED)) {
                    // This is the first ranging data instance reported by the session, so start it.
                    mCallback.onStarted(null);
                }
                mCallback.onData(data);
                scheduleTimeout(
                        mParameters.getNoUpdatedDataTimeout(),
                        RangingSession.Callback.StoppedReason.NO_UPDATED_DATA_TIMEOUT);
            }
        }
    }

    private class NoOpFusionEngine extends FusionEngine {
        NoOpFusionEngine() {
            super(new DataFusers.PassthroughDataFuser());
        }

        @Override
        protected @NonNull Set<RangingTechnology> getDataSources() {
            return mAdapters.keySet();
        }

        @Override
        public void addDataSource(@NonNull RangingTechnology technology) {
        }

        @Override
        public void removeDataSource(@NonNull RangingTechnology technology) {
        }
    }

    @VisibleForTesting
    public void useAdapterForTesting(RangingTechnology technology, RangingAdapter adapter) {
        mAdapters.put(technology, adapter);
    }

    private enum State {
        STARTING,
        STARTED,
        STOPPED,
    }
}
