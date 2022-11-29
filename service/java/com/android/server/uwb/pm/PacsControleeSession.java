/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb.pm;

import static com.android.server.uwb.data.UwbConfig.CONTROLEE_AND_RESPONDER;
import static com.android.server.uwb.data.UwbConfig.OOB_TYPE_BLE;
import static com.android.server.uwb.data.UwbConfig.PERIPHERAL;

import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_DYNAMIC;

import android.annotation.NonNull;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.AttributionSource;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;

import com.android.internal.util.State;
import com.android.modules.utils.HandlerExecutor;
import com.android.server.uwb.UwbInjector;
import com.android.server.uwb.data.ServiceProfileData.ServiceProfileInfo;
import com.android.server.uwb.data.UwbConfig;
import com.android.server.uwb.discovery.DiscoveryAdvertiseProvider;
import com.android.server.uwb.discovery.DiscoveryProvider;
import com.android.server.uwb.discovery.DiscoveryProviderFactory;
import com.android.server.uwb.discovery.TransportProviderFactory;
import com.android.server.uwb.discovery.TransportServerProvider;
import com.android.server.uwb.discovery.ble.DiscoveryAdvertisement;
import com.android.server.uwb.discovery.info.AdvertiseInfo;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.secure.SecureFactory;
import com.android.server.uwb.secure.SecureSession;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

/** Session for PACS profile controlee */
public class PacsControleeSession extends RangingSessionController {
    private static final String TAG = "PacsControleeSession";
    private final PacsAdvertiseCallback mAdvertiseCallback;
    // TODO populate before calling secureSessionInit()
    private PacsControleeSessionInfo mControleeSessionInfo;
    private final PacsControleeSessionCallback mControleeSessionCallback;
    private final TransportServerProvider.TransportServerCallback mServerCallback;

    public PacsControleeSession(
            SessionHandle sessionHandle,
            AttributionSource attributionSource,
            Context context,
            UwbInjector uwbInjector,
            ServiceProfileInfo serviceProfileInfo,
            IUwbRangingCallbacks rangingCallbacks,
            Handler handler,
            String chipId) {
        super(
                sessionHandle,
                attributionSource,
                context,
                uwbInjector,
                serviceProfileInfo,
                rangingCallbacks,
                handler,
                chipId);
        mAdvertiseCallback = new PacsAdvertiseCallback(this);
        mControleeSessionInfo = new PacsControleeSessionInfo(this);
        mControleeSessionCallback = new PacsControleeSessionCallback(this);
        mServerCallback = null;
    }

    @Override
    public State getIdleState() {
        return new IdleState();
    }

    @Override
    public State getDiscoveryState() {
        return new DiscoveryState();
    }

    @Override
    public State getTransportState() {
        return new TransportState();
    }

    @Override
    public State getSecureState() {
        return new SecureSessionState();
    }

    @Override
    public State getRangingState() {
        return new RangingState();
    }

    @Override
    public State getEndingState() {
        return new EndSessionState();
    }

    private DiscoveryProvider mDiscoveryProvider;
    private DiscoveryInfo mDiscoveryInfo;
    private TransportServerProvider mTransportServerProvider;
    private SecureSession mSecureSession;
    private DiscoveryAdvertisement mDiscoveryAdvertisement;
    private AdvertisingSetParameters mAdvertisingSetParameters;

    public void setDiscoveryAdvertisement(DiscoveryAdvertisement discoveryAdvertisement) {
        mDiscoveryAdvertisement = discoveryAdvertisement;
    }

    public void setAdvertisingSetParameters(AdvertisingSetParameters advertisingSetParameters) {
        mAdvertisingSetParameters = advertisingSetParameters;
    }

    /** Advertise capabilities */
    public void startAdvertising() {
        AdvertiseInfo advertiseInfo =
                new AdvertiseInfo(mAdvertisingSetParameters, mDiscoveryAdvertisement);

        mDiscoveryInfo =
                new DiscoveryInfo(
                        DiscoveryInfo.TransportType.BLE,
                        Optional.empty(),
                        Optional.of(advertiseInfo),
                        Optional.empty());

        mDiscoveryProvider =
                DiscoveryProviderFactory.createAdvertiser(
                        mSessionInfo.mAttributionSource,
                        mSessionInfo.mContext,
                        new HandlerExecutor(mHandler),
                        mDiscoveryInfo,
                        mAdvertiseCallback);
        mDiscoveryProvider.start();
        // sendMessage(TRANSPORT_INIT);
    }

