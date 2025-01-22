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

package com.android.server.ranging;

import static java.lang.Math.min;

import android.content.Context;
import android.ranging.RangingManager;

import com.android.server.ranging.blerssi.BleRssiCapabilitiesAdapter;
import com.android.server.ranging.cs.CsCapabilitiesAdapter;
import com.android.server.ranging.rtt.RttCapabilitiesAdapter;
import com.android.server.ranging.uwb.UwbCapabilitiesAdapter;

import com.google.common.collect.ImmutableList;

import java.util.BitSet;
import java.util.Collection;

/** Enum representing an individual ranging technology. */
public enum RangingTechnology {
    UWB(0), // Ultra-Wide Band
    CS(1), // Channel Sounding, formerly known as HADM

    RTT(2), // Wifi RTT.
    RSSI(3); // BLE RSSI.

    public static final ImmutableList<RangingTechnology> TECHNOLOGIES =
            ImmutableList.copyOf(RangingTechnology.values());

    private static final int BITMAP_SIZE_BYTES = 2;

    private final int value;

    RangingTechnology(int value) {
        this.value = value;
    }

    public @RangingManager.RangingTechnology int getValue() {
        return value;
    }

    public byte toByte() {
        return (byte) value;
    }

    /**
     * Check whether this technology is available given the provided context.
     * @return true if the technology is supported, false otherwise.
     */
    public boolean isSupported(Context context) {
        switch (this) {
            case UWB:
                return UwbCapabilitiesAdapter.isSupported(context);
            case CS:
                return CsCapabilitiesAdapter.isSupported(context);
            case RTT:
                return RttCapabilitiesAdapter.isSupported(context);
            case RSSI:
                return BleRssiCapabilitiesAdapter.isSupported(context);
            default:
                return false;
        }
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

    public static byte[] toBitmap(Collection<RangingTechnology> technologies) {
        if (technologies.isEmpty()) {
            return new byte[BITMAP_SIZE_BYTES];
        }
        BitSet bitset = new BitSet(BITMAP_SIZE_BYTES * 8);
        for (RangingTechnology technology : technologies) {
            bitset.set(technology.value);
        }
        byte[] bitmap = new byte[BITMAP_SIZE_BYTES];
        System.arraycopy(
                bitset.toByteArray(), 0, bitmap, 0,
                min(BITMAP_SIZE_BYTES, bitset.toByteArray().length));
        return bitmap;
    }

    public static ImmutableList<RangingTechnology> fromBitmap(byte[] technologiesBitmap) {
        ImmutableList.Builder<RangingTechnology> techs = ImmutableList.builder();
        BitSet bitSet = BitSet.valueOf(technologiesBitmap);
        for (int i = 0; i < BITMAP_SIZE_BYTES * 8; i++) {
            if (bitSet.get(i)) {
                try {
                    techs.add(RangingTechnology.TECHNOLOGIES.get(i));
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException("Unknown technology " + i);
                }
            }
        }
        return techs.build();
    }
}