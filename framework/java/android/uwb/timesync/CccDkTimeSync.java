/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.uwb.timesync;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.Hide;
import android.annotation.NonNull;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.permission.PermissionManager;
import android.util.Log;

import com.android.ranging.flags.Flags;

import java.util.concurrent.Executor;

/**
 * Class for Framework-Vendor Time Sync process.
 *
 * @hide
 */
@Hide
@FlaggedApi(com.android.ranging.flags.Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class CccDkTimeSync {
    private static final String TAG = "CccDkTimeSync";
    //TODO (b/467707737) maybe use int instead of byte?
    @NonNull
    public static final Version VERSION = new Version((byte) 1, (byte) 1);
    @NonNull
    public static final Version VERSION_UNSUPPORTED = new Version((byte) 0, (byte) 0);

    private final Context mContext;
    private ICccDkTimeSync mService;
    private final PermissionManager mPermissionManager;

    public interface ConnectionCallback {
        void onConnected();

        void onDisconnected();
    }

    public CccDkTimeSync(
            @NonNull Context context,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull ConnectionCallback callback) {

        if (executor == null || callback == null) {
            throw new NullPointerException("Arguments must not be null");
        }
        mPermissionManager = context.getSystemService(PermissionManager.class);
        mContext = context;
        Log.i(TAG, "Constructor CccDkTimeSync");

        ServiceConnection connection =
                new ServiceConnection() {
                    public synchronized void onServiceConnected(
                            ComponentName className, IBinder service) {
                        mService = ICccDkTimeSync.Stub.asInterface(service);
                        final long token = Binder.clearCallingIdentity();
                        try {
                            executor.execute(() -> callback.onConnected());
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                        Log.i(TAG, "Service onServiceConnected");
                    }

                    public void onServiceDisconnected(ComponentName className) {
                        mService = null;
                        final long token = Binder.clearCallingIdentity();
                        try {
                            executor.execute(() -> callback.onDisconnected());
                        } finally {
                            Binder.restoreCallingIdentity(token);
                        }
                        Log.i(TAG, "Service onServiceDisconnected");
                    }
                };

        Intent intent = new Intent(ICccDkTimeSync.class.getName());
        ResolveInfo resolveInfo =
                context.getPackageManager()
                        .resolveService(intent, PackageManager.MATCH_SYSTEM_ONLY);
        intent.setPackage(resolveInfo.serviceInfo.packageName);
        boolean bindingSuccessful =
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (bindingSuccessful) {
            Log.i(TAG, "bindService successful");
        } else {
            Log.e(TAG, "bindService failed");
        }
    }

    public CccDkTimeSync(@NonNull Context context, @NonNull ICccDkTimeSync service) {
        mContext = context;
        mService = service;
        mPermissionManager = context.getSystemService(PermissionManager.class);
    }

    private static void enforceUwbPrivilegedPermission(Context context) {
        context.enforceCallingOrSelfPermission(android.Manifest.permission.UWB_PRIVILEGED,
                "UwbService");
    }

    public interface VersionListener {
        /**
         * Callback for receiving API version.
         *
         * @param version API version supported by the secure element. A value of
         * {@link VERSION_UNSUPPORTED} denotes no supported version between supported version range
         * of the framework.
         */
        void onVersion(@NonNull Version version);
    }

    private static class VersionListenerWrapper extends IVersionListener.Stub {
        private final Context mContext;
        private final VersionListener mListener;

        VersionListenerWrapper(Context context, VersionListener listener) {
            mContext = context;
            mListener = listener;
        }

        @Override
        @PermissionManuallyEnforced
        public void onVersion(Version version) {
            enforceUwbPrivilegedPermission(mContext);
            mListener.onVersion(version);
        }
    }

    /**
     * API version getter.
     *
     * <p>Used to negotiate compatible API version supported by both the Framework and Secure
     * Element. The minimum and maximum versions supported by the Framework are provided as input,
     * and a compatible version supported by the Secure Element is returned.
     *
     * @param versionMin Minimum version supported by Framework.
     * @param versionMax Maximum version supported by Framework.
     * @param listener An instance of the VersionListener.
     *
     */
    public void getApiVersion(
            @NonNull Version versionMin,
            @NonNull Version versionMax,
            @NonNull VersionListener listener)
            throws RemoteException {
        if (mService == null) {
            throw new IllegalStateException("service not connected to system");
        }
        try {
            mService.getApiVersion(versionMin,
                    versionMax,
                    new VersionListenerWrapper(mContext, listener));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Direction of the LMP event */
    public enum Direction {
        RX,
        TX,
    }

    /**
     * LMP event id to be monitored CONNECT_IND indicator for initiating connection, timestamp will
     * be at the anchor point LL_PHY_UPDATE_IND indicator for PHY update
     */
    public enum BleLmpEvent {
        CONNECT_IND,
        LL_PHY_UPDATE_IND,
    }

    public interface BleLmpEventListener {
        /**
         * Callback when receiving BLE LMP event timestamps.
         *
         * @param address Remote bluetooth address that invoked LMP event
         * @param timestamp Timestamp when the BLE LMP event invoked
         * @param direction Direction of the invoked BLE LMP event
         * @param event BLE LMP event id that Bluetooth chip invoked
         * @param eventCounter counter incremented by one for each new connection event
         */
        void onTimestamp(
                @NonNull byte[] address,
                @NonNull BleTimestamp timestamp,
                @NonNull Direction direction,
                @NonNull BleLmpEvent event,
                int eventCounter);
    }

    public interface EventCallback extends BleLmpEventListener {
        /** Callback indicates that event registration success. */
        void onRegisterSuccess();

        /** Callback indicates that event registration failure. */
        void onRegisterFailure();
    }

    private static class BleLmpEventListenerWrapper extends IBleLmpEventListener.Stub {
        private final Context mContext;
        private final BleLmpEventListener mListener;

        private final PermissionManager mPermissionManager;

        BleLmpEventListenerWrapper(Context context, BleLmpEventListener listener) {
            mContext = context;
            mListener = listener;
            mPermissionManager = context.getSystemService(PermissionManager.class);
        }

        @Override
        @PermissionManuallyEnforced
        public void onTimestamp(
                byte[] address,
                BleTimestamp timestamp,
                int direction,
                int event,
                int eventCounter) {
            enforceUwbPrivilegedPermission(mContext);
            int permissionCheckResult = mPermissionManager.checkPermissionForStartDataDelivery(
                    BLUETOOTH_CONNECT, mContext.getAttributionSource(), "BLE Timestamp");
            if (permissionCheckResult != PERMISSION_GRANTED) {
                Log.e(TAG, "Not delivering BLE timestamp because of permission denial");
                return;
            }

            mListener.onTimestamp(
                    address,
                    timestamp,
                    Direction.values()[direction],
                    BleLmpEvent.values()[event],
                    eventCounter);
        }
    }

    private static class EventCallbackWrapper extends IEventCallback.Stub {
        private final BleLmpEventListenerWrapper mListenerWrapper;
        private final EventCallback mCallback;
        private final Context mContext;

        EventCallbackWrapper(Context context, EventCallback callback) {
            mCallback = callback;
            mContext = context;
            mListenerWrapper = new BleLmpEventListenerWrapper(context, callback);
        }

        @Override
        @PermissionManuallyEnforced
        public void onRegisterSuccess() {
            enforceUwbPrivilegedPermission(mContext);
            mCallback.onRegisterSuccess();
        }

        @Override
        @PermissionManuallyEnforced
        public void onRegisterFailure() {
            enforceUwbPrivilegedPermission(mContext);
            mCallback.onRegisterFailure();
        }

        @Override
        @PermissionManuallyEnforced
        public void onTimestamp(
                byte[] address,
                BleTimestamp timestamp,
                int direction,
                int event,
                int eventCounter) {
            enforceUwbPrivilegedPermission(mContext);
            mListenerWrapper.onTimestamp(address, timestamp, direction, event, eventCounter);
        }
    }

    private void checkBluetoothConnectPermissionForPreFlight() {
        AttributionSource attributionSource = mContext.getAttributionSource();

        if (!attributionSource.checkCallingUid()) {
            throw new SecurityException("Invalid attribution source " + attributionSource);
        }

        int permissionCheckResult = mPermissionManager.checkPermissionForPreflight(
                BLUETOOTH_CONNECT, attributionSource);
        if (permissionCheckResult != PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold BLUETOOTH_CONNECT permission");
        }
    }

    /**
     * API to monitor Bluetooth and system timestamp for given Bluetooth device when Bluetooth
     * controller send/receive given LMP events.
     *
     * @param address Bluetooth address to use for monitoring timestamp.
     * @param listener An instance of the BleLmpEventListener
     */
    public void registerBleLmpEventListener(
            @NonNull byte[] address,
            @NonNull BleLmpEventListener listener)
            throws RemoteException {
        checkBluetoothConnectPermissionForPreFlight();

        if (mService == null) {
            throw new IllegalStateException("service not connected to system");
        }
        mService.registerBleLmpEventListener(
                address, new BleLmpEventListenerWrapper(mContext, listener));
    }

    /**
     * API to monitor Bluetooth and system timestamp for given Bluetooth device when Bluetooth
     * controller send/receive given LMP events.
     *
     * <p>Supported in Version 1.1 and later.
     *
     * @param address Bluetooth address to use for monitoring timestamp.
     * @param callback An instance of the {@link EventCallback}
     */
    @RequiresPermission(BLUETOOTH_CONNECT)
    public void registerEventCallback(@NonNull byte[] address, @NonNull EventCallback callback)
            throws RemoteException {
        checkBluetoothConnectPermissionForPreFlight();

        if (mService == null) {
            throw new IllegalStateException("service not connected to system");
        }
        mService.registerEventCallback(address, new EventCallbackWrapper(mContext, callback));
    }

    /**
     * API to stop monitoring given Bluetooth device.
     *
     * @param address Bluetooth device to stop monitoring.
     */
    @RequiresPermission(BLUETOOTH_CONNECT)
    public void unregisterBleLmpEventListener(@NonNull byte[] address) throws RemoteException {
        unregisterEventCallback(address);
    }

    /**
     * API to stop monitoring given Bluetooth device.
     *
     * <p>Supported in Version 1.1 and later.
     *
     * @param address Bluetooth device to stop monitoring.
     */
    @RequiresPermission(BLUETOOTH_CONNECT)
    public void unregisterEventCallback(@NonNull byte[] address) throws RemoteException {
        checkBluetoothConnectPermissionForPreFlight();

        if (mService == null) {
            throw new IllegalStateException("service not connected to system");
        }
        mService.unregisterEventCallback(address);
    }
}
