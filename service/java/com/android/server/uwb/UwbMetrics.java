/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.uwb;

import com.android.server.uwb.UwbSessionManager.UwbSession;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.proto.UwbStatsLog;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Calendar;
import java.util.Deque;

/**
 * A class to collect and report UWB metrics.
 */
public class UwbMetrics {
    private static final String TAG = "UwbMetrics";

    private static final int MAX_RANGING_SESSIONS = 128;
    private static final int MAX_RANGING_REPORTS = 1024;
    public static final int DISTANCE_FOM_DEFAULT = 100;
    public static final int INVALID_DISTANCE = 0xFFFF;
    private final UwbInjector mUwbInjector;
    private final Deque<RangingSessionStats> mRangingSessionList = new ArrayDeque<>();
    private final Deque<RangingReportEvent> mRangingReportList = new ArrayDeque<>();
    private int mNumApps = 0;
    private long mLastRangingDataLogTimeMs;
    private final Object mLock = new Object();
    /**
     * The class storing the stats of a ranging session.
     */
    public class RangingSessionStats {
        private int mSessionId;
        private int mChannel = 9;
        private long mStartTimeMs;
        private int mInitLatencyMs;
        private int mInitStatus;
        private int mActiveDuration;
        private int mRangingcount;
        private int mValidRangingCount;
        private int mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__UNKNOWN_STS;
        private boolean mIsInitiator;
        private boolean mIsController;
        private boolean mIsDiscoveredByFramework = false;
        private boolean mIsOutOfBand = true;

        RangingSessionStats(int sessionId) {
            mSessionId = sessionId;
            mStartTimeMs = mUwbInjector.getElapsedSinceBootMillis();
        }

        /**
         * Parse UWB profile parameters
         */
        public void parseParams(Params params) {
            if (params instanceof FiraOpenSessionParams) {
                parseFiraParams((FiraOpenSessionParams) params);
            } else if (params instanceof CccOpenRangingParams) {
                parseCccParams((CccOpenRangingParams) params);
            }
        }

        private void parseFiraParams(FiraOpenSessionParams params) {
            if (params.getStsConfig() == FiraParams.STS_CONFIG_STATIC) {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__STATIC;
            } else if (params.getStsConfig() == FiraParams.STS_CONFIG_DYNAMIC) {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__DYNAMIC;
            } else {
                mStsType = UwbStatsLog.UWB_SESSION_INITIATED__STS__PROVISIONED;
            }

            mIsInitiator = params.getDeviceRole() == FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
            mIsController = params.getDeviceType() == FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
            mChannel = params.getChannelNumber();
        }

        private void parseCccParams(CccOpenRangingParams params) {
            mChannel = params.getChannel();
        }

        private void convertInitStatus(int status) {
            mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__GENERAL_FAILURE;
            switch (status) {
                case UwbUciConstants.STATUS_CODE_OK:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SUCCESS;
                    break;
                case UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SESSION_EXCEEDED;
                    break;
                case UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__SESSION_DUPLICATE;
                    break;
                case UwbUciConstants.STATUS_CODE_INVALID_PARAM:
                case UwbUciConstants.STATUS_CODE_INVALID_RANGE:
                case UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE:
                    mInitStatus = UwbStatsLog.UWB_SESSION_INITIATED__STATUS__BAD_PARAMS;
                    break;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("startTime=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mStartTimeMs);
                sb.append(mStartTimeMs == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", sessionId=").append(mSessionId);
                sb.append(", initLatencyMs=").append(mInitLatencyMs);
                sb.append(", initStatus=").append(mInitStatus);
                return sb.toString();
            }
        }
    }

    private class RangingReportEvent {
        private int mSessionId;
        private int mNlos;
        private int mDistanceCm;
        private int mAzimuthDegree;
        private int mAzimuthFom;
        private int mElevationDegree;
        private int mElevationFom;
        private long mTimeStampMs;

        RangingReportEvent(int sessionId, int nlos, int distanceCm,
                int azimuthDegree, int azimuthFom,
                int elevationDegree, int elevationFom) {
            mSessionId = sessionId;
            mTimeStampMs = mUwbInjector.getElapsedSinceBootMillis();
            mNlos = nlos;
            mDistanceCm = distanceCm;
            mAzimuthDegree = azimuthDegree;
            mAzimuthFom = azimuthFom;
            mElevationDegree = elevationDegree;
            mElevationFom = elevationFom;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("mTimeStampMs=");
            Calendar c = Calendar.getInstance();
            synchronized (mLock) {
                c.setTimeInMillis(mTimeStampMs);
                sb.append(mTimeStampMs == 0 ? "            <null>" :
                        String.format("%tm-%td %tH:%tM:%tS.%tL", c, c, c, c, c, c));
                sb.append(", sessionId=").append(mSessionId);
                sb.append(", Nlos=").append(mNlos);
                sb.append(", DistanceCm=").append(mDistanceCm);
                sb.append(", AzimuthDegree=").append(mAzimuthDegree);
                sb.append(", AzimuthFom=").append(mAzimuthFom);
                sb.append(", ElevationDegree=").append(mElevationDegree);
                sb.append(", ElevationFom=").append(mElevationFom);
                return sb.toString();
            }
        }
    }

