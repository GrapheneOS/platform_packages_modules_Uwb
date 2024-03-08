/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.uwb.support.fira;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.annotation.IntRange;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Uwb Hybrid session controller configuration
 */
public class FiraHybridSessionControllerConfig extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final int SHORT_MAC_ADDRESS = 0;
    private static final int EXTENDED_MAC_ADDRESS = 1;
    private static final int PHASE_LIST_SIZE = 20;

    private final int mNumberOfPhases;
    private final byte[] mUpdateTime;
    private final byte mMessageControl;
    private final List<FiraHybridSessionPhaseList> mPhaseList;

    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String KEY_NUMBER_OF_PHASES = "number_of_phases";
    public static final String KEY_MESSAGE_CONTROL = "message_control";
    public static final String KEY_UPDATE_TIME = "update_time";
    public static final String KEY_PHASE_LIST = "phase_list";

    @Override
    public int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public int getNumberOfPhases() {
        return mNumberOfPhases;
    }

    public byte[] getUpdateTime() {
        return mUpdateTime;
    }

    public byte getMessageControl() {
        return mMessageControl;
    }

    public List<FiraHybridSessionPhaseList> getPhaseList() {
        return mPhaseList;
    }

    private FiraHybridSessionControllerConfig(int numberOfPhases, byte[] updateTime,
            byte messageControl, List<FiraHybridSessionPhaseList> phaseList) {
        mNumberOfPhases = numberOfPhases;
        mUpdateTime = updateTime;
        mMessageControl = messageControl;
        mPhaseList = phaseList;
    }

    //TODO, move these utility methods to helper class
    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = bytes[i];
        }
        return values;
    }

    @Nullable
    private static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putInt(KEY_MESSAGE_CONTROL, mMessageControl);
        bundle.putInt(KEY_NUMBER_OF_PHASES, mNumberOfPhases);
        bundle.putIntArray(KEY_UPDATE_TIME, byteArrayToIntArray(mUpdateTime));

        ByteBuffer buffer = ByteBuffer.allocate(mNumberOfPhases * PHASE_LIST_SIZE);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        for (FiraHybridSessionPhaseList phaseList : mPhaseList) {
            buffer.putInt(phaseList.getSessionHandle());
            buffer.putShort(phaseList.getStartSlotIndex());
            buffer.putShort(phaseList.getEndSlotIndex());
            buffer.putInt(phaseList.getPhaseParticipation());
            buffer.putLong(uwbAddressToLong(phaseList.getMacAddress()));
        }

        bundle.putIntArray(KEY_PHASE_LIST, byteArrayToIntArray(buffer.array()));
        return bundle;
    }

    public static FiraHybridSessionControllerConfig fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static FiraHybridSessionControllerConfig parseVersion1(PersistableBundle bundle) {
        FiraHybridSessionControllerConfig.Builder builder =
                new FiraHybridSessionControllerConfig.Builder();

        int numberOfPhases = bundle.getInt(KEY_NUMBER_OF_PHASES);
        builder.setNumberOfPhases(numberOfPhases);
        builder.setUpdateTime(intArrayToByteArray(bundle.getIntArray(KEY_UPDATE_TIME)));
        int messageControl = bundle.getInt(KEY_MESSAGE_CONTROL);
        byte macAddressMode = (byte) (messageControl & 0x01);
        builder.setMacAddressMode(macAddressMode);

        byte[] phaseByteArray = intArrayToByteArray(bundle.getIntArray(KEY_PHASE_LIST));
        ByteBuffer buffer = ByteBuffer.wrap(phaseByteArray);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < numberOfPhases; i++) {
            FiraHybridSessionPhaseList mFiraHybridSessionPhaseList = new FiraHybridSessionPhaseList(
                    buffer.getInt(),
                    buffer.getShort(),
                    buffer.getShort(),
                    (byte) buffer.getInt(),
                    longToUwbAddress(buffer.getLong(),
                        (macAddressMode == SHORT_MAC_ADDRESS
                        ? UwbAddress.SHORT_ADDRESS_BYTE_LENGTH
                                    : UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH)));
            builder.addPhaseList(mFiraHybridSessionPhaseList);
        }
        return builder.build();
    }

    /** Builder */
    public static class Builder {
        private int mNumberOfPhases;
        private byte[] mUpdateTime;
        private byte mMacAddressMode;
        private final List<FiraHybridSessionPhaseList> mPhaseList = new ArrayList<>();

        public FiraHybridSessionControllerConfig.Builder setNumberOfPhases(int numberOfPhases) {
            mNumberOfPhases = numberOfPhases;
            return this;
        }

        public FiraHybridSessionControllerConfig.Builder setUpdateTime(byte[] updateTime) {
            mUpdateTime = updateTime;
            return this;
        }

        public FiraHybridSessionControllerConfig.Builder setMacAddressMode(byte macAddressMode) {
            mMacAddressMode = macAddressMode;
            return this;
        }

        public FiraHybridSessionControllerConfig.Builder addPhaseList(
                FiraHybridSessionPhaseList phaseList) {
            mPhaseList.add(phaseList);
            return this;
        }

        public FiraHybridSessionControllerConfig build() {
            if (mPhaseList.size() == 0) {
                throw new IllegalStateException("No hybrid session phase list have been set");
            }
            return new FiraHybridSessionControllerConfig(
                    mNumberOfPhases,
                    mUpdateTime,
                    (byte) (mMacAddressMode & 0x01),
                    mPhaseList);
        }
    }

    /** Defines parameters for hybrid session's secondary phase list */
    public static class FiraHybridSessionPhaseList {
        private final int mSessionHandle;

        @IntRange(from = 1, to = 32767)
        private final short mStartSlotIndex;

        @IntRange(from = 1, to = 32767)
        private final short mEndSlotIndex;
        private final byte mPhaseParticipation;
        private final UwbAddress mMacAddress;

        public FiraHybridSessionPhaseList(int sessionHandle,
                @IntRange(from = 1, to = 32767) short startSlotIndex,
                @IntRange(from = 1, to = 32767) short endSlotIndex,
                @PhaseParticipationHybridSessionController byte phaseParticipation,
                UwbAddress macAddress) {
            mSessionHandle = sessionHandle;
            mStartSlotIndex = startSlotIndex;
            mEndSlotIndex = endSlotIndex;
            mPhaseParticipation = phaseParticipation;
            mMacAddress = macAddress;
        }

        public int getSessionHandle() {
            return mSessionHandle;
        }

        @IntRange(from = 1, to = 32767)
        public short getStartSlotIndex() {
            return mStartSlotIndex;
        }

        @IntRange(from = 1, to = 32767)
        public short getEndSlotIndex() {
            return mEndSlotIndex;
        }

        @PhaseParticipationHybridSessionController
        public byte getPhaseParticipation() {
            return mPhaseParticipation;
        }

        public UwbAddress getMacAddress() {
            return mMacAddress;
        }
    }
}
