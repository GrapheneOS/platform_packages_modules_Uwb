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
package com.android.server.uwb.jni;

import android.annotation.NonNull;
import android.util.Log;

import com.android.internal.annotations.Keep;
import com.android.server.uwb.UciLogModeStore;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.DtTagUpdateRangingRoundsStatus;
import com.android.server.uwb.data.UwbConfigStatusData;
import com.android.server.uwb.data.UwbMulticastListUpdateStatus;
import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTlvData;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.data.UwbVendorUciResponse;
import com.android.server.uwb.info.UwbPowerStats;
import com.android.server.uwb.multchip.UwbMultichipData;

@Keep
public class NativeUwbManager {
    private static final String TAG = NativeUwbManager.class.getSimpleName();

    public final Object mSessionFnLock = new Object();
    public final Object mSessionCountFnLock = new Object();
    public final Object mGlobalStateFnLock = new Object();
    public final Object mGetSessionStatusFnLock = new Object();
    public final Object mSetAppConfigFnLock = new Object();
    private final UwbInjector mUwbInjector;
    private final UciLogModeStore mUciLogModeStore;
    private final UwbMultichipData mUwbMultichipData;
    protected INativeUwbManager.DeviceNotification mDeviceListener;
    protected INativeUwbManager.SessionNotification mSessionListener;
    private long mDispatcherPointer;
    protected INativeUwbManager.VendorNotification mVendorListener;

    public NativeUwbManager(@NonNull UwbInjector uwbInjector, UciLogModeStore uciLogModeStore,
            UwbMultichipData uwbMultichipData) {
        mUwbInjector = uwbInjector;
        mUciLogModeStore = uciLogModeStore;
        mUwbMultichipData = uwbMultichipData;
        loadLibrary();
    }

    protected void loadLibrary() {
        System.loadLibrary("uwb_uci_jni_rust");
        nativeInit();
    }

    public void setDeviceListener(INativeUwbManager.DeviceNotification deviceListener) {
        mDeviceListener = deviceListener;
    }

    public void setSessionListener(INativeUwbManager.SessionNotification sessionListener) {
        mSessionListener = sessionListener;
    }

    public void setVendorListener(INativeUwbManager.VendorNotification vendorListener) {
        mVendorListener = vendorListener;
    }

    /**
     * Device status callback invoked via the JNI
     */
    public void onDeviceStatusNotificationReceived(int deviceState, String chipId) {
        Log.d(TAG, "onDeviceStatusNotificationReceived(" + deviceState + ", " + chipId + ")");
        mDeviceListener.onDeviceStatusNotificationReceived(deviceState, chipId);
    }

    /**
     * Error callback invoked via the JNI
     */
    public void onCoreGenericErrorNotificationReceived(int status, String chipId) {
        Log.d(TAG, "onCoreGenericErrorNotificationReceived(" + status + ", " + chipId + ")");
        mDeviceListener.onCoreGenericErrorNotificationReceived(status, chipId);
    }

    public void onSessionStatusNotificationReceived(long id, int state, int reasonCode) {
        Log.d(TAG, "onSessionStatusNotificationReceived(" + id + ", " + state + ", " + reasonCode
                + ")");
        mSessionListener.onSessionStatusNotificationReceived(id, state, reasonCode);
    }

    public void onRangeDataNotificationReceived(UwbRangingData rangeData) {
        Log.d(TAG, "onRangeDataNotificationReceived : " + rangeData);
        mSessionListener.onRangeDataNotificationReceived(rangeData);
    }

    public void onMulticastListUpdateNotificationReceived(
            UwbMulticastListUpdateStatus multicastListUpdateData) {
        Log.d(TAG, "onMulticastListUpdateNotificationReceived : " + multicastListUpdateData);
        mSessionListener.onMulticastListUpdateNotificationReceived(multicastListUpdateData);
    }

    /**
     * Vendor callback invoked via the JNI
     */
    public void onVendorUciNotificationReceived(int gid, int oid, byte[] payload) {
        Log.d(TAG, "onVendorUciNotificationReceived: " + gid + ", " + oid + ", " + payload);
        mVendorListener.onVendorUciNotificationReceived(gid, oid, payload);
    }

