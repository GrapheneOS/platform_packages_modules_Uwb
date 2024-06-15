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

package com.google.uwb.support.fira;

import static com.google.uwb.support.fira.FiraParams.longToUwbAddress;
import static com.google.uwb.support.fira.FiraParams.uwbAddressToLong;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

/**
 * UWB parameters for removing a controlee from a FiRa session.
 */
public class FiraOnControleeRemovedParams {
    @IntDef(
        value = {
            Reason.UNKNOWN,
            Reason.LOST_CONNECTION,
            Reason.REQUESTED_BY_API,
        }
    )
    public @interface Reason {
        int UNKNOWN = 0;
        int LOST_CONNECTION = 1;
        int REQUESTED_BY_API = 2;
    }

    private static final String KEY_MAC_ADDRESS_MODE = "mac_address_mode";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_REASON = "reason";
    private final @NonNull UwbAddress mAddress;
    private final @Reason int mReason;

    private FiraOnControleeRemovedParams(@NonNull UwbAddress address, @Reason int reason) {
        mAddress = address;
        mReason = reason;
    }

    @SuppressWarnings("NewApi")
    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        int addressMode = mAddress.size() == UwbAddress.SHORT_ADDRESS_BYTE_LENGTH
                ? FiraParams.MAC_ADDRESS_MODE_2_BYTES : FiraParams.MAC_ADDRESS_MODE_8_BYTES;
        bundle.putInt(KEY_MAC_ADDRESS_MODE, addressMode);
        bundle.putLong(KEY_ADDRESS, uwbAddressToLong(mAddress));
        bundle.putInt(KEY_REASON, mReason);
        return bundle;
    }

    /**
     * @param bundle to build the params from.
     * @return the parameters stored within the bundle.
     */
    @SuppressWarnings("NewApi")
    public static FiraOnControleeRemovedParams fromBundle(PersistableBundle bundle) {
        int addressMode = bundle.getInt(KEY_MAC_ADDRESS_MODE);
        UwbAddress uwbAddress = longToUwbAddress(
                bundle.getLong(KEY_ADDRESS),
                addressMode == FiraParams.MAC_ADDRESS_MODE_2_BYTES
                        ? UwbAddress.SHORT_ADDRESS_BYTE_LENGTH
                        : UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH
        );
        return new Builder(uwbAddress).setReason(bundle.getInt(KEY_REASON)).build();
    }

    @NonNull
    public UwbAddress getAddress() {
        return mAddress;
    }

    public @Reason int getReason() {
        return mReason;
    }

    /** Param builder. **/
    public static class Builder {
        private final UwbAddress mAddress;
        private @Reason int mReason = Reason.UNKNOWN;

        /**
         * @param address that was removed.
         */
        public Builder(@NonNull UwbAddress address) {
            mAddress = address;
        }

        /**
         * @param reason for removal.
         */
        public FiraOnControleeRemovedParams.Builder setReason(@Reason int reason) {
            mReason = reason;
            return this;
        }

        /**
         * @return a {@link FiraOnControleeRemovedParams} containing the provided params.
         * @throws IllegalArgumentException if an address was not provided.
         */
        public FiraOnControleeRemovedParams build() throws IllegalArgumentException {
            return new FiraOnControleeRemovedParams(mAddress, mReason);
        }
    }
}
