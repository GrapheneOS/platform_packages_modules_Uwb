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

package com.android.server.uwb.correction.math;

import static com.android.server.uwb.correction.TestHelpers.assertClose;

import static com.google.common.truth.Truth.assertThat;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class AoAVectorTest {
    /**
     * Tests what the AoAVector does when it's given impossible angles.
     */
    @Test
    public void testCtorScaleDown() {
        AoAVector vec = AoAVector.fromDegrees(90, 90, 1);
        assertClose(vec.elevation, Math.PI / 4);
        assertClose(vec.azimuth, Math.PI / 4);

        vec = AoAVector.fromDegrees(180 - 45, 90, 1);
        assertClose(vec.azimuth, Math.PI * 5 / 6);
        assertClose(vec.elevation, Math.PI / 3);
    }

    @Test
    public void testCtorNormalization() {
        AoAVector vec = AoAVector.fromDegrees(185, 10, 10);
        assertClose(toDegrees(vec.azimuth), -175);
        assertClose(toDegrees(vec.elevation), 10);

        // This is looking right and up so far that you're basically looking
        //  at what's behind you on your left.
        vec = AoAVector.fromRadians((float) toRadians(5), (float) toRadians(110), 10);
        assertClose(vec.azimuth, toRadians(-175)); // +5deg from "behind".

        // 20° beyond ⦨90° is ⦨70° because all elevations lead down from there.
        assertClose(vec.elevation, toRadians(70));
    }

    @Test public void testCtorNegDistance() {
        AoAVector vec = AoAVector.fromDegrees(10, 12, -14);

        // A negative distance should flip azimuth by 180 and negate elevation.
        assertClose(toDegrees(vec.azimuth), -170);
        assertClose(toDegrees(vec.elevation), -12);
        assertClose(vec.distance, 14);
    }

    @Test
    public void testToSphericalVector() {
        AoAVector aoav = AoAVector.fromDegrees(0, 18, 10);
        SphericalVector sv = aoav.toSphericalVector();

        // When az/el is at 0deg, aoav and sv are effectively the same.
        assertThat(sv.azimuth).isEqualTo(aoav.azimuth);
        assertThat(sv.elevation).isEqualTo(aoav.elevation);
        assertThat(sv.distance).isEqualTo(aoav.distance);

        aoav = AoAVector.fromDegrees(-12, 0, 10);
        sv = aoav.toSphericalVector();
        assertThat(sv.azimuth).isEqualTo(aoav.azimuth);
        assertThat(sv.elevation).isEqualTo(aoav.elevation);
        assertThat(sv.distance).isEqualTo(aoav.distance);

        assertClose(
                SphericalVector
                        .fromDegrees(-35, -45, 1)
                        .toCartesian()
                        .length(),
                1);

        AoAVector avec = AoAVector.fromDegrees(15, 25, 6);
        SphericalVector svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());

        avec = AoAVector.fromDegrees(95, 25, 6);
        svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());
        assertClose(svec.toAoAVector().toCartesian(), svec.toCartesian());

        avec = AoAVector.fromDegrees(-15, 35, 6);
        svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());
        assertClose(svec.toAoAVector().toCartesian(), svec.toCartesian());

        avec = AoAVector.fromDegrees(-15, 35, 6);
        svec = avec.toSphericalVector();
        assertClose(avec.toCartesian(), svec.toCartesian());
        assertClose(svec.toAoAVector().toCartesian(), svec.toCartesian());
    }

    @Test
    public void cartesian() {
        assertThat(AoAVector.fromCartesian(0, 0, 0).distance).isEqualTo(0);

        // negative z-axis is "straight ahead".
        assertClose(
                AoAVector.fromCartesian(new Vector3(0, 0, -1)),
                AoAVector.fromDegrees(0, 0, 1)
        );

        // looking left.
        assertClose(
                AoAVector.fromCartesian(new Vector3(-1, 0, 0)),
                AoAVector.fromDegrees(-90, 0, 1)
        );

        // looking up.
        AoAVector gimbalLock = AoAVector.fromCartesian(new Vector3(0, 1, 0));
        // Note that this suffers from gimbal lock - meaning that ALL azimuth values are valid
        //  when looking up or down.
        assertClose(toDegrees(gimbalLock.elevation), 90);
        assertClose(gimbalLock.distance, 1);

        // looking 45 deg back left.
        assertClose(
                AoAVector.fromCartesian(new Vector3(-1, 0, 1)),
                AoAVector.fromDegrees(-(90 + 45), 0, (float) Math.sqrt(2))
        );

        Vector3 targ = new Vector3(-7, 23, 4);
        assertClose(AoAVector.fromCartesian(targ).toCartesian(), targ);
    }

    @Test
    public void testToString() {
        AoAVector loc = AoAVector.fromDegrees(1.2f, -3.4f, 5.6f);
        assertThat(loc.toString()).isEqualTo("[⦡   1.2,⦨ -3.4,⤠ 5.60]");
    }
}
