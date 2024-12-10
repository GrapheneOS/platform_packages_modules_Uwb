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

import android.content.AttributionSource;
import android.os.Binder;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.SessionHandle;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingServiceManager;
import com.android.server.ranging.RangingTechnology;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** A multi-technology ranging session in the Android generic ranging service */
public class BaseRangingSession {
    private static final String TAG = BaseRangingSession.class.getSimpleName();

    private final RangingInjector mInjector;
    private final AttributionSource mAttributionSource;
    private final RangingServiceManager.SessionListener mSessionListener;
    private final ListeningExecutorService mAdapterExecutor;

    protected final SessionHandle mSessionHandle;
    protected final RangingSessionConfig mConfig;

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
            if (mConfig.getSensorFusionConfig().isSensorFusionEnabled()) {
                fusionEngine = new FilteringFusionEngine(
                        new DataFusers.PreferentialDataFuser(RangingTechnology.UWB));
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
            @NonNull RangingServiceManager.SessionListener listener,
            @NonNull ListeningExecutorService adapterExecutor
    ) {
        mInjector = injector;
        mAttributionSource = attributionSource;
        mSessionHandle = sessionHandle;
        mConfig = config;
        mSessionListener = listener;
        mAdapterExecutor = adapterExecutor;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mPeers = new ConcurrentHashMap<>();
        mAdapters = new ConcurrentHashMap<>();
    }

    /** Start ranging in this session. */
    public void start(ImmutableSet<TechnologyConfig> technologyConfigs) {
        synchronized (mLock) {
            if (!mStateMachine.transition(State.STOPPED, State.STARTING)) {
                Log.w(TAG, "Failed transition STOPPED -> STARTING");
                return;
            }

            for (TechnologyConfig config : technologyConfigs) {
                ImmutableSet<RangingDevice> peerDevices;

                if (config instanceof UnicastTechnologyConfig unicastConfig) {
                    peerDevices = ImmutableSet.of(unicastConfig.getPeerDevice());
                } else if (config instanceof MulticastTechnologyConfig multicastConfig) {
                    peerDevices = multicastConfig.getPeerDevices();
                } else {
                    Log.e(TAG,
                            "Received unsupported config for technology " + config.getTechnology());
                    mSessionListener.onSessionStopped(
                            RangingAdapter.Callback.ClosedReason.FAILED_TO_START);
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
                        config, mConfig.getDeviceRole(), mAdapterExecutor);
                mAdapters.put(config, adapter);
                adapter.start(config, new AdapterListener(config));
                Binder.restoreCallingIdentity(token);
            }
        }
    }

    public void addPeer(RangingDevice params) {
        //TODO: Implement this
        throw new IllegalArgumentException("Dynamic addition of raw peer not supported yet");
    }

    public void removePeer(RangingDevice params) {
        //TODO: Implement this
        throw new IllegalArgumentException("Dynamic addition of raw peer not supported yet");
    }

    public void reconfigureInterval(int intervalSkipCount) {
        //TODO: Implement this
        throw new IllegalArgumentException("Dynamic addition of raw peer not supported yet");
    }

    /** Stop ranging in this session. */
    public void stop() {
        synchronized (mLock) {
            if (mStateMachine.getState() == State.STOPPING
                    || mStateMachine.getState() == State.STOPPED) {
                Log.v(TAG, "Ranging already stopping or stopped, skipping");
                return;
            }
            mStateMachine.setState(State.STOPPING);

            // Any calls to the corresponding technology stacks must be
            // done with a clear calling identity.
            long token = Binder.clearCallingIdentity();
            for (RangingAdapter adapter : mAdapters.values()) {
                adapter.stop();
            }
            Binder.restoreCallingIdentity(token);
        }
    }

    private class AdapterListener implements RangingAdapter.Callback {
        private final TechnologyConfig mConfig;

        AdapterListener(@NonNull TechnologyConfig config) {
            mConfig = config;
        }

        @Override
        public void onStarted(@NonNull RangingDevice peerDevice) {
            synchronized (mLock) {
                mStateMachine.transition(State.STARTING, State.STARTED);
                mPeers.get(peerDevice).setUsingTechnology(mConfig.getTechnology());
                mSessionListener.onTechnologyStarted(peerDevice, mConfig.getTechnology());
            }
        }

        @Override
        public void onStopped(@NonNull RangingDevice peerDevice) {
            synchronized (mLock) {
                Peer peer = mPeers.get(peerDevice);
                peer.setNotUsingTechnology(mConfig.getTechnology());
                mSessionListener.onTechnologyStopped(peerDevice, mConfig.getTechnology());
                if (peer.technologies.isEmpty()) {
                    peer.fusionEngine.stop();
                    mPeers.remove(peerDevice);
                }
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
        public void onClosed(@ClosedReason int reason) {
            synchronized (mLock) {
                mAdapters.remove(mConfig);
                if (mAdapters.isEmpty()) {
                    mStateMachine.setState(State.STOPPED);
                    mSessionListener.onSessionStopped(reason);
                }
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
