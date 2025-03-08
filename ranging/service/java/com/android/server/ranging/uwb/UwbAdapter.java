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

package com.android.server.ranging.uwb;

import static com.android.ranging.uwb.backend.internal.RangingMeasurement.CONFIDENCE_HIGH;
import static com.android.ranging.uwb.backend.internal.RangingMeasurement.CONFIDENCE_MEDIUM;
import static com.android.server.ranging.uwb.UwbConfig.toBackend;

import android.content.AttributionSource;
import android.content.Context;
import android.ranging.DataNotificationConfig;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.RangingPreference;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.RangingController;
import com.android.ranging.uwb.backend.internal.RangingPosition;
import com.android.ranging.uwb.backend.internal.RangingSessionCallback;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbDevice;
import com.android.ranging.uwb.backend.internal.UwbHwSwitchHelper;
import com.android.ranging.uwb.backend.internal.UwbServiceImpl;
import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.RangingUtils;
import com.android.server.ranging.RangingUtils.InternalReason;
import com.android.server.ranging.RangingUtils.StateMachine;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.util.DataNotificationManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Optional;

/** Ranging adapter for Ultra-wideband (UWB). */
public class UwbAdapter implements RangingAdapter {
    private static final String TAG = UwbAdapter.class.getSimpleName();
    private final Context mContext;
    private final RangingInjector mRangingInjector;
    private final com.android.ranging.uwb.backend.internal.RangingDevice mUwbClient;
    private final ListeningExecutorService mExecutorService;
    private final ExecutorResultHandlers mUwbClientResultHandlers = new ExecutorResultHandlers();
    private final RangingSessionCallback mUwbListener = new UwbListener();
    private final StateMachine<State> mStateMachine;
    private final BiMap<RangingDevice, UwbAddress> mPeers;

    private DataNotificationManager mDataNotificationManager;

    /** Invariant: non-null while a ranging session is active */
    private Callback mCallbacks;

    private AttributionSource mNonPrivilegedAttributionSource;
    boolean mIsBackgroundRangingSupported;

    private final AttributionSource mAttributionSource;

    public UwbAdapter(
            @NonNull Context context,
            RangingInjector injector,
            AttributionSource attributionSource,
            @NonNull ListeningExecutorService executor,
            @RangingPreference.DeviceRole int role
    ) {
        this(context, injector, attributionSource, executor,
                role == RangingPreference.DEVICE_ROLE_INITIATOR
                        ? UwbServiceImpl.getController(context, executor)
                        : UwbServiceImpl.getControlee(context, executor));
    }

    /** Injectable constructor for testing. */
    @VisibleForTesting
    public UwbAdapter(
            @NonNull Context context,
            RangingInjector injector,
            AttributionSource attributionSource,
            @NonNull ListeningExecutorService executor,
            @NonNull com.android.ranging.uwb.backend.internal.RangingDevice uwbClient
    ) {
        if (!RangingTechnology.UWB.isSupported(context)) {
            throw new IllegalArgumentException("UWB system feature not found.");
        }
        mContext = context;
        mRangingInjector = injector;
        mStateMachine = new StateMachine<>(State.STOPPED);
        mUwbClient = uwbClient;
        mExecutorService = executor;
        mCallbacks = null;
        mPeers = Maps.synchronizedBiMap(HashBiMap.create());
        mDataNotificationManager = new DataNotificationManager(
                new DataNotificationConfig.Builder().build(),
                new DataNotificationConfig.Builder().build()
        );
        mIsBackgroundRangingSupported = Optional.ofNullable(mRangingInjector)
                .map(RangingInjector::getCapabilitiesProvider)
                .map(CapabilitiesProvider::getCapabilities)
                .map(RangingCapabilities::getUwbCapabilities)
                .map(UwbRangingCapabilities::isBackgroundRangingSupported)
                .orElse(true); // Defaults to true;
        mAttributionSource = attributionSource;
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.UWB;
    }