    public UwbMetrics(UwbInjector uwbInjector) {
        mUwbInjector = uwbInjector;
    }

    /**
     * Log the ranging session initialization event
     */
    public void logRangingSessionInitEvent(UwbSession uwbSession, int status) {
        synchronized (mLock) {
            // If past maximum events, start removing the oldest
            while (mRangingSessionList.size() >= MAX_RANGING_SESSIONS) {
                mRangingSessionList.removeFirst();
            }
            RangingSessionStats session = new RangingSessionStats(uwbSession.getSessionId());
            session.parseParams(uwbSession.getParams());
            session.convertInitStatus(status);
            mRangingSessionList.add(session);
            UwbStatsLog.write(UwbStatsLog.UWB_SESSION_INITED, uwbSession.getProfileType(),
                    session.mStsType, session.mIsInitiator,
                    session.mIsController, session.mIsDiscoveredByFramework, session.mIsOutOfBand,
                    session.mChannel, session.mInitStatus,
                    session.mInitLatencyMs, session.mInitLatencyMs / 20);
        }
    }

    /**
     * Log the usage of API from a new App
     */
    public void logNewAppUsage() {
        mNumApps++;
    }

    /**
     * Log the ranging measurement result
     */
    public void logRangingResult(int profileType, UwbRangingData rangingData) {
        if (rangingData.getRangingMeasuresType()
                != UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY
                || rangingData.getNoOfRangingMeasures() < 1) {
            return;
        }

        UwbTwoWayMeasurement[] uwbTwoWayMeasurement = rangingData.getRangingTwoWayMeasures();
        UwbTwoWayMeasurement measurement = uwbTwoWayMeasurement[0];

        int rangingStatus = measurement.getRangingStatus();
        if (rangingStatus != FiraParams.STATUS_CODE_OK) {
            return;
        }

        int sessionId = (int) rangingData.getSessionId();
        int distanceCm = measurement.getDistance();
        int azimuthDegree = (int) measurement.getAoaAzimuth();
        int azimuthFom = measurement.getAoaAzimuthFom();
        int elevationDegree = (int) measurement.getAoaElevation();
        int elevationFom = measurement.getAoaElevationFom();
        int nlos = getNlos(measurement);

        while (mRangingReportList.size() >= MAX_RANGING_REPORTS) {
            mRangingReportList.removeFirst();
        }
        RangingReportEvent report = new RangingReportEvent(sessionId, nlos, distanceCm,
                azimuthDegree, azimuthFom, elevationDegree, elevationFom);
        mRangingReportList.add(report);

        long currTimeMs = mUwbInjector.getElapsedSinceBootMillis();
        if ((currTimeMs - mLastRangingDataLogTimeMs) < mUwbInjector.getDeviceConfigFacade()
                .getRangingResultLogIntervalMs()) {
            return;
        }
        mLastRangingDataLogTimeMs = currTimeMs;

        boolean isDistanceValid = distanceCm != INVALID_DISTANCE;
        boolean isAzimuthValid = azimuthFom > 0;
        boolean isElevationValid = elevationFom > 0;
        int distance50Cm = isDistanceValid ? distanceCm / 50 : 0;
        int azimuth10Degree = isAzimuthValid ? azimuthDegree / 10 : 0;
        int elevation10Degree = isElevationValid ? elevationDegree / 10 : 0;
        UwbStatsLog.write(UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED, profileType, nlos,
                isDistanceValid, distanceCm, distance50Cm, DISTANCE_FOM_DEFAULT,
                isAzimuthValid, azimuthDegree, azimuth10Degree, azimuthFom,
                isElevationValid, elevationDegree, elevation10Degree, elevationFom);
    }

    private int getNlos(UwbTwoWayMeasurement measurement) {
        int nlos = measurement.getNLoS();
        if (nlos == 0) {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__LOS;
        } else if (nlos == 1) {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS;
        } else {
            return UwbStatsLog.UWB_RANGING_MEASUREMENT_RECEIVED__NLOS__NLOS_UNKNOWN;
        }
    }

    /**
     * Dump the UWB logs
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        synchronized (mLock) {
            pw.println("Dump of UwbMetrics");
            pw.println("mRangingSessionList");
            for (RangingSessionStats stats: mRangingSessionList) {
                pw.println(stats.toString());
            }
            pw.println("mRangingReportList");
            for (RangingReportEvent event: mRangingReportList) {
                pw.println(event.toString());
            }
            pw.println("mNumApps=" + mNumApps);
        }
    }
}
