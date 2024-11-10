/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.ranging.uwb;

import android.content.Context;
import android.content.pm.PackageManager;
import android.ranging.RangingManager.RangingTechnologyAvailability;
import android.ranging.uwb.UwbRangingCapabilities;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.UwbAvailabilityCallback;
import com.android.ranging.uwb.backend.internal.UwbServiceImpl;
import com.android.server.ranging.CapabilitiesProvider.AvailabilityCallback;
import com.android.server.ranging.CapabilitiesProvider.CapabilitiesAdapter;

public class UwbCapabilitiesAdapter extends CapabilitiesAdapter {
    private static final String TAG = UwbCapabilitiesAdapter.class.getSimpleName();

    private final Context mContext;
    /** Null if UWB is not available on this device */
    private final UwbServiceImpl mUwbService;

    /** @return true if UWB is supported in the provided context, false otherwise */
    public static boolean isSupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_UWB);
    }

    public UwbCapabilitiesAdapter(Context context) {
        mContext = context;
        if (isSupported(mContext)) {
            mUwbService = new UwbServiceImpl(
                    context,
                    UwbServiceImpl.FEATURE_FLAGS,
                    new AvailabilityListener());
        } else {
            mUwbService = null;
        }
    }

    @Override
    public @RangingTechnologyAvailability int getAvailability() {
        if (mUwbService == null) {
            return RangingTechnologyAvailability.NOT_SUPPORTED;
        } else if (mUwbService.isAvailable()) {
            return RangingTechnologyAvailability.ENABLED;
        } else {
            return RangingTechnologyAvailability.DISABLED_USER;
        }
    }

    private static UwbRangingCapabilities convertCapabilities(
            com.android.ranging.uwb.backend.internal.RangingCapabilities capabilities
    ) {
        return new UwbRangingCapabilities.Builder()
                .setSupportsDistance(capabilities.supportsDistance())
                .setSupportsAzimuthalAngle(
                        capabilities.supportsAzimuthalAngle())
                .setSupportsElevationAngle(
                        capabilities.supportsElevationAngle())
                .setSupportsRangingIntervalReconfigure(
                        capabilities.supportsRangingIntervalReconfigure())
                .setMinRangingInterval(
                        capabilities.getMinRangingInterval())
                .setSupportedChannels(
                        capabilities.getSupportedChannels())
                .setSupportedNtfConfigs(
                        capabilities.getSupportedNtfConfigs())
                .setSupportedConfigIds(
                        capabilities.getSupportedConfigIds())
                .setSupportedSlotDurations(
                        capabilities.getSupportedSlotDurations())
                .setSupportedRangingUpdateRates(
                        capabilities.getSupportedRangingUpdateRates())
                .setHasBackgroundRangingSupport(
                        capabilities.hasBackgroundRangingSupport())
                .build();
    }

    @Override
    public @Nullable UwbRangingCapabilities getCapabilities() {
        if (getAvailability() == RangingTechnologyAvailability.ENABLED) {
            try {
                return convertCapabilities(mUwbService.getRangingCapabilities());
            } catch (IllegalStateException e) {
                Log.w(TAG, "Failed to get capabilities from UWB backend " + e);
            }
        }
        return null;
    }

    private class AvailabilityListener implements UwbAvailabilityCallback {

        public static @AvailabilityCallback.AvailabilityChangedReason int convertReason(
                @UwbStateChangeReason int reason
        ) {
            switch (reason) {
                case REASON_SYSTEM_POLICY:
                case REASON_COUNTRY_CODE_ERROR:
                    return AvailabilityCallback.AvailabilityChangedReason.SYSTEM_POLICY;
                default:
                    return AvailabilityCallback.AvailabilityChangedReason.UNKNOWN;

            }
        }

        @Override
        public void onUwbAvailabilityChanged(
                boolean isUwbAvailable, @UwbStateChangeReason int reason
        ) {
            AvailabilityCallback callback = getAvailabilityCallback();
            if (callback == null) return;

            if (reason == REASON_COUNTRY_CODE_ERROR && !isUwbAvailable) {
                callback.onAvailabilityChange(
                        RangingTechnologyAvailability.DISABLED_REGULATORY,
                        convertReason(reason));
            } else {
                callback.onAvailabilityChange(
                        isUwbAvailable
                                ? RangingTechnologyAvailability.ENABLED
                                : RangingTechnologyAvailability.DISABLED_USER,
                        convertReason(reason));
            }
        }
    }
}
