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

package android.ranging.uwb;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.uwb.UwbRangingParams.SlotDuration;
import com.android.ranging.flags.Flags;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Class to represent UWB Downlink TDoA ranging parameters.
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class DlTdoaRangingParams implements Parcelable {
    private static final int FIRA_OOB_WIFI_VSE_MINIMUM_TOTAL_LENGTH = 6;
    private static final int FIRA_OOB_UWB_CONFIGURATION_HEADER_LENGTH = 2;

    private static final int FIRA_OOB_WIFI_VSE_ID = 0xDD;
    private static final int FIRA_OOB_WIFI_OUI_0 = 0x5A;
    private static final int FIRA_OOB_WIFI_OUI_1 = 0x18;
    private static final int FIRA_OOB_WIFI_OUI_2 = 0xFF;
    private static final int FIRA_SUB_ELEMENT_TYPE_UWB_CONFIG = 0x05;
    private static final int FIRA_UWB_PROFILE_ID = 0x02;

    private static final int TAG_CHANNEL_NUMBER = 0x04;
    private static final int TAG_DEVICE_MAC_ADDRESS = 0x06;
    private static final int TAG_SLOT_DURATION = 0x08;
    private static final int TAG_RANGING_DURATION = 0x09;
    private static final int TAG_PREAMBLE_CODE_INDEX = 0x14;
    private static final int TAG_SLOTS_PER_RR = 0x1B;
    private static final int TAG_VENDOR_ID = 0x27;
    private static final int TAG_STATIC_STS_IV = 0x28;
    private static final int TAG_SESSION_ID = 0x9F;

    // As per FiRa/UCI, Slot Duration is typically in RSTU (Ranging Slot Time Units),
    // where 1200 RSTU is approx 1ms. We convert to the nearest supported ms value.
    private static final int RSTU_PER_MS = 1200;

    private final int mSessionId;
    private final UwbAddress mDeviceAddress;
    private final byte[] mSessionKeyInfo;
    private final UwbComplexChannel mComplexChannel;
    private final int mRangingIntervalMs;
    @SlotDuration
    private final int mSlotDuration;
    private final int mSlotsPerRangingRound;
    private final byte[] mRangingRoundIndexes;

    private DlTdoaRangingParams(Builder builder) {
        mSessionId = builder.mSessionId;
        mDeviceAddress = builder.mDeviceAddress;
        mSessionKeyInfo = builder.mSessionKeyInfo;
        mComplexChannel = builder.mComplexChannel;
        mRangingIntervalMs = builder.mRangingIntervalMs;
        mSlotDuration = builder.mSlotDuration;
        mSlotsPerRangingRound = builder.mSlotsPerRangingRound;
        mRangingRoundIndexes = builder.mRangingRoundIndexes;
    }

    private DlTdoaRangingParams(Parcel in) {
        mSessionId = in.readInt();
        mDeviceAddress = Objects.requireNonNull(
                in.readParcelable(UwbAddress.class.getClassLoader(), UwbAddress.class));
        mSessionKeyInfo = in.createByteArray();
        mComplexChannel = in.readParcelable(UwbComplexChannel.class.getClassLoader(),
                UwbComplexChannel.class);
        mRangingIntervalMs = in.readInt();
        mSlotDuration = in.readInt();
        mSlotsPerRangingRound = in.readInt();
        mRangingRoundIndexes = in.createByteArray();
    }

    public static final @NonNull Creator<DlTdoaRangingParams> CREATOR =
            new Creator<DlTdoaRangingParams>() {
        @Override
        public DlTdoaRangingParams createFromParcel(Parcel in) {
            return new DlTdoaRangingParams(in);
        }

        @Override
        public DlTdoaRangingParams[] newArray(int size) {
            return new DlTdoaRangingParams[size];
        }
    };

    /**
     * Creates a {@link DlTdoaRangingParams} from a FiRa compliant configuration packet.
     *
     * @param config The byte array containing the FiRa configuration packet.
     * @param rangingRoundIndexes The active ranging round indexes. If null, use the default
     * value of {@link DlTdoaRangingParams.Builder}.
     * @return A {@link DlTdoaRangingParams} instance.
     * @throws IllegalArgumentException if the configuration packet is malformed or missing
     * mandatory fields.
     * @see <a href="https://groups.firaconsortium.org/wg/FPSG/document/5944">FiRa Specific OOB
     * Profile Advertisement Message</a> for the configuration packet format.
     */
    @NonNull
    public static DlTdoaRangingParams createFromFiraConfigPacket(
            @NonNull byte[] config, @Nullable byte [] rangingRoundIndexes) {
        Objects.requireNonNull(config);

        // Validate header
        if (config.length < FIRA_OOB_WIFI_VSE_MINIMUM_TOTAL_LENGTH) {
            throw new IllegalArgumentException("Not enough bytes for a valid FiRa OOB WiFi VSE.");
        }
        int vseLength = config[1] & 0xFF;
        int totalLength = 2 + vseLength;
        if (totalLength < FIRA_OOB_WIFI_VSE_MINIMUM_TOTAL_LENGTH) {
            throw new IllegalArgumentException("Invalid FiRa OOB WiFi VSE length.");
        }
        if (config.length < totalLength) {
            throw new IllegalArgumentException("Not enough bytes for FiRa OOB WiFi VSE content.");
        }
        if ((config[0] & 0xFF) != FIRA_OOB_WIFI_VSE_ID
                || (config[2] & 0xFF) != FIRA_OOB_WIFI_OUI_0
                || (config[3] & 0xFF) != FIRA_OOB_WIFI_OUI_1
                || (config[4] & 0xFF) != FIRA_OOB_WIFI_OUI_2) {
            throw new IllegalArgumentException("Invalid FiRa OOB WiFi VSE header.");
        }
        if (((config[5] & 0xF0) >> 4) != FIRA_SUB_ELEMENT_TYPE_UWB_CONFIG) {
            throw new IllegalArgumentException("Unsupported FiRa Sub-Element type.");
        }

        // Validate sub-element
        int subElementLength = config[5] & 0x0F;
        int subElementDataOffset = 6;
        if (subElementLength == 0x0F) {
            // parse extra bytes for length extension
            int lengthExtensionOffset = subElementDataOffset;
            int lengthExtension = config[lengthExtensionOffset++] & 0xFF;
            while (lengthExtension == 0xFF && lengthExtensionOffset < totalLength) {
                subElementLength += lengthExtension;
                lengthExtension = config[lengthExtensionOffset++] & 0xFF;
            }
            subElementLength += lengthExtension;
            // update offset for sub-element data
            subElementDataOffset = lengthExtensionOffset;
        }
        if ((subElementDataOffset + subElementLength) > totalLength) {
            throw new IllegalArgumentException(
                    "Not enough bytes for UWB Configuration Sub-Element content.");
        }
        if (subElementLength < FIRA_OOB_UWB_CONFIGURATION_HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid UWB Configuration Sub-Element length.");
        }
        if ((config[subElementDataOffset] & 0xFF) != FIRA_UWB_PROFILE_ID) {
            throw new IllegalArgumentException("Invalid UWB Configuration Sub-Element header.");
        }

        // mandatory fields
        Integer sessionId = null;

        // configurable fields with default values
        Short channelNumber = null;
        byte[] deviceMacAddress = null;
        Integer slotDuration = null;
        Long rangingDuration = null;
        Short preambleCodeIndex = null;
        Short slotsPerRangingRound = null;
        byte[] vendorId = null;
        byte[] staticStsIv = null;

        int offset = subElementDataOffset + FIRA_OOB_UWB_CONFIGURATION_HEADER_LENGTH;
        while (offset + 1 < totalLength) {
            int tag = config[offset++] & 0xFF;
            int length = config[offset++] & 0xFF;

            if (offset + length > totalLength) {
                throw new IllegalArgumentException(
                        "Not enough bytes for UWB Configuration Parameter List content.");
            }

            // Helper to read Little Endian values
            ByteBuffer buffer = ByteBuffer.wrap(config, offset, length).order(
                    ByteOrder.LITTLE_ENDIAN);

            switch (tag) {
                case TAG_CHANNEL_NUMBER -> {
                    if (length != 1) {
                        throw new IllegalArgumentException("Invalid length for CHANNEL_NUMBER.");
                    }
                    channelNumber = (short) (buffer.get() & 0xFF);
                }
                case TAG_DEVICE_MAC_ADDRESS -> {
                    if (length != UwbAddress.SHORT_ADDRESS_BYTE_LENGTH
                            && length != UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH) {
                        throw new IllegalArgumentException(
                                "Invalid length for DEVICE_MAC_ADDRESS.");
                    }
                    deviceMacAddress = new byte[length];
                    buffer.get(deviceMacAddress);
                }
                case TAG_SLOT_DURATION -> {
                    if (length != 2) {
                        throw new IllegalArgumentException("Invalid length for SLOT_DURATION.");
                    }
                    slotDuration = buffer.getShort() & 0xFFFF; // Reads 2 bytes as LE
                }
                case TAG_RANGING_DURATION -> {
                    if (length != 4) {
                        throw new IllegalArgumentException("Invalid length for RANGING_DURATION.");
                    }
                    rangingDuration = buffer.getInt() & 0xFFFFFFFFL; // Reads 4 bytes as LE
                }
                case TAG_PREAMBLE_CODE_INDEX -> {
                    if (length != 1) {
                        throw new IllegalArgumentException(
                                "Invalid length for PREAMBLE_CODE_INDEX.");
                    }
                    preambleCodeIndex = (short) (buffer.get() & 0xFF);
                }
                case TAG_SLOTS_PER_RR -> {
                    if (length != 1) {
                        throw new IllegalArgumentException("Invalid length for SLOTS_PER_RR.");
                    }
                    slotsPerRangingRound = (short) (buffer.get() & 0xFF);
                }
                case TAG_VENDOR_ID -> {
                    if (length != 2) {
                        throw new IllegalArgumentException("Invalid length for VENDOR_ID.");
                    }
                    vendorId = new byte[length];
                    buffer.get(vendorId);
                }
                case TAG_STATIC_STS_IV -> {
                    if (length != 6) {
                        throw new IllegalArgumentException("Invalid length for STATIC_STS_IV.");
                    }
                    staticStsIv = new byte[length];
                    buffer.get(staticStsIv);
                }
                case TAG_SESSION_ID -> {
                    if (length != 4) {
                        throw new IllegalArgumentException("Invalid length for SESSION_ID.");
                    }
                    sessionId = buffer.getInt(); // Reads 4 bytes as LE
                }
                default -> {
                    // Skip unknown tags
                }
            }
            // Move offset past the value
            offset += length;
        }

        if (sessionId == null) {
            throw new IllegalArgumentException(
                    "Missing SESSION_ID parameter in UWB Configuration Parameter List.");
        }

        Builder builder = new Builder(sessionId);

        if (channelNumber != null || preambleCodeIndex != null) {
            int channel = channelNumber == null
                    ? UwbConstants.DEFAULT_DLTDOA_CHANNEL_9 : channelNumber;
            int preambleIndex = preambleCodeIndex == null
                    ? UwbConstants.DEFAULT_DLTDOA_PREAMBLE_INDEX_10 : preambleCodeIndex;
            builder.setComplexChannel(new UwbComplexChannel.Builder()
                    .setChannel(channel)
                    .setPreambleIndex(preambleIndex)
                    .build());
        }

        if (deviceMacAddress != null) {
            builder.setDeviceAddress(UwbAddress.fromBytes(deviceMacAddress));
        }

        if (slotDuration != null) {
            builder.setSlotDuration(slotDuration / RSTU_PER_MS);
        }

        if (rangingDuration != null) {
            builder.setRangingIntervalMillis(rangingDuration.intValue());
        }

        if (slotsPerRangingRound != null) {
            builder.setSlotsPerRangingRound(slotsPerRangingRound);
        }

        if (vendorId != null && staticStsIv != null) {
            byte[] sessionKeyInfo = new byte[vendorId.length + staticStsIv.length];
            System.arraycopy(vendorId, 0, sessionKeyInfo, 0, vendorId.length);
            System.arraycopy(staticStsIv, 0, sessionKeyInfo, vendorId.length, staticStsIv.length);
            builder.setSessionKeyInfo(sessionKeyInfo);
        }

        if (rangingRoundIndexes != null) {
            builder.setRangingRoundIndexes(rangingRoundIndexes);
        }

        return builder.build();
    }

    /**
     * Gets the session ID.
     *
     * @return The session ID as an integer.
     */
    public int getSessionId() {
        return mSessionId;
    }

    /**
     * Gets the UWB address of the device.
     *
     * @return The {@link UwbAddress} of the device.
     */
    @NonNull
    public UwbAddress getDeviceAddress() {
        return mDeviceAddress;
    }

    /**
     * Gets the session key information.
     *
     * @return A byte array containing session key info, or null if not available.
     */
    @Nullable
    public byte[] getSessionKeyInfo() {
        return mSessionKeyInfo == null ? null : Arrays.copyOf(mSessionKeyInfo,
                mSessionKeyInfo.length);
    }

    /**
     * Gets the complex channel used for the session.
     *
     * @return A {@link UwbComplexChannel} object containing channel and preamble index.
     */
    @NonNull
    public UwbComplexChannel getComplexChannel() {
        return mComplexChannel;
    }

    /**
     * Gets the ranging interval in milliseconds.
     *
     * @return The ranging interval in milliseconds.
     */
    public int getRangingIntervalMillis() {
        return mRangingIntervalMs;
    }

    /**
     * Gets the slot duration.
     *
     * @return The slot duration.
     */
    @SlotDuration
    public int getSlotDuration() {
        return mSlotDuration;
    }

    /**
     * Gets the number of slots per ranging round.
     *
     * @return The number of slots per ranging round.
     */
    public int getSlotsPerRangingRound() {
        return mSlotsPerRangingRound;
    }

    /**
     * Gets the active ranging round indexes.
     */
    @Nullable
    public byte[] getRangingRoundIndexes() {
        return mRangingRoundIndexes == null ? null : Arrays.copyOf(mRangingRoundIndexes,
                mRangingRoundIndexes.length);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mSessionId);
        dest.writeParcelable(mDeviceAddress, flags);
        dest.writeByteArray(mSessionKeyInfo);
        dest.writeParcelable(mComplexChannel, flags);
        dest.writeInt(mRangingIntervalMs);
        dest.writeInt(mSlotDuration);
        dest.writeInt(mSlotsPerRangingRound);
        dest.writeByteArray(mRangingRoundIndexes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DlTdoaRangingParams)) return false;
        DlTdoaRangingParams that = (DlTdoaRangingParams) o;
        return mSessionId == that.mSessionId &&
                mRangingIntervalMs == that.mRangingIntervalMs &&
                mSlotDuration == that.mSlotDuration &&
                mSlotsPerRangingRound == that.mSlotsPerRangingRound &&
                Objects.equals(mDeviceAddress, that.mDeviceAddress) &&
                Arrays.equals(mSessionKeyInfo, that.mSessionKeyInfo) &&
                Objects.equals(mComplexChannel, that.mComplexChannel) &&
                Arrays.equals(mRangingRoundIndexes, that.mRangingRoundIndexes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mSessionId, mDeviceAddress, mComplexChannel, mRangingIntervalMs,
                mSlotDuration, mSlotsPerRangingRound);
        result = 31 * result + Arrays.hashCode(mSessionKeyInfo);
        result = 31 * result + Arrays.hashCode(mRangingRoundIndexes);
        return result;
    }

    @Override
    public String toString() {
        return "DlTdoaRangingParams{"
                + "mSessionId=" + mSessionId +
                ", mDeviceAddress=" + mDeviceAddress +
                ", mSessionKeyInfo=" + Arrays.toString(mSessionKeyInfo) +
                ", mComplexChannel=" + mComplexChannel +
                ", mRangingIntervalMs=" + mRangingIntervalMs +
                ", mSlotDuration=" + mSlotDuration +
                ", mSlotsPerRangingRound=" + mSlotsPerRangingRound +
                ", mRangingRoundIndexes=" + Arrays.toString(mRangingRoundIndexes) +
                '}';
    }

    /**
     * Builder for {@link DlTdoaRangingParams}.
     */
    public static final class Builder {
        private final int mSessionId;
        private UwbAddress mDeviceAddress = UwbAddress.createRandomShortAddress();
        private byte[] mSessionKeyInfo = UwbConstants.DEFAULT_DLTDOA_SESSION_KEY_INFO.clone();
        private UwbComplexChannel mComplexChannel =
                new UwbComplexChannel.Builder()
                        .setChannel(UwbConstants.DEFAULT_DLTDOA_CHANNEL_9)
                        .setPreambleIndex(UwbConstants.DEFAULT_DLTDOA_PREAMBLE_INDEX_10)
                        .build();
        private int mRangingIntervalMs = UwbConstants.DEFAULT_DLTDOA_RANGING_INTERVAL_200_MS;
        @SlotDuration
        private int mSlotDuration = UwbConstants.DEFAULT_DLTDOA_SLOT_DURATION_2_MS;
        private int mSlotsPerRangingRound = UwbConstants.DEFAULT_DLTDOA_SLOTS_PER_RANGING_ROUND_25;
        private byte[] mRangingRoundIndexes =
                UwbConstants.DEFAULT_DLTDOA_RANGING_ROUND_INDEXES.clone();

        /**
         * Constructor for the Builder.
         * @param sessionId The session ID.
         */
        public Builder(int sessionId) {
            mSessionId = sessionId;
        }

        /**
         * Sets the UWB address of the device.
         *
         * <p>If not set, a random short address is used as default.
         *
         * @param deviceAddress The UWB address of the device.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setDeviceAddress(@NonNull UwbAddress deviceAddress) {
            Objects.requireNonNull(deviceAddress);
            mDeviceAddress = deviceAddress;
            return this;
        }

        /**
         * Sets the session key information.
         *
         * <p>If not set, {@code {7, 8, 1, 2, 3, 4, 5, 6}} is used as default.
         *
         * @param sessionKeyInfo The session key information.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setSessionKeyInfo(@NonNull byte[] sessionKeyInfo) {
            mSessionKeyInfo = Objects.requireNonNull(sessionKeyInfo);
            return this;
        }

        /**
         * Sets the complex channel.
         *
         * <p>If not set, a default channel with channel 9 and preamble index 10 is used.
         *
         * @param complexChannel The complex channel.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setComplexChannel(@NonNull UwbComplexChannel complexChannel) {
            mComplexChannel = Objects.requireNonNull(complexChannel);
            return this;
        }

        /**
         * Sets the ranging interval in milliseconds.
         *
         * <p>If not set, 200ms is used as default.
         *
         * @param rangingIntervalMs The ranging interval in milliseconds.
         * @return this {@link Builder} instance.
         * @throws IllegalArgumentException if the ranging interval is not positive.
         */
        @NonNull
        public Builder setRangingIntervalMillis(@IntRange(from = 1) int rangingIntervalMs) {
            if (rangingIntervalMs <= 0) {
                throw new IllegalArgumentException("Ranging interval must be positive.");
            }
            mRangingIntervalMs = rangingIntervalMs;
            return this;
        }

        /**
         * Sets the slot duration.
         *
         * <p>If not set, {@link UwbRangingParams#DURATION_2_MS} is used as default.
         *
         * @param slotDuration The slot duration.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setSlotDuration(@SlotDuration int slotDuration) {
            mSlotDuration = slotDuration;
            return this;
        }

        /**
         * Sets the number of slots per ranging round.
         *
         * <p>If not set, 25 is used as default.
         *
         * @param slotsPerRangingRound The number of slots per ranging round.
         * @return this {@link Builder} instance.
         * @throws IllegalArgumentException if the slots per ranging round is not positive.
         */
        @NonNull
        public Builder setSlotsPerRangingRound(@IntRange(from = 1) int slotsPerRangingRound) {
            if (slotsPerRangingRound <= 0) {
                throw new IllegalArgumentException("Slots per ranging round must be positive.");
            }
            mSlotsPerRangingRound = slotsPerRangingRound;
            return this;
        }

        /**
         * Sets the active ranging round indexes.
         *
         * <p>If not set, {@code {0}} is used as default.
         *
         * @param rangingRoundIndexes The active ranging round indexes.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setRangingRoundIndexes(@NonNull byte[] rangingRoundIndexes) {
            mRangingRoundIndexes = Objects.requireNonNull(rangingRoundIndexes);
            return this;
        }

        /**
         * Builds the {@link DlTdoaRangingParams} instance.
         *
         * @return The {@link DlTdoaRangingParams} instance.
         */
        @NonNull
        public DlTdoaRangingParams build() {
            return new DlTdoaRangingParams(this);
        }
    }
}
