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

package com.android.server.ranging.metrics;

import static android.ranging.RangingConfig.RANGING_SESSION_RAW;

import android.content.AttributionSource;
import android.ranging.RangingConfig;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.RangingUtils.StateMachine;

public class SessionMetricsLogger {
    private final SessionHandle mSessionHandle;
    private final @RangingPreference.DeviceRole int mDeviceRole;
    private final @RangingConfig.RangingSessionType int mSessionType;
    private final StateMachine<State> mStateMachine;
    private final AttributionSource mAttributionSource;
    private final RangingInjector mInjector;

    private long mLastStateChangeTimestampMs;

    public static SessionMetricsLogger startLogging(
            SessionHandle sessionHandle,
            @RangingPreference.DeviceRole int deviceRole,
            @RangingConfig.RangingSessionType int sessionType,
            AttributionSource attributionSource,
            RangingInjector injector
    ) {
        return new SessionMetricsLogger(sessionHandle, deviceRole, sessionType, attributionSource,
                injector);
    }

    private SessionMetricsLogger(
            SessionHandle sessionHandle,
            @RangingPreference.DeviceRole int deviceRole,
            @RangingConfig.RangingSessionType int sessionType,
            AttributionSource attributionSource,
            RangingInjector injector
    ) {
        mSessionHandle = sessionHandle;
        mDeviceRole = deviceRole;
        mSessionType = sessionType;
        mStateMachine = new StateMachine<>(
                sessionType == RANGING_SESSION_RAW
                        ? State.STARTING
                        : State.OOB);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
        mAttributionSource = attributionSource;
        mInjector = injector;
    }

    public synchronized void logSessionConfigured(int numPeers) {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_SESSION_CONFIGURED,
                mSessionHandle.hashCode(),
                mStateMachine.getState() == State.OOB
                        ? System.currentTimeMillis() - mLastStateChangeTimestampMs
                        : 0,
                coerceUnknownEnumValueToZero(mSessionType, 2),
                coerceUnknownEnumValueToZero(mDeviceRole, 2),
                numPeers);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
        mStateMachine.setState(State.STARTING);
    }

    public synchronized void logSessionStarted() {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_SESSION_STARTED,
                mSessionHandle.hashCode(),
                mAttributionSource.getUid(),
                System.currentTimeMillis() - mLastStateChangeTimestampMs,
                mInjector.isPrivilegedApp(
                        mAttributionSource.getUid(),
                        mAttributionSource.getPackageName()));
        mLastStateChangeTimestampMs = System.currentTimeMillis();
        mStateMachine.setState(State.RANGING);
    }

    public synchronized void logTechnologyStarted(RangingTechnology technology, int numPeers) {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_TECHNOLOGY_STARTED,
                mSessionHandle.hashCode(),
                coerceUnknownEnumValueToZero(
                        technology.getValue(), RangingTechnology.TECHNOLOGIES.size()),
                numPeers);
    }

    public synchronized void logTechnologyStopped(
            RangingTechnology technology, int numPeers, @InternalReason int reason
    ) {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_TECHNOLOGY_STOPPED,
                mSessionHandle.hashCode(),
                coerceUnknownEnumValueToZero(
                        technology.getValue(), RangingTechnology.TECHNOLOGIES.size()),
                coerceUnknownEnumValueToZero(
                        mStateMachine.getState().toInt(), State.values().length),
                reason,
                numPeers);
    }

    public synchronized void logSessionClosed(@InternalReason int reason) {
        RangingStatsLog.write(
                RangingStatsLog.RANGING_SESSION_CLOSED,
                mSessionHandle.hashCode(),
                coerceUnknownEnumValueToZero(
                        mStateMachine.getState().toInt(), State.values().length),
                System.currentTimeMillis() - mLastStateChangeTimestampMs,
                reason);
        mLastStateChangeTimestampMs = System.currentTimeMillis();
    }

    private int coerceUnknownEnumValueToZero(int enumValue, int numEnumValues) {
        if (enumValue >= numEnumValues) {
            return 0;
        } else {
            return enumValue + 1;
        }
    }

    private enum State {
        OOB(0),
        STARTING(1),
        RANGING(2);

        private final int mValue;

        State(int value) {
            mValue = value;
        }

        public final int toInt() {
            return mValue;
        }
    }
}
