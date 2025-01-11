/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.ranging.blerssi;

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;

import static com.android.server.ranging.RangingAdapter.Callback.ClosedReason.ERROR;
import static com.android.server.ranging.RangingAdapter.Callback.ClosedReason.FAILED_TO_START;
import static com.android.server.ranging.RangingAdapter.Callback.ClosedReason.SYSTEM_POLICY;
import static com.android.server.ranging.RangingUtils.convertBluetoothReasonCode;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.content.AttributionSource;
import android.content.Context;
import android.os.SystemClock;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingMeasurement;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.util.DataNotificationManager;

import java.util.concurrent.Executors;

public class BleRssiAdapter implements RangingAdapter {

    private static final String TAG = BleRssiAdapter.class.getSimpleName();

    private final Context mContext;
    private final RangingInjector mRangingInjector;
    private final BluetoothAdapter mBluetoothAdapter;
    private final StateMachine<State> mStateMachine;
    private Callback mCallbacks;
    private BluetoothDevice mDeviceFromPeerBluetoothAddress;
    private RangingDevice mRangingDevice;
    private DistanceMeasurementSession mSession;
    private BleRssiConfig mConfig;

    private DataNotificationManager mDataNotificationManager;
    @Nullable
    private AttributionSource mNonPrivilegedAttributionSource;

    private final AlarmManager mAlarmManager;

    private final AlarmManager.OnAlarmListener mMeasurementLimitListener;

    public BleRssiAdapter(@NonNull Context context, RangingInjector rangingInjector) {
        if (!RangingTechnology.RSSI.isSupported(context)) {
            throw new IllegalArgumentException("BT_RSSI system feature not found.");
        }
        mContext = context;
        mRangingInjector = rangingInjector;
        mBluetoothAdapter = context.getSystemService(BluetoothManager.class).getAdapter();
        mStateMachine = new StateMachine<>(State.STOPPED);
        mCallbacks = null;
        mSession = null;
        mConfig = null;
        mDataNotificationManager = new DataNotificationManager(
                new DataNotificationConfig.Builder().build(),
                new DataNotificationConfig.Builder().build()
        );
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mMeasurementLimitListener = () -> {
            Log.i(TAG, "Measurements limit exceeded. Stopping the session");
            Executors.newCachedThreadPool().execute(this::stop);
        };
    }

    public DataNotificationManager getDataNotificationManager() {
        return mDataNotificationManager;
    }

    @VisibleForTesting
    public void setSession(DistanceMeasurementSession session) {
        mSession = session;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.RSSI;
    }

    @Override
    public void start(
            @NonNull RangingSessionConfig.TechnologyConfig config,
            @Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callback
    ) {
        Log.i(TAG, "Start called.");
        mCallbacks = callback;
        mNonPrivilegedAttributionSource = nonPrivilegedAttributionSource;
        if (mNonPrivilegedAttributionSource != null && !mRangingInjector.isForegroundAppOrService(
                mNonPrivilegedAttributionSource.getUid(),
                mNonPrivilegedAttributionSource.getPackageName())) {
            Log.w(TAG, "Background ranging is not supported");
            closeForReason(SYSTEM_POLICY);
            return;
        }
        if (!(config instanceof BleRssiConfig bleRssiConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            closeForReason(ERROR);
            return;
        }
        BleRssiRangingParams bleRssiRangingParams = bleRssiConfig.getRangingParams();
        if ((bleRssiConfig.getPeerDevice() == null)
                || (bleRssiRangingParams.getPeerBluetoothAddress() == null)) {
            Log.e(TAG, "Peer device is null");
            closeForReason(ERROR);
            return;
        }
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            closeForReason(FAILED_TO_START);
            return;
        }

        mConfig = bleRssiConfig;
        mRangingDevice = bleRssiConfig.getPeerDevice();
        mDeviceFromPeerBluetoothAddress =
                mBluetoothAdapter.getRemoteDevice(bleRssiRangingParams.getPeerBluetoothAddress());
        DistanceMeasurementManager distanceMeasurementManager =
                mBluetoothAdapter.getDistanceMeasurementManager();

        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mDeviceFromPeerBluetoothAddress)
                        .setDurationSeconds(DistanceMeasurementParams.getDefaultDurationSeconds())
                        .setFrequency(getBleRssiFrequency(
                                bleRssiConfig.getRangingParams().getRangingUpdateRate()))
                        .setMethodId(DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_RSSI)
                        .build();
        mDataNotificationManager = new DataNotificationManager(
                bleRssiConfig.getSessionConfig().getDataNotificationConfig(),
                bleRssiConfig.getSessionConfig().getDataNotificationConfig());

