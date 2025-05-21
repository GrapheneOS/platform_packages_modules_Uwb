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

package com.android.server.ranging.rtt;

import android.ranging.wifi.rtt.RttStationRangingCapabilities;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

@AutoValue
public abstract class RttStationOobCapabilities {

    /** Size in bytes of all properties when serialized. */
    private static final int EXPECTED_SIZE_BYTES = 5;

    private static final int RTT_SUPPORTED_FEATURES_SIZE = 1;
    private static final int RTT_SUPPORTED_FEATURES_SHIFT = 0;

    public static RttStationOobCapabilities parseBytes(byte[] capabilitiesBytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(capabilitiesBytes);

        if (capabilitiesBytes.length < EXPECTED_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "RttStationOobCapabilities size is %d, expected at least %d",
                            capabilitiesBytes.length, EXPECTED_SIZE_BYTES));
        }

        if (capabilitiesBytes.length < header.getSize()) {
            throw new IllegalArgumentException(
                    String.format(
                            "RttStationOobCapabilities header size field is %d, but the size of "
                                    + "the array"
                                    + " is"
                                    + " %d",
                            header.getSize(), capabilitiesBytes.length));
        }

        if (header.getRangingTechnology() != RangingTechnology.RTT_STATION) {
            throw new IllegalArgumentException(
                    String.format(
                            "RttStationOobCapabilities header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.RTT_STATION));
        }

        int parseCursor = header.getHeaderSize();

        ImmutableList<RttStationOobConfig.RttSupportedFeatures> supportedFeatures =
                RangingUtils.Conversions.byteArrayToIntList(
                                Arrays.copyOfRange(capabilitiesBytes, parseCursor,
                                        parseCursor + RTT_SUPPORTED_FEATURES_SIZE),
                                RTT_SUPPORTED_FEATURES_SHIFT)
                        .stream()
                        .map(RttStationOobConfig.RttSupportedFeatures::fromValue)
                        .collect(ImmutableList.toImmutableList());
        parseCursor += RTT_SUPPORTED_FEATURES_SIZE;

        int supportedBands = capabilitiesBytes[parseCursor++];
        int supportedSecurity = capabilitiesBytes[parseCursor++];

        return RttStationOobCapabilities.builder()
                //.setSupportedFeatures(supportedFeatures)
                .setNumSupportedBands(supportedBands)
                .setSupportedSecurity(supportedSecurity)
                .build();
    }

    public final byte[] toBytes() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(EXPECTED_SIZE_BYTES);
        byteBuffer
                .put(RangingTechnology.RTT_STATION.toByte())
                .put((byte) EXPECTED_SIZE_BYTES)
                .put((byte) 1) // TODO: how to check for 11mc or 11az support
                .put((byte) numSupportedBands())
                .put((byte) supportedSecurity());

        return byteBuffer.array();
    }

    public static RttStationOobCapabilities fromRangingCapabilities(
            RttStationRangingCapabilities capabilities) {
        return RttStationOobCapabilities.builder()
                .setNumSupportedBands(capabilities.getNumSupportedBands())
                .setSupportedSecurity(capabilities.getSupportedSecurity())
                .build();
    }

    //public abstract ImmutableList<RttOobConfig.RttSupportedFeatures> supportedFeatures();

    //public abstract int numSupportedBands();

    public abstract int numSupportedBands();

    public abstract int supportedSecurity();

    public static Builder builder() {
        return new AutoValue_RttStationOobCapabilities.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        //public abstract Builder setSupportedFeatures(
        //ImmutableList<RttOobConfig.RttSupportedFeatures> value);

        public abstract Builder setNumSupportedBands(int value);

        public abstract Builder setSupportedSecurity(int value);

        public abstract RttStationOobCapabilities autoBuild();

        public RttStationOobCapabilities build() {
            return autoBuild();
        }
    }
}
