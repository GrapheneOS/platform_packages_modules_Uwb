/*
 * Copyright 2025 The Android Open Source Project
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

import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.fusion.DataFusers;
import com.android.server.ranging.fusion.FilteringFusionEngine;
import com.android.server.ranging.fusion.FusionEngine;
import com.android.server.ranging.heuristic.RangeHeuristicEventFactory;
import com.android.server.ranging.heuristic.RangeHeuristicEventFactory.RangeHeuristicEvent;
import com.android.server.ranging.heuristic.StreakCounter;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;
import com.android.server.ranging.telemetry.SessionTelemetryLogger;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** The state of a peer that is ranging with the local device. */
class Peer implements AutoCloseable {
    private static final String TAG = Peer.class.getSimpleName();
    private static final int PEER_DISCONNECT_STREAK = -5;

    private final RangingDevice mDevice;
    private final DeviceType mDeviceType;
    private final SessionHandle mSessionHandle;
    /** Technologies that this peer is ranging with. */
    private final ConcurrentHashMap<RangingTechnology, TechnologyInfo> mTechnologies;
    /** Fusion engine to use for this device. */
    private final FusionEngine mFusionEngine;

    private final RangeHeuristicEventFactory mEventFactory;
    private final Executor mExecutor;
    private final RangingInjector mInjector;

    private static class TechnologyInfo {
        private final StreakCounter mStreakCounter;
        private final RangeHeuristicEvent mDisconnectEvent;
        private @Nullable RangingData mLastData = null;

        TechnologyInfo(StreakCounter streakCounter, RangeHeuristicEvent disconnectEvent) {
            mStreakCounter = streakCounter;
            mDisconnectEvent = disconnectEvent;
        }
    }

    Peer(
            @NonNull RangingDevice device, @NonNull DeviceType deviceType,
            @NonNull SessionHandle sessionHandle, @NonNull SessionConfig sessionConfig,
            @NonNull FusionEngine.Callback engineListener,
            @NonNull RangeHeuristicEventFactory eventFactory, @NonNull Executor executor,
            @NonNull RangingInjector injector
    ) {
        mDevice = device;
        mDeviceType = deviceType;
        mSessionHandle = sessionHandle;
        mTechnologies = new ConcurrentHashMap<>();
        mEventFactory = eventFactory;
        mExecutor = executor;
        mInjector = injector;

        if (sessionConfig.getSensorFusionParams().isSensorFusionEnabled()) {
            mFusionEngine = new FilteringFusionEngine(
                    new DataFusers.PreferentialDataFuser(RangingTechnology.UWB),
                    sessionConfig.isAngleOfArrivalNeeded(), mInjector);
        } else {
            mFusionEngine = new NoOpFusionEngine();
        }
        mFusionEngine.start(data -> {
            synchronized (Peer.this) {
                mTechnologies.forEach((technology, info) -> {
                    if (data.getRangingTechnology() == technology.getValue()) {
                        info.mLastData = data;
                    }
                    info.mStreakCounter.onData(data);
                });
            }
            engineListener.onData(data);
        });
    }

    public synchronized void setUsingTechnology(@NonNull TechnologyConfig config) {
        StreakCounter counter = new StreakCounter(config, mExecutor, mInjector);

        RangeHeuristicEvent disconnectEvent = mEventFactory.when(
                counter.count().threshold(streak -> streak >= PEER_DISCONNECT_STREAK));
        disconnectEvent.onNextOccurrence(unused -> onDisconnect(config.getTechnology()));

        mTechnologies.put(
                config.getTechnology(),
                new TechnologyInfo(counter, disconnectEvent));
        mFusionEngine.addDataSource(config.getTechnology());
    }

    public synchronized void setNotUsingTechnology(@NonNull RangingTechnology technology) {
        mTechnologies.remove(technology).mDisconnectEvent.cancel();
        mFusionEngine.removeDataSource(technology);
    }

    public synchronized Set<RangingTechnology> getActiveTechnologies() {
        return mTechnologies.keySet();
    }

    /**
     * Provide unfused data to the peer's fusion engine. When the fusion engine has data to produce,
     * it will notify the provided {@link FusionEngine.Callback} asynchronously.
     */
    public void feedToFusionEngine(@NonNull RangingData data) {
        mFusionEngine.feed(data);
    }

    private synchronized void onDisconnect(RangingTechnology technology) {
        Log.w(TAG, "Peer " + mDevice + " went " + PEER_DISCONNECT_STREAK
                + " ranging intervals with no data from " + technology);
        RangingData lastData = mTechnologies.get(technology).mLastData;
        SessionTelemetryLogger logger = mInjector.getTelemetryManager().getLogger(mSessionHandle);
        if (lastData != null && logger != null) {
            logger.logPeerDisconnected(mDeviceType, lastData);
        }
    }

    @Override
    public void close() {
        mTechnologies.values().forEach(info -> info.mDisconnectEvent.cancel());
        mTechnologies.clear();
        mFusionEngine.stop();
    }

    private class NoOpFusionEngine extends FusionEngine {
        NoOpFusionEngine() {
            super(new DataFusers.PassthroughDataFuser());
        }

        protected @NonNull Set<RangingTechnology> getDataSources() {
            return getActiveTechnologies();
        }

        public void addDataSource(@NonNull RangingTechnology technology) {
        }

        public void removeDataSource(@NonNull RangingTechnology technology) {
        }
    }
}
