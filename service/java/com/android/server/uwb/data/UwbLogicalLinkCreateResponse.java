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
package com.android.server.uwb.data;

/**
 * Represents the response to a {@code LOGICAL_LINK_CREATE_CMD} command.
 *
 * <p>This class encapsulates the status and connection ID of the logical link creation.
 */
public class UwbLogicalLinkCreateResponse {
    private final int mStatus;
    private final int mLogicalLinkConnectId;

    public UwbLogicalLinkCreateResponse(int status, int connectId) {
        mStatus = status;
        mLogicalLinkConnectId = connectId;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getLogicalLinkConnectId() {
        return mLogicalLinkConnectId;
    }

    @Override
    public String toString() {
        return "UwbLogicalLinkCreateResponse { "
                + " status = " + mStatus
                + " LogicalLinkConnectId = " + mLogicalLinkConnectId
                + "] }";
    }
}
