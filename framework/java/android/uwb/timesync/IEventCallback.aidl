package org.carconnectivity.android.digitalkey.timesync;

import org.carconnectivity.android.digitalkey.timesync.BleTimestamp;

/** @hide */
oneway interface IEventCallback {
  void onRegisterSuccess() = 1;
  void onRegisterFailure() = 2;
  void onTimestamp(
    in byte[] address,
    in BleTimestamp timestamp,
    int direction,
    int events,
    int eventCount) = 3;
}