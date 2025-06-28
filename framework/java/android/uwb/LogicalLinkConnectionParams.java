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

package android.uwb;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.uwb.LogicalLinkCreationParams.LogicalLinkStatusCode;

import com.android.uwb.flags.Flags;

import java.util.Objects;

/**
 * Logical link parameters associated with a particular UWB connection identifier.
*
* <p>This class provides access to Logical Link layer parameters returned from the UWB stack.
*
* <p>Implements {@link Parcelable} for IPC transactions.
*
* @hide
*/
@SystemApi
@FlaggedApi(Flags.FLAG_UWB_FIRA_3_0_25Q4)
public final class LogicalLinkConnectionParams implements Parcelable {

    /**
     * Control field parameter for the given logical link connection.
     *
     * @hide
     */
    @IntDef(prefix = "CONTROL_FIELD_", value = {
            CONTROL_FIELD_MAX_LL_SDU_SIZE,
            CONTROL_FIELD_MAX_LL_PDU_SIZE,
            CONTROL_FIELD_TRANSMIT_WINDOW_SIZE,
            CONTROL_FIELD_RECEIVE_WINDOW_SIZE,
            CONTROL_FIELD_REPEAT_COUNT_MAX,
            CONTROL_FIELD_LINK_TIMEOUT,
            CONTROL_FIELD_PORT
    })
    public @interface ControlField {}

    /**
     * Indicates that the Max LL SDU Size parameter is present.
     */
    public static final int CONTROL_FIELD_MAX_LL_SDU_SIZE = 0x01;

    /**
     * Indicates that the Max LL PDU Size parameter is present.
     */
    public static final int CONTROL_FIELD_MAX_LL_PDU_SIZE = 0x02;

    /**
     * Indicates that the Transmit Window Size parameter is present.
     */
    public static final int CONTROL_FIELD_TRANSMIT_WINDOW_SIZE = 0x04;

    /**
     * Indicates that the Receive Window Size parameter is present.
     */
    public static final int CONTROL_FIELD_RECEIVE_WINDOW_SIZE = 0x08;

    /**
     * Indicates that the Repeat Count Max parameter is present.
     */
    public static final int CONTROL_FIELD_REPEAT_COUNT_MAX = 0x10;

    /**
     * Indicates that the Link Timeout parameter is present.
     */
    public static final int CONTROL_FIELD_LINK_TIMEOUT = 0x20;

    /**
     * Indicates that the Port parameter is present.
     */
    public static final int CONTROL_FIELD_PORT = 0x40;

    private final int mStatus;
    private final int mControlField;
    private final int mMaxLinkLayerSduSize;
    private final int mMaxLinkLayerPduSize;
    private final int mTransmitWindowSize;
    private final int mReceiveWindowSize;
    private final int mRepeatCountMax;
    private final int mLinkTimeout;
    private final int mDestinationPort;
    private final int mSourcePort;

    private LogicalLinkConnectionParams(Builder builder) {
        mStatus = builder.mStatus;
        mControlField = builder.mControlField;
        mMaxLinkLayerSduSize = builder.mMaxLinkLayerSduSize;
        mMaxLinkLayerPduSize = builder.mMaxLinkLayerPduSize;
        mTransmitWindowSize = builder.mTransmitWindowSize;
        mReceiveWindowSize = builder.mReceiveWindowSize;
        mRepeatCountMax = builder.mRepeatCountMax;
        mLinkTimeout = builder.mLinkTimeout;
        mDestinationPort = builder.mDestinationPort;
        mSourcePort = builder.mSourcePort;
    }

    /**
     * Returns the status of the LOGICAL_LINK_GET_PARAM_RSP.
    */
    @LogicalLinkStatusCode
    public int getStatus() {
        return mStatus;
    }

    /** Returns true if Max LL SDU Size is present. */
    public boolean hasMaxLinkLayerSduSize() {
        return (mControlField & CONTROL_FIELD_MAX_LL_SDU_SIZE) != 0;
    }

