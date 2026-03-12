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

package com.android.server.ranging.common;

import android.annotation.IntDef;
import android.app.AlarmManager;
import android.bluetooth.BluetoothStatusCodes;
import android.os.Build;
import android.os.SystemClock;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.packets.TechnologySet;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Utilities for {@link com.android.ranging}.
 */
public class RangingUtils {

    private static final String MEASUREMENT_TIME_LIMIT_EXCEEDED = "measurementTimeLimitExceeded";

    /**
     * Converts a Bluetooth MAC address from byte array to string format. Throws if input byte
     * array is not of correct format.
     *
     * <p>e.g. {-84, 55, 67, -68, -87, 40} -> "AC:37:43:BC:A9:28".
     */
    public static String macAddressToString(byte[] macAddress) {
        if (macAddress == null || macAddress.length != 6) {
            throw new IllegalArgumentException("Invalid mac address byte array");
        }
        StringBuilder sb = new StringBuilder(18);
        for (byte b : macAddress) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", b));
        }
        return Ascii.toUpperCase(sb.toString());
    }

    /**
     * Convert a Bluetooth MAC address from string to byte array format. Throws if input string
     * is not of correct format.
     *
     * <p>e.g. "AC:37:43:BC:A9:28" -> {-84, 55, 67, -68, -87, 40}.
     */
    public static byte[] macAddressToBytes(String macAddress) {
        if (macAddress.isEmpty()) {
            throw new IllegalArgumentException("MAC address cannot be empty");
        }

        byte[] bytes = new byte[6];
        List<String> address = Splitter.on(':').splitToList(macAddress);
        if (address.size() != 6) {
            throw new IllegalArgumentException("Invalid MAC address format");
        }
        for (int i = 0; i < 6; i++) {
            bytes[i] = Integer.decode("0x" + address.get(i)).byteValue();
        }
        return bytes;
    }

    /**
     * Convert a Bluetooth MAC address from string to anonymized string format.
     *
     * <p>e.g. "AC:37:43:BC:A9:28" -> "XX:XX:XX:XX:A9:28".
     */
    public static String toAnonymizedMacAddress(String address) {
        if (address == null || address.length() != 17) {
            return null;
        }
        return "XX:XX:XX:XX" + address.substring(11);
    }

    /**
     * Convert the hex string to byte array.
     */
    public static byte[] hexStringToByteArray(String hex) {
        // remove whitespace in the hex string.
        hex = hex.replaceAll("\\s", "");

        int len = hex.length();
        if (len % 2 != 0) {
            // Pad the hex string with a leading zero.
            hex = String.format("0%s", hex);
            len++;
        }
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] =
                    (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                            | Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static long convertNanosToMillis(long timestampNanos) {
        return timestampNanos / 1_000_000L;
    }

    public static void setMeasurementsLimitTimeout(
            AlarmManager alarmManager,
            AlarmManager.OnAlarmListener measurementLimitListener,
            int measurementsLimit, int rangingIntervalMs) {
        if (alarmManager == null) {
            return;
        }
        alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ((long) measurementsLimit * rangingIntervalMs),
                MEASUREMENT_TIME_LIMIT_EXCEEDED,
                measurementLimitListener,
                null
        );
    }

    /**
     * Covert bluetooth reason code to ranging reason code.
     */
    public static @InternalReason int convertBluetoothReasonCode(int bluetoothReasonCode) {
        return switch (bluetoothReasonCode) {
            case BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ENABLED,
                 BluetoothStatusCodes.ERROR_BLUETOOTH_NOT_ALLOWED,
                 BluetoothStatusCodes.ERROR_DEVICE_NOT_BONDED,
                 BluetoothStatusCodes.FEATURE_NOT_SUPPORTED,
                 BluetoothStatusCodes.ERROR_BAD_PARAMETERS -> InternalReason.UNSUPPORTED;

            case BluetoothStatusCodes.ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION,
                 BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND,
                 BluetoothStatusCodes.ERROR_NOT_ACTIVE_DEVICE,
                 BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES,
                 BluetoothStatusCodes.ERROR_PROFILE_NOT_CONNECTED,
                 BluetoothStatusCodes.ERROR_HARDWARE_GENERIC,
                 BluetoothStatusCodes.ERROR_LOCAL_NOT_ENOUGH_RESOURCES,
                 BluetoothStatusCodes.ERROR_REMOTE_NOT_ENOUGH_RESOURCES,
                 BluetoothStatusCodes.ERROR_REMOTE_OPERATION_REJECTED,
                 BluetoothStatusCodes.ERROR_ALREADY_IN_TARGET_STATE,
                 BluetoothStatusCodes.ERROR_CALLBACK_NOT_REGISTERED,
                 BluetoothStatusCodes.ERROR_ANOTHER_ACTIVE_REQUEST,
                 BluetoothStatusCodes.ERROR_REMOTE_OPERATION_NOT_SUPPORTED ->
                    InternalReason.INTERNAL_ERROR;

            case BluetoothStatusCodes.ERROR_TIMEOUT,
                 BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR,
                 BluetoothStatusCodes.ERROR_NO_LE_CONNECTION -> InternalReason.NO_PEERS_FOUND;

            case BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST -> InternalReason.LOCAL_REQUEST;

            case BluetoothStatusCodes.REASON_REMOTE_REQUEST -> InternalReason.REMOTE_REQUEST;

            case BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST,
                 BluetoothStatusCodes.REASON_SYSTEM_POLICY -> InternalReason.SYSTEM_POLICY;

            default -> InternalReason.UNKNOWN;
        };
    }

    @IntDef(value = {
            InternalReason.UNKNOWN,
            InternalReason.LOCAL_REQUEST,
            InternalReason.REMOTE_REQUEST,
            InternalReason.UNSUPPORTED,
            InternalReason.SYSTEM_POLICY,
            InternalReason.NO_PEERS_FOUND,
            InternalReason.INTERNAL_ERROR,
            InternalReason.BACKGROUND_RANGING_POLICY,
            InternalReason.PEER_CAPABILITIES_MISMATCH,
            InternalReason.ENGINE_REQUEST,
    })
    @Target({ElementType.TYPE_USE})
    public @interface InternalReason {
        int UNKNOWN = 0;
        int LOCAL_REQUEST = 1;
        int REMOTE_REQUEST = 2;
        int UNSUPPORTED = 3;
        int SYSTEM_POLICY = 4;
        int NO_PEERS_FOUND = 5;
        int INTERNAL_ERROR = 6;
        int BACKGROUND_RANGING_POLICY = 7;
        int PEER_CAPABILITIES_MISMATCH = 8;
        int ENGINE_REQUEST = 9;
    }

    public static String privateAddressIfUserBuild(String address) {
        return "user".equals(Build.TYPE) ? "00:00:00:00:00:00" : address;
    }

    public static TechnologySet technologyBitset(Collection<RangingTechnology> technologies) {
        TechnologySet.Builder bitset = new TechnologySet.Builder();
        for (RangingTechnology technology : technologies) {
            switch (technology) {
                case UWB -> bitset.setUwb(true);
                case CS -> bitset.setBleCs(true);
                case RTT -> bitset.setWifiNanRtt(true);
                case RSSI -> bitset.setBleRssi(true);
                case WIFI_PD -> bitset.setWifiPd(true);
                case RTT_STATION -> {
                    continue;
                }
            }
        }
        return bitset.build();
    }

    public static EnumSet<RangingTechnology> technologyBitset(TechnologySet bitset) {
        EnumSet<RangingTechnology> technologies = EnumSet.noneOf(RangingTechnology.class);
        if (bitset.getUwb()) technologies.add(RangingTechnology.UWB);
        if (bitset.getBleCs()) technologies.add(RangingTechnology.CS);
        if (bitset.getWifiNanRtt()) technologies.add(RangingTechnology.RTT);
        if (bitset.getBleRssi()) technologies.add(RangingTechnology.RSSI);
        if (bitset.getWifiPd()) technologies.add(RangingTechnology.WIFI_PD);
        return technologies;
    }

    public static ImmutableSet<Integer> bitset(int bitfield) {
        return bitset(bitfield, IntUnaryOperator.identity());
    }

    /**
     * @param op function to apply to the offsets in the bitfield before collecting them into a set.
     */
    public static ImmutableSet<Integer> bitset(int bitfield, IntUnaryOperator op) {
        ImmutableSet.Builder<Integer> set = ImmutableSet.builder();
        for (int index = 0; bitfield != 0; bitfield >>>= 1) {
            if ((bitfield & 1) == 1) set.add(op.applyAsInt(index));
            index++;
        }
        return set.build();
    }

    public static int bitset(Collection<Integer> offsets) {
        return bitset(offsets, IntUnaryOperator.identity());
    }

    /**
     * @param op function to apply to each values before inserting them into the bitfield.
     */
    public static int bitset(Collection<Integer> offsets, IntUnaryOperator op) {
        int result = 0;
        for (int offset : offsets) {
            result |= 1 << op.applyAsInt(offset);
        }
        return result;
    }

    /** Converts a byte array to a list of integers. */
    public static List<Integer> byteArrayToIntegerList(byte[] bytes) {
        if (bytes == null) {
            return new ArrayList<>();
        }
        List<Integer> list = new ArrayList<>(bytes.length);
        for (byte b : bytes) {
            list.add((int) b);
        }
        return list;
    }
}
