/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ranging.generic;

import com.google.common.collect.ImmutableList;

import java.util.BitSet;
import java.util.List;

/** Enum representing an individual ranging technology. */
public enum RangingTechnology {
    UWB(0), // Ultra-Wide Band
    CS(1); // Channel Sounding, formerly known as HADM

    private final int value;

    RangingTechnology(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public byte toByte() {
        return (byte) (1 << value);
    }

    public static ImmutableList<RangingTechnology> parseByte(byte technologiesByte) {
        BitSet bitset = BitSet.valueOf(new byte[]{technologiesByte});
        ImmutableList.Builder<RangingTechnology> technologies = ImmutableList.builder();
        for (RangingTechnology technology : RangingTechnology.values()) {
            if (bitset.get(technology.value)) {
                technologies.add(technology);
            }
        }
        return technologies.build();
    }

    public static byte toBitmap(List<RangingTechnology> technologies) {
        if (technologies.isEmpty()) {
            return 0x0;
        }
        BitSet bitset = new BitSet();
        for (RangingTechnology technology : technologies) {
            bitset.set(technology.value);
        }
        return bitset.toByteArray()[0];
    }
}