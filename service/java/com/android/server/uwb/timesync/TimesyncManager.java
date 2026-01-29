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

import static android.uwb.UwbManager.MESSAGE_TYPE_COMMAND;

import static com.android.server.uwb.util.DataTypeConversionUtil.bluetoothAddressToBytes;
import static com.android.server.uwb.util.DataTypeConversionUtil.bytesToStringBluetoothAddress;

import android.annotation.NonNull;
import android.annotation.RequiresNoPermission;
import android.bluetooth.BluetoothDevice.BluetoothAddress;
import android.content.Context;
import android.hardware.bluetooth.lmp_event.IBluetoothLmpEvent;
import android.hardware.bluetooth.lmp_event.IBluetoothLmpEventCallback;
import android.hardware.bluetooth.lmp_event.LmpEventId;
import android.hardware.bluetooth.lmp_event.Timestamp;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.uwb.UwbManager;
import android.uwb.timesync.ITimesyncCallbackListener;
import android.uwb.timesync.TimesyncEvent;

import androidx.annotation.VisibleForTesting;

import com.android.server.uwb.UwbContext;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.data.UwbVendorUciResponse;
import com.android.server.uwb.jni.NativeUwbManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    public static final int DEVICE_TIME_UNCERTAINTY = 18000;

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

    // This comes from UwbServiceCore
    public static final int SEND_VENDOR_CMD_TIMEOUT_MS = 10000;

    // This is constant for the most part, but when doing unit tests we'll
    // force it lower to tests aren't so slow.
    private static long sTimeout = 5;
    private static TimeUnit sTimeoutUnit = TimeUnit.SECONDS;

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
    private static int encodeTimeUncertainty(int uncertaintyUs) {
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

    private static class BluetoothCccCallback extends IBluetoothLmpEventCallback.Stub {

        private Object mClockLock = new Object();

        // A small structure to pass results between threads w/ the BlockingQueue.
        private static class TimestampConversionResult {
            TimestampConversionResult(byte status, long timestampUs, int uncertaintyUs) {
                mStatus = status;
                mTimestampUs = timestampUs;
                mUncertaintyUs = uncertaintyUs;
            }

            TimestampConversionResult(byte status) {
                this(status, 0, 0);
            }

            byte mStatus;
            long mTimestampUs;
            int mUncertaintyUs;
        }

        private static class BleTimestamp {
            private long mUwbTimestamp;
            private int mDeviceTimeUncertainty;
            private int mMaxClockSkewPpm;

            private BleTimestamp(
                    long uwbTimestamp,
                    int deviceTimeUncertainty,
                    int maxClockSkewPpm) {
                mUwbTimestamp = uwbTimestamp;
                mDeviceTimeUncertainty = deviceTimeUncertainty;
                mMaxClockSkewPpm = maxClockSkewPpm;
            }

            public long getUwbTimestamp() {
                return mUwbTimestamp;
            }

            public int getDeviceTimeUncertainty() {
                return mDeviceTimeUncertainty;
            }

            public int getMaxClockSkewPpm() {
                return mMaxClockSkewPpm;
            }
        }

        private BleTimestamp timestampFromHal(Timestamp timestamp) {
            if (timestamp.systemTimeUs == timestamp.bluetoothTimeUs) {
                Log.i(TAG, "Using combo chip systemtime==uwbstime");
                // If the device has combo chip, use system time as uwb time is in the same domain.
                return new BleTimestamp(
                        timestamp.systemTimeUs,
                        mUwbInjector.getDeviceConfigFacade().getTimesyncUncertaintyUs(),
                        mUwbInjector.getDeviceConfigFacade().getTimesyncClockSkewPpm()
                );
            } else if (mUwbInjector.getDeviceConfigFacade().isAndroidSpecificTimesyncSupported()) {
                // If Android proprietary timesync calculation is supported
                Log.i(TAG, "Android specific timesync calculation");
                return androidUwbTimestamp(timestamp);
            } else if (mUwbInjector.getUwbServiceCore().getCachedSpecificationParams(
                    null).getFiraSpecificationParams().getUciVersionSupported() >= 2) {
                // Fira based uci query uwbs timestamp
                Log.i(TAG, "Fira 2.0 UCI query uwbs timestamp based timesync");
                return uciUwbTimestamp(timestamp);
            } else {
                // All other scenarios
                Log.i(TAG, "Default timesync calculation");
                return defaultTimestamp(timestamp);
            }
        }

        private BleTimestamp androidUwbTimestamp(Timestamp timestamp) {
            // Kick off a call to the UWB HAL via a UCI message. We'll get
            // a callback and the response will be passed back via a queue.
            ByteBuffer systemTimeBuffer = ByteBuffer.allocate(16);
            systemTimeBuffer.order(ByteOrder.LITTLE_ENDIAN);
            systemTimeBuffer.putLong(timestamp.systemTimeUs);
            systemTimeBuffer.putLong(timestamp.bluetoothTimeUs);

            FutureTask<UwbVendorUciResponse> sendVendorCmdTask = new FutureTask<>(
                    () -> mNativeUwbManager.sendRawVendorCmd(MESSAGE_TYPE_COMMAND,
                            ANDROID_GID, ANDROID_TIMESTAMP_ANCHOR_TO_UWBS,
                            systemTimeBuffer.array(), null));

            UwbVendorUciResponse response = null;
            int uciStatus = UwbUciConstants.STATUS_CODE_FAILED;
            try {
                response = mUwbInjector.runTaskOnSingleThreadExecutorUci(sendVendorCmdTask,
                        SEND_VENDOR_CMD_TIMEOUT_MS);
                uciStatus = response.status;
            } catch (TimeoutException e) {
                Log.i(TAG, "Failed to send vendor command - status : TIMEOUT");
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }

            if (uciStatus != UwbManager.SEND_VENDOR_UCI_SUCCESS) {
                throw new RuntimeException(String.format("sendVendorUciMessage failed: %d",
                        uciStatus));
            }

            // status (1), timestamp (8), uncertainty (4) = 13 bytes
            final int expectedPayloadLen = 13;

            byte[] payload = response.payload;
            TimestampConversionResult result;
            ByteBuffer convBuffer = ByteBuffer.wrap(payload);
            convBuffer.order(ByteOrder.LITTLE_ENDIAN);

            byte convStatus = convBuffer.get();
            if (convStatus != UCI_STATUS_OK) {
                /* Caller handles/prints errors since some non-OK statuses are expected */
                result = new TimestampConversionResult(convStatus);
            } else if (payload.length < expectedPayloadLen) {
                Log.e(TAG, String.format("Timestamp conversion response too short: %d < %d",
                        payload.length, expectedPayloadLen));
                result = new TimestampConversionResult(UCI_STATUS_INVALID_MESSAGE_SIZE);
            } else {
                if (payload.length != expectedPayloadLen) {
                    Log.i(TAG, String.format(
                            "Ignoring extra bytes in timestamp conversion response: %d > %d",
                            payload.length, expectedPayloadLen));
                }
                result = new TimestampConversionResult(convStatus,
                        convBuffer.getLong(), convBuffer.getInt());
            }

            long uwbsTimeOffsetUs;
            int uwbsConversionUncertaintyUs;
            if (result.mStatus == UCI_STATUS_OK) {
                // The UWB HAL gave us an absolute time, but convert to an
                // offset so the math is the same if we don't need to convert.
                uwbsTimeOffsetUs = result.mTimestampUs - timestamp.systemTimeUs;
                uwbsConversionUncertaintyUs = result.mUncertaintyUs;
            } else if (result.mStatus == UCI_STATUS_UNKNOWN_GID
                    || result.mStatus == UCI_STATUS_UNKNOWN_OID) {
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
                Log.i(TAG, String.format("UWB HAL doesn't convert timestamps: %d", result.mStatus));
                uwbsTimeOffsetUs = mUwbInjector.getDeviceConfigFacade().getTimesyncDeviceOffset();
                uwbsConversionUncertaintyUs = DEVICE_TIME_UNCERTAINTY;
            } else {
                throw new RuntimeException(String.format("Timestamp conversion failed: %d",
                        result.mStatus));
            }

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
                    Math.max(encodeTimeUncertainty(uwbsConversionUncertaintyUs),
                            mUwbInjector.getDeviceConfigFacade().getTimesyncUncertaintyUs()),
                    mUwbInjector.getDeviceConfigFacade().getTimesyncClockSkewPpm());
        }

        private BleTimestamp uciUwbTimestamp(Timestamp timestamp) {
            long systemTimestampUs0;
            long uwbDeviceTime;
            long systemTimestampUs1;
            long elapsedTime;
            long uwbTimeOffsetUs;
            long bestTimeOffsetUs = 0; // The offset between UWB and System clocks
            long bestElapsedTime = Long.MAX_VALUE;

            // Perform 3 iterations to find the execution path with the lowest latency (lowest
            // jitter).
            // A shorter elapsedTime means the midpoint calculation is more accurate.
            for (int i = 0; i < 3; i++) {
                systemTimestampUs0 = SystemClock.elapsedRealtimeNanos() / 1000;

                synchronized (mClockLock) {
                    // Fetch the current absolute microsecond counter from the UWB hardware
                    uwbDeviceTime = mUwbInjector.getUwbServiceCore().queryUwbsTimestampMicros();
                }

                systemTimestampUs1 = SystemClock.elapsedRealtimeNanos() / 1000;
                elapsedTime = systemTimestampUs1 - systemTimestampUs0;

                // Calculate the offset using the 'Midpoint' assumption:
                // We assume the hardware captured its timestamp exactly halfway through the
                // request.
                // Offset = UWB_Hardware_Time - Estimated_System_Time_At_Capture
                uwbTimeOffsetUs = uwbDeviceTime - (systemTimestampUs0 + elapsedTime / 2);

                // Keep the result from the "tightest" bracket (least amount of system noise).
                if (bestElapsedTime > elapsedTime) {
                    bestElapsedTime = elapsedTime;
                    bestTimeOffsetUs = uwbTimeOffsetUs;
                }
            }

            return new BleTimestamp(
                    timestamp.systemTimeUs + bestTimeOffsetUs,
                    Math.max(encodeTimeUncertainty((int) (bestElapsedTime / 2)),
                            mUwbInjector.getDeviceConfigFacade().getTimesyncUncertaintyUs()),
                    mUwbInjector.getDeviceConfigFacade().getTimesyncClockSkewPpm()
            );
        }

        private BleTimestamp defaultTimestamp(Timestamp timestamp) {
            return new BleTimestamp(
                    // Shift the system time by a pre-configured device-specific offset
                    timestamp.systemTimeUs
                            + mUwbInjector.getDeviceConfigFacade().getTimesyncDeviceOffset(),
                    Math.max(encodeTimeUncertainty(DEVICE_TIME_UNCERTAINTY),
                            mUwbInjector.getDeviceConfigFacade().getTimesyncUncertaintyUs()),
                    mUwbInjector.getDeviceConfigFacade().getTimesyncClockSkewPpm()
            );
        }


        //TODO (b/467707737) check if switch case implementation correct
        private static int directionFromHal(int direction) {
            switch (direction) {
                case TimesyncEvent.DIRECTION_TX:
                    return 1;
                case TimesyncEvent.DIRECTION_RX:
                    return 0;
                default:
                    return 0;
            }
        }

        private static int eventFromHal(int event) {
            switch (event) {
                case TimesyncEvent.CONNECT_IND:
                    return 0;
                case TimesyncEvent.LL_PHY_UPDATE_IND:
                    return 1;
                default:
                    return 0;
            }
        }

        private final ITimesyncCallbackListener mCallbackListener;
        private final NativeUwbManager mNativeUwbManager;
        private final UwbInjector mUwbInjector;

        private BluetoothCccCallback(ITimesyncCallbackListener callback,
                NativeUwbManager nativeUwbManager, UwbInjector uwbInjector) {
            mCallbackListener = callback;
            mNativeUwbManager = nativeUwbManager;
            mUwbInjector = uwbInjector;
        }

        @Override
        @RequiresNoPermission
        public void onEventGenerated(
                Timestamp timestamp,
                byte addressType,
                byte[] address,
                byte direction,
                byte lmpEventId,
                char eventCounter)
                throws RemoteException {
            Log.i(TAG, "Received system timestamp: "
                    + String.format("systemTime=%d, bluetoothTime=%d,",
                            timestamp.systemTimeUs,
                            timestamp.bluetoothTimeUs));
            BleTimestamp mBleTimestamp = timestampFromHal(timestamp);
            Log.i(TAG, "Calculated uwb timestamp: " + String.format(
                    "mUwbTimestamp=%d, mDeviceTimeUncertainty=%d, mMaxClockSkewPpm=%d us",
                    mBleTimestamp.mUwbTimestamp,
                    mBleTimestamp.mDeviceTimeUncertainty,
                    mBleTimestamp.mMaxClockSkewPpm));
            BluetoothAddress bluetoothAddress = new BluetoothAddress(
                    bytesToStringBluetoothAddress(address), addressType);
            //TODO check injector for on delivery check
            if (sAddressCallbackMap.containsKey(bluetoothAddress.getAddress())) {
                sAddressCallbackMap.get(bluetoothAddress.getAddress()).mCallbackListener
                        .onTimesyncEvent(
                                new TimesyncEvent.Builder(
                                        bluetoothAddress.getAddress(),
                                        bluetoothAddress.getAddressType(),
                                        eventFromHal(lmpEventId),
                                        directionFromHal(direction),
                                        mBleTimestamp.getUwbTimestamp(),
                                        mBleTimestamp.getDeviceTimeUncertainty(),
                                        mBleTimestamp.getMaxClockSkewPpm(),
                                        eventCounter)
                                        .build());
            }
        }

        @Override
        @RequiresNoPermission
        public void onRegistered(boolean status) throws RemoteException {
            if (status) {
                Log.i(TAG, "Register Success");
                mCallbackListener.onRegistered();
            } else {
                Log.i(TAG, "Register Failed");
                mCallbackListener.onRegisterFailed();
            }
        }

        @Override
        @RequiresNoPermission
        public String getInterfaceHash() {
            return IBluetoothLmpEventCallback.HASH;
        }

        @Override
        @RequiresNoPermission
        public int getInterfaceVersion() {
            return IBluetoothLmpEventCallback.VERSION;
        }
    }

    private final Context mContext;
    private IBluetoothLmpEvent mBtCccHal;
    private IBinder.DeathRecipient mServiceDeathRecipient;
    private NativeUwbManager mNativeUwbManager;
    private final UwbInjector mUwbInjector;
    private static final Map<String, BluetoothCccCallback> sAddressCallbackMap =
            new ConcurrentHashMap<>();


    public TimesyncManager(@NonNull UwbContext context, @NonNull NativeUwbManager nativeUwbManager,
            @NonNull UwbInjector uwbInjector) {
        mContext = context;
        mUwbInjector = uwbInjector;
        mNativeUwbManager = nativeUwbManager;

        try {
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
            //TODO need to check
//            mServiceDeathRecipient = new BluetoothCccDeathRecipient();
//            serviceBinder.linkToDeath(mServiceDeathRecipient, /* flags */ 0);

            mBtCccHal = bluetoothCcc;
        } catch (Exception e) {
            Log.e(TAG, "Failed with exception: " + e);
        }
    }

    /**
     *
     */
    public void registerEventCallback(
            ITimesyncCallbackListener callback,
            BluetoothAddress bluetoothAddress)
            throws RemoteException {
        BluetoothCccCallback bluetoothCccCallback = new BluetoothCccCallback(callback,
                mNativeUwbManager, mUwbInjector);
        sAddressCallbackMap.put(bluetoothAddress.getAddress(), bluetoothCccCallback);
        if (mBtCccHal == null) {
            Log.e(TAG, "Unable to obtain mBtCccHal");
            return;
        }
        mBtCccHal.registerForLmpEvents(
                bluetoothCccCallback,
                (byte) bluetoothAddress.getAddressType(),
                bluetoothAddressToBytes(bluetoothAddress.getAddress()),
                new byte[]{LmpEventId.CONNECT_IND, LmpEventId.LL_PHY_UPDATE_IND});
    }

    public void unregisterEventCallback(ITimesyncCallbackListener callback,
            BluetoothAddress bluetoothAddress)
            throws RemoteException {
        if (sAddressCallbackMap.containsKey(bluetoothAddress.getAddress())
                && sAddressCallbackMap.get(
                bluetoothAddress.getAddress()).mCallbackListener == callback) {
            mBtCccHal.unregisterLmpEvents(
                    (byte) bluetoothAddress.getAddressType(),
                    bluetoothAddressToBytes(bluetoothAddress.getAddress()));
            sAddressCallbackMap.remove(bluetoothAddress.getAddress());
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public void mockCreate(@NonNull IBluetoothLmpEvent hal) {
        mBtCccHal = hal;
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    public static void mockTimeout(long timeout, TimeUnit timeoutUnit) {
        sTimeout = timeout;
        sTimeoutUnit = timeoutUnit;
    }

    private class BluetoothCccDeathRecipient implements IBinder.DeathRecipient {
        @Override
        public void binderDied() {
            Log.e(TAG, "BluetoothCcc service died.");
//            unregisterEventCallback()
            mBtCccHal = null;
        }
    }
}
