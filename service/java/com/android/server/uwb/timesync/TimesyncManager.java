/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.uwb.timesync;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.annotation.Hide;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresNoPermission;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;
import android.uwb.UwbManager;
import android.uwb.UwbManager.UwbVendorUciCallback;

import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.UwbInjector;

// TODO (b/422751710): we want these but adding the right dependency in
// Android.bp is having problems. For now we'll just define constants ourselves.
//
// import android.hardware.uwb.fira_android.UwbVendorGids;
// import android.hardware.uwb.fira_android.UwbVendorGidAndroidOids;

import android.uwb.timesync.BleTimestamp;
import android.uwb.timesync.CccDkTimeSync;
import android.uwb.timesync.IBleLmpEventListener;
import android.uwb.timesync.ICccDkTimeSync;
import android.uwb.timesync.IEventCallback;
import android.uwb.timesync.IVersionListener;
import android.uwb.timesync.Version;

import android.hardware.bluetooth.lmp_event.Direction;
import android.hardware.bluetooth.lmp_event.IBluetoothLmpEvent;
import android.hardware.bluetooth.lmp_event.IBluetoothLmpEventCallback;
import android.hardware.bluetooth.lmp_event.LmpEventId;
import android.hardware.bluetooth.lmp_event.Timestamp;

/**
 * @hide
 */
@Hide
public class TimesyncManager {
    private static final String TAG = TimesyncManager.class.getSimpleName();
    private static final String HAL_INSTANCE_NAME = IBluetoothLmpEvent.DESCRIPTOR + "/default";

    // TODO (b/422469744):
    // * It needs to be well-documented how we arrive at any offsets /
    //   uncertainty. Apparently, these numbers came from:
    //     > If the actual time of the LMP event was X and the reported time
    //     > was Y, profiling found Y = X + 36 +/- 18.
    //   ...but that should be re-done.
    // * These should really be configurable per-device rather than hard coded
    //   to the implementation. Runtime Resource Overlays
    //   (https://source.android.com/docs/core/runtime/rros) are a potential
    //   solution for that.
    public static final int DEVICE_TIME_OFFSET_US = -36000;
    public static final int DEVICE_TIME_UNCERTAINTY = 18000;
    public static final int MAX_CLOCK_SKEW_PPM = 100;

    // TODO (b/422751710): These ought to come from
    // android.hardware.uwb.fira_android.UwbVendorGids and
    // android.hardware.uwb.fira_android.UwbVendorGidAndroidOids
    public static final int ANDROID_GID = 0x0c;
    public static final int ANDROID_TIMESTAMP_ANCHOR_TO_UWBS = 0x03;

    // These come from "Table 67: Status Codes" the FiRa 2.0 spec.
    //
    // TODO (b/422752229): should move to UwbManager since anyone using
    // sendVendorUciMessage() needs these defines.
    public static final byte UCI_STATUS_OK = 0x0;
    public static final byte UCI_STATUS_INVALID_MESSAGE_SIZE = 0x06;
    public static final byte UCI_STATUS_UNKNOWN_GID = 0x07;
    public static final byte UCI_STATUS_UNKNOWN_OID = 0x08;

    // This is constant for the most part, but when doing unit tests we'll
    // force it lower to tests aren't so slow.
    private static long TIMEOUT = 5;
    private static TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

    public static final Version SUPPORTED_VERSIONS[] = {
            new Version((byte) 1, (byte) 1), new Version((byte) 1, (byte) 0),
    };

    // Encode the uncertainty as per Table 19-43 of the Digital Key Technical
    // Specification Release 3.
    //
    // NOTES:
    // * That spec has a typo where it says to divide by 8 instead of multiply.
    //   Multiply is clearly correct given that this allows the full range of
    //   values given in the spec.
    // * The spec doesn't make the Math.ceil() call explicit but doing so will
    //   err on the side of saying that we're _more_ uncertain which is much
    //   safer.
    private static final int encodeTimeUncertainty(int uncertaintyUs) {
        int result;

        // log(0) is not defined; lowest uncertainty possible is 1us, so use
        // that if someone claims uncertainty is 0.
        if (uncertaintyUs <= 0) {
            return 0;
        }

        result = (int) Math.ceil(Math.log(uncertaintyUs) / Math.log(2) * 8);

        // Uncertainty has to be 1 byte. 0xff is ~1 hour so max at that.
        if (result > 0xff) {
            return 0xff;
        }

        return result;
    }

