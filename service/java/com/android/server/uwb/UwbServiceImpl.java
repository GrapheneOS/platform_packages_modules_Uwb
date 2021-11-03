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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbAdapter2;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbRangingCallbacks2;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.SessionHandle;

import com.android.internal.annotations.GuardedBy;
import com.android.uwb.UwbService;
import com.android.uwb.jni.NativeUwbManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Implementation of {@link android.uwb.IUwbAdapter2} binder service.
 */
public class UwbServiceImpl extends IUwbAdapter2.Stub implements IBinder.DeathRecipient{
    private static final String TAG = "UwbServiceImpl";

    private final Context mContext;
    private final UwbInjector mUwbInjector;
    private final UwbSettingsStore mUwbSettingsStore;
    private final UwbMetrics mUwbMetrics;
    /**
     * Map for storing the callbacks wrapper for each session.
     */
    @GuardedBy("mCallbacksMap")
    private final Map<SessionHandle, UwbRangingCallbacksWrapper> mCallbacksMap = new ArrayMap<>();

    /**
     * Used for caching the vendor implementation of {@link IUwbAdapter} interface.
     */
    private IUwbAdapter mVendorUwbAdapter;

    /**
     * Wrapper for callback registered with vendor service. This wrapper is needed for performing
     * permission check before sending the callback to the external app.
     *
     * Access to these callbacks are synchronized.
     */
    private class UwbRangingCallbacksWrapper extends IUwbRangingCallbacks.Stub
            implements IBinder.DeathRecipient {
        private final AttributionSource mAttributionSource;
        private final SessionHandle mSessionHandle;
        private final IUwbRangingCallbacks2 mExternalCb;
        private boolean mIsValid;

        UwbRangingCallbacksWrapper(@NonNull AttributionSource attributionSource,
                @NonNull SessionHandle sessionHandle,
                @NonNull IUwbRangingCallbacks2 externalCb) {
            mAttributionSource = attributionSource;
            mSessionHandle = sessionHandle;
            mExternalCb = externalCb;
            mIsValid = true;

            // Link to death for external callback.
            linkToDeath();
        }

        private void linkToDeath() {
            IBinder binder = mExternalCb.asBinder();
            try {
                binder.linkToDeath(this, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to link to client death event.", e);
            }
        }

        private void removeClientAndUnlinkToDeath() {
            // Remove from the map.
            synchronized (mCallbacksMap) {
                mCallbacksMap.remove(mSessionHandle);
            }
            IBinder binder = mExternalCb.asBinder();
            binder.unlinkToDeath(this, 0);
            mIsValid = false;
        }


        @Override
        public synchronized void onRangingOpened(SessionHandle sessionHandle)
                throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingOpened(sessionHandle);
        }

        @Override
        public synchronized void onRangingOpenFailed(SessionHandle sessionHandle,
                int reason, PersistableBundle parameters) throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingOpenFailed(sessionHandle, reason, parameters);
        }

