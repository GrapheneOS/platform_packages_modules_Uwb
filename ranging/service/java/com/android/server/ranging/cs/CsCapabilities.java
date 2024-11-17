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

package com.android.server.ranging.cs;

import android.ranging.RangingCapabilities;
import android.ranging.RangingManager;

import com.google.errorprone.annotations.DoNotCall;

/** Channel Sounding Capability data send as part of CapabilityResponseMessage during Finder OOB. */
public final class CsCapabilities implements RangingCapabilities.TechnologyCapabilities {

    private CsCapabilities() {
    }

    @Override
    public @RangingManager.RangingTechnology int getTechnology() {
        return RangingManager.BLE_CS;
    }

    // // CS data
    // // 2 byte bitmask bit 0 - standard, rest rfu
    // private byte[] supportedFeatures;
    // private boolean isDeviceBonded;
    // // 16 bytes
    // private byte[] confirmationHash;
    // private boolean isRandmizerHashPresent;
    // // 16 bytes if it exists
    // private byte[] randmizerHash;
    // // 7 bytes
    // private byte[] deviceAddress;
    // // 1 byte
    // private int deviceRole;
    // // 16 bytes
    // private byte[] leTemporaryKey;
    // // 2 bytes
    // private byte[] leAppearance;
    // private int discoveryMode;

    /** Returns the size of this {@link CsCapabilities} object when serialized. */
    @DoNotCall("Always throws UnsupportedOperationException.")
    public int getSize() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Parses the given byte array and returns {@link CsCapabilities} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    @DoNotCall("Always throws UnsupportedOperationException.")
    public static CsCapabilities parseBytes(byte[] csCapabilitiesBytes) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Serializes this {@link CsCapabilities} object to bytes. */
    @DoNotCall("Always throws UnsupportedOperationException.")
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String toString() {
        return "CsCapabilities{}";
    }

}