    /**
     * Returns the maximum Logical Link SDU size.
     * <p><strong>Note:</strong> Call {@link #hasMaxLinkLayerSduSize()} before invoking this method
     * to ensure the parameter is available.
     */
    public int getMaxLinkLayerSduSize() {
        return mMaxLinkLayerSduSize;
    }

    /** Returns true if maximum link layer PDU Size is present. */
    public boolean hasMaxLinkLayerPduSize() {
        return (mControlField & CONTROL_FIELD_MAX_LL_PDU_SIZE) != 0;
    }

    /**
     * Returns the maximum Logical Link PDU size.
     * <p><strong>Note:</strong> Call {@link #hasMaxLinkLayerPduSize()} before invoking this method
     * to ensure the parameter is available.
     */
    public int getMaxLinkLayerPduSize() {
        return mMaxLinkLayerPduSize;
    }

    /** Returns true if transmit window size is present. */
    public boolean hasTransmitWindowSize() {
        return (mControlField & CONTROL_FIELD_TRANSMIT_WINDOW_SIZE) != 0;
    }

    /**
     * Returns the transmit window size in number of PDUs.
     * <p><strong>Note:</strong> Call {@link #hasTransmitWindowSize()} before invoking this method
     * to ensure the parameter is available.
     */
    public int getTransmitWindowSize() {
        return mTransmitWindowSize;
    }

    /** Returns true if receive window size is present. */
    public boolean hasReceiveWindowSize() {
        return (mControlField & CONTROL_FIELD_RECEIVE_WINDOW_SIZE) != 0;
    }

    /**
     * Returns the receive window size in number of PDUs.
     * <p><strong>Note:</strong> Call {@link #hasReceiveWindowSize()} before invoking this method to
     * ensure the parameter is available.
     */
    public int getReceiveWindowSize() {
        return mReceiveWindowSize;
    }

    /** Returns true if repeat count max is present. */
    public boolean hasRepeatCountMax() {
        return (mControlField & CONTROL_FIELD_REPEAT_COUNT_MAX) != 0;
    }

    /**
     * Returns the maximum repeat count used for retransmissions.
     * <p><strong>Note:</strong> Call {@link #hasRepeatCountMax()} before invoking this method to
     * ensure the parameter is available.
     */
    public int getRepeatCountMax() {
        return mRepeatCountMax;
    }

    /** Returns true if link timeout is present. */
    public boolean hasLinkTimeout() {
        return (mControlField & CONTROL_FIELD_LINK_TIMEOUT) != 0;
    }

    /**
     * Returns the Link Timeout value, used to detect connection loss.
     * <p><strong>Note:</strong> Call {@link #hasLinkTimeout()} before invoking this method to
     * ensure the parameter is available.
     */
    public int getLinkTimeout() {
        return mLinkTimeout;
    }

    /** Returns true if destination port is present. */
    public boolean hasDestinationPort() {
        return (mControlField & CONTROL_FIELD_PORT) != 0;
    }

    /**
     * Returns the Logical Link destination port number.
     * <p><strong>Note:</strong> Call {@link #hasDestinationPort()} before invoking this method to
     * ensure the parameter is available.
     */
    public int getDestinationPort() {
        return mDestinationPort;
    }

    /** Returns true if source port is present. */
    public boolean hasSourcePort() {
        return (mControlField & CONTROL_FIELD_PORT) != 0;
    }

