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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;

import static com.android.server.uwb.data.UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;

import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_DISABLE;
import static com.google.uwb.support.fira.FiraParams.RangeDataNtfConfigCapabilityFlag.HAS_RANGE_DATA_NTF_CONFIG_ENABLE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.AttributionSource;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingChangeReason;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.INativeUwbManager;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.proto.UwbStatsLog;
import com.android.server.uwb.util.ArrayUtils;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UwbSessionManager implements INativeUwbManager.SessionNotification {

    private static final String TAG = "UwbSessionManager";
    @VisibleForTesting
    public static final int SESSION_OPEN_RANGING = 1;
    @VisibleForTesting
    public static final int SESSION_START_RANGING = 2;
    @VisibleForTesting
    public static final int SESSION_STOP_RANGING = 3;
    @VisibleForTesting
    public static final int SESSION_RECONFIG_RANGING = 4;
    @VisibleForTesting
    public static final int SESSION_CLOSE = 5;
    @VisibleForTesting
    public static final int SESSION_ON_DEINIT = 6;

    // TODO: don't expose the internal field for testing.
    @VisibleForTesting
    final ConcurrentHashMap<Integer, UwbSession> mSessionTable = new ConcurrentHashMap();
    final ConcurrentHashMap<Integer, List<UwbSession>> mNonPrivilegedUidToFiraSessionsTable =
            new ConcurrentHashMap();
    private final ActivityManager mActivityManager;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbMetrics mUwbMetrics;
    private final UwbConfigurationManager mConfigurationManager;
    private final UwbSessionNotificationManager mSessionNotificationManager;
    private final UwbInjector mUwbInjector;
    private final AlarmManager mAlarmManager;
    private final int mMaxSessionNumber;
    private final Looper mLooper;
    private final EventTask mEventTask;

    private Boolean mIsRangeDataNtfConfigEnableDisableSupported;

    public UwbSessionManager(
            UwbConfigurationManager uwbConfigurationManager,
            NativeUwbManager nativeUwbManager, UwbMetrics uwbMetrics,
            UwbSessionNotificationManager uwbSessionNotificationManager,
            UwbInjector uwbInjector, AlarmManager alarmManager, ActivityManager activityManager,
            Looper serviceLooper) {
        mNativeUwbManager = nativeUwbManager;
        mNativeUwbManager.setSessionListener(this);
        mUwbMetrics = uwbMetrics;
        mConfigurationManager = uwbConfigurationManager;
        mSessionNotificationManager = uwbSessionNotificationManager;
        mUwbInjector = uwbInjector;
        mAlarmManager = alarmManager;
        mActivityManager = activityManager;
        mMaxSessionNumber = mNativeUwbManager.getMaxSessionNumber();
        mLooper = serviceLooper;
        mEventTask = new EventTask(serviceLooper);
        registerUidImportanceTransitions();
    }

    private boolean isRangeDataNtfConfigEnableDisableSupported() {
        if (mIsRangeDataNtfConfigEnableDisableSupported == null) {
            GenericSpecificationParams specificationParams =
                    mUwbInjector.getUwbServiceCore().getCachedSpecificationParams(null);
            if (specificationParams == null) return false;
            EnumSet<FiraParams.RangeDataNtfConfigCapabilityFlag> supportedRangeDataNtfConfigs =
                    specificationParams.getFiraSpecificationParams()
                            .getRangeDataNtfConfigCapabilities();
            mIsRangeDataNtfConfigEnableDisableSupported =
                    supportedRangeDataNtfConfigs.containsAll(EnumSet.of(
                            HAS_RANGE_DATA_NTF_CONFIG_DISABLE,
                            HAS_RANGE_DATA_NTF_CONFIG_ENABLE));
        }
        return mIsRangeDataNtfConfigEnableDisableSupported;
    }

    // Detect UIDs going foreground/background
    private void registerUidImportanceTransitions() {
        Handler handler = new Handler(mLooper);
        mActivityManager.addOnUidImportanceListener(new ActivityManager.OnUidImportanceListener() {
            @Override
            public void onUidImportance(final int uid, final int importance) {
                handler.post(() -> {
                    List<UwbSession> uwbSessions = mNonPrivilegedUidToFiraSessionsTable.get(uid);
                    // Not a uid in the watch list
                    if (uwbSessions == null) return;
                    // Feature not supported on device.
                    if (!isRangeDataNtfConfigEnableDisableSupported()) return;
                    boolean newModeHasNonPrivilegedFgApp =
                            UwbInjector.isForegroundAppOrServiceImportance(importance);
                    for (UwbSession uwbSession : uwbSessions) {
                        // already at correct state.
                        if (newModeHasNonPrivilegedFgApp == uwbSession.hasNonPrivilegedFgApp()) {
                            continue;
                        }
                        uwbSession.setHasNonPrivilegedFgApp(newModeHasNonPrivilegedFgApp);
                        // Reconfigure the session based on the new fg/bg state.
                        Log.i(TAG, "App state change. IsFg: " + newModeHasNonPrivilegedFgApp
                                + ". Reconfiguring session ntf control");
                        uwbSession.reconfigureFiraSessionOnFgStateChange();
                    }
                });
            }
        }, IMPORTANCE_FOREGROUND_SERVICE);
    }

    private static boolean hasAllRangingResultError(@NonNull UwbRangingData rangingData) {
        for (UwbTwoWayMeasurement measure : rangingData.getRangingTwoWayMeasures()) {
            if (measure.getRangingStatus() == UwbUciConstants.STATUS_CODE_OK) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRangeDataNotificationReceived(UwbRangingData rangingData) {
        long sessionId = rangingData.getSessionId();
        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession != null) {
            mUwbMetrics.logRangingResult(uwbSession.getProfileType(), rangingData);
            mSessionNotificationManager.onRangingResult(uwbSession, rangingData);
            if (hasAllRangingResultError(rangingData)) {
                uwbSession.startRangingResultErrorStreakTimerIfNotSet();
            } else {
                uwbSession.stopRangingResultErrorStreakTimerIfSet();
            }
        } else {
            Log.i(TAG, "Session is not initialized or Ranging Data is Null");
        }
    }

    @Override
    public void onMulticastListUpdateNotificationReceived(
            UwbMulticastListUpdateStatus multicastListUpdateStatus) {
        Log.d(TAG, "onMulticastListUpdateNotificationReceived");
        UwbSession uwbSession = getUwbSession((int) multicastListUpdateStatus.getSessionId());
        if (uwbSession == null) {
            Log.d(TAG, "onMulticastListUpdateNotificationReceived - invalid session");
            return;
        }
        uwbSession.setMulticastListUpdateStatus(multicastListUpdateStatus);
        synchronized (uwbSession.getWaitObj()) {
            uwbSession.getWaitObj().blockingNotify();
        }
    }

    @Override
    public void onSessionStatusNotificationReceived(long sessionId, int state, int reasonCode) {
        Log.i(TAG, "onSessionStatusNotificationReceived - Session ID : " + sessionId + ", state : "
                + UwbSessionNotificationHelper.getSessionStateString(state) + " reasonCode:"
                + reasonCode);
        UwbSession uwbSession = mSessionTable.get((int) sessionId);

        if (uwbSession == null) {
            Log.d(TAG, "onSessionStatusNotificationReceived - invalid session");
            return;
        }
        int prevState = uwbSession.getSessionState();
        synchronized (uwbSession.getWaitObj()) {
            uwbSession.getWaitObj().blockingNotify();
            setCurrentSessionState((int) sessionId, state);
        }

        //TODO : process only error handling in this switch function, b/218921154
        switch (state) {
            case UwbUciConstants.UWB_SESSION_STATE_IDLE:
                if (prevState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
                    // If session was stopped explicitly, then the onStopped() is sent from
                    // stopRanging method.
                    if (reasonCode != REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS) {
                        mSessionNotificationManager.onRangingStoppedWithUciReasonCode(
                                uwbSession, reasonCode);
                        mUwbMetrics.longRangingStopEvent(uwbSession);
                    }
                } else if (prevState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                    //mSessionNotificationManager.onRangingReconfigureFailed(
                    //      uwbSession, reasonCode);
                }
                break;
            case UwbUciConstants.UWB_SESSION_STATE_DEINIT:
                mEventTask.execute(SESSION_ON_DEINIT, uwbSession);
                break;
            default:
                break;
        }
    }

    private byte getSessionType(String protocolName) {
        byte sessionType = UwbUciConstants.SESSION_TYPE_RANGING;
        if (protocolName.equals(FiraParams.PROTOCOL_NAME)) {
            sessionType = UwbUciConstants.SESSION_TYPE_RANGING;
        } else if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
            sessionType = UwbUciConstants.SESSION_TYPE_CCC;
        }
        return sessionType;
    }

    private int setAppConfigurations(UwbSession uwbSession) {
        return mConfigurationManager.setAppConfigurations(uwbSession.getSessionId(),
                uwbSession.getParams(), uwbSession.getChipId());
    }

    public synchronized void initSession(AttributionSource attributionSource,
            SessionHandle sessionHandle, int sessionId, String protocolName, Params params,
            IUwbRangingCallbacks rangingCallbacks, String chipId)
            throws RemoteException {
        Log.i(TAG, "initSession() : Enter - sessionId : " + sessionId);
        UwbSession uwbSession =  createUwbSession(attributionSource, sessionHandle, sessionId,
                protocolName, params, rangingCallbacks, chipId);
        // Check the attribution source chain to ensure that there are no 3p apps which are not in
        // fg which can receive the ranging results.
        AttributionSource nonPrivilegedAppAttrSource =
                uwbSession.getAnyNonPrivilegedAppInAttributionSource();
        if (nonPrivilegedAppAttrSource != null) {
            Log.d(TAG, "Found a non fg 3p app/service in the attribution source of request: "
                    + nonPrivilegedAppAttrSource);
            // TODO(b/211445008): Move this operation to uwb thread.
            long identity = Binder.clearCallingIdentity();
            boolean hasNonPrivilegedFgApp = mUwbInjector.isForegroundAppOrService(
                    nonPrivilegedAppAttrSource.getUid(),
                    nonPrivilegedAppAttrSource.getPackageName());
            Binder.restoreCallingIdentity(identity);
            uwbSession.setHasNonPrivilegedFgApp(hasNonPrivilegedFgApp);
            if (!hasNonPrivilegedFgApp) {
                Log.e(TAG, "openRanging - System policy disallows for non fg 3p apps");
                rangingCallbacks.onRangingOpenFailed(sessionHandle,
                        RangingChangeReason.SYSTEM_POLICY, new PersistableBundle());
                return;
            }
        }
        if (isExistedSession(sessionId)) {
            Log.i(TAG, "Duplicated sessionId");
            rangingCallbacks.onRangingOpenFailed(sessionHandle, RangingChangeReason.BAD_PARAMETERS,
                    UwbSessionNotificationHelper.convertUciStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE);
            return;
        }

        if (getSessionCount() >= mMaxSessionNumber) {
            Log.i(TAG, "Max Sessions Exceeded");
            rangingCallbacks.onRangingOpenFailed(sessionHandle,
                    RangingChangeReason.MAX_SESSIONS_REACHED,
                    UwbSessionNotificationHelper.convertUciStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED);
            return;
        }

        byte sessionType = getSessionType(protocolName);

        try {
            uwbSession.getBinder().linkToDeath(uwbSession, 0);
        } catch (RemoteException e) {
            uwbSession.binderDied();
            Log.e(TAG, "linkToDeath fail - sessionID : " + uwbSession.getSessionId());
            rangingCallbacks.onRangingOpenFailed(sessionHandle, RangingChangeReason.UNKNOWN,
                    UwbSessionNotificationHelper.convertUciStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_FAILED));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_FAILED);
            removeSession(uwbSession);
            return;
        }

        mSessionTable.put(sessionId, uwbSession);
        addToNonPrivilegedUidToFiraSessionTableIfNecessary(uwbSession);
        mEventTask.execute(SESSION_OPEN_RANGING, uwbSession);
        return;
    }

    // TODO: use UwbInjector.
    @VisibleForTesting
    UwbSession createUwbSession(AttributionSource attributionSource, SessionHandle sessionHandle,
            int sessionId, String protocolName, Params params,
            IUwbRangingCallbacks iUwbRangingCallbacks, String chipId) {
        return new UwbSession(attributionSource, sessionHandle, sessionId, protocolName, params,
                iUwbRangingCallbacks, chipId);
    }

    public synchronized void deInitSession(SessionHandle sessionHandle) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "sessionDeInit() - Session ID : " + sessionId);
        UwbSession uwbSession = getUwbSession(sessionId);
        mEventTask.execute(SESSION_CLOSE, uwbSession);
        return;
    }

    public synchronized void startRanging(SessionHandle sessionHandle, @Nullable Params params) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "startRanging() - Session ID : " + sessionId);

        UwbSession uwbSession = getUwbSession(sessionId);

        int currentSessionState = getCurrentSessionState(sessionId);
        if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
            if (uwbSession.getProtocolName().equals(CccParams.PROTOCOL_NAME)
                    && params instanceof CccStartRangingParams) {
                CccStartRangingParams rangingStartParams = (CccStartRangingParams) params;
                Log.i(TAG, "startRanging() - update RAN multiplier: "
                        + rangingStartParams.getRanMultiplier());
                // Need to update the RAN multiplier from the CccStartRangingParams for CCC session.
                uwbSession.updateCccParamsOnStart(rangingStartParams);
            }
            mEventTask.execute(SESSION_START_RANGING, uwbSession);
        } else if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
            Log.i(TAG, "session is already ranging");
            mSessionNotificationManager.onRangingStartFailed(
                    uwbSession, UwbUciConstants.STATUS_CODE_REJECTED);
        } else {
            Log.i(TAG, "session can't start ranging");
            mSessionNotificationManager.onRangingStartFailed(
                    uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            mUwbMetrics.longRangingStartEvent(uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
        }
    }

    public synchronized void stopRanging(SessionHandle sessionHandle) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "stopRanging() - Session ID : " + sessionId);

        UwbSession uwbSession = getUwbSession(sessionId);
        int currentSessionState = getCurrentSessionState(sessionId);
        if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
            mEventTask.execute(SESSION_STOP_RANGING, uwbSession);
        } else if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
            Log.i(TAG, "session is already idle state");
            mSessionNotificationManager.onRangingStopped(uwbSession,
                    UwbUciConstants.STATUS_CODE_OK);
            mUwbMetrics.longRangingStopEvent(uwbSession);
        } else {
            mSessionNotificationManager.onRangingStopFailed(uwbSession,
                    UwbUciConstants.STATUS_CODE_REJECTED);
            Log.i(TAG, "Not active session ID");
        }
    }

    public UwbSession getUwbSession(int sessionId) {
        return mSessionTable.get(sessionId);
    }

    public Integer getSessionId(SessionHandle sessionHandle) {
        for (Map.Entry<Integer, UwbSession> sessionEntry : mSessionTable.entrySet()) {
            UwbSession uwbSession = sessionEntry.getValue();
            if ((uwbSession.getSessionHandle()).equals(sessionHandle)) {
                return sessionEntry.getKey();
            }
        }
        return null;
    }

    private int getActiveSessionCount() {
        int count = 0;
        for (Map.Entry<Integer, UwbSession> sessionEntry : mSessionTable.entrySet()) {
            UwbSession uwbSession = sessionEntry.getValue();
            if ((uwbSession.getSessionState() == UwbUciConstants.DEVICE_STATE_ACTIVE)) {
                count++;
            }
        }
        return count;
    }

    public boolean isExistedSession(SessionHandle sessionHandle) {
        return (getSessionId(sessionHandle) != null);
    }

    public boolean isExistedSession(int sessionId) {
        return mSessionTable.containsKey(sessionId);
    }

    public void stopAllRanging() {
        Log.d(TAG, "stopAllRanging()");
        for (Map.Entry<Integer, UwbSession> sessionEntry : mSessionTable.entrySet()) {
            int status = mNativeUwbManager.stopRanging(sessionEntry.getKey(),
                    sessionEntry.getValue().getChipId());

            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "stopAllRanging() - Session " + sessionEntry.getKey()
                        + " is failed to stop ranging");
            } else {
                UwbSession uwbSession = sessionEntry.getValue();
                mUwbMetrics.longRangingStopEvent(uwbSession);
                uwbSession.setSessionState(UwbUciConstants.UWB_SESSION_STATE_IDLE);
            }
        }
    }

    public synchronized void deinitAllSession() {
        Log.d(TAG, "deinitAllSession()");
        for (Map.Entry<Integer, UwbSession> sessionEntry : mSessionTable.entrySet()) {
            UwbSession uwbSession = sessionEntry.getValue();
            onDeInit(uwbSession);
        }

        // Not resetting chip on UWB toggle off.
        // mNativeUwbManager.resetDevice(UwbUciConstants.UWBS_RESET);
    }

    public synchronized void onDeInit(UwbSession uwbSession) {
        if (!isExistedSession(uwbSession.getSessionId())) {
            Log.i(TAG, "onDeinit - Ignoring already deleted session "
                    + uwbSession.getSessionId());
            return;
        }
        Log.d(TAG, "onDeinit: " + uwbSession.getSessionId());
        mSessionNotificationManager.onRangingClosedWithApiReasonCode(uwbSession,
                RangingChangeReason.SYSTEM_POLICY);
        mUwbMetrics.logRangingCloseEvent(uwbSession, UwbUciConstants.STATUS_CODE_OK);
        removeSession(uwbSession);
    }

    public void setCurrentSessionState(int sessionId, int state) {
        UwbSession uwbSession = mSessionTable.get(sessionId);
        if (uwbSession != null) {
            uwbSession.setSessionState(state);
        }
    }

    public int getCurrentSessionState(int sessionId) {
        UwbSession uwbSession = mSessionTable.get(sessionId);
        if (uwbSession != null) {
            return uwbSession.getSessionState();
        }
        return UwbUciConstants.UWB_SESSION_STATE_ERROR;
    }

    public int getSessionCount() {
        return mSessionTable.size();
    }

    public Set<Integer> getSessionIdSet() {
        return mSessionTable.keySet();
    }

    public int reconfigure(SessionHandle sessionHandle, @Nullable Params params) {
        int status = UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return status;
        }
        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "reconfigure() - Session ID : " + sessionId);
        UwbSession uwbSession = getUwbSession(sessionId);
        if (uwbSession.getProtocolName().equals(FiraParams.PROTOCOL_NAME)
                && params instanceof FiraRangingReconfigureParams) {
            FiraRangingReconfigureParams rangingReconfigureParams =
                    (FiraRangingReconfigureParams) params;
            Log.i(TAG, "reconfigure() - update reconfigure params: "
                    + rangingReconfigureParams);
            uwbSession.updateFiraParamsOnReconfigure(rangingReconfigureParams);
        }
        Pair<SessionHandle, Params> info = new Pair<>(sessionHandle, params);
        mEventTask.execute(SESSION_RECONFIG_RANGING, info);
        return 0;
    }

    void removeSession(UwbSession uwbSession) {
        if (uwbSession != null) {
            uwbSession.getBinder().unlinkToDeath(uwbSession, 0);
            removeFromNonPrivilegedUidToFiraSessionTableIfNecessary(uwbSession);
            mSessionTable.remove(uwbSession.getSessionId());
        }
    }

    void addToNonPrivilegedUidToFiraSessionTableIfNecessary(@NonNull UwbSession uwbSession) {
        if (getSessionType(uwbSession.getProtocolName()) == UwbUciConstants.SESSION_TYPE_RANGING) {
            AttributionSource nonPrivilegedAppAttrSource =
                    uwbSession.getAnyNonPrivilegedAppInAttributionSource();
            if (nonPrivilegedAppAttrSource != null) {
                Log.d(TAG, "Detected start of non privileged FIRA session from "
                        + nonPrivilegedAppAttrSource);
                List<UwbSession> sessions = mNonPrivilegedUidToFiraSessionsTable.computeIfAbsent(
                        nonPrivilegedAppAttrSource.getUid(), v -> new ArrayList<>());
                sessions.add(uwbSession);
            }
        }
    }

    void removeFromNonPrivilegedUidToFiraSessionTableIfNecessary(@NonNull UwbSession uwbSession) {
        if (getSessionType(uwbSession.getProtocolName()) == UwbUciConstants.SESSION_TYPE_RANGING) {
            AttributionSource nonPrivilegedAppAttrSource =
                    uwbSession.getAnyNonPrivilegedAppInAttributionSource();
            if (nonPrivilegedAppAttrSource != null) {
                Log.d(TAG, "Detected end of non privileged FIRA session from "
                        + nonPrivilegedAppAttrSource);
                List<UwbSession> sessions = mNonPrivilegedUidToFiraSessionsTable.get(
                        nonPrivilegedAppAttrSource.getUid());
                if (sessions == null) {
                    Log.wtf(TAG, "No sessions found for uid: "
                            + nonPrivilegedAppAttrSource.getUid());
                    return;
                }
                sessions.remove(uwbSession);
                if (sessions.isEmpty()) {
                    mNonPrivilegedUidToFiraSessionsTable.remove(
                            nonPrivilegedAppAttrSource.getUid());
                }
            }
        }
    }
    private class EventTask extends Handler {

        EventTask(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int type = msg.what;
            switch (type) {
                case SESSION_OPEN_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    openRanging(uwbSession);
                    break;
                }

                case SESSION_START_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    startRanging(uwbSession);
                    break;
                }

                case SESSION_STOP_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    stopRanging(uwbSession);
                    break;
                }

                case SESSION_RECONFIG_RANGING: {
                    Log.d(TAG, "SESSION_RECONFIG_RANGING");
                    Pair<SessionHandle, Params> info = (Pair<SessionHandle, Params>) msg.obj;
                    reconfigure(info.first, info.second);
                    break;
                }

                case SESSION_CLOSE: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    close(uwbSession);
                    break;
                }

                case SESSION_ON_DEINIT : {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    onDeInit(uwbSession);
                    break;
                }

                default: {
                    Log.d(TAG, "EventTask : Undefined Task");
                    break;
                }
            }
        }

        public void execute(int task, Object obj) {
            Message msg = mEventTask.obtainMessage();
            msg.what = task;
            msg.obj = obj;
            this.sendMessage(msg);
        }

        private void openRanging(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<Integer> initSessionTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            status = mNativeUwbManager.initSession(
                                    uwbSession.getSessionId(),
                                    getSessionType(uwbSession.getParams().getProtocolName()),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                return status;
                            }

                            uwbSession.getWaitObj().blockingWait();
                            status = UwbUciConstants.STATUS_CODE_FAILED;
                            if (uwbSession.getSessionState()
                                    == UwbUciConstants.UWB_SESSION_STATE_INIT) {
                                status = UwbSessionManager.this.setAppConfigurations(uwbSession);
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    return status;
                                }

                                uwbSession.getWaitObj().blockingWait();
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                if (uwbSession.getSessionState()
                                        == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                                    mSessionNotificationManager.onRangingOpened(uwbSession);
                                    status = UwbUciConstants.STATUS_CODE_OK;
                                } else {
                                    status = UwbUciConstants.STATUS_CODE_FAILED;
                                }
                                return status;
                            }
                            return status;
                        }
                    });
            executor.submit(initSessionTask);

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = initSessionTask.get(
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                executor.shutdownNow();
                Log.i(TAG, "Failed to initialize session - status : TIMEOUT");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            mUwbMetrics.logRangingInitEvent(uwbSession, status);
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "Failed to initialize session - status : " + status);
                mSessionNotificationManager.onRangingOpenFailed(uwbSession, status);
                mNativeUwbManager.deInitSession(uwbSession.getSessionId(), uwbSession.getChipId());
                removeSession(uwbSession);
            }
            Log.i(TAG, "sessionInit() : finish - sessionId : " + uwbSession.getSessionId());
        }

        private void startRanging(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<Integer> startRangingTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            if (uwbSession.getParams().getProtocolName()
                                    .equals(CccParams.PROTOCOL_NAME)) {
                                status = mConfigurationManager.setAppConfigurations(
                                        uwbSession.getSessionId(),
                                        uwbSession.getParams(), uwbSession.getChipId());
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    mSessionNotificationManager.onRangingStartFailed(
                                            uwbSession, status);
                                    return status;
                                }
                            }

                            status = mNativeUwbManager.startRanging(uwbSession.getSessionId(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                mSessionNotificationManager.onRangingStartFailed(
                                        uwbSession, status);
                                return status;
                            }
                            uwbSession.getWaitObj().blockingWait();
                            if (uwbSession.getSessionState()
                                    == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
                                // TODO: Ensure |rangingStartedParams| is valid for FIRA sessions
                                // as well.
                                Params rangingStartedParams = uwbSession.getParams();
                                // For CCC sessions, retrieve the app configs
                                if (uwbSession.getProtocolName().equals(CccParams.PROTOCOL_NAME)) {
                                    Pair<Integer, CccRangingStartedParams> statusAndParams  =
                                            mConfigurationManager.getAppConfigurations(
                                                    uwbSession.getSessionId(),
                                                    CccParams.PROTOCOL_NAME,
                                                    new byte[0],
                                                    CccRangingStartedParams.class,
                                                    uwbSession.getChipId());
                                    if (statusAndParams.first != UwbUciConstants.STATUS_CODE_OK) {
                                        Log.e(TAG, "Failed to get CCC ranging started params");
                                    }
                                    rangingStartedParams = statusAndParams.second;
                                }
                                mSessionNotificationManager.onRangingStarted(
                                        uwbSession, rangingStartedParams);
                            } else {
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                mSessionNotificationManager.onRangingStartFailed(uwbSession,
                                        status);
                            }
                        }
                        return status;
                    });

            executor.submit(startRangingTask);

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = startRangingTask.get(
                        IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Start Ranging - status : TIMEOUT");
                executor.shutdownNow();
                mSessionNotificationManager.onRangingStartFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            mUwbMetrics.longRangingStartEvent(uwbSession, status);
        }

        private void stopRanging(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<Integer> stopRangingTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            status = mNativeUwbManager.stopRanging(uwbSession.getSessionId(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                mSessionNotificationManager.onRangingStopFailed(uwbSession, status);
                                return status;
                            }
                            uwbSession.getWaitObj().blockingWait();
                            if (uwbSession.getSessionState()
                                    == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                                mSessionNotificationManager.onRangingStopped(uwbSession, status);
                            } else {
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                mSessionNotificationManager.onRangingStopFailed(uwbSession,
                                        status);
                            }
                        }
                        return status;
                    });

            executor.submit(stopRangingTask);

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = stopRangingTask.get(
                        IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Stop Ranging - status : TIMEOUT");
                executor.shutdownNow();
                mSessionNotificationManager.onRangingStopFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if (status != UwbUciConstants.STATUS_CODE_FAILED) {
                mUwbMetrics.longRangingStopEvent(uwbSession);
            }
            // Reset any stored error streak timestamp when session is stopped.
            uwbSession.stopRangingResultErrorStreakTimerIfSet();
        }

        private void reconfigure(SessionHandle sessionHandle, @Nullable Params param) {
            UwbSession uwbSession = getUwbSession(getSessionId(sessionHandle));
            if (!(param instanceof FiraRangingReconfigureParams)) {
                Log.e(TAG, "Invalid reconfigure params: " + param);
                mSessionNotificationManager.onRangingReconfigureFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_INVALID_PARAM);
                return;
            }
            FiraRangingReconfigureParams rangingReconfigureParams =
                    (FiraRangingReconfigureParams) param;
            // TODO(b/211445008): Consolidate to a single uwb thread.
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<Integer> cmdTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            // Handle SESSION_UPDATE_CONTROLLER_MULTICAST_LIST_CMD
                            if (rangingReconfigureParams.getAction() != null) {
                                Log.d(TAG, "call multicastlist update");
                                int dstAddressListSize =
                                        rangingReconfigureParams.getAddressList().length;
                                List<Short> dstAddressList = new ArrayList<>();
                                for (UwbAddress address :
                                        rangingReconfigureParams.getAddressList()) {
                                    dstAddressList.add(
                                            ByteBuffer.wrap(address.toBytes()).getShort(0));
                                }
                                int[] subSessionIdList = null;
                                if (!ArrayUtils.isEmpty(
                                        rangingReconfigureParams.getSubSessionIdList())) {
                                    subSessionIdList =
                                        rangingReconfigureParams.getSubSessionIdList();
                                } else {
                                    // Set to 0's for the UCI stack.
                                    subSessionIdList = new int[dstAddressListSize];
                                }

                                status = mNativeUwbManager.controllerMulticastListUpdate(
                                        uwbSession.getSessionId(),
                                        rangingReconfigureParams.getAction(),
                                        subSessionIdList.length,
                                        ArrayUtils.toPrimitive(dstAddressList),
                                        subSessionIdList,
                                        uwbSession.getChipId());
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    if (rangingReconfigureParams.getAction()
                                            == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                        mSessionNotificationManager.onControleeAddFailed(
                                                uwbSession, status);
                                    } else if (rangingReconfigureParams.getAction()
                                            == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                        mSessionNotificationManager.onControleeRemoveFailed(
                                                uwbSession, status);
                                    }
                                    return status;
                                }

                                uwbSession.getWaitObj().blockingWait();

                                UwbMulticastListUpdateStatus multicastList =
                                        uwbSession.getMulticastListUpdateStatus();
                                if (multicastList != null) {
                                    if (rangingReconfigureParams.getAction()
                                            == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                        for (int i = 0; i < multicastList.getNumOfControlee();
                                                i++) {
                                            if (multicastList.getStatus()[i]
                                                    != UwbUciConstants.STATUS_CODE_OK) {
                                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    if (rangingReconfigureParams.getAction()
                                            == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                        mSessionNotificationManager.onControleeAddFailed(
                                                uwbSession, status);
                                    } else if (rangingReconfigureParams.getAction()
                                            == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                        mSessionNotificationManager.onControleeRemoveFailed(
                                                uwbSession, status);
                                    }
                                    return status;
                                }
                                if (rangingReconfigureParams.getAction()
                                        == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                    mSessionNotificationManager.onControleeAdded(uwbSession);
                                } else if (rangingReconfigureParams.getAction()
                                        == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                    mSessionNotificationManager.onControleeRemoved(uwbSession);
                                }
                            }
                            status = mConfigurationManager.setAppConfigurations(
                                    uwbSession.getSessionId(), param, uwbSession.getChipId());
                            Log.d(TAG, "status: " + status);
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                return status;
                            }
                            mSessionNotificationManager.onRangingReconfigured(uwbSession);
                            return status;
                        }
                    });

            executor.submit(cmdTask);

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = cmdTask.get(
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Reconfigure - status : TIMEOUT");
                executor.shutdownNow();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "Failed to Reconfigure : " + status);
                mSessionNotificationManager.onRangingReconfigureFailed(uwbSession, status);
            }
        }

        private void close(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
            ExecutorService executor = Executors.newSingleThreadExecutor();
            FutureTask<Integer> closeTask = new FutureTask<>(
                    (Callable<Integer>) () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            status = mNativeUwbManager.deInitSession(uwbSession.getSessionId(),
                                    uwbSession.getChipId());
                            if (status != UwbUciConstants.STATUS_CODE_OK) {
                                mSessionNotificationManager.onRangingClosed(uwbSession, status);
                                return status;
                            }
                            uwbSession.getWaitObj().blockingWait();
                            Log.i(TAG, "onRangingClosed - status : " + status);
                            mSessionNotificationManager.onRangingClosed(uwbSession, status);
                        }
                        return status;
                    });
            executor.submit(closeTask);

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = closeTask.get(
                        IUwbAdapter.RANGING_SESSION_CLOSE_THRESHOLD_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Stop Ranging - status : TIMEOUT");
                executor.shutdownNow();
                mSessionNotificationManager.onRangingClosed(uwbSession, status);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            mUwbMetrics.logRangingCloseEvent(uwbSession, status);
            removeSession(uwbSession);
            Log.i(TAG, "deinit finish : status :" + status);
        }
    }

    public class UwbSession implements IBinder.DeathRecipient {
        // Amount of time we allow continuous failures before stopping the session.
        @VisibleForTesting
        public static final long RANGING_RESULT_ERROR_STREAK_TIMER_TIMEOUT_MS = 30_000L;
        private static final String RANGING_RESULT_ERROR_STREAK_TIMER_TAG =
                "UwbSessionRangingResultError";

        private final AttributionSource mAttributionSource;
        private final SessionHandle mSessionHandle;
        private final int mSessionId;
        private final IUwbRangingCallbacks mIUwbRangingCallbacks;
        private final String mProtocolName;
        private final IBinder mIBinder;
        private final WaitObj mWaitObj;
        private Params mParams;
        private int mSessionState;
        private UwbMulticastListUpdateStatus mMulticastListUpdateStatus;
        private final int mProfileType;
        private AlarmManager.OnAlarmListener mRangingResultErrorStreakTimerListener;
        private final String mChipId;
        private boolean mHasNonPrivilegedFgApp = false;
        private @FiraParams.RangeDataNtfConfig Integer mOrigRangeDataNtfConfig;

        UwbSession(AttributionSource attributionSource, SessionHandle sessionHandle, int sessionId,
                String protocolName, Params params, IUwbRangingCallbacks iUwbRangingCallbacks,
                String chipId) {
            this.mAttributionSource = attributionSource;
            this.mSessionHandle = sessionHandle;
            this.mSessionId = sessionId;
            this.mProtocolName = protocolName;
            this.mIUwbRangingCallbacks = iUwbRangingCallbacks;
            this.mIBinder = iUwbRangingCallbacks.asBinder();
            this.mSessionState = UwbUciConstants.UWB_SESSION_STATE_DEINIT;
            this.mParams = params;
            this.mWaitObj = new WaitObj();
            this.mProfileType = convertProtolNameToProfileType(protocolName);
            this.mChipId = chipId;
        }

        private boolean isPrivilegedApp(int uid, String packageName) {
            return mUwbInjector.isSystemApp(uid, packageName)
                    || mUwbInjector.isAppSignedWithPlatformKey(uid);
        }

        /**
         * Check the attribution source chain to check if there are any 3p apps.
         * @return true if there is some non-system app, false otherwise.
         */
        @Nullable
        public AttributionSource getAnyNonPrivilegedAppInAttributionSource() {
            // Iterate attribution source chain to ensure that there is no non-fg 3p app in the
            // request.
            AttributionSource attributionSource = mAttributionSource;
            while (attributionSource != null) {
                int uid = attributionSource.getUid();
                String packageName = attributionSource.getPackageName();
                if (!isPrivilegedApp(uid, packageName)) {
                    return attributionSource;
                }
                attributionSource = attributionSource.getNext();
            }
            return null;
        }

        public AttributionSource getAttributionSource() {
            return this.mAttributionSource;
        }

        public int getSessionId() {
            return this.mSessionId;
        }

        public String getChipId() {
            return this.mChipId;
        }

        public SessionHandle getSessionHandle() {
            return this.mSessionHandle;
        }

        public Params getParams() {
            return this.mParams;
        }

        public void updateCccParamsOnStart(CccStartRangingParams rangingStartParams) {
            // Need to update the RAN multiplier from the CccStartRangingParams for CCC session.
            CccOpenRangingParams newParams =
                    new CccOpenRangingParams.Builder((CccOpenRangingParams) mParams)
                            .setRanMultiplier(rangingStartParams.getRanMultiplier())
                            .build();
            this.mParams = newParams;
        }

        public void updateFiraParamsOnReconfigure(FiraRangingReconfigureParams reconfigureParams) {
            // Need to update the reconfigure params from the FiraRangingReconfigureParams for
            // FiRa session.
            FiraOpenSessionParams.Builder newParamsBuilder =
                    new FiraOpenSessionParams.Builder((FiraOpenSessionParams) mParams);
            if (reconfigureParams.getBlockStrideLength() != null) {
                newParamsBuilder.setBlockStrideLength(reconfigureParams.getBlockStrideLength());
            }
            if (reconfigureParams.getRangeDataNtfConfig() != null) {
                newParamsBuilder.setRangeDataNtfConfig(reconfigureParams.getRangeDataNtfConfig());
            }
            if (reconfigureParams.getRangeDataProximityNear() != null) {
                newParamsBuilder.setRangeDataNtfProximityNear(
                        reconfigureParams.getRangeDataProximityNear());
            }
            if (reconfigureParams.getRangeDataProximityFar() != null) {
                newParamsBuilder.setRangeDataNtfProximityFar(
                        reconfigureParams.getRangeDataProximityFar());
            }
            this.mParams = newParamsBuilder.build();
        }

        public String getProtocolName() {
            return this.mProtocolName;
        }

        public IUwbRangingCallbacks getIUwbRangingCallbacks() {
            return this.mIUwbRangingCallbacks;
        }

        public int getSessionState() {
            return this.mSessionState;
        }

        public void setSessionState(int state) {
            this.mSessionState = state;
        }

        public void setMulticastListUpdateStatus(
                UwbMulticastListUpdateStatus multicastListUpdateStatus) {
            mMulticastListUpdateStatus = multicastListUpdateStatus;
        }

        public UwbMulticastListUpdateStatus getMulticastListUpdateStatus() {
            return mMulticastListUpdateStatus;
        }

        private int convertProtolNameToProfileType(String protocolName) {
            if (protocolName.equals(FiraParams.PROTOCOL_NAME)) {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__FIRA;
            } else if (protocolName.equals(CccParams.PROTOCOL_NAME)) {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CCC;
            } else {
                return UwbStatsLog.UWB_SESSION_INITIATED__PROFILE__CUSTOMIZED;
            }
        }

        public int getProfileType() {
            return mProfileType;
        }

        public IBinder getBinder() {
            return mIBinder;
        }

        public WaitObj getWaitObj() {
            return mWaitObj;
        }

        public boolean hasNonPrivilegedFgApp() {
            return mHasNonPrivilegedFgApp;
        }

        public void setHasNonPrivilegedFgApp(boolean hasNonPrivilegedFgApp) {
            mHasNonPrivilegedFgApp = hasNonPrivilegedFgApp;
        }

        /**
         * Starts a timer to detect if the error streak is longer than
         * {@link #RANGING_RESULT_ERROR_STREAK_TIMER_TIMEOUT_MS}.
         */
        public void startRangingResultErrorStreakTimerIfNotSet() {
            // Start a timer on first failure to detect continuous failures.
            if (mRangingResultErrorStreakTimerListener == null) {
                mRangingResultErrorStreakTimerListener = () -> {
                    Log.w(TAG, "Continuous errors or no ranging results detected for 30 seconds."
                            + " Stopping session");
                    stopRanging(mSessionHandle);
                };
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mUwbInjector.getElapsedSinceBootMillis()
                                + RANGING_RESULT_ERROR_STREAK_TIMER_TIMEOUT_MS,
                        RANGING_RESULT_ERROR_STREAK_TIMER_TAG,
                        mRangingResultErrorStreakTimerListener, mEventTask);
            }
        }

        public void stopRangingResultErrorStreakTimerIfSet() {
            // Cancel error streak timer on any success.
            if (mRangingResultErrorStreakTimerListener != null) {
                mAlarmManager.cancel(mRangingResultErrorStreakTimerListener);
                mRangingResultErrorStreakTimerListener = null;
            }
        }

        public void reconfigureFiraSessionOnFgStateChange() {
            if (mOrigRangeDataNtfConfig == null) {
                mOrigRangeDataNtfConfig = ((FiraOpenSessionParams) mParams).getRangeDataNtfConfig();
            }
            // Reconfigure the session to change notification control when the app transitions
            // from fg to bg and vice versa.
            FiraRangingReconfigureParams reconfigureParams =
                    new FiraRangingReconfigureParams.Builder()
                    // If app is in fg, use the configured ntf control, else disable.
                    .setRangeDataNtfConfig(mHasNonPrivilegedFgApp
                            // use to retrieve the latest configured ntf control.
                            ? mOrigRangeDataNtfConfig : FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE)
                    .build();
            reconfigure(mSessionHandle, reconfigureParams);
        }

        @Override
        public void binderDied() {
            Log.i(TAG, "binderDied : getSessionId is getSessionId() " + getSessionId());

            synchronized (UwbSessionManager.this) {
                int status = mNativeUwbManager.deInitSession(getSessionId(), getChipId());
                mUwbMetrics.logRangingCloseEvent(this, status);
                if (status == UwbUciConstants.STATUS_CODE_OK) {
                    removeSession(this);
                    Log.i(TAG, "binderDied : Session count currently is " + getSessionCount());
                } else {
                    Log.e(TAG,
                            "binderDied : sessionDeinit Failure because of NativeSessionDeinit "
                                    + "Error");
                }
            }
        }
    }

    // TODO: refactor the async operation flow.
    // Wrapper for unit test.
    @VisibleForTesting
    static class WaitObj {
        WaitObj() {
        }

        void blockingWait() throws InterruptedException {
            wait();
        }

        void blockingNotify() {
            notify();
        }
    }
}