    /** Stop advertising on ranging stopped or closed */
    public void stopAdvertising() {
        if (mDiscoveryProvider != null) {
            mDiscoveryProvider.stop();
        }
    }

    /** Initialize Transport server */
    public void transportServerInit() {
        mTransportServerProvider =
                TransportProviderFactory.createServer(
                        mSessionInfo.mAttributionSource,
                        mSessionInfo.mContext,
                        // TODO: Transport server supports auto assigning secid.
                        /*secid placeholder*/ 2,
                        mDiscoveryInfo,
                        mServerCallback);
        sendMessage(TRANSPORT_STARTED);
    }

    /** Start Transport server */
    public void transportServerStart() {
        mTransportServerProvider.start();
    }

    /** Stop Transport server */
    public void transportServerStop() {
        mTransportServerProvider.stop();
    }

    /** Initialize controlee responder session */
    public void secureSessionInit() {
        mSecureSession =
                SecureFactory.makeResponderSecureSession(
                        mSessionInfo.mContext,
                        mHandler.getLooper(),
                        mControleeSessionCallback,
                        mControleeSessionInfo,
                        mTransportServerProvider);
    }

    @Override
    public UwbConfig getUwbConfig() {
        // PACS controlee config
        UwbConfig.Builder builder = new UwbConfig.Builder()
                .setUwbRole(CONTROLEE_AND_RESPONDER)
                .setStsConfig(STS_CONFIG_DYNAMIC)
                .setMultiNodeMode(MULTI_NODE_MODE_ONE_TO_MANY)
                .setRframeConfig(RFRAME_CONFIG_SP3)
                .setTofReport(true)
                .setOobType(OOB_TYPE_BLE)
                .setOobBleRole(PERIPHERAL);
        // Config received in sessionData
        if (mSessionInfo.mSessionData != null) {
            mSessionInfo.setSessionId(mSessionInfo.mSessionData.mSessionId);
            mSessionInfo.mSessionData.mSubSessionId.ifPresent(
                    integer -> mSessionInfo.setSubSessionId(integer));

            if (mSessionInfo.mSessionData.mConfigurationParams.isPresent()) {
                ConfigurationParams configurationParams =
                        mSessionInfo.mSessionData.mConfigurationParams.get();
                configurationParams.mScheduledMode.ifPresent(builder::setScheduledMode);
                configurationParams.mBlockStriding.ifPresent(builder::setBlockStriding);
                configurationParams.mChannel.ifPresent(builder::setUwbChannel);
                configurationParams.mSp0PhyParameterSet.ifPresent(builder::setSp0PhyParameterSet);
                configurationParams.mSp1PhyParameterSet.ifPresent(builder::setSp1PhyParameterSet);
                configurationParams.mSp3PhyParameterSet.ifPresent(builder::setSp3PhyParameterSet);
                configurationParams.mPreambleCodeIndex.ifPresent(builder::setUwbPreambleCodeIndex);
                configurationParams.mSlotsPerRangingRound.ifPresent(
                        builder::setSlotsPerRangingRound);
                configurationParams.mMaxContentionPhaseLength.ifPresent(
                        builder::setMaxContentionPhaseLength);
                configurationParams.mSlotDuration.ifPresent(builder::setSlotDurationRstu);
                configurationParams.mRangingIntervalMs.ifPresent(builder::setRangingIntervalMs);
                configurationParams.mKeyRotationRate.ifPresent(builder::setKeyRotationRate);
                configurationParams.mMacFcsType.ifPresent(builder::setKMacFcsType);
            }
        }
        return builder.build();
    }

