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

package com.android.server.ranging;

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import static java.lang.Math.min;

import android.annotation.IntDef;
import android.app.AlarmManager;
import android.bluetooth.BluetoothStatusCodes;
import android.os.SystemClock;
import android.ranging.raw.RawRangingDevice.RangingUpdateRate;
import android.util.Range;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/**
 * Utilities for {@link com.android.ranging}.
 */
public class RangingUtils {

    private static final String MEASUREMENT_TIME_LIMIT_EXCEEDED = "measurementTimeLimitExceeded";

    /**
     * A basic synchronized state machine.
     *
     * @param <E> enum representing the different states of the machine.
     */
    public static class StateMachine<E extends Enum<E>> {
        private E mState;

        public StateMachine(E start) {
            mState = start;
        }

        /** Gets the current state */
        public synchronized E getState() {
            return mState;
        }

        /** Sets the current state */
        public synchronized void setState(E state) {
            mState = state;
        }

        /**
         * Sets the current state.
         *
         * @return true if the state was successfully changed, false if the current state is
         * already {@code state}.
         */
        public synchronized boolean changeStateTo(E state) {
            if (mState == state) {
                return false;
            }
            setState(state);
            return true;
        }

        /**
         * If the current state is {@code from}, sets it to {@code to}.
         *
         * @return true if the current state is {@code from}, false otherwise.
         */
        public synchronized boolean transition(E from, E to) {
            if (mState != from) {
                return false;
            }
            mState = to;
            return true;
        }

        @Override
        public String toString() {
            return "StateMachine{ "
                    + mState
                    + "}";
        }
    }

    public static class Conversions {
        /**
         * Converts a list of integers to a byte array representing a bitmap of the integers. Given
         * integers are first shifted by the shift param amount before being placed into the bitmap
         * (e.g int x results in bit at pos "x - shift" being set).
         */
        public static byte[] intListToByteArrayBitmap(
                List<Integer> list, int expectedSizeBytes, int shift) {
            BitSet bitSet = new BitSet(expectedSizeBytes * 8);
            for (int i : list) {
                bitSet.set(i - shift);
            }
            byte[] byteArray = new byte[expectedSizeBytes];
            System.arraycopy(bitSet.toByteArray(), 0, byteArray, 0,
                    min(expectedSizeBytes, bitSet.toByteArray().length));
            return byteArray;
        }

        /**
         * Converts a byte array representing a bitmap of integers to a list of integers. The
         * resulting integers are shifted by the shift param amount (e.g bit set at pos x results
         * to "x + shift" int in the final list).
         */
        public static ImmutableList<Integer> byteArrayToIntList(byte[] byteArray, int shift) {
            ImmutableList.Builder<Integer> list = ImmutableList.builder();
            BitSet bitSet = BitSet.valueOf(byteArray);
            for (int i = 0; i < bitSet.length(); i++) {
                if (bitSet.get(i)) {
                    list.add(i + shift);
                }
            }
            return list.build();
        }

        /** Converts an int to a byte array of a given size, using little endianness. */
        public static byte[] intToByteArray(int value, int expectedSizeBytes) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putInt(value).rewind();
            byte[] byteArray = new byte[expectedSizeBytes];
            buffer.get(byteArray, 0, min(expectedSizeBytes, 4));
            return byteArray;
        }

        /** Converts the given byte array to an integer using little endianness. */
        public static int byteArrayToInt(byte[] byteArray) {
            ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put(byteArray).rewind();
            return buffer.getInt();
        }

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

    public static Optional<@RangingUpdateRate Integer> getUpdateRateFromDurationRange(
            Range<Duration> preferred,
            ImmutableMap<@RangingUpdateRate Integer, Duration> allowed
    ) {
        if (preferred.getLower().compareTo(allowed.get(UPDATE_RATE_INFREQUENT)) > 0) {
            // Range of preferred durations lies entirely above allowed durations
            return Optional.of(UPDATE_RATE_INFREQUENT);
        }
        if (preferred.getUpper().compareTo(allowed.get(UPDATE_RATE_FREQUENT)) < 0) {
            // Range of preferred durations lies entirely below allowed durations
            return Optional.of(UPDATE_RATE_FREQUENT);
        }
        // Otherwise, the intervals overlap. Pick fastest we can.
        if (preferred.contains(allowed.get(UPDATE_RATE_FREQUENT))) {
            return Optional.of(UPDATE_RATE_FREQUENT);
        } else if (preferred.contains(allowed.get(UPDATE_RATE_NORMAL))) {
            return Optional.of(UPDATE_RATE_NORMAL);
        } else if (preferred.contains(allowed.get(UPDATE_RATE_INFREQUENT))) {
            return Optional.of(UPDATE_RATE_INFREQUENT);
        } else {
            return Optional.empty();
        }
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
    }
}
