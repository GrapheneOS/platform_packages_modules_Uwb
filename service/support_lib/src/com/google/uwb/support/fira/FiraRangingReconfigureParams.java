/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkArgument;
import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;

/** UWB parameters used to reconfigure a FiRa session. Supports peer adding/removing. */
public class FiraRangingReconfigureParams extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final int mSessionId;
    @MulticastListUpdateAction private final int mAction;
    @MacAddressMode private final int mMacAddressMode;
    private final UwbAddress[] mAddressList;
    @Nullable private final int[] mSubSessionIdList;

    private static final String KEY_SESSION_ID = "id";
    private static final String KEY_ACTION = "action";
    private static final String KEY_MAC_ADDRESS_MODE = "mac_address_mode";
    private static final String KEY_ADDRESS_LIST = "address_list";
    private static final String KEY_SUB_SESSION_ID_LIST = "sub_session_id_list";

    private FiraRangingReconfigureParams(
            int sessionId,
            @MulticastListUpdateAction int action,
            @MacAddressMode int macAddressMode,
            UwbAddress[] addressList,
            @Nullable int[] subSessionIdList) {
        mSessionId = sessionId;
        mAction = action;
        mMacAddressMode = macAddressMode;
        mAddressList = addressList;
        mSubSessionIdList = subSessionIdList;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public int getSessionId() {
        return mSessionId;
    }

    @MulticastListUpdateAction
    public int getAction() {
        return mAction;
    }

    @MacAddressMode
    public int getMacAddressMode() {
        return mMacAddressMode;
    }

    public UwbAddress[] getAddressList() {
        return mAddressList;
    }

    @Nullable
    public int[] getSubSessionIdList() {
        return mSubSessionIdList;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_ACTION, mAction);
        bundle.putInt(KEY_MAC_ADDRESS_MODE, mMacAddressMode);

        long[] addressList = new long[mAddressList.length];
        int i = 0;
        for (UwbAddress address : mAddressList) {
            addressList[i++] = uwbAddressToLong(address);
        }

        bundle.putLongArray(KEY_ADDRESS_LIST, addressList);
        bundle.putIntArray(KEY_SUB_SESSION_ID_LIST, mSubSessionIdList);
        return bundle;
    }

    public static FiraRangingReconfigureParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static FiraRangingReconfigureParams parseVersion1(PersistableBundle bundle) {
        int macAddressMode = bundle.getInt(KEY_MAC_ADDRESS_MODE);
        int addressByteLength = 2;
        if (macAddressMode == MAC_ADDRESS_MODE_8_BYTES) {
            addressByteLength = 8;
        }

        long[] addresses = bundle.getLongArray(KEY_ADDRESS_LIST);
        UwbAddress[] addressList = new UwbAddress[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            addressList[i] = longToUwbAddress(addresses[i], addressByteLength);
        }

        return new FiraRangingReconfigureParams.Builder()
                .setSessionId(bundle.getInt(KEY_SESSION_ID))
                .setAction(bundle.getInt(KEY_ACTION))
                .setAddressList(addressList)
                .setSubSessionIdList(bundle.getIntArray(KEY_SUB_SESSION_ID_LIST))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Integer> mSessionId = new RequiredParam<>();
        private final RequiredParam<Integer> mAction = new RequiredParam<>();
        @MacAddressMode private int mMacAddressMode;
        private UwbAddress[] mAddressList;
        private int[] mSubSessionIdList;

        public FiraRangingReconfigureParams.Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public FiraRangingReconfigureParams.Builder setAction(
                @MulticastListUpdateAction int action) {
            mAction.set(action);
            return this;
        }

        public FiraRangingReconfigureParams.Builder setAddressList(UwbAddress[] addressList) {
            mAddressList = addressList;
            return this;
        }

        public FiraRangingReconfigureParams.Builder setSubSessionIdList(int[] subSessionIdList) {
            mSubSessionIdList = subSessionIdList;
            return this;
        }

        private void checkAddressList() {
            checkArgument(mAddressList != null && mAddressList.length > 0);
            for (int i = 0; i < mAddressList.length; i++) {
                checkNotNull(mAddressList[i]);
                checkArgument(mAddressList[i].size() == mAddressList[0].size());
            }
        }

        public FiraRangingReconfigureParams build() {
            checkAddressList();
            checkArgument(
                    mSubSessionIdList == null || mSubSessionIdList.length == mAddressList.length);
            mMacAddressMode =
                    mAddressList[0].size() == UwbAddress.SHORT_ADDRESS_BYTE_LENGTH
                            ? MAC_ADDRESS_MODE_2_BYTES
                            : MAC_ADDRESS_MODE_8_BYTES;

            return new FiraRangingReconfigureParams(
                    mSessionId.get(),
                    mAction.get(),
                    mMacAddressMode,
                    mAddressList,
                    mSubSessionIdList);
        }
    }
}
