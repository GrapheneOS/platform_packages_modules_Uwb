/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.uwb.support.oemextension;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.Nullable;

import com.google.uwb.support.base.RequiredParam;

/**
 * RF test notification for oem extension callback
*
* <p> This is passed as a bundle to oem extension API
* {@link UwbManager.UwbOemExtensionCallback#onRfTestNotificationReceived(PersistableBundle)}.
*/
public class RfTestNotification {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final int mRfTestOperationType;
    private final byte[] mRfTestNtfData;
    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String RF_TEST_NTF_DATA = "rf_test_ntf_data";
    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public byte[] getRfTestNtfData() {
        return mRfTestNtfData;
    }

    /*
     * RfTestParams.RfTestOperationType defined as part of RfTest support lib
     * Possible values are:
     * - TEST_PERIODIC_TX = 0
     * - TEST_PER_RX = 1
     * - TEST_RX = 2
     * - TEST_LOOPBACK = 3
     * - TEST_SS_TWR = 4
     * - TEST_SR_RX = 5
     *
     * @return The RF test operation type.
     */
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    private RfTestNotification(int rfTestOperationType, byte[] rfTestNtfData) {
        mRfTestOperationType = rfTestOperationType;
        mRfTestNtfData = rfTestNtfData;
    }

    @Nullable
    private static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = (bytes[i]);
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
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putIntArray(RF_TEST_NTF_DATA, byteArrayToIntArray(mRfTestNtfData));
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        return bundle;
    }

    public static RfTestNotification fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static RfTestNotification parseVersion1(PersistableBundle bundle) {
        return new RfTestNotification.Builder()
                .setRfTestOperationType(bundle.getInt(KEY_RF_OPERATION_TYPE))
                .setRfTestNtfData(intArrayToByteArray(bundle.getIntArray(RF_TEST_NTF_DATA)))
                .build();
    }

    /** Builder */
    public static class Builder {
        private RequiredParam<Integer> mRfTestOperationType = new RequiredParam<Integer>();
        private byte[] mRfTestNtfData = null;

        public RfTestNotification.Builder setRfTestNtfData(byte[] rfTestNtfData) {
            mRfTestNtfData = rfTestNtfData;
            return this;
        }

        public Builder setRfTestOperationType(int rfTestOperationType) {
            mRfTestOperationType.set(rfTestOperationType);
            return this;
        }

        public RfTestNotification build() {
            return new RfTestNotification(mRfTestOperationType.get(), mRfTestNtfData);
        }
    }
}
