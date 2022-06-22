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
import com.android.server.uwb.discovery.info.DiscoveryInfo;

import java.util.Optional;

/**
 * Session for PACS profile controller
 */
public class PacsControllerSession extends RangingSessionController {
    private static final String TAG = "PACSControllerSession";
    private final ScanCallback mScanCallback;

    public PacsControllerSession(SessionHandle sessionHandle,
            AttributionSource attributionSource,
            Context context,
            UwbInjector uwbInjector,
            ServiceProfileInfo serviceProfileInfo,
            IUwbRangingCallbacks rangingCallbacks,
            Handler handler) {
        super(sessionHandle, attributionSource, context, uwbInjector, serviceProfileInfo,
                rangingCallbacks, handler);
        mIdleState = new IdleState();
        mDiscoveryState = new DiscoveryState();
        mTransportState = new TransportState();
        mSecureSessionState = new SecureSessionState();
        mRangingState = new RangingState();
        mEndSessionState = new EndSessionState();
        mScanCallback = new ScanCallback(this);
    }

    /** Scan for devices */
    public void scanBle() {
        DiscoveryInfo discoveryInfo = new DiscoveryInfo(
                DiscoveryInfo.TransportType.BLE,
                Optional.empty(), Optional.empty());

        DiscoveryScanService discoveryScanService = new DiscoveryScanService(
                mSessionInfo.mAttributionSource,
                mSessionInfo.mContext,
                new HandlerExecutor(mHandler),
                discoveryInfo,
                mScanCallback
        );
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


    @Override
    public UwbConfig getUwbConfig() {
        return PacsProfile.getPacsControllerProfile();
    }

    public static class ScanCallback implements DiscoveryScanProvider.DiscoveryScanCallback {

        public final PacsControllerSession mPacsControllerSession;

        public ScanCallback(
                PacsControllerSession pacsControllerSession) {
            mPacsControllerSession = pacsControllerSession;
        }

        @Override
        public void onDiscovered(DiscoveryScanProvider.DiscoveryResult result) {
        }

        @Override
        public void onDiscoveryFailed(int errorCode) {
            Log.w(TAG, "Discovery failed");
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
                case DISCOVERY_INIT:
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
            //scanBle();
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
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit TransportState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            return true;
        }
    }

    public class SecureSessionState extends State {

        @Override
        public void enter() {
            if (mVerboseLoggingEnabled) {
                log("Enter SecureSessionState");
            }
        }

        @Override
        public void exit() {
            if (mVerboseLoggingEnabled) {
                log("Exit SecureSessionState");
            }
        }

        @Override
        public boolean processMessage(Message message) {
            transitionTo(mRangingState);
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
                        startRangingSession();
                    } catch (RemoteException e) {
                        Log.e(TAG, "Ranging session start failed");
                        e.printStackTrace();
                    }
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
