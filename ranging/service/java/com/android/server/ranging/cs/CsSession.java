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

package com.android.server.ranging.cs;

import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import static com.android.bluetooth.flags.Flags.includePowerAndRssiInDistanceMeasurementResult;
import static com.android.server.ranging.common.RangingUtils.InternalReason;
import static com.android.server.ranging.common.RangingUtils.convertBluetoothReasonCode;

import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ChannelSoundingParams;
import android.bluetooth.le.DistanceMeasurementManager;
import android.bluetooth.le.DistanceMeasurementMethod;
import android.bluetooth.le.DistanceMeasurementParams;
import android.bluetooth.le.DistanceMeasurementResult;
import android.bluetooth.le.DistanceMeasurementSession;
import android.os.CancellationSignal;
import android.ranging.RangingData;
import android.ranging.RangingDataExtras;
import android.ranging.RangingMeasurement;
import android.ranging.ble.BleSpecificData;
import android.ranging.ble.cs.BleCsConstants;
import android.ranging.ble.cs.BleCsRangingParams;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

class CsSession {
    private static final String TAG = CsSession.class.getSimpleName();
    private static final Map<String, CsSession> sCsSessions = new ConcurrentHashMap<>();

    @GuardedBy("sCsSessions")
    private final List<CsAdapter> mCsAdapters = new ArrayList<>();
    private final Object mLock;
    private final AlarmManager mAlarmManager;
    private AlarmManager.OnAlarmListener mMeasurementLimitListener;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mPeerBluetoothDevice;
    private String mPeerIdentityAddress;
    private CancellationSignal mStartCancellationSignal;
    private DistanceMeasurementSession mSession;
    private CsConfig mConfig;

    static void registerAdapter(
            CsAdapter adapter,
            BluetoothAdapter bluetoothAdapter,
            BluetoothDevice bluetoothDevice,
            AlarmManager alarmManager,
            CsConfig csConfig,
            Object lock) {
        Log.i(TAG, "Registering adapter: " + adapter.getId());

        synchronized (sCsSessions) {
            CsSession currentSession = sCsSessions.get(bluetoothDevice.getIdentityAddress());

            if (currentSession != null) {
                Log.i(TAG,
                        "Joining an existing CsSession attached to device: "
                        + bluetoothDevice);
                currentSession.mCsAdapters.add(adapter);
                currentSession.cancelMeasurementsLimit();
                return;
            }

            Log.i(TAG, "Creating a new CsSession for device: " + bluetoothDevice);
            currentSession = new CsSession(
                    bluetoothAdapter,
                    bluetoothDevice,
                    alarmManager,
                    csConfig,
                    lock);
            currentSession.mCsAdapters.add(adapter);
            sCsSessions.put(bluetoothDevice.getIdentityAddress(), currentSession);
            currentSession.start();
        }
    }

    static void deregisterAdapter(CsAdapter adapter, String peerIdentityAddress) {
        Log.i(TAG, "Deregistering adapter: " + adapter.getId());

        synchronized (sCsSessions) {
            CsSession sessionToStop = sCsSessions.get(peerIdentityAddress);

            if (sessionToStop == null
                    || (sessionToStop.mStartCancellationSignal == null
                            && sessionToStop.mSession == null)) {
                Log.i(TAG, "Attempted to deregister CsAdapter when session was already stopped.");
                return;
            }
            if (sessionToStop.mSession == null) {
                sessionToStop.mStartCancellationSignal.cancel(); // In the middle of starting
                return;
            }

            sessionToStop.mCsAdapters.removeIf(c -> c == adapter);

            // Reinsate measurements limit alarm when only 1 adapter active
            if (sessionToStop.mCsAdapters.size() == 1) {
                sessionToStop.setMeasurementsLimit();
            } else if (sessionToStop.mCsAdapters.isEmpty()) {
                Log.i(TAG, "Last adapter left. Closing ranging session");
                sessionToStop.close();
            }
        }
    }

    CsSession(
            BluetoothAdapter bluetoothAdapter,
            BluetoothDevice bluetoothDevice,
            AlarmManager alarmManager,
            CsConfig config,
            Object lock) {
        mBluetoothAdapter = bluetoothAdapter;
        mPeerBluetoothDevice = bluetoothDevice;
        mPeerIdentityAddress = mPeerBluetoothDevice.getIdentityAddress();
        mAlarmManager = alarmManager;
        mConfig = config;
        mLock = lock;
    }

