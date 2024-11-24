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

package com.google.uwb.support.rftest;

import android.os.PersistableBundle;

import androidx.annotation.NonNull;

import com.google.uwb.support.base.RequiredParam;

/**
 * Defines parameters to open a Rftest session.
 *
 * <p>This is passed as a bundle to the service API {@link UwbSessionManager#start}.
 */
public class RfTestStartSessionParams extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";
    private static final String KEY_PSDU_DATA = "psdu_data";

    @RfTestOperationType private final int mRfTestOperationType;
    private final byte[] mPsduData;

    private RfTestStartSessionParams(
            int rfTestOperationType,
            byte[] psduData) {
        mRfTestOperationType = rfTestOperationType;
        mPsduData = psduData;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        bundle.putIntArray(KEY_PSDU_DATA, RfTestParams.byteArrayToIntArray(mPsduData));
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestStartSessionParams} */
    public static RfTestStartSessionParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static RfTestStartSessionParams parseBundleVersion1(PersistableBundle bundle) {

        return new Builder()
            .setRfTestOperationType(bundle.getInt(KEY_RF_OPERATION_TYPE))
            .setPsduData(RfTestParams.intArrayToByteArray(bundle.getIntArray(KEY_PSDU_DATA)))
            .build();
    }

    @RfTestOperationType
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    public byte[] getPsduData() {
        return mPsduData;
    }

    /** Builder */
    public static final class Builder {
        @RfTestOperationType
        private RequiredParam<Integer> mRfTestOperationType =
                new RequiredParam<Integer>();

        private byte[] mPsduData = new byte[0];

        public Builder() {}

        public Builder(@NonNull Builder builder) {
            mRfTestOperationType.set(builder.mRfTestOperationType.get());
            mPsduData = builder.mPsduData;
        }

        public Builder(@NonNull RfTestStartSessionParams params) {
            mRfTestOperationType.set(params.mRfTestOperationType);
            mPsduData = params.mPsduData;
        }

        public Builder setRfTestOperationType(@RfTestOperationType int rfTestOperationType) {
            mRfTestOperationType.set(rfTestOperationType);
            return this;
        }

        public Builder setPsduData(byte[] psduData) {
            mPsduData = psduData;
            return this;
        }

        /** Build {@link RfTestStartSessionParams} */
        public RfTestStartSessionParams build() {
            return new RfTestStartSessionParams(
                mRfTestOperationType.get(),
                mPsduData);
        }
    }
}
