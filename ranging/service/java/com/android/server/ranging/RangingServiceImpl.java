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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.NonNull;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;
import android.ranging.IRangingAdapter;
import android.ranging.IRangingCallbacks;
import android.ranging.IRangingCapabilitiesCallback;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionHandle;
import android.ranging.oob.IOobSendDataListener;
import android.ranging.oob.OobHandle;
import android.ranging.oob.OobResponderRangingParams;
import android.ranging.raw.RawResponderRangingParams;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class RangingServiceImpl extends IRangingAdapter.Stub {

    private static final String TAG = "RangingServiceImpl";
    private final RangingInjector mRangingInjector;
    private final Context mContext;

    RangingServiceImpl(@NonNull Context context, @NonNull RangingInjector rangingInjector) {
        mContext = context;
        mRangingInjector = rangingInjector;
    }


    @Override
    public void registerCapabilitiesCallback(IRangingCapabilitiesCallback callback)
            throws RemoteException {
        mRangingInjector.getRangingServiceManager().registerCapabilitiesCallback(callback);
    }

    @Override
    public void unregisterCapabilitiesCallback(IRangingCapabilitiesCallback callback)
            throws RemoteException {
        mRangingInjector.getRangingServiceManager().unregisterCapabilitiesCallback(callback);
    }

    @Override
    public void startRanging(AttributionSource attributionSource, SessionHandle sessionHandle,
            RangingPreference rangingPreference, IRangingCallbacks callbacks) {
        mRangingInjector.enforceRangingPermissionForPreflight(attributionSource);
        mRangingInjector.getRangingServiceManager().startRanging(attributionSource, sessionHandle,
                rangingPreference, callbacks);
    }

    @Override
    public void reconfigureRangingInterval(SessionHandle sessionHandle, int intervalSkipCount) {
        enforceRangingPermission();
        mRangingInjector.getRangingServiceManager().reconfigureInterval(sessionHandle,
                intervalSkipCount);
    }

    @Override
    public void addRawDevice(SessionHandle sessionHandle, RawResponderRangingParams rangingParams) {
        enforceRangingPermission();
        mRangingInjector.getRangingServiceManager().addRawPeer(sessionHandle, rangingParams);
    }

    @Override
    public void addOobDevice(SessionHandle sessionHandle, OobResponderRangingParams rangingParams) {
        enforceRangingPermission();
        throw new IllegalArgumentException("Dynamic addition of oob peer not supported yet");
    }

    @Override
    public void removeDevice(SessionHandle sessionHandle, RangingDevice rangingDevice) {
        enforceRangingPermission();
        mRangingInjector.getRangingServiceManager().removePeer(sessionHandle, rangingDevice);
    }

    @Override
    public void stopRanging(SessionHandle sessionHandle) {
        enforceRangingPermission();
        mRangingInjector.getRangingServiceManager().stopRanging(sessionHandle);
    }

    @Override
    public void oobDataReceived(OobHandle oobHandle, byte[] data) {
        mRangingInjector.getRangingServiceManager().oobDataReceived(oobHandle, data);
    }

    @Override
    public void deviceOobDisconnected(OobHandle oobHandle) {
        mRangingInjector.getRangingServiceManager().deviceOobDisconnected(oobHandle);
    }

    @Override
    public void deviceOobReconnected(OobHandle oobHandle) {
        mRangingInjector.getRangingServiceManager().deviceOobReconnected(oobHandle);
    }

    @Override
    public void deviceOobClosed(OobHandle oobHandle) {
        mRangingInjector.getRangingServiceManager().deviceOobClosed(oobHandle);
    }

    @Override
    public void registerOobSendDataListener(IOobSendDataListener oobSendDataListener) {
        mRangingInjector.getRangingServiceManager().registerOobSendDataListener(
                oobSendDataListener);
    }

    private void enforceRangingPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.RANGING,
                "RangingService");
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump RangingService from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid());
            return;
        }
        mRangingInjector.getRangingServiceManager().dump(fd, pw, args);
    }
}
