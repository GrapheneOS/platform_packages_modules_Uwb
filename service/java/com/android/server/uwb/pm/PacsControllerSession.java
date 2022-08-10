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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
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
import com.android.server.uwb.discovery.DiscoveryScanProvider;
import com.android.server.uwb.discovery.DiscoveryScanService;
import com.android.server.uwb.discovery.TransportClientProvider;
import com.android.server.uwb.discovery.TransportClientService;
import com.android.server.uwb.discovery.info.DiscoveryInfo;
import com.android.server.uwb.discovery.info.FiraConnectorCapabilities;
import com.android.server.uwb.discovery.info.ScanInfo;
import com.android.server.uwb.discovery.info.TransportClientInfo;
import com.android.server.uwb.secure.SecureFactory;
import com.android.server.uwb.secure.SecureSession;
import com.android.server.uwb.transport.Transport;
import com.android.server.uwb.util.ObjectIdentifier;

import java.util.List;
import java.util.Optional;

/** Session for PACS profile controller */
public class PacsControllerSession extends RangingSessionController {
    private static final String TAG = "PACSControllerSession";
    private final ScanCallback mScanCallback;
    // TODO populate before calling secureSessionInit()
    private PacsControllerSessionInfo mControllerSessionInfo;
    private final PacsControllerSessionCallback mControllerSessionCallback;
    private final Transport mControllerTransport;
    private final TransportClientProvider.TransportClientCallback mClientCallback;

