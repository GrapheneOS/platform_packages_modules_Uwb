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

package com.android.server.ranging.oob;

/** Enum representing a message type of the OOB message. */
public enum MessageType {
    CAPABILITY_REQUEST(0),
    CAPABILITY_RESPONSE(1),
    SET_CONFIGURATION(2),
    SET_CONFIGURATION_RESPONSE(3),
    START_RANGING(4),
    START_RANGING_RESPONSE(5),
    STOP_RANGING(6),
    STOP_RANGING_RESPONSE(7),
    UNKNOWN(8);

    private final int mValue;

    MessageType(int value) {
        this.mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    public byte toByte() {
        return (byte) mValue;
    }

    public static MessageType parseByte(byte messageId) {
        if (messageId > 8) {
            return UNKNOWN;
        }
        return MessageType.values()[messageId];
    }
}
