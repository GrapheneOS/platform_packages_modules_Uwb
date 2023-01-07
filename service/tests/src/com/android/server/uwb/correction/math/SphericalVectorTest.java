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

import static org.junit.Assert.assertEquals;

import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

@Presubmit
public class SphericalVectorTest {
    @Test
    public void testCtorNormalization() {
        SphericalVector vec = SphericalVector.fromDegrees(185, 10, 10);
        assertClose(toDegrees(vec.azimuth), -175);
        assertClose(toDegrees(vec.elevation), 10);

        // This is looking right and up so far that you're basically looking
        //  at what's behind you on your left.
        vec = SphericalVector.fromRadians((float) toRadians(5), (float) toRadians(110), 10);
        assertClose(vec.azimuth, toRadians(-175)); // +5deg from "behind".

        // 20° beyond ⦨90° is ⦨70° because all elevations lead down from there.
        assertClose(vec.elevation, toRadians(70));
    }

    @Test public void testCtorNegDistance() {
        SphericalVector vec = SphericalVector.fromDegrees(10, 12, -14);

        // A negative distance should flip azimuth by 180 and negate elevation.
        assertClose(toDegrees(vec.azimuth), -170);
        assertClose(toDegrees(vec.elevation), -12);
        assertClose(vec.distance, 14);
    }

    @Test
    public void testToAoAVector() {
        SphericalVector sv = SphericalVector.fromDegrees(0, 18, 10);
        AoAVector av = sv.toAoAVector();

        // When az/el is at 0deg, aoav and sv are effectively the same.
        assertThat(av.azimuth).isEqualTo(sv.azimuth);
        assertThat(av.elevation).isEqualTo(sv.elevation);
        assertThat(av.distance).isEqualTo(sv.distance);

        sv = SphericalVector.fromDegrees(-12, 0, 10);
        av = sv.toAoAVector();
        assertThat(av.azimuth).isEqualTo(sv.azimuth);
        assertThat(av.elevation).isEqualTo(sv.elevation);
        assertThat(av.distance).isEqualTo(sv.distance);

        assertClose(
                SphericalVector
                        .fromDegrees(-35, -45, 1)
                        .toCartesian()
                        .length(),
                1);

        SphericalVector svec = SphericalVector.fromDegrees(15, 25, 6);
        AoAVector avec = svec.toAoAVector();
        assertClose(svec.toCartesian(), avec.toCartesian());

        svec = SphericalVector.fromDegrees(95, 25, 6);
        avec = svec.toAoAVector();
        assertClose(svec.toCartesian(), avec.toCartesian());
        assertClose(avec.toSphericalVector().toCartesian(), avec.toCartesian());

        svec = SphericalVector.fromDegrees(-15, 35, 6);
        avec = svec.toAoAVector();
        assertClose(svec.toCartesian(), avec.toCartesian());
        assertClose(avec.toSphericalVector().toCartesian(), avec.toCartesian());

        svec = SphericalVector.fromDegrees(-15, 35, 6);
        avec = svec.toAoAVector();
        assertClose(svec.toCartesian(), avec.toCartesian());
        assertClose(avec.toSphericalVector().toCartesian(), avec.toCartesian());
    }

    @Test
    public void cartesian() {
        assertThat(SphericalVector.fromCartesian(0, 0, 0).distance).isEqualTo(0);

        // negative z-axis is "straight ahead".
        assertClose(
                SphericalVector.fromCartesian(new Vector3(0, 0, -1)),
                SphericalVector.fromDegrees(0, 0, 1)
        );

        // looking left.
        assertClose(
                SphericalVector.fromCartesian(new Vector3(-1, 0, 0)),
                SphericalVector.fromDegrees(-90, 0, 1)
        );

        // looking up.
        SphericalVector gimbalLock = SphericalVector.fromCartesian(new Vector3(0, 1, 0));
        // Note that this suffers from gimbal lock - meaning that ALL azimuth values are valid
        //  when looking up or down.
        assertClose(toDegrees(gimbalLock.elevation), 90);
        assertClose(gimbalLock.distance, 1);

        // looking 45 deg back left.
        assertClose(
                SphericalVector.fromCartesian(new Vector3(-1, 0, 1)),
                SphericalVector.fromDegrees(-(90 + 45), 0, (float) Math.sqrt(2))
        );

        Vector3 targ = new Vector3(-7, 23, 4);
        assertClose(SphericalVector.fromCartesian(targ).toCartesian(), targ);
    }

    @Test
    public void sparse() {
        SphericalVector vec = SphericalVector.fromDegrees(10, 15, 20);
        SphericalVector.Sparse sparse = vec.toSparse();

        assertClose(sparse.vector, vec);
        assertThat(sparse.isComplete()).isTrue();
        assertThat(sparse.toString().compareTo(vec.toString())).isEqualTo(0);

        sparse = vec.toSparse(true, false, false);
        assertThat(sparse.hasAzimuth).isTrue();
        assertThat(sparse.hasElevation).isFalse();
        assertThat(sparse.hasDistance).isFalse();
        assertEquals("[⦡  10.0,⦨  x  ,⤠  x  ]", sparse.toString());

        sparse = vec.toSparse(false, true, false);
        assertThat(sparse.hasAzimuth).isFalse();
        assertThat(sparse.hasElevation).isTrue();
        assertThat(sparse.hasDistance).isFalse();
        assertEquals("[⦡   x  ,⦨ 15.0,⤠  x  ]", sparse.toString());

        sparse = vec.toSparse(false, false, true);
        assertThat(sparse.hasAzimuth).isFalse();
        assertThat(sparse.hasElevation).isFalse();
        assertThat(sparse.hasDistance).isTrue();
        assertEquals("[⦡   x  ,⦨  x  ,⤠20.00]", sparse.toString());
    }

    @Test
    public void testToString() {
        SphericalVector loc = SphericalVector.fromDegrees(1.2f, -3.4f, 5.6f);
        assertEquals("[⦡   1.2,⦨ -3.4,⤠ 5.60]", loc.toString());
    }
}
