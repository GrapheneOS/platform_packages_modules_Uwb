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

import androidx.annotation.IntDef;

import com.android.uwb.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents the parameters for the LOGICAL_LINK_CREATE_CMD, which is used by a FiRa Controller to
 * establish a data connection with a remote device.
 *
 * Implements {@link Parcelable} for IPC transactions.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_UWB_FIRA_3_0_25Q4)
public final class LogicalLinkCreationParams implements Parcelable {
    private final byte[] mDestinationAddress;
    private final int mLogicalLinkClassLength;

    // Table 39: Link Layer Mode Selector values
    /**
     * @hide
     */
    @IntDef(
        value = {
            LINK_LAYER_MODE_CONNECTIONLESS_NON_SECURE,
            LINK_LAYER_MODE_CONNECTIONLESS_SECURE,
            LINK_LAYER_MODE_CONNECTION_ORIENTED_NON_SECURE,
            LINK_LAYER_MODE_CONNECTION_ORIENTED_SECURE,
            LINK_LAYER_MODE_CONNECTIONLESS_UWBS_TO_UWBS,
            LINK_LAYER_MODE_CONNECTION_ORIENTED_UWBS_UWBS,
        })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LinkLayerMode {}

    /**
     * Connectionless mode with no security.
     */
    public static final int LINK_LAYER_MODE_CONNECTIONLESS_NON_SECURE = 0x00;

    /**
     * Connectionless mode with security enabled.
     */
    public static final int LINK_LAYER_MODE_CONNECTIONLESS_SECURE = 0x01;

    /**
     * Connection-oriented mode with no security.
     */
    public static final int LINK_LAYER_MODE_CONNECTION_ORIENTED_NON_SECURE = 0x02;

    /**
     * Connection-oriented mode with security enabled.
     */
    public static final int LINK_LAYER_MODE_CONNECTION_ORIENTED_SECURE = 0x03;

    /**
     * Connectionless mode for UWBS-to-UWBS communication.
     */
    public static final int LINK_LAYER_MODE_CONNECTIONLESS_UWBS_TO_UWBS = 0x04;

    /**
     * Connection-oriented mode for UWBS-to-UWBS communication.
     */
    public static final int LINK_LAYER_MODE_CONNECTION_ORIENTED_UWBS_UWBS = 0x05;

