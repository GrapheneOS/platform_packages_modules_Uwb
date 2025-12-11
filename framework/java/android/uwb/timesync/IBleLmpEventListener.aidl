package org.carconnectivity.android.digitalkey.timesync;

import org.carconnectivity.android.digitalkey.timesync.BleTimestamp;

/** @hide */
oneway interface IBleLmpEventListener {
  void onTimestamp(
    in byte[] address,
    in BleTimestamp timestamp,
    int direction,
    int events,
    int eventCount);
}