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

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils.Conversions;
import com.android.server.ranging.cs.CsOobConfig.CsLeFlag;
import com.android.server.ranging.cs.CsOobConfig.CsSecurityType;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Capability data for CS sent as part of CapabilityResponseMessage. */
@AutoValue
public abstract class CsOobCapabilities {

    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 10;

    // Size in bytes of properties for serialization/deserialization.
    private static final int SECURITY_TYPE_SIZE = 1;
    private static final int BLUETOOTH_ADDRESS_SIZE = 6;
    private static final int LE_FLAG_SIZE = 1;

    private static final int SECURITY_TYPE_SHIFT = 0;

    /** Returns the size of the object in bytes when serialized. */
    public static int getSize() {
        return EXPECTED_SIZE_BYTES;
    }

    /**
     * Parses the given byte array and returns {@link CsOobCapabilities} object. Throws {@link
     * IllegalArgumentException} on invalid input.
     */
    public static CsOobCapabilities parseBytes(byte[] capabilitiesBytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(capabilitiesBytes);

        if (capabilitiesBytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "CsOobCapabilities size is %d, expected at least %d",
                            capabilitiesBytes.length, EXPECTED_SIZE_BYTES));
        }

        if (capabilitiesBytes.length < header.getSize()) {
            throw new IllegalArgumentException(
                    String.format(
                            "CsOobCapabilities header size field is %d, but the size of the array"
                                    + " is"
                                    + " %d",
                            header.getSize(), capabilitiesBytes.length));
        }

        if (header.getRangingTechnology() != RangingTechnology.CS) {
            throw new IllegalArgumentException(
                    String.format(
                            "CsOobCapabilities header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.CS));
        }

        int parseCursor = header.getHeaderSize();

        // Supported security type
        ImmutableList<CsSecurityType> securityTypes =
                Conversions.byteArrayToIntList(
                                Arrays.copyOfRange(
                                        capabilitiesBytes, parseCursor,
                                        parseCursor + SECURITY_TYPE_SIZE),
                                SECURITY_TYPE_SHIFT)
                        .stream()
                        .map(CsSecurityType::fromValue)
                        .collect(ImmutableList.toImmutableList());
        parseCursor += SECURITY_TYPE_SIZE;

        // CS Address
        String bluetoothAddress =
                Conversions.macAddressToString(
                        Arrays.copyOfRange(
                                capabilitiesBytes, parseCursor,
                                parseCursor + BLUETOOTH_ADDRESS_SIZE));
        parseCursor += BLUETOOTH_ADDRESS_SIZE;

        // LE Flag
        CsLeFlag leFlag = CsLeFlag.parseByte(capabilitiesBytes[parseCursor]);
        parseCursor += LE_FLAG_SIZE;

        return CsOobCapabilities.builder()
                .setSupportedSecurityTypes(securityTypes)
                .setBluetoothAddress(bluetoothAddress)
                .setLeFlag(leFlag)
                .build();
    }

    /** Serializes this {@link CsOobCapabilities} object to bytes. */
    public final byte[] toBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
        byteBuffer
                .put(RangingTechnology.CS.toByte())
                .put((byte) EXPECTED_SIZE_BYTES)
                .put(
                        Conversions.intListToByteArrayBitmap(
                                getSupportedSecurityTypes().stream()
                                        .map(CsSecurityType::getValue)
                                        .collect(ImmutableList.toImmutableList()),
                                SECURITY_TYPE_SIZE,
                                SECURITY_TYPE_SHIFT))
                .put(Conversions.macAddressToBytes(getBluetoothAddress()))
                .put(getLeFlag().toByte());

        return byteBuffer.array();
    }

    /** Returns the security type for CS. */
    public abstract ImmutableList<CsSecurityType> getSupportedSecurityTypes();

    /** Returns the Bluetooth address of the device. */
    public abstract String getBluetoothAddress();

    /** Returns the LE flag for CS. */
    public abstract CsLeFlag getLeFlag();

    /** Returns a builder for {@link CsOobCapabilities}. */
    public static Builder builder() {
        return new AutoValue_CsOobCapabilities.Builder();
    }

    /** Builder for {@link CsOobCapabilities}. */
    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setSupportedSecurityTypes(
                ImmutableList<CsSecurityType> securityTypes);

        public abstract Builder setBluetoothAddress(String bluetoothAddress);

        public abstract Builder setLeFlag(CsLeFlag leFlag);

        public abstract CsOobCapabilities autoBuild();

        public CsOobCapabilities build() {
            CsOobCapabilities csCapabilities = autoBuild();
            // Validate Bluetooth Address, will throw if invalid.
            var unused = Conversions.macAddressToBytes(csCapabilities.getBluetoothAddress());
            return csCapabilities;
        }
    }
}