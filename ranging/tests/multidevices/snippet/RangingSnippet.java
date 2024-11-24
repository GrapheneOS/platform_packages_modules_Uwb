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

package com.google.snippet.ranging;

import android.app.UiAutomation;
import android.content.Context;
import android.net.ConnectivityManager;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingManager.RangingTechnology;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RangingSnippet implements Snippet {
    private static final String TAG = "GenericRangingSnippet";

    private final Context mContext;
    private final RangingManager mRangingManager;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final EventCache mEventCache = EventCache.getInstance();
    private final ConnectivityManager mConnectivityManager;
    private final ConcurrentMap<String, RangingSessionInfo> mSessions;
    private final ConcurrentMap<Integer, Integer> mTechnologyAvailability;

    public RangingSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mRangingManager = mContext.getSystemService(RangingManager.class);

        mSessions = new ConcurrentHashMap<>();
        mTechnologyAvailability = new ConcurrentHashMap<>();
        mRangingManager.registerCapabilitiesCallback(mExecutor, new AvailabilityListener());
    }

    private void adoptShellPermission() {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to cleanup ui automation", e);
        }
    }

    private void dropShellPermission() throws Throwable {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.dropShellPermissionIdentity();
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to cleanup ui automation", e);
        }
    }

    private enum Event {
        OPENED,
        OPEN_FAILED,
        STARTED,
        DATA,
        STOPPED,
        CLOSED
    }

    private class RangingSessionCallback implements RangingSession.Callback {

        private final String mCallbackId;

        RangingSessionCallback(String callbackId) {
            mCallbackId = callbackId;
        }

        @Override
        public void onOpened() {
            Log.d(TAG, "onOpened");
            mEventCache.postEvent(new SnippetEvent(mCallbackId, Event.OPENED.toString()));
        }

        @Override
        public void onOpenFailed(@Reason int reason) {
            Log.d(TAG, "onOpenFailed");
            mEventCache.postEvent(new SnippetEvent(mCallbackId, Event.OPEN_FAILED.toString()));
        }

        @Override
        public void onStarted(@NonNull RangingDevice peer, @RangingTechnology int technology) {
            Log.d(TAG, "onStarted");
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.STARTED.toString());
            event.getData().putString("peer", peer.getUuid().toString());
            event.getData().putInt("technology", technology);
            mEventCache.postEvent(event);
        }

        @Override
        public void onResults(@NonNull RangingDevice peer, @NonNull RangingData data) {
            Log.d(TAG, "onData");
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.DATA.toString());
            event.getData().putString("peer", peer.getUuid().toString());
            event.getData().putInt("technology", data.getRangingTechnology());
            mEventCache.postEvent(event);
        }

        @Override
        public void onStopped(@NonNull RangingDevice peer, @RangingTechnology int technology) {
            Log.d(TAG, "onStopped");
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.STOPPED.toString());
            event.getData().putString("peer", peer.getUuid().toString());
            event.getData().putInt("technology", technology);
            mEventCache.postEvent(event);
        }

        @Override
        public void onClosed(@Reason int reason) {
            Log.d(TAG, "onClosed");
            mEventCache.postEvent(new SnippetEvent(mCallbackId, Event.CLOSED.toString()));
        }
    }

    private static class RangingSessionInfo {
        private final RangingSession mSession;
        private final RangingSessionCallback mCallback;

        RangingSessionInfo(RangingSession session, RangingSessionCallback callback) {
            mSession = session;
            mCallback = callback;
        }

        public RangingSession getSession() {
            return mSession;
        }

        public RangingSessionCallback getCallback() {
            return mCallback;
        }
    }

    private class AvailabilityListener implements RangingManager.RangingCapabilitiesCallback {
        @Override
        public void onRangingCapabilities(@NonNull RangingCapabilities capabilities) {
            Map<Integer, Integer> availabilities = capabilities.getTechnologyAvailability();
            mTechnologyAvailability.putAll(availabilities);
        }
    }


    @AsyncRpc(description = "Start a ranging session")
    public void startRanging(
            String callbackId, String sessionHandle, RangingPreference preference
    ) {
        RangingSessionCallback callback = new RangingSessionCallback(callbackId);
        RangingSession session = mRangingManager.createRangingSession(mExecutor, callback);
        mSessions.put(sessionHandle, new RangingSessionInfo(session, callback));
        session.start(preference);
    }

    @AsyncRpc(description = "Stop a ranging session")
    public void stopRanging(String unused, String sessionHandle) {
        RangingSessionInfo sessionInfo = mSessions.get(sessionHandle);
        if (sessionInfo != null) {
            sessionInfo.getSession().stop();
            mSessions.remove(sessionHandle);
        }
    }

    @Rpc(description = "Check whether the provided ranging technology is enabled")
    public boolean isTechnologyEnabled(int technology) {
        Integer availability = mTechnologyAvailability.get(technology);
        return availability != null
                && availability == RangingCapabilities.ENABLED;
    }

    @Rpc(description = "Check whether the provided ranging technology is supported")
    public boolean isTechnologySupported(int technology) {
        Integer availability = mTechnologyAvailability.get(technology);
        return availability != null
                && availability != RangingCapabilities.NOT_SUPPORTED;
    }

    @Rpc(description = "Set airplane mode")
    public void setAirplaneMode(boolean enabled) throws Throwable {
        adoptShellPermission();
        mConnectivityManager.setAirplaneMode(enabled);
        dropShellPermission();
    }

    @Rpc(description = "Log info level message to device logcat")
    public void logInfo(String message) {
        Log.i(TAG, message);
    }
}
