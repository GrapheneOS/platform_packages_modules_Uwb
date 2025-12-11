package org.carconnectivity.android.digitalkey.timesync;

import org.carconnectivity.android.digitalkey.timesync.Version;

/** @hide */
oneway interface IVersionListener {
  void onVersion(in Version version);
}