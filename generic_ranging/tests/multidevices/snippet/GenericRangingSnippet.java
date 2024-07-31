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
import android.os.RemoteException;
import android.util.Log;
import android.uwb.UwbManager;

import androidx.core.uwb.backend.impl.internal.RangingParameters;
import androidx.core.uwb.backend.impl.internal.UwbAddress;
import androidx.core.uwb.backend.impl.internal.UwbComplexChannel;
import androidx.core.uwb.backend.impl.internal.UwbRangeDataNtfConfig;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.ranging.PrecisionData;
import com.android.ranging.PrecisionRanging;
import com.android.ranging.PrecisionRangingConfig;
import com.android.ranging.PrecisionRangingImpl;
import com.android.ranging.RangingData;
import com.android.ranging.RangingTechnology;
import com.android.ranging.adapter.UwbAdapter;

import com.google.android.mobly.snippet.Snippet;
import com.google.android.mobly.snippet.event.EventCache;
import com.google.android.mobly.snippet.event.SnippetEvent;
import com.google.android.mobly.snippet.rpc.AsyncRpc;
import com.google.android.mobly.snippet.rpc.Rpc;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import dagger.Lazy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

public class GenericRangingSnippet implements Snippet {
    private static final String TAG = "GenericRangingSnippet: ";

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final UwbManager mUwbManager;
    private final ListeningExecutorService mExecutor = MoreExecutors.listeningDecorator(
            Executors.newSingleThreadExecutor());
    private final EventCache mEventCache = EventCache.getInstance();
    private static final HashMap<String, PrecisionRanging> sRangingHashMap =
            new HashMap<>();
    private static final HashMap<String, GenericRangingCallback> sRangingCallbackHashMap =
            new HashMap<>();

    public GenericRangingSnippet() throws Throwable {
        adoptShellPermission();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mConnectivityManager = mContext.getSystemService(ConnectivityManager.class);
        mUwbManager = mContext.getSystemService(UwbManager.class);
    }

    private static class UwbManagerSnippetException extends Exception {

        UwbManagerSnippetException(String msg) {
            super(msg);
        }

        UwbManagerSnippetException(String msg, Throwable err) {
            super(msg, err);
        }
    }

    private void adoptShellPermission() throws Throwable {
        UiAutomation uia = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uia.adoptShellPermissionIdentity();
        try {
            Class<?> cls = Class.forName("android.app.UiAutomation");
            Method destroyMethod = cls.getDeclaredMethod("destroy");
            destroyMethod.invoke(uia);
        } catch (ReflectiveOperationException e) {
            throw new UwbManagerSnippetException("Failed to cleaup Ui Automation", e);
        }
    }

    private enum Event {
        Invalid(0),
        Started(1 << 0),
        Stopped(1 << 1),
        ReportReceived(1 << 2),
        EventAll(
                1 << 0
                        | 1 << 1
                        | 1 << 2
        );

        private final int mType;

        Event(int type) {
            mType = type;
        }

        private int getType() {
            return mType;
        }
    }

    class GenericRangingCallback implements PrecisionRanging.Callback {
        private String mId;
        private PrecisionData mLastDataReceived = null;

        GenericRangingCallback(String id, int events) {
            mId = id;
        }

        public Optional<PrecisionData> getLastDataReceived() {
            if (mLastDataReceived == null) {
                return Optional.empty();
            }
            return Optional.of(mLastDataReceived);
        }

        private void handleEvent(Event e) {
            Log.d(TAG, "GenericRangingCallback#handleEvent() for " + e.toString());
            SnippetEvent event = new SnippetEvent(mId, "GenericRangingCallback");
            event.getData().putString("genericRangingSessionEvent", e.toString());
            mEventCache.postEvent(event);
        }

        @Override
        public void onStarted() {
            Log.d(TAG, "GenericRangingCallback#onStarted() called");
            handleEvent(Event.Started);
        }

        @Override
        public void onStopped(int reason) {
            Log.d(TAG, "GenericRangingCallback#onStopped() called");
            handleEvent(Event.Stopped);
        }

        @Override
        public void onData(PrecisionData data) {
            Log.d(TAG, "GenericRangingCallback#onData() called");
            mLastDataReceived = data;
            handleEvent(Event.ReportReceived);
        }
    }

    private RangingParameters generateRangingParameters(JSONObject j) throws JSONException {
        if (j == null) {
            return null;
        }
        List<UwbAddress> peerAddresses = new ArrayList<>();
        if (j.has("peerAddresses")) {
            JSONArray jArray = j.getJSONArray("peerAddresses");
            UwbAddress[] destinationUwbAddresses = new UwbAddress[jArray.length()];
            for (int i = 0; i < jArray.length(); i++) {
                destinationUwbAddresses[i] = UwbAddress.fromBytes(
                        convertJSONArrayToByteArray(jArray.getJSONArray(i)));
            }
            peerAddresses = Arrays.asList(destinationUwbAddresses);
        }
        UwbComplexChannel uwbComplexChannel = new UwbComplexChannel(9, 11);
        UwbRangeDataNtfConfig rangeDataNtfConfig = new UwbRangeDataNtfConfig.Builder()
                .setRangeDataConfigType(j.getInt("rangeDataConfigType"))
                .build();

        return new RangingParameters(
                j.getInt("configType"),
                j.getInt("sessionId"),
                j.getInt("subSessionId"),
                convertJSONArrayToByteArray(j.getJSONArray("sessionKeyInfo")),
                j.has("subSessionKeyInfo")
                        ? convertJSONArrayToByteArray(j.getJSONArray("subSessionKeyInfo"))
                        : null,
                uwbComplexChannel,
                peerAddresses,
                j.getInt("updateRateType"),
                rangeDataNtfConfig,
                j.getInt("slotDurationMillis"),
                j.getBoolean("isAoaDisabled")
        );
    }

