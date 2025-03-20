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

package com.android.server.ranging.session;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.os.Binder;
import android.os.SystemClock;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.SessionHandle;
import android.ranging.raw.RawResponderRangingConfig;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager.SessionListener;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.session.RangingSessionConfig.MulticastTechnologyConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;
import com.android.server.ranging.session.RangingSessionConfig.UnicastTechnologyConfig;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A multi-technology ranging session in the Android generic ranging service */
public class BaseRangingSession {
    private static final String TAG = BaseRangingSession.class.getSimpleName();

    private static final int NON_PRIVILEGED_RANGING_BG_APP_TIMEOUT_MS = 60_000;

    public static final String NON_PRIVILEGED_RANGING_BG_APP_TIMER_TAG =
            "RangingSessionNonPrivilegedBgAppTimeout";
    private final AttributionSource mAttributionSource;
    private final ListeningExecutorService mAdapterExecutor;

    protected final RangingInjector mInjector;
    protected final SessionHandle mSessionHandle;
    protected final RangingSessionConfig mSessionConfig;
    protected final SessionListener mSessionListener;

    private final AlarmManager mAlarmManager;
    private AlarmManager.OnAlarmListener mNonPrivilegedBgAppTimerListener;

    /* Lock for internal state. */
    private final Object mLock = new Object();

    /**
     * Keeps track of state of the ranging session.
     */
    @GuardedBy("mLock")
    private final StateMachine<State> mStateMachine;

    /**
     * Ranging adapters used for this session.
     * <ul>
     *    <li /> Each {@link TechnologyConfig} provided in the {@link RangingSessionConfig}
     *    configures a unique adapter.
     *    <li /> One adapter handles ranging for one technology.
     *    <li /> One adapter may handle ranging for multiple peers if the technology supports
     *    multicasting
     *    <li /> A session may contain multiple adapters for a single technology.
     * </ul>
     */
    @GuardedBy("mLock")
    private final ConcurrentMap<TechnologyConfig, RangingAdapter> mAdapters;
    @GuardedBy("mLock")
    private final ConcurrentMap<TechnologyConfig, @InternalReason Integer>
            mStopReasonOverride;

    /** State of all peers in the session */
    @GuardedBy("mLock")
    private final ConcurrentMap<RangingDevice, Peer> mPeers;

    /** The state of a peer that is ranging with the local device. */
    private class Peer {
        /** Technologies that this peer is ranging with. */
        public final Set<RangingTechnology> technologies;
        /** Fusion engine to use for this device. */
        public final FusionEngine fusionEngine;

        Peer(@NonNull RangingDevice device, @NonNull RangingTechnology initialTechnology) {
            technologies = Sets.newConcurrentHashSet(Set.of(initialTechnology));
            if (mSessionConfig.getSessionConfig().getSensorFusionParams().isSensorFusionEnabled()) {
                fusionEngine = new FilteringFusionEngine(
                        new DataFusers.PreferentialDataFuser(RangingTechnology.UWB),
                        mSessionConfig.getSessionConfig().isAngleOfArrivalNeeded(), mInjector);
            } else {
                fusionEngine = new NoOpFusionEngine(device);
            }
            fusionEngine.start(new FusionEngineListener(device));
        }

        public void setUsingTechnology(@NonNull RangingTechnology technology) {
            technologies.add(technology);
            fusionEngine.addDataSource(technology);
        }

        public void setNotUsingTechnology(@NonNull RangingTechnology technology) {
            technologies.remove(technology);
            fusionEngine.removeDataSource(technology);
        }
    }

