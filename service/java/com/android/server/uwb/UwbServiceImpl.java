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
import android.app.admin.SecurityLog;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.uwb.IOnUwbActivityEnergyInfoListener;
import android.uwb.IUwbAdapter;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbAdfProvisionStateCallbacks;
import android.uwb.IUwbOemExtensionCallback;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbVendorUciCallback;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.uwb.data.UwbUciConstants;

import com.google.uwb.support.generic.GenericSpecificationParams;
import com.google.uwb.support.multichip.ChipInfoParams;
import com.google.uwb.support.profile.ServiceProfile;
import com.google.uwb.support.profile.UuidBundleWrapper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link android.uwb.IUwbAdapter} binder service.
 */
public class UwbServiceImpl extends IUwbAdapter.Stub {
    private static final String TAG = "UwbServiceImpl";

    private final Context mContext;
    private final UwbInjector mUwbInjector;
    private final UwbSettingsStore mUwbSettingsStore;
    private final UwbServiceCore mUwbServiceCore;

    private boolean mUwbUserRestricted;


    UwbServiceImpl(@NonNull Context context, @NonNull UwbInjector uwbInjector) {
        mContext = context;
        mUwbInjector = uwbInjector;
        mUwbSettingsStore = uwbInjector.getUwbSettingsStore();
        mUwbServiceCore = uwbInjector.getUwbServiceCore();
        registerAirplaneModeReceiver();
        mUwbUserRestricted = isUwbUserRestricted();
        registerUserRestrictionsReceiver();
    }

    /**
     * Initialize the stack after boot completed.
     */
    public void initialize() {
        mUwbSettingsStore.initialize();
        mUwbInjector.getMultichipData().initialize();
        mUwbInjector.getUwbCountryCode().initialize();
        mUwbInjector.getUciLogModeStore().initialize();
        // Initialize the UCI stack at bootup.
        mUwbServiceCore.setEnabled(isUwbEnabled());
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
        pw.println();
        mUwbInjector.getUwbMetrics().dump(fd, pw, args);
        pw.println();
        mUwbServiceCore.dump(fd, pw, args);
        pw.println();
        mUwbInjector.getUwbSessionManager().dump(fd, pw, args);
        pw.println();
        mUwbInjector.getUwbCountryCode().dump(fd, pw, args);
        pw.println();
        mUwbInjector.getUwbConfigStore().dump(fd, pw, args);
        pw.println();
        dumpPowerStats(fd, pw, args);
    }

    private void dumpPowerStats(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("---- PowerStats ----");
        try {
            PersistableBundle bundle = getSpecificationInfo(null);
            GenericSpecificationParams params = GenericSpecificationParams.fromBundle(bundle);
            if (params == null) {
                pw.println("Spec info is empty. Fail to get power stats.");
                return;
            }
            if (params.hasPowerStatsSupport()) {
                pw.println(mUwbInjector.getNativeUwbManager().getPowerStats(getDefaultChipId()));
            } else {
                pw.println("power stats query is not supported");
            }
        } catch (Exception e) {
            pw.println("Exception while getting power stats.");
            e.printStackTrace(pw);
        }
        pw.println("---- PowerStats ----");
    }

    private void enforceUwbPrivilegedPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.UWB_PRIVILEGED,
                "UwbService");
    }

    private void onUserRestrictionsChanged() {
        if (mUwbUserRestricted == isUwbUserRestricted()) {
            return;
        }

        Log.i(TAG, "Disallow UWB user restriction changed from " + mUwbUserRestricted + " to "
                + !mUwbUserRestricted + ".");
        mUwbUserRestricted = !mUwbUserRestricted;
        logSecurityUwbUserRestrictionChanged(mUwbUserRestricted);

        try {
            mUwbServiceCore.setEnabled(isUwbEnabled());
        } catch (Exception e) {
            Log.e(TAG, "Unable to set UWB Adapter state.", e);
        }
    }

    private void logSecurityUwbUserRestrictionChanged(boolean restricted) {
        if (restricted) {
            SecurityLog.writeEvent(SecurityLog.TAG_USER_RESTRICTION_ADDED);
        } else {
            SecurityLog.writeEvent(SecurityLog.TAG_USER_RESTRICTION_ADDED);
        }
    }

    @Override
    public void registerAdapterStateCallbacks(IUwbAdapterStateCallbacks adapterStateCallbacks)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.registerAdapterStateCallbacks(adapterStateCallbacks);
    }

    @Override
    public void registerVendorExtensionCallback(IUwbVendorUciCallback callbacks)
            throws RemoteException {
        Log.i(TAG, "Register the callback");
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.registerVendorExtensionCallback(callbacks);
    }

    @Override
    public void unregisterVendorExtensionCallback(IUwbVendorUciCallback callbacks)
            throws RemoteException {
        Log.i(TAG, "Unregister the callback");
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.unregisterVendorExtensionCallback(callbacks);
    }


    @Override
    public void unregisterAdapterStateCallbacks(IUwbAdapterStateCallbacks adapterStateCallbacks)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.unregisterAdapterStateCallbacks(adapterStateCallbacks);
    }

    @Override
    public void registerOemExtensionCallback(IUwbOemExtensionCallback callbacks)
            throws RemoteException {
        if (!SdkLevel.isAtLeastU()) {
            throw new UnsupportedOperationException();
        }
        Log.i(TAG, "Register Oem Extension callback");
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.registerOemExtensionCallback(callbacks);
    }

    @Override
    public void unregisterOemExtensionCallback(IUwbOemExtensionCallback callbacks)
            throws RemoteException {
        if (!SdkLevel.isAtLeastU()) {
            throw new UnsupportedOperationException();
        }
        Log.i(TAG, "Unregister Oem Extension callback");
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.unregisterOemExtensionCallback(callbacks);
    }

    @Override
    public long getTimestampResolutionNanos(String chipId) throws RemoteException {
        enforceUwbPrivilegedPermission();
        validateChipId(chipId);
        // TODO(/b/237601383): Determine whether getTimestampResolutionNanos should take a chipId
        // parameter
        return mUwbServiceCore.getTimestampResolutionNanos();
    }

    @Override
    public PersistableBundle getSpecificationInfo(String chipId) throws RemoteException {
        enforceUwbPrivilegedPermission();
        chipId = validateChipId(chipId);
        return mUwbServiceCore.getSpecificationInfo(chipId);
    }

    @Override
    public void openRanging(AttributionSource attributionSource,
            SessionHandle sessionHandle,
            IUwbRangingCallbacks rangingCallbacks,
            PersistableBundle parameters,
            String chipId) throws RemoteException {
        enforceUwbPrivilegedPermission();
        chipId = validateChipId(chipId);
        mUwbInjector.enforceUwbRangingPermissionForPreflight(attributionSource);
        mUwbServiceCore.openRanging(attributionSource,
                sessionHandle,
                rangingCallbacks,
                parameters,
                chipId);
    }

    @Override
    public void startRanging(SessionHandle sessionHandle, PersistableBundle parameters)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.startRanging(sessionHandle, parameters);
    }

    @Override
    public void reconfigureRanging(SessionHandle sessionHandle, PersistableBundle parameters)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.reconfigureRanging(sessionHandle, parameters);
    }

    @Override
    public void stopRanging(SessionHandle sessionHandle) throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.stopRanging(sessionHandle);
    }

    @Override
    public void closeRanging(SessionHandle sessionHandle) throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.closeRanging(sessionHandle);
    }

    @Override
    public synchronized int sendVendorUciMessage(int mt, int gid, int oid, byte[] payload)
            throws RemoteException {
        enforceUwbPrivilegedPermission();
        // TODO(b/237533396): Add a sendVendorUciMessage that takes a chipId parameter
        return mUwbServiceCore.sendVendorUciMessage(mt, gid, oid, payload, getDefaultChipId());
    }

    @Override
    public void addControlee(SessionHandle sessionHandle, PersistableBundle params) {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.addControlee(sessionHandle, params);
    }

    @Override
    public void removeControlee(SessionHandle sessionHandle, PersistableBundle params) {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.removeControlee(sessionHandle, params);
    }

    @Override
    public void pause(SessionHandle sessionHandle, PersistableBundle params) {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void resume(SessionHandle sessionHandle, PersistableBundle params) {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void sendData(SessionHandle sessionHandle, UwbAddress remoteDeviceAddress,
            PersistableBundle params, byte[] data) throws RemoteException {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.sendData(sessionHandle, remoteDeviceAddress, params, data);
    }

    @Override
    public void updateRangingRoundsDtTag(SessionHandle sessionHandle,
            PersistableBundle parameters) throws RemoteException {
        if (!SdkLevel.isAtLeastU()) {
            throw new UnsupportedOperationException();
        }
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.rangingRoundsUpdateDtTag(sessionHandle, parameters);
    }

    @Override
    public int queryMaxDataSizeBytes(SessionHandle sessionHandle) {
        if (!SdkLevel.isAtLeastU()) {
            throw new UnsupportedOperationException();
        }
        enforceUwbPrivilegedPermission();
        return mUwbServiceCore.queryMaxDataSizeBytes(sessionHandle);
    }

    @Override
    public synchronized int getAdapterState() throws RemoteException {
        return mUwbServiceCore.getAdapterState();
    }

    @Override
    public synchronized void setEnabled(boolean enabled) throws RemoteException {
        enforceUwbPrivilegedPermission();
        persistUwbToggleState(enabled);
        // Shell command from rooted shell, we allow UWB toggle on even if APM mode and
        // user restriction are on.
        if (Binder.getCallingUid() == Process.ROOT_UID) {
            mUwbServiceCore.setEnabled(isUwbToggleEnabled());
            return;
        }
        mUwbServiceCore.setEnabled(isUwbEnabled());
    }

    @Override
    public List<PersistableBundle> getChipInfos() {
        enforceUwbPrivilegedPermission();
        List<ChipInfoParams> chipInfoParamsList = mUwbInjector.getMultichipData().getChipInfos();
        List<PersistableBundle> chipInfos = new ArrayList<>();
        for (ChipInfoParams chipInfoParams : chipInfoParamsList) {
            chipInfos.add(chipInfoParams.toBundle());
        }
        return chipInfos;
    }

    @Override
    public List<String> getChipIds() {
        enforceUwbPrivilegedPermission();
        List<ChipInfoParams> chipInfoParamsList = mUwbInjector.getMultichipData().getChipInfos();
        List<String> chipIds = new ArrayList<>();
        for (ChipInfoParams chipInfoParams : chipInfoParamsList) {
            chipIds.add(chipInfoParams.getChipId());
        }
        return chipIds;
    }

    @Override
    public String getDefaultChipId() {
        enforceUwbPrivilegedPermission();
        return mUwbInjector.getMultichipData().getDefaultChipId();
    }

    @Override
    public PersistableBundle addServiceProfile(@NonNull PersistableBundle parameters) {
        enforceUwbPrivilegedPermission();
        ServiceProfile serviceProfile = ServiceProfile.fromBundle(parameters);
        Optional<UUID> serviceInstanceID = mUwbInjector
                .getProfileManager()
                .addServiceProfile(serviceProfile.getServiceID());
        return new UuidBundleWrapper.Builder()
                .setServiceInstanceID(serviceInstanceID)
                .build()
                .toBundle();
    }

    @Override
    public int removeServiceProfile(@NonNull PersistableBundle parameters) {
        enforceUwbPrivilegedPermission();
        UuidBundleWrapper uuidBundleWrapper = UuidBundleWrapper.fromBundle(parameters);
        if (uuidBundleWrapper.getServiceInstanceID().isPresent()) {
            return mUwbInjector
                    .getProfileManager()
                    .removeServiceProfile(uuidBundleWrapper.getServiceInstanceID().get());
        }
        return UwbUciConstants.STATUS_CODE_FAILED;
    }

    @Override
    public PersistableBundle getAllServiceProfiles() {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @NonNull
    @Override
    public PersistableBundle getAdfProvisioningAuthorities(@NonNull PersistableBundle parameters) {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @NonNull
    @Override
    public PersistableBundle getAdfCertificateAndInfo(@NonNull PersistableBundle parameters) {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void provisionProfileAdfByScript(@NonNull PersistableBundle serviceProfileBundle,
            @NonNull IUwbAdfProvisionStateCallbacks callback) {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public int removeProfileAdf(@NonNull PersistableBundle serviceProfileBundle) {
        enforceUwbPrivilegedPermission();
        // TODO(b/200678461): Implement this.
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {

        UwbShellCommand shellCommand = mUwbInjector.makeUwbShellCommand(this);
        return shellCommand.exec(this, in.getFileDescriptor(), out.getFileDescriptor(),
                err.getFileDescriptor(), args);
    }

    @Override
    public void updatePose(SessionHandle sessionHandle, PersistableBundle params) {
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.updatePose(sessionHandle, params);
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

    /** Returns true if UWB has user restriction set. */
    private boolean isUwbUserRestricted() {
        if (!SdkLevel.isAtLeastU()) {
            return false; // older platforms did not have a uwb user restriction.
        }

        final long ident = Binder.clearCallingIdentity();
        try {
            return mUwbInjector.getUserManager().getUserRestrictions().getBoolean(
                    UserManager.DISALLOW_ULTRA_WIDEBAND_RADIO);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /** Returns true if UWB is enabled - based on UWB, APM toggle and user restriction */
    private boolean isUwbEnabled() {
        return isUwbToggleEnabled() && !isAirplaneModeOn() && !isUwbUserRestricted();
    }

    private void registerAirplaneModeReceiver() {
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleAirplaneModeEvent();
            }
        }, new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED));
    }

    private void registerUserRestrictionsReceiver() {
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        onUserRestrictionsChanged();
                    }
                },
                new IntentFilter(UserManager.ACTION_USER_RESTRICTIONS_CHANGED)
        );
    }

    private void handleAirplaneModeEvent() {
        try {
            mUwbServiceCore.setEnabled(isUwbEnabled());
        } catch (Exception e) {
            Log.e(TAG, "Unable to set UWB Adapter state.", e);
        }
    }

    private String validateChipId(String chipId) {
        if (chipId == null || chipId.isEmpty()) {
            return getDefaultChipId();
        }

        if (!getChipIds().contains(chipId)) {
            throw new IllegalArgumentException("invalid chipId: " + chipId);
        }

        return chipId;
    }

    public void handleUserSwitch(int userId) {
        mUwbServiceCore.getHandler().post(() -> {
            Log.d(TAG, "Handle user switch " + userId);
            mUwbInjector.getUwbConfigStore().handleUserSwitch(userId);
        });
    }

    public void handleUserUnlock(int userId) {
        mUwbServiceCore.getHandler().post(() -> {
            Log.d(TAG, "Handle user unlock " + userId);
            mUwbInjector.getUwbConfigStore().handleUserUnlock(userId);
        });
    }

    @Override
    public synchronized void getUwbActivityEnergyInfoAsync(
            IOnUwbActivityEnergyInfoListener listener) throws RemoteException {
        Log.i(TAG, "getUwbActivityEnergyInfoAsync uid=" + Binder.getCallingUid());
        enforceUwbPrivilegedPermission();
        mUwbServiceCore.reportUwbActivityEnergyInfo(listener);
    }

}
