/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging;

import static android.Manifest.permission.RANGING;
import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.permission.PermissionManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.bluetooth.BluetoothManager;
import android.content.AttributionSource;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;
import com.android.server.ranging.blerssi.BleRssiAdapter;
import com.android.server.ranging.blerssi.BleRssiCapabilitiesAdapter;
import com.android.server.ranging.cs.CsAdapter;
import com.android.server.ranging.cs.CsCapabilitiesAdapter;
import com.android.server.ranging.oob.OobController;
import com.android.server.ranging.rtt.RttAdapter;
import com.android.server.ranging.rtt.RttCapabilitiesAdapter;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.uwb.UwbAdapter;
import com.android.server.ranging.uwb.UwbCapabilitiesAdapter;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class RangingInjector {

    private static final String TAG = "RangingInjector";

    private static final int APP_INFO_FLAGS_SYSTEM_APP =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

    private final Context mContext;
    private final RangingServiceManager mRangingServiceManager;
    private final OobController mOobController;

    private final CapabilitiesProvider mCapabilitiesProvider;
    private final PermissionManager mPermissionManager;

    private final Looper mLooper;

    private final Handler mAlarmHandler;
    private final DeviceConfigFacade mDeviceConfigFacade;

    @SuppressLint("StaticFieldLeak")
    private static RangingInjector sInstance;

    public RangingInjector(@NonNull Context context) {
        HandlerThread rangingHandlerThread = new HandlerThread("RangingServiceHandler");
        rangingHandlerThread.start();
        mLooper = rangingHandlerThread.getLooper();
        mContext = context;
        mCapabilitiesProvider = new CapabilitiesProvider(this);
        mRangingServiceManager = new RangingServiceManager(this,
                mContext.getSystemService(ActivityManager.class),
                mLooper);
        mOobController = new OobController(this);
        mPermissionManager = context.getSystemService(PermissionManager.class);
        mAlarmHandler = new Handler(mLooper);
        mDeviceConfigFacade = new DeviceConfigFacade(new Handler(mLooper), mContext);
        sInstance = this;
    }

     public static RangingInjector getInstance() {
        return Objects.requireNonNull(sInstance);
    }

    @VisibleForTesting
    public  static void setInstance(RangingInjector rangingInjector) {
        sInstance = rangingInjector;
    }

    public Context getContext() {
        return mContext;
    }

    public CapabilitiesProvider getCapabilitiesProvider() {
        return mCapabilitiesProvider;
    }

    public RangingServiceManager getRangingServiceManager() {
        return mRangingServiceManager;
    }

    public OobController getOobController() {
        return mOobController;
    }

    public Handler getAlarmHandler() {
        return mAlarmHandler;
    }

    public DeviceConfigFacade getDeviceConfigFacade() {
        return mDeviceConfigFacade;
    }

    /**
     * Create a new adapter for a technology.
     */
    public @NonNull RangingAdapter createAdapter(
            @NonNull AttributionSource attributionSource,
            @NonNull RangingSessionConfig.TechnologyConfig config,
            @NonNull ListeningExecutorService executor
    ) {
        switch (config.getTechnology()) {
            case UWB:
                return new UwbAdapter(
                        mContext, this, attributionSource, executor, config.getDeviceRole());
            case CS:
                return new CsAdapter(mContext, this);
            case RTT:
                return new RttAdapter(mContext, this, executor, config.getDeviceRole());
            case RSSI:
                return new BleRssiAdapter(mContext, this);
            default:
                throw new IllegalArgumentException(
                        "Adapter does not exist for technology " + config.getTechnology());
        }
    }

    public @NonNull CapabilitiesAdapter createCapabilitiesAdapter(
            @NonNull RangingTechnology technology,
            @NonNull CapabilitiesProvider.TechnologyAvailabilityListener listener
    ) {
        switch (technology) {
            case UWB:
                return new UwbCapabilitiesAdapter(mContext, listener);
            case CS:
                return new CsCapabilitiesAdapter(mContext, listener);
            case RTT:
                return new RttCapabilitiesAdapter(mContext, listener);
            case RSSI:
                return new BleRssiCapabilitiesAdapter(mContext, listener);
            default:
                throw new IllegalArgumentException(
                        "CapabilitiesAdapter does not exist for technology " + technology);
        }
    }

    public void enforceRangingPermissionForPreflight(
            @NonNull AttributionSource attributionSource) {
        if (!attributionSource.checkCallingUid()) {
            throw new SecurityException("Invalid attribution source " + attributionSource
                    + ", callingUid: " + Binder.getCallingUid());
        }
        int permissionCheckResult = mPermissionManager.checkPermissionForPreflight(
                RANGING, attributionSource);
        if (permissionCheckResult != PERMISSION_GRANTED) {
            throw new SecurityException("Caller does not hold RANGING permission");
        }
    }

    public boolean checkUwbRangingPermissionForStartDataDelivery(
            @NonNull AttributionSource attributionSource, @NonNull String message) {
        int permissionCheckResult = mPermissionManager.checkPermissionForStartDataDelivery(
                RANGING, attributionSource, message);
        return permissionCheckResult == PERMISSION_GRANTED;
    }

    /** Helper method to check if the app is a system app. */
    public boolean isSystemApp(int uid, @NonNull String packageName) {
        try {
            ApplicationInfo info = createPackageContextAsUser(uid)
                    .getPackageManager()
                    .getApplicationInfo(packageName, 0);
            return (info.flags & APP_INFO_FLAGS_SYSTEM_APP) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            // In case of exception, assume unknown app (more strict checking)
            // Note: This case will never happen since checkPackage is
            // called to verify validity before checking App's version.
            Log.e(TAG, "Failed to get the app info", e);
        }
        return false;
    }

    /**
     * Helper method creating a context based on the app's uid (to deal with multi user scenarios)
     */
    @Nullable
    private Context createPackageContextAsUser(int uid) {
        Context userContext;
        try {
            userContext = mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unknown package name");
            return null;
        }
        if (userContext == null) {
            Log.e(TAG, "Unable to retrieve user context for " + uid);
            return null;
        }
        return userContext;
    }

    @Nullable
    public AttributionSource getAnyNonPrivilegedAppInAttributionSource(AttributionSource source) {
        // Iterate attribution source chain to ensure that there is no non-fg 3p app in the
        // request.
        AttributionSource attributionSource = source;
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

    /** Whether the uid is signed with the same key as the platform. */
    public boolean isAppSignedWithPlatformKey(int uid) {
        return mContext.getPackageManager().checkSignatures(uid, Process.SYSTEM_UID)
                == PackageManager.SIGNATURE_MATCH;
    }

    /** Helper method to check if the app is from foreground app/service. */
    public static boolean isForegroundAppOrServiceImportance(int importance) {
        return importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
    }

    /** Helper method to check if the app or service is no longer running. */
    public static boolean isNonExistentAppOrService(int importance) {
        return importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
    }

    public boolean isPrivilegedApp(int uid, String packageName) {
        return isSystemApp(uid, packageName) || isAppSignedWithPlatformKey(uid);
    }

    /** Helper method to check if the app is from foreground app/service. */
    public boolean isForegroundAppOrService(int uid, @NonNull String packageName) {
        long identity = Binder.clearCallingIdentity();
        try {
            return isForegroundAppOrServiceImportance(getPackageImportance(uid, packageName));
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to retrieve the app importance", e);
            return false;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /** Helper method to retrieve app importance. */
    private int getPackageImportance(int uid, @NonNull String packageName) {
        if (sOverridePackageImportance.containsKey(packageName)) {
            Log.w(TAG, "Overriding package importance for testing");
            return sOverridePackageImportance.get(packageName);
        }
        try {
            return createPackageContextAsUser(uid)
                    .getSystemService(ActivityManager.class)
                    .getPackageImportance(packageName);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to retrieve the app importance", e);
            return ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE;
        }
    }

    private static Map<String, Integer> sOverridePackageImportance = new HashMap();

    // Use this if we have adb shell command support
    public void setOverridePackageImportance(String packageName, int importance) {
        sOverridePackageImportance.put(packageName, importance);
    }
    public void resetOverridePackageImportance(String packageName) {
        sOverridePackageImportance.remove(packageName);
    }

    /**
     * Valid Bluetooth hardware addresses must be upper case, in big endian byte order, and in a
     * format such as "00:11:22:33:AA:BB".
     */
    public boolean isRemoteDeviceBluetoothBonded(String btAddress) {
        long identity = Binder.clearCallingIdentity();
        try {
            return mContext.getSystemService(BluetoothManager.class)
                    .getAdapter()
                    .getRemoteDevice(btAddress)
                    .getBondState() == BOND_BONDED;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean isRangingTechnologyEnabled(RangingTechnology rangingTechnology) {
        return Arrays.asList(getDeviceConfigFacade().getTechnologyPreferenceList()).contains(
                rangingTechnology.toString()
        );
    }
}
