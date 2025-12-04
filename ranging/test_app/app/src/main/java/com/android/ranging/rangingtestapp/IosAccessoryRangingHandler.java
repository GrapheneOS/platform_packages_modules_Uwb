/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.ranging.rangingtestapp;

import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.AccessoryConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.AppleShareableConfigurationData;

public abstract class IosAccessoryRangingHandler {
    // Callback for receiving ranging status to notify the remote application
    abstract static class AccessoryCallback {
        abstract void onRangingStarted();
        abstract void onRangingStopped();
    }

    // Handler to control the ranging of the accessory
    abstract void setAccessoryCallback(AccessoryCallback accessoryCallback);
    abstract AccessoryConfigurationData createAccessoryConfigurationData();
    // A pair of AccessoryConfigurationData and AppleShareableConfigurationData is needed to start
    // the ranging.
    abstract boolean startAccessoryRanging(
            AccessoryConfigurationData accessoryConfigurationData,
            AppleShareableConfigurationData appleShareableConfigurationData);
    abstract boolean stopAccessoryRanging();
}
