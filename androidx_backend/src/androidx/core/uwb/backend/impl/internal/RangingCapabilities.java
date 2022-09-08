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

import androidx.annotation.IntRange;

import java.util.ArrayList;
import java.util.List;

/** Describes UWB ranging capabilities for the current device. */
public class RangingCapabilities {
    /** Default minimum ranging interval if the system API doesn't provide it. */
    public static final int FIRA_DEFAULT_RANGING_INTERVAL_MS = 200;
    /** Default supported channel if the system API doesn't provide it. */
    public static final int FIRA_DEFAULT_SUPPORTED_CHANNEL = 9;

    private final boolean mSupportsDistance;
    private final boolean mSupportsAzimuthalAngle;
    private final boolean mSupportsElevationAngle;
    private final int mMinRangingInterval;
    private final List<Integer> mSupportedChannels;

    public RangingCapabilities(
            boolean supportsDistance,
            boolean supportsAzimuthalAngle,
            boolean supportsElevationAngle) {
        this(
                supportsDistance,
                supportsAzimuthalAngle,
                supportsElevationAngle,
                FIRA_DEFAULT_RANGING_INTERVAL_MS,
                new ArrayList<Integer>(FIRA_DEFAULT_SUPPORTED_CHANNEL));
    }

    public RangingCapabilities(
            boolean supportsDistance,
            boolean supportsAzimuthalAngle,
            boolean supportsElevationAngle,
            int minRangingInterval,
            List<Integer> supportedChannels) {
        this.mSupportsDistance = supportsDistance;
        this.mSupportsAzimuthalAngle = supportsAzimuthalAngle;
        this.mSupportsElevationAngle = supportsElevationAngle;
        this.mMinRangingInterval = minRangingInterval;
        this.mSupportedChannels = supportedChannels;
    }

    /** Whether distance ranging is supported. */
    public boolean supportsDistance() {
        return mSupportsDistance;
    }

    /** Whether azimuthal angle of arrival is supported. */
    public boolean supportsAzimuthalAngle() {
        return mSupportsAzimuthalAngle;
    }

    /** Whether elevation angle of arrival is supported. */
    public boolean supportsElevationAngle() {
        return mSupportsElevationAngle;
    }

    /**
     * Gets the minimum supported ranging interval in milliseconds.
     *
     * @hide
     */
    @IntRange(from = 0)
    public int getMinRangingInterval() {
        return mMinRangingInterval;
    }

    /**
     * Gets the supported channel number.
     *
     * @hide
     */
    public List<Integer> getSupportedChannels() {
        return mSupportedChannels;
    }
}
