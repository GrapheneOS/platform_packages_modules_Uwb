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

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
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
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
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
import com.android.ranging.flags.Flags;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
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

    private final Set<Integer> mSupportedTechnologies = new HashSet<>();

    @Before
    public void setup() throws Exception {
        assumeTrue(Flags.rangingStackEnabled());
        mRangingManager = mContext.getSystemService(RangingManager.class);
        assertThat(mRangingManager).isNotNull();
        PackageManager packageManager = mContext.getPackageManager();
        assertThat(packageManager).isNotNull();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)) {
            mSupportedTechnologies.add(RangingManager.UWB);
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            mSupportedTechnologies.add(RangingManager.WIFI_NAN_RTT);
        }
        if (packageManager.hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING)) {
            mSupportedTechnologies.add(RangingManager.BLE_CS);
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            mSupportedTechnologies.add(RangingManager.BLE_RSSI);
        }
    }

    @After
    public void teardown() throws Exception {
        // Just in case if some test failed.
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
    }

    private void enableUwb() {
        UwbManager uwbManager = mContext.getSystemService(UwbManager.class);
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        // Ensure UWB is toggled on.
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assert uwbManager != null;
            if (!uwbManager.isUwbEnabled()) {
                CountDownLatch countDownLatch = new CountDownLatch(1);
                int adapterState = STATE_ENABLED_INACTIVE;
                AdapterStateCallback adapterStateCallback =
                        new AdapterStateCallback(countDownLatch, adapterState);
                uwbManager.registerAdapterStateCallback(
                        Executors.newSingleThreadExecutor(), adapterStateCallback);
                try {
                    uwbManager.setUwbEnabled(true);
                    assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThat(uwbManager.isUwbEnabled()).isEqualTo(true);
                    assertThat(adapterStateCallback.state).isEqualTo(adapterState);
                } finally {
                    uwbManager.unregisterAdapterStateCallback(adapterStateCallback);
                }
            }
            if (uwbManager.isUwbHwIdleTurnOffEnabled()) {
                // If HW idle mode is turned on, vote for the UWB hardware for tests to pass.
                CountDownLatch countDownLatch = new CountDownLatch(1);
                int adapterState = STATE_ENABLED_INACTIVE;
                AdapterStateCallback adapterStateCallback =
                        new AdapterStateCallback(countDownLatch, adapterState);
                try {
                    uwbManager.registerAdapterStateCallback(
                            Executors.newSingleThreadExecutor(), adapterStateCallback);
                    uwbManager.requestUwbHwEnabled(true);
                    assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThat(adapterStateCallback.state).isEqualTo(adapterState);
                } finally {
                    uwbManager.unregisterAdapterStateCallback(adapterStateCallback);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private void enableWifiNanRtt() throws InterruptedException {
        assertTrue("Wi-Fi Aware requires Location to be Enabled",
                (mContext.getSystemService(LocationManager.class).isLocationEnabled()));
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        try {
            WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
            assertNotNull("Wi-Fi Manager", wifiManager);

            WifiAwareManager wifiAwareManager = mContext.getSystemService(WifiAwareManager.class);
            assertNotNull("Wi-Fi Aware Manager", wifiAwareManager);

            // Turn on Wi-Fi
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
            }

            ConnectivityManager connectivityManager =
                    mContext.getSystemService(ConnectivityManager.class);
            assertNotNull("Connectivity Manager", connectivityManager);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
            WifiAwareStateBroadcastReceiver receiver = new WifiAwareStateBroadcastReceiver();
            mContext.registerReceiver(receiver, intentFilter);
            if (!wifiAwareManager.isAvailable()) {
                assertTrue("Timeout waiting for Wi-Fi Aware to change status",
                        receiver.waitForStateChange());
                assertTrue("Wi-Fi Aware is not available (should be)",
                        wifiAwareManager.isAvailable());
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    public void enableBluetooth() {
        BluetoothAdapter adapter = BlockingBluetoothAdapter.getAdapter();
        assertThat(adapter).isNotNull();
        assertThat(BlockingBluetoothAdapter.enable()).isTrue();
    }

    private RangingPreference getGenericUwbRangingPreference(int deviceRole) {
        // Generic ranging preference, Improve this method based on future needs.
        return new RangingPreference.Builder(deviceRole,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(15,
                                        CONFIG_MULTICAST_DS_TWR,
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
                .setSessionConfig(new SessionConfig.Builder()
                        .setRangingMeasurementsLimit(100)
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

        RangingPreference preference = getGenericUwbRangingPreference(DEVICE_ROLE_INITIATOR);

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
    public void testUwbRangingSession() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        int sessionId = 10;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
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
                .setSessionConfig(new SessionConfig.Builder()
                        .setRangingMeasurementsLimit(1000)
                        .setAngleOfArrivalNeeded(true)
                        .setSensorFusionParams(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .build())
                .build();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testRawAddRemoverPeer() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        int sessionId = 10;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = getGenericUwbRangingPreference(DEVICE_ROLE_INITIATOR);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnPeerAdded.await(2, TimeUnit.SECONDS)).isTrue();
        RangingDevice device = new RangingDevice.Builder().build();
        RawResponderRangingConfig peerParams = new RawResponderRangingConfig.Builder()
                .setRawRangingDevice(
                        new RawRangingDevice.Builder()
                                .setRangingDevice(device)
                                .setUwbRangingParams(new UwbRangingParams.Builder(
                                        sessionId, CONFIG_UNICAST_DS_TWR,
                                        UwbAddress.fromBytes(new byte[]{1, 2}),
                                        UwbAddress.fromBytes(new byte[]{5, 6}))
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSessionKeyInfo(
                                                new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5,
                                                        4, 3, 2, 1})
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .build())
                                .build())
                .build();

        callback.replaceOnPeerAddedLatch(new CountDownLatch(1));
        rangingSession.addDeviceToRangingSession(peerParams);
        assertThat(callback.mOnPeerAdded.await(2, TimeUnit.SECONDS)).isTrue();

        callback.replaceOnPeerRemovedLatch(new CountDownLatch(1));
        rangingSession.removeDeviceFromRangingSession(device);
        assertThat(callback.mOnPeerRemoved.await(2, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(3, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testRawReconfigureRangingInterval() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = getGenericUwbRangingPreference(DEVICE_ROLE_INITIATOR);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        rangingSession.reconfigureRangingInterval(3);
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(3, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testMultipleUwbRangingSessions() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        int sessionId1 = 10;
        int sessionId2 = 15;
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
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(sessionId1,
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
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(sessionId2,
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

        assertThat(callback1.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(callback2.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    private static class RangingSessionCallback implements RangingSession.Callback {

        private final CountDownLatch mOnOpenedCalled = new CountDownLatch(1);
        private CountDownLatch mOnClosedCalled = new CountDownLatch(1);
        private CountDownLatch mOnPeerAdded = new CountDownLatch(1);
        private CountDownLatch mOnPeerRemoved = new CountDownLatch(1);

        public void replaceOnPeerAddedLatch(CountDownLatch countDownLatch) {
            mOnPeerAdded = countDownLatch;
        }

        public void replaceOnPeerRemovedLatch(CountDownLatch countDownLatch) {
            mOnPeerRemoved = countDownLatch;
        }

        public void replaceOnClosedCalled(CountDownLatch countDownLatch) {
            mOnClosedCalled = countDownLatch;
        }
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
            mOnPeerAdded.countDown();
        }

        @Override
        public void onResults(@NonNull RangingDevice peer, @NonNull RangingData data) {
        }

        @Override
        public void onStopped(@NonNull RangingDevice peer,
                @RangingManager.RangingTechnology int technology) {
            mOnPeerRemoved.countDown();
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
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
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
        assumeTrue(mSupportedTechnologies.contains(RangingManager.WIFI_NAN_RTT));
        enableWifiNanRtt();
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

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
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
        assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        // OnOpened can be successful for test_rtt_1 and not be successful yet for test_rtt_2,
        // calling stop before it was initialized will result in not getting onClosed. So, sleep for
        // 1 second here.
        Thread.sleep(1000);
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_rtt_enabled")
    public void testRttRangingResponder() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.WIFI_NAN_RTT));
        enableWifiNanRtt();
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

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new RawResponderRangingConfig.Builder()
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
        assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_rtt_enabled")
    public void testRttRangingResponder_WithMeasurementLimit() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.WIFI_NAN_RTT));
        enableWifiNanRtt();
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

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new RawResponderRangingConfig.Builder()
                        .setRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_1")
                                        .setRangingUpdateRate(UPDATE_RATE_FREQUENT)
                                        .build())
                                .build())
                        .build())
                .setSessionConfig(
                        new SessionConfig.Builder().setRangingMeasurementsLimit(2).build())
                .build();

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        // Session should close after measurement limit.
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_rtt_enabled")
    public void testMultiRangingSession() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.WIFI_NAN_RTT));
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        enableWifiNanRtt();
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

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new RawResponderRangingConfig.Builder()
                        .setRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_multi")
                                        .build())
                                .setUwbRangingParams(new UwbRangingParams.Builder(
                                        sessionId, CONFIG_UNICAST_DS_TWR,
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
        assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        // OnOpened can be successful for uwb and not be successful yet for rtt session,
        // calling stop before it was initialized will result in not getting onClosed. So, sleep for
        // 1 second here.
        Thread.sleep(1000);
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(4, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testBleRssiRangingSession() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.BLE_RSSI));
        enableBluetooth();
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setBleRssiRangingParams(
                                        new BleRssiRangingParams.Builder("00:11:22:33:AA:BB")
                                                .build())
                                .build())
                        .build())
                .setSessionConfig(new SessionConfig.Builder()
                        .setRangingMeasurementsLimit(1000)
                        .setAngleOfArrivalNeeded(true)
                        .setSensorFusionParams(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .build())
                .build();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_cs_enabled")
    public void testBleCsRangingSession() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.BLE_CS));
        enableBluetooth();
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setCsRangingParams(new
                                        BleCsRangingParams.Builder("00:11:22:33:AA:BB")
                                        .build())
                                .build())
                        .build())
                .setSessionConfig(new SessionConfig.Builder()
                        .setRangingMeasurementsLimit(1000)
                        .setAngleOfArrivalNeeded(true)
                        .setSensorFusionParams(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .build())
                .build();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

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

    private static class AdapterStateCallback implements UwbManager.AdapterStateCallback {
        private final CountDownLatch mCountDownLatch;
        private final Integer mWaitForState;
        public int state;
        public int reason;

        AdapterStateCallback(CountDownLatch countDownLatch, Integer waitForState) {
            mCountDownLatch = countDownLatch;
            mWaitForState = waitForState;
        }

        public void onStateChanged(int state, int reason) {
            this.state = state;
            this.reason = reason;
            if (mWaitForState != null) {
                if (mWaitForState == state) {
                    mCountDownLatch.countDown();
                }
            } else {
                mCountDownLatch.countDown();
            }
        }
    }

    private static class WifiAwareStateBroadcastReceiver extends BroadcastReceiver {
        private final Object mLock = new Object();
        private CountDownLatch mBlocker = new CountDownLatch(1);
        private int mCountNumber = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED.equals(intent.getAction())) {
                synchronized (mLock) {
                    mCountNumber += 1;
                    mBlocker.countDown();
                    mBlocker = new CountDownLatch(1);
                }
            }
        }

        boolean waitForStateChange() throws InterruptedException {
            CountDownLatch blocker;
            synchronized (mLock) {
                mCountNumber--;
                if (mCountNumber >= 0) {
                    return true;
                }
                blocker = mBlocker;
            }
            return blocker.await(10, TimeUnit.SECONDS);
        }
    }

}
