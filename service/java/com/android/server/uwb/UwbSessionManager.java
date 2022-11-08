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

import static com.android.server.uwb.data.UwbUciConstants.RANGING_DEVICE_ROLE_OBSERVER;
import static com.android.server.uwb.data.UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;
import static com.android.server.uwb.data.UwbUciConstants.ROUND_USAGE_OWR_AOA_MEASUREMENT;
import static com.android.server.uwb.data.UwbUciConstants.UWB_DEVICE_EXT_MAC_ADDRESS_LEN;
import static com.android.server.uwb.data.UwbUciConstants.UWB_SESSION_STATE_ACTIVE;
import static com.android.server.uwb.util.DataTypeConversionUtil.byteArrayToLong;

import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_NAME;
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

import com.android.server.uwb.advertisement.UwbAdvertiseManager;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbOwrAoaMeasurement;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.jni.INativeUwbManager;
import com.android.server.uwb.jni.NativeUwbManager;
import com.android.server.uwb.params.TlvUtil;
import com.android.server.uwb.proto.UwbStatsLog;
import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.UwbUtil;

import com.google.uwb.support.base.Params;
import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccRangingStartedParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.generic.GenericSpecificationParams;
import com.google.uwb.support.oemextension.SessionStatus;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

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
    @VisibleForTesting
    public static final int SESSION_SEND_DATA = 7;

    // TODO: don't expose the internal field for testing.
    @VisibleForTesting
    final ConcurrentHashMap<Integer, UwbSession> mSessionTable = new ConcurrentHashMap();
    final ConcurrentHashMap<Long, ReceivedDataInfo> mReceivedDataMap =
            new ConcurrentHashMap<Long, ReceivedDataInfo>();
    final ConcurrentHashMap<Integer, List<UwbSession>> mNonPrivilegedUidToFiraSessionsTable =
            new ConcurrentHashMap();
    private final ActivityManager mActivityManager;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbMetrics mUwbMetrics;
    private final UwbConfigurationManager mConfigurationManager;
    private final UwbSessionNotificationManager mSessionNotificationManager;
    private final UwbAdvertiseManager mAdvertiseManager;
    private final UwbInjector mUwbInjector;
    private final AlarmManager mAlarmManager;
    private final int mMaxSessionNumber;
    private final Looper mLooper;
    private final EventTask mEventTask;

    private Boolean mIsRangeDataNtfConfigEnableDisableSupported;

    public UwbSessionManager(
            UwbConfigurationManager uwbConfigurationManager,
            NativeUwbManager nativeUwbManager, UwbMetrics uwbMetrics,
            UwbAdvertiseManager uwbAdvertiseManager,
            UwbSessionNotificationManager uwbSessionNotificationManager,
            UwbInjector uwbInjector, AlarmManager alarmManager, ActivityManager activityManager,
            Looper serviceLooper) {
        mNativeUwbManager = nativeUwbManager;
        mNativeUwbManager.setSessionListener(this);
        mUwbMetrics = uwbMetrics;
        mAdvertiseManager = uwbAdvertiseManager;
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
        if (rangingData.getRangingMeasuresType()
                == UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY) {
            for (UwbTwoWayMeasurement measure : rangingData.getRangingTwoWayMeasures()) {
                if (measure.getRangingStatus() == UwbUciConstants.STATUS_CODE_OK) {
                    return false;
                }
            }
        } else if (rangingData.getRangingMeasuresType()
                == UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA) {
            UwbOwrAoaMeasurement measure = rangingData.getRangingOwrAoaMeasure();
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
            processRangeData(rangingData, uwbSession);
            if (uwbSession.mRangingErrorStreakTimeoutMs
                    != UwbSession.RANGING_RESULT_ERROR_NO_TIMEOUT) {
                if (hasAllRangingResultError(rangingData)) {
                    uwbSession.startRangingResultErrorStreakTimerIfNotSet();
                } else {
                    uwbSession.stopRangingResultErrorStreakTimerIfSet();
                }
            }
        } else {
            Log.i(TAG, "Session is not initialized or Ranging Data is Null");
        }
    }

    /* Notification of received data over UWB to Application*/
    @Override
    public void onDataReceived(
            long sessionId, int status, long sequenceNum,
            byte[] address, int sourceEndPoint, int destEndPoint, byte[] data) {
        Log.d(TAG, "onDataReceived - Data: " + UwbUtil.toHexString(data));

        // Size of address is always expected to be 8(EXTENDED_ADDRESS_BYTE_LENGTH). It can contain
        // the MacAddress in short format however (2 LSB with MacAddress, 6 MSB zeroed out).
        if (address.length != UWB_DEVICE_EXT_MAC_ADDRESS_LEN) {
            Log.e(TAG, "onDataReceived(): Received data for sessionId=" + sessionId
                    + ", with unexpected MacAddress length = " + address.length);
            return;
        }
        Long longAddress = ByteBuffer.wrap(address).getLong();

        ReceivedDataInfo info = new ReceivedDataInfo();
        info.sessionId = sessionId;
        info.status = status;
        info.sequenceNum = sequenceNum;
        info.address = longAddress;
        info.sourceEndPoint = sourceEndPoint;
        info.destEndPoint = destEndPoint;
        info.payload = data;
        mReceivedDataMap.put(longAddress, info);
    }

    private static final class ReceivedDataInfo {
        public long sessionId;
        public int status;
        public long sequenceNum;
        public long address;
        public int sourceEndPoint;
        public int destEndPoint;
        public byte[] payload;
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
                + UwbSessionNotificationHelper.getSessionStateString(state)
                + ", reasonCode:" + reasonCode);
        UwbSession uwbSession = mSessionTable.get((int) sessionId);

        if (uwbSession == null) {
            Log.d(TAG, "onSessionStatusNotificationReceived - invalid session");
            return;
        }
        if (mUwbInjector.getUwbServiceCore().isOemExtensionCbRegistered()) {
            PersistableBundle sessionStatusBundle = new SessionStatus.Builder()
                    .setSessionId(sessionId)
                    .setState(state)
                    .setReasonCode(reasonCode)
                    .build()
                    .toBundle();
            try {
                mUwbInjector.getUwbServiceCore().getOemExtensionCallback()
                        .onSessionStatusNotificationReceived(sessionStatusBundle);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send vendor notification", e);
            }
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
        int status = mConfigurationManager.setAppConfigurations(uwbSession.getSessionId(),
                uwbSession.getParams(), uwbSession.getChipId());
        if (status == UwbUciConstants.STATUS_CODE_OK
                && mUwbInjector.getUwbServiceCore().isOemExtensionCbRegistered()) {
            try {
                status = mUwbInjector.getUwbServiceCore().getOemExtensionCallback()
                        .onSessionConfigurationReceived(uwbSession.getParams().toBundle());
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send vendor notification", e);
            }
        }
        return status;
    }

    public synchronized void initSession(AttributionSource attributionSource,
            SessionHandle sessionHandle, int sessionId, String protocolName, Params params,
            IUwbRangingCallbacks rangingCallbacks, String chipId)
            throws RemoteException {
        Log.i(TAG, "initSession() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);
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
        Log.i(TAG, "deinitSession() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);
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
        Log.i(TAG, "startRanging() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);

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

    private synchronized void stopRangingInternal(SessionHandle sessionHandle,
            boolean triggeredBySystemPolicy) {
        if (!isExistedSession(sessionHandle)) {
            Log.i(TAG, "Not initialized session ID");
            return;
        }

        int sessionId = getSessionId(sessionHandle);
        Log.i(TAG, "stopRanging() - sessionId: " + sessionId
                + ", sessionHandle: " + sessionHandle);

        UwbSession uwbSession = getUwbSession(sessionId);
        int currentSessionState = getCurrentSessionState(sessionId);
        if (currentSessionState == UwbUciConstants.UWB_SESSION_STATE_ACTIVE) {
            mEventTask.execute(SESSION_STOP_RANGING, uwbSession, triggeredBySystemPolicy ? 1 : 0);
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

    public synchronized void stopRanging(SessionHandle sessionHandle) {
        stopRangingInternal(sessionHandle, false /* triggeredBySystemPolicy */);
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

    private void processRangeData(UwbRangingData rangingData, UwbSession uwbSession) {
        if (rangingData.getRangingMeasuresType()
                != UwbUciConstants.RANGING_MEASUREMENT_TYPE_OWR_AOA) {
            return;
        }

        if (!isValidUwbSessionForOwrAoaRanging(uwbSession)) {
            return;
        }

        // Record the OWR Aoa Measurement from the RANGE_DATA_NTF.
        UwbOwrAoaMeasurement uwbOwrAoaMeasurement = rangingData.getRangingOwrAoaMeasure();
        mAdvertiseManager.updateAdvertiseTarget(uwbOwrAoaMeasurement);

        byte[] macAddress = uwbOwrAoaMeasurement.getMacAddress();
        uwbSession.setRemoteMacAddress(macAddress);

        // Get any application payload data received in this OWR AOA ranging session and notify it.
        ReceivedDataInfo receivedDataInfo = getReceivedDataInfo(macAddress);
        if (receivedDataInfo == null) {
            return;
        }

        UwbSession uwbSessionFromReceivedData = getUwbSession((int) receivedDataInfo.sessionId);
        if (uwbSessionFromReceivedData != uwbSession) {
            return;
        }

        if (mAdvertiseManager.isPointedTarget(macAddress)) {
            UwbAddress uwbAddress = UwbAddress.fromBytes(TlvUtil.getReverseBytes(macAddress));
            mSessionNotificationManager.onDataReceived(
                    uwbSession, uwbAddress, new PersistableBundle(), receivedDataInfo.payload);
            mAdvertiseManager.removeAdvertiseTarget(macAddress);
        }
    }

    /** Get any received data for the given device MacAddress */
    @VisibleForTesting
    public ReceivedDataInfo getReceivedDataInfo(byte[] macAddress) {
        // Convert the macAddress to a long as the address could be in short or extended format.
        Long longAddress = byteArrayToLong(macAddress);
        return mReceivedDataMap.get(longAddress);
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
            handleOnDeInit(uwbSession);
        }

        // Not resetting chip on UWB toggle off.
        // mNativeUwbManager.resetDevice(UwbUciConstants.UWBS_RESET);
    }

    public synchronized void handleOnDeInit(UwbSession uwbSession) {
        if (!isExistedSession(uwbSession.getSessionId())) {
            Log.i(TAG, "onDeinit - Ignoring already deleted session "
                    + uwbSession.getSessionId());
            return;
        }
        Log.d(TAG, "onDeinit: " + uwbSession.getSessionId());
        mSessionNotificationManager.onRangingClosedWithApiReasonCode(uwbSession,
                RangingChangeReason.SYSTEM_POLICY);
        mUwbMetrics.logRangingCloseEvent(uwbSession, UwbUciConstants.STATUS_CODE_OK);

        // Reset all UWB session timers when the session is de-init.
        uwbSession.stopTimers();
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

    private synchronized int reconfigureInternal(SessionHandle sessionHandle,
            @Nullable Params params, boolean triggeredByFgStateChange) {
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
        mEventTask.execute(SESSION_RECONFIG_RANGING,
                new ReconfigureEventParams(uwbSession, params, triggeredByFgStateChange));
        return 0;
    }

    public synchronized int reconfigure(SessionHandle sessionHandle, @Nullable Params params) {
        return reconfigureInternal(sessionHandle, params, false /* triggeredByFgStateChange */);
    }

    /** Send the payload data to a remote device in the UWB session */
    public synchronized void sendData(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            PersistableBundle params, byte[] data) {
        SendDataInfo info = new SendDataInfo();
        info.sessionHandle = sessionHandle;
        info.remoteDeviceAddress = remoteDeviceAddress;
        info.params = params;
        info.data = data;

        mEventTask.execute(SESSION_SEND_DATA, info);
    }

    private static final class SendDataInfo {
        public SessionHandle sessionHandle;
        public UwbAddress remoteDeviceAddress;
        public PersistableBundle params;
        public byte[] data;
    }

    void removeSession(UwbSession uwbSession) {
        if (uwbSession != null) {
            uwbSession.getBinder().unlinkToDeath(uwbSession, 0);
            removeFromNonPrivilegedUidToFiraSessionTableIfNecessary(uwbSession);
            removeAdvertiserData(uwbSession);
            mSessionTable.remove(uwbSession.getSessionId());
        }
    }

    private void removeAdvertiserData(UwbSession uwbSession) {
        byte[] remoteMacAddress = uwbSession.getRemoteMacAddress();
        if (remoteMacAddress != null) {
            mAdvertiseManager.removeAdvertiseTarget(remoteMacAddress);
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

    private static class ReconfigureEventParams {
        public final UwbSession uwbSession;
        public final Params params;
        public final boolean triggeredByFgStateChange;

        ReconfigureEventParams(UwbSession uwbSession, Params params,
                boolean triggeredByFgStateChange) {
            this.uwbSession = uwbSession;
            this.params = params;
            this.triggeredByFgStateChange = triggeredByFgStateChange;
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
                    handleOpenRanging(uwbSession);
                    break;
                }

                case SESSION_START_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    handleStartRanging(uwbSession);
                    break;
                }

                case SESSION_STOP_RANGING: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    boolean triggeredBySystemPolicy = msg.arg1 == 1;
                    handleStopRanging(uwbSession, triggeredBySystemPolicy);
                    break;
                }

                case SESSION_RECONFIG_RANGING: {
                    Log.d(TAG, "SESSION_RECONFIG_RANGING");
                    ReconfigureEventParams params = (ReconfigureEventParams) msg.obj;
                    handleReconfigure(
                            params.uwbSession, params.params, params.triggeredByFgStateChange);
                    break;
                }

                case SESSION_CLOSE: {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    handleClose(uwbSession);
                    break;
                }

                case SESSION_ON_DEINIT : {
                    UwbSession uwbSession = (UwbSession) msg.obj;
                    handleOnDeInit(uwbSession);
                    break;
                }

                case SESSION_SEND_DATA : {
                    Log.d(TAG, "SESSION_SEND_DATA");
                    SendDataInfo info = (SendDataInfo) msg.obj;
                    handleSendData(info);
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

        public void execute(int task, Object obj, int arg1) {
            Message msg = mEventTask.obtainMessage();
            msg.what = task;
            msg.obj = obj;
            msg.arg1 = arg1;
            this.sendMessage(msg);
        }

        private void handleOpenRanging(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
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

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(initSessionTask,
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS);
            } catch (TimeoutException e) {
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

        private void handleStartRanging(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
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
            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(startRangingTask,
                        IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Start Ranging - status : TIMEOUT");
                mSessionNotificationManager.onRangingStartFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_FAILED);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            mUwbMetrics.longRangingStartEvent(uwbSession, status);
        }

        private void handleStopRanging(UwbSession uwbSession, boolean triggeredBySystemPolicy) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
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
                                int apiReasonCode = triggeredBySystemPolicy
                                        ? RangingChangeReason.SYSTEM_POLICY
                                        : RangingChangeReason.LOCAL_API;
                                mSessionNotificationManager.onRangingStoppedWithApiReasonCode(
                                        uwbSession, apiReasonCode);
                            } else {
                                status = UwbUciConstants.STATUS_CODE_FAILED;
                                mSessionNotificationManager.onRangingStopFailed(uwbSession,
                                        status);
                            }
                        }
                        return status;
                    });


            int status = UwbUciConstants.STATUS_CODE_FAILED;
            int timeoutMs = IUwbAdapter.RANGING_SESSION_START_THRESHOLD_MS;
            if (uwbSession.getProtocolName().equals(PROTOCOL_NAME)) {
                // TODO (b/235714647): Temporary workaround to 2x ranging interval.
                int minTimeoutNecessary = uwbSession.getCurrentFiraRangingIntervalMs() * 2;
                timeoutMs = timeoutMs > minTimeoutNecessary ? timeoutMs : minTimeoutNecessary;
            }
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(stopRangingTask, timeoutMs);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Stop Ranging - status : TIMEOUT");
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
            // Reset all UWB session timers when the session is stopped.
            uwbSession.stopTimers();
            removeAdvertiserData(uwbSession);
        }

        private void handleReconfigure(UwbSession uwbSession, @Nullable Params param,
                boolean triggeredByFgStateChange) {
            if (!(param instanceof FiraRangingReconfigureParams)) {
                Log.e(TAG, "Invalid reconfigure params: " + param);
                mSessionNotificationManager.onRangingReconfigureFailed(
                        uwbSession, UwbUciConstants.STATUS_CODE_INVALID_PARAM);
                return;
            }
            FiraRangingReconfigureParams rangingReconfigureParams =
                    (FiraRangingReconfigureParams) param;
            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> cmdTask = new FutureTask<>(
                    () -> {
                        int status = UwbUciConstants.STATUS_CODE_FAILED;
                        synchronized (uwbSession.getWaitObj()) {
                            // Handle SESSION_UPDATE_CONTROLLER_MULTICAST_LIST_CMD
                            UwbAddress[] addrList = rangingReconfigureParams.getAddressList();
                            Integer action = rangingReconfigureParams.getAction();
                            // Action will indicate if this is a controlee add/remove.
                            //  if null, it's a session configuration change.
                            if (action != null) {
                                if (addrList == null) {
                                    Log.e(TAG,
                                            "Multicast update missing the address list.");
                                    return status;
                                }
                                int dstAddressListSize = addrList.length;
                                List<Short> dstAddressList = new ArrayList<>();
                                for (UwbAddress address : addrList) {
                                    dstAddressList.add(
                                            ByteBuffer.wrap(address.toBytes()).getShort(0));
                                }
                                int[] subSessionIdList;
                                if (!ArrayUtils.isEmpty(
                                        rangingReconfigureParams.getSubSessionIdList())) {
                                    subSessionIdList =
                                        rangingReconfigureParams.getSubSessionIdList();
                                } else {
                                    // Set to 0's for the UCI stack.
                                    subSessionIdList = new int[dstAddressListSize];
                                }
                                boolean isV2 = rangingReconfigureParams.getMessageControl() != null;
                                if (isV2) {
                                    int messageControl =
                                            rangingReconfigureParams.getMessageControl();
                                    int[] subsessionKeyList =
                                            rangingReconfigureParams.getSubSessionKeyList();

                                    status = mNativeUwbManager.controllerMulticastListUpdateV2(
                                            uwbSession.getSessionId(),
                                            action,
                                            subSessionIdList.length,
                                            ArrayUtils.toPrimitive(dstAddressList),
                                            subSessionIdList,
                                            messageControl,
                                            subsessionKeyList,
                                            uwbSession.getChipId());
                                } else {
                                    status = mNativeUwbManager.controllerMulticastListUpdateV1(
                                            uwbSession.getSessionId(),
                                            action,
                                            subSessionIdList.length,
                                            ArrayUtils.toPrimitive(dstAddressList),
                                            subSessionIdList,
                                            uwbSession.getChipId());
                                }
                                if (status != UwbUciConstants.STATUS_CODE_OK) {
                                    Log.e(TAG, "Unable to update controller multicast list.");
                                    if (action == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                        mSessionNotificationManager.onControleeAddFailed(
                                                uwbSession, status);
                                    } else if (action == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                        mSessionNotificationManager.onControleeRemoveFailed(
                                                uwbSession, status);
                                    }
                                    return status;
                                }

                                uwbSession.getWaitObj().blockingWait();

                                UwbMulticastListUpdateStatus multicastList =
                                        uwbSession.getMulticastListUpdateStatus();

                                if (multicastList == null) {
                                    Log.e(TAG, "Confirmed controller multicast list is empty!");
                                    return status;
                                }

                                for (int i = 0; i < multicastList.getNumOfControlee(); i++) {
                                    int actionStatus = multicastList.getStatus()[i];
                                    if (actionStatus == UwbUciConstants.STATUS_CODE_OK) {
                                        if (action == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                            uwbSession.addControlee(
                                                    multicastList.getControleeUwbAddresses()[i]);
                                            mSessionNotificationManager.onControleeAdded(
                                                    uwbSession);
                                        } else if (action == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                            uwbSession.removeControlee(
                                                    multicastList.getControleeUwbAddresses()[i]);
                                            mSessionNotificationManager.onControleeRemoved(
                                                    uwbSession);
                                        }
                                    }
                                    else {
                                        status = actionStatus;
                                        if (action == MULTICAST_LIST_UPDATE_ACTION_ADD) {
                                            mSessionNotificationManager.onControleeAddFailed(
                                                    uwbSession, actionStatus);
                                        } else if (action == MULTICAST_LIST_UPDATE_ACTION_DELETE) {
                                            mSessionNotificationManager.onControleeRemoveFailed(
                                                    uwbSession, actionStatus);
                                        }
                                    }
                                }
                            } else {
                                // setAppConfigurations only applies to config changes,
                                //  not controlee list changes
                                status = mConfigurationManager.setAppConfigurations(
                                        uwbSession.getSessionId(), param, uwbSession.getChipId());
                            }
                            if (status == UwbUciConstants.STATUS_CODE_OK) {
                                // only call this if all controlees succeeded otherwise the
                                //  fail status cause a onRangingReconfigureFailed later.
                                mSessionNotificationManager.onRangingReconfigured(uwbSession);
                            }
                            Log.d(TAG, "Multicast update status: " + status);
                            return status;
                        }
                    });
            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(cmdTask,
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Reconfigure - status : TIMEOUT");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if (status != UwbUciConstants.STATUS_CODE_OK) {
                Log.i(TAG, "Failed to Reconfigure : " + status);
                if (!triggeredByFgStateChange) {
                    mSessionNotificationManager.onRangingReconfigureFailed(uwbSession, status);
                }
            }
        }

        private void handleClose(UwbSession uwbSession) {
            // TODO(b/211445008): Consolidate to a single uwb thread.
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

            int status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(closeTask,
                        IUwbAdapter.RANGING_SESSION_CLOSE_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Stop Ranging - status : TIMEOUT");
                mSessionNotificationManager.onRangingClosed(uwbSession, status);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            mUwbMetrics.logRangingCloseEvent(uwbSession, status);

            // Reset all UWB session timers when the session is closed.
            uwbSession.stopTimers();
            removeSession(uwbSession);
            Log.i(TAG, "deinit finish : status :" + status);
        }

        private void handleSendData(SendDataInfo sendDataInfo) {
            int status = UwbUciConstants.STATUS_CODE_ERROR_SESSION_NOT_EXIST;
            SessionHandle sessionHandle = sendDataInfo.sessionHandle;
            if (sessionHandle == null) {
                Log.i(TAG, "Not present sessionHandle");
                mSessionNotificationManager.onDataSendFailed(
                        null, sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
                return;
            }

            Integer sessionId = getSessionId(sessionHandle);
            if (sessionId == null) {
                Log.i(TAG, "UwbSessionId not found");
                mSessionNotificationManager.onDataSendFailed(
                        null, sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
                return;
            }

            // TODO(b/256675656): Check if there is race condition between uwbSession being
            // retrieved here and used below (and similar for uwbSession being stored in the
            //  mLooper message and being used during processing for all other message types).
            UwbSession uwbSession = getUwbSession(sessionId);
            if (uwbSession == null) {
                Log.i(TAG, "UwbSession not found");
                mSessionNotificationManager.onDataSendFailed(
                        null, sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
                return;
            }

            // TODO(b/211445008): Consolidate to a single uwb thread.
            FutureTask<Integer> sendDataTask = new FutureTask<>((Callable<Integer>) () -> {
                int sendDataStatus = UwbUciConstants.STATUS_CODE_FAILED;
                synchronized (uwbSession.getWaitObj()) {
                    if (!isValidUwbSessionForApplicationDataTransfer(uwbSession)) {
                        sendDataStatus = UwbUciConstants.STATUS_CODE_FAILED;
                        Log.i(TAG, "UwbSession not in active state");
                        mSessionNotificationManager.onDataSendFailed(
                                uwbSession, sendDataInfo.remoteDeviceAddress, sendDataStatus,
                                sendDataInfo.params);
                        return sendDataStatus;
                    }
                    if (!isValidSendDataInfo(sendDataInfo)) {
                        sendDataStatus = UwbUciConstants.STATUS_CODE_INVALID_PARAM;
                        mSessionNotificationManager.onDataSendFailed(
                                uwbSession, sendDataInfo.remoteDeviceAddress, sendDataStatus,
                                sendDataInfo.params);
                        return sendDataStatus;
                    }

                    // TODO(b/246678053): Check on the usage of sequenceNum field, is it used
                    //  for ordering the data payload packets by host or firmware ?
                    int sequenceNum = 1;

                    sendDataStatus = mNativeUwbManager.sendData(
                            uwbSession.getSessionId(), sendDataInfo.remoteDeviceAddress.toBytes(),
                            UwbUciConstants.UWB_DESTINATION_END_POINT_HOST, sequenceNum,
                            sendDataInfo.data);
                    Log.d(TAG, "MSG_SESSION_SEND_DATA status: " + sendDataStatus);

                    if (sendDataStatus == UwbUciConstants.STATUS_CODE_OK) {
                        mSessionNotificationManager.onDataSent(
                                uwbSession, sendDataInfo.remoteDeviceAddress,
                                sendDataInfo.params);
                    } else {
                        mSessionNotificationManager.onDataSendFailed(
                                uwbSession, sendDataInfo.remoteDeviceAddress, sendDataStatus,
                                sendDataInfo.params);
                    }
                    return sendDataStatus;
                }
            });

            status = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                status = mUwbInjector.runTaskOnSingleThreadExecutor(sendDataTask,
                        IUwbAdapter.RANGING_SESSION_OPEN_THRESHOLD_MS);
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to Send data - status : TIMEOUT");
                mSessionNotificationManager.onDataSendFailed(uwbSession,
                        sendDataInfo.remoteDeviceAddress, status, sendDataInfo.params);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isValidUwbSessionForOwrAoaRanging(UwbSession uwbSession) {
        Params params = uwbSession.getParams();
        if (params instanceof FiraOpenSessionParams) {
            FiraOpenSessionParams firaParams = (FiraOpenSessionParams) params;
            if (firaParams.getRangingRoundUsage() != ROUND_USAGE_OWR_AOA_MEASUREMENT) {
                return false;
            }
            if (firaParams.getDeviceRole() != RANGING_DEVICE_ROLE_OBSERVER) {
                return false;
            }
            return true;
        }
        return false;
    }

    private boolean isValidUwbSessionForApplicationDataTransfer(UwbSession uwbSession) {
        // The session state must be SESSION_STATE_ACTIVE, as that's required to transmit or receive
        // application data.
        return uwbSession != null && uwbSession.getSessionState() == UWB_SESSION_STATE_ACTIVE;
    }

    private boolean isValidSendDataInfo(SendDataInfo sendDataInfo) {
        if (sendDataInfo.data == null) {
            return false;
        }

        if (sendDataInfo.remoteDeviceAddress == null) {
            return false;
        }

        if (sendDataInfo.remoteDeviceAddress.size()
                > UwbUciConstants.UWB_DEVICE_EXT_MAC_ADDRESS_LEN) {
            return false;
        }
        return true;
    }

    public class UwbSession implements IBinder.DeathRecipient {
        @VisibleForTesting
        public static final long RANGING_RESULT_ERROR_NO_TIMEOUT = 0;
        private static final String RANGING_RESULT_ERROR_STREAK_TIMER_TAG =
                "UwbSessionRangingResultError";
        private static final long NON_PRIVILEGED_BG_APP_TIMEOUT_MS = 120_000;
        @VisibleForTesting
        public static final String NON_PRIVILEGED_BG_APP_TIMER_TAG =
                "UwbSessionNonPrivilegedBgAppError";

        private final AttributionSource mAttributionSource;
        private final SessionHandle mSessionHandle;
        private final int mSessionId;
        private byte[] mRemoteMacAddress;
        private final IUwbRangingCallbacks mIUwbRangingCallbacks;
        private final String mProtocolName;
        private final IBinder mIBinder;
        private final WaitObj mWaitObj;
        private Params mParams;
        private int mSessionState;
        private UwbMulticastListUpdateStatus mMulticastListUpdateStatus;
        private final int mProfileType;
        private AlarmManager.OnAlarmListener mRangingResultErrorStreakTimerListener;
        private AlarmManager.OnAlarmListener mNonPrivilegedBgAppTimerListener;
        private final String mChipId;
        private boolean mHasNonPrivilegedFgApp = false;
        private @FiraParams.RangeDataNtfConfig Integer mOrigRangeDataNtfConfig;
        private long mRangingErrorStreakTimeoutMs = RANGING_RESULT_ERROR_NO_TIMEOUT;

        @VisibleForTesting
        public List<UwbControlee> mControleeList;

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

            if (params instanceof FiraOpenSessionParams) {
                FiraOpenSessionParams firaParams = (FiraOpenSessionParams) params;
                if (firaParams.getDestAddressList() != null) {
                    // Set up list of all controlees involved.
                    mControleeList = firaParams.getDestAddressList().stream()
                            .map(UwbControlee::new)
                            .collect(Collectors.toList());
                }
                mRangingErrorStreakTimeoutMs = firaParams
                        .getRangingErrorStreakTimeoutMs();
            }
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

        public List<UwbControlee> getControleeList() {
            return Collections.unmodifiableList(mControleeList);
        }

        /**
         * Adds a Controlee to the session. This should only be called to reflect
         *  the state of the native UWB interface.
         * @param address The UWB address of the Controlee to add.
         */
        public void addControlee(UwbAddress address) {
            if (mControleeList != null
                    && !mControleeList.stream().anyMatch(e -> e.getUwbAddress().equals(address))) {
                mControleeList.add(new UwbControlee(address));
            }
        }

        /**
         * Removes a Controlee from the session. This should only be called to reflect
         *  the state of the native UWB interface.
         * @param address The UWB address of the Controlee to remove.
         */
        public void removeControlee(UwbAddress address) {
            if (mControleeList != null) {
                mControleeList.removeIf(e -> e.getUwbAddress().equals(address));
            }
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
            if (reconfigureParams.getRangeDataAoaAzimuthLower() != null) {
                newParamsBuilder.setRangeDataNtfAoaAzimuthLower(
                        reconfigureParams.getRangeDataAoaAzimuthLower());
            }
            if (reconfigureParams.getRangeDataAoaAzimuthUpper() != null) {
                newParamsBuilder.setRangeDataNtfAoaAzimuthUpper(
                        reconfigureParams.getRangeDataAoaAzimuthUpper());
            }
            if (reconfigureParams.getRangeDataAoaElevationLower() != null) {
                newParamsBuilder.setRangeDataNtfAoaElevationLower(
                        reconfigureParams.getRangeDataAoaElevationLower());
            }
            if (reconfigureParams.getRangeDataAoaElevationUpper() != null) {
                newParamsBuilder.setRangeDataNtfAoaElevationUpper(
                        reconfigureParams.getRangeDataAoaElevationUpper());
            }
            this.mParams = newParamsBuilder.build();
        }

        public int getCurrentFiraRangingIntervalMs() {
            FiraOpenSessionParams firaOpenSessionParams = (FiraOpenSessionParams) mParams;
            return firaOpenSessionParams.getRangingIntervalMs()
                    * (firaOpenSessionParams.getBlockStrideLength() + 1);
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

        public byte[] getRemoteMacAddress() {
            return mRemoteMacAddress;
        }

        public void setRemoteMacAddress(byte[] remoteMacAddress) {
            this.mRemoteMacAddress = Arrays.copyOf(remoteMacAddress, remoteMacAddress.length);
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
         * {@link UwbSession#mRangingErrorStreakTimeoutMs }.
         */
        public void startRangingResultErrorStreakTimerIfNotSet() {
            // Start a timer on first failure to detect continuous failures.
            if (mRangingResultErrorStreakTimerListener == null) {
                mRangingResultErrorStreakTimerListener = () -> {
                    Log.w(TAG, "Continuous errors or no ranging results detected for 30 seconds."
                            + " Stopping session");
                    stopRangingInternal(mSessionHandle, true /* triggeredBySystemPolicy */);
                };
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mUwbInjector.getElapsedSinceBootMillis()
                                + mRangingErrorStreakTimeoutMs,
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


        /**
         * Starts a timer to detect if the app that started the UWB session is in the background
         * for longer than {@link UwbSession#mNonPrivilegedBgTimeoutMs }.
         */
        private void startNonPrivilegedBgAppTimerIfNotSet() {
            // Start a timer when the non-privileged app goes into the background.
            if (mNonPrivilegedBgAppTimerListener == null) {
                mNonPrivilegedBgAppTimerListener = () -> {
                    Log.w(TAG, "Non-privileged app in background for longer than timeout - "
                            + " Stopping session");
                    stopRangingInternal(mSessionHandle, true /* triggeredBySystemPolicy */);
                };
                mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        mUwbInjector.getElapsedSinceBootMillis()
                                + NON_PRIVILEGED_BG_APP_TIMEOUT_MS,
                        NON_PRIVILEGED_BG_APP_TIMER_TAG,
                        mNonPrivilegedBgAppTimerListener, mEventTask);
            }
        }

        private void stopNonPrivilegedBgAppTimerIfSet() {
            // Stop the timer when the non-privileged app goes into the foreground.
            if (mNonPrivilegedBgAppTimerListener != null) {
                mAlarmManager.cancel(mNonPrivilegedBgAppTimerListener);
                mNonPrivilegedBgAppTimerListener = null;
            }
        }

        private void stopTimers() {
            // Reset any stored error streak or non-privileged background app timestamps.
            stopRangingResultErrorStreakTimerIfSet();
            stopNonPrivilegedBgAppTimerIfSet();
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
            reconfigureInternal(
                    mSessionHandle, reconfigureParams, true /* triggeredByFgStateChange */);

            // When a non-privileged app goes into the background, start a timer (that will stop the
            // ranging session). If the app goes back into the foreground, the timer will get reset
            // (but any stopped UWB session will not be auto-resumed).
            if (!mHasNonPrivilegedFgApp) {
                startNonPrivilegedBgAppTimerIfNotSet();
            } else {
                stopNonPrivilegedBgAppTimerIfSet();
            }
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
