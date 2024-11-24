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

package com.android.server.uwb.rftest;

import android.os.PersistableBundle;

/**
 * Interface representing a generic RF Notification event in UWB (Ultra-Wideband) testing.
 * This interface provides methods to retrieve the raw notification data, status,
 * operation type, and a conversion to a {@link PersistableBundle} for further processing.
 *
 * Implementations of this interface will represent specific types of RF test results
 * and provide the relevant data and functionality required for handling UWB test notifications.
 */
public interface RfNotificationEvent {

    /**
     * Retrieves the raw notification data associated with this RF notification event.
     *
     * @return a byte array containing the raw notification data.
     */
    byte[] getRawNotificationData();

    /**
     * Retrieves the status of the RF notification event.
     * The status typically indicates the result of the test operation.
     *
     * @return an integer representing the status of the RF notification event.
     */
    int getStatus();

    /**
     * Retrieves the operation type associated with this RF notification event.
     * The operation type indicates the type of test being performed.
     *
     * @return an integer representing the RF test operation type.
     */
    int getOperationType();

    /**
     * Converts this RF notification event to a {@link PersistableBundle}.
     * The bundle can be used for further processing or passing data to other components.
     *
     * @return a {@link PersistableBundle} containing the data of this RF notification event.
     */
    PersistableBundle toBundle();
}

