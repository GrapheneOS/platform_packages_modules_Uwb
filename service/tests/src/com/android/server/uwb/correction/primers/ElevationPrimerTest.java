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

import static com.android.server.uwb.correction.TestHelpers.assertClose;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Sparse;
import com.android.server.uwb.correction.math.Vector3;
import com.android.server.uwb.correction.pose.IPoseSource.Capabilities;
import com.android.server.uwb.correction.pose.NullPoseSource;

import org.junit.Test;

import java.util.EnumSet;

public class ElevationPrimerTest {
    @Test
    public void hasElevationTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource();
        nps.setCapabilities(EnumSet.of(Capabilities.UPRIGHT));

        Sparse input = SphericalVector.fromDegrees(35, 0, 10).toSparse();
        Sparse result = primer.prime(input, null, nps);

        assertThat(result.hasElevation).isTrue();
        // Verify that, since elevation was already available, it was unchanged.
        assertThat(result.vector.elevation).isEqualTo(input.vector.elevation);
    }

    @Test
    public void noPoseTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource(); // Note: no upright capability.

        Sparse input = SphericalVector.fromDegrees(35, 0, 10)
                .toSparse(true, false, true);

        Sparse result = primer.prime(input, null, nps);
        // Pose is not capable of guessing elevation.
        assertThat(result.hasElevation).isFalse();

        result = primer.prime(input, null, null);
        // Pose source does not exist.
        assertThat(result.hasElevation).isFalse();
    }

    @Test
    public void noElevationTest() {
        ElevationPrimer primer = new ElevationPrimer();
        NullPoseSource nps = new NullPoseSource();
        nps.setCapabilities(EnumSet.of(Capabilities.UPRIGHT));
        float rads = (float) Math.toRadians(-5);
        nps.changePose(new Pose(Vector3.ORIGIN, Quaternion.yawPitchRoll(0, rads, 0)));

        Sparse input = SphericalVector.fromDegrees(35, 0, 10)
                .toSparse(true, false, true);
        SphericalVector prediction = SphericalVector.fromDegrees(5, 6, 7);
        Sparse result = primer.prime(input, prediction, nps);

        assertThat(result.hasElevation).isTrue();
        // The phone pose is slightly facing down, so the elevation should be slightly up.
        assertClose(result.vector.elevation, -rads);
    }
}
