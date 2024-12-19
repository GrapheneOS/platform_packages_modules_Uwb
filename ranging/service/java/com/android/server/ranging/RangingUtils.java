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

import static java.lang.Math.min;

import android.app.AlarmManager;
import android.os.SystemClock;

import com.google.common.collect.ImmutableList;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.List;

/**
 * Utilities for {@link com.android.ranging}.
 */
public class RangingUtils {

    private static final String MEASUREMENT_TIME_LIMIT_EXCEEDED = "measurementTimeLimitExceeded";
    /**
     * A basic synchronized state machine.
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
}
