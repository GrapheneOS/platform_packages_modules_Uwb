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

package android.uwb;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.uwb.UwbManager.AdapterStateCallback.State;

import java.util.Objects;

/**
 * Record of energy and activity information from controller and
 * underlying Uwb stack state. Timestamp the record with elapsed
 * real-time.
 * @hide
 */
public final class UwbActivityEnergyInfo implements Parcelable {
    private final long mTimeSinceBootMillis;
    private final @State int mStackState;
    private final long mControllerTxDurationMillis;
    private final long mControllerRxDurationMillis;
    private final long mControllerIdleDurationMillis;
    private final long mControllerWakeCount;

    private UwbActivityEnergyInfo(
            @ElapsedRealtimeLong long timeSinceBootMillis,
            @State int stackState,
            @IntRange(from = 0) long txDurationMillis,
            @IntRange(from = 0) long rxDurationMillis,
            @IntRange(from = 0) long idleDurationMillis,
            @IntRange(from = 0) long wakeCount) {
        mTimeSinceBootMillis = timeSinceBootMillis;
        mStackState = stackState;
        mControllerTxDurationMillis = txDurationMillis;
        mControllerRxDurationMillis = rxDurationMillis;
        mControllerIdleDurationMillis = idleDurationMillis;
        mControllerWakeCount = wakeCount;
    }

    /**
     * @hide
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof UwbActivityEnergyInfo) {
            UwbActivityEnergyInfo other = (UwbActivityEnergyInfo) obj;
            return mTimeSinceBootMillis == other.getTimeSinceBootMillis()
                    && mStackState == other.getStackState()
                    && mControllerTxDurationMillis == other.getControllerTxDurationMillis()
                    && mControllerRxDurationMillis == other.getControllerRxDurationMillis()
                    && mControllerIdleDurationMillis == other.getControllerIdleDurationMillis()
                    && mControllerWakeCount == other.getControllerWakeCount();
        }
        return false;
    }

    /**
     * @hide
     */
    @Override
    public int hashCode() {
        return Objects.hash(mTimeSinceBootMillis, mStackState, mControllerTxDurationMillis,
                mControllerRxDurationMillis, mControllerIdleDurationMillis, mControllerWakeCount);
    }

    @Override
    public String toString() {
        return "UwbActivityEnergyInfo{"
                + " mTimeSinceBootMillis=" + mTimeSinceBootMillis
                + " mStackState=" + mStackState
                + " mControllerTxDurationMillis=" + mControllerTxDurationMillis
                + " mControllerRxDurationMillis=" + mControllerRxDurationMillis
                + " mControllerIdleDurationMillis=" + mControllerIdleDurationMillis
                + " mControllerWakeCount=" + mControllerWakeCount
                + " }";
    }

    public static final @NonNull Creator<UwbActivityEnergyInfo> CREATOR =
            new Creator<UwbActivityEnergyInfo>() {
                @Override
                public UwbActivityEnergyInfo createFromParcel(Parcel in) {
                    long timestamp = in.readLong();
                    int stackState = in.readInt();
                    long txTime = in.readLong();
                    long rxTime = in.readLong();
                    long idleTime = in.readLong();
                    long wakeCount = in.readLong();
                    Builder builder = new Builder(timestamp, stackState, txTime,
                            rxTime, idleTime, wakeCount);
                    return builder.build();
                }
                @Override
                public UwbActivityEnergyInfo[] newArray(int size) {
                    return new UwbActivityEnergyInfo[size];
                }
            };

    @Override
    public void writeToParcel(@NonNull Parcel out, int flags) {
        out.writeLong(mTimeSinceBootMillis);
        out.writeInt(mStackState);
        out.writeLong(mControllerTxDurationMillis);
        out.writeLong(mControllerRxDurationMillis);
        out.writeLong(mControllerIdleDurationMillis);
        out.writeLong(mControllerWakeCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Get the timestamp (elapsed real time milliseconds since boot) of record creation. */
    @ElapsedRealtimeLong
    public long getTimeSinceBootMillis() {
        return mTimeSinceBootMillis;
    }

    /** Get the Uwb stack reported state. */
    @State
    public int getStackState() {
        return mStackState;
    }

    /** Get the Uwb transmission duration, in milliseconds. */
    @IntRange(from = 0)
    public long getControllerTxDurationMillis() {
        return mControllerTxDurationMillis;
    }

    /** Get the Uwb receive duration, in milliseconds. */
    @IntRange(from = 0)
    public long getControllerRxDurationMillis() {
        return mControllerRxDurationMillis;
    }

    /** Get the Uwb idle duration, in milliseconds. */
    @IntRange(from = 0)
    public long getControllerIdleDurationMillis() {
        return mControllerIdleDurationMillis;
    }

    /** Get the Uwb wakeup count. */
    @IntRange(from = 0)
    public long getControllerWakeCount() {
        return mControllerWakeCount;
    }

    /**
     * Builder for a {@link UwbActivityEnergyInfo} object.
     */
    public static final class Builder {
        private final long mTimeSinceBootMillis;
        private final @State int mStackState;
        private final long mControllerTxDurationMillis;
        private final long mControllerRxDurationMillis;
        private final long mControllerIdleDurationMillis;
        private final long mControllerWakeCount;

        /**
         * Construct a new builder for the {@link UwbActivityEnergyInfo} object
         * @param timeSinceBootMillis the elapsed real time since boot, in milliseconds
         * @param stackState the current state of the Uwb Stack
         * @param txDurationMillis cumulative milliseconds of active transmission
         * @param rxDurationMillis cumulative milliseconds of active receive
         * @param idleDurationMillis cumulative milliseconds when radio is awake but not
         *                           transmitting or receiving
         * @param wakeCount cumulative number of wakeup count for the radio
         */
        public Builder(@ElapsedRealtimeLong long timeSinceBootMillis,
                @State int stackState,
                @IntRange(from = 0) long txDurationMillis,
                @IntRange(from = 0) long rxDurationMillis,
                @IntRange(from = 0) long idleDurationMillis,
                @IntRange(from = 0) long wakeCount) {
            if (timeSinceBootMillis < 0) {
                throw new IllegalArgumentException("timeSinceBootMillis must be >= 0");
            }
            if (stackState != UwbManager.AdapterStateCallback.STATE_DISABLED
                    && stackState != UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE
                    && stackState != UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE) {
                throw new IllegalArgumentException("invalid UWB stack state");
            }
            if (txDurationMillis < 0) {
                throw new IllegalArgumentException("txDurationMillis must be >= 0");
            }
            if (rxDurationMillis < 0) {
                throw new IllegalArgumentException("rxDurationMillis must be >= 0");
            }
            if (idleDurationMillis < 0) {
                throw new IllegalArgumentException("idleDurationMillis must be >= 0");
            }
            if (wakeCount < 0) {
                throw new IllegalArgumentException("wakeCount must be >= 0");
            }
            mTimeSinceBootMillis = timeSinceBootMillis;
            mStackState = stackState;
            mControllerTxDurationMillis = txDurationMillis;
            mControllerRxDurationMillis = rxDurationMillis;
            mControllerIdleDurationMillis = idleDurationMillis;
            mControllerWakeCount = wakeCount;
        }

        /**
         * Build the {@link UwbActivityEnergyInfo} object
         */
        @NonNull
        public UwbActivityEnergyInfo build() {
            return new UwbActivityEnergyInfo(mTimeSinceBootMillis, mStackState,
                    mControllerTxDurationMillis, mControllerRxDurationMillis,
                    mControllerIdleDurationMillis, mControllerWakeCount);
        }
    }
}
