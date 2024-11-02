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

package multidevices.snippet.ranging;

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
import java.util.Optional;
import java.util.UUID;
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
        adoptShellPermission();
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

    private enum Event {
        STARTED,
        START_FAILED,
        CLOSED,
        STOPPED,
        RESULTS_RECEIVED;
    }

    private class RangingSessionCallback implements RangingSession.Callback {

        private final String mCallbackId;
        private final ConcurrentMap<RangingDevice, ConcurrentMap<Integer, RangingData>>
                mReceivedData;

        RangingSessionCallback(String callbackId) {
            mCallbackId = callbackId;
            mReceivedData = new ConcurrentHashMap<>();
        }

        @Override
        public void onStarted(int technology) {
            Log.d(TAG, "RangingCallback#onStarted() called");
            mEventCache.postEvent(new SnippetEvent(mCallbackId, Event.STARTED.toString()));
        }

        @Override
        public void onStartFailed(int reason, RangingDevice device) {
        }

        @Override
        public void onClosed(int reasonCode) {
            Log.d(TAG, "RangingCallback#onClosed() called");
            mEventCache.postEvent(new SnippetEvent(mCallbackId, Event.CLOSED.toString()));
        }

        @Override
        public void onRangingStopped(@NonNull RangingDevice device) {
        }

        @Override
        public void onResults(@NonNull RangingDevice peer, @NonNull RangingData data) {
            Log.d(TAG, "RangingCallback#onResults() called");
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.RESULTS_RECEIVED.toString());
            event.getData().putString("peer", peer.getUuid().toString());
            event.getData().putInt("technology", data.getRangingTechnology());
            mEventCache.postEvent(event);
        }

        Optional<RangingData> getLastDataReceived(
                RangingDevice peer, @RangingTechnology int technology
        ) {
            if (mReceivedData.containsKey(peer)) {
                return Optional.ofNullable(mReceivedData.get(peer).remove(technology));
            } else {
                return Optional.empty();
            }
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
            Map<Integer, Integer> availabilities = capabilities.getTechnologyAvailabilityMap();
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

    @Rpc(description = "Check whether the cached ranging results include data from the specified "
            + "peer with the specified technology")
    public boolean verifyPeerFoundWithTechnology(
            String sessionHandle, String peerId, int technology
    ) {
        RangingSessionInfo sessionInfo = mSessions.get(sessionHandle);
        if (sessionInfo == null) {
            throw new IllegalArgumentException("Could not find session with id " + sessionHandle);
        }

        return sessionInfo
                .getCallback()
                .getLastDataReceived(
                        new RangingDevice.Builder().setUuid(UUID.fromString(peerId)).build(),
                        technology
                )
                .isPresent();
    }

    @Rpc(description = "Check whether the provided ranging technology is enabled")
    public boolean isTechnologyEnabled(int technology) {
        Integer availability = mTechnologyAvailability.get(technology);
        return availability != null
                && availability == RangingManager.RangingTechnologyAvailability.ENABLED;
    }

    @Rpc(description = "Set airplane mode")
    public void setAirplaneMode(boolean enabled) {
        mConnectivityManager.setAirplaneMode(enabled);
    }

    @Rpc(description = "Log info level message to device logcat")
    public void logInfo(String message) {
        Log.i(TAG, message);
    }
}
