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

package androidx.core.uwb.backend.impl.internal;

import static android.content.pm.PackageManager.FEATURE_UWB;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_DISABLED;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.content.Context;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.core.uwb.backend.RangingCapabilities;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Implements UWB session creation, adaptor state tracking and ranging capability reporting. */
public class UwbServiceImpl {

    private static final String FIRA_SPECIFICATION_BUNDLE_KEY = "fira";

    private int mAdapterState = STATE_DISABLED;
    private final boolean mHasUwbFeature;
    @Nullable private final UwbManager mUwbManager;

    /** Adapter State callback used to update adapterState field */
    private final UwbManager.AdapterStateCallback mAdapterStateCallback =
            (state, reason) -> mAdapterState = state;

    /** A serial thread used to handle session callback */
    private final ExecutorService mSerialExecutor = Executors.newSingleThreadExecutor();

    public UwbServiceImpl(Context context) {
        mHasUwbFeature = context.getPackageManager().hasSystemFeature(FEATURE_UWB);
        if (mHasUwbFeature) {
            mUwbManager = context.getSystemService(UwbManager.class);
            requireNonNull(mUwbManager);
            mUwbManager.registerAdapterStateCallback(mSerialExecutor, mAdapterStateCallback);
        } else {
            mUwbManager = null;
        }
    }

    /** Gets a Ranging Controller session with given context. */
    public RangingController getController(Context context) {
        UwbManager uwbManagerWithContext = context.getSystemService(UwbManager.class);
        return new RangingController(uwbManagerWithContext, mSerialExecutor);
    }

    /** Gets a Ranging Controle session with given context. */
    public RangingControlee getControlee(Context context) {
        UwbManager uwbManagerWithContext = context.getSystemService(UwbManager.class);
        return new RangingControlee(uwbManagerWithContext, mSerialExecutor);
    }

    /**
     * Cleans up any resource such as threads, registered listeners, receivers or any cached data,
     * called when the service destroyed.
     */
    public void shutdown() {
        mSerialExecutor.shutdown();
        if (mUwbManager != null) {
            mUwbManager.unregisterAdapterStateCallback(mAdapterStateCallback);
        }
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
}
