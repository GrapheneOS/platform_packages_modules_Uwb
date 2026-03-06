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
import static com.android.server.ranging.common.RangingUtils.InternalReason.INTERNAL_ERROR;
import static com.android.server.ranging.uwb.UwbConfig.toBackend;

import android.app.AlarmManager;
import android.content.AttributionSource;
import android.content.Context;
import android.os.SystemClock;
import android.ranging.DataNotificationConfig;
import android.ranging.DlTdoaMeasurement;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDataExtras;
import android.ranging.RangingDevice;
import android.ranging.RangingMeasurement;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.DlTdoaRangingParams;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbSpecificData;
import android.util.Log;
import android.util.Range;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.uwb.backend.internal.DtTagParameters;
import com.android.ranging.uwb.backend.internal.RangingController;
import com.android.ranging.uwb.backend.internal.RangingParameters;
import com.android.ranging.uwb.backend.internal.RangingPosition;
import com.android.ranging.uwb.backend.internal.RangingSessionCallback;
import com.android.ranging.uwb.backend.internal.Utils;
import com.android.ranging.uwb.backend.internal.UwbDevice;
import com.android.ranging.uwb.backend.internal.UwbHwSwitchHelper;
import com.android.ranging.uwb.backend.internal.UwbRangeLimitsConfig;
import com.android.ranging.uwb.backend.internal.UwbServiceImpl;
import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.RangingAdapter;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.DataNotificationManager;
import com.android.server.ranging.common.RangingUtils;
import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.common.StateMachine;
import com.android.server.ranging.session.ConfigurationManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ranging adapter for Ultra-wideband (UWB). */
public class UwbAdapter implements RangingAdapter {
    private static final String TAG = UwbAdapter.class.getSimpleName();
    private static final int DL_TDOA_BG_TIMEOUT_MILLIS = 120_000;
    private final Context mContext;
    private final RangingInjector mRangingInjector;
    private final com.android.ranging.uwb.backend.internal.RangingDevice mUwbClient;
    private final ListeningExecutorService mExecutorService;
    private final ExecutorResultHandlers mUwbClientResultHandlers = new ExecutorResultHandlers();
    private final RangingSessionCallback mUwbListener = new UwbListener();
    private final StateMachine<State> mStateMachine;
    private final Object mLock;
    private final BiMap<RangingDevice, UwbAddress> mPeers;
    private final HashMap<UwbDevice, RangingDevice> mAnchors;
    private boolean mIsDlTdoaSession = false;

    private DataNotificationManager mDataNotificationManager;

    /** Invariant: non-null while a ranging session is active */
    private Callback mCallbacks;

    private AttributionSource mNonPrivilegedAttributionSource;
    boolean mIsBackgroundRangingSupported;
    private List<Integer> mSupportedAntennaModes;

    private final AttributionSource mAttributionSource;

    private final AlarmManager mAlarmManager;

    private AlarmManager.OnAlarmListener mDlTdoaTimeoutListener;

    public UwbAdapter(
            @NonNull Context context,
            RangingInjector injector,
            AttributionSource attributionSource,
            @NonNull ListeningExecutorService executor,
            @NonNull Object lock,
            @RangingPreference.DeviceRole int role
    ) {
        this(context, injector, attributionSource, executor, lock,
                getBackendDevice(context, executor, role));
    }

    private static com.android.ranging.uwb.backend.internal.RangingDevice getBackendDevice(
            @NonNull Context context,
            @NonNull ListeningExecutorService executor,
            @RangingPreference.DeviceRole int role) {
        switch (role) {
            case RangingPreference.DEVICE_ROLE_INITIATOR:
                return UwbServiceImpl.getController(context, executor);
            case RangingPreference.DEVICE_ROLE_RESPONDER:
                return UwbServiceImpl.getControlee(context, executor);
            case RangingPreference.DEVICE_ROLE_DT_TAG:
                return UwbServiceImpl.getRangingTag(context, executor);
            default:
                throw new IllegalArgumentException("Invalid device role: " + role);
        }
    }