    @LinkLayerMode private final int mLinkLayerModeSelector;

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            LOGICAL_LINK_STATUS_OK,
            LOGICAL_LINK_STATUS_FAILED,
    })
    @interface LogicalLinkStatusCode {}

    /**
     * Indicates that the logical link creation was successful.
     */
    public static final int LOGICAL_LINK_STATUS_OK = 0;

    /**
     * Indicates that the logical link creation failed.
     */
    public static final int LOGICAL_LINK_STATUS_FAILED = 1;

    /**
     * Reason for a Logical Link closure as indicated in Table 47.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
        LOGICAL_LINK_CLOSE_REASON_REMOTE,
        LOGICAL_LINK_CLOSE_REASON_TIMEOUT,
        LOGICAL_LINK_CLOSE_REASON_TRANSMISSION_ERROR,
        LOGICAL_LINK_CLOSE_REASON_SECURE_COMPONENT,
        LOGICAL_LINK_CLOSE_REASON_UNKNOWN,
        LOGICAL_LINK_CLOSE_REASON_HOST,
    })
    @interface LogicalLinkClosureReason {}

    /**
     * The logical link was terminated by the remote device.
     */
    public static final int LOGICAL_LINK_CLOSE_REASON_REMOTE = 0x00;

    /**
     * The logical link was inactive for longer than the configured timeout period.
     */
    public static final int LOGICAL_LINK_CLOSE_REASON_TIMEOUT = 0x01;

    /**
     * The logical link was terminated due to unrecoverable transmission errors.
     */
    public static final int LOGICAL_LINK_CLOSE_REASON_TRANSMISSION_ERROR = 0x02;

    /**
     * The logical link was terminated by the secure component.
     */
    public static final int LOGICAL_LINK_CLOSE_REASON_SECURE_COMPONENT = 0x03;

    /**
     * The logical link was terminated due to an unknown reason.
     */
    public static final int LOGICAL_LINK_CLOSE_REASON_UNKNOWN = 0x04;

    /**
     * The logical link was explicitly terminated by the host.
     */
    public static final int LOGICAL_LINK_CLOSE_REASON_HOST = 0x05;

    /** Indicates that no specific Logical Link Connection ID is provided. */
    public static final int CONNECT_ID_UNSPECIFIED = -1;

    private LogicalLinkCreationParams(Builder builder) {
        mLinkLayerModeSelector = builder.mLinkLayerModeSelector;
        mDestinationAddress = builder.mDestinationAddress;
        mLogicalLinkClassLength = builder.mLogicalLinkClassLength;
    }

    /**
     * Returns the selected Link Layer Mode for the logical link.
     *
     * @return The link layer mode selector {@link LinkLayerMode}
     */
    @LinkLayerMode
    public int getLinkLayerModeSelector() {
        return mLinkLayerModeSelector;
    }

    /**
     * Returns the destination MAC address for the logical link.
     *
     * <p>This address identifies the peer device involved in the logical link communication.</p>
     *
     * @return A byte array representing the destination MAC address. Must not be null.
     */
    @NonNull
    public byte[] getDestinationAddress() {
        return mDestinationAddress;
    }

    /**
     * Returns the logical link class length.
     *
     * <p>This value represents the class length (in bytes) associated with the logical link.</p>
     *
     * @return The class length of the logical link.
     */
    public int getLogicalLinkClassLength() {
        return mLogicalLinkClassLength;
    }

    /**
     * @hide
     */
    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LogicalLinkCreationParams) {
            LogicalLinkCreationParams other = (LogicalLinkCreationParams) obj;
            return mLinkLayerModeSelector == other.mLinkLayerModeSelector
                    && Arrays.equals(mDestinationAddress, other.mDestinationAddress)
                    && mLogicalLinkClassLength == other.mLogicalLinkClassLength;
        }
        return false;
    }

    /**
     * @hide
     */
    @Override
    public int hashCode() {
        return Objects.hash(mLinkLayerModeSelector, Arrays.hashCode(mDestinationAddress),
                mLogicalLinkClassLength);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    @Override
    public String toString() {
        return "LogicalLinkCreationParams{"
                + "linkLayerModeSelector=" + mLinkLayerModeSelector
                + ", destinationAddress=" + UwbAddress.fromBytes(mDestinationAddress)
                + ", logicalLinkClassLength=" + mLogicalLinkClassLength
                + '}';
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mLinkLayerModeSelector);
        dest.writeByteArray(mDestinationAddress);
        dest.writeInt(mLogicalLinkClassLength);
    }

    public static final @NonNull Creator<LogicalLinkCreationParams> CREATOR =
            new Creator<LogicalLinkCreationParams>() {
                @Override
                public LogicalLinkCreationParams createFromParcel(Parcel in) {
                    return new Builder(in.readInt(), UwbAddress.fromBytes(in.createByteArray()))
                            .setLogicalLinkClassLength(in.readInt())
                            .build();
                }

                @Override
                public LogicalLinkCreationParams[] newArray(int size) {
                    return new LogicalLinkCreationParams[size];
                }
            };

    /**
     * Builder for {@link LogicalLinkCreationParams} object
    */
    public static final class Builder {
        @LinkLayerMode
        private int mLinkLayerModeSelector;
        private byte[] mDestinationAddress = new byte[UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH];
        private int mLogicalLinkClassLength = 0;

        /**
         * Constructor for the {@link Builder} class.
         *
         * @param linkLayerModeSelector The mode selector value.
         * @param destinationAddress The destination address, must be a valid {@link UwbAddress}.
         */
        public Builder(@LinkLayerMode int linkLayerModeSelector,
                @NonNull UwbAddress destinationAddress) {
            mLinkLayerModeSelector = linkLayerModeSelector;
            byte[] addressBytes = destinationAddress.toBytes();

            if (addressBytes.length == UwbAddress.SHORT_ADDRESS_BYTE_LENGTH) {
                // Ensure octets 2-7 are set to 0x00 for short addresses
                byte[] extendedAddress = new byte[UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH];
                System.arraycopy(addressBytes, 0, extendedAddress, 0,
                        UwbAddress.SHORT_ADDRESS_BYTE_LENGTH);
                mDestinationAddress = extendedAddress;
            } else {
                mDestinationAddress = addressBytes;
            }
        }

        /**
         * Sets the logical link class length.
         * The default value is 0 if not explicitly set.
         *
         * @param logicalLinkClassLength The logical link class length value.
         * @return The {@link Builder} instance for method chaining.
         */
        @NonNull
        public Builder setLogicalLinkClassLength(int logicalLinkClassLength) {
            mLogicalLinkClassLength = logicalLinkClassLength;
            return this;
        }

        @NonNull
        public LogicalLinkCreationParams build() {
            return new LogicalLinkCreationParams(this);
        }
    }
}