    /** Implements callback of DiscoveryAdvertiseProvider */
    public static class PacsAdvertiseCallback
            implements DiscoveryAdvertiseProvider.DiscoveryAdvertiseCallback {

        public final PacsControleeSession mPacsControleeSession;

        public PacsAdvertiseCallback(PacsControleeSession pacsControleeSession) {
            mPacsControleeSession = pacsControleeSession;
        }

        @Override
        public void onDiscoveryFailed(int errorCode) {
            Log.e(TAG, "Advertising failed with error code: " + errorCode);
            mPacsControleeSession.sendMessage(DISCOVERY_FAILED);
        }
    }

    /** Pacs profile controlee implementation of RunningProfileSessionInfo. */
    public static class PacsControleeSessionInfo implements RunningProfileSessionInfo {
        public final PacsControleeSession mPacsControleeSession;

        public PacsControleeSessionInfo(PacsControleeSession pacsControleeSession) {
            mPacsControleeSession = pacsControleeSession;
        }

        @NonNull
        @Override
        public ControlleeInfo getControlleeInfo() {
            GenericSpecificationParams genericSpecificationParams =
                    mPacsControleeSession.getSpecificationInfo();
            if (genericSpecificationParams == null
                    || genericSpecificationParams.getFiraSpecificationParams() == null) {
                Log.e(TAG, "Specification params not populated, sending default values");
                return new ControlleeInfo.Builder()
                        .setUwbCapability(new UwbCapability.Builder()
                                .build())
                        .build();
            }
            FiraSpecificationParams firaSpecificationParams =
                    genericSpecificationParams.getFiraSpecificationParams();
            UwbCapability uwbCapability = new UwbCapability.Builder()
                    .setMinPhyVersionSupported(firaSpecificationParams.getMinPhyVersionSupported())
                    .setMaxPhyVersionSupported(firaSpecificationParams.getMaxPhyVersionSupported())
                    .setMinMacVersionSupported(firaSpecificationParams.getMinMacVersionSupported())
                    .setMaxMacVersionSupported(firaSpecificationParams.getMaxMacVersionSupported())
                    .setDeviceRoles(firaSpecificationParams.getDeviceRoleCapabilities())
                    .setRangingMethod(firaSpecificationParams.getRangingRoundCapabilities())
                    .setStsConfig(firaSpecificationParams.getStsCapabilities())
                    .setMultiNodeMode(firaSpecificationParams.getMultiNodeCapabilities())
                    .setBlockStriding(firaSpecificationParams.hasBlockStridingSupport())
                    .setUwbInitiationTime(firaSpecificationParams.hasInitiationTimeSupport())
                    .setChannels(firaSpecificationParams.getSupportedChannels())
                    .setRFrameConfig(firaSpecificationParams.getRframeCapabilities())
                    .setCcConstraintLength(firaSpecificationParams.getPsduDataRateCapabilities())
                    .setBprfParameterSet(firaSpecificationParams.getBprfParameterSetCapabilities())
                    .setHprfParameterSet(firaSpecificationParams.getHprfParameterSetCapabilities())
                    .setAoaSupport(firaSpecificationParams.getAoaCapabilities())
                    .build();
            return new ControlleeInfo.Builder().setUwbCapability(uwbCapability).build();
        }

        @NonNull
        @Override
        public Optional<SessionData> getSessionDataForControllee(
                ControlleeInfo controlleeInfoOfPeerDevice) {
            return Optional.empty();
        }

        @NonNull
        @Override
        public ObjectIdentifier getOidOfProvisionedAdf() {
            byte[] bytes =
                    ByteBuffer.allocate(4)
                            .putInt(
                                    mPacsControleeSession.mSessionInfo.mServiceProfileInfo
                                            .getServiceAdfID())
                            .array();
            return ObjectIdentifier.fromBytes(bytes);
        }

        @NonNull
        @Override
        public List<ObjectIdentifier> getSelectableOidsOfPeerDevice() {
            return null;
        }

        @Override
        public boolean isUwbController() {
            return false;
        }

        @Override
        public boolean isUnicast() {
            return true;
        }

        @NonNull
        @Override
        public Optional<Integer> getSharedPrimarySessionId() {
            return Optional.of(mPacsControleeSession.mSessionInfo.getSessionId());
        }

        @NonNull
        @Override
        public Optional<byte[]> getSecureBlob() {
            return Optional.empty();
        }
    }