    /**
     * Enable UWB hardware.
     *
     * @return : If this returns true, UWB is on
     */
    public synchronized boolean doInitialize() {
        mDispatcherPointer = nativeDispatcherNew(mUwbMultichipData.getChipIds().toArray());
        for (String chipId : mUwbMultichipData.getChipIds()) {
            if (!nativeDoInitialize(chipId)) {
                return false;
            }
        }
        nativeSetLogMode(mUciLogModeStore.getMode());
        return true;
    }

    /**
     * Disable UWB hardware.
     *
     * @return : If this returns true, UWB is off
     */
    public synchronized boolean doDeinitialize() {
        for (String chipId : mUwbMultichipData.getChipIds()) {
            nativeDoDeinitialize(chipId);
        }

        nativeDispatcherDestroy();
        mDispatcherPointer = 0L;
        return true;
    }

    public synchronized long getTimestampResolutionNanos() {
        return 0L;
        /* TODO: Not Implemented in native stack
        return nativeGetTimestampResolutionNanos(); */
    }

    /**
     * Retrieves maximum number of UWB sessions concurrently
     *
     * @return : Retrieves maximum number of UWB sessions concurrently
     */
    public int getMaxSessionNumber() {
        return nativeGetMaxSessionNumber();
    }

    /**
     * Retrieves power related stats
     */
    public UwbPowerStats getPowerStats(String chipId) {
        return nativeGetPowerStats(chipId);
    }

    /**
     * Creates the new UWB session with parameter session ID and type of the session.
     *
     * @param sessionId   : Session ID is 4 Octets unique random number generated by application
     * @param sessionType : Type of session 0x00: Ranging session 0x01: Data transfer 0x02-0x9F: RFU
     *                    0xA0-0xCF: Reserved for Vendor Specific use case 0xD0: Device Test Mode
     *                    0xD1-0xDF: RFU 0xE0-0xFF: Vendor Specific use
     * @param chipId      : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte initSession(int sessionId, byte sessionType, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeSessionInit(sessionId, sessionType, chipId);
        }
    }

    /**
     * De-initializes the session.
     *
     * @param sessionId : Session ID for which session to be de-initialized
     * @param chipId    : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte deInitSession(int sessionId, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeSessionDeInit(sessionId, chipId);
        }
    }

    /**
     * reset the UWBs
     *
     * @param resetConfig : Reset config
     * @param chipId      : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte resetDevice(byte resetConfig, String chipId) {
        return nativeResetDevice(resetConfig, chipId);
    }

    /**
     * Retrieves number of UWB sessions in the UWBS.
     *
     * @param chipId : Identifier of UWB chip for multi-HAL devices
     * @return : Number of UWB sessions present in the UWBS.
     */
    public byte getSessionCount(String chipId) {
        synchronized (mSessionCountFnLock) {
            return nativeGetSessionCount(chipId);
        }
    }

    /**
     * Queries the current state of the UWB session.
     *
     * @param sessionId : Session of the UWB session for which current session state to be queried
     * @param chipId    : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbUciConstants}  Session State
     */
    public byte getSessionState(int sessionId, String chipId) {
        synchronized (mGetSessionStatusFnLock) {
            return nativeGetSessionState(sessionId, chipId);
        }
    }

    /**
     * Starts a UWB session.
     *
     * @param sessionId : Session ID for which ranging shall start
     * @param chipId    : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte startRanging(int sessionId, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeRangingStart(sessionId, chipId);
        }
    }

    /**
     * Stops the ongoing UWB session.
     *
     * @param sessionId : Stop the requested ranging session.
     * @param chipId    : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte stopRanging(int sessionId, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeRangingStop(sessionId, chipId);
        }
    }

    /**
     * set APP Configuration Parameters for the requested UWB session
     *
     * @param noOfParams        : The number (n) of APP Configuration Parameters
     * @param appConfigParamLen : The length of APP Configuration Parameters
     * @param appConfigParams   : APP Configuration Parameter
     * @param chipId            : Identifier of UWB chip for multi-HAL devices
     * @return : {@link UwbConfigStatusData} : Contains statuses for all cfg_id
     */
    public UwbConfigStatusData setAppConfigurations(int sessionId, int noOfParams,
            int appConfigParamLen, byte[] appConfigParams, String chipId) {
        synchronized (mSetAppConfigFnLock) {
            return nativeSetAppConfigurations(sessionId, noOfParams, appConfigParamLen,
                    appConfigParams, chipId);
        }
    }

