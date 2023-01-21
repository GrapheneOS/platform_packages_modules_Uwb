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
package com.android.server.uwb.correction.filtering;

import static com.android.server.uwb.correction.math.MathHelper.F_PI;

import com.android.server.uwb.correction.math.MathHelper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A median and average filter that operates identically to {@link MAFilter}, but uses the radians
 * circular number system, wherein numbers refer to points on a circle such that +PI and -PI are
 * the same, and averages refer to the average angle on a circle rather than the average of their
 * linear numerical values.
 */
public class MARotationFilter extends MAFilter {
    public MARotationFilter(int windowSize, float cut) {
        super(windowSize, cut);
    }

    /**
     * Creates a naive average of the given samples. Both the value and instant of the samples are
     * averaged. This will probably not produce a desired result if the samples are normalized
     * to roll over at +/-PI rad. Use {@link #sortSamples(Collection)} to shift the roll over
     * to a more desired location.
     * @param samples The list of samples.
     * @return The average of the samples, normalized within +/-PI.
     */
    @Override
    protected Sample averageSortedSamples(Collection<Sample> samples) {
        Sample result = super.averageSortedSamples(samples);
        return new Sample(MathHelper.normalizeRadians(result.value), result.instant);
    }

    /**
     * Rewrites all sample values based on the selector.
     * @param selector The interface containing the remapping function.
     */
    public void remap(RemapFunction selector) {
        super.remap(v -> MathHelper.normalizeRadians(selector.run(v)));
    }

    /**
     * Changes the input list so that angles can be sorted, averaged and compared, even if
     * they are on either side of the +/-180 divide. Note that this will return angles
     * higher than 180 degrees.
     * The input values must be between -180 and +180.
     * Creating a sorted list, this finds the largest "gap" between angles and assumes that
     * angles on either side of that gap represent the upper and lower bounds of what needs to
     * be averaged. It then adds 360 to the values to the left of the gap and rearranges
     * to make the list is sorted again.
     * (Degrees are used for the explanation - this function actually operates on radians)
     */
    @Override
    protected List<Sample> sortSamples(Collection<Sample> list) {
        List<Sample> sorted = super.sortSamples(list);
        int size = sorted.size();
        if (size < 2) {
            return sorted;
        }

        // The initial gap to check, maybe not the biggest, is the gap on either side of +/-180,
        // which is at the index 0.
        int largestGapIndex = 0;
        float largestGapSize =
                (sorted.get(size - 1).value - sorted.get(0).value + 2 * F_PI) % F_PI;
        for (int i = 1; i < size; i++) {
            float diff = sorted.get(i).value - sorted.get(i - 1).value;
            if (diff > largestGapSize) {
                largestGapSize = diff;
                largestGapIndex = i;
            }
        }
        for (int i = 0; i < largestGapIndex; i++) {
            sorted.set(
                    i,
                    new Sample(sorted.get(i).value + 2 * F_PI, sorted.get(i).instant)
            );
        }
        Collections.rotate(sorted, -largestGapIndex);
        return sorted;
    }
}
