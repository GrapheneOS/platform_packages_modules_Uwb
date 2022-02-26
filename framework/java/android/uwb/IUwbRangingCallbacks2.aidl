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

import android.os.PersistableBundle;
import android.uwb.RangingChangeReason;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

/**
 * @hide
 * TODO(b/196225233): Rename this to IUwbRangingCallbacks when qorvo stack is integrated.
 * Temporary AIDL interface name for the interface between UwbManager & UwbService.
 * The existing IUwbAdapter interface is kept behind for providing backwards
 * compatibility with the old UWB architecture.
 */
oneway interface IUwbRangingCallbacks2 {
  void onRangingOpened(in SessionHandle sessionHandle);

  void onRangingOpenFailed(in SessionHandle sessionHandle,
                           RangingChangeReason reason,
                           in PersistableBundle parameters);

  void onRangingStarted(in SessionHandle sessionHandle,
                        in PersistableBundle parameters);

  void onRangingStartFailed(in SessionHandle sessionHandle,
                            RangingChangeReason reason,
                            in PersistableBundle parameters);

  void onRangingReconfigured(in SessionHandle sessionHandle,
                             in PersistableBundle parameters);

  void onRangingReconfigureFailed(in SessionHandle sessionHandle,
                                  RangingChangeReason reason,
                                  in PersistableBundle parameters);

  void onRangingStopped(in SessionHandle sessionHandle,
                        RangingChangeReason reason,
                        in PersistableBundle parameters);

  void onRangingStopFailed(in SessionHandle sessionHandle,
                           RangingChangeReason reason,
                           in PersistableBundle parameters);

  void onRangingClosed(in SessionHandle sessionHandle,
                       RangingChangeReason reason,
                       in PersistableBundle parameters);

  void onRangingResult(in SessionHandle sessionHandle, in RangingReport result);

  void onControleeAdded(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void onControleeAddFailed(in SessionHandle sessionHandle,
          RangingChangeReason reason, in PersistableBundle parameters);

  void onControleeRemoved(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void onControleeRemoveFailed(in SessionHandle sessionHandle,
          RangingChangeReason reason, in PersistableBundle parameters);

  void onRangingPaused(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void onRangingPauseFailed(in SessionHandle sessionHandle,
          RangingChangeReason reason, in PersistableBundle parameters);

  void onRangingResumed(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void onRangingResumeFailed(in SessionHandle sessionHandle,
          RangingChangeReason reason, in PersistableBundle parameters);

  void onDataSent(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          in PersistableBundle parameters);

  void onDataSendFailed(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          RangingChangeReason reason, in PersistableBundle parameters);

  void onDataReceived(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          in PersistableBundle parameters, in byte[] data);

  void onDataReceiveFailed(in SessionHandle sessionHandle, in UwbAddress remoteDeviceAddress,
          RangingChangeReason reason, in PersistableBundle parameters);

  void onServiceDiscovered(in SessionHandle sessionHandle, in PersistableBundle parameters);

  void onServiceConnected(in SessionHandle sessionHandle, in PersistableBundle parameters);
}
