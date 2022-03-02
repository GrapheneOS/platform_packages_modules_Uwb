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

import static com.android.server.uwb.data.UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;

import android.annotation.Nullable;
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
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import java.nio.ByteBuffer;
import java.util.ArrayList;
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
    private static final int SESSION_OPEN_RANGING = 1;
    private static final int SESSION_START_RANGING = 2;
    private static final int SESSION_STOP_RANGING = 3;
    private static final int SESSION_RECONFIG_RANGING = 4;
    private static final int SESSION_CLOSE = 5;

    // TODO: don't expose the internal field for testing.
    @VisibleForTesting
    final ConcurrentHashMap<Integer, UwbSession> mSessionTable = new ConcurrentHashMap();
    private final NativeUwbManager mNativeUwbManager;
    private final UwbMetrics mUwbMetrics;
    private final UwbSessionNotificationManager mSessionNotificationManager;
    private final UwbConfigurationManager mConfigurationManager;
    private final int mMaxSessionNumber;
    private final EventTask mEventTask;

    public UwbSessionManager(UwbConfigurationManager uwbConfigurationManager,
            NativeUwbManager nativeUwbManager, UwbMetrics uwbMetrics,
            UwbSessionNotificationManager uwbSessionNotificationManager,
            Looper serviceLooper) {
        mNativeUwbManager = nativeUwbManager;
        mNativeUwbManager.setSessionListener(this);
        mUwbMetrics = uwbMetrics;
        mConfigurationManager = uwbConfigurationManager;
        mSessionNotificationManager = uwbSessionNotificationManager;
        mMaxSessionNumber = mNativeUwbManager.getMaxSessionNumber();
        mEventTask = new EventTask(serviceLooper);
    }

    @Override
    public void onRangeDataNotificationReceived(UwbRangingData rangingData) {
        long sessionId = rangingData.getSessionId();
        UwbSession uwbSession = getUwbSession((int) sessionId);
        if (uwbSession != null) {
            mUwbMetrics.logRangingResult(uwbSession.getProfileType(), rangingData);
            mSessionNotificationManager.onRangingResult(uwbSession, rangingData);
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
            Log.d(TAG, "onSessionStatusNotificationReceived - invalid session");
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
                    if (reasonCode
                            == UwbUciConstants.REASON_MAX_RANGING_ROUND_RETRY_COUNT_REACHED) {
                        mSessionNotificationManager.onRangingStopped(uwbSession, reasonCode);
                        mUwbMetrics.longRangingStopEvent(uwbSession);
                    }
                } else if (prevState == UwbUciConstants.UWB_SESSION_STATE_IDLE) {
                    //mSessionNotificationManager.onRangingReconfigureFailed(
                    //      uwbSession, reasonCode);
                }
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
                uwbSession.getParams());
    }

    public synchronized void initSession(SessionHandle sessionHandle, int sessionId,
            String protocolName, Params params, IUwbRangingCallbacks rangingCallbacks)
            throws RemoteException {
        Log.i(TAG, "initSession() : Enter - sessionId : " + sessionId);
        UwbSession uwbSession =  createUwbSession(sessionHandle, sessionId, protocolName, params,
                rangingCallbacks);
        if (isExistedSession(sessionId)) {
            Log.i(TAG, "Duplicated sessionId");
            rangingCallbacks.onRangingOpenFailed(sessionHandle, RangingChangeReason.UNKNOWN,
                    UwbSessionNotificationHelper.convertStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_ERROR_SESSION_DUPLICATE);
            return;
        }

        if (getSessionCount() >= mMaxSessionNumber) {
            Log.i(TAG, "Max Sessions Exceeded");
            rangingCallbacks.onRangingOpenFailed(sessionHandle,
                    RangingChangeReason.MAX_SESSIONS_REACHED,
                    UwbSessionNotificationHelper.convertStatusToParam(protocolName,
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
                    UwbSessionNotificationHelper.convertStatusToParam(protocolName,
                            UwbUciConstants.STATUS_CODE_FAILED));
            mUwbMetrics.logRangingInitEvent(uwbSession,
                    UwbUciConstants.STATUS_CODE_FAILED);
            removeSession(uwbSession);
            return;
        }

        mSessionTable.put(sessionId, uwbSession);
        mEventTask.execute(SESSION_OPEN_RANGING, uwbSession);
        return;
    }

    // TODO: use UwbInjector.
    @VisibleForTesting
    UwbSession createUwbSession(SessionHandle sessionHandle, int sessionId, String protocolName,
            Params params, IUwbRangingCallbacks iUwbRangingCallbacks) {
        return new UwbSession(sessionHandle, sessionId, protocolName, params, iUwbRangingCallbacks);
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
                    REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
            mUwbMetrics.longRangingStopEvent(uwbSession);
        } else {
            int status = UwbUciConstants.STATUS_CODE_REJECTED;
            mSessionNotificationManager.onRangingStopFailed(uwbSession, status);
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
            int status = mNativeUwbManager.stopRanging(sessionEntry.getKey());

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
            mSessionNotificationManager.onRangingClosed(uwbSession,
                    REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);
            mUwbMetrics.logRangingCloseEvent(uwbSession, UwbUciConstants.STATUS_CODE_OK);
            removeSession(uwbSession);
        }

        // Not resetting chip on UWB toggle off.
        // mNativeUwbManager.resetDevice(UwbUciConstants.UWBS_RESET);
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

    public int reconfigure(SessionHandle sessionHandle, PersistableBundle params) {
        Log.i(TAG, "reconfigure() - Session Handle : " + sessionHandle);
        int status = UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return status;
        }

        /*
         FiraRangingReconfigureParams is only implemented
         TODO - CccRangingReconfiguredParams should be implemented
         */
        FiraRangingReconfigureParams param = FiraRangingReconfigureParams.fromBundle(params);

        Pair<SessionHandle, Params> info = new Pair<SessionHandle, Params>(sessionHandle, param);
        mEventTask.execute(SESSION_RECONFIG_RANGING, info);

        return 0;
    }

    void removeSession(UwbSession uwbSession) {
        if (uwbSession != null) {
            uwbSession.getBinder().unlinkToDeath(uwbSession, 0);
            mSessionTable.remove(uwbSession.getSessionId());
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
                                    getSessionType(uwbSession.getParams().getProtocolName()));
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
                mNativeUwbManager.deInitSession(uwbSession.getSessionId());
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
                                        uwbSession.getParams());
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    mSessionNotificationManager.onRangingStartFailed(
                                            uwbSession, status);
                                    return status;
                                }
                            }

                            status = mNativeUwbManager.startRanging(uwbSession.getSessionId());
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
                                                    CccRangingStartedParams.class);
                                    if (statusAndParams.first != UwbUciConstants.STATUS_CODE_OK) {
                                        Log.e(TAG, "Failed to get CCC ranging started params");
                                    }
                                    rangingStartedParams = statusAndParams.second;
                                }
                                mSessionNotificationManager.onRangingStarted(
                                        uwbSession, rangingStartedParams);
                            } else {
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                mSessionNotificationManager.onRangingStartFailed(
                                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
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
                            status = mNativeUwbManager.stopRanging(uwbSession.getSessionId());
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
                                mSessionNotificationManager.onRangingStopFailed(
                                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
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
        }

        private void reconfigure(SessionHandle sessionHandle, Params param) {
            FiraRangingReconfigureParams rangingReconfigureParams =
                    (FiraRangingReconfigureParams) param;
            // TODO(b/211445008): Consolidate to a single uwb thread.
            UwbSession uwbSession = getUwbSession(getSessionId(sessionHandle));
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
                                        subSessionIdList);
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    return status;
                                }

                                uwbSession.getWaitObj().blockingWait();

                                UwbMulticastListUpdateStatus multicastList =
                                        uwbSession.getMulticastListUpdateStatus();
                                if (multicastList != null) {
                                    if (rangingReconfigureParams.getAction()
                                            == FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                        for (int i = 0; i < multicastList.getNumOfControlee();
                                                i++) {
                                            if (multicastList.getStatus()[i]
                                                    != UwbUciConstants.STATUS_CODE_OK) {
                                                //TODO - Need implement for partial fail case.
                                                return UwbUciConstants.STATUS_CODE_FAILED;
                                            }
                                        }
                                    }
                                }

                            }

                            status = mConfigurationManager.setAppConfigurations(
                                    uwbSession.getSessionId(), param);
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
                            status = mNativeUwbManager.deInitSession(uwbSession.getSessionId());
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
        private final SessionHandle mSessionHandle;
        private final int mSessionId;
        private final IUwbRangingCallbacks mIUwbRangingCallbacks;
        private final String mProtocolName;
        private final IBinder mIBinder;
        private final WaitObj mWaitObj;
        public boolean isWait;
        private Params mParams;
        private int mSessionState;
        private UwbMulticastListUpdateStatus mMulticastListUpdateStatus;
        private final int mProfileType;

        UwbSession(SessionHandle sessionHandle, int sessionId, String protocolName,
                Params params, IUwbRangingCallbacks iUwbRangingCallbacks) {
            this.mSessionHandle = sessionHandle;
            this.mSessionId = sessionId;
            this.mProtocolName = protocolName;
            this.mIUwbRangingCallbacks = iUwbRangingCallbacks;
            this.mIBinder = iUwbRangingCallbacks.asBinder();
            this.mSessionState = UwbUciConstants.UWB_SESSION_STATE_DEINIT;
            this.mParams = params;
            this.mWaitObj = new WaitObj();
            this.isWait = false;
            this.mProfileType = convertProtolNameToProfileType(protocolName);
        }

        public int getSessionId() {
            return this.mSessionId;
        }

        public SessionHandle getSessionHandle() {
            return this.mSessionHandle;
        }

        public Params getParams() {
            return this.mParams;
        }

        public void updateCccParamsOnStart(CccStartRangingParams rangingStartParams) {
            // Need to update the RAN multiplier from the CccStartRangingParams for CCC session.
            CccOpenRangingParams rangingOpenedParams = (CccOpenRangingParams) mParams;
            CccOpenRangingParams newParams =
                    new CccOpenRangingParams.Builder()
                            .setProtocolVersion(rangingOpenedParams.getProtocolVersion())
                            .setUwbConfig(rangingOpenedParams.getUwbConfig())
                            .setPulseShapeCombo(rangingOpenedParams.getPulseShapeCombo())
                            .setSessionId(rangingOpenedParams.getSessionId())
                            .setRanMultiplier(rangingStartParams.getRanMultiplier())
                            .setChannel(rangingOpenedParams.getChannel())
                            .setNumChapsPerSlot(rangingOpenedParams.getNumChapsPerSlot())
                            .setNumResponderNodes(rangingOpenedParams.getNumResponderNodes())
                            .setNumSlotsPerRound(rangingOpenedParams.getNumSlotsPerRound())
                            .setSyncCodeIndex(rangingOpenedParams.getSyncCodeIndex())
                            .setHoppingConfigMode(rangingOpenedParams.getHoppingConfigMode())
                            .setHoppingSequence(rangingOpenedParams.getHoppingSequence())
                            .build();
            this.mParams = newParams;
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

        @Override
        public void binderDied() {
            Log.i(TAG, "binderDied : getSessionId is getSessionId() " + getSessionId());

            synchronized (UwbSessionManager.this) {
                int status = mNativeUwbManager.deInitSession(getSessionId());
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
