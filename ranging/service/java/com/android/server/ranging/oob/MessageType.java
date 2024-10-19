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

public enum MessageType {
    RANGING_CAPABILITY_REQUEST(0), RANGING_CAPABILITY_RESPONSE(1), RANGING_CONFIGURATION(
            2), START_RANGING(3), STOP_RANGING(4);

    private final int mMessageId;

    MessageType(int messageId) {
        mMessageId = messageId;
    }

    /**
     * Returns a MessageType the data represents.
     *
     * @param data OOB data.
     * @return MessageType
     */
    public static MessageType fromOobData(byte[] data) {
        if (data.length < 1) {
            throw new IllegalArgumentException(
                    "Invalid OOB data length. Data length: " + data.length);
        }

        int value = data[0];
        for (MessageType messageType : MessageType.values()) {
            if (messageType.mMessageId == value) {
                return messageType;
            }
        }

        throw new IllegalArgumentException("Unknown Message Type. Message Type value: " + value);
    }
}
