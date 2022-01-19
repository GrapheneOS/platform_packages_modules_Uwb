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
package com.android.uwb.jni;

import android.os.SystemProperties;
import android.util.Log;

import com.android.uwb.data.UwbMulticastListUpdateStatus;
import com.android.uwb.data.UwbRangingData;
import com.android.uwb.data.UwbUciConstants;
import com.android.uwb.info.UwbSpecificationInfo;

import java.util.List;

public class NativeUwbManager {
    private static final String TAG = NativeUwbManager.class.getSimpleName();

    public final Object mSessionFnLock = new Object();
    public final Object mSessionCountFnLock = new Object();
    public final Object mGetRangingCountFnLock = new Object();
    public final Object mGetSessionStatusFnLock = new Object();
    public final Object mSetAppConfigFnLock = new Object();
    protected INativeUwbManager.DeviceNotification mDeviceListener;
    protected INativeUwbManager.SessionNotification mSessionListener;
    private long mDispatcherPointer;

    public NativeUwbManager() {
        loadLibrary();
    }

    protected void loadLibrary() {
        // TODO(b/197341298): Remove this when rust native stack is ready.
        if (SystemProperties.getBoolean("persist.uwb.enable_uci_rust_stack", false)) {
            System.loadLibrary("uwb_uci_jni_rust");
        } else {
            System.loadLibrary("uwb_uci_jni");
        }
        nativeInit();
    }

    public void setDeviceListener(INativeUwbManager.DeviceNotification deviceListener) {
        mDeviceListener = deviceListener;
    }

    public void setSessionListener(INativeUwbManager.SessionNotification sessionListener) {
        mSessionListener = sessionListener;
    }

    public void onDeviceStatusNotificationReceived(int deviceState) {
        Log.d(TAG, "onDeviceStatusNotificationReceived(" + deviceState + ")");
        mDeviceListener.onDeviceStatusNotificationReceived(deviceState);
    }