    /**
     * Get APP Configuration Parameters for the requested UWB session
     *
     * @param noOfParams        : The number (n) of APP Configuration Parameters
     * @param appConfigParamLen : The length of APP Configuration Parameters
     * @param appConfigIds      : APP Configuration Parameter
     * @param chipId            : Identifier of UWB chip for multi-HAL devices
     * @return :  {@link UwbTlvData} : All tlvs that are to be decoded
     */
    public UwbTlvData getAppConfigurations(int sessionId, int noOfParams, int appConfigParamLen,
            byte[] appConfigIds, String chipId) {
        synchronized (mSetAppConfigFnLock) {
            return nativeGetAppConfigurations(sessionId, noOfParams, appConfigParamLen,
                    appConfigIds, chipId);
        }
    }

    /**
     * Get Core Capabilities information
     *
     * @param chipId : Identifier of UWB chip for multi-HAL devices
     * @return :  {@link UwbTlvData} : All tlvs that are to be decoded
     */
    public UwbTlvData getCapsInfo(String chipId) {
        synchronized (mGlobalStateFnLock) {
            return nativeGetCapsInfo(chipId);
        }
    }

    /**
     * Update Multicast list for the requested UWB session using V1 command.
     *
     * @param sessionId         : Session ID to which multicast list to be updated
     * @param action            : Update the multicast list by adding or removing
     *                          0x00 - Adding
     *                          0x01 - removing
     * @param noOfControlee     : The number(n) of Controlees
     * @param addresses         : address list of Controlees
     * @param subSessionIds     : Specific sub-session ID list of Controlees
     * @return : refer to SESSION_SET_APP_CONFIG_RSP
     * in the Table 16: Control messages to set Application configurations
     */
    public byte controllerMulticastListUpdateV1(int sessionId, int action, int noOfControlee,
            short[] addresses, int[] subSessionIds, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeControllerMulticastListUpdateV1(sessionId, (byte) action,
                    (byte) noOfControlee, addresses, subSessionIds, chipId);
        }
    }

    /**
     * Update Multicast list for the requested UWB session using V2 command.
     *
     * @param sessionId         : Session ID to which multicast list to be updated
     * @param action            : Update the multicast list by adding or removing
     *                          0x00 - Adding
     *                          0x01 - removing
     * @param noOfControlee     : The number(n) of Controlees
     * @param addresses         : address list of Controlees
     * @param subSessionIds     : Specific sub-session ID list of Controlees
     * @param messageControl    : Bitmap indicating the presence and length of Sub-session Key
     *                          Bits 0-2: Sub-Session key type
     *                          0 = Key length of 128 bits (16 bytes)
     *                          1 = Key length of 256 bits (32 bytes)
     *                          2-7 = RFU
     *                          Bit 3: Sub-Session key presence
     *                          0 = Sub-session Key is not configured by the Host
     *                          1 = Sub-session Key parameter is configured by the Host
     * @param subSessionKeyList : The list of Sub-session Keys
     * @return : refer to SESSION_SET_APP_CONFIG_RSP
     * in the Table 16: Control messages to set Application configurations
     */
    public byte controllerMulticastListUpdateV2(int sessionId, int action, int noOfControlee,
            short[] addresses, int[] subSessionIds, int messageControl,
            int[] subSessionKeyList, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeControllerMulticastListUpdateV2(sessionId, (byte) action,
                    (byte) noOfControlee, addresses, subSessionIds, messageControl,
                    subSessionKeyList, chipId);
        }
    }

    /**
     * Set country code.
     *
     * @param countryCode 2 char ISO country code
     */
    public byte setCountryCode(byte[] countryCode) {
        Log.i(TAG, "setCountryCode: " + new String(countryCode));

        synchronized (mGlobalStateFnLock) {
            for (String chipId : mUwbMultichipData.getChipIds()) {
                byte status = nativeSetCountryCode(countryCode, chipId);
                if (status != UwbUciConstants.STATUS_CODE_OK) {
                    return status;
                }
            }
            return UwbUciConstants.STATUS_CODE_OK;
        }
    }

    /**
     * Sets the log mode for the current and future UWB UCI messages.
     *
     * @param logModeStr is one of Disabled, Filtered, or Unfiltered (case insensitive).
     * @return true if the log mode is set successfully, false otherwise.
     */
    public boolean setLogMode(String logModeStr) {
        if (mUciLogModeStore.storeMode(logModeStr)) {
            return nativeSetLogMode(mUciLogModeStore.getMode());
        } else {
            return false;
        }
    }

    @NonNull
    public UwbVendorUciResponse sendRawVendorCmd(int gid, int oid, byte[] payload, String chipId) {
        synchronized (mGlobalStateFnLock) {
            return nativeSendRawVendorCmd(gid, oid, payload, chipId);
        }
    }

    /**
     * Receive payload data from a remote device in a UWB ranging session.
     */
    public void onDataReceived(
            long sessionID, int status, long sequenceNum, byte[] address,
            int sourceEndPoint, int destEndPoint, byte[] data) {
        Log.d(TAG, "onDataReceived ");
        mSessionListener.onDataReceived(
                sessionID, status, sequenceNum, address, sourceEndPoint, destEndPoint, data);
    }

    /**
     * Send payload data to a remote device in a UWB ranging session.
     */
    public byte sendData(
            int sessionId, byte[] address, byte destEndPoint, int sequenceNum, byte[] appData) {
        return nativeSendData(sessionId, address, destEndPoint, sequenceNum, appData);
    }

    private native byte nativeSendData(int sessionId, byte[] address,
            byte destEndPoint, int sequenceNum, byte[] appData);


    /**
     * Update active Ranging Rounds for DT Tag
     *
     * @param sessionId Session ID to which ranging round to be updated
     * @param noOfActiveRangingRounds new active ranging round
     * @param rangingRoundIndexes Indexes of ranging rounds
     * @return refer to SESSION_SET_APP_CONFIG_RSP
     * in the Table 16: Control messages to set Application configurations
     */
    public DtTagUpdateRangingRoundsStatus sessionUpdateActiveRoundsDtTag(int sessionId,
            int noOfActiveRangingRounds, byte[] rangingRoundIndexes, String chipId) {
        synchronized (mSessionFnLock) {
            return nativeSessionUpdateActiveRoundsDtTag(sessionId, noOfActiveRangingRounds,
                    rangingRoundIndexes, chipId);
        }
    }

    private native long nativeDispatcherNew(Object[] chipIds);

    private native void nativeDispatcherDestroy();

    private native boolean nativeInit();

    private native boolean nativeDoInitialize(String chipIds);

    private native boolean nativeDoDeinitialize(String chipId);

    private native long nativeGetTimestampResolutionNanos();

    private native UwbPowerStats nativeGetPowerStats(String chipId);

    private native int nativeGetMaxSessionNumber();

    private native byte nativeResetDevice(byte resetConfig, String chipId);

    private native byte nativeSessionInit(int sessionId, byte sessionType, String chipId);

    private native byte nativeSessionDeInit(int sessionId, String chipId);

    private native byte nativeGetSessionCount(String chipId);

    private native byte nativeRangingStart(int sessionId, String chipId);

    private native byte nativeRangingStop(int sessionId, String chipId);

    private native byte nativeGetSessionState(int sessionId, String chipId);

    private native UwbConfigStatusData nativeSetAppConfigurations(int sessionId, int noOfParams,
            int appConfigParamLen, byte[] appConfigParams, String chipId);

    private native UwbTlvData nativeGetAppConfigurations(int sessionId, int noOfParams,
            int appConfigParamLen, byte[] appConfigParams, String chipId);

    private native UwbTlvData nativeGetCapsInfo(String chipId);

    private native byte nativeControllerMulticastListUpdateV1(int sessionId, byte action,
            byte noOfControlee, short[] address, int[] subSessionId, String chipId);

    private native byte nativeControllerMulticastListUpdateV2(int sessionId, byte action,
            byte noOfControlee, short[] address, int[] subSessionId, int messageControl,
            int[] subSessionKeyList, String chipId);

    private native byte nativeSetCountryCode(byte[] countryCode, String chipId);

    private native boolean nativeSetLogMode(String logMode);

    private native UwbVendorUciResponse nativeSendRawVendorCmd(int gid, int oid, byte[] payload,
            String chipId);

    private native DtTagUpdateRangingRoundsStatus nativeSessionUpdateActiveRoundsDtTag(
            int sessionId, int noOfActiveRangingRounds, byte[] rangingRoundIndexes, String chipId);
}