    /** Injectable constructor for testing. */
    @VisibleForTesting
    public UwbAdapter(
            @NonNull Context context,
            RangingInjector injector,
            AttributionSource attributionSource,
            @NonNull ListeningExecutorService executor,
            @NonNull Object lock,
            @NonNull com.android.ranging.uwb.backend.internal.RangingDevice uwbClient
    ) {
        if (!RangingTechnology.UWB.isSupported(context)) {
            throw new IllegalArgumentException("UWB system feature not found.");
        }
        mContext = context;
        mRangingInjector = injector;
        mStateMachine = new StateMachine<>(State.STOPPED, lock);
        mLock = lock;
        mUwbClient = uwbClient;
        mExecutorService = executor;
        mCallbacks = null;
        mPeers = Maps.synchronizedBiMap(HashBiMap.create());
        mAnchors = new HashMap<>();
        mDataNotificationManager = new DataNotificationManager(
                new DataNotificationConfig.Builder().build(),
                new DataNotificationConfig.Builder().build()
        );
        UwbRangingCapabilities uwbCapabilities = Optional.ofNullable(mRangingInjector)
                .map(RangingInjector::getCapabilitiesProvider)
                .map(CapabilitiesProvider::getCapabilities)
                .map(RangingCapabilities::getUwbCapabilities)
                .orElse(null);
        mIsBackgroundRangingSupported = Optional.ofNullable(uwbCapabilities)
                .map(UwbRangingCapabilities::isBackgroundRangingSupported)
                .orElse(true); // Defaults to true;
        mSupportedAntennaModes = Optional.ofNullable(uwbCapabilities)
                .map(UwbRangingCapabilities::getSupportedAntennaModes)
                .orElse(List.of()); // Defaults to empty;
        mAttributionSource = attributionSource;
        mAlarmManager = context.getSystemService(AlarmManager.class);
        Objects.requireNonNull(mAlarmManager);
    }

    @Override
    public @NonNull RangingTechnology getTechnology() {
        return RangingTechnology.UWB;
    }

    /**
     * Use this method to do some service side validation of params
     * {@link android.ranging.RangingSession#start(RangingPreference)}.
     */
    public boolean isConfigValid(ConfigurationManager.TechnologyConfig config) {
        if (config instanceof UwbConfig uwbConfig) {
            int antennaMode = uwbConfig.getSessionConfig().getAntennaMode();
            if (antennaMode != SessionConfig.ANTENNA_MODE_UNSET
                && !mSupportedAntennaModes.contains(antennaMode)) {
                Log.e(TAG,  "Invalid antenna mode: " + antennaMode);
                return false;
            }
        }
        return true;
    }

