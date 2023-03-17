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

import android.content.Context;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.Nullable;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.multichip.ChipInfoParams;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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
            mAdapterState = mUwbManager.getAdapterState();
            mUwbManager.registerAdapterStateCallback(mSerialExecutor, mAdapterStateCallback);
        } else {
            mUwbManager = null;
        }
    }

    /** Gets a Ranging Controller session with given context. */
    public RangingController getController(Context context) {
        UwbManager uwbManagerWithContext = context.getSystemService(UwbManager.class);
        return new RangingController(
                uwbManagerWithContext, mSerialExecutor, new OpAsyncCallbackRunner<>());
    }

    /** Gets a Ranging Controlee session with given context. */
    public RangingControlee getControlee(Context context) {
        UwbManager uwbManagerWithContext = context.getSystemService(UwbManager.class);
        return new RangingControlee(
                uwbManagerWithContext, mSerialExecutor, new OpAsyncCallbackRunner<>());
    }

    /** Returns multi-chip information. */
    public List<ChipInfoParams> getChipInfos() {
        List<PersistableBundle> chipInfoBundles = mUwbManager.getChipInfos();
        List<ChipInfoParams> chipInfos = new ArrayList<>();
        for (PersistableBundle chipInfo : chipInfoBundles) {
            chipInfos.add(ChipInfoParams.fromBundle(chipInfo));
        }
        return chipInfos;
    }

    /** Gets the default chip of the system. */
    String getDefaultChipId() {
        return mUwbManager.getDefaultChipId();
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
        int minRangingInterval = specificationParams.getMinRangingInterval();
        EnumSet<FiraParams.AoaCapabilityFlag> aoaCapabilityFlags =
                specificationParams.getAoaCapabilities();
        List<Integer> supportedChannels = specificationParams.getSupportedChannels();
        if (minRangingInterval <= 0) {
            minRangingInterval = RangingCapabilities.FIRA_DEFAULT_RANGING_INTERVAL_MS;
        }
        if (supportedChannels == null || supportedChannels.isEmpty()) {
            supportedChannels =
                    new ArrayList<Integer>(RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CHANNEL);
        }
        List<Integer> supportedNtfConfigs = specificationParams.getRangeDataNtfConfigCapabilities()
                .stream()
                .map(Enum::ordinal)
                .map(Utils::convertFromFiraNtfConfig)
                .distinct()
                .collect(Collectors.toList());
        return new RangingCapabilities(
                true,
                aoaCapabilityFlags.contains(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT),
                aoaCapabilityFlags.contains(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT),
                minRangingInterval,
                supportedChannels,
                supportedNtfConfigs);
    }

    /**
     * Update the callback executor of the given ranging device.
     *
     * <p>If previous service is shut down, the ranging device may hold a stale serial executor.
     */
    public void updateRangingDevice(RangingDevice device) {
        device.setSystemCallbackExecutor(mSerialExecutor);
    }
}
