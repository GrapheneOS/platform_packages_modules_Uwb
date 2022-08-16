/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.core.uwb.backend.impl;

import static android.content.pm.PackageManager.FEATURE_UWB;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_DISABLED;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.core.uwb.backend.IUwb;
import androidx.core.uwb.backend.IUwbClient;
import androidx.core.uwb.backend.RangingCapabilities;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.util.EnumSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Uwb service entry point of the backend. */
public class UwbService extends Service {

    private static final String FIRA_SPECIFICATION_BUNDLE_KEY = "fira";

    private int mAdapterState = STATE_DISABLED;
    private final boolean mHasUwbFeature;
    @Nullable private UwbManager mUwbManager;

    /** Adapter State callback used to update adapterState field */
    private final UwbManager.AdapterStateCallback mAdapterStateCallback =
            (state, reason) -> mAdapterState = state;

    /** A serial thread used to handle session callback */
    private final Executor mSerialExecutor = Executors.newSingleThreadExecutor();

    public UwbService() {
        mHasUwbFeature = getPackageManager().hasSystemFeature(FEATURE_UWB);
    }

    /** True if UWB is available. */
    public boolean isAvailable() {
        return mHasUwbFeature && mAdapterState != STATE_DISABLED;
    }

    /** Gets ranging capabilities of the device. */
    public RangingCapabilities getRangingCapabilities() {
        requireNonNull(mUwbManager);
        PersistableBundle bundle = mUwbManager.getSpecificationInfo();
        if (bundle.keySet().contains(FIRA_SPECIFICATION_BUNDLE_KEY)) {
            bundle = requireNonNull(bundle.getPersistableBundle(FIRA_SPECIFICATION_BUNDLE_KEY));
        }
        FiraSpecificationParams specificationParams = FiraSpecificationParams.fromBundle(bundle);
        EnumSet<FiraParams.AoaCapabilityFlag> aoaCapabilityFlags =
                specificationParams.getAoaCapabilities();
        RangingCapabilities capabilities = new RangingCapabilities();
        capabilities.supportsDistance = true;
        capabilities.supportsAzimuthalAngle =
                aoaCapabilityFlags.contains(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
        capabilities.supportsElevationAngle =
                aoaCapabilityFlags.contains(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
        return capabilities;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (mHasUwbFeature) {
            mUwbManager = getSystemService(UwbManager.class);
            requireNonNull(mUwbManager);
            mUwbManager.registerAdapterStateCallback(mSerialExecutor, mAdapterStateCallback);
        } else {
            mUwbManager = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mHasUwbFeature) {
            mUwbManager.unregisterAdapterStateCallback(mAdapterStateCallback);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the interface
        return mBinder;
    }

    private final IUwb.Stub mBinder =
            new IUwb.Stub() {
                @Override
                public IUwbClient getControleeClient() {
                    // TODO (b/234033640): Implement this. How do we reuse gmscore code here?
                    return null;
                }

                @Override
                public IUwbClient getControllerClient() {
                    // TODO (b/234033640): Implement this. How do we reuse gmscore code here?
                    return null;
                }

                @Override
                public int getInterfaceVersion() {
                    return this.VERSION;
                }

                @Override
                public String getInterfaceHash() {
                    return this.HASH;
                }
            };
}