    @Override
    public void start(
            @NonNull ConfigurationManager.TechnologyConfig config,
            @android.annotation.Nullable AttributionSource nonPrivilegedAttributionSource,
            @NonNull Callback callbacks
    ) {
        Log.i(TAG, "Start called.");
        mCallbacks = callbacks;
        mNonPrivilegedAttributionSource = nonPrivilegedAttributionSource;
        if (!mStateMachine.transition(State.STOPPED, State.STARTED)) {
            Log.v(TAG, "Attempted to start adapter when it was already started");
            closeForReason(InternalReason.INTERNAL_ERROR);
            return;
        }
        if (!isConfigValid(config)) {
            Log.v(TAG, "Invalid session config passed to start");
            closeForReason(InternalReason.UNSUPPORTED);
            return;
        }

        if (config instanceof UwbConfig uwbConfig) {
            mDataNotificationManager = new DataNotificationManager(
                    uwbConfig.getSessionConfig().getDataNotificationConfig(),
                    uwbConfig.getSessionConfig().getDataNotificationConfig());
            if (mNonPrivilegedAttributionSource != null &&
                    !mRangingInjector.isForegroundAppOrService(
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
        } else if (config instanceof DlTdoaConfig dlTdoaConfig) {
            mIsDlTdoaSession = true;
            if (mNonPrivilegedAttributionSource != null
                    && !mRangingInjector.isForegroundAppOrService(
                            mNonPrivilegedAttributionSource.getUid(),
                            mNonPrivilegedAttributionSource.getPackageName())) {
                if (!mIsBackgroundRangingSupported) {
                    Log.w(TAG, "Background ranging is not supported");
                    closeForReason(InternalReason.BACKGROUND_RANGING_POLICY);
                    return;
                }
                Log.e(TAG, "Starting Dl-tdoa ranging session in background, timing out in "
                        + (DL_TDOA_BG_TIMEOUT_MILLIS / 1000) + " seconds");
                setDlTdoaBackgroundSessionTimeout();
            }
            mUwbClient.setLocalAddress(
                    toBackend(dlTdoaConfig.getDeviceAddress()));
            if (mUwbClient instanceof com.android.ranging.uwb.backend.internal.RangingTag) {
                ((com.android.ranging.uwb.backend.internal.RangingTag) mUwbClient)
                        .setComplexChannel(toBackend(dlTdoaConfig.getParams().getComplexChannel()));
            }
            mUwbClient.setRangingParameters(
                    dlTdoaAsBackendParameters(dlTdoaConfig));
        } else {
            Log.w(TAG, "Tried to start adapter with invalid ranging parameters: " + config);
            mCallbacks.onClosed(INTERNAL_ERROR);
            return;
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

    private void setDlTdoaBackgroundSessionTimeout() {
        if (mDlTdoaTimeoutListener != null) {
            mAlarmManager.cancel(mDlTdoaTimeoutListener);
        }

        mDlTdoaTimeoutListener = () -> {
            Log.i(TAG, "Dl-TDoA background session timed out");
            mExecutorService.execute(this::stop);
        };

        mAlarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + DL_TDOA_BG_TIMEOUT_MILLIS,
                "DlTdoaBgTimeout",
                mDlTdoaTimeoutListener,
                null
        );
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
        if (mNonPrivilegedAttributionSource != null && mDataNotificationManager != null
                && !mIsDlTdoaSession) {
            mDataNotificationManager.updateConfigAppMovedToBackground();
            var unused = Futures.submit(
                    () -> mUwbClient.reconfigureRangeDataNtfConfig(
                            UwbConfig.toBackend(mDataNotificationManager.getCurrentConfig())),
                    mExecutorService);
        }
    }

    @Override
    public void appMovedToForeground() {
        if (mIsDlTdoaSession && mDlTdoaTimeoutListener != null) {
            mAlarmManager.cancel(mDlTdoaTimeoutListener);
            mDlTdoaTimeoutListener = null;
        } else if (mNonPrivilegedAttributionSource != null && mDataNotificationManager != null) {

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
            synchronized (mLock) {
                Log.i(TAG, "onRangingInitialized");
                if (mStateMachine.getState() == State.STARTED) {
                    if (mIsDlTdoaSession) {
                        mCallbacks.onStarted(ImmutableSet.of());
                    } else if (!mPeers.isEmpty()) {
                        mCallbacks.onStarted(ImmutableSet.copyOf(mPeers.keySet()));
                    }
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
            dataBuilder.setRangingDataExtras(
                    new RangingDataExtras.Builder().setUwbSpecificData(
                            new UwbSpecificData.Builder()
                                    .setNonLineOfSight(position.getNlos())
                                    .build()
                    ).build()
            );

            synchronized (mLock) {
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
            synchronized (mLock) {
                Log.i(TAG, "onPeerDisconnected: " + peer.getAddress() + ", " + reason);
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

        @Override
        public void onDlTdoaRangingResult(UwbDevice anchor,
                com.android.ranging.uwb.backend.internal.DlTdoaMeasurement measurement) {
            DlTdoaMeasurement dlTdoaMeasurement = convertDlTdoaMeasurement(measurement);

            synchronized (mLock) {
                if (mStateMachine.getState() == State.STARTED) {
                    RangingDevice anchorDevice = convertPeerDevice(anchor);
                    if (anchorDevice != null) {
                        mCallbacks.onDlTdoaRangingResult(anchorDevice, dlTdoaMeasurement);
                    }
                }
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
            if (mIsDlTdoaSession) {
                // For DL-TDoA, the "peer" is the anchor. We create a RangingDevice on the fly.
                return mAnchors.computeIfAbsent(peer, key ->
                        new RangingDevice.Builder()
                                .setDlTdoaUwbAddress(
                                        UwbAddress.fromBytes(key.getAddress().toBytes()))
                                .build());
            }
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

        private static @DlTdoaRangingParams.MeasurementVersion int convertMeasurementVersion(
                int measurementVersion) {
            return switch (measurementVersion) {
                case com.android.ranging.uwb.backend.internal.DlTdoaMeasurement
                        .MEASUREMENT_VERSION_1 ->
                                DlTdoaRangingParams.MEASUREMENT_VERSION_1;
                case com.android.ranging.uwb.backend.internal.DlTdoaMeasurement
                        .MEASUREMENT_VERSION_2 ->
                                DlTdoaRangingParams.MEASUREMENT_VERSION_2;
                default -> DlTdoaRangingParams.MEASUREMENT_VERSION_UNKNOWN;
            };
        }

        private static @Nullable DlTdoaMeasurement.Wgs84Location convertWgs84Location(
                @Nullable UwbAnchorLocation.UwbWgs84Location wgs84Location) {
            if (wgs84Location == null) {
                return null;
            }
            return new DlTdoaMeasurement.Wgs84Location(
                    wgs84Location.getLatitude(),
                    wgs84Location.getLongitude(),
                    wgs84Location.getAltitude());
        }

        private static @Nullable DlTdoaMeasurement.RelativeLocation convertRelativeLocation(
                @Nullable UwbAnchorLocation.UwbRelativeLocation relativeLocation) {
            if (relativeLocation == null) {
                return null;
            }
            return new DlTdoaMeasurement.RelativeLocation(
                    relativeLocation.getX(),
                    relativeLocation.getY(),
                    relativeLocation.getZ());
        }

        private static @Nullable DlTdoaMeasurement.ZElementExtension convertZElementExtension(
                @Nullable UwbAnchorLocation.UwbZElementExtension zElementExtension) {
            if (zElementExtension == null) {
                return null;
            }
            Range<Double> anchorHeightAboveFloorRange =
                    zElementExtension.getAnchorHeightAboveFloorRange();
            return new DlTdoaMeasurement.ZElementExtension(
                    zElementExtension.getAnchorFloorNumber(),
                    zElementExtension.getExpectedToMove(),
                    zElementExtension.getAnchorHeightAboveFloor(),
                    zElementExtension.getAnchorHeightAboveFloorUncertainty(),
                    zElementExtension.isAnchorFloorNumberOutOfRange(),
                    zElementExtension.isAnchorHeightAboveFloorOutOfRange(),
                    anchorHeightAboveFloorRange == null
                            ? Double.NaN : anchorHeightAboveFloorRange.getLower(),
                    anchorHeightAboveFloorRange == null
                            ? Double.NaN : anchorHeightAboveFloorRange.getUpper()
                    );
        }

        private static @DlTdoaMeasurement.AnchorLocation.CoordinateType int
                convertCoordinateType(int coordinateType) {
            return switch (coordinateType) {
                case UwbAnchorLocation.COORDINATE_WGS84 ->
                        DlTdoaMeasurement.AnchorLocation.COORDINATE_WGS84;
                case UwbAnchorLocation.COORDINATE_RELATIVE ->
                        DlTdoaMeasurement.AnchorLocation.COORDINATE_RELATIVE;
                case UwbAnchorLocation.COORDINATE_WGS84_PLUS_Z_ELEMENT ->
                        DlTdoaMeasurement.AnchorLocation.COORDINATE_WGS84_PLUS_Z_ELEMENT;
                case UwbAnchorLocation.COORDINATE_RELATIVE_PLUS_Z_ELEMENT ->
                        DlTdoaMeasurement.AnchorLocation.COORDINATE_RELATIVE_PLUS_Z_ELEMENT;
                case UwbAnchorLocation.COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED ->
                        DlTdoaMeasurement.AnchorLocation.COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED;
                case UwbAnchorLocation.COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED_PLUS_Z_ELEMENT ->
                        DlTdoaMeasurement
                                .AnchorLocation
                                        .COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED_PLUS_Z_ELEMENT;
                default -> DlTdoaMeasurement.AnchorLocation.COORDINATE_UNKNOWN;
            };
        }

        private static DlTdoaMeasurement.AnchorLocation convertAnchorLocation(
                int measurementVersion, int messageControl, @NonNull byte[] rawAnchorLocation) {
            Objects.requireNonNull(rawAnchorLocation);

            UwbAnchorLocation location = switch (measurementVersion) {
                case com.android.ranging.uwb.backend.internal.DlTdoaMeasurement
                        .MEASUREMENT_VERSION_1 ->
                                UwbAnchorLocation.fromBytesV1(messageControl, rawAnchorLocation);
                case com.android.ranging.uwb.backend.internal.DlTdoaMeasurement
                        .MEASUREMENT_VERSION_2 ->
                                UwbAnchorLocation.fromBytesV2(rawAnchorLocation);
                default -> UwbAnchorLocation.fromBytesWithUnknownType(rawAnchorLocation);
            };
            return new DlTdoaMeasurement.AnchorLocation(
                    convertCoordinateType(location.getCoordinateType()),
                    location.getRawBytes(),
                    convertWgs84Location(location.getWgs84Location()),
                    convertRelativeLocation(location.getRelativeLocation()),
                    convertZElementExtension(location.getZElementExtension()));
        }

        private static DlTdoaMeasurement convertDlTdoaMeasurement(
                @NonNull com.android.ranging.uwb.backend.internal.DlTdoaMeasurement measurement) {
            android.ranging.DlTdoaMeasurement.Builder builder =
                    new android.ranging.DlTdoaMeasurement.Builder(
                            convertMeasurementVersion(measurement.getMeasurementVersion()),
                            measurement.getMessageType(),
                            measurement.getMessageControl(),
                            measurement.getBlockIndex(),
                            measurement.getRoundIndex(),
                            measurement.getNLoS(),
                            measurement.getTxTimestampV2() != null
                                    ? measurement.getTxTimestampV2()
                                    : ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                                    .putLong(measurement.getTxTimestamp()).array(),
                            measurement.getRxTimestampV2() != null
                                    ? measurement.getRxTimestampV2()
                                    : ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                                    .putLong(measurement.getRxTimestamp()).array(),
                            measurement.getAnchorCfo(),
                            measurement.getCfo(),
                            measurement.getInitiatorReplyTime(),
                            measurement.getResponderReplyTime(),
                            measurement.getInitiatorResponderTof()
                    );
            builder.setAoaAzimuth(measurement.getAoaAzimuth());
            builder.setAoaAzimuthFom(measurement.getAoaAzimuthFom());
            builder.setAoaElevation(measurement.getAoaElevation());
            builder.setAoaElevationFom(measurement.getAoaElevationFom());
            builder.setRssi(measurement.getRssi());
            builder.setAnchorLocation(convertAnchorLocation(
                    measurement.getMeasurementVersion(),
                    measurement.getMessageControl(),
                    measurement.getAnchorLocation()));
            builder.setActiveRangingRoundIndexes(measurement.getActiveRangingRounds());
            if (measurement.getSuperclusterId() !=
                    com.android.ranging.uwb.backend.internal.DlTdoaMeasurement
                            .SUPERCLUSTER_ID_ABSENT) {
                builder.setSuperclusterId(measurement.getSuperclusterId());
            }
            return builder.build();
        }
    }

    /**
     * Informs callbacks that all peers disconnected and the session closed. Resets internal
     * state.
     */
    private void closeForReason(@InternalReason int reason) {
        synchronized (mLock) {
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
        mAnchors.clear();
        mIsDlTdoaSession = false;
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

    private static RangingParameters dlTdoaAsBackendParameters(
            DlTdoaConfig config) {

        DlTdoaRangingParams params = config.getParams();

        final UwbRangeLimitsConfig rangeLimitsConfig =
                new UwbRangeLimitsConfig.Builder()
                        .setRangeMaxNumberOfMeasurements(
                                config.getSessionConfig().getRangingMeasurementsLimit())
                        .build();

        return new DtTagParameters(
                params.getSessionId(),
                params.getSessionKeyInfo(),
                toBackend(params.getComplexChannel()),
                params.getSlotDuration(),
                rangeLimitsConfig,
                params.getRangingIntervalMillis(),
                params.getSlotsPerRangingRound(),
                params.getMeasurementVersion(),
                params.getRangingRoundIndexes());
    }
}
