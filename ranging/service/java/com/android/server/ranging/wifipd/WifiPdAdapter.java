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

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;

import android.content.AttributionSource;
import android.content.Context;
import android.net.wifi.rtt.WifiRttManager;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingPreference;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.blerssi.BleRssiAdapter;
import com.android.server.ranging.common.DataNotificationManager;
import com.android.server.ranging.common.RangingUtils;
import com.android.server.ranging.common.StateMachine;
import com.android.server.ranging.session.ConfigurationManager;

import com.google.common.util.concurrent.ListeningExecutorService;

public class WifiPdAdapter implements RangingAdapter {
    private static final String TAG = WifiPdAdapter.class.getSimpleName();

    public static final int RANGING_SERVICE_ROLE_SEEKER = 1;
    public static final int RANGING_SERVICE_ROLE_ADVERTISER = 2;
    private final Context mContext;
    private final RangingInjector mRangingInjector;
    private final AttributionSource mAttributionSource;
    private Callback mCallback;

    private final StateMachine<BleRssiAdapter.State> mStateMachine;

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
            @RangingPreference.DeviceRole int role
    ) {
        mContext = context;
        mRangingInjector = injector;
        mAttributionSource = attributionSource;
        mExecutorService = executor;
        mStateMachine = new StateMachine<>(BleRssiAdapter.State.STOPPED);
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
//        PasnConfig pasnConfig = new PasnConfig( < akm >, <cipher >)
// 				.setPassword(password) // String
//                .deviceIdentityKey(deviceIK) // byte[]
//                .build();
//
//        WifiRttProximityDetectionConfig pdConfig = new
//                WifiRttProximityDetectionConfig.Builder( < role >)
//               .setAdvertiserRequireRangeResult( < true | false >)
//                .setContinuousRangingInterval(rangingInterval)
//                .setIngressDistanceMm( < int>)
//                .setEgressDistanceMm( < int>)
//                .preferredRangingChannelFrequencyMHz( < int>)
//                .build();
//
//        ResponderConfig responderConfig = new ResponderConfig.Builder()
//                .setMacAddress( < mac - addr >)
//  		 		.set80211mcSupported( < true | false >)
//                .set80211azNtbSupported( < true | false >)
//                .setChannelWidth( < channel - width >)
//                .setPreamble( < preamble >)
//                .setResponderType(1) // RESPONDER_STA
//                .setSecureRangingConfig(
//                        new SecureRangingConfig.Builder(pasnConfig))
//                .setProximityDetectionConfig(pdConfig)
//                .build();
//
//        RangingRequest request = new RangingRequest.Builder()
//                .addResponder(responderConfig)
//                .build();
//        // Start ranging
//        mWifiRttManager.startContinuousRanging(null /*WorkSource*/, request, executor,
//                callback);

    }

    @Override
    public void stop() {
        mWifiRttManager.cancelRanging(null);
    }

    @Override
    public void appMovedToBackground() {
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != BleRssiAdapter.State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
        }
    }

    @Override
    public void appMovedToForeground() {
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != BleRssiAdapter.State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToForeground();
        }
    }

    @Override
    public void appInBackgroundTimeout() {
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != BleRssiAdapter.State.STOPPED) {
            stop();
        }
    }

    private void closeForReason(@RangingUtils.InternalReason int reason) {
//        if (mRangingDevice != null) {
//            mCallbacks.onStopped(ImmutableSet.of(mRangingDevice), reason);
//        }
        mCallback.onClosed(reason);
        clear();
    }

    private void clear() {
//        if (mConfig != null && mConfig.getSessionConfig().getRangingMeasurementsLimit() > 0) {
//            mAlarmManager.cancel(mMeasurementLimitListener);
//        }
        //mSession = null;
        mCallback = null;
        //mConfig = null;
    }
}
