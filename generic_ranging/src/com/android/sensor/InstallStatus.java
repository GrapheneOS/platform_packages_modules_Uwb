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

package com.android.sensor;

/** The status returned by MultiSensorFinder when requestInstall was called. */
public enum InstallStatus {
    /** Everything is installed and start can be called. */
    OK,
    /**
     * The activity was switched, and the user was prompted to install dependencies. requestInstall
     * must be called again before starting the session.
     */
    USER_PROMPTED_TO_INSTALL_DEPENDENCIES,
    /**
     * The user was asked to install dependencies, and the user rejected the request. The user will
     * not be asked again until the app is restarted.
     */
    USER_DECLINED_TO_INSTALL_DEPENDENCIES,
    /** ARCore cannot be installed on this device. */
    DEVICE_INCOMPATIBLE,
    UNKNOWN_ERROR
}
