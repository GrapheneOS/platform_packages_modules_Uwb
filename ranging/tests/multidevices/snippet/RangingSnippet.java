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
import android.net.wifi.WifiManager;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingManager.RangingTechnology;
import android.ranging.RangingMeasurement;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.oob.TransportHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class RangingSnippet implements Snippet {
    private static final String TAG = "GenericRangingSnippet";

    private final Context mContext;
    private final RangingManager mRangingManager;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final EventCache mEventCache = EventCache.getInstance();
    private final ConnectivityManager mConnectivityManager;
    private final WifiManager mWifiManager;
    private final ConcurrentMap<String, RangingSessionInfo> mSessions;
    private final ConcurrentMap<Integer, Integer> mTechnologyAvailability;
    private final AtomicReference<RangingCapabilities> mRangingCapabilities =
            new AtomicReference<>();

    public RangingSnippet() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mWifiManager = mContext.getSystemService(WifiManager.class);
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
        CLOSED,
        OOB_SEND_CAPABILITIES_REQUEST,
        OOB_SEND_CAPABILITIES_RESPONSE,
        OOB_SEND_SET_CONFIGURATION,
        OOB_SEND_STOP_RANGING,
        OOB_SEND_UNKNOWN,
        OOB_CLOSED
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
            event.getData().putString("peer_id", peer.getUuid().toString());
            event.getData().putInt("technology", technology);
            mEventCache.postEvent(event);
        }

        @Override
        public void onResults(@NonNull RangingDevice peer, @NonNull RangingData data) {
            Log.d(TAG, "onData { peer: " + peer.getUuid()
                    + " Distance: " + data.getDistance()
                    + " Azimuth: " + data.getAzimuth()
                    + " Elevation: " + data.getElevation()
                    + " RangingTechnology: " + data.getRangingTechnology()
                    + " Timestamp: " + data.getTimestampMillis()
                    + " hasRssi: " + data.hasRssi()
                    + " getRssi: " + (data.hasRssi() ? data.getRssi() : "null")
                    + " }");
            RangingMeasurement distance = data.getDistance();
            if (distance != null) {
                Log.d(TAG, " Distance: " + distance.getMeasurement()
                        + "  Confidence: " + distance.getConfidence());
            }
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.DATA.toString());
            event.getData().putString("peer_id", peer.getUuid().toString());
            event.getData().putInt("technology", data.getRangingTechnology());
            mEventCache.postEvent(event);
        }

        @Override
        public void onStopped(@NonNull RangingDevice peer, @RangingTechnology int technology) {
            Log.d(TAG, "onStopped");
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.STOPPED.toString());
            event.getData().putString("peer_id", peer.getUuid().toString());
            event.getData().putInt("technology", technology);
            mEventCache.postEvent(event);
        }

        @Override
        public void onClosed(@Reason int reason) {
            Log.d(TAG, "onClosed");
            mEventCache.postEvent(new SnippetEvent(mCallbackId, Event.CLOSED.toString()));
        }
    }

    static class RangingSessionInfo {
        private final RangingSession mSession;
        private final RangingSessionCallback mCallback;
        private final ConcurrentMap<RangingDevice, OobTransportImpl> mOobTransports;

        RangingSessionInfo(RangingSession session, RangingSessionCallback callback) {
            mSession = session;
            mCallback = callback;
            mOobTransports = new ConcurrentHashMap<>();
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
            mRangingCapabilities.set(capabilities);
        }
    }


    class OobTransportFactory {
        private final String mCallbackId;
        private final RangingSessionInfo mSessionInfo;

        OobTransportFactory(String callbackId, RangingSessionInfo sessionInfo) {
            mCallbackId = callbackId;
            mSessionInfo = sessionInfo;
        }

        public OobTransportImpl createOobTransport(RangingDevice peer) {
            OobTransportImpl transport = new OobTransportImpl(mCallbackId, peer);
            mSessionInfo.mOobTransports.put(peer, transport);
            return transport;
        }
    }

    class OobTransportImpl implements TransportHandle {
        private final String mCallbackId;
        private final RangingDevice mPeer;
        private ReceiveCallback mReceiveCallback;

        OobTransportImpl(String callbackId, RangingDevice peer) {
            mCallbackId = callbackId;
            mPeer = peer;
        }

        private SnippetEvent getOobEvent(@NonNull byte[] data) {
            int messageType = data[1];
            switch (messageType) {
                case 0:
                    return new SnippetEvent(
                            mCallbackId,
                            Event.OOB_SEND_CAPABILITIES_REQUEST.toString());
                case 1:
                    return new SnippetEvent(
                            mCallbackId,
                            Event.OOB_SEND_CAPABILITIES_RESPONSE.toString());
                case 2:
                    return new SnippetEvent(
                            mCallbackId,
                            Event.OOB_SEND_SET_CONFIGURATION.toString());
                case 6:
                    return new SnippetEvent(
                            mCallbackId,
                            Event.OOB_SEND_STOP_RANGING.toString());
                default:
                    return new SnippetEvent(
                            mCallbackId,
                            Event.OOB_SEND_UNKNOWN.toString());
            }
        }
        @Override
        public void sendData(@NonNull byte[] data) {
            SnippetEvent event = getOobEvent(data);
            event.getData().putString("peer_id", mPeer.getUuid().toString());
            event.getData().putByteArray("data", data);
            mEventCache.postEvent(event);
        }

        @Override
        public void registerReceiveCallback(
                @NonNull Executor executor, @NonNull ReceiveCallback callback
        ) {
            mReceiveCallback = callback;
        }

        @Override
        public void close() throws Exception {
            Log.d(TAG, "TransportHandle close");
            SnippetEvent event = new SnippetEvent(mCallbackId, Event.OOB_CLOSED.toString());
            event.getData().putString("peer_id", mPeer.getUuid().toString());
            mEventCache.postEvent(event);
        }
    }

    @AsyncRpc(description = "Start a ranging session")
    public void startRanging(
            String callbackId, String sessionHandle, JSONObject j
    ) throws JSONException {

        RangingSessionCallback callback = new RangingSessionCallback(callbackId);
        RangingSession session = mRangingManager.createRangingSession(mExecutor, callback);
        RangingSessionInfo sessionInfo = new RangingSessionInfo(session, callback);
        mSessions.put(sessionHandle, sessionInfo);

        RangingPreference preference =
                new RangingPreferenceConverter(new OobTransportFactory(callbackId, sessionInfo))
                        .deserialize(j, RangingPreference.class);

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

    @Rpc(description = "Handle data received from a peer via OOB")
    public void handleOobDataReceived(String sessionHandle, String peerId, byte[] data) {
        mSessions.get(sessionHandle)
                .mOobTransports.get(new RangingDevice.Builder()
                        .setUuid(UUID.fromString(peerId))
                        .build())
                .mReceiveCallback.onReceiveData(data);
    }

    @Rpc(description = "Handle an OOB peer disconnecting")
    public void handleOobPeerDisconnected(String sessionHandle, String peerId) {
        mSessions.get(sessionHandle)
                .mOobTransports.get(new RangingDevice.Builder()
                        .setUuid(UUID.fromString(peerId))
                        .build())
                .mReceiveCallback.onDisconnect();
    }

    @Rpc(description = "Handle an OOB peer reconnecting")
    public void handleOobPeerReconnect(String sessionHandle, String peerId) {
        mSessions.get(sessionHandle)
                .mOobTransports.get(new RangingDevice.Builder()
                        .setUuid(UUID.fromString(peerId))
                        .build())
                .mReceiveCallback.onReconnect();
    }

    @Rpc(description = "Handle an OOB transport closing")
    public void handleOobClosed(String sessionHandle, String peerId) {
        mSessions.get(sessionHandle)
                .mOobTransports.get(new RangingDevice.Builder()
                        .setUuid(UUID.fromString(peerId))
                        .build())
                .mReceiveCallback.onClose();
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

    @Rpc(description = "Check whether periodic RTT ranging technology is supported")
    public boolean hasPeriodicRangingHwFeature() {
        RangingCapabilities capabilities = mRangingCapabilities.get();
        if (capabilities == null) {
            return false;
        }
        return capabilities.getRttRangingCapabilities().hasPeriodicRangingHardwareFeature();
    }

    @Rpc(description = "Set airplane mode")
    public void setAirplaneMode(boolean enabled) throws Throwable {
        runWithShellPermission(() -> mConnectivityManager.setAirplaneMode(enabled));
    }

    @Rpc(description = "Set wifi mode")
    public void setWifiEnabled(boolean enabled) throws Throwable {
        runWithShellPermission(() -> mWifiManager.setWifiEnabled(enabled));
    }

    @Rpc(description = "Return wifi mode")
    public boolean isWifiEnabled() throws Throwable {
        return runWithShellPermission(() -> mWifiManager.isWifiEnabled());
    }

    @Rpc(description = "Log info level message to device logcat")
    public void logInfo(String message) {
        Log.i(TAG, message);
    }

    public void runWithShellPermission(Runnable action) throws Throwable {
        adoptShellPermission();
        try {
            action.run();
        } finally {
            dropShellPermission();
        }
    }

    public <T> T runWithShellPermission(ThrowingSupplier<T> action) throws Throwable {
        adoptShellPermission();
        try {
            return action.get();
        } finally {
            dropShellPermission();
        }
    }

    /**
     * Similar to {@link Supplier} but has {@code throws Exception}.
     *
     * @param <T> type of the value produced
     */
    public interface ThrowingSupplier<T> {
        /**
         * Similar to {@link Supplier#get} but has {@code throws Exception}.
         */
        T get() throws Exception;
    }
}