    void start() {
        Log.i(TAG, "start called");

        BleCsRangingParams bleCsRangingParams = mConfig.getRangingParams();

        DistanceMeasurementManager distanceMeasurementManager =
                mBluetoothAdapter.getDistanceMeasurementManager();
        int duration = DistanceMeasurementParams.getMaxDurationSeconds();
        int frequency = getFrequency(bleCsRangingParams.getRangingUpdateRate());
        int methodId = DistanceMeasurementMethod.DISTANCE_MEASUREMENT_METHOD_CHANNEL_SOUNDING;

        DistanceMeasurementParams params =
                new DistanceMeasurementParams.Builder(mPeerBluetoothDevice)
                        .setChannelSoundingParams(new ChannelSoundingParams.Builder()
                                .setLocationType(bleCsRangingParams.getLocationType())
                                .setCsSecurityLevel(bleCsRangingParams.getSecurityLevel())
                                .setSightType(bleCsRangingParams.getSightType())
                                .build())
                        .setDurationSeconds(duration)
                        .setFrequency(frequency)
                        .setMethodId(methodId)
                        .build();

        try {
            mStartCancellationSignal =
                    distanceMeasurementManager.startMeasurementSession(
                        params,
                        Executors.newSingleThreadExecutor(),
                        mDistanceMeasurementCallback);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error starting BT Channel Sounding session", e);
            closeAllAdapters(InternalReason.INTERNAL_ERROR);
            return;
        }

        if (mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
            setMeasurementsLimit();
        }
    }

    void close() {
        Log.i(TAG, "close called");

        synchronized (sCsSessions) {
            if (mSession != null) {
                mSession.stopSession();
            }

            sCsSessions.remove(mPeerIdentityAddress);

            if (mConfig != null && mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
                cancelMeasurementsLimit();
            }
        }

    }

    void closeAllAdapters(@InternalReason int reason) {
        Log.i(TAG, "CloseAllAdapters called");
        synchronized (mLock) {
            synchronized (sCsSessions) {
                for (CsAdapter adapter : mCsAdapters) {
                    adapter.closeForReason(reason);
                }
                close();
            }
        }
    }

    /**
     * Sets/Reinstates the measurements limit alarm on the CsSession instance.
     *
     * <p>Called when only one active adapter present in the CsSession.
     */
    private void setMeasurementsLimit() {
        synchronized (sCsSessions) {
            // limit should be aligned with the only active adapter
            int limit = mCsAdapters.get(0).mConfig.getSessionConfig().getRangingMeasurementsLimit();
            if (limit > 0 && mMeasurementLimitListener == null) {
                Log.i(TAG, "Setting measurements limit for session: " + limit + "m");
                mMeasurementLimitListener = () -> {
                    Log.i(TAG, "Measurements limit exceeded. Stopping the session");
                    Executors.newCachedThreadPool()
                            .execute(() -> this.closeAllAdapters(InternalReason.INTERNAL_ERROR));
                };

                RangingUtils.setMeasurementsLimitTimeout(
                        mAlarmManager,
                        mMeasurementLimitListener,
                        limit,
                        BleCsConstants.getIntervalInMs(
                                mConfig.getRangingParams().getRangingUpdateRate()));
            }
        }
    }

    /**
     * Cancels the measurements limit alarm on the CsSession instance.
     *
     * <p>Called when more than one active adapter present in the CsSession.
     */
    private void cancelMeasurementsLimit() {
        Log.i(TAG, "Cancelling measurement limits");
        if (mMeasurementLimitListener != null) {
            mAlarmManager.cancel(mMeasurementLimitListener);
            mMeasurementLimitListener = null;
        }
    }