    @Override
    public void start(
            @NonNull RangingSessionConfig.TechnologyConfig config,
            @android.annotation.Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callbacks
    ) {
        Log.i(TAG, "Start called.");
        mCallbacks = callbacks;
        mNonPrivilegedAttributionSource = nonPrivilegedAttributionSource;
        if (!(config instanceof UwbConfig uwbConfig)) {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters");
            closeForReason(InternalReason.INTERNAL_ERROR);
            return;
        }
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            closeForReason(InternalReason.INTERNAL_ERROR);
            return;
        }
        mDataNotificationManager = new DataNotificationManager(
                uwbConfig.getSessionConfig().getDataNotificationConfig(),
                uwbConfig.getSessionConfig().getDataNotificationConfig());
        if (mNonPrivilegedAttributionSource != null && !mRangingInjector.isForegroundAppOrService(
                mNonPrivilegedAttributionSource.getUid(),
                mNonPrivilegedAttributionSource.getPackageName())) {
            if (!mIsBackgroundRangingSupported) {
                Log.w(TAG, "Background ranging is not supported");
                closeForReason(InternalReason.BACKGROUND_RANGING_POLICY);
                return;
            }
            mDataNotificationManager.updateConfigAppMovedToBackground();
        }

        mPeers.putAll(uwbConfig.getPeerAddresses());
        mUwbClient.setRangingParameters(
                uwbConfig.asBackendParameters(mDataNotificationManager.getCurrentConfig()));
        mUwbClient.setLocalAddress(toBackend(uwbConfig.getParameters().getDeviceAddress()));
        if (mUwbClient instanceof RangingController controller) {
            controller.setComplexChannel(
                    toBackend(uwbConfig.getParameters().getComplexChannel()));
        }
        if (mUwbClient.isHwTurnOffEnabled()) {
            if (!UwbHwSwitchHelper.enable(mContext, mAttributionSource)) {
                Log.e(TAG, "Failed enabling UWB Hardware");
                closeForReason(InternalReason.UNSUPPORTED);
                return;
            }
        }
        var future = Futures.submit(() -> mUwbClient.startRanging(mUwbListener), mExecutorService);
        Futures.addCallback(future, mUwbClientResultHandlers.startRanging, mExecutorService);
    }

    @Override
    public boolean isDynamicUpdatePeersSupported() {
        return true;
    }

    @Override
    public void addPeer(RawResponderRangingConfig params) {
        Log.i(TAG, "Add peer called");
        if (mUwbClient instanceof RangingController) {
            UwbAddress uwbAddress =
                    params.getRawRangingDevice().getUwbRangingParams().getPeerAddress();
            com.android.ranging.uwb.backend.internal.UwbAddress uwbBackendAddress =
                    com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                            uwbAddress.getAddressBytes());
            mPeers.put(params.getRawRangingDevice().getRangingDevice(), uwbAddress);
            var unused = Futures.submit(
                    () -> ((RangingController) mUwbClient).addControlee(uwbBackendAddress),
                    mExecutorService);
        }
    }

    @Override
    public void removePeer(RangingDevice device) {
        Log.i(TAG, "Remove peer called");
        if (mUwbClient instanceof RangingController) {
            synchronized (mPeers) {
                if (mPeers.containsKey(device)) {
                    com.android.ranging.uwb.backend.internal.UwbAddress uwbBackendAddress =
                            com.android.ranging.uwb.backend.internal.UwbAddress.fromBytes(
                                    mPeers.get(device).getAddressBytes());
                    var unused = Futures.submit(
                            () -> ((RangingController) mUwbClient)
                                    .removeControlee(uwbBackendAddress),
                            mExecutorService);
                }
            }
        }
    }

    @Override
    public void reconfigureRangingInterval(int intervalSkipCount) {
        Log.i(TAG, "Reconfigure ranging interval called");
        if (mUwbClient instanceof RangingController) {
            ((RangingController) mUwbClient).setBlockStriding(intervalSkipCount);
        }
    }

    @Override
    public void appMovedToBackground() {
        if (mNonPrivilegedAttributionSource != null && mDataNotificationManager != null) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
            var unused = Futures.submit(
                    () -> mUwbClient.reconfigureRangeDataNtfConfig(
                            UwbConfig.toBackend(mDataNotificationManager.getCurrentConfig())),
                    mExecutorService);
        }
    }

    @Override
    public void appMovedToForeground() {
        if (mNonPrivilegedAttributionSource != null && mDataNotificationManager != null) {
            mDataNotificationManager.updateConfigAppMovedToForeground();
            var unused = Futures.submit(
                    () -> mUwbClient.reconfigureRangeDataNtfConfig(
                            UwbConfig.toBackend(mDataNotificationManager.getCurrentConfig())),
                    mExecutorService);
        }
    }

    @Override
    public void appInBackgroundTimeout() {
        if (mNonPrivilegedAttributionSource != null && !mIsBackgroundRangingSupported) {
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
        var future = Futures.submit(mUwbClient::stopRanging, mExecutorService);
        Futures.addCallback(future, mUwbClientResultHandlers.stopRanging, mExecutorService);
    }

    public @Nullable UwbComplexChannel getComplexChannel() {
        if (!(mUwbClient instanceof RangingController controller)) {
            return null;
        }
        com.android.ranging.uwb.backend.internal.UwbComplexChannel complexChannel =
                controller.getComplexChannel();
        return new UwbComplexChannel.Builder()
                .setChannel((int) complexChannel.getChannel())
                .setPreambleIndex((int) complexChannel.getPreambleIndex())
                .build();
    }

    private class UwbListener implements RangingSessionCallback {

        @Override
        public void onRangingInitialized(UwbDevice localDevice) {
            Log.i(TAG, "onRangingInitialized");
            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STARTED && !mPeers.isEmpty()) {
                    mCallbacks.onStarted(ImmutableSet.copyOf(mPeers.keySet()));
                }
            }
        }

        @Override
        public void onRangingResult(UwbDevice peer, RangingPosition position) {
            RangingData.Builder dataBuilder = new RangingData.Builder()
                    .setRangingTechnology((int) RangingTechnology.UWB.getValue())
                    .setDistance(convertMeasurement(position.getDistance()))
                    .setTimestampMillis(RangingUtils.convertNanosToMillis(
                            position.getElapsedRealtimeNanos()));

            if (position.getAzimuth() != null) {
                dataBuilder.setAzimuth(convertMeasurement(position.getAzimuth()));
            }
            if (position.getElevation() != null) {
                dataBuilder.setElevation(convertMeasurement(position.getElevation()));
            }
            if (position.getRssiDbm() != RangingPosition.RSSI_UNKNOWN) {
                dataBuilder.setRssi(position.getRssiDbm());
            }

            synchronized (mStateMachine) {
                if (mStateMachine.getState() == State.STARTED) {
                    RangingDevice device = convertPeerDevice(peer);
                    if (device != null) {
                        mCallbacks.onRangingData(device, dataBuilder.build());
                    }
                }
            }
        }

        @Override
        public void onRangingSuspended(UwbDevice localDevice, @RangingSuspendedReason int reason) {
            Log.i(TAG, "onRangingSuspended: " + reason);
            if (mUwbClient.isHwTurnOffEnabled()) {
                UwbHwSwitchHelper.disable(mContext, mAttributionSource);
            }
            closeForReason(convertClosedReason(reason));
        }

        @Override
        public void onPeerDisconnected(UwbDevice peer, @PeerDisconnectedReason int reason) {
            Log.i(TAG, "onPeerDisconnected: " + peer.getAddress() + ", " + reason);

            synchronized (mStateMachine) {
                RangingDevice device = convertPeerDevice(peer);
                if (device != null) {
                    mPeers.remove(device);
                    mCallbacks.onStopped(
                            ImmutableSet.of(device), convertDisconnectedReason(reason));
                }
            }
        }

        @Override
        public void onPeerConnected(UwbDevice peer) {
            RangingDevice device = convertPeerDevice(peer);
            if (device != null) {
                mCallbacks.onStarted(ImmutableSet.of(device));
            }
        }


        private static @InternalReason int convertDisconnectedReason(
                @PeerDisconnectedReason int reason
        ) {
            return switch (reason) {
                case PeerDisconnectedReason.UNKNOWN -> InternalReason.UNKNOWN;
                case PeerDisconnectedReason.LOCAL_DEVICE_REQUEST -> InternalReason.LOCAL_REQUEST;
                case PeerDisconnectedReason.SYSTEM_POLICY -> InternalReason.SYSTEM_POLICY;
                case PeerDisconnectedReason.FAILED_TO_ADD_CONTROLEE ->
                        InternalReason.NO_PEERS_FOUND;
                default -> InternalReason.UNKNOWN;
            };
        }

        private static @InternalReason int convertClosedReason(
                @RangingSessionCallback.RangingSuspendedReason int reason) {
            return switch (reason) {
                case REASON_UNKNOWN -> InternalReason.UNKNOWN;
                case REASON_WRONG_PARAMETERS, REASON_FAILED_TO_START -> InternalReason.UNSUPPORTED;
                case REASON_STOPPED_BY_PEER -> InternalReason.REMOTE_REQUEST;
                case REASON_STOP_RANGING_CALLED -> InternalReason.LOCAL_REQUEST;
                case REASON_MAX_RANGING_ROUND_RETRY_REACHED -> InternalReason.NO_PEERS_FOUND;
                case REASON_SYSTEM_POLICY -> InternalReason.SYSTEM_POLICY;
                default -> InternalReason.UNKNOWN;
            };
        }

        private @Nullable RangingDevice convertPeerDevice(
                @NonNull com.android.ranging.uwb.backend.internal.UwbDevice peer
        ) {
            RangingDevice device = mPeers
                    .inverse()
                    .get(UwbAddress.fromBytes(peer.getAddress().toBytes()));
            if (device == null) {
                Log.e(TAG, "Attempted lookup of unknown peer with UWB address "
                        + peer.getAddress().toHexString());
                return null;
            }
            return device;
        }

        private static RangingMeasurement convertMeasurement(
                @NonNull com.android.ranging.uwb.backend.internal.RangingMeasurement measurement
        ) {
            return new RangingMeasurement.Builder()
                    .setMeasurement(measurement.getValue())
                    .setConfidence(convertConfidence(measurement.getConfidence()))
                    .build();
        }
    }

    /**
     * Informs callbacks that all peers disconnected and the session closed. Resets internal
     * state.
     */
    private void closeForReason(@InternalReason int reason) {
        synchronized (mStateMachine) {
            mStateMachine.setState(State.STOPPED);
            if (mCallbacks == null) {
                Log.i(TAG, "Callback is empty.");
                return;
            }
            if (!mPeers.isEmpty()) {
                mCallbacks.onStopped(ImmutableSet.copyOf(mPeers.keySet()), reason);
            }
            mCallbacks.onClosed(reason);
            clear();
        }
    }

    private void clear() {
        mCallbacks = null;
        mPeers.clear();
    }

    public enum State {
        STARTED,
        STOPPED,
    }

    private class ExecutorResultHandlers {
        public final FutureCallback<Integer> startRanging = new FutureCallback<>() {
            @Override
            public void onSuccess(Integer status) {
                if (status != Utils.STATUS_OK) {
                    Log.e(TAG, "startRainging failed with status " + status);
                    closeForReason(convertStatus(status));
                }
                Log.i(TAG, "startRanging succeeded.");
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "startRanging failed ", t);
                closeForReason(InternalReason.INTERNAL_ERROR);
            }
        };

        public final FutureCallback<Integer> stopRanging = new FutureCallback<>() {
            @Override
            public void onSuccess(@Utils.UwbStatusCodes Integer status) {
                if (status != Utils.STATUS_OK) {
                    Log.e(TAG, "stopRanging failed with status " + status);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                Log.e(TAG, "stopRanging failed ", t);
                // We failed to stop but there's nothing else we can do.
                closeForReason(InternalReason.INTERNAL_ERROR);
            }
        };
    }

    public static @InternalReason int convertStatus(@Utils.UwbStatusCodes int status) {
        return switch (status) {
            case Utils.STATUS_OK -> InternalReason.UNKNOWN;
            case Utils.STATUS_ERROR,
                 Utils.INVALID_API_CALL,
                 Utils.MISSING_PERMISSION_UWB_RANGING,
                 Utils.UWB_SYSTEM_CALLBACK_FAILURE,
                 Utils.RANGING_ALREADY_STARTED -> InternalReason.INTERNAL_ERROR;
            default -> InternalReason.UNKNOWN;
        };
    }

    public static int convertConfidence(int confidence) {
        return switch (confidence) {
            case CONFIDENCE_HIGH -> android.ranging.RangingMeasurement.CONFIDENCE_HIGH;
            case CONFIDENCE_MEDIUM -> android.ranging.RangingMeasurement.CONFIDENCE_MEDIUM;
            default -> android.ranging.RangingMeasurement.CONFIDENCE_LOW;
        };
    }
}