        @Override
        public synchronized void onRangingStarted(SessionHandle sessionHandle,
                PersistableBundle parameters)
                throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingStarted(sessionHandle, parameters);
        }

        @Override
        public synchronized void onRangingStartFailed(SessionHandle sessionHandle,
                int reason, PersistableBundle parameters) throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingStartFailed(sessionHandle, reason, parameters);
        }

        @Override
        public synchronized void onRangingReconfigured(SessionHandle sessionHandle,
                PersistableBundle parameters)
                throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingReconfigured(sessionHandle, parameters);
        }

        @Override
        public synchronized void onRangingReconfigureFailed(SessionHandle sessionHandle,
                int reason, PersistableBundle parameters) throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingReconfigureFailed(sessionHandle, reason, parameters);
        }

        @Override
        public synchronized void onRangingStopped(SessionHandle sessionHandle, int reason,
                PersistableBundle parameters)
                throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingStopped(sessionHandle, reason, parameters);
        }

        @Override
        public synchronized void onRangingStopFailed(SessionHandle sessionHandle, int reason,
                PersistableBundle parameters) throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingStopFailed(sessionHandle, reason, parameters);
        }

        @Override
        public synchronized void onRangingClosed(SessionHandle sessionHandle, int reason,
                PersistableBundle parameters) throws RemoteException {
            if (!mIsValid) return;
            mExternalCb.onRangingClosed(sessionHandle, reason, parameters);
            removeClientAndUnlinkToDeath();
        }

        @Override
        public synchronized void onRangingResult(SessionHandle sessionHandle,
                RangingReport rangingReport)
                throws RemoteException {
            if (!mIsValid) return;
            final long ident = Binder.clearCallingIdentity();
            try {
                boolean permissionGranted = mUwbInjector.checkUwbRangingPermissionForDataDelivery(
                        mAttributionSource, "uwb ranging result");
                if (!permissionGranted) {
                    Log.e(TAG, "Not delivering ranging result because of permission denial"
                            + mSessionHandle);
                    return;
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
            mExternalCb.onRangingResult(sessionHandle, rangingReport);
        }

        @Override
        public synchronized void binderDied() {
            if (!mIsValid) return;
            Log.i(TAG, "Client died: ending session: " + mSessionHandle);
            try {
                removeClientAndUnlinkToDeath();
                stopRanging(mSessionHandle);
                closeRanging(mSessionHandle);
            } catch (RemoteException e) {
                Log.e(TAG, "Remote exception while handling client death", e);
            }
        }
    }

    private void linkToVendorServiceDeath() {
        IBinder binder = mVendorUwbAdapter.asBinder();
        try {
            binder.linkToDeath(this, 0);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to link to vendor service death event.", e);
        }
    }

    @Override
    public void binderDied() {
        Log.i(TAG, "Vendor service died: sending session close callbacks");
        synchronized (mCallbacksMap) {
            for (Map.Entry<SessionHandle, UwbRangingCallbacksWrapper> e
                    : mCallbacksMap.entrySet()) {
                try {
                    e.getValue().mExternalCb.onRangingClosed(
                            e.getKey(), RangingSession.Callback.REASON_UNKNOWN,
                            new PersistableBundle());
                } catch (RemoteException ex) {
                    Log.e(TAG, "Failed to send session close callback " + e.getKey(), ex);
                }
            }
            // Clear all sessions.
            mCallbacksMap.clear();
        }
        mVendorUwbAdapter = null;
    }

    private synchronized IUwbAdapter getVendorUwbAdapter()
            throws IllegalStateException, RemoteException {
        if (mVendorUwbAdapter != null) return mVendorUwbAdapter;
        // TODO(b/196225233): Remove this when qorvo stack is integrated.
        if (mUwbInjector.isUciStackEnabled()) {
            Log.i(TAG, "Using the UCI stack");
            mVendorUwbAdapter = new UwbService(mContext, new NativeUwbManager(), mUwbMetrics,
                    mUwbInjector).getIUwbAdapter();
        } else {
            Log.i(TAG, "Using the legacy stack");
            mVendorUwbAdapter = mUwbInjector.getVendorService();
            if (mVendorUwbAdapter == null) {
                throw new IllegalStateException("No vendor service found!");
            }
            Log.i(TAG, "Retrieved vendor service");
            linkToVendorServiceDeath();
        }
        // TODO(b/196225233): Remove this when the AOSP -> vendor bridge is removed.
        getVendorUwbAdapter().setEnabled(isUwbEnabled());
        return mVendorUwbAdapter;
    }

    UwbServiceImpl(@NonNull Context context, @NonNull UwbInjector uwbInjector) {
        mContext = context;
        mUwbInjector = uwbInjector;
        mUwbSettingsStore = uwbInjector.getUwbSettingsStore();
        mUwbMetrics = new UwbMetrics(uwbInjector);
        registerAirplaneModeReceiver();
    }

    /**
     * Initialize the stack after boot completed.
     */
    public void initialize() {
        mUwbSettingsStore.initialize();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump UwbService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        mUwbSettingsStore.dump(fd, pw, args);
        mUwbMetrics.dump(fd, pw, args);
    }

    private void enforceUwbPrivilegedPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.UWB_PRIVILEGED,
                "UwbService");
    }

    @Override
    public void registerAdapterStateCallbacks(IUwbAdapterStateCallbacks adapterStateCallbacks)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        getVendorUwbAdapter().registerAdapterStateCallbacks(adapterStateCallbacks);
    }

    @Override
    public void unregisterAdapterStateCallbacks(IUwbAdapterStateCallbacks adapterStateCallbacks)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        getVendorUwbAdapter().unregisterAdapterStateCallbacks(adapterStateCallbacks);
    }

    @Override
    public long getTimestampResolutionNanos() throws RemoteException {
        enforceUwbPrivilegedPermission();
        return getVendorUwbAdapter().getTimestampResolutionNanos();
    }

    @Override
    public PersistableBundle getSpecificationInfo() throws RemoteException {
        enforceUwbPrivilegedPermission();
        return getVendorUwbAdapter().getSpecificationInfo();
    }

    @Override
    public void openRanging(AttributionSource attributionSource,
            SessionHandle sessionHandle, IUwbRangingCallbacks2 rangingCallbacks,
            PersistableBundle parameters) throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbInjector.enforceUwbRangingPermissionForPreflight(attributionSource);

        UwbRangingCallbacksWrapper wrapperCb =
                new UwbRangingCallbacksWrapper(attributionSource, sessionHandle, rangingCallbacks);
        synchronized (mCallbacksMap) {
            mCallbacksMap.put(sessionHandle, wrapperCb);
        }
        getVendorUwbAdapter().openRanging(attributionSource, sessionHandle, wrapperCb, parameters);
    }

    @Override
    public void startRanging(SessionHandle sessionHandle, PersistableBundle parameters)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        getVendorUwbAdapter().startRanging(sessionHandle, parameters);
    }

    @Override
    public void reconfigureRanging(SessionHandle sessionHandle, PersistableBundle parameters)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        getVendorUwbAdapter().reconfigureRanging(sessionHandle, parameters);
    }

    @Override
    public void stopRanging(SessionHandle sessionHandle) throws RemoteException {
        enforceUwbPrivilegedPermission();
        getVendorUwbAdapter().stopRanging(sessionHandle);
    }

    @Override
    public void closeRanging(SessionHandle sessionHandle) throws RemoteException {
        enforceUwbPrivilegedPermission();
        getVendorUwbAdapter().closeRanging(sessionHandle);
    }

    @Override
    public synchronized int getAdapterState() throws RemoteException {
        return getVendorUwbAdapter().getAdapterState();
    }

    @Override
    public synchronized void setEnabled(boolean enabled) throws RemoteException {
        enforceUwbPrivilegedPermission();
        persistUwbToggleState(enabled);
        getVendorUwbAdapter().setEnabled(isUwbEnabled());
    }

    private void persistUwbToggleState(boolean enabled) {
        mUwbSettingsStore.put(UwbSettingsStore.SETTINGS_TOGGLE_STATE, enabled);
    }

    private boolean isUwbToggleEnabled() {
        return mUwbSettingsStore.get(UwbSettingsStore.SETTINGS_TOGGLE_STATE);
    }

    /** Returns true if airplane mode is turned on. */
    private boolean isAirplaneModeOn() {
        return mUwbInjector.getSettingsInt(
                Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
    }

    /** Returns true if UWB is enabled - based on UWB and APM toggle */
    private boolean isUwbEnabled() {
        return isUwbToggleEnabled() && !isAirplaneModeOn();
    }

    private void registerAirplaneModeReceiver() {
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleAirplaneModeEvent();
            }
        }, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
    }

    private void handleAirplaneModeEvent() {
        try {
            getVendorUwbAdapter().setEnabled(isUwbEnabled());
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to set UWB Adapter state.", e);
        }
    }
}
