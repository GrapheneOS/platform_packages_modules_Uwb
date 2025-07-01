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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.uwb.LogicalLinkCreationParams.LinkLayerMode;

import com.android.uwb.flags.Flags;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents the parameters for the LOGICAL_LINK_UWBS_CREATE_NTF, which is used
 * by a FiRa Controller to notify the Host about an incoming Logical Link connection
 * from a remote device.
 *
 * This object contains the Connect ID, Link Layer Mode, and source address of
 * the initiating remote controller.
 *
 * Implements {@link Parcelable} for IPC transactions.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_UWB_FIRA_3_0_25Q4)
public final class LogicalLinkConnectionRequest implements Parcelable {
    private final int mConnectId;
    @LogicalLinkCreationParams.LinkLayerMode
    private final int mLinkLayerModeSelector;
    private final byte[] mSourceAddress;

    private LogicalLinkConnectionRequest(Builder builder) {
        mConnectId = builder.mConnectId;
        mLinkLayerModeSelector = builder.mLinkLayerModeSelector;
        mSourceAddress = builder.mSourceAddress;
    }

    /**
     * Returns the Logical Link Connect ID associated with this request.
     *
     * @return Logical Link Connect ID.
     */
    public int getConnectId() {
        return mConnectId;
    }

    /**
     * Returns the Link Layer Mode Selector value for this connection request.
     * <p>
     * The value corresponds to a mode such as connection-oriented or connectionless,
     * secure or non-secure.
     *
     * @return Link Layer Mode Selector value.
     */
    @LinkLayerMode
    public int getLinkLayerModeSelector() {
        return mLinkLayerModeSelector;
    }

    /**
     * Returns the source address (UWB address) of the remote controller
     * initiating the logical link.
     *
     * @return A {@link UwbAddress} representing the source address of the remote device.
     */
    @NonNull
    public UwbAddress getSourceAddress() {
        return UwbAddress.fromBytes(mSourceAddress);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(mSourceAddress);
        dest.writeInt(mConnectId);
        dest.writeInt(mLinkLayerModeSelector);
    }

    public static final @NonNull Creator<LogicalLinkConnectionRequest> CREATOR =
            new Creator<>() {
                @Override
                public LogicalLinkConnectionRequest createFromParcel(Parcel in) {
                    byte[] sourceAddress = in.createByteArray();
                    if (sourceAddress == null) {
                        throw new IllegalArgumentException("sourceAddress in parcel is null");
                    }
                    return new Builder(
                            in.readInt(),
                            in.readInt(),
                            UwbAddress.fromBytes(sourceAddress)
                    ).build();
                }

                @Override
                public LogicalLinkConnectionRequest[] newArray(int size) {
                    return new LogicalLinkConnectionRequest[size];
                }
            };

    /**
     * Builder for {@link LogicalLinkConnectionRequest} object.
     */
    public static final class Builder {
        private byte[] mSourceAddress = new byte[UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH];
        private int mConnectId;
        @LinkLayerMode
        private int mLinkLayerModeSelector;

        /**
         *  Creates a new {@link Builder} for constructing a {@link LogicalLinkConnectionRequest}.
         *
         * @param connectId The Logical Link Connection ID associated with the request.
         * @param linkLayerModeSelector The link layer mode indicating the type and security of the
         *          logical link. Must be one of the values defined in {@link LinkLayerMode}.
         * @param sourceAddress The UWB source address of the remote controller initiating the
         *          logical link.
         */
        public Builder(int connectId, @LinkLayerMode int linkLayerModeSelector,
                       @NonNull UwbAddress sourceAddress) {
            this.mConnectId = connectId;
            this.mLinkLayerModeSelector = linkLayerModeSelector;
            this.mSourceAddress = sourceAddress.toBytes();
        }

        /**
         * Builds the {@link LogicalLinkConnectionRequest} instance.
         */
        @NonNull
        public LogicalLinkConnectionRequest build() {
            return new LogicalLinkConnectionRequest(this);
        }
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LogicalLinkConnectionRequest)) return false;
        LogicalLinkConnectionRequest other = (LogicalLinkConnectionRequest) obj;
        return mConnectId == other.mConnectId
                && mLinkLayerModeSelector == other.mLinkLayerModeSelector
                && Arrays.equals(mSourceAddress, other.mSourceAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mSourceAddress), mConnectId, mLinkLayerModeSelector);
    }

    @Override
    public String toString() {
        return "LogicalLinkConnectionRequest{"
                + "sourceAddress=" + UwbAddress.fromBytes(mSourceAddress)
                + ", connectId=" + mConnectId
                + ", linkLayerModeSelector=" + mLinkLayerModeSelector
                + '}';
    }
}