        distanceMeasurementManager.startMeasurementSession(params,
                Executors.newSingleThreadExecutor(), mDistanceMeasurementCallback);
        // Added callback here to be consistent with other ranging technology.
        mCallbacks.onStarted(bleRssiConfig.getPeerDevice());
        if (mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
            RangingUtils.setMeasurementsLimitTimeout(
                    mAlarmManager,
                    mMeasurementLimitListener,
                    mConfig.getSessionConfig().getRangingMeasurementsLimit(),
                    getIntervalInMs(mConfig.getRangingParams().getRangingUpdateRate()));
        }
    }

    public enum State {
        STARTED,
        STOPPED,
    }

    public static int getBleRssiFrequency(int updateRate) {
        switch (updateRate) {
            case UPDATE_RATE_INFREQUENT:
                return DistanceMeasurementParams.REPORT_FREQUENCY_LOW;
            case UPDATE_RATE_FREQUENT:
                return DistanceMeasurementParams.REPORT_FREQUENCY_HIGH;
            default:
                return DistanceMeasurementParams.REPORT_FREQUENCY_MEDIUM;
        }
    }

    public static int getIntervalInMs(int updateRate) {
        switch (updateRate) {
            case UPDATE_RATE_FREQUENT -> {
                return 500;
            }
            case UPDATE_RATE_INFREQUENT -> {
                return 3000;
            }
            default -> {
                return 1000;
            }
        }
    }

    @Override
    public void appMovedToBackground() {
        if (mNonPrivilegedAttributionSource != null && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
        }
    }

    @Override
    public void appMovedToForeground() {
        if (mNonPrivilegedAttributionSource != null && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToForeground();
        }
    }

    @Override
    public void appInBackgroundTimeout() {
        if (mNonPrivilegedAttributionSource != null && mStateMachine.getState() != State.STOPPED) {
            stop();
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");
        if (!mStateMachine.transition(State.STARTED, State.STOPPED)) {
            Log.v(TAG, "Attempted to stop adapter when it was already stopped");
            return;
        }

        if (mSession == null) {
            Log.v(TAG, "Attempted to stop adapter when ranging session was already stopped");
            return;
        }
        mSession.stopSession();
    }

    private void clear() {
        if (mConfig != null && mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
            mAlarmManager.cancel(mMeasurementLimitListener);
        }
        mSession = null;
        mCallbacks = null;
        mConfig = null;
    }

    private void closeForReason(@Callback.ClosedReason int reason) {
        if (mRangingDevice != null) {
            mCallbacks.onStopped(mRangingDevice);
        }
        mCallbacks.onClosed(reason);
        clear();
    }

    @VisibleForTesting
    public final DistanceMeasurementSession.Callback mDistanceMeasurementCallback =
            new DistanceMeasurementSession.Callback() {
                public void onStarted(DistanceMeasurementSession session) {
                    Log.i(TAG, "DistanceMeasurement onStarted !");
                    mSession = session;
                    // onStarted is called right after start measurement is called, other ranging
                    // technologies do not wait for this callback till they find the peer, if peer
                    // is not found here, we get onStartFail.
                    //mCallbacks.onStarted(mConfig.getPeerDevice());
                }

                public void onStartFail(int reason) {
                    Log.i(TAG, "DistanceMeasurement onStartFail ! reason " + reason);
                    closeForReason(Callback.ClosedReason.FAILED_TO_START);
                }

                public void onStopped(DistanceMeasurementSession session, int reason) {
                    Log.i(TAG, "DistanceMeasurement onStopped ! reason " + reason);
                    closeForReason(convertBluetoothReasonCode(reason));
                }

                public void onResult(BluetoothDevice device, DistanceMeasurementResult result) {
                    if (!mDataNotificationManager.shouldSendResult(result.getResultMeters())) {
                        return;
                    }
                    Log.i(TAG, "DistanceMeasurement onResult ! "
                            + result.getResultMeters()
                            + ", "
                            + result.getErrorMeters());
                    RangingData.Builder dataBuilder = new RangingData.Builder()
                            .setRangingTechnology(RangingManager.BLE_RSSI)
                            .setDistance(new RangingMeasurement.Builder()
                                    .setMeasurement(result.getResultMeters())
                                    .build())
                            // DistanceMeasurementResult#getMeasurementTimestampNanos is flagged
                            // with FLAG_CHANNEL_SOUNDING_25Q2_APIS, check whether we can use that
                            // instead.
                            .setTimestampMillis(SystemClock.elapsedRealtime());
                    if (!Double.isNaN(result.getAzimuthAngle())) {
                        dataBuilder.setAzimuth(new RangingMeasurement.Builder()
                                .setMeasurement(result.getAzimuthAngle())
                                .build());
                    }
                    if (!Double.isNaN(result.getAltitudeAngle())) {
                        dataBuilder.setElevation(new RangingMeasurement.Builder()
                                .setMeasurement(result.getAltitudeAngle())
                                .build());
                    }

                    synchronized (mStateMachine) {
                        if (mStateMachine.getState() == State.STARTED) {
                            mCallbacks.onRangingData(mRangingDevice, dataBuilder.build());
                        }
                    }
                }
            };
}