    public PacsControllerSession(
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
        mScanCallback = new ScanCallback(this);
        mControllerSessionCallback = new PacsControllerSessionCallback(this);
        mControllerSessionInfo = new PacsControllerSessionInfo(this);
        // TODO: Modify based on OOB transport implementation
        mControllerTransport = null;
        mClientCallback = null;
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

    private DiscoveryScanService mDiscoveryScanService;
    private DiscoveryInfo mDiscoveryInfo;
    private TransportClientService mTransportClientService;
    private SecureSession mSecureSession;

    private List<ScanFilter> mScanFilterList;
    private ScanSettings mScanSettings;

    public void setScanFilterList(List<ScanFilter> scanFilterList) {
        mScanFilterList = scanFilterList;
    }

    public void setScanSettings(ScanSettings scanSettings) {
        mScanSettings = scanSettings;
    }

    public void setTransportclientInfo(TransportClientInfo transportClientInfo) {
        mDiscoveryInfo.transportClientInfo = Optional.of(transportClientInfo);
    }

    /** Scan for devices */
    public void startScan() {
        ScanInfo scanInfo = new ScanInfo(mScanFilterList, mScanSettings);
        mDiscoveryInfo =
                new DiscoveryInfo(
                        DiscoveryInfo.TransportType.BLE,
                        Optional.of(scanInfo),
                        Optional.empty(),
                        Optional.empty());

        mDiscoveryScanService =
                new DiscoveryScanService(
                        mSessionInfo.mAttributionSource,
                        mSessionInfo.mContext,
                        new HandlerExecutor(mHandler),
                        mDiscoveryInfo,
                        mScanCallback);
        mDiscoveryScanService.startDiscovery();
    }

    /** Stop scanning on ranging stopped or closed */
    public void stopScan() {
        if (mDiscoveryScanService != null) {
            mDiscoveryScanService.stopDiscovery();
        }
    }

    /** Initialize transport client with updated TransportClientInfo */
    public void transportClientInit() {
        mTransportClientService = new TransportClientService(
                mSessionInfo.mAttributionSource,
                mSessionInfo.mContext,
                new HandlerExecutor(mHandler),
                mDiscoveryInfo,
                mClientCallback
        );

        FiraConnectorCapabilities firaConnectorCapabilities =
                new FiraConnectorCapabilities.Builder().build();
        mTransportClientService.setCapabilites(firaConnectorCapabilities);
        sendMessage(TRANSPORT_STARTED);
    }

    /** Start Transport client */
    public void transportClientStart() {
        mTransportClientService.start();
    }

    /** Stop Transport client */
    public void transportClientStop() {
        mTransportClientService.stop();
    }

    /** Initialize controller initiator session */
    public void secureSessionInit() {
        mSecureSession = SecureFactory.makeInitiatorSecureSession(
                mSessionInfo.mContext,
                mHandler.getLooper(),
                mControllerSessionCallback,
                mControllerSessionInfo,
                mControllerTransport
        );
    }

    @Override
    public UwbConfig getUwbConfig() {
        return PacsProfile.getPacsControllerProfile();
    }

    /** Implements callback of DiscoveryScanProvider */
    public static class ScanCallback implements DiscoveryScanProvider.DiscoveryScanCallback {

        public final PacsControllerSession mPacsControllerSession;

        public ScanCallback(PacsControllerSession pacsControllerSession) {
            mPacsControllerSession = pacsControllerSession;
        }

        @Override
        public void onDiscovered(DiscoveryScanProvider.DiscoveryResult result) {
            TransportClientInfo transportClientInfo =
                    new TransportClientInfo(result.scanResult);
            mPacsControllerSession.setTransportclientInfo(transportClientInfo);
            mPacsControllerSession.sendMessage(TRANSPORT_INIT);
        }

        @Override
        public void onDiscoveryFailed(int errorCode) {
            Log.e(TAG, "Discovery failed with error code: " + errorCode);
            mPacsControllerSession.sendMessage(DISCOVERY_FAILED);
        }
    }

    public static class PacsControllerSessionInfo implements
            RunningProfileSessionInfo {

        public final PacsControllerSession mPacsControllerSession;

        public PacsControllerSessionInfo(
                PacsControllerSession pacsControllerSession) {
            mPacsControllerSession = pacsControllerSession;
        }

        @NonNull
        @Override
        public ControlleeInfo getControlleeInfo() {
            return null;
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
            return null;
        }

        @NonNull
        @Override
        public List<ObjectIdentifier> getSelectableOidsOfPeerDevice() {
            return null;
        }

        @Override
        public boolean isUwbController() {
            return true;
        }

        @Override
        public boolean isUnicast() {
            return false;
        }

        @NonNull
        @Override
        public Optional<Integer> getSharedPrimarySessionId() {
            return Optional.of(mPacsControllerSession.mSessionInfo.getSessionId());
        }

        @NonNull
        @Override
        public Optional<byte[]> getSecureBlob() {
            return Optional.empty();
        }
    }

    public static class PacsControllerSessionCallback implements
            SecureSession.Callback {

        public final PacsControllerSession mPacsControllerSession;

        public PacsControllerSessionCallback(
                PacsControllerSession pacsControllerSession) {
            mPacsControllerSession = pacsControllerSession;
        }

        @Override
        public void onSessionDataReady(int updatedSessionId, @Nullable byte[] sessionData,
                boolean isSessionTerminated) {
            mPacsControllerSession.sendMessage(RANGING_INIT);
        }

        @Override
        public void onSessionAborted() {
        }

        @Override
        public void onSessionTerminated() {
        }
    }


    public class IdleState extends State {
        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter IdleState");
            }
            transitionTo(mDiscoveryState);
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit IdleState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            switch (message.what) {
                case SESSION_INITIALIZED:
                    if (mVerboseLoggingEnabled) {
                        log("Pacs controller session initialized");
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
            startScan();
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
                    if (mVerboseLoggingEnabled) {
                        log("Scanning failed ");
                    }
                    break;
                case SESSION_START:
                    startScan();
                    if (mVerboseLoggingEnabled) {
                        log("Started scanning");
                    }
                    break;
                case SESSION_STOP:
                    stopScan();
                    if (mVerboseLoggingEnabled) {
                        log("Stopped scanning");
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
            transportClientInit();
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
                    transportClientStart();
                    break;
                case SESSION_STOP:
                case TRANSPORT_COMPLETED:
                    stopScan();
                    transportClientStop();
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

        /**
         * TODO Once ranging starts with a client, controller should continue to scan
         * for other devices as this is a multicast session. Transition to discovery state
         * after session is started and add new devices discovered.
         **/
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
                    break;

                case SESSION_START:
                case RANGING_OPENED:
                    startScan();
                    startRanging();
                    break;

                case SESSION_STOP:
                    stopRanging();
                    stopScan();
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
            stopScan();
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
