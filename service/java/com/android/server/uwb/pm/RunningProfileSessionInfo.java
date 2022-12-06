/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.pm;

import androidx.annotation.NonNull;

import com.android.server.uwb.util.ObjectIdentifier;

import java.util.List;
import java.util.Optional;

/**
 * Provides the session information for this profile
 */
public interface RunningProfileSessionInfo {
    /**
     * Gets the controlleeInfo.
     */
    @NonNull
    ControlleeInfo getControlleeInfo();

    /**
     * Gets the UWB capability of the current device.
     */
    @NonNull
    UwbCapability getUwbCapability();

    /**
     * Gets the OID of ADF which was provisioned successfully for the current profile.
     */
    @NonNull
    ObjectIdentifier getOidOfProvisionedAdf();

    /**
     * Gets the selectable ADF OIDs of the peer device, which is empty if the device is
     * taking the responder role.
     */
    @NonNull
    List<ObjectIdentifier> getSelectableOidsOfPeerDevice();

    /**
     * Checks if the device is the controller of UWB.
     * @return
     */
    boolean isUwbController();

    /**
     * Checks if the current UWB session is unicast session.
     */
    boolean isUnicast();

    /**
     * For multicast case, the primary sessionId should be assigned by the framework.
     */
    @NonNull
    Optional<Integer> getSharedPrimarySessionId();

    /**
     * The secure blob is required if the session is using the dynamic slot mechanism.
     */
    @NonNull
    Optional<byte[]> getSecureBlob();
}
