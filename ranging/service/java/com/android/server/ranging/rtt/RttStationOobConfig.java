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

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.wifi.rtt.RttStationRangingParams;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;

@AutoValue
public abstract class RttStationOobConfig implements SetConfigurationMessage.TechnologyOobConfig {

    private static final int MIN_SIZE_BYTES = 5;
    private static final String BSSID = "AA:BB:CC:CC:BB:AA";

    public enum RttSupportedFeatures {
        RTT_11_MC(1),
        RTT_11_AZ(2),
        RTT_UNKNOWN(255);

        public static final ImmutableList<RttSupportedFeatures> FEATURES =
                ImmutableList.copyOf(RttSupportedFeatures.values());

        private final int mValue;

        RttSupportedFeatures(int value) {
            this.mValue = value;
        }

        public int getValue() {
            return mValue;
        }

        public static RttSupportedFeatures fromValue(int value) {
            return value < RTT_11_MC.mValue || value > RTT_11_AZ.mValue ? RTT_UNKNOWN :
                    RttSupportedFeatures.values()[value];
        }
    }

    public enum Bandwidth {
        BANDWIDTH_20MHZ(0),
        BANDWIDTH_40MHZ(1),
        BANDWIDTH_80MHZ(2),
        BANDWIDTH_160MHZ(3),
        BANDWIDTH_80MHZ_PLUS_MHZ(4),
        BANDWIDTH_320MHZ(5),
        BANDWIDTH_UNDEFINED(255);

        private final int mValue;

        Bandwidth(int value) {
            this.mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public static RttStationOobConfig parseBytes(byte[] rttConfigBytes) {
        TechnologyHeader header = TechnologyHeader.parseBytes(rttConfigBytes);

        if (rttConfigBytes.length < MIN_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format("RttConfig size is %d, expected at least %d",
                            rttConfigBytes.length, MIN_SIZE_BYTES));
        }

        if (rttConfigBytes.length < header.getSize()) {
            throw new IllegalArgumentException(
                    String.format(
                            "RttConfig header size field is %d, but the size of the array is %d",
                            header.getSize(), rttConfigBytes.length));
        }

        if (header.getRangingTechnology() != RangingTechnology.RTT_STATION) {
            throw new IllegalArgumentException(
                    String.format(
                            "RttConfig header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.RTT_STATION));
        }

        int parseCursor = header.getHeaderSize();
        parseCursor += 2; //skip frequency
        int bandWidth = rttConfigBytes[parseCursor++];

        return builder()
                .setBandWidth(bandWidth)
                .build();
    }

    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES * 2;
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.RTT_STATION.toByte())
                .put((byte) size)
                .put((byte) BSSID.length())
                .put(BSSID.getBytes())
                .put((byte) bandWidth())
                .array();
    }

    public int getSize() {
        return toBytes().length;
    }

    public RttConfig toTechnologyConfig(RangingDevice peer, int deviceRole) {
        return new RttConfig(
                OobDeviceRole.INITIATOR,
                new RttStationRangingParams.Builder(BSSID).build(),
                new SessionConfig.Builder().build(),
                peer);
    }

    public abstract int bandWidth();

    //@OobDeviceRole
    //public abstract int deviceRole();

    public @interface OobDeviceRole {
        int RESPONDER = 0;
        int INITIATOR = 1;
    }

    public static Builder builder() {
        return new AutoValue_RttStationOobConfig.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {

        public abstract Builder setBandWidth(int value);

        abstract RttStationOobConfig autoBuild();

        public RttStationOobConfig build() {
            return autoBuild();
        }

    }
}
