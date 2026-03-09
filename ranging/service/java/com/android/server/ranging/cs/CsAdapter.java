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

package com.android.server.ranging.cs;

import static com.android.server.ranging.common.RangingUtils.InternalReason;

import android.annotation.Nullable;
import android.app.AlarmManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.AttributionSource;
import android.content.Context;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.ble.cs.BleCsRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.DataNotificationManager;
import com.android.server.ranging.common.StateMachine;
import com.android.server.ranging.session.ConfigurationManager;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import java.util.UUID;

public class CsAdapter implements RangingAdapter {
    private static final String TAG = CsAdapter.class.getSimpleName();

    private final String mId;
    private final StateMachine<State> mStateMachine;
    private final Context mContext;
    private final RangingInjector mRangingInjector;
    private final BluetoothAdapter mBluetoothAdapter;
    private final Object mLock;

    /** Invariant: non-null while a ranging session is active */
    private final AlarmManager mAlarmManager;
    private BluetoothDevice mPeerBluetoothDevice;
    private String mPeerIdentityAddress;
    private DataNotificationManager mDataNotificationManager;
    private AttributionSource mNonPrivilegedAttributionSource;
    private Callback mCallbacks;

    RangingDevice mRangingDevice;
    CsConfig mConfig;

    enum State {
        STARTED,
        STOPPED,
    }

    /**
     * Every instance of the CsAdapter must share the same lock parameter
     */
    public CsAdapter(@NonNull Context context, RangingInjector rangingInjector, Object lock) {
        if (!RangingTechnology.CS.isSupported(context)) {
            throw new IllegalArgumentException("BT_CS system feature not found.");
        }
        mContext = context;
        mRangingInjector = rangingInjector;
        mLock = lock;
        mId = UUID.randomUUID().toString();

        mCallbacks = null;
        mBluetoothAdapter = context.getSystemService(BluetoothManager.class).getAdapter();
        mStateMachine = new StateMachine<>(State.STOPPED, mLock);
        mAlarmManager = mContext.getSystemService(AlarmManager.class);
        mDataNotificationManager = new DataNotificationManager(
                new DataNotificationConfig.Builder().build(),
                new DataNotificationConfig.Builder().build());
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.CS;
    }

    public DataNotificationManager getDataNotificationManager() {
        return mDataNotificationManager;
    }

    public String getId() {
        return mId;
    }

    public Callback getCallbacks() {
        return mCallbacks;
    }

    public State getStateMachineState() {
        return mStateMachine.getState();
    }

    @Override
    public void start(
            @NonNull ConfigurationManager.TechnologyConfig config,
            @Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callback
    ) {
        Log.i(TAG, "Start called.");
        mCallbacks = callback;
        mNonPrivilegedAttributionSource = nonPrivilegedAttributionSource;

        if (mNonPrivilegedAttributionSource != null && !mRangingInjector.isForegroundAppOrService(
                mNonPrivilegedAttributionSource.getUid(),
                mNonPrivilegedAttributionSource.getPackageName())) {
            Log.e(TAG, "Background ranging is not supported");
            closeForReason(InternalReason.BACKGROUND_RANGING_POLICY);
            return;
        }
        if (!(config instanceof CsConfig csConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            closeForReason(InternalReason.INTERNAL_ERROR);
            return;
        }

        mConfig = csConfig;
        BleCsRangingParams bleCsRangingParams = mConfig.getRangingParams();
        mRangingDevice = Iterables.getOnlyElement(mConfig.getPeerDevices());

        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_OFF) {
            Log.e(TAG, "Failed to start ranging, Bluetooth is turned off!");
            closeForReason(InternalReason.UNSUPPORTED);
            return;
        }
        if (mConfig.getPeerBluetoothDevice() != null) {
            mPeerBluetoothDevice = mConfig.getPeerBluetoothDevice();
            Log.v(TAG,
                    "BluetoothDevice is provided. Using it instead of the address.");
        } else {
            mPeerBluetoothDevice =
                    mBluetoothAdapter.getRemoteDevice(bleCsRangingParams.getPeerBluetoothAddress());
            Log.v(TAG, "BluetoothDevice not provided, using provided BLE address");
        }
        mPeerIdentityAddress = mPeerBluetoothDevice.getIdentityAddress();
        mDataNotificationManager = new DataNotificationManager(
                mConfig.getSessionConfig().getDataNotificationConfig(),
                mConfig.getSessionConfig().getDataNotificationConfig());

        synchronized (mLock) {
            // This either starts a new DistanceMeasurementSession
            // Or attach this adapter to an existing one
            CsSession.registerAdapter(
                    this,
                    mBluetoothAdapter,
                    mPeerBluetoothDevice,
                    mAlarmManager,
                    mConfig,
                    mLock);

            // Adapter must not start until DistanceMeasurementSession is started
            if (mStateMachine.transition(State.STOPPED, State.STARTED)) {
                mCallbacks.onStarted(ImmutableSet.of(mRangingDevice));
            } else {
                CsSession.deregisterAdapter(this, mPeerIdentityAddress);
                closeForReason(InternalReason.INTERNAL_ERROR);
            }
        }
    }

    @Override
    public void appMovedToBackground() {
        Log.i(TAG, "app moved to background");
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
        }
    }

    @Override
    public void appMovedToForeground() {
        Log.i(TAG, "app moved to foreground");
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != State.STOPPED) {
            mDataNotificationManager.updateConfigAppMovedToForeground();
        }
    }

    @Override
    public void appInBackgroundTimeout() {
        Log.i(TAG, "app in background timeout");
        if (mNonPrivilegedAttributionSource != null
                && mStateMachine.getState() != State.STOPPED) {
            stop();
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop called for CsAdapter : " + mId);
        CsSession.deregisterAdapter(this, mPeerIdentityAddress);
        closeForReason(InternalReason.LOCAL_REQUEST);
    }

    public void closeForReason(@InternalReason int reason) {
        Log.i(TAG, "CloseForReason called");
        synchronized (mLock) {
            if (mStateMachine.transition(State.STARTED, State.STOPPED)) {
                if (mRangingDevice != null) {
                    mCallbacks.onStopped(ImmutableSet.of(mRangingDevice), reason);
                }
            }

            if (mCallbacks != null) {
                mCallbacks.onClosed(reason);
                clear();
            }
        }
    }

    private void clear() {
        mRangingDevice = null;
        mPeerBluetoothDevice = null;
        mPeerIdentityAddress = null;
        mDataNotificationManager = null;
        mNonPrivilegedAttributionSource = null;
        mCallbacks = null;
    }

}

