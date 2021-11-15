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

package com.android.uwb;

import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.StateChangeReason;
import android.uwb.UwbManager.AdapterStateCallback;

import com.android.server.uwb.UwbCountryCode;
import com.android.server.uwb.UwbMetrics;
import com.android.uwb.data.UwbUciConstants;
import com.android.uwb.info.UwbSpecificationInfo;
import com.android.uwb.jni.INativeUwbManager;
import com.android.uwb.jni.NativeUwbManager;

import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import java.util.concurrent.ConcurrentHashMap;

public class UwbService implements INativeUwbManager.DeviceNotification {
    private static final String TAG = "UwbService";

    private static final String SERVICE_NAME = "uwb";

    private static final int TASK_ENABLE = 1;
    private static final int TASK_DISABLE = 2;

    private static final int WATCHDOG_MS = 10000;
    private final PowerManager.WakeLock mUwbWakeLock;
    private final Context mContext;
    private final UwbAdapterService mUwbAdapterService;
    private final ConcurrentHashMap<Integer, AdapterInfo> mAdapterMap =
            new ConcurrentHashMap<Integer, AdapterInfo>();
    private final EnableDisableTask mEnableDisableTask;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_SHUTDOWN)) {
                Log.i(TAG, "Device shutdown");
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                if (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.Global.AIRPLANE_MODE_ON, 0) == 1) {
                    Log.i(TAG, "Airplane mode On");
                    mEnableDisableTask.execute(TASK_DISABLE);
                } else {
                    Log.i(TAG, "Airplane mode Off");
                    mEnableDisableTask.execute(TASK_ENABLE);
                }
            }
        }
    };
    private final UwbSessionManager mSessionManager;
    private final NativeUwbManager mNativeUwbManager;
    private final UwbMetrics mUwbMetrics;
    private final UwbCountryCode mUwbCountryCode;
    private UwbSpecificationInfo mUwbSpecificationInfo = null;
    private /* @UwbManager.AdapterStateCallback.State */ int mState;
    private @StateChangeReason int mLastStateChangedReason;

    public UwbService(Context uwbApplicationContext, NativeUwbManager nativeUwbManager,
            UwbMetrics uwbMetrics, UwbCountryCode uwbCountryCode) {
        mContext = uwbApplicationContext;

        Log.d(TAG, "Starting Uwb");

        mUwbAdapterService = new UwbAdapterService();
        // ServiceManager.addService(SERVICE_NAME, mUwbAdapterService);

        mUwbWakeLock = mContext.getSystemService(PowerManager.class).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "UwbService:mUwbWakeLock");

        mNativeUwbManager = nativeUwbManager;

        mNativeUwbManager.setDeviceListener(this);
        mUwbMetrics = uwbMetrics;
        mUwbCountryCode = uwbCountryCode;
        mSessionManager = new UwbSessionManager(mNativeUwbManager, mUwbMetrics);

        initIntentFilter();
        updateState(AdapterStateCallback.STATE_DISABLED, StateChangeReason.SYSTEM_BOOT);

        HandlerThread handlerThread = new HandlerThread("EnableDisableTask", Thread.MAX_PRIORITY);
        handlerThread.start();
        mEnableDisableTask = new EnableDisableTask(handlerThread.getLooper());

        mEnableDisableTask.execute(TASK_ENABLE);
    }

    // TODO(b/196225233): Remove this when qorvo stack is integrated.
    public IUwbAdapter.Stub getIUwbAdapter() {
        return mUwbAdapterService;
    }

    private void updateState(int state, int reason) {
        synchronized (UwbService.this) {
            mState = state;
            mLastStateChangedReason = reason;
        }
    }

    private boolean isUwbEnabled() {
        synchronized (UwbService.this) {
            return (mState == AdapterStateCallback.STATE_ENABLED_ACTIVE
                    || mState == AdapterStateCallback.STATE_ENABLED_INACTIVE);
        }
    }

    String getDeviceStateString(int state) {
        String ret = "";
        switch (state) {
            case UwbUciConstants.DEVICE_STATE_OFF:
                ret = "OFF";
                break;
            case UwbUciConstants.DEVICE_STATE_READY:
                ret = "READY";
                break;
            case UwbUciConstants.DEVICE_STATE_ACTIVE:
                ret = "ACTIVE";
                break;
            case UwbUciConstants.DEVICE_STATE_ERROR:
                ret = "ERROR";
                break;
        }
        return ret;
    }

    @Override
    public void onDeviceStatusNotificationReceived(int deviceState) {
        handleDeviceStatusNotification(deviceState);
    }

    void handleDeviceStatusNotification(int deviceState) {
        Log.i(TAG, "handleDeviceStatusNotification = " + getDeviceStateString(deviceState));
        int state = AdapterStateCallback.STATE_DISABLED;
        int reason = StateChangeReason.UNKNOWN;

        if (deviceState == UwbUciConstants.DEVICE_STATE_OFF) {
            state = AdapterStateCallback.STATE_DISABLED;
            reason = StateChangeReason.SYSTEM_POLICY;
        } else if (deviceState == UwbUciConstants.DEVICE_STATE_READY) {
            state = AdapterStateCallback.STATE_ENABLED_INACTIVE;
            reason = StateChangeReason.SYSTEM_POLICY;
        } else if (deviceState == UwbUciConstants.DEVICE_STATE_ACTIVE) {
            state = AdapterStateCallback.STATE_ENABLED_ACTIVE;
            reason = StateChangeReason.SESSION_STARTED;
        } else if (deviceState == UwbUciConstants.DEVICE_STATE_ERROR) {
            state = AdapterStateCallback.STATE_DISABLED;
            reason = StateChangeReason.UNKNOWN;
        }

        updateState(state, reason);

        for (AdapterInfo adapter : mAdapterMap.values()) {
            try {
                adapter.getAdapterStateCallbacks().onAdapterStateChanged(state, reason);
            } catch (RemoteException e) {
                Log.e(TAG, "onAdapterStateChanged is failed");
            }
        }
    }

    @Override
    public void onCoreGenericErrorNotificationReceived(int status) {
        Log.e(TAG, "onCoreGenericErrorNotificationReceived status = " + status);
    }

    private void initIntentFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        try {
            mContext.registerReceiverForAllUsers(mReceiver, filter, null, null);
        } catch (SecurityException e) {
            Log.e(TAG, "fail to register intents");
        }

    }

    public final class UwbAdapterService extends IUwbAdapter.Stub {
        @Override
        public void registerAdapterStateCallbacks(IUwbAdapterStateCallbacks adapterStateCallbacks)
                throws RemoteException {
            AdapterInfo adapter = new AdapterInfo(getCallingPid(), adapterStateCallbacks);
            mAdapterMap.put(getCallingPid(), adapter);
            adapter.getBinder().linkToDeath(adapter, 0);
            adapterStateCallbacks.onAdapterStateChanged(mState, mLastStateChangedReason);
        }

        @Override
        public void unregisterAdapterStateCallbacks(IUwbAdapterStateCallbacks callbacks)
                throws RemoteException {
            int pid = getCallingPid();
            AdapterInfo adapter = mAdapterMap.get(pid);
            adapter.getBinder().unlinkToDeath(adapter, 0);
            mAdapterMap.remove(pid);
        }

        @Override
        public PersistableBundle getSpecificationInfo() throws RemoteException {
            if (mUwbSpecificationInfo == null) {
                mUwbSpecificationInfo = mNativeUwbManager.getSpecificationInfo();
            }
            return mUwbSpecificationInfo.toBundle();
        }

        @Override
        public long getTimestampResolutionNanos() throws RemoteException {
            return mNativeUwbManager.getTimestampResolutionNanos();
        }

        @Override
        public void openRanging(
                AttributionSource attributionSource,
                SessionHandle sessionHandle,
                IUwbRangingCallbacks rangingCallbacks,
                PersistableBundle params) throws RemoteException {
            if (!isUwbEnabled()) {
                throw new RemoteException("Uwb is not enabled");
            }
            int sessionId = 0;
            if (FiraParams.isCorrectProtocol(params)) {
                FiraOpenSessionParams firaOpenSessionParams = FiraOpenSessionParams.fromBundle(
                        params);
                sessionId = firaOpenSessionParams.getSessionId();
                mSessionManager.initSession(sessionHandle, sessionId,
                        firaOpenSessionParams.getProtocolName(),
                        firaOpenSessionParams, rangingCallbacks);
            } else if (CccParams.isCorrectProtocol(params)) {
                CccOpenRangingParams cccOpenRangingParams = CccOpenRangingParams.fromBundle(params);
                sessionId = cccOpenRangingParams.getSessionId();
                mSessionManager.initSession(sessionHandle, sessionId,
                        cccOpenRangingParams.getProtocolName(),
                        cccOpenRangingParams, rangingCallbacks);
            } else {
                Log.e(TAG, "openRanging - Wrong parameters");
            }
        }

        @Override
        public void startRanging(SessionHandle sessionHandle, PersistableBundle parameters)
                throws RemoteException {
            if (!isUwbEnabled()) {
                throw new RemoteException("Uwb is not enabled");
            }
            mSessionManager.startRanging(sessionHandle, parameters);
            return;
        }

        @Override
        public void reconfigureRanging(SessionHandle sessionHandle, PersistableBundle params)
                throws RemoteException {
            if (!isUwbEnabled()) {
                Log.i(TAG, "UWB is not enabled");
                return;
            }
            mSessionManager.reconfigure(sessionHandle, params);
            return;
        }

        @Override
        public void stopRanging(SessionHandle sessionHandle) throws RemoteException {
            if (!isUwbEnabled()) {
                throw new RemoteException("Uwb is not enabled");
            }
            mSessionManager.stopRanging(sessionHandle);
            return;
        }

        @Override
        public void closeRanging(SessionHandle sessionHandle) throws RemoteException {
            if (!isUwbEnabled()) {
                throw new RemoteException("Uwb is not enabled");
            }
            mSessionManager.deInitSession(sessionHandle);
            return;
        }

        @Override
        public /* @UwbManager.AdapterStateCallback.State */ int getAdapterState()
                throws RemoteException {
            synchronized (UwbService.this) {
                return mState;
            }
        }

        @Override
        public synchronized void setEnabled(boolean enabled) throws RemoteException {
            int task = enabled ? TASK_ENABLE : TASK_DISABLE;

            if (enabled && isUwbEnabled()) {
                throw new RemoteException("Uwb is already enabled");
            } else if (!enabled && !isUwbEnabled()) {
                throw new RemoteException("Uwb is already disabled");
            }

            mEnableDisableTask.execute(task);
        }
    }

    private class EnableDisableTask extends Handler {

        EnableDisableTask(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            int type = msg.what;
            switch (type) {
                case TASK_ENABLE:
                    enableInternal();
                    break;

                case TASK_DISABLE:
                    mSessionManager.deinitAllSession();
                    disableInternal();
                    break;
                default:
                    Log.d(TAG, "EnableDisableTask : Undefined Task");
                    break;
            }
        }

        public void execute(int task) {
            Message msg = mEnableDisableTask.obtainMessage();
            msg.what = task;
            this.sendMessage(msg);
        }

        private void enableInternal() {
            if (isUwbEnabled()) {
                Log.i(TAG, "UWB service is already enabled");
                return;
            }
            try {
                WatchDogThread watchDog = new WatchDogThread("enableInternal", WATCHDOG_MS);
                watchDog.start();

                Log.i(TAG, "Initialization start ...");
                mUwbWakeLock.acquire();
                try {
                    if (!mNativeUwbManager.doInitialize()) {
                        Log.e(TAG, "Error enabling UWB");
                        updateState(AdapterStateCallback.STATE_DISABLED,
                                StateChangeReason.SYSTEM_POLICY);
                    } else {
                        Log.i(TAG, "Initialization success");
                        /* TODO : keep it until MW, FW fix b/196943897 */
                        handleDeviceStatusNotification(UwbUciConstants.DEVICE_STATE_READY);
                        // Set country code on every enable.
                        mUwbCountryCode.setCountryCode();
                    }
                } finally {
                    mUwbWakeLock.release();
                    watchDog.cancel();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void disableInternal() {
            if (!isUwbEnabled()) {
                Log.i(TAG, "UWB service is already disabled");
                return;
            }

            WatchDogThread watchDog = new WatchDogThread("disableInternal", WATCHDOG_MS);
            watchDog.start();

            try {
                updateState(AdapterStateCallback.STATE_DISABLED, StateChangeReason.SYSTEM_POLICY);
                Log.i(TAG, "Deinitialization start ...");
                mUwbWakeLock.acquire();

                if (!mNativeUwbManager.doDeinitialize()) {
                    Log.w(TAG, "Error disabling UWB");
                } else {
                    Log.i(TAG, "Deinitialization success");
                    /* UWBS_STATUS_OFF is not the valid state. so handle device state directly */
                    handleDeviceStatusNotification(UwbUciConstants.DEVICE_STATE_OFF);
                }
            } finally {
                mUwbWakeLock.release();
                watchDog.cancel();
            }
        }

        public class WatchDogThread extends Thread {
            final Object mCancelWaiter = new Object();
            final int mTimeout;
            boolean mCanceled = false;

            WatchDogThread(String threadName, int timeout) {
                super(threadName);

                mTimeout = timeout;
            }

            @Override
            public void run() {
                try {
                    synchronized (mCancelWaiter) {
                        mCancelWaiter.wait(mTimeout);
                        if (mCanceled) {
                            return;
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    interrupt();
                }

                if (mUwbWakeLock.isHeld()) {
                    Log.e(TAG, "Release mUwbWakeLock before aborting.");
                    mUwbWakeLock.release();
                }
            }

            public synchronized void cancel() {
                synchronized (mCancelWaiter) {
                    mCanceled = true;
                    mCancelWaiter.notify();
                }
            }
        }
    }

    class AdapterInfo implements IBinder.DeathRecipient {
        private final IBinder mIBinder;
        private IUwbAdapterStateCallbacks mAdapterStateCallbacks;
        private int mPid;

        AdapterInfo(int pid, IUwbAdapterStateCallbacks adapterStateCallbacks) {
            mIBinder = adapterStateCallbacks.asBinder();
            mAdapterStateCallbacks = adapterStateCallbacks;
            mPid = pid;
        }

        public IUwbAdapterStateCallbacks getAdapterStateCallbacks() {
            return mAdapterStateCallbacks;
        }

        public IBinder getBinder() {
            return mIBinder;
        }

        @Override
        public void binderDied() {
            mIBinder.unlinkToDeath(this, 0);
            mAdapterMap.remove(mPid);
        }
    }
}