    public void onCoreGenericErrorNotificationReceived(int status) {
        Log.d(TAG, "onCoreGenericErrorNotificationReceived(" + status + ")");
        mDeviceListener.onCoreGenericErrorNotificationReceived(status);
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
     * Enable UWB hardware.
     *
     * @return : If this returns true, UWB is on
     */
    public synchronized boolean doInitialize() {
        if (SystemProperties.getBoolean("persist.uwb.enable_uci_rust_stack", false)
                && this.mDispatcherPointer == 0L) {
            this.mDispatcherPointer = nativeDispatcherNew();
        }
        return nativeDoInitialize();
    }

    /**
     * Disable UWB hardware.
     *
     * @return : If this returns true, UWB is off
     */
    public synchronized boolean doDeinitialize() {
        boolean res = nativeDoDeinitialize();
        if (res && SystemProperties.getBoolean("persist.uwb.enable_uci_rust_stack", false)) {
            nativeDispatcherDestroy();
            this.mDispatcherPointer = 0L;
        }
        return res;
    }

    public synchronized long getTimestampResolutionNanos() {
        return 0L;
        /* TODO: Not Implemented in native stack
        return nativeGetTimestampResolutionNanos(); */
    }

    public UwbSpecificationInfo getSpecificationInfo() {
        return nativeGetSpecificationInfo();
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
     * Creates the new UWB session with parameter session ID and type of the session.
     *
     * @param sessionId   : Session ID is 4 Octets unique random number generated by application
     * @param sessionType : Type of session 0x00: Ranging session 0x01: Data transfer 0x02-0x9F: RFU
     *                    0xA0-0xCF: Reserved for Vendor Specific use case 0xD0: Device Test Mode
     *                    0xD1-0xDF: RFU 0xE0-0xFF: Vendor Specific use
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte initSession(int sessionId, byte sessionType) {
        synchronized (mSessionFnLock) {
            return nativeSessionInit(sessionId, sessionType);
        }
    }

    /**
     * De-initializes the session.
     *
     * @param sessionId : Session ID for which session to be de-initialized
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte deInitSession(int sessionId) {
        synchronized (mSessionFnLock) {
            return nativeSessionDeInit(sessionId);
        }
    }

    /**
     * reset the UWBs
     *
     * @param resetConfig : Reset config
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte resetDevice(byte resetConfig) {
        return nativeResetDevice(resetConfig);
    }

    /**
     * Retrieves number of UWB sessions in the UWBS.
     *
     * @return : Number of UWB sessions present in the UWBS.
     */
    public byte getSessionCount() {
        synchronized (mSessionCountFnLock) {
            return nativeGetSessionCount();
        }
    }

    /**
     * Queries the current state of the UWB session.
     *
     * @param sessionId : Session of the UWB session for which current session state to be queried
     * @return : {@link UwbUciConstants}  Session State
     */
    public byte getSessionState(int sessionId) {
        synchronized (mGetSessionStatusFnLock) {
            return nativeGetSessionState(sessionId);
        }
    }

    /**
     * Starts a UWB session.
     *
     * @param sessionId : Session ID for which ranging shall start
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte startRanging(int sessionId) {
        synchronized (mSessionFnLock) {
            return nativeRangingStart(sessionId);
        }
    }

    /**
     * Stops the ongoing UWB session.
     *
     * @param sessionId : Stop the requested ranging session.
     * @return : {@link UwbUciConstants}  Status code
     */
    public byte stopRanging(int sessionId) {
        synchronized (mSessionFnLock) {
            return nativeRangingStop(sessionId);
        }
    }

    /**
     * set APP Configuration Parameters for the requested UWB session
     *
     * @param noOfParams        : The number (n) of APP Configuration Parameters
     * @param appConfigParamLen : The length of APP Configuration Parameters
     * @param appConfigParams   : APP Configuration Parameter
     * @return : refer to SESSION_SET_APP_CONFIG_RSP in the Table 16: Control messages to set
     * Application configurations
     */
    public byte[] setAppConfigurations(int sessionId, int noOfParams, int appConfigParamLen,
            byte[] appConfigParams) {
        synchronized (mSetAppConfigFnLock) {
            return nativeSetAppConfigurations(sessionId, noOfParams, appConfigParamLen,
                    appConfigParams);
        }
    }

    /**
     * Get APP Configuration Parameters for the requested UWB session
     *
     * @param noOfParams        : The number (n) of APP Configuration Parameters
     * @param appConfigParamLen : The length of APP Configuration Parameters
     * @param appConfigIds      : APP Configuration Parameter
     * @return : refer to SESSION_GET_APP_CONFIG_RSP in the Table 16: Control messages to get
     * Application configurations
     */
    public byte[] getAppConfigurations(int sessionId, int noOfParams, int appConfigParamLen,
            byte[] appConfigIds) {
        synchronized (mSetAppConfigFnLock) {
            return nativeGetAppConfigurations(sessionId, noOfParams, appConfigParamLen,
                    appConfigIds);
        }
    }

    /**
     * Update Multicast list for the requested UWB session
     *
     * @param sessionId  : Session ID to which multicast list to be updated
     * @param action     : Update the multicast list by adding or removing
     *                     0x00 - Adding
     *                     0x01 - removing
     * @param noOfControlee : The number(n) of Controlees
     * @param address       : address list of Controlees
     * @param subSessionId : Specific sub-session ID list of Controlees
     * @return : refer to SESSION_SET_APP_CONFIG_RSP
     * in the Table 16: Control messages to set Application configurations
     */
    public byte controllerMulticastListUpdate(int sessionId, int action, int noOfControlee,
            byte[] address, int[]subSessionId) {
        /**
         * TODO:
         * 1. change address type short[] to byte[]
         * 2. call native function after jni function is implemented correctly
         */
        synchronized (mSessionFnLock) {
            return nativeControllerMulticastListUpdate(sessionId, (byte) action,
                    (byte) noOfControlee, address, subSessionId);
        }
    }

    /**
     * Set country code.
     *
     * @param countryCode 2 char ISO country code
     */
    public byte setCountryCode(byte[] countryCode) {
        synchronized (mSessionFnLock) {
            return nativeSetCountryCode(countryCode);
        }
    }

    /**
     * Returns a list of UWB chip identifiers.
     *
     * Callers can invoke methods on a specific UWB chip by passing its {@code chipId} to the
     * method.
     *
     * @return list of UWB chip identifiers for a multi-HAL system, or a list of a single chip
     * identifier for a single HAL system.
     */
    public List<String> getChipIds() {
        // TODO(b/206150133): Get list of chip ids from configuration file
        return List.of(getDefaultChipId());
    }

    /**
     * Returns the default UWB chip identifier.
     *
     * If callers do not pass a specific {@code chipId} to UWB methods, then the method will be
     * invoked on the default chip, which is determined at system initialization from a
     * configuration file.
     *
     * @return default UWB chip identifier for a multi-HAL system, or the identifier of the only UWB
     * chip in a single HAL system.
     */
    public String getDefaultChipId() {
        // TODO(b/206150133): Get list of chip ids from configuration file
        return "defaultChipId";
    }

    private native long nativeDispatcherNew();

    private native void nativeDispatcherDestroy();

    private native boolean nativeInit();

    private native boolean nativeDoInitialize();

    private native boolean nativeDoDeinitialize();

    private native long nativeGetTimestampResolutionNanos();

    private native UwbSpecificationInfo nativeGetSpecificationInfo();

    private native int nativeGetMaxSessionNumber();

    private native byte nativeResetDevice(byte resetConfig);

    private native byte nativeSessionInit(int sessionId, byte sessionType);

    private native byte nativeSessionDeInit(int sessionId);

    private native byte nativeGetSessionCount();

    private native byte nativeRangingStart(int sessionId);

    private native byte nativeRangingStop(int sessionId);

    private native byte nativeGetSessionState(int sessionId);

    private native byte[] nativeSetAppConfigurations(int sessionId, int noOfParams,
            int appConfigParamLen, byte[] appConfigParams);

    private native byte[] nativeGetAppConfigurations(int sessionId, int noOfParams,
            int appConfigParamLen, byte[] appConfigParams);

    private native byte nativeControllerMulticastListUpdate(int sessionId, byte action,
            byte noOfControlee, byte[] address, int[]subSessionId);

    private native byte nativeSetCountryCode(byte[] countryCode);
}