    private int getFrequency(int updateRate) {
        if (updateRate == UPDATE_RATE_INFREQUENT) {
            return DistanceMeasurementParams.REPORT_FREQUENCY_LOW;
        } else if (updateRate == UPDATE_RATE_NORMAL) {
            return DistanceMeasurementParams.REPORT_FREQUENCY_MEDIUM;
        } else if (updateRate == UPDATE_RATE_FREQUENT) {
            return DistanceMeasurementParams.REPORT_FREQUENCY_HIGH;
        }
        return DistanceMeasurementParams.REPORT_FREQUENCY_LOW;
    }

    private static RangingData toRangingData(DistanceMeasurementResult result) {
        RangingData.Builder dataBuilder =
            new RangingData.Builder()
                .setRangingTechnology(RangingTechnology.CS.getValue())
                .setDistance(
                    new RangingMeasurement.Builder()
                        .setMeasurement(result.getResultMeters())
                        .setConfidence((int) Math.round(result.getConfidenceLevel() * 2.0))
                        .setRawConfidence(result.getConfidenceLevel())
                        .setError(result.getErrorMeters())
                        .build())
                .setTimestampMillis(
                    RangingUtils.convertNanosToMillis(result.getMeasurementTimestampNanos()))
                .setDelaySpreadMeters(result.getDelaySpreadMeters())
                .setDetectedAttackLevel((byte) result.getDetectedAttackLevel())
                .setVelocityMetersPerSec(result.getVelocityMetersPerSecond());
        if (!Double.isNaN(result.getAzimuthAngle())) {
            dataBuilder.setAzimuth(
                new RangingMeasurement.Builder()
                    .setMeasurement(result.getAzimuthAngle())
                    .setError(result.getErrorAzimuthAngle())
                    .build());
        }
        if (!Double.isNaN(result.getAltitudeAngle())) {
            dataBuilder.setElevation(
                new RangingMeasurement.Builder()
                    .setMeasurement(result.getAltitudeAngle())
                    .setError(result.getErrorAltitudeAngle())
                    .build());
        }
        BleSpecificData.Builder bleCsSpecificDataBuilder =
                new BleSpecificData.Builder().setDelaySpreadMeters(result.getDelaySpreadMeters());

        if (includePowerAndRssiInDistanceMeasurementResult()) {
            dataBuilder.setRssi(result.getRssiDbm());
            bleCsSpecificDataBuilder.setRemoteTxPowerDbm(result.getRemoteTxPowerDbm());
        }

        dataBuilder.setRangingDataExtras(
            new RangingDataExtras.Builder()
                .setBleSpecificData(bleCsSpecificDataBuilder.build())
                .build());

        return dataBuilder.build();
    }

    private DistanceMeasurementSession.Callback mDistanceMeasurementCallback =
            new DistanceMeasurementSession.Callback() {
                @Override
                public void onStarted(@NonNull DistanceMeasurementSession session) {
                    Log.i(TAG, "DistanceMeasurement onStarted!");
                    synchronized (sCsSessions) {
                        mSession = session;
                    }
                }

                @Override
                public void onStartFail(int reason) {
                    Log.i(TAG, "DistanceMeasurement onStartFail! reason " + reason);
                    closeAllAdapters(convertBluetoothReasonCode(reason));
                }

                @Override
                public void onStopped(DistanceMeasurementSession session, int reason) {
                    Log.i(TAG, "DistanceMeasurement onStopped! reason " + reason);
                    closeAllAdapters(convertBluetoothReasonCode(reason));
                }

                @Override
                public void onResult(BluetoothDevice device, DistanceMeasurementResult result) {
                    RangingData rangingData = toRangingData(result);
                    synchronized (mLock) {
                        synchronized (sCsSessions) {
                            for (CsAdapter adapter : mCsAdapters) {
                                if (adapter.getStateMachineState() == CsAdapter.State.STARTED) {
                                    if (!adapter.getDataNotificationManager().shouldSendResult(
                                            result.getResultMeters())) {
                                        Log.i(TAG, "DistanceMeasurement onResult skipping adapter "
                                                + adapter.getId());
                                        continue;
                                    }

                                    Log.i(TAG, "DistanceMeasurement onResult for adapter "
                                            + adapter.getId() + " : " + result.getResultMeters()
                                            + ", " + result.getErrorMeters());
                                    adapter.getCallbacks()
                                            .onRangingData(adapter.mRangingDevice, rangingData);
                                }
                            }
                        }
                    }
                }
            };
}
