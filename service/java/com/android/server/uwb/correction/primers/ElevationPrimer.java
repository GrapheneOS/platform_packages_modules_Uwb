/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.server.uwb.correction.primers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.pose.IPoseSource;
import com.android.server.uwb.correction.pose.IPoseSource.Capabilities;

/**
 * Applies a default pose-based elevation to a UWB reading. A basic "assumption" about what the
 * elevation might be helps improve the quality of pose-based azimuth compensations, and may
 * provide a more understandable UWB location guess to the user.
 * Recommended for hardware that does not support elevation. This should execute before the
 * AoAPrimer in the primer execution order.
 * This will replace any existing elevation value, as it assumes that the hardware's elevation is
 * invalid or zero.
 */
public class ElevationPrimer implements IPrimer {
    /**
     * Applies a default pose-based elevation to a UWB reading that doesn't have one.
     *
     * @param input     The original UWB reading.
     * @param prediction A prediction of where the signal probably came from.
     * @param poseSource A pose source that may indicate phone orientation.
     * @return A replacement value for the UWB vector that has been corrected for the situation.
     */
    @Override
    public SphericalVector.Sparse prime(
            @NonNull SphericalVector.Sparse input,
            @Nullable SphericalVector prediction,
            @Nullable IPoseSource poseSource) {
        SphericalVector.Sparse position = input;
        if (poseSource != null
                && poseSource.getCapabilities().contains(Capabilities.UPRIGHT)
        ) {
            Pose pose = poseSource.getPose();
            if (pose != null) {
                // The pose source knows which way is upright, so if we don't have
                // an AoA elevation, we'll assume that elevation is level with the phone.
                // i.e. If the phone pitches down, the elevation would appear up.

                position = new SphericalVector.Sparse(
                    SphericalVector.fromRadians(
                        input.vector.azimuth,
                        -pose.rotation.toYawPitchRoll().y, // -Pitch becomes our assumed elevation
                        input.vector.distance
                    ),
                    input.hasAzimuth,
                    true,
                    input.hasDistance
                );
            }
        }
        return position;
    }
}
