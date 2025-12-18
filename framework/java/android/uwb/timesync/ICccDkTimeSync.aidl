package org.carconnectivity.android.digitalkey.timesync;

import org.carconnectivity.android.digitalkey.timesync.IBleLmpEventListener;
import org.carconnectivity.android.digitalkey.timesync.IEventCallback;
import org.carconnectivity.android.digitalkey.timesync.IVersionListener;
import org.carconnectivity.android.digitalkey.timesync.Version;

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