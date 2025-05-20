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

package com.google.uwb.support.fira;

import static com.google.uwb.support.fira.FiraParams.isCorrectProtocol;

import android.os.PersistableBundle;

/**
 * Class representing logical link information.
 */
public class FiraLogicalLinkInfo extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_LOGICAL_LINK_CONNECT_ID = "logical_link_connect_id";

    private final int mLogicalLinkConnectId;

    private FiraLogicalLinkInfo(int logicalLinkConnectId) {
        this.mLogicalLinkConnectId = logicalLinkConnectId;
    }

    public int getLogicalLinkConnectId() {
        return mLogicalLinkConnectId;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_LOGICAL_LINK_CONNECT_ID, mLogicalLinkConnectId);
        return bundle;
    }

    public static FiraLogicalLinkInfo fromBundle(PersistableBundle bundle) {
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

    private static FiraLogicalLinkInfo parseVersion1(PersistableBundle bundle) {
        int connectId = bundle.getInt(KEY_LOGICAL_LINK_CONNECT_ID, -1);
        return new FiraLogicalLinkInfo.Builder(connectId).build();
    }

    /**
     * Builder class for constructing {@link FiraLogicalLinkInfo} instances.
     */
    public static class Builder {
        private final int mLogicalLinkConnectId;

        /**
         * Constructs a builder with the required logical link connection ID.
         *
         * @param logicalLinkConnectId the logical link connection ID
         */
        public Builder(int logicalLinkConnectId) {
            this.mLogicalLinkConnectId = logicalLinkConnectId;
        }

        public FiraLogicalLinkInfo build() {
            return new FiraLogicalLinkInfo(mLogicalLinkConnectId);
        }
    }
}
