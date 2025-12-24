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

package com.android.server.ranging.wifipd;

import static android.net.wifi.rtt.PasnConfig.AKM_PASN;
import static android.net.wifi.rtt.PasnConfig.CIPHER_GCMP_256;
import static android.net.wifi.rtt.ProximityDetectionConfig.RANGING_SERVICE_ROLE_ADVERTISER;
import static android.net.wifi.rtt.ProximityDetectionConfig.RANGING_SERVICE_ROLE_SEEKER;
import static android.net.wifi.rtt.ResponderConfig.RESPONDER_STA;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import static com.android.server.ranging.common.RangingUtils.InternalReason.INTERNAL_ERROR;

import android.content.AttributionSource;
import android.content.Context;
import android.net.wifi.rtt.ContinuousRangingResultCallback;
import android.net.wifi.rtt.PasnConfig;
import android.net.wifi.rtt.ProximityDetectionConfig;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.ResponderConfig;
import android.net.wifi.rtt.SecureRangingConfig;
import android.net.wifi.rtt.WifiRttManager;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingData;
import android.ranging.RangingDataExtras;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingMeasurement;
import android.ranging.RangingPreference;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;
import android.ranging.wifi.pd.WifiPdRangingParams;
import android.ranging.wifi.rtt.WifiRttSpecificData;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.DataNotificationManager;
import com.android.server.ranging.common.RangingUtils;
import com.android.server.ranging.common.StateMachine;
import com.android.server.ranging.session.ConfigurationManager;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.List;

public class WifiPdAdapter implements RangingAdapter {
    private static final String TAG = WifiPdAdapter.class.getSimpleName();
    private final Context mContext;
    private final RangingInjector mRangingInjector;
    private final AttributionSource mAttributionSource;
    private Callback mCallback;

    private RangingDevice mPeer;
    private final StateMachine<State> mStateMachine;
    private final Object mLock;

    private DataNotificationManager mDataNotificationManager;

    private final ListeningExecutorService mExecutorService;
    private AttributionSource mNonPrivilegedAttributionSource;
    private final int mRangingServiceRole;
    private final WifiRttManager mWifiRttManager;

    public WifiPdAdapter(
            @NonNull Context context,
            RangingInjector injector,
            AttributionSource attributionSource,
            @NonNull ListeningExecutorService executor,
            @NonNull Object lock,
            @RangingPreference.DeviceRole int role
    ) {
        mContext = context;
        mRangingInjector = injector;
        mAttributionSource = attributionSource;
        mExecutorService = executor;
        mStateMachine = new StateMachine<>(State.STOPPED, lock);
        mLock = lock;
        mRangingServiceRole = role == DEVICE_ROLE_RESPONDER ? RANGING_SERVICE_ROLE_ADVERTISER
                : RANGING_SERVICE_ROLE_SEEKER;
        mWifiRttManager = context.getSystemService(WifiRttManager.class);
        mDataNotificationManager = new DataNotificationManager(
                new DataNotificationConfig.Builder().build(),
                new DataNotificationConfig.Builder().build()
        );
    }

    @NonNull
    @Override
    public RangingTechnology getTechnology() {
        return RangingTechnology.WIFI_PD;
    }

    @Override
    public void start(@NonNull ConfigurationManager.TechnologyConfig config,
            @Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callback) {
        Log.i(TAG, "Start called.");
        mCallback = callback;
        mNonPrivilegedAttributionSource = nonPrivilegedAttributionSource;
        if (mNonPrivilegedAttributionSource != null && !mRangingInjector.isForegroundAppOrService(
                mNonPrivilegedAttributionSource.getUid(),
                mNonPrivilegedAttributionSource.getPackageName())) {
            Log.w(TAG, "Background ranging is not supported");
            closeForReason(RangingUtils.InternalReason.BACKGROUND_RANGING_POLICY);
            return;
        }

        if (!(config instanceof WifiPdConfig wifiPdConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            mCallback.onClosed(INTERNAL_ERROR);
            return;
        }

        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            closeForReason(INTERNAL_ERROR);
            return;
        }

        WifiPdRangingParams wifiPdRangingParams = wifiPdConfig.getPdRangingParams();
        mPeer = wifiPdConfig.getPeerDevice();
        mDataNotificationManager = new DataNotificationManager(
                wifiPdConfig.getSessionConfig().getDataNotificationConfig(),
                wifiPdConfig.getSessionConfig().getDataNotificationConfig()
        );
        PasnConfig.Builder pasnConfigBuilder = new PasnConfig.Builder(AKM_PASN, CIPHER_GCMP_256);
        if (wifiPdRangingParams.getPasnMode()
                == WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE) {
            if (wifiPdRangingParams.getPassword() == null
                    || wifiPdRangingParams.getDeviceIk() == null) {
                Log.e(TAG,
                        " Password or DeviceIK cannot be null when using Authenticated PASN mode");
                closeForReason(INTERNAL_ERROR);
                return;
            }
            pasnConfigBuilder
                    .setPassword(wifiPdRangingParams.getPassword())
                    .setProximityDetectionSeekerDeviceIdentityKey(
                            wifiPdRangingParams.getDeviceIk());
        }

        // TODO: look into ingress and egress for data manager edge trigger
        //                .setIngressDistanceMm(ingressMm)
        //                .setEgressDistanceMm(egressMm)
        ProximityDetectionConfig pdConfig = new
                ProximityDetectionConfig.Builder(mRangingServiceRole)
                .setAdvertiserRequireRangeResult(
                        wifiPdConfig.getSessionConfig().getDataNotificationConfig()
                                .getNotificationConfigType()
                                != DataNotificationConfig.NOTIFICATION_CONFIG_DISABLE)
                .setDiscoveryChannelFrequencyMhz(
                        wifiPdRangingParams.getDiscoveryChannelFrequencyMhz())
                .setContinuousRangingIntervalMillis(
                        (int) wifiPdConfig.getRangingInterval().toMillis())
                .build();

        ResponderConfig responderConfig = new ResponderConfig.Builder()
                .setMacAddress(wifiPdRangingParams.getPeerMacAddress())
                .set80211azNtbSupported(wifiPdRangingParams.isResponder80211azNtbSupported())
                .setChannelWidth(wifiPdRangingParams.getChannelWidth())
                .setPreamble(wifiPdRangingParams.getPreambleType())
                .setResponderType(RESPONDER_STA)
                .setSecureRangingConfig(
                        new SecureRangingConfig.Builder(pasnConfigBuilder.build()).build())
                .setProximityDetectionConfig(pdConfig)
                .build();

        RangingRequest request = new RangingRequest.Builder()
                .addResponder(responderConfig)
                .build();
        mWifiRttManager.startContinuousRanging(null /*WorkSource*/, request, mExecutorService,
                mContinuousRangingResultCallback);
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called.");
        if (!mStateMachine.transition(State.STARTED, State.STOPPED)) {
            Log.v(TAG, "Attempted to stop adapter when it was already stopped");
            return;
        }

        if (mWifiRttManager == null) {
            return;
        }
        mWifiRttManager.cancelRanging(null);
    }

