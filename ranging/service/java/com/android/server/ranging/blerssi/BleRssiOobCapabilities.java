/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.blerssi;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;

import java.nio.ByteBuffer;
import java.util.Arrays;

@AutoValue
public abstract class BleRssiOobCapabilities {
    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 8;

    // Size in bytes of properties for serialization/deserialization.
    private static final int BLUETOOTH_ADDRESS_SIZE = 6;

    public static int getSize() {
        return EXPECTED_SIZE_BYTES;
    }

    /** Returns the size of the object in bytes when serialized. */
    public static BleRssiOobCapabilities parseBytes(byte[] bytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(bytes);

        if (bytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "BleRssiOobCapabilities size is %d, expected at least %d",
                            bytes.length, EXPECTED_SIZE_BYTES));
        }

        if (bytes.length < header.getSize()) {
            throw new IllegalArgumentException(
                    String.format(
                            "BleRssiOobCapabilities header size field is %d, but the size of the "
                                    + "array is %d", header.getSize(), bytes.length));
        }

        if (header.getRangingTechnology() != RangingTechnology.RSSI) {
            throw new IllegalArgumentException(
                    String.format(
                            "BleRssiOobCapabilities header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.RSSI));
        }

        int cursor = header.getHeaderSize();

        // Device address
        String bluetoothAddress = Conversions.macAddressToString(
                Arrays.copyOfRange(bytes, cursor, cursor + BLUETOOTH_ADDRESS_SIZE));
        cursor += BLUETOOTH_ADDRESS_SIZE;

        return BleRssiOobCapabilities.builder().setBluetoothAddress(bluetoothAddress).build();
    }

    public final byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
        buffer
                .put(RangingTechnology.RSSI.toByte())
                .put((byte) EXPECTED_SIZE_BYTES)
                .put(Conversions.macAddressToBytes(getBluetoothAddress()));

        return buffer.array();
    }

    /** Returns the Bluetooth address of the device. */
    public abstract String getBluetoothAddress();

    /** Returns a builder for {@link BleRssiOobCapabilities}. */
    public static Builder builder() {
        return new AutoValue_BleRssiOobCapabilities.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setBluetoothAddress(String address);

        public abstract BleRssiOobCapabilities autoBuild();

        public BleRssiOobCapabilities build() {
            BleRssiOobCapabilities capabilities = autoBuild();
            // Validate Bluetooth Address, will throw if invalid.
            var unused = Conversions.macAddressToBytes(capabilities.getBluetoothAddress());
            return capabilities;
        }
    }
}
