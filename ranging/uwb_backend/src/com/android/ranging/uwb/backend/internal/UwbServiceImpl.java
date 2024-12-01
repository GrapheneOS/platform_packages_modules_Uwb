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

package com.android.ranging.uwb.backend.internal;

import static android.content.pm.PackageManager.FEATURE_UWB;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_DISABLED;

import static com.android.ranging.uwb.backend.internal.RangingCapabilities.DEFAULT_SUPPORTED_RANGING_UPDATE_RATE;
import static com.android.ranging.uwb.backend.internal.RangingCapabilities.DEFAULT_SUPPORTED_SLOT_DURATIONS;
import static com.android.ranging.uwb.backend.internal.RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CONFIG_IDS;
import static com.android.ranging.uwb.backend.internal.Utils.CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR;
import static com.android.ranging.uwb.backend.internal.Utils.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
import static com.android.ranging.uwb.backend.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static com.android.ranging.uwb.backend.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST;
import static com.android.ranging.uwb.backend.internal.Utils.RANGE_DATA_NTF_ENABLE;
import static com.android.ranging.uwb.backend.internal.Utils.SUPPORTED_BPRF_PREAMBLE_INDEX;
import static com.android.ranging.uwb.backend.internal.Utils.SUPPORTED_HPRF_PREAMBLE_INDEX;
import static com.android.ranging.uwb.backend.internal.UwbAvailabilityCallback.REASON_UNKNOWN;

import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.multichip.ChipInfoParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Implements UWB session creation, adaptor state tracking and ranging capability reporting. */
public class UwbServiceImpl {

    private static final String FIRA_SPECIFICATION_BUNDLE_KEY = "fira";

    private int mAdapterState = STATE_DISABLED;
    private final boolean mHasUwbFeature;
    @Nullable
    private final UwbManager mUwbManager;
    @NonNull
    private final UwbFeatureFlags mUwbFeatureFlags;
    @NonNull
    private final UwbAvailabilityCallback mUwbAvailabilityCallback;

    public static final UwbFeatureFlags FEATURE_FLAGS = new UwbFeatureFlags.Builder().build();

    /** A serial thread used to handle session callback */
    private final ExecutorService mSerialExecutor = Executors.newSingleThreadExecutor();

    /** Adapter State callback used to update adapterState field */
    private final UwbManager.AdapterStateCallback mAdapterStateCallback;
    @UwbAvailabilityCallback.UwbStateChangeReason
    private int mLastStateChangeReason = REASON_UNKNOWN;

