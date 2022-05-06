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

package com.android.server.uwb.secure;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.pm.RunningProfileSessionInfo;
import com.android.server.uwb.secure.csml.DispatchResponse;

/**
 * Interface for a Dynamic STS session, which set up secure channel and exchange
 * the UWB parameters.
 */
public abstract class SecureSession {
    private static final String LOG_TAG = "SecureSession";

    protected final Handler mWorkHandler;
    protected final FiRaSecureChannel mFiRaSecureChannel;
    protected final Callback mSessionCallback;
    protected final RunningProfileSessionInfo mRunningProfileSessionInfo;

    private final FiRaSecureChannel.SecureChannelCallback mSecureChannelCallback =
            new FiRaSecureChannel.SecureChannelCallback() {
                @Override
                public void onEstablished() {
                    handleFiRaSecureChannelEstablished();
                }

                @Override
                public void onSetUpError(FiRaSecureChannel.SetupError error) {
                    logw("secure channel set up error: " + error);
                    mFiRaSecureChannel.cleanUpTerminatedOrAbortedSession();
                    mSessionCallback.onSessionAborted();
                }

                @Override
                public void onDispatchResponseAvailable(DispatchResponse dispatchResponse) {
                    handleDispatchResponse(dispatchResponse);
                }

                @Override
                public void onDispatchCommandFailure() {
                    handleDispatchCommandFailure();
                }

                @Override
                public void onTerminated(boolean withError) {
                    logd("session is terminated with error: " + withError);
                    mSessionCallback.onSessionTerminated();
                }

                @Override
                public void onSeChannelClosed(boolean withError) {
                    // TODO: no action, may be removed.
                }
            };

    SecureSession(@NonNull Looper workLooper,
            @NonNull FiRaSecureChannel fiRaSecureChannel,
            @NonNull Callback sessionCallback,
            @NonNull RunningProfileSessionInfo runningProfileSessionInfo) {
        mWorkHandler = new Handler(workLooper);
        mFiRaSecureChannel = fiRaSecureChannel;
        mSessionCallback = sessionCallback;
        mRunningProfileSessionInfo = runningProfileSessionInfo;
    }

    protected abstract void handleDispatchCommandFailure();

    protected abstract void handleDispatchResponse(@NonNull DispatchResponse dispatchResponse);

    protected abstract void handleFiRaSecureChannelEstablished();

    /**
     * Start the dynamic STS secure session set up.
     */
    public final void startSession() {
        mFiRaSecureChannel.init(mSecureChannelCallback);
    }

    /**
     * Terminate the dynamic STS secure session.
     */
    public abstract void terminateSession();

    /**
     * Callback to get the secure session information.
     */
    public interface Callback {
        /**
         * The session data is ready, the UWB configuration of Controller can be sent to UWBS.
         *
         * @param sessionData         null for controller, TLV data for controllee.
         * @param isSessionTerminated If the session is not terminated, the client should
         *                            terminate the session accordingly.
         */
        // TODO: what if the 1 to m case? is this sub sessionId ?
        void onSessionDataReady(int updatedSessionId,
                @Nullable byte[] sessionData, boolean isSessionTerminated);

        /**
         * Something wrong, the session is aborted.
         */
        void onSessionAborted();

        /**
         * Session is terminated as responding to the calling of {@code #termiaateSession}.
         */
        void onSessionTerminated();
    }

    private void logd(@NonNull String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }

    private void logw(@NonNull String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }
}
