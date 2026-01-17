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
import android.ranging.MotionState;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.raw.RawResponderRangingConfig;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager.SessionListener;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.common.StateMachine;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.heuristic.RangeHeuristicEventFactory;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.session.ConfigurationManager.MulticastTechnologyConfig;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;
import com.android.server.ranging.session.ConfigurationManager.UnicastTechnologyConfig;

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
    protected final SessionConfig mSessionConfig;
    protected final RangeHeuristicEventFactory mEventFactory;
    protected final SessionListener mSessionListener;

    private final AlarmManager mAlarmManager;
    private AlarmManager.OnAlarmListener mNonPrivilegedBgAppTimerListener;

    /**
     * Keeps track of state of the ranging session.
     */
    private final StateMachine<State> mStateMachine;

    /**
     * Ranging adapters used for this session.
     * <ul>
     *    <li /> Each {@link TechnologyConfig} provided to {@link
     *    BaseRangingSession#start(ImmutableSet)} configures a unique adapter.
     *    <li /> One adapter handles ranging for one technology.
     *    <li /> One adapter may handle ranging for multiple peers if the technology supports
     *    multicasting
     *    <li /> A session may contain multiple adapters for a single technology.
     * </ul>
     */
    @GuardedBy("this")
    private final ConcurrentMap<TechnologyConfig, RangingAdapter> mAdapters;
    @GuardedBy("this")
    private final ConcurrentMap<TechnologyConfig, @InternalReason Integer> mStopReasonOverride;

    /** State of all peers in the session */
    @GuardedBy("this")
    private final ConcurrentMap<RangingDevice, Peer> mPeers;

    public BaseRangingSession(
            @NonNull AttributionSource attributionSource,
            @NonNull SessionHandle sessionHandle,
            @NonNull RangingInjector injector,
            @NonNull SessionConfig config,
            @NonNull SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        mInjector = injector;
        mAttributionSource = attributionSource;
        mSessionHandle = sessionHandle;
        mSessionConfig = config;
        mSessionListener = listener;
        mAdapterExecutor = adapterExecutor;
        mStateMachine = new StateMachine<>(State.STOPPED, this);
        mPeers = new ConcurrentHashMap<>();
        mAdapters = new ConcurrentHashMap<>();
        mStopReasonOverride = new ConcurrentHashMap<>();
        mAlarmManager = mInjector.getContext().getSystemService(AlarmManager.class);
        mEventFactory = new RangeHeuristicEventFactory(mAdapterExecutor);
    }

    /** Start ranging in this session with the provided configs. */
    public synchronized void start(ImmutableSet<TechnologyConfig> technologyConfigs) {
        if (mStateMachine.transition(State.STOPPED, State.STARTING)) {
            Log.i(TAG, "Starting session");
            mSessionListener.onConfigurationComplete(technologyConfigs);
        }

        AttributionSource nonPrivilegedAttributionSource =
                mInjector.getAnyNonPrivilegedAppInAttributionSource(mAttributionSource);

        for (TechnologyConfig config : Sets.difference(technologyConfigs, mAdapters.keySet())) {
            ImmutableSet<RangingDevice> peerDevices;

            if (config instanceof UnicastTechnologyConfig unicastConfig) {
                peerDevices = ImmutableSet.of(unicastConfig.getPeerDevice());
            } else if (config instanceof MulticastTechnologyConfig multicastConfig) {
                peerDevices = multicastConfig.getPeerDevices();
            } else if (config instanceof com.android.server.ranging.uwb.DlTdoaConfig) {
                // DL-TDOA is peerless, so we create an empty set of peer devices.
                peerDevices = ImmutableSet.of();
            } else {
                Log.e(TAG, "Received unknown RangingTechnology subclass "
                        + config.getClass());
                onSessionClosed(InternalReason.INTERNAL_ERROR);
                return;
            }

            peerDevices.forEach(device ->
                    mPeers.computeIfAbsent(device, unused -> createPeer(device))
                            .setUsingTechnology(config));

            // Any calls to the corresponding technology stacks must be
            // done with a clear calling identity.
            long token = Binder.clearCallingIdentity();

            RangingAdapter adapter = mInjector.createAdapter(
                    mAttributionSource, config, mAdapterExecutor, this);
            mAdapters.put(config, adapter);
            Log.v(TAG, "Starting ranging with technology : " + config.getTechnology());
            adapter.start(config, nonPrivilegedAttributionSource, new AdapterListener(config));
            Binder.restoreCallingIdentity(token);
        }
    }

    public synchronized void addPeer(RawResponderRangingConfig params) {
        for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
            if (entry.getValue().isDynamicUpdatePeersSupported()) {
                RangingDevice peerDevice = params.getRawRangingDevice().getRangingDevice();
                mPeers.computeIfAbsent(peerDevice, unused -> createPeer(peerDevice))
                        .setUsingTechnology(entry.getKey());
                entry.getValue().addPeer(params);
            }
        }
    }

    private Peer createPeer(RangingDevice device) {
        return new Peer(device, getPeerType(device), mSessionHandle, mSessionConfig,
                new FusionEngineListener(device), mEventFactory, mAdapterExecutor,
                mInjector);
    }

    public synchronized void removePeer(RangingDevice device) {
        for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
            if (entry.getValue().isDynamicUpdatePeersSupported()) {
                entry.getValue().removePeer(device);
            }
        }
    }

    public synchronized void reconfigureInterval(int intervalSkipCount) {
        for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
            entry.getValue().reconfigureRangingInterval(intervalSkipCount);
        }
    }

    public synchronized void appForegroundStateUpdated(boolean appInForeground) {
        for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
            entry.getValue().appForegroundStateUpdated(appInForeground);
            if (!appInForeground) {
                startNonPrivilegedBgAppTimerIfNotSet();
            } else {
                stopNonPrivilegedBgAppTimerIfSet();
            }
        }
    }

    public synchronized void appInBackgroundTimeout() {
        for (Map.Entry<TechnologyConfig, RangingAdapter> entry : mAdapters.entrySet()) {
            entry.getValue().appInBackgroundTimeout();
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
    protected synchronized boolean stop(@InternalReason int reason) {
        Log.v(TAG, "Stop ranging, stopping all adapters");
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

    /**
     * Stop all adapters associated with the provided {@code technologies}. When we receive the
     * {@link RangingAdapter.Callback#onStopped(ImmutableSet, int)} callback for each of these
     * adapters, override the reason code provided in the callback with the {@code reason} given
     * here.
     */
    protected synchronized void stopTechnologies(
            Set<RangingTechnology> technologies, @InternalReason int reason) {
        Log.v(TAG, "Stop ranging with technologies " + technologies);
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

    protected synchronized ImmutableSet<RangingTechnology> getTechnologiesUsedByPeer(
            RangingDevice device
    ) {
        Peer peer = mPeers.get(device);
        if (peer == null) {
            return ImmutableSet.of();
        } else {
            return ImmutableSet.copyOf(peer.getActiveTechnologies());
        }
    }

    /** Let subclasses override how onTechnologyStarted gets called. */
    protected void onTechnologyStarted(
            @NonNull RangingTechnology technology, @NonNull Set<RangingDevice> peers
    ) {
        mSessionListener.onTechnologyStarted(technology, peers);
    }

    /** Let subclasses override how onTechnologyStopped gets called. */
    protected void onTechnologyStopped(
            @NonNull RangingTechnology technology, @NonNull Set<RangingDevice> peers,
            @InternalReason int reason
    ) {
        mSessionListener.onTechnologyStopped(technology, peers, reason);
    }

    /** Let subclasses override to inspect ranging data. */
    protected void onResults(@NonNull RangingDevice peer, @NonNull RangingData data) {
        mSessionListener.onResults(peer, data);
    }

    protected void reportPeerMotion(
            @NonNull RangingDevice peer, @NonNull MotionState motion) {
        mSessionListener.onMotionReceived(peer, motion);
    }

    /** Let subclasses override how onSessionClosed gets called. */
    protected void onSessionClosed(@InternalReason int reason) {
        mSessionListener.onSessionClosed(reason);
    }

    /** Let subclasses provide the type of each peer. */
    protected DeviceType getPeerType(RangingDevice peer) {
        return DeviceType.Unknown;
    }

    private class AdapterListener implements RangingAdapter.Callback {
        private final TechnologyConfig mConfig;

        AdapterListener(@NonNull TechnologyConfig config) {
            mConfig = config;
        }

        @Override
        public void onStarted(@NonNull ImmutableSet<RangingDevice> peerDevices) {
            synchronized (BaseRangingSession.this) {
                for (RangingDevice peerDevice : peerDevices) {
                    if (!mPeers.containsKey(peerDevice)) {
                        Log.w(TAG, "onStarted peer not found");
                        continue;
                    }
                    mStateMachine.transition(State.STARTING, State.STARTED);
                    mPeers.get(peerDevice).setUsingTechnology(mConfig);
                }
                onTechnologyStarted(mConfig.getTechnology(), peerDevices);
            }
        }

        @Override
        public void onStopped(
                @NonNull ImmutableSet<RangingDevice> peerDevices, @InternalReason int reason
        ) {
            synchronized (BaseRangingSession.this) {
                for (RangingDevice peerDevice : peerDevices) {
                    Peer peer = mPeers.get(peerDevice);
                    if (peer == null) {
                        Log.w(TAG, "onStopped peer not found");
                        continue;
                    }
                    peer.setNotUsingTechnology(mConfig.getTechnology());
                    if (peer.getActiveTechnologies().isEmpty()) {
                        mPeers.remove(peerDevice).close();
                    }
                }
                onTechnologyStopped(mConfig.getTechnology(), peerDevices, maybeOverridden(reason));
            }
        }

        @Override
        public void onRangingData(@NonNull RangingDevice peerDevice, @NonNull RangingData data) {
            synchronized (BaseRangingSession.this) {
                if (mStateMachine.getState() != State.STOPPING
                        && mStateMachine.getState() != State.STOPPED
                ) {
                    mPeers.get(peerDevice).feedToFusionEngine(data);
                }
            }
        }

        @Override
        public void onClosed(@InternalReason int reason) {
            synchronized (BaseRangingSession.this) {
                mAdapters.remove(mConfig);
                if (mAdapters.isEmpty()) {
                    mStateMachine.setState(State.STOPPED);
                    onSessionClosed(maybeOverridden(reason));
                }
                mStopReasonOverride.remove(mConfig);
            }
        }

        /** @return the (possibly overriding) reason code for the last state change. */
        @GuardedBy("BaseRangingSession.this")
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
            synchronized (BaseRangingSession.this) {
                if (mStateMachine.getState() != State.STOPPING
                        && mStateMachine.getState() != State.STOPPED
                ) {
                    onResults(mPeer, data);
                }
            }
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