    public BaseRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull RangingSessionConfig config,
            @NonNull SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        mInjector = injector;
        mAttributionSource = attributionSource;
        mSessionHandle = sessionHandle;
        mSessionConfig = config;
        mSessionListener = listener;
        mAdapterExecutor = adapterExecutor;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mPeers = new ConcurrentHashMap<>();
        mAdapters = new ConcurrentHashMap<>();
        mStopReasonOverride = new ConcurrentHashMap<>();
        mAlarmManager = mInjector.getContext().getSystemService(AlarmManager.class);
    }

    /** Start ranging in this session. */
    public void start(ImmutableSet<TechnologyConfig> technologyConfigs) {
        Log.v(TAG, "Starting session");
        synchronized (mLock) {
            mSessionListener.onConfigurationComplete(technologyConfigs);

            if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
                Log.w(TAG, "Failed transition STOPPED -> STARTING");
                onSessionClosed(InternalReason.INTERNAL_ERROR);
                return;
            }
            AttributionSource nonPrivilegedAttributionSource =
                    mInjector.getAnyNonPrivilegedAppInAttributionSource(mAttributionSource);

            for (TechnologyConfig config : technologyConfigs) {
                ImmutableSet<RangingDevice> peerDevices;

                if (config instanceof UnicastTechnologyConfig unicastConfig) {
                    peerDevices = ImmutableSet.of(unicastConfig.getPeerDevice());
                } else if (config instanceof MulticastTechnologyConfig multicastConfig) {
                    peerDevices = multicastConfig.getPeerDevices();
                } else {
                    Log.e(TAG, "Received unknown RangingTechnology subclass "
                            + config.getClass());
                    onSessionClosed(InternalReason.INTERNAL_ERROR);
                    return;
                }

                for (RangingDevice peerDevice : peerDevices) {
                    if (mPeers.containsKey(peerDevice)) {
                        mPeers.get(peerDevice).setUsingTechnology(config.getTechnology());
                    } else {
                        mPeers.put(peerDevice, new Peer(peerDevice, config.getTechnology()));
                    }
                }

                // Any calls to the corresponding technology stacks must be
                // done with a clear calling identity.
                long token = Binder.clearCallingIdentity();
                RangingAdapter adapter = mInjector.createAdapter(
                        mAttributionSource, config, mAdapterExecutor);
                mAdapters.put(config, adapter);
                Log.v(TAG, "Starting ranging with technology : " + config.getTechnology());
                adapter.start(config, nonPrivilegedAttributionSource, new AdapterListener(config));
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    public void addPeer(RawResponderRangingConfig params) {
        synchronized (mLock) {
            for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
                if (entry.getValue().isDynamicUpdatePeersSupported()) {
                    RangingDevice peerDevice = params.getRawRangingDevice().getRangingDevice();
                    mPeers.put(peerDevice, new Peer(peerDevice, entry.getKey().getTechnology()));
                    entry.getValue().addPeer(params);
                }
            }
        }
    }

    public void removePeer(RangingDevice device) {
        synchronized (mLock) {
            for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
                if (entry.getValue().isDynamicUpdatePeersSupported()) {
                    entry.getValue().removePeer(device);
                }
            }
        }
    }

    public void reconfigureInterval(int intervalSkipCount) {
        synchronized (mLock) {
            for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
                entry.getValue().reconfigureRangingInterval(intervalSkipCount);
            }
        }
    }

    public void appForegroundStateUpdated(boolean appInForeground) {
        synchronized (mLock) {
            for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
                entry.getValue().appForegroundStateUpdated(appInForeground);
                if (!appInForeground) {
                    startNonPrivilegedBgAppTimerIfNotSet();
                } else {
                    stopNonPrivilegedBgAppTimerIfSet();
                }
            }
        }
    }

    public void appInBackgroundTimeout() {
        synchronized (mLock) {
            for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
                entry.getValue().appInBackgroundTimeout();
            }
        }
    }

    /**
     * Starts a timer to detect if the app that started the UWB session is in the background
     * for longer than NON_PRIVILEGED_BG_APP_TIMEOUT_MS.
     */
    private void startNonPrivilegedBgAppTimerIfNotSet() {
        // Start a timer when the non-privileged app goes into the background.
        if (mNonPrivilegedBgAppTimerListener == null) {
            mNonPrivilegedBgAppTimerListener = () -> {
                Log.w(TAG, "Non-privileged app in background for longer than timeout");
                appInBackgroundTimeout();
            };
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + NON_PRIVILEGED_RANGING_BG_APP_TIMEOUT_MS,
                    NON_PRIVILEGED_RANGING_BG_APP_TIMER_TAG,
                    mNonPrivilegedBgAppTimerListener,
                    mInjector.getAlarmHandler());
        }
    }

    public void stopNonPrivilegedBgAppTimerIfSet() {
        // Stop the timer when the non-privileged app goes into the foreground.
        if (mNonPrivilegedBgAppTimerListener != null) {
            mAlarmManager.cancel(mNonPrivilegedBgAppTimerListener);
            mNonPrivilegedBgAppTimerListener = null;
        }
    }

    /** Stop ranging in this session. */
    public void stop() {
        stop(InternalReason.LOCAL_REQUEST);
    }

    /**
     * Stop all adapters in the session.
     * @param reason When we receive the
     * {@link RangingAdapter.Callback#onStopped(ImmutableSet, int)} callback for each of these
     * adapters, override the reason code provided in the callback with this one.
     * @return true if there are currently any active adapters in the session.
     */
    protected boolean stop(@InternalReason int reason) {
        Log.v(TAG, "Stop ranging, stopping all adapters");
        synchronized (mLock) {
            if (mStateMachine.getState() == State.STOPPING
                    || mStateMachine.getState() == State.STOPPED) {
                Log.v(TAG, "Ranging already stopping or stopped, skipping");
                return false;
            }
            stopNonPrivilegedBgAppTimerIfSet();
            mStateMachine.setState(State.STOPPING);

            // Any calls to the corresponding technology stacks must be
            // done with a clear calling identity.
            long token = Binder.clearCallingIdentity();
            boolean existsAdaptersWithActiveRanging = !mAdapters.isEmpty();
            for (TechnologyConfig config : mAdapters.keySet()) {
                if (reason != InternalReason.LOCAL_REQUEST) mStopReasonOverride.put(config, reason);
                mAdapters.get(config).stop();
            }
            Binder.restoreCallingIdentity(token);
            return existsAdaptersWithActiveRanging;
        }
    }

    /**
     * Stop all adapters associated with the provided {@code technologies}. When we receive the
     * {@link RangingAdapter.Callback#onStopped(ImmutableSet, int)} callback for each of these
     * adapters, override the reason code provided in the callback with the {@code reason} given
     * here.
     */
    protected void stopTechnologies(
            Set<RangingTechnology> technologies, @InternalReason int reason) {
        Log.v(TAG, "Stop ranging with technologies " + technologies);
        synchronized (mLock) {
            long token = Binder.clearCallingIdentity();
            for (TechnologyConfig config : mAdapters.keySet()) {
                if (technologies.contains(config.getTechnology())) {
                    if (reason != InternalReason.LOCAL_REQUEST) {
                        mStopReasonOverride.put(config, reason);
                    }
                    mAdapters.get(config).stop();
                }
            }
            Binder.restoreCallingIdentity(token);
        }
    }

    protected ImmutableSet<RangingTechnology> getTechnologiesUsedByPeer(RangingDevice device) {
        synchronized (mLock) {
            Peer peer = mPeers.get(device);
            if (peer == null) {
                return ImmutableSet.of();
            } else {
                return ImmutableSet.copyOf(peer.technologies);
            }
        }
    }

    /** Let subclasses override onSessionClosed behavior. */
    protected void onSessionClosed(@InternalReason int reason) {
        mSessionListener.onSessionClosed(reason);
    }

    private class AdapterListener implements RangingAdapter.Callback {
        private final TechnologyConfig mConfig;

        AdapterListener(@NonNull TechnologyConfig config) {
            mConfig = config;
        }

        @Override
        public void onStarted(@NonNull ImmutableSet<RangingDevice> peerDevices) {
            synchronized (mLock) {
                for (RangingDevice peerDevice : peerDevices) {
                    if (!mPeers.containsKey(peerDevice)) {
                        Log.w(TAG, "onStarted peer not found");
                        continue;
                    }
                    mStateMachine.transition(State.STARTING, State.STARTED);
                    mPeers.get(peerDevice).setUsingTechnology(mConfig.getTechnology());
                }
                mSessionListener.onTechnologyStarted(mConfig.getTechnology(), peerDevices);
            }
        }

        @Override
        public void onStopped(
                @NonNull ImmutableSet<RangingDevice> peerDevices, @InternalReason int reason
        ) {
            synchronized (mLock) {
                for (RangingDevice peerDevice : peerDevices) {
                    Peer peer = mPeers.get(peerDevice);
                    if (peer == null) {
                        Log.w(TAG, "onStopped peer not found");
                        continue;
                    }
                    peer.setNotUsingTechnology(mConfig.getTechnology());
                    if (peer.technologies.isEmpty()) {
                        peer.fusionEngine.stop();
                        mPeers.remove(peerDevice);
                    }
                }
                mSessionListener.onTechnologyStopped(
                        mConfig.getTechnology(), peerDevices, maybeOverridden(reason));
            }
        }

        @Override
        public void onRangingData(@NonNull RangingDevice peerDevice, @NonNull RangingData data) {
            synchronized (mLock) {
                if (mStateMachine.getState() != State.STOPPING
                        && mStateMachine.getState() != State.STOPPED
                ) {
                    mPeers.get(peerDevice).fusionEngine.feed(data);
                }
            }
        }

        @Override
        public void onClosed(@InternalReason int reason) {
            synchronized (mLock) {
                mAdapters.remove(mConfig);
                if (mAdapters.isEmpty()) {
                    mStateMachine.setState(State.STOPPED);
                    onSessionClosed(maybeOverridden(reason));
                }
                mStopReasonOverride.remove(mConfig);
            }
        }

        /** @return the (possibly overriding) reason code for the last state change. */
        @GuardedBy("mLock")
        private @InternalReason int maybeOverridden(@InternalReason int reason) {
            if (reason == InternalReason.LOCAL_REQUEST) {
                return Optional.ofNullable(mStopReasonOverride.get(mConfig)).orElse(reason);
            } else {
                return reason;
            }
        }
    }

    /** Listens for fusion engine events. */
    private class FusionEngineListener implements FusionEngine.Callback {
        private final RangingDevice mPeer;

        FusionEngineListener(@NonNull RangingDevice peer) {
            mPeer = peer;
        }

        @Override
        public void onData(@NonNull RangingData data) {
            synchronized (mLock) {
                if (mStateMachine.getState() != State.STOPPING
                        && mStateMachine.getState() != State.STOPPED
                ) {
                    mSessionListener.onResults(mPeer, data);
                }
            }
        }
    }

    private class NoOpFusionEngine extends FusionEngine {
        private final RangingDevice mPeer;

        NoOpFusionEngine(@NonNull RangingDevice peer) {
            super(new DataFusers.PassthroughDataFuser());
            mPeer = peer;
        }

        protected @NonNull Set<RangingTechnology> getDataSources() {
            return mPeers.get(mPeer).technologies;
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

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- Dump of RangingSession ----");
        pw.println("Session handle: " + mSessionHandle);
        pw.println("Attribution source: " + mAttributionSource);
        pw.println("Config: " + mSessionConfig);
        pw.println("Adapters:");
        for (RangingAdapter adapter : mAdapters.values()) {
            pw.println(adapter);
        }
        pw.println("Peers:");
        for (Peer peer : mPeers.values()) {
            pw.println(peer);
        }
        pw.println("---- Dump of RangingSession ----");
    }
}
