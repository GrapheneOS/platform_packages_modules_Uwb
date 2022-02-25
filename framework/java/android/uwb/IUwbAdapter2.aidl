/*
 * Copyright 2020 The Android Open Source Project
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

package android.uwb;

import android.content.AttributionSource;
import android.os.PersistableBundle;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbAdfProvisionStateCallbacks;
import android.uwb.IUwbRangingCallbacks2;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;
import android.uwb.IUwbVendorUciCallback;

/**
 * @hide
 * TODO(b/196225233): Rename this to IUwbAdapter when qorvo stack is integrated.
 * Temporary AIDL interface name for the interface between UwbManager & UwbService.
 * The existing IUwbAdapter interface is kept behind for providing backwards
 * compatibility with the old UWB architecture.
 */
interface IUwbAdapter2 {
  void registerAdapterStateCallbacks(in IUwbAdapterStateCallbacks adapterStateCallbacks);

  void unregisterAdapterStateCallbacks(in IUwbAdapterStateCallbacks callbacks);

   /*
    * Register the callbacks used to notify the framework of events and data
    *
    * The provided callback's IUwbUciVendorCallback#onVendorNotificationReceived
    * function must be called immediately following vendorNotification received
    *
    * @param callbacks callback to provide Notification data updates to the framework
    */
   void registerVendorExtensionCallback(in IUwbVendorUciCallback callbacks);

   /*
    * Unregister the callbacks used to notify the framework of events and data
    *
    * Calling this function with an unregistered callback is a no-op
    *
    * @param callbacks callback to unregister
    */
   void unregisterVendorExtensionCallback(in IUwbVendorUciCallback callbacks);


  long getTimestampResolutionNanos(in String chipId);

  PersistableBundle getSpecificationInfo(in String chipId);

  void openRanging(in AttributionSource attributionSource,
                   in SessionHandle sessionHandle,
                   in IUwbRangingCallbacks2 rangingCallbacks,
                   in PersistableBundle parameters,
                   in String chipId);

  void startRanging(in SessionHandle sessionHandle,
                    in PersistableBundle parameters);

  void reconfigureRanging(in SessionHandle sessionHandle,
                          in PersistableBundle parameters);

  void stopRanging(in SessionHandle sessionHandle);

  void closeRanging(in SessionHandle sessionHandle);

  void addControlee(in SessionHandle sessionHandle, in PersistableBundle params);

  void removeControlee(in SessionHandle sessionHandle, in PersistableBundle params);

  void pause(in SessionHandle sessionHandle, in PersistableBundle params);

  void resume(in SessionHandle sessionHandle, in PersistableBundle params);

  void sendData(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          in PersistableBundle params, in byte[] data);

  void setEnabled(boolean enabled);

  int getAdapterState();

  /**
   * Returns a list of UWB chip infos in a {@link PersistableBundle}.
   *
   * Callers can invoke methods on a specific UWB chip by passing its {@code chipId} to the
   * method, which can be determined by calling:
   * <pre>
   * List<PersistableBundle> chipInfos = getChipInfos();
   * for (PersistableBundle chipInfo : chipInfos) {
   *     String chipId = ChipInfoParams.fromBundle(chipInfo).getChipId();
   * }
   * </pre>
   *
   * @return list of {@link PersistableBundle} containing info about UWB chips for a multi-HAL
   * system, or a list of info for a single chip for a single HAL system.
   */
  List<PersistableBundle> getChipInfos();

  List<String> getChipIds();

  String getDefaultChipId();

  PersistableBundle addServiceProfile(in PersistableBundle parameters);

  int removeServiceProfile(in PersistableBundle parameters);

  PersistableBundle getAllServiceProfiles();

  PersistableBundle getAdfProvisioningAuthorities(in PersistableBundle parameters);

  PersistableBundle getAdfCertificateAndInfo(in PersistableBundle parameters);

  void provisionProfileAdfByScript(in PersistableBundle serviceProfileBundle,
            in IUwbAdfProvisionStateCallbacks callback);

  int removeProfileAdf(in PersistableBundle serviceProfileBundle);

  int sendVendorUciMessage(int gid, int oid, in byte[] payload);

  /**
   * The maximum allowed time to open a ranging session.
   */
  const int RANGING_SESSION_OPEN_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to start a ranging session.
   */
  const int RANGING_SESSION_START_THRESHOLD_MS = 3000; // Value TBD

  /**
   * The maximum allowed time to notify the framework that a session has been
   * closed.
   */
  const int RANGING_SESSION_CLOSE_THRESHOLD_MS = 3000; // Value TBD
}