    /**
     * Returns the Logical Link source port number.
     * <p><strong>Note:</strong> Call {@link #hasSourcePort()} before invoking this method to
     * ensure the parameter is available.
     */
    public int getSourcePort() {
        return mSourcePort;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mStatus);
        dest.writeInt(mControlField);
        dest.writeInt(mMaxLinkLayerSduSize);
        dest.writeInt(mMaxLinkLayerPduSize);
        dest.writeInt(mTransmitWindowSize);
        dest.writeInt(mReceiveWindowSize);
        dest.writeInt(mRepeatCountMax);
        dest.writeInt(mLinkTimeout);
        dest.writeInt(mDestinationPort);
        dest.writeInt(mSourcePort);
    }

    public static final @NonNull Creator<LogicalLinkConnectionParams> CREATOR =
            new Creator<>() {
                @Override
                public LogicalLinkConnectionParams createFromParcel(Parcel in) {
                    Builder builder = new Builder(in.readInt(), in.readInt());
                    builder.setMaxLinkLayerSduSize(in.readInt());
                    builder.setMaxLinkLayerPduSize(in.readInt());
                    builder.setTransmitWindowSize(in.readInt());
                    builder.setReceiveWindowSize(in.readInt());
                    builder.setRepeatCountMax(in.readInt());
                    builder.setLinkTimeout(in.readInt());
                    builder.setDestinationPort(in.readInt());
                    builder.setSourcePort(in.readInt());
                    return builder.build();
                }

                @Override
                public LogicalLinkConnectionParams[] newArray(int size) {
                    return new LogicalLinkConnectionParams[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LogicalLinkConnectionParams)) return false;
        LogicalLinkConnectionParams other = (LogicalLinkConnectionParams) obj;
        return mStatus == other.mStatus
                && mControlField == other.mControlField
                && mMaxLinkLayerSduSize == other.mMaxLinkLayerSduSize
                && mMaxLinkLayerPduSize == other.mMaxLinkLayerPduSize
                && mTransmitWindowSize == other.mTransmitWindowSize
                && mReceiveWindowSize == other.mReceiveWindowSize
                && mRepeatCountMax == other.mRepeatCountMax
                && mLinkTimeout == other.mLinkTimeout
                && mDestinationPort == other.mDestinationPort
                && mSourcePort == other.mSourcePort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mStatus,
                mControlField,
                mMaxLinkLayerSduSize,
                mMaxLinkLayerPduSize,
                mTransmitWindowSize,
                mReceiveWindowSize,
                mRepeatCountMax,
                mLinkTimeout,
                mDestinationPort,
                mSourcePort);
    }

    @Override
    public String toString() {
        return "LogicalLinkConnectionParams{"
                + "status=" + mStatus
                + ", controlField=" + mControlField
                + ", maxLinkLayerSduSize=" + mMaxLinkLayerSduSize
                + ", maxLinkLayerPduSize=" + mMaxLinkLayerPduSize
                + ", transmitWindowSize=" + mTransmitWindowSize
                + ", receiveWindowSize=" + mReceiveWindowSize
                + ", repeatCountMax=" + mRepeatCountMax
                + ", linkTimeout=" + mLinkTimeout
                + ", destinationPort=" + mDestinationPort
                + ", sourcePort=" + mSourcePort
                + '}';
    }

    /** Builder for {@link LogicalLinkConnectionParams} */
    public static final class Builder {
        private final int mStatus;
        private final int mControlField;
        private int mMaxLinkLayerSduSize;
        private int mMaxLinkLayerPduSize;
        private int mTransmitWindowSize;
        private int mReceiveWindowSize;
        private int mRepeatCountMax;
        private int mLinkTimeout;
        private int mDestinationPort;
        private int mSourcePort;

        public Builder(int status, int controlField) {
            mStatus = status;
            mControlField = controlField;
        }

        @NonNull
        public Builder setMaxLinkLayerSduSize(int value) {
            mMaxLinkLayerSduSize = value;
            return this;
        }

        @NonNull
        public Builder setMaxLinkLayerPduSize(int value) {
            mMaxLinkLayerPduSize = value;
            return this;
        }

        @NonNull
        public Builder setTransmitWindowSize(int value) {
            mTransmitWindowSize = value;
            return this;
        }

        @NonNull
        public Builder setReceiveWindowSize(int value) {
            mReceiveWindowSize = value;
            return this;
        }

        @NonNull
        public Builder setRepeatCountMax(int value) {
            mRepeatCountMax = value;
            return this;
        }

        @NonNull
        public Builder setLinkTimeout(int value) {
            mLinkTimeout = value;
            return this;
        }

        @NonNull
        public Builder setDestinationPort(int value) {
            mDestinationPort = value;
            return this;
        }

        @NonNull
        public Builder setSourcePort(int value) {
            mSourcePort = value;
            return this;
        }

        @NonNull
        public LogicalLinkConnectionParams build() {
            return new LogicalLinkConnectionParams(this);
        }
    }
}
