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

package com.android.server.ranging.oob;

import com.google.common.util.concurrent.ListenableFuture;

/** OOB Provider for Precision Finding. */
public interface OobProvider {
    /**
     * Sends a {@link CapabilityRequestMessage} to the peripheral device to determine its ranging
     * capabilities.
     */
    ListenableFuture<com.android.server.ranging.oob.CapabilityResponseMessage>
        sendCapabilityRequestMsg(com.android.server.ranging.oob.CapabilityRequestMessage message);

    /**
     * Sends a {@link SetConfigurationMessage} to the peripheral device to set the ranging configs.
     */
    ListenableFuture<StatusResponseMessage> sendSetConfigMsg(SetConfigurationMessage message);

    /** Notifies the peripheral device to start ranging. */
    ListenableFuture<StatusResponseMessage> sendStartRangingMsg(StartRangingMessage message);

    /** Notifies the peripheral device to stop ranging. */
    ListenableFuture<StatusResponseMessage> sendStopRangingMsg(StopRangingMessage message);
}
