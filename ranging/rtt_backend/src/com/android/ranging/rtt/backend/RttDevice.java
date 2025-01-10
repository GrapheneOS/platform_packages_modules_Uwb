/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.ranging.rtt.backend;

/*
 * Represents a RTT device.
 */

import static java.nio.charset.StandardCharsets.UTF_8;

import androidx.annotation.NonNull;

import java.util.Arrays;

public class RttDevice {

    private final RttRangingDevice mRttRangingDevice;
    private final RttAddress mAddress;

    public RttRangingDevice getRttRangingDevice() {
        return mRttRangingDevice;
    }

    /**
     * Ranging Device for Wifi RTT
     *
     * @param rttRangingDevice RttRangingDevices
     */


    public RttDevice(RttRangingDevice rttRangingDevice) {
        mRttRangingDevice = rttRangingDevice;
        mAddress = new RttAddress(new byte[]{});
    }

    public RttAddress getAddress() {
        return mAddress;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mAddress.toBytes());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RttDevice device)) {
            return false;
        }
        return Arrays.equals(mAddress.toBytes(), device.mAddress.toBytes());
    }

    /**
     * Represents a RTT address.
     */
    public static class RttAddress {
        byte[] mAddress;

        /**
         * Creates a RttAddress from a HEX string.
         */
        public RttAddress(@NonNull String address) {
            this.mAddress = address.getBytes(UTF_8);
        }

        /**
         * Creates a RttAddress from a byte array.
         */
        public RttAddress(@NonNull byte[] address) {
            this.mAddress = address;
        }

        public byte[] toBytes() {
            return mAddress;
        }

        /**
         * Gets the device address (eg, MAC address).
         *
         * @return RTT address
         */
        public byte[] getAddress() {
            return mAddress;
        }
    }
}
