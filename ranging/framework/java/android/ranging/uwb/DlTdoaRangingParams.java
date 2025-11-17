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

import java.util.Arrays;
import java.util.Objects;

/**
 * Class to represent UWB Downlink TDoA ranging parameters.
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class DlTdoaRangingParams implements Parcelable {

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
        private byte[] mSessionKeyInfo;
        private UwbComplexChannel mComplexChannel = new UwbComplexChannel.Builder().build();
        private int mRangingIntervalMs = 200;
        @SlotDuration
        private int mSlotDuration = UwbRangingParams.DURATION_2_MS;
        private int mSlotsPerRangingRound = 25;
        private byte[] mRangingRoundIndexes;

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
