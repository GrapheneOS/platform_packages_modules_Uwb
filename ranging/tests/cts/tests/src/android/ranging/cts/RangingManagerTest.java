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

package android.ranging.cts;

import static android.ranging.RangingCapabilities.NOT_SUPPORTED;
import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingManager.RangingCapabilitiesCallback;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfiguration;
import android.ranging.raw.RawInitiatorRangingParams;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingParams;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.rtt.RttRangingParams;
import android.util.Log;
import android.uwb.UwbManager;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get RangingManager in instant app mode")
public class RangingManagerTest {
    private static final String TAG = "RangingManagerTest";
    private final Context mContext = InstrumentationRegistry.getContext();
    private RangingManager mRangingManager;

    @Before
    public void setup() throws Exception {
        mRangingManager = mContext.getSystemService(RangingManager.class);
        assertThat(mRangingManager).isNotNull();
    }

    @After
    public void teardown() throws Exception {
        // Just in case if some test failed.
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
    }

    private RangingPreference getGenericRangingPreference(int deviceRole) {
        // Generic ranging preference, Improve this method based on future needs.
        return new RangingPreference.Builder(deviceRole,
                new RawInitiatorRangingParams.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(10,
                                        CONFIG_UNICAST_DS_TWR,
                                        UwbAddress.fromBytes(new byte[]{1, 2}),
                                        UwbAddress.fromBytes(new byte[]{3, 4}))
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSessionKeyInfo(
                                                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                                        2, 1})
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .build())
                                .build())
                        .build())
                .build();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStartRangingSession_WithoutPermission() throws Exception {
        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = getGenericRangingPreference(DEVICE_ROLE_INITIATOR);

        try {
            rangingSession.start(preference);
            // Caller does not hold RANGING permission, should fail if start was successful.
            fail();
        } catch (SecurityException e) {
            // pass
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStopRangingSession_WithoutPermission() throws Exception {

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        try {
            rangingSession.stop();
            // Caller does not hold RANGING permission, should fail if start was successful.
            fail();
        } catch (SecurityException e) {
            // pass
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStartStopRangingSession() throws Exception {
        int sessionId = 10;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingParams.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(
                                        sessionId, CONFIG_UNICAST_DS_TWR,
                                        UwbAddress.fromBytes(new byte[]{1, 2}),
                                        UwbAddress.fromBytes(new byte[]{3, 4}))
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSessionKeyInfo(
                                                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                                        2, 1})
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .build())
                                .build())
                        .build())
                .setSessionConfiguration(new SessionConfiguration.Builder()
                        .setRangingMeasurementsLimit(1000)
                        .setAngleOfArrivalNeeded(true)
                        .setSensorFusionParameters(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .build())
                .build();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStartStopMultipleRangingSessions() throws Exception {
        int sessionId = 10;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback1 = new RangingSessionCallback();
        RangingSessionCallback callback2 = new RangingSessionCallback();

        RangingSession rangingSession1 = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback1);
        assertThat(rangingSession1).isNotNull();

        RangingSession rangingSession2 = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback2);
        assertThat(rangingSession2).isNotNull();
        RangingPreference preference1 = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingParams.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(sessionId,
                                        CONFIG_UNICAST_DS_TWR,
                                        UwbAddress.fromBytes(new byte[]{1, 2}),
                                        UwbAddress.fromBytes(new byte[]{3, 4}))
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSessionKeyInfo(
                                                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                                        2, 1})
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .build())
                                .build())
                        .build())
                .build();

        RangingPreference preference2 = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingParams.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(sessionId,
                                        CONFIG_UNICAST_DS_TWR,
                                        UwbAddress.fromBytes(new byte[]{3, 5}),
                                        UwbAddress.fromBytes(new byte[]{1, 2}))
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSessionKeyInfo(
                                                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                                        2, 1})
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .build())
                                .build())
                        .build())
                .build();

        rangingSession1.start(preference1);
        rangingSession2.start(preference2);

        assertThat(callback1.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callback2.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession1.stop();
        rangingSession2.stop();

        assertThat(callback1.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callback2.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    private static class RangingSessionCallback implements RangingSession.Callback {

        private final CountDownLatch mOnOpenedCalled = new CountDownLatch(1);
        private final CountDownLatch mOnClosedCalled = new CountDownLatch(1);

        @Override
        public void onOpened() {
            mOnOpenedCalled.countDown();
        }

        @Override
        public void onOpenFailed(int reason) {
        }

        @Override
        public void onStarted(@NonNull RangingDevice peer,
                @RangingManager.RangingTechnology int technology) {
        }

        @Override
        public void onResults(@NonNull RangingDevice peer, @NonNull RangingData data) {
        }

        @Override
        public void onStopped(@NonNull RangingDevice peer,
                @RangingManager.RangingTechnology int technology) {
        }

        @Override
        public void onClosed(int reasonCode) {
            mOnClosedCalled.countDown();
        }

    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testCapabilitiesListener() throws InterruptedException {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        CapabilitiesCallback callback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                callback);

        assertThat(callback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnCapabilitiesReceived).isTrue();
        assertThat(callback.mRangingCapabilities).isNotNull();

        callback.reset(new CountDownLatch(1));
        UwbManager uwbManager = mContext.getSystemService(UwbManager.class);
        uwbManager.setUwbEnabled(!uwbManager.isUwbEnabled());

        assertThat(callback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnCapabilitiesReceived).isTrue();
        assertThat(callback.mRangingCapabilities).isNotNull();

        uwbManager.setUwbEnabled(true);
        callback.reset(new CountDownLatch(1));
        assertThat(callback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnCapabilitiesReceived).isTrue();
        assertThat(callback.mRangingCapabilities).isNotNull();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_rtt_enabled")
    public void testRttRangingInitiator() throws InterruptedException {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(capabilitiesCallback.mOnCapabilitiesReceived).isTrue();
        assertThat(capabilitiesCallback.mRangingCapabilities).isNotNull();
        assertThat(
                capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability())
                .isNotNull();

        assumeTrue(capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_NAN_RTT) != NOT_SUPPORTED);
        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingParams.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_1")
                                        .build())
                                .build())
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_2")
                                        .build())
                                .build())
                        .build())
                .build();

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_rtt_enabled")
    public void testRttRangingResponder() throws InterruptedException {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(capabilitiesCallback.mOnCapabilitiesReceived).isTrue();
        assertThat(capabilitiesCallback.mRangingCapabilities).isNotNull();
        assertThat(
                capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability())
                .isNotNull();

        assumeTrue(capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_NAN_RTT) != NOT_SUPPORTED);
        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new RawResponderRangingParams.Builder()
                        .setRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_1")
                                        .build())
                                .build())
                        .build())
                .build();

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_rtt_enabled")
    public void testMultiRangingResponder() throws InterruptedException {
        int sessionId = 10;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(capabilitiesCallback.mOnCapabilitiesReceived).isTrue();
        assertThat(capabilitiesCallback.mRangingCapabilities).isNotNull();
        assertThat(
                capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability())
                .isNotNull();

        assumeTrue(capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_NAN_RTT) != NOT_SUPPORTED);
        assumeTrue(capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.UWB) != NOT_SUPPORTED);
        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new RawResponderRangingParams.Builder()
                        .setRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_multi")
                                        .build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(
                                        sessionId, DEVICE_ROLE_RESPONDER,
                                        UwbAddress.fromBytes(new byte[]{3, 5}),
                                        UwbAddress.fromBytes(new byte[]{1, 2}))
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSessionKeyInfo(
                                                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                                        2, 1})
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .build())
                                .build())
                        .build())
                .build();

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }


    private static class CapabilitiesCallback implements RangingCapabilitiesCallback {

        private CountDownLatch mCountDownLatch;
        private boolean mOnCapabilitiesReceived = false;
        private RangingCapabilities mRangingCapabilities = null;

        CapabilitiesCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onRangingCapabilities(@NonNull RangingCapabilities capabilities) {
            mOnCapabilitiesReceived = true;
            mRangingCapabilities = capabilities;
            mCountDownLatch.countDown();
        }

        public void reset(CountDownLatch latch) {
            mCountDownLatch = latch;
            mOnCapabilitiesReceived = false;
        }
    }
}
