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

package com.google.uwb.support.rftest;

import android.os.PersistableBundle;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.StatusCode;
import com.google.uwb.support.rftest.RfTestParams.RfTestOperationType;

public class RfTestSessionStatus extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_RF_OPERATION_TYPE = "rf_operation_type";
    private static final String KEY_STATUS_CODE = "status_code";

    /** FiRa status code defined in Table 32 */
    @StatusCode
    private final int mStatusCode;
    @RfTestOperationType private final int mRfTestOperationType;

    private RfTestSessionStatus(@RfTestOperationType int rfTestOperationType,
            @StatusCode int statusCode) {
        mRfTestOperationType = rfTestOperationType;
        mStatusCode = statusCode;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @RfTestOperationType
    public int getRfTestOperationType() {
        return mRfTestOperationType;
    }

    @StatusCode
    public int getStatusCode() {
        return mStatusCode;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_RF_OPERATION_TYPE, mRfTestOperationType);
        bundle.putInt(KEY_STATUS_CODE, mStatusCode);
        return bundle;
    }

    public static RfTestSessionStatus fromBundle(PersistableBundle bundle) {
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

    private static RfTestSessionStatus parseVersion1(PersistableBundle bundle) {
        return new RfTestSessionStatus.Builder()
            .setRfTestOperationType(bundle.getInt(KEY_RF_OPERATION_TYPE))
            .setStatusCode(bundle.getInt(KEY_STATUS_CODE)).build();
    }

    /** Builder */
    public static class Builder {
        @RfTestOperationType
        private RequiredParam<Integer> mRfTestOperationType =
                new RequiredParam<Integer>();
        private final RequiredParam<Integer> mStatusCode = new RequiredParam<>();

        public RfTestSessionStatus.Builder setRfTestOperationType(
                @RfTestOperationType int rfTestOperationType) {
            mRfTestOperationType.set(rfTestOperationType);
            return this;
        }

        public RfTestSessionStatus.Builder setStatusCode(int statusCode) {
            mStatusCode.set(statusCode);
            return this;
        }

        public RfTestSessionStatus build() {
            return new RfTestSessionStatus(mRfTestOperationType.get(), mStatusCode.get());
        }
    }
}