    /** Pacs profile controlee implementation of SecureSession.Callback. */
    public static class PacsControleeSessionCallback implements SecureSession.Callback {
        public final PacsControleeSession mPacsControleeSession;

        public PacsControleeSessionCallback(PacsControleeSession pacsControleeSession) {
            mPacsControleeSession = pacsControleeSession;
        }

        @Override
        public void onSessionDataReady(
                int updatedSessionId, Optional<byte[]> sessionData, boolean isSessionTerminated) {
            mPacsControleeSession.mSessionInfo.mSessionData =
                    SessionData.fromBytes(sessionData.get());
            mPacsControleeSession.sendMessage(RANGING_INIT);
        }

        @Override
        public void onSessionAborted() {
            Log.w(TAG, "Secure Session aborted");
        }

        @Override
        public void onSessionTerminated() {
            Log.w(TAG, "Secure Session terminated");
        }
    }

    public class IdleState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter IdleState");
            }
            getSpecificationInfo();
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit IdleState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            if (mVerboseLoggingEnabled) {
                log("No message handled in IdleState");
            }
            switch (message.what) {
                case SESSION_INITIALIZED:
                    if (mVerboseLoggingEnabled) {
                        log("Pacs controlee session initialized");
                    }
                    break;
                case SESSION_START:
                    if (mVerboseLoggingEnabled) {
                        log("Starting OOB Discovery");
                    }
                    transitionTo(mDiscoveryState);
                    break;
                default:
                    if (mVerboseLoggingEnabled) {
                        log(message.toString() + " not handled in IdleState");
                    }
            }
            return true;
        }
    }

    public class DiscoveryState extends State {

        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter DiscoveryState");
            }
            sendMessage(DISCOVERY_STARTED);
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit DiscoveryState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case DISCOVERY_FAILED:
                    log("Failed to advertise");
                    break;
                case DISCOVERY_STARTED:
                case SESSION_START:
                    startAdvertising();
                    if (mVerboseLoggingEnabled) {
                        log("Started advertising");
                    }
                    break;
                case SESSION_STOP:
                    stopAdvertising();
                    if (mVerboseLoggingEnabled) {
                        log("Stopped advertising");
                    }
                    break;
                case TRANSPORT_INIT:
                    transitionTo(mTransportState);
                    break;
            }
            return true;
        }
    }

    public class TransportState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter TransportState");
            }
            transportServerInit();
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit TransportState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case TRANSPORT_STARTED:
                    transportServerStart();
                    break;
                case SESSION_STOP:
                    stopAdvertising();
                    transportServerStop();
                    break;
                case TRANSPORT_COMPLETED:
                    stopAdvertising();
                    transportServerStop();
                    transitionTo(mSecureSessionState);
                    break;
            }
            return true;
        }
    }

    public class SecureSessionState extends State {

        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter SecureSessionState");
            }
            sendMessage(SECURE_SESSION_INIT);
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit SecureSessionState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case SECURE_SESSION_INIT:
                    secureSessionInit();
                    break;
                case SECURE_SESSION_ESTABLISHED:
                    transitionTo(mRangingState);
                    break;
            }
            return true;
        }
    }

    public class RangingState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter RangingState");
            }
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit RangingState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case RANGING_INIT:
                    try {
                        Log.i(TAG, "Starting ranging session");
                        openRangingSession();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Ranging session start failed");
                        e.printStackTrace();
                    }
                    stopAdvertising();
                    break;
                case SESSION_START:
                case RANGING_OPENED:
                    startRanging();
                    if (mVerboseLoggingEnabled) {
                        log("Started ranging");
                    }
                    break;

                case SESSION_STOP:
                    stopRanging();
                    if (mVerboseLoggingEnabled) {
                        log("Stopped ranging session");
                    }
                    break;

                case RANGING_ENDED:
                    closeRanging();
                    transitionTo(mEndSessionState);
                    break;
            }
            return true;
        }
    }

    public class EndSessionState extends State {

        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter EndSessionState");
            }
            stopAdvertising();
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit EndSessionState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            return true;
        }
    }
}
