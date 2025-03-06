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
import android.ranging.wifi.rtt.RttRangingParams;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.SetConfigurationMessage;
import com.android.server.ranging.oob.TechnologyHeader;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.util.Arrays;

@AutoValue
public abstract class RttOobConfig implements SetConfigurationMessage.TechnologyOobConfig {

    private static final int MIN_SIZE_BYTES = 6;

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

    public static RttOobConfig parseBytes(byte[] rttConfigBytes) {
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

        if (header.getRangingTechnology() != RangingTechnology.RTT) {
            throw new IllegalArgumentException(
                    String.format(
                            "RttConfig header technology field is %s, expected %s",
                            header.getRangingTechnology(), RangingTechnology.RTT));
        }

        int parseCursor = header.getHeaderSize();
        int serviceNameSize = rttConfigBytes[parseCursor++];
        byte[] serviceNameBytes = Arrays.copyOfRange(rttConfigBytes, parseCursor,
                parseCursor + serviceNameSize);
        String serviceName = new String(serviceNameBytes);
        parseCursor += serviceNameSize;

        int deviceRole = rttConfigBytes[parseCursor++];
        boolean usePeriodicRanging = rttConfigBytes[parseCursor++] != 0;

        return builder()
                .setDeviceRole(deviceRole)
                .setServiceName(serviceName)
                .setUsePeriodicRanging(usePeriodicRanging)
                .build();
    }

    public final byte[] toBytes() {
        int size = MIN_SIZE_BYTES + serviceName().length() - 1;
        return ByteBuffer.allocate(size)
                .put(RangingTechnology.RTT.toByte())
                .put((byte) size)
                .put((byte) serviceName().length())
                .put(serviceName().getBytes())
                .put((byte) deviceRole())
                .put((byte) (usePeriodicRanging() ? 1 : 0))
                .array();
    }

    public int getSize() {
        return toBytes().length;
    }

    public RttConfig toTechnologyConfig(RangingDevice peer, int deviceRole) {
        return new RttConfig(
                deviceRole,
                new RttRangingParams.Builder(serviceName()).build(),
                new SessionConfig.Builder().build(),
                peer);
    }

    public abstract String serviceName();

    @OobDeviceRole
    public abstract int deviceRole();

    public abstract boolean usePeriodicRanging();

    public @interface OobDeviceRole {
        int RESPONDER = 0;
        int INITIATOR = 1;
    }

    public static Builder builder() {
        return new AutoValue_RttOobConfig.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setServiceName(String value);

        public abstract Builder setDeviceRole(int value);

        public abstract Builder setUsePeriodicRanging(boolean value);

        abstract RttOobConfig autoBuild();

        public RttOobConfig build() {
            return autoBuild();
        }

    }
}