    public UwbServiceImpl(Context context, @NonNull UwbFeatureFlags uwbFeatureFlags,
            UwbAvailabilityCallback uwbAvailabilityCallback) {
        mHasUwbFeature = context.getPackageManager().hasSystemFeature(FEATURE_UWB);
        mUwbFeatureFlags = uwbFeatureFlags;
        mUwbAvailabilityCallback = uwbAvailabilityCallback;
        this.mAdapterStateCallback =
                (newState, reason) -> {
                    mLastStateChangeReason = Conversions.convertAdapterStateReason(reason);
                    // Send update only if old or new state is disabled, ignore if state
                    // changed from active
                    // to inactive and vice-versa.
                    int oldState = mAdapterState;
                    mAdapterState = newState;
                    if (newState == STATE_DISABLED || oldState == STATE_DISABLED) {
                        mSerialExecutor.execute(
                                () -> mUwbAvailabilityCallback.onUwbAvailabilityChanged(
                                        isAvailable(), mLastStateChangeReason));
                    }
                };
        if (mHasUwbFeature) {
            mUwbManager = context.getSystemService(UwbManager.class);
            requireNonNull(mUwbManager);
            // getAdapterState was added in Android T.
            if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
                mAdapterState = mUwbManager.getAdapterState();
            }
            mUwbManager.registerAdapterStateCallback(mSerialExecutor, mAdapterStateCallback);
        } else {
            mUwbManager = null;
        }
    }

    /** Gets a Ranging Controller session with given context. */
    public static RangingController getController(Context context, Executor executor) {
        UwbManager uwbManagerWithContext = context.getSystemService(UwbManager.class);
        return new RangingController(uwbManagerWithContext, executor,
                new OpAsyncCallbackRunner<>(), FEATURE_FLAGS);
    }

    /** Gets a Ranging Controlee session with given context. */
    public static RangingControlee getControlee(Context context, Executor executor) {
        UwbManager uwbManagerWithContext = context.getSystemService(UwbManager.class);
        return new RangingControlee(uwbManagerWithContext, executor,
                new OpAsyncCallbackRunner<>(), FEATURE_FLAGS);
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

    /** Gets the reason code for last state change. */
    public int getLastStateChangeReason() {
        return mLastStateChangeReason;
    }

    /** Gets ranging capabilities of the device. */
    @SuppressLint("NewApi")
    public RangingCapabilities getRangingCapabilities() {
        requireNonNull(mUwbManager);
        requireNonNull(mUwbFeatureFlags);

        PersistableBundle bundle = new PersistableBundle();
        if (!mUwbFeatureFlags.skipRangingCapabilitiesCheck()
                || VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            bundle = mUwbManager.getSpecificationInfo();
        }
        if (bundle.isEmpty()) {
            return new RangingCapabilities(
                    /* supportsDistance= */ true,
                    mUwbFeatureFlags.hasAzimuthSupport(),
                    mUwbFeatureFlags.hasElevationSupport(),
                    /* supportsRangingIntervalReconfigure */ false,
                    /* minRangingInterval= */ RangingCapabilities.FIRA_DEFAULT_RANGING_INTERVAL_MS,
                    new ArrayList<Integer>(RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CHANNEL),
                    new ArrayList<>(RANGE_DATA_NTF_ENABLE),
                    FIRA_DEFAULT_SUPPORTED_CONFIG_IDS,
                    DEFAULT_SUPPORTED_SLOT_DURATIONS,
                    DEFAULT_SUPPORTED_RANGING_UPDATE_RATE,
                    SUPPORTED_BPRF_PREAMBLE_INDEX,
                    /* hasBackgroundRangingSupport */ false);
        }

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
        List<Integer> supportedRangingUpdateRates = new ArrayList<>(
                DEFAULT_SUPPORTED_RANGING_UPDATE_RATE);
        if (minRangingInterval <= 120) {
            supportedRangingUpdateRates.add(Utils.FAST);
        }
        if (supportedChannels == null || supportedChannels.isEmpty()) {
            supportedChannels =
                    new ArrayList<>(RangingCapabilities.FIRA_DEFAULT_SUPPORTED_CHANNEL);
        }

        Set<Integer> supportedNtfConfigsSet = new TreeSet<>();
        for (FiraParams.RangeDataNtfConfigCapabilityFlag e :
                specificationParams.getRangeDataNtfConfigCapabilities()) {
            supportedNtfConfigsSet.add(Utils.convertFromFiraNtfConfig(e.ordinal()));
        }
        List<Integer> supportedNtfConfigs = new ArrayList<>(supportedNtfConfigsSet);

        List<Integer> supportedConfigIds = new ArrayList<>(FIRA_DEFAULT_SUPPORTED_CONFIG_IDS);
        EnumSet<FiraParams.StsCapabilityFlag> stsCapabilityFlags =
                specificationParams.getStsCapabilities();
        if (stsCapabilityFlags.contains(FiraParams.StsCapabilityFlag.HAS_PROVISIONED_STS_SUPPORT)) {
            supportedConfigIds.add(CONFIG_PROVISIONED_UNICAST_DS_TWR);
            supportedConfigIds.add(CONFIG_PROVISIONED_MULTICAST_DS_TWR);
        }
        if (stsCapabilityFlags.contains(FiraParams.StsCapabilityFlag
                .HAS_PROVISIONED_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT)) {
            supportedConfigIds.add(CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR);
        }
        if (minRangingInterval <= 96) {
            supportedConfigIds.add(CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST);
        }
        EnumSet<FiraParams.RangingRoundCapabilityFlag> rangingRoundCapabilityFlags =
                specificationParams.getRangingRoundCapabilities();
        EnumSet<FiraParams.PrfCapabilityFlag> prfModeCapabilityFlags =
                specificationParams.getPrfCapabilities();

        List<Integer> supportedPreambleIndexes = new ArrayList<>(SUPPORTED_BPRF_PREAMBLE_INDEX);
        if (prfModeCapabilityFlags.contains(FiraParams.PrfCapabilityFlag.HAS_HPRF_SUPPORT)) {
            supportedPreambleIndexes.addAll(SUPPORTED_HPRF_PREAMBLE_INDEX);
        }
        int minSlotDurationUs = specificationParams.getMinSlotDurationUs();
        List<Integer> supportedSlotDurations = new ArrayList<>(Arrays.asList(Utils.DURATION_2_MS));
        if (minSlotDurationUs <= 1000) {
            supportedSlotDurations.add(Utils.DURATION_1_MS);
        }

        return new RangingCapabilities(
                true,
                aoaCapabilityFlags.contains(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT),
                aoaCapabilityFlags.contains(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT),
                specificationParams.hasBlockStridingSupport(),
                minRangingInterval,
                ImmutableList.copyOf(supportedChannels),
                ImmutableList.copyOf(supportedNtfConfigs),
                ImmutableList.copyOf(supportedConfigIds),
                ImmutableList.copyOf(supportedSlotDurations),
                ImmutableList.copyOf(supportedRangingUpdateRates),
                ImmutableList.copyOf(supportedPreambleIndexes),
                specificationParams.hasBackgroundRangingSupport()
        );
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