    private byte[] convertJSONArrayToByteArray(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        byte[] bArray = new byte[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            bArray[i] = (byte) jArray.getInt(i);
        }
        return bArray;
    }

    private static String getUwbSessionKeyFromId(int sessionId) {
        return "uwb_session_" + sessionId;
    }

    @AsyncRpc(description = "Start UWB ranging session")
    public void startUwbRanging(String callbackId, JSONObject config)
            throws JSONException, RemoteException {
        int deviceType = config.getInt("deviceType");
        UwbAdapter uwbAdapter = null;
        if (deviceType == 0) {
            logInfo("Starting controlee session");
            uwbAdapter = new UwbAdapter(mContext, mExecutor, UwbAdapter.DeviceType.CONTROLEE);
        } else {
            logInfo("Starting controller session");
            uwbAdapter = new UwbAdapter(mContext, mExecutor, UwbAdapter.DeviceType.CONTROLLER);
        }

        //TODO: Make this configurable
        //    private Provider<PrecisionRanging.Factory> mRangingFactory;
        PrecisionRangingConfig precisionRangingConfig =
                PrecisionRangingConfig.builder().setRangingTechnologiesToRangeWith(
                        ImmutableList.of(RangingTechnology.UWB)).setUseFusingAlgorithm(
                        false).setMaxUpdateInterval(
                        Duration.ofMillis(200)).setFusionAlgorithmDriftTimeout(
                        Duration.ofSeconds(1)).setNoUpdateTimeout(
                        Duration.ofSeconds(2)).setInitTimeout(Duration.ofSeconds(3)).build();

        PrecisionRanging precisionRanging = new PrecisionRangingImpl(
                new CustomUwbAdapterProvider(uwbAdapter), mContext, precisionRangingConfig,
                Executors.newSingleThreadScheduledExecutor(),
                Optional.of(ImmutableMap.of(RangingTechnology.UWB, uwbAdapter)));

        precisionRanging.setUwbConfig(generateRangingParameters(config));
        uwbAdapter.setLocalAddressForTesting(UwbAddress.fromBytes(
                convertJSONArrayToByteArray(config.getJSONArray("deviceAddress"))));

        // Test forces channel to 9 and preamble to 11
        uwbAdapter.setComplexChannelForTesting();
        precisionRanging.getUwbComplexChannel();

        GenericRangingCallback genericRangingCallback =
                new GenericRangingCallback(callbackId, Event.EventAll.getType());
        String uwbSessionKey = getUwbSessionKeyFromId(config.getInt("sessionId"));
        sRangingHashMap.put(uwbSessionKey, precisionRanging);
        precisionRanging.start(genericRangingCallback);
        sRangingCallbackHashMap.put(uwbSessionKey, genericRangingCallback);
    }

    @Rpc(description = "Stop UWB ranging session")
    public void stopUwbRanging(int sessionId) throws JSONException {
        String uwbSessionKey = getUwbSessionKeyFromId(sessionId);
        if (sRangingHashMap.containsKey(uwbSessionKey)) {
            sRangingHashMap.get(uwbSessionKey).stop();
        }
    }

    @Rpc(description = "Check whether the last report included UWB data from the specified address")
    public boolean verifyUwbPeerFound(JSONArray peerAddress, int sessionId)
            throws JSONException {
        GenericRangingCallback callback =
                sRangingCallbackHashMap.get(getUwbSessionKeyFromId(sessionId));
        if (callback == null) {
            throw new IllegalArgumentException("Could not find session with id " + sessionId);
        }

        Optional<PrecisionData> precisionData = callback.getLastDataReceived();
        if (precisionData.isEmpty() || precisionData.get().getRangingData().isEmpty()) {
            Log.i(TAG, "No data has been received yet, or the last data received was empty");
            return false;
        }

        byte[] address = convertJSONArrayToByteArray(peerAddress);
        ImmutableList<RangingData> rangingData = precisionData.get().getRangingData().get();
        for (RangingData data : rangingData) {
            if (data.getRangingTechnology() == RangingTechnology.UWB
                    && Arrays.equals(data.getPeerAddress(), address)) {
                return true;
            }
        }
        Log.i(TAG, "Last ranging report did not include any data from peer "
                + Arrays.toString(address));
        return false;
    }

    @Rpc(description = "Check whether uwb is enabled")
    public boolean isUwbEnabled() {
        return mUwbManager.isUwbEnabled();
    }

    @Rpc(description = "Set airplane mode")
    public void setAirplaneMode(boolean enabled) {
        mConnectivityManager.setAirplaneMode(enabled);
    }

    @Rpc(description = "Log info level message to device logcat")
    public void logInfo(String message) throws JSONException {
        com.google.android.mobly.snippet.util.Log.i(TAG + message);
    }

    private static class CustomUwbAdapterProvider implements Lazy<UwbAdapter> {
        private final UwbAdapter mUwbAdapter;

        CustomUwbAdapterProvider(UwbAdapter uwbAdapter) {
            this.mUwbAdapter = uwbAdapter;
        }

        @Override
        public UwbAdapter get() {
            return mUwbAdapter;
        }
    }
}