    private static void enforceUwbPrivilegedPermission(Context context) {
        context.enforceCallingOrSelfPermission(android.Manifest.permission.UWB_PRIVILEGED,
                "UwbService");
    }

    private static class BleLmpEventListenerWrapper extends IEventCallback.Stub {
        private final Context mContext;
        private final IBleLmpEventListener mListener;

        private BleLmpEventListenerWrapper(Context context, IBleLmpEventListener listener) {
            mContext = context;
            mListener = listener;
        }

        @Override
        @RequiresNoPermission
        public void onRegisterSuccess() {
            // do nothing
        }

        @Override
        @RequiresNoPermission
        public void onRegisterFailure() {
            // do nothing
        }

        @Override
        @PermissionManuallyEnforced
        public void onTimestamp(
                byte[] address, BleTimestamp timestamp, int direction, int events, int eventCount)
                throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            mListener.onTimestamp(address, timestamp, direction, events, eventCount);
        }
    }

    private static class BluetoothCccCallback extends IBluetoothLmpEventCallback.Stub {

        // A small structure to pass results between threads w/ the BlockingQueue.
        private static class TimestampConversionResult {
            TimestampConversionResult(byte status_, long timestampUs_, int uncertaintyUs_) {
                status = status_;
                timestampUs = timestampUs_;
                uncertaintyUs = uncertaintyUs_;
            }
            TimestampConversionResult(byte status_) {
                this(status_, 0, 0);
            }
            byte status;
            long timestampUs;
            int uncertaintyUs;
        }

        // When we call mUwbManager.sendVendorUciMessage() it doesn't directly
        // return the result. Instead, it will invoke a callback in a separate
        // context. We really want the result back in the same context where
        // we called sendVendorUciMessage(). This class will get the callback
        // and use a BlockingQueue to send the result back to the original site.
        private static class CccDkTimeSyncVendorUciCallback implements UwbVendorUciCallback {
            private final BlockingQueue<TimestampConversionResult> mQueue;

            private CccDkTimeSyncVendorUciCallback(BlockingQueue<TimestampConversionResult> queue) {
                mQueue = queue;
            }

            @Override
            public void onVendorUciResponse(
                    @IntRange(from = 0, to = 15) int gid, int oid, @NonNull byte[] payload) {

                // status (1), timestamp (8), uncertainty (4) = 13 bytes
                final int expectedPayloadLen = 13;

                Log.v(TAG, String.format("onVendorUciResponse %d %d (%d)",
                        gid, oid, payload.length));
                if (gid == ANDROID_GID && oid == ANDROID_TIMESTAMP_ANCHOR_TO_UWBS) {
                    TimestampConversionResult result;
                    ByteBuffer buffer = ByteBuffer.wrap(payload);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);

                    byte status = buffer.get();
                    if (status != UCI_STATUS_OK) {
                        /* Caller handles/prints errors since some non-OK statuses are expected */
                        result = new TimestampConversionResult(status);
                    } else if (payload.length < expectedPayloadLen) {
                        Log.e(TAG, String.format("Timestamp conversion response too short: %d < %d",
                                payload.length, expectedPayloadLen));
                        result = new TimestampConversionResult(UCI_STATUS_INVALID_MESSAGE_SIZE);
                    } else {
                        if (payload.length != expectedPayloadLen) {
                            Log.i(TAG, String.format("Ignoring extra bytes in timestamp conversion response: %d > %d",
                                    payload.length, expectedPayloadLen));
                        }
                        result = new TimestampConversionResult(status, buffer.getLong(),
                                buffer.getInt());
                    }

                    try {
                        mQueue.put(result);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Queue put failed with InterruptedException: " + e);
                    }
                }
            }

            @Override
            public void onVendorUciNotification(
                    @IntRange(from = 9, to = 15) int gid, int oid, @NonNull byte[] payload) {
                Log.d(TAG, String.format("UCI notification gid=%d oid=%d", gid, oid));
            }
        }

        private BleTimestamp timestampFromHal(Timestamp timestamp) {

            // While it's unlikely to be needed, flush the queue just in case
            // there is something stale in it.
            while (mVendorUciQueue.poll() != null) { }

            // Kick off a call to the UWB HAL via a UCI message. We'll get
            // a callback and the response will be passed back via a queue.
            ByteBuffer systemTimeBuffer = ByteBuffer.allocate(16);
            systemTimeBuffer.order(ByteOrder.LITTLE_ENDIAN);
            systemTimeBuffer.putLong(timestamp.systemTimeUs);
            systemTimeBuffer.putLong(timestamp.bluetoothTimeUs);
            int sendVendorUciStatus = mUwbManager.sendVendorUciMessage(
                    ANDROID_GID, ANDROID_TIMESTAMP_ANCHOR_TO_UWBS,
                    systemTimeBuffer.array());
            if (sendVendorUciStatus != UwbManager.SEND_VENDOR_UCI_SUCCESS) {
                throw new RuntimeException(String.format("sendVendorUciMessage failed: %d",
                        sendVendorUciStatus));
            }

            // The callback is expected to fire right away. Put a long timeout
            // so we don't get stuck forever if something goes wrong.
            TimestampConversionResult result;
            try {
                result = mVendorUciQueue.poll(TIMEOUT, TIMEOUT_UNIT);
                if (result == null) {
                    throw new RuntimeException("Timeout waiting for UCI Vendor Message Response");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Communication with UCI Vendor Callback failed: " + e);
            }

            long uwbsTimeOffsetUs;
            int uwbsConversionUncertaintyUs;
            if (result.status == UCI_STATUS_OK) {
                // The UWB HAL gave us an absolute time, but convert to an
                // offset so the math is the same if we don't need to convert.
                uwbsTimeOffsetUs = result.timestampUs - timestamp.systemTimeUs;
                uwbsConversionUncertaintyUs = result.uncertaintyUs;
            } else if (result.status == UCI_STATUS_UNKNOWN_GID
                    || result.status == UCI_STATUS_UNKNOWN_OID) {
                // If a HAL doesn't know about timestamp conversion then
                // presumably it doesn't need it and the Bluetooth timestamp
                // is already in the UWBS time domain.
                //
                // We'll still add DEVICE_TIME_OFFSET_US and
                // DEVICE_TIME_UNCERTAINTY, but maybe we should just get rid
                // of those? See the TODO (b/422469744) where they're defined.
                //
                // TODO (b/422755279): add an option to assume `systemTime`
                // is in BOOTTIME and use `queryUwbsTimestampMicros()` to
                // convert.
                Log.i(TAG, String.format("UWB HAL doesn't convert timestamps: %d", result.status));
                uwbsTimeOffsetUs = DEVICE_TIME_OFFSET_US;
                uwbsConversionUncertaintyUs = DEVICE_TIME_UNCERTAINTY;
            } else {
                throw new RuntimeException(String.format("Timestamp conversion failed: %d",
                        result.status));
            }

            Log.i(TAG, String.format("systemTime=%d => %d, bluetoothTime=%d, uncertainty=%d us",
                    timestamp.systemTimeUs,
                    timestamp.systemTimeUs + uwbsTimeOffsetUs,
                    timestamp.bluetoothTimeUs,
                    uwbsConversionUncertaintyUs));

            // Fill in the BleTimestamp structure after converting the time
            // domain of systemTime (if no conversion is needed,
            // `uwbsTimeOffsetUs` and `uwbsConversionUncertaintyUs` will be 0).
            //
            // NOTES:
            // - bluetoothTime: Timestamp of the anchor point in the time
            //                  domain of Bluetooth hardware.
            //                  Extracted from the Bluetooth VSE
            //                  (Vendor-Specific Event) for TimeSync report.
            //                  This is passed straight through, though it's
            //                  expected that nothing will look at this field
            //                  in the BleTimestamp since the `systemTime`
            //                  field will have UWBS time (see below).
            // - systemTime: Timestamp of the anchor point in the time
            //               domain of the "system". When received from the
            //               Bluetooth CCC HAL (via the `timestamp` parameter
            //               to this function), this is in a time domain that
            //               is mutually agreed upon by the Bluetooth CCC HAL
            //               and the UWB HAL on the device. When returned
            //               from this function as a `BleTimestamp` this is
            //               in the UWBS time domain and appropriate for
            //               using in DeviceTime0 or DeviceTime1 (depending on
            //               whether we're performing Procedure 0 or
            //               Procedure 1).
            return new BleTimestamp(
                    timestamp.systemTimeUs + uwbsTimeOffsetUs,
                    timestamp.bluetoothTimeUs,
                    encodeTimeUncertainty(uwbsConversionUncertaintyUs),
                    MAX_CLOCK_SKEW_PPM,
                    /* isClockSkewMeasurementAvailable= */ false);
        }

        private static int directionFromHal(byte direction) {
            switch (direction) {
                case Direction.TX:
                    return CccDkTimeSync.Direction.TX.ordinal();
                case Direction.RX:
                    return CccDkTimeSync.Direction.RX.ordinal();
                default:
                    return 0;
            }
        }

        private static int eventFromHal(byte event) {
            switch (event) {
                case LmpEventId.CONNECT_IND:
                    return CccDkTimeSync.BleLmpEvent.CONNECT_IND.ordinal();
                case LmpEventId.LL_PHY_UPDATE_IND:
                    return CccDkTimeSync.BleLmpEvent.LL_PHY_UPDATE_IND.ordinal();
                default:
                    return 0;
            }
        }

        private final IEventCallback mCallback;
        private final Context mContext;
        private final UwbManager mUwbManager;
        private final BlockingQueue<TimestampConversionResult> mVendorUciQueue;
        private final CccDkTimeSyncVendorUciCallback mVendorUciCallback;

        private BluetoothCccCallback(IEventCallback callback, Context context,
                UwbManager uwbManager) {
            mCallback = callback;
            mContext = context;
            mUwbManager = uwbManager;
            mVendorUciQueue = new LinkedBlockingQueue<>();
            mVendorUciCallback = new CccDkTimeSyncVendorUciCallback(mVendorUciQueue);
            mUwbManager.registerUwbVendorUciCallback(Executors.newSingleThreadExecutor(),
                    mVendorUciCallback);
        }

        private BluetoothCccCallback(IBleLmpEventListener listener, Context context,
                UwbManager uwbManager) {
            this(new BleLmpEventListenerWrapper(context, listener), context, uwbManager);
        }

        public void close() {
            mUwbManager.unregisterUwbVendorUciCallback(mVendorUciCallback);
        }
        //TODO (b/467707737): fix address type, currently just set to public
        @Override
        @PermissionManuallyEnforced
        public void onEventGenerated(
                Timestamp timestamp,
                byte addressType,
                byte[] address,
                byte direction,
                byte lmpEventId,
                char eventCounter)
                throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            mCallback.onTimestamp(
                    address,
                    timestampFromHal(timestamp),
                    directionFromHal(direction),
                    eventFromHal(lmpEventId),
                    eventCounter);
        }

        @Override
        @PermissionManuallyEnforced
        public void onRegistered(boolean status) throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            if (status) {
                Log.i(TAG, "Register Success");
                mCallback.onRegisterSuccess();
            } else {
                Log.i(TAG, "Register Failed");
                mCallback.onRegisterFailure();
            }
        }

        @Override
        @PermissionManuallyEnforced
        public String getInterfaceHash() {
            enforceUwbPrivilegedPermission(mContext);
            return IBluetoothLmpEventCallback.HASH;
        }

        @Override
        @PermissionManuallyEnforced
        public int getInterfaceVersion() {
            enforceUwbPrivilegedPermission(mContext);
            return IBluetoothLmpEventCallback.VERSION;
        }
    }

    private class CccDkTimeSyncImpl extends ICccDkTimeSync.Stub {

        @Override
        @PermissionManuallyEnforced
        public void getApiVersion(
                Version versionMin,
                Version versionMax,
                IVersionListener listener)
                throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            for (Version version : SUPPORTED_VERSIONS) {
                if (versionMin.isGreaterThan(version) || versionMax.isLessThan(version)) {
                    continue;
                }

                listener.onVersion(version);
                return;
            }

            listener.onVersion(CccDkTimeSync.VERSION_UNSUPPORTED);
        }

        //TODO (b/467707737): fix address type, currently just set to public
        public static final byte PUBLIC = 0x0;
        @Override
        @PermissionManuallyEnforced
        public void registerBleLmpEventListener(byte[] address, IBleLmpEventListener listener)
                throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            mBluetoothCccCallback = new BluetoothCccCallback(listener, mContext, mUwbManager);
            mBtCccHal.registerForLmpEvents(
                    mBluetoothCccCallback,
                    PUBLIC,
                    address,
                    new byte[]{LmpEventId.CONNECT_IND, LmpEventId.LL_PHY_UPDATE_IND}); }

        @Override
        @PermissionManuallyEnforced
        public void registerEventCallback(byte[] address, IEventCallback callback)
                throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            mBluetoothCccCallback = new BluetoothCccCallback(callback, mContext, mUwbManager);
            mBtCccHal.registerForLmpEvents(
                    mBluetoothCccCallback,
                    PUBLIC,
                    address,
                    new byte[] {LmpEventId.CONNECT_IND, LmpEventId.LL_PHY_UPDATE_IND});
        }

        @Override
        @PermissionManuallyEnforced
        public void unregisterEventCallback(byte[] address) throws RemoteException {
            enforceUwbPrivilegedPermission(mContext);
            mBtCccHal.unregisterLmpEvents(
                    PUBLIC,
                    address);
            mBluetoothCccCallback.close();
        }
    }
    private final Context mContext;
    private IBluetoothLmpEvent mBtCccHal;
    private CccDkTimeSyncImpl mCccDkTimeSyncImpl;
    private IBinder.DeathRecipient mServiceDeathRecipient;
    private UwbManager mUwbManager;
    //TODO (b/467707737) call serviceCore instead of using uwbManager
    private final UwbInjector mUwbInjector;
    private BluetoothCccCallback mBluetoothCccCallback;

    public TimesyncManager(@NonNull Context context, @NonNull UwbInjector uwbInjector) {
        mContext = context;
        mUwbInjector = uwbInjector;

        try {
            mUwbManager = mContext.getSystemService(UwbManager.class);
            if (mUwbManager == null) {
                Log.e(TAG, "Unable to obtain UwbManager");
                return;
            }

            IBluetoothLmpEvent bluetoothCcc = IBluetoothLmpEvent.Stub.asInterface(
                    ServiceManager.waitForDeclaredService(HAL_INSTANCE_NAME));
            if (bluetoothCcc == null) {
                Log.e(TAG, "Unable to obtain IBluetoothLmpEvent");
                return;
            }
            IBinder serviceBinder = bluetoothCcc.asBinder();
            if (serviceBinder == null) {
                Log.e(TAG, "Unable to obtain service binder");
                return;
            }
            mServiceDeathRecipient = new BluetoothCccDeathRecipient();
            serviceBinder.linkToDeath(mServiceDeathRecipient, /* flags */ 0);

            mBtCccHal = bluetoothCcc;
            mCccDkTimeSyncImpl = new CccDkTimeSyncImpl();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed with remote exception: " + e);
        } catch (Exception e) {
            Log.e(TAG, "Failed with exception: " + e);
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public void mockCreate(@NonNull UwbManager uwbManager, @NonNull IBluetoothLmpEvent hal) {
        mUwbManager = uwbManager;
        mBtCccHal = hal;
        mCccDkTimeSyncImpl = new CccDkTimeSyncImpl();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void mockTimeout(long timeout, TimeUnit timeoutUnit) {
        TIMEOUT = timeout;
        TIMEOUT_UNIT = timeoutUnit;
    }

    private class BluetoothCccDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            Log.e(TAG, "BluetoothCcc service died.");
            mBtCccHal = null;
        }
    }

    public IBinder onBind(Intent intent) {
        if (ICccDkTimeSync.class.getName().equals(intent.getAction())) {
            return mCccDkTimeSyncImpl;
        }
        return null;
    }
}
