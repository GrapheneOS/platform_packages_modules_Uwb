/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.uwb.support.rftest;

import android.os.PersistableBundle;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.google.uwb.support.base.Params;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Defines parameters for RF test operation */
public abstract class RfTestParams extends Params {
    public static final String PROTOCOL_NAME = "rftest";

    @Override
    public final String getProtocolName() {
        return PROTOCOL_NAME;
    }

    /** Checks if the {@link PersistableBundle} is based on the rftest protocol. */
    public static boolean isCorrectProtocol(PersistableBundle bundle) {
        return isProtocol(bundle, PROTOCOL_NAME);
    }

    /** Checks if the protocolName is rftest . */
    public static boolean isCorrectProtocol(String protocolName) {
        return protocolName.equals(PROTOCOL_NAME);
    }

    /** Session Id */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {SESSION_ID_RFTEST})
    public @interface SessionId {
    }

    public static final int SESSION_ID_RFTEST = 0x00;

    /** Session Type */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {SESSION_TYPE_RFTEST})
    public @interface SessionType {
    }

    public static final int SESSION_TYPE_RFTEST = 0xD0;

    /** Randomized PSDU default value 0 */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    NO_RANDOMIZATION,
                    RANDOMIZE_PSDU,
            })
    public @interface RandomizePsdu {
    }

    public static final int NO_RANDOMIZATION = 0;
    public static final int RANDOMIZE_PSDU = 1;

    /** Ranging bit field default value 0 */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    DISABLE_PHR,
                    ENABLE_PHR,
            })
    public @interface PhrRangingBit {
    }

    public static final int DISABLE_PHR = 0;
    public static final int ENABLE_PHR = 1;

    /** STS INDEX increment default value 0 */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    NO_AUTO_INCR,
                    AUTO_INCR_STS_INDEX,
            })
    public @interface StsIndexAutoIncr {
    }

    public static final int NO_AUTO_INCR = 0;
    public static final int AUTO_INCR_STS_INDEX = 1;

    /** STS bitmap default value 0 */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    NO_STS_DETECT_BITMAP,
                    REPORT_STS_DETECT_BITMAP,
            })
    public @interface StsDetectBitmap {
    }

    public static final int NO_STS_DETECT_BITMAP = 0;
    public static final int REPORT_STS_DETECT_BITMAP = 1;

    /** RF Test command */
    @IntDef(
            value = {
                    TEST_PERIODIC_TX,
                    TEST_PER_RX,
                    TEST_RX,
                    TEST_LOOPBACK,
                    TEST_SS_TWR,
                    TEST_SR_RX,
            })
    public @interface RfTestOperationType {
    }

    public static final int TEST_PERIODIC_TX = 0;
    public static final int TEST_PER_RX = 1;
    public static final int TEST_RX = 2;
    public static final int TEST_LOOPBACK = 3;
    public static final int TEST_SS_TWR = 4;
    public static final int TEST_SR_RX = 5;

    @Nullable
    public static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    @Nullable
    public static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = bytes[i];
        }
        return values;
    }
}