    @Override
    public void appMovedToBackground() {
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
        }
    }

    @Override
    public void appMovedToForeground() {
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToForeground();
        }
    }

    @Override
    public void appInBackgroundTimeout() {
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != State.STOPPED) {
            stop();
        }
    }

    public void closeForReason(@RangingUtils.InternalReason int reason) {
        synchronized (mLock) {
            mStateMachine.setState(State.STOPPED);
            if (mCallback != null) {
                mCallback.onStopped(ImmutableSet.of(mPeer), reason);
                mCallback.onClosed(reason);
            }
            clear();
        }
    }

    private void clear() {
        mCallback = null;
        mContinuousRangingResultCallback = null;
    }

    public enum State {
        STARTED,
        STOPPED,
    }

    private ContinuousRangingResultCallback mContinuousRangingResultCallback;

    {
        new ContinuousRangingResultCallback() {
            @Override
            public void onRangingFailure(int reason) {
                Log.e(TAG, "onRangingFailure: " + reason);
                closeForReason(convertReason(reason));
            }

            @Override
            public void onRangingStopped(int reason) {
                Log.e(TAG, "onRangingStopped: " + reason);
                closeForReason(convertReason(reason));
            }

            @Override
            public void onRangingResults(@NonNull List<RangingResult> results) {
                if (results == null || results.isEmpty()) {
                    Log.w(TAG, "Wifi PD range results are empty");
                }
                RangingResult result = results.get(0);
                int status = result.getStatus();
                if (status != RangingResult.STATUS_SUCCESS) {
                    closeForReason(convertReason(status));
                    return;
                }
                RangingData.Builder rangingDataBuilder = new RangingData.Builder()
                        .setRangingTechnology(RangingManager.WIFI_PD)
                        .setDistance(new RangingMeasurement.Builder()
                                .setMeasurement(result.getDistanceMm() / 1000.0)
                                .build())
                        .setRssi(result.getRssi())
                        .setTimestampMillis(result.getRangingTimestampMillis());

                rangingDataBuilder.setRangingDataExtras(new RangingDataExtras.Builder()
                        .setRttSpecificData(new WifiRttSpecificData.Builder()
                                .setNumSuccessfulMeasurements(result.getNumSuccessfulMeasurements())
                                .setNumAttemptedMeasurements(result.getNumAttemptedMeasurements())
                                .setMeasurementBandwidth((int) result.getMeasurementBandwidth())
                                .setMeasurementChannelFrequencyMHz(
                                        result.getMeasurementChannelFrequencyMHz())
                                .setLci(result.getLci())
                                .setDistanceStandardDeviationMeters(
                                        result.getDistanceStdDevMm() / 1000.0)
                                .build())
                        .build());
                synchronized (mLock) {
                    if (mStateMachine.getState() == State.STARTED) {
                        mCallback.onRangingData(mPeer, rangingDataBuilder.build());
                    }
                }
            }
        };
    }

    private static @RangingUtils.InternalReason int convertReason(int reason) {
        return switch (reason) {
            case RangingResult.STATUS_FAIL,
                 RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC ->
                    RangingUtils.InternalReason.UNSUPPORTED;
            case RangingResult.STATUS_BUSY_TRY_LATER -> RangingUtils.InternalReason.SYSTEM_POLICY;
            default -> RangingUtils.InternalReason.UNKNOWN;
        };
    }
}