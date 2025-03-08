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
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;

import java.nio.ByteBuffer;
import java.util.Arrays;

@AutoValue
public abstract class BleRssiOobConfig implements SetConfigurationMessage.TechnologyOobConfig {
    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 8;

    private static final int BLUETOOTH_ADDRESS_SIZE = 6;

    public static int getSize() {
        return EXPECTED_SIZE_BYTES;
    }

    public static BleRssiOobConfig parseBytes(byte[] bytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(bytes);

        if (bytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("BleRssiOobConfig size is %d, expected at least %d",
                            bytes.length, EXPECTED_SIZE_BYTES));
        }

        if (header.getRangingTechnology() != RangingTechnology.RSSI) {
            throw new IllegalArgumentException(
                    String.format(
                            "BleRssiOobConfig header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.RSSI));
        }

        int parseCursor = header.getHeaderSize();

        String address = Conversions.macAddressToString(
                Arrays.copyOfRange(bytes, parseCursor, parseCursor + BLUETOOTH_ADDRESS_SIZE));
        parseCursor += BLUETOOTH_ADDRESS_SIZE;

        return BleRssiOobConfig.builder()
                .setBluetoothAddress(address)
                .build();
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

    /** Returns a builder for {@link BleRssiOobConfig}. */
    public static BleRssiOobConfig.Builder builder() {
        return new AutoValue_BleRssiOobConfig.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setBluetoothAddress(String address);

        public abstract BleRssiOobConfig autoBuild();

        public BleRssiOobConfig build() {
            BleRssiOobConfig config = autoBuild();
            var unused = Conversions.macAddressToBytes(config.getBluetoothAddress());
            return config;
        }
    }
}
