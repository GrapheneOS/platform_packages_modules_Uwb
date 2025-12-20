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

package android.uwb.timesync;

import android.uwb.timesync.IBleLmpEventListener;
import android.uwb.timesync.IEventCallback;
import android.uwb.timesync.IVersionListener;
import android.uwb.timesync.Version;

/**
 * @hide
 *
 * Next ID: 5
 */
oneway interface ICccDkTimeSync {
  void getApiVersion(in Version versionMin, in Version versionMax, IVersionListener callback) = 1;
  void registerBleLmpEventListener(in byte[] address, IBleLmpEventListener callback) = 2;
  void registerEventCallback(in byte[] address, IEventCallback callback) = 4;
  void unregisterEventCallback(in byte[] address) = 3;
}
