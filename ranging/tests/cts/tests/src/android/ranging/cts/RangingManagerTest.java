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

import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_PROXIMITY_LEVEL;
import static android.ranging.RangingCapabilities.ENABLED;
import static android.ranging.RangingCapabilities.NOT_SUPPORTED;
import static android.ranging.RangingConfig.RANGING_SESSION_OOB;
import static android.ranging.RangingConfig.RANGING_SESSION_RAW;
import static android.ranging.RangingPreference.DEVICE_ROLE_DT_TAG;
import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.SessionConfig.ANTENNA_MODE_DIRECTIONAL;
import static android.ranging.SessionConfig.ANTENNA_MODE_OMNI;
import static android.ranging.ble.cs.BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE;
import static android.ranging.ble.cs.BleCsRangingParams.LOCATION_TYPE_INDOOR;
import static android.ranging.ble.cs.BleCsRangingParams.SIGHT_TYPE_LINE_OF_SIGHT;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_FUSED;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY_PREFERRED;
import static android.ranging.oob.OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.DURATION_2_MS;
import static android.ranging.wifi.pd.WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.annotation.SuppressLint;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.test_utils.BlockingBluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextParams;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.MacAddress;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.rtt.WifiRttManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.ranging.DataNotificationConfig;
import android.ranging.DlTdoaMeasurement;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingManager.RangingCapabilitiesCallback;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.oob.TransportHandle;
import android.ranging.raw.RawDtTagRangingConfig;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.DlTdoaRangingParams;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;
import android.ranging.wifi.pd.WifiPdRangingParams;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingParams;
import android.ranging.wifi.rtt.RttStationRangingCapabilities;
import android.ranging.wifi.rtt.RttStationRangingParams;
import android.util.Log;
import android.util.Range;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get RangingManager in instant app mode")
public class RangingManagerTest {
    private static final String TAG = "RangingManagerTest";
    private final Context mContext = InstrumentationRegistry.getContext();
    private RangingManager mRangingManager;

    private final Set<Integer> mSupportedTechnologies = new HashSet<>();
    // Latest capabilities received from the RangingManager.
    private static final AtomicReference<RangingCapabilities> sRangingCapabilities = new AtomicReference<>();

    @Before
    public void setup() throws Exception {
        assumeTrue(Flags.rangingStackEnabled());
        PackageManager packageManager = mContext.getPackageManager();
        assertThat(packageManager).isNotNull();
        // Check is any ranging tech is supported.
        assumeTrue(isAnyRangingTechSupported(packageManager));
        mRangingManager = mContext.getSystemService(RangingManager.class);
        assertThat(mRangingManager).isNotNull();
        CapabilitiesCallback callback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                callback);

        assertThat(callback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnCapabilitiesReceived).isTrue();
        assertThat(callback.mRangingCapabilities).isNotNull();

        if (callback.mRangingCapabilities.getTechnologyAvailability().get(RangingManager.UWB)
                != NOT_SUPPORTED) {
            mSupportedTechnologies.add(RangingManager.UWB);
        }
        if (callback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_NAN_RTT) != NOT_SUPPORTED) {
            mSupportedTechnologies.add(RangingManager.WIFI_NAN_RTT);
        }
        if (callback.mRangingCapabilities.getTechnologyAvailability().get(RangingManager.BLE_CS)
                != NOT_SUPPORTED) {
            mSupportedTechnologies.add(RangingManager.BLE_CS);
        }
        if (callback.mRangingCapabilities.getTechnologyAvailability().get(RangingManager.BLE_RSSI)
                != NOT_SUPPORTED) {
            mSupportedTechnologies.add(RangingManager.BLE_RSSI);
        }
        if (callback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_STA_RTT) != NOT_SUPPORTED) {
            mSupportedTechnologies.add(RangingManager.WIFI_STA_RTT);
        }
        if (Flags.rangingStackUpdates26Q2() && callback.mRangingCapabilities
                .getTechnologyAvailability().get(RangingManager.WIFI_PD) != NOT_SUPPORTED) {
            mSupportedTechnologies.add(RangingManager.WIFI_PD);
        }
        assumeTrue(!mSupportedTechnologies.isEmpty());
    }

    @After
    public void teardown() throws Exception {
        // Just in case if some test failed.
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
    }

    public boolean isAnyRangingTechSupported(PackageManager packageManager) {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                || (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
                && packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT));
    }

    @SuppressLint({"CheckResult", "CheckReturnValue"})
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
                    assertThat(countDownLatch.await(6, TimeUnit.SECONDS)).isTrue();
                    if (!uwbManager.isUwbHwIdleTurnOffEnabled()) {
                        assertThat(uwbManager.isUwbEnabled()).isEqualTo(true);
                        assertThat(adapterStateCallback.state).isEqualTo(adapterState);
                    }
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

    private void enableWifi() throws InterruptedException {
        assertTrue("Wi-Fi Ranging requires Location to be Enabled",
                (mContext.getSystemService(LocationManager.class).isLocationEnabled()));
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        try {
            WifiManager wifiManager = mContext.getSystemService(WifiManager.class);
            assertNotNull("Wi-Fi Manager", wifiManager);

            // Turn on Wi-Fi
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
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

    private UwbRangingParams getUwbRangingParams(int sessionId, int deviceRole,
            byte[] peerAddress) {
        return new UwbRangingParams.Builder(sessionId,
                CONFIG_MULTICAST_DS_TWR,
                UwbAddress.createRandomShortAddress(),
                UwbAddress.fromBytes(peerAddress))
                .setComplexChannel(
                        new UwbComplexChannel.Builder().setChannel(
                                9).setPreambleIndex(11).build())
                .setSessionKeyInfo(
                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3,
                                2, 1})
                .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                .setSlotDuration(DURATION_2_MS)
                .build();
    }

    private RangingPreference getGenericUwbRangingPreference(int sessionId) {
        // Generic ranging preference, Improve this method based on future needs.
        SessionConfig.Builder sessionConfigBuilder = new SessionConfig.Builder()
                .setRangingMeasurementsLimit(100);
        if (Flags.rangingStackUpdates26Q2()
                && sRangingCapabilities.get().getUwbCapabilities() != null) {
            List<Integer> supportedAntennaModes =
                    sRangingCapabilities.get().getUwbCapabilities().getSupportedAntennaModes();
            if (supportedAntennaModes.contains(ANTENNA_MODE_DIRECTIONAL)) {
                sessionConfigBuilder.setAntennaMode(ANTENNA_MODE_DIRECTIONAL);
            } else if (supportedAntennaModes.contains(ANTENNA_MODE_OMNI)) {
                sessionConfigBuilder.setAntennaMode(ANTENNA_MODE_OMNI);
            }
        }
        return new RangingPreference.Builder(RangingPreference.DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(
                                        getUwbRangingParams(sessionId,
                                                RangingPreference.DEVICE_ROLE_INITIATOR,
                                                new byte[]{3, 4}))
                                .build())
                        .build())
                .setSessionConfig(sessionConfigBuilder.build())
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

        RangingPreference preference = getGenericUwbRangingPreference(10);

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

    private void testUwbRangingSessionInternal(RangingManager rangingManager) throws Exception {
        int sessionId = 10;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = rangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(getUwbRangingParams(
                                        sessionId, DEVICE_ROLE_INITIATOR,
                                        new byte[]{3, 4}))
                                .build())
                        .build())
                .setSessionConfig(new SessionConfig.Builder()
                        .setRangingMeasurementsLimit(1000)
                        .setAngleOfArrivalNeeded(true)
                        .setSensorFusionParams(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .setDataNotificationConfig(new DataNotificationConfig.Builder()
                                .setNotificationConfigType(NOTIFICATION_CONFIG_PROXIMITY_LEVEL)
                                .setProximityNearCm(100)
                                .setProximityFarCm(200)
                                .build())
                        .build())
                .build();

        assertEquals(preference.getRangingParams().getRangingSessionType(), RANGING_SESSION_RAW);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();

        rangingSession.close();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testUwbRangingSession() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        testUwbRangingSessionInternal(mRangingManager);
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testUwbRangingSession_withHwIdleOff() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();
        Context contextWithTag = mContext.createContext(
                new ContextParams.Builder()
                        .setAttributionTag("Custom_cts_tag")
                        .build()
        );
        RangingManager rangingManager = contextWithTag.getSystemService(RangingManager.class);
        assertThat(rangingManager).isNotNull();
        testUwbRangingSessionInternal(rangingManager);
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testUwbProvisionedStsRangingSession() throws Exception {
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
                                        sessionId, CONFIG_PROVISIONED_MULTICAST_DS_TWR,
                                        UwbAddress.createRandomShortAddress(),
                                        UwbAddress.createRandomShortAddress())
                                        .setComplexChannel(
                                                new UwbComplexChannel.Builder().setChannel(
                                                        9).setPreambleIndex(11).build())
                                        .setSubSessionId(50)
                                        .setSessionKeyInfo(new byte[]{
                                                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                                                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8
                                        })
                                        .setSubSessionKeyInfo(new byte[]{
                                                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                                                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8
                                        }).build())
                                .build())
                        .build())
                .build();

        assertEquals(preference.getRangingParams().getRangingSessionType(), RANGING_SESSION_RAW);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();

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

        RangingPreference preference = getGenericUwbRangingPreference(sessionId
        );

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnPeerAdded.await(2, TimeUnit.SECONDS)).isTrue();
        RangingDevice device = new RangingDevice.Builder().build();
        RawResponderRangingConfig peerParams = new RawResponderRangingConfig.Builder()
                .setRawRangingDevice(
                        new RawRangingDevice.Builder()
                                .setRangingDevice(device)
                                .setUwbRangingParams(getUwbRangingParams(sessionId,
                                        DEVICE_ROLE_RESPONDER, new byte[]{5, 6}))
                                .build())
                .build();

        callback.replaceOnPeerAddedLatch(new CountDownLatch(1));
        rangingSession.addDeviceToRangingSession(peerParams);
        assertThat(callback.mOnPeerAdded.await(2, TimeUnit.SECONDS)).isTrue();

        // Intentional sleep here so that there are few ranging rounds with newly added controlee
        // before removing it.
        Thread.sleep(1000);

        callback.replaceOnPeerRemovedLatch(new CountDownLatch(1));
        rangingSession.removeDeviceFromRangingSession(device);
        assertThat(callback.mOnPeerRemoved.await(4, TimeUnit.SECONDS)).isTrue();

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

        RangingPreference preference = getGenericUwbRangingPreference(10);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();
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
                                .setUwbRangingParams(getUwbRangingParams(sessionId1,
                                        DEVICE_ROLE_INITIATOR, new byte[]{3, 4}))
                                .build())
                        .build())
                .build();

        RangingPreference preference2 = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder()
                                        .setUuid(UUID.randomUUID())
                                        .build())
                                .setUwbRangingParams(getUwbRangingParams(sessionId2,
                                        DEVICE_ROLE_INITIATOR, new byte[]{3, 4}))
                                .build())
                        .build())
                .build();

        rangingSession1.start(preference1);
        rangingSession2.start(preference2);

        assertThat(callback1.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();
        assertThat(callback2.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();

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
        private CountDownLatch mOnOpenFailed = new CountDownLatch(1);
        private CountDownLatch mOnDlTdoaResultsCalled = new CountDownLatch(1);

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
            mOnOpenFailed.countDown();
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
        @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
        public void onDlTdoaResults(
                @NonNull RangingDevice peer, @NonNull DlTdoaMeasurement measurement) {
            mOnDlTdoaResultsCalled.countDown();
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
        // This test is not suitable if hw idle is enabled
        assumeTrue(!uwbManager.isUwbHwIdleTurnOffEnabled());
        uwbManager.setUwbEnabled(!uwbManager.isUwbEnabled());

        assertThat(callback.mCountDownLatch.await(4, TimeUnit.SECONDS)).isTrue();
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
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testAllCapabilities() throws InterruptedException {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        if (mSupportedTechnologies.contains(RangingManager.UWB)) {
            enableUwb();
        }
        if (mSupportedTechnologies.contains(RangingManager.BLE_RSSI)) {
            enableBluetooth();
        }
        if (mSupportedTechnologies.contains(RangingManager.WIFI_NAN_RTT)) {
            enableWifiNanRtt();
        }
        if (mSupportedTechnologies.contains(RangingManager.WIFI_STA_RTT)) {
            enableWifi();
        }

        CapabilitiesCallback callback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                callback);

        assertThat(callback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.mOnCapabilitiesReceived).isTrue();
        assertThat(callback.mRangingCapabilities).isNotNull();

        if (callback.mRangingCapabilities.getTechnologyAvailability().get(RangingManager.UWB)
                == ENABLED) {
            UwbRangingCapabilities uwbRangingCapabilities =
                    callback.mRangingCapabilities.getUwbCapabilities();
            assertThat(uwbRangingCapabilities).isNotNull();
            assertThat(uwbRangingCapabilities.getSupportedChannels()).isNotNull();
            assertThat(uwbRangingCapabilities.getMinimumRangingInterval()).isNotNull();
            assertThat(uwbRangingCapabilities.getSupportedPreambleIndexes()).isNotNull();
            assertThat(uwbRangingCapabilities.getSupportedNotificationConfigurations()).isNotNull();
            assertThat(uwbRangingCapabilities.getSupportedConfigIds()).isNotNull();
            assertThat(uwbRangingCapabilities.getSupportedSlotDurations()).isNotNull();
            assertThat(uwbRangingCapabilities.getSupportedRangingUpdateRates()).isNotNull();
            assertTrue(uwbRangingCapabilities.isDistanceMeasurementSupported());
            assertThat(uwbRangingCapabilities.getSupportedAntennaModes()).isNotNull();

            boolean unused = uwbRangingCapabilities.isAzimuthalAngleSupported();
            unused = uwbRangingCapabilities.isElevationAngleSupported();
            unused = uwbRangingCapabilities.isRangingIntervalReconfigurationSupported();
            unused = uwbRangingCapabilities.isBackgroundRangingSupported();
        }

        if (callback.mRangingCapabilities.getTechnologyAvailability().get(RangingManager.BLE_CS)
                == ENABLED) {
            BleCsRangingCapabilities csRangingCapabilities =
                    callback.mRangingCapabilities.getCsCapabilities();
            assertThat(csRangingCapabilities.getSupportedSecurityLevels()).isNotNull();
        }

        if (callback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_NAN_RTT) == ENABLED) {
            RttRangingCapabilities rttRangingCapabilities =
                    callback.mRangingCapabilities.getRttRangingCapabilities();
            boolean unused = rttRangingCapabilities.hasPeriodicRangingHardwareFeature();
        }

        if (callback.mRangingCapabilities.getTechnologyAvailability().get(
                RangingManager.WIFI_STA_RTT) == ENABLED) {
            RttStationRangingCapabilities rttStationRangingCapabilities =
                    callback.mRangingCapabilities.getRttStationRangingCapabilities();
        }
        mRangingManager.unregisterCapabilitiesCallback(callback);

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.2.5/C-1-1,C-1-2"})
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

        List<RawRangingDevice> rawRangingDevices = new ArrayList<>();
        rawRangingDevices.add(new RawRangingDevice.Builder()
                .setRangingDevice(new RangingDevice.Builder().build())
                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_initiator_1").build())
                .build());
        rawRangingDevices.add(new RawRangingDevice.Builder()
                .setRangingDevice(new RangingDevice.Builder().build())
                .setRttRangingParams(new RttRangingParams.Builder("test_rtt_initiator_2").build())
                .build());

        RangingPreference preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevices(rawRangingDevices)
                        .build())
                .build();

        RawInitiatorRangingConfig config = (RawInitiatorRangingConfig)
                preference.getRangingParams();
        RawRangingDevice rawRangingDevice = config.getRawRangingDevices().getFirst();
        assertThat(rawRangingDevice).isNotNull();
        assertThat(rawRangingDevice.getRangingDevice()).isNotNull();

        RttRangingParams params = rawRangingDevice.getRttRangingParams();
        assertThat(params).isNotNull();
        assertThat(params.getServiceName()).isNotNull();
        assertThat(params.getMatchFilter()).isNull();
        assertEquals(params.getRangingUpdateRate(), UPDATE_RATE_NORMAL);
        boolean unused = params.isPeriodicRangingHwFeatureEnabled();

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

        // Intentional sleep for NAN interface to clean up.
        Thread.sleep(1000);

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.2.5/C-1-1,C-1-2"})
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
                                        .setMatchFilter(new byte[]{})
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

        // Intentional sleep for NAN interface to clean up.
        Thread.sleep(1000);

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.2.5/C-1-1,C-1-2"})
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
                        new SessionConfig.Builder().setRangingMeasurementsLimit(6).build())
                .build();

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        // Session should close after measurement limit.
        assertThat(callback.mOnClosedCalled.await(4, TimeUnit.SECONDS)).isTrue();

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2", "7.4.2.5/C-1-1,C-1-2"})
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
                                .setUwbRangingParams(getUwbRangingParams(sessionId,
                                        DEVICE_ROLE_INITIATOR, new byte[]{3, 4}))
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
    @CddTest(requirements = {"7.4.3/C-10-1"})
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

        assertThat(preference.getRangingParams() instanceof RawInitiatorRangingConfig).isTrue();
        RawInitiatorRangingConfig config =
                (RawInitiatorRangingConfig) preference.getRangingParams();
        RawRangingDevice device = config.getRawRangingDevices().get(0);
        assertThat(device.getBleRssiRangingParams()).isNotNull();
        assertThat(device.getBleRssiRangingParams().getPeerBluetoothAddress()).isNotNull();
        assertThat(device.getBleRssiRangingParams().getRangingUpdateRate()).isEqualTo(
                UPDATE_RATE_NORMAL);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.3/C-10-1"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testBleRssiRangingSession_whenDisabled() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.BLE_RSSI));
        BluetoothAdapter adapter = BlockingBluetoothAdapter.getAdapter();
        assertThat(adapter).isNotNull();
        assertThat(BlockingBluetoothAdapter.disable(true)).isTrue();
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
                                                .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                                .build())
                                .build())
                        .build())
                .build();

        assertThat(preference.getRangingParams() instanceof RawInitiatorRangingConfig).isTrue();
        RawInitiatorRangingConfig config =
                (RawInitiatorRangingConfig) preference.getRangingParams();
        RawRangingDevice device = config.getRawRangingDevices().get(0);
        assertThat(device.getBleRssiRangingParams()).isNotNull();
        assertThat(device.getBleRssiRangingParams().getPeerBluetoothAddress()).isNotNull();
        assertThat(device.getBleRssiRangingParams().getRangingUpdateRate()).isEqualTo(
                UPDATE_RATE_NORMAL);

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isFalse();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.3/C-11-1,C-11-2"})
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
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .setLocationType(LOCATION_TYPE_INDOOR)
                                        .setSecurityLevel(CS_SECURITY_LEVEL_ONE)
                                        .setSightType(SIGHT_TYPE_LINE_OF_SIGHT)
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


        RawInitiatorRangingConfig config = (RawInitiatorRangingConfig)
                preference.getRangingParams();
        RawRangingDevice rawRangingDevice = config.getRawRangingDevices().getFirst();
        assertThat(rawRangingDevice).isNotNull();
        assertThat(rawRangingDevice.getRangingDevice()).isNotNull();

        BleCsRangingParams params = rawRangingDevice.getCsRangingParams();
        assertThat(params).isNotNull();
        assertThat(params.getPeerBluetoothAddress()).isNotNull();
        assertEquals(params.getRangingUpdateRate(), UPDATE_RATE_NORMAL);
        assertEquals(params.getLocationType(), LOCATION_TYPE_INDOOR);
        assertEquals(params.getSecurityLevel(), CS_SECURITY_LEVEL_ONE);
        assertEquals(params.getSightType(), SIGHT_TYPE_LINE_OF_SIGHT);


        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2", "7.4.3/C-11-1,C-11-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStartOobInitiatorRangingSession() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB)
                || mSupportedTechnologies.contains(RangingManager.BLE_CS));

        if (mSupportedTechnologies.contains(RangingManager.BLE_CS)) {
            enableBluetooth();
        }

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSession session = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), new RangingSessionCallback());
        assertThat(session).isNotNull();

        OobTransport oobTransport = new OobTransport();

        List<DeviceHandle> deviceHandles = List.of(
                new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(), oobTransport).build(),
                new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(), oobTransport).build(),
                new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(), oobTransport).build());

        OobInitiatorRangingConfig config = new OobInitiatorRangingConfig.Builder()
                .setFastestRangingInterval(Duration.ofMillis(100))
                .setSlowestRangingInterval(Duration.ofMillis(5000))
                .setRangingMode(RANGING_MODE_FUSED)
                .setSecurityLevel(SECURITY_LEVEL_BASIC)
                .addDeviceHandle(deviceHandles.get(0))
                .addDeviceHandles(deviceHandles.subList(1, deviceHandles.size()))
                .build();

        assertEquals(Duration.ofMillis(100), config.getFastestRangingInterval());
        assertEquals(Duration.ofMillis(5000), config.getSlowestRangingInterval());
        assertEquals(Range.create(Duration.ofMillis(100), Duration.ofMillis(5000)),
                config.getRangingIntervalRange());
        assertEquals(RANGING_MODE_FUSED, config.getRangingMode());
        assertEquals(SECURITY_LEVEL_BASIC, config.getSecurityLevel());
        assertThat(
                config.getDeviceHandles().stream().map(DeviceHandle::getRangingDevice)
        ).containsExactlyElementsIn(
                deviceHandles.stream().map(DeviceHandle::getRangingDevice).toList()
        );

        RangingPreference preference =
                new RangingPreference.Builder(DEVICE_ROLE_INITIATOR, config).build();

        assertEquals(RANGING_SESSION_OOB, preference.getRangingParams().getRangingSessionType());

        session.start(preference);
        assertThat(oobTransport.mSendDataCalled.await(2, TimeUnit.SECONDS)).isTrue();

        session.stop();
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testConfigureOobResponderRangingSession() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        OobTransport oobTransport = new OobTransport();

        DeviceHandle handle = new DeviceHandle.Builder(
                new RangingDevice.Builder().build(), oobTransport).build();

        OobResponderRangingConfig config = new OobResponderRangingConfig.Builder(handle).build();

        assertEquals(handle.getRangingDevice(), config.getDeviceHandle().getRangingDevice());

        uiAutomation.dropShellPermissionIdentity();
    }

    private void testWifiPdSessionInternal(int deviceRole) throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.WIFI_PD));
        enableWifi();
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(capabilitiesCallback.mOnCapabilitiesReceived).isTrue();
        assertThat(capabilitiesCallback.mRangingCapabilities).isNotNull();

        WifiPdRangingCapabilities capabilities =
                capabilitiesCallback.mRangingCapabilities.getWifiPdRangingCapabilities();
        assertThat(capabilities).isNotNull();
        assertThat(capabilities.getSupportedDiscoveryChannelFrequenciesMhz()).isNotNull();
        assertFalse(capabilities.getSupportedDiscoveryChannelFrequenciesMhz().isEmpty());
        if (capabilities.is80211mcSupported()) {
            assertThat(capabilities.get80211mcMinRangingInterval().toMillis()).isGreaterThan(0);
        }
        if (capabilities.is80211azNtbSupported()) {
            assertThat(capabilities.get80211azNtbMinRangingInterval().toMillis()).isGreaterThan(0);
        }

        WifiPdRangingParams wifiPdRangingParams = getWifiPdRangingParamsBuilder(capabilities)
                .build();

        RangingPreference preference;
        if (deviceRole == DEVICE_ROLE_INITIATOR) {
            List<RawRangingDevice> rawRangingDevices = new ArrayList<>();
            rawRangingDevices.add(new RawRangingDevice.Builder()
                    .setRangingDevice(new RangingDevice.Builder().build())
                    .setWifiPdRangingParams(wifiPdRangingParams)
                    .build());

            preference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                    new RawInitiatorRangingConfig.Builder()
                            .addRawRangingDevices(rawRangingDevices)
                            .build())
                    .build();
        } else {
            preference = new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                    new RawResponderRangingConfig.Builder()
                            .setRawRangingDevice(new RawRangingDevice.Builder()
                                    .setRangingDevice(new RangingDevice.Builder().build())
                                    .setWifiPdRangingParams(wifiPdRangingParams)
                                    .build())
                            .build())
                    .build();
        }

        RawRangingDevice rawRangingDevice;
        if (deviceRole == DEVICE_ROLE_INITIATOR) {
            RawInitiatorRangingConfig config = (RawInitiatorRangingConfig)
                    preference.getRangingParams();
            assertNotNull(config);
            rawRangingDevice = config.getRawRangingDevices().getFirst();
        } else {
            RawResponderRangingConfig config = (RawResponderRangingConfig)
                    preference.getRangingParams();
            assertNotNull(config);
            rawRangingDevice = config.getRawRangingDevice();
        }
        assertThat(rawRangingDevice).isNotNull();
        assertThat(rawRangingDevice.getRangingDevice()).isNotNull();

        WifiPdRangingParams params = rawRangingDevice.getWifiPdRangingParams();
        assertThat(params).isNotNull();
        assertThat(params.getPeerMacAddress()).isEqualTo(
                capabilities.getProximityDetectionMacAddress());
        assertThat(params.getDiscoveryChannelFrequencyMhz()).isEqualTo(
                capabilities.getSupportedDiscoveryChannelFrequenciesMhz().stream().findFirst()
                        .get());
        assertThat(params.getRangingUpdateRate()).isEqualTo(UPDATE_RATE_FREQUENT);
        if (capabilities.getSupportedPasnModes().contains(AUTHENTICATED_PASN_MODE)) {
            assertThat(params.getPasnMode()).isEqualTo(AUTHENTICATED_PASN_MODE);
            assertThat(params.getPassword()).isEqualTo("test_password");
            assertThat(params.getDeviceIk()).isEqualTo(new byte[]{1, 2, 3, 4, 5, 6});
        }

        assertThat(params.getPreambleType()).isEqualTo(capabilities.getMaxPreamble());
        assertThat(params.isResponder80211azNtbSupported()).isTrue();
        assertThat(params.getChannelWidth()).isEqualTo(capabilities.getMaxChannelWidth());

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(1000);
        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(1000);
        uiAutomation.dropShellPermissionIdentity();
    }

    private WifiPdRangingParams.Builder getWifiPdRangingParamsBuilder(
            WifiPdRangingCapabilities capabilities) {
        MacAddress peerMacAddress = MacAddress.fromString("00:11:22:33:AA:BB");
        final WifiPdRangingParams.Builder paramsBuilder =
                new WifiPdRangingParams.Builder(peerMacAddress)
                        .setRangingUpdateRate(UPDATE_RATE_FREQUENT);


        if (capabilities.getSupportedPasnModes().contains(AUTHENTICATED_PASN_MODE)) {
            paramsBuilder.setPasnMode(AUTHENTICATED_PASN_MODE)
                    .setPassword("test_password")
                    .setDeviceIk(new byte[]{1, 2, 3, 4, 5, 6});
        }

        if (capabilities.is80211azNtbSupported()) {
            paramsBuilder.setResponder80211azNtbSupported(true);
        }

        if (capabilities.getMaxChannelWidth() != 0) {
            paramsBuilder.setChannelWidth(capabilities.getMaxChannelWidth());
        }

        if (capabilities.getMaxPreamble() != 0) {
            paramsBuilder.setPreambleType(capabilities.getMaxPreamble());
        }

        if (!capabilities.getSupportedDiscoveryChannelFrequenciesMhz().isEmpty()) {
            paramsBuilder.setDiscoveryChannelFrequencyMhz(
                    capabilities.getSupportedDiscoveryChannelFrequenciesMhz().stream().findFirst()
                            .get());
        }
        return paramsBuilder;
    }

    @Test
    @CddTest(requirements = {" 7.4.2.10/C-1-1,C-1-2,C-1-3,C-1-4"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_updates_26_q_2")
    public void testWifiPdInitiatorSession() throws InterruptedException {
        testWifiPdSessionInternal(DEVICE_ROLE_INITIATOR);
    }

    @Test
    @CddTest(requirements = {" 7.4.2.10/C-1-1,C-1-2,C-1-3,C-1-4"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_updates_26_q_2")
    public void testWifiPdResponderSession() throws InterruptedException {
        testWifiPdSessionInternal(DEVICE_ROLE_RESPONDER);
    }

    @Test
    @CddTest(requirements = {"7.4.3/C-11-1,C-11-2", "7.4.3/C-10-1"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_updates_26_q_2")
    public void testBluetoothDeviceSetAndUsedInOobFlow() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.BLE_CS)
                || mSupportedTechnologies.contains(RangingManager.BLE_RSSI));

        enableBluetooth();

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSession session = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), new RangingSessionCallback());
        assertThat(session).isNotNull();

        OobTransport oobTransport = new OobTransport();

        BluetoothAdapter adapter = BlockingBluetoothAdapter.getAdapter();
        assertThat(adapter).isNotNull();

        BluetoothDevice remoteBluetoothDevice = adapter.getRemoteDevice("F2:A3:45:BC:78:90");
        assertThat(remoteBluetoothDevice).isNotNull();

        DeviceHandle device =
                new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(), oobTransport).setBluetoothDevice(
                                remoteBluetoothDevice)
                        .build();
        OobInitiatorRangingConfig config = new OobInitiatorRangingConfig.Builder()
                .setFastestRangingInterval(Duration.ofMillis(100))
                .setSlowestRangingInterval(Duration.ofMillis(5000))
                .setRangingMode(RANGING_MODE_HIGH_ACCURACY_PREFERRED)
                .setSecurityLevel(SECURITY_LEVEL_BASIC)
                .addDeviceHandle(device)
                .build();

        assertThat(config.getDeviceHandles().getFirst().getBluetoothDevice()).isEqualTo(
                remoteBluetoothDevice);

        RangingPreference preference =
                new RangingPreference.Builder(DEVICE_ROLE_INITIATOR, config).build();
        session.start(preference);
        assertThat(oobTransport.mSendDataCalled.await(2, TimeUnit.SECONDS)).isTrue();

        session.stop();
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.2.5/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_updates_25q4")
    public void testRttStationRanging() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.WIFI_STA_RTT));
        enableWifi();
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(capabilitiesCallback.mOnCapabilitiesReceived).isTrue();
        assertThat(capabilitiesCallback.mRangingCapabilities).isNotNull();
        assertThat(
                capabilitiesCallback.mRangingCapabilities.getTechnologyAvailability())
                .isNotNull();

        WifiRttManager mWifiRttManager = mContext.getSystemService(WifiRttManager.class);
        assertThat(mWifiRttManager).isNotNull();

        final RangingPreference preference =
                createPreferenceAndTestRttRangingParams(null /* channelWidth */);

        RangingSessionCallback callback = new RangingSessionCallback();
        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        rangingSession.start(preference);
        //assertThat(callback.mOnOpenedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(1000);
        rangingSession.stop();
        //assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(1000);

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.2.5/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_updates_25q4")
    public void testRttStationRangingParams() throws InterruptedException {
        createPreferenceAndTestRttRangingParams(0 /* channelWidth */);
    }

    private RangingPreference createPreferenceAndTestRttRangingParams(Integer channelWidth) {
        final RttStationRangingParams.Builder paramsBuilder =
                new RttStationRangingParams.Builder("AA:BB:CC:AA:BB:CC")
                        .setRangingUpdateRate(UPDATE_RATE_NORMAL);

        if (channelWidth != null) {
            paramsBuilder.setChannelWidth(channelWidth.intValue());
        }

        final RawRangingDevice rawRangingDevice = new RawRangingDevice.Builder()
                .setRangingDevice(new RangingDevice.Builder().build())
                .setRttStationRangingParams(paramsBuilder.build())
                .build();

        final RangingPreference preference = new RangingPreference.Builder(
                DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(rawRangingDevice)
                        .build())
                .build();

        final RawInitiatorRangingConfig config = (RawInitiatorRangingConfig)
                preference.getRangingParams();
        final RawRangingDevice device = config.getRawRangingDevices().get(0);
        assertNotNull(device);
        assertNotNull(rawRangingDevice);
        assertNotNull(rawRangingDevice.getRangingDevice());

        final RttStationRangingParams params = rawRangingDevice.getRttStationRangingParams();
        assertNotNull(params);
        assertNotNull(params.getBssid());
        assertEquals(params.getRangingUpdateRate(), UPDATE_RATE_NORMAL);

        // If channelWidth was provided, assert it.
        if (channelWidth != null) {
            assertEquals(params.getChannelWidth(), channelWidth.intValue());
        }

        // Compare the parameter set the same with the original one.
        assertEquals(device.getRttStationRangingParams(), params);

        return preference;
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2", "7.4.3/C-11-1,C-11-2", "7.4.3/C-10-1"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_updates_25q4")
    public void testStartOobInitiatorRangingSession_withFilterSet() throws InterruptedException {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB)
                || mSupportedTechnologies.contains(RangingManager.BLE_CS));
        assumeTrue(mSupportedTechnologies.contains(RangingManager.BLE_RSSI));

        enableBluetooth();

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSession session = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), new RangingSessionCallback());
        assertThat(session).isNotNull();

        OobTransport oobTransport = new OobTransport();

        DeviceHandle device =
                new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(), oobTransport).build();

        OobInitiatorRangingConfig config = new OobInitiatorRangingConfig.Builder()
                .setFastestRangingInterval(Duration.ofMillis(100))
                .setSlowestRangingInterval(Duration.ofMillis(5000))
                .setRangingMode(RANGING_MODE_FUSED)
                .setSecurityLevel(SECURITY_LEVEL_BASIC)
                .addDeviceHandle(device)
                .setRangingTechnologyFilter(Set.of(RangingManager.BLE_RSSI))
                .build();

        assertEquals(Set.of(RangingManager.BLE_RSSI), config.getRangingTechnologyFilter());

        RangingPreference preference =
                new RangingPreference.Builder(DEVICE_ROLE_INITIATOR, config).build();
        session.start(preference);
        assertThat(oobTransport.mSendDataCalled.await(2, TimeUnit.SECONDS)).isTrue();

        session.stop();
        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.4.3/C-11-1,C-11-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_cs_enabled")
    public void testRangingIntervalValues() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.BLE_CS)
                && mSupportedTechnologies.contains(RangingManager.UWB));
        enableBluetooth();
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference csPreference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setCsRangingParams(new
                                        BleCsRangingParams.Builder("00:11:22:33:AA:BB")
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .setLocationType(LOCATION_TYPE_INDOOR)
                                        .setSecurityLevel(CS_SECURITY_LEVEL_ONE)
                                        .setSightType(SIGHT_TYPE_LINE_OF_SIGHT)
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


        RawInitiatorRangingConfig csConfig = (RawInitiatorRangingConfig)
                csPreference.getRangingParams();
        RawRangingDevice rawRangingDeviceCs = csConfig.getRawRangingDevices().getFirst();

        assertEquals(1, rawRangingDeviceCs.getRangingIntervalValues().size());
        assertThat(rawRangingDeviceCs.getRangingIntervalValues().containsKey(
                RangingManager.BLE_CS)).isTrue();

        RangingPreference csAndUwbPreference = new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setCsRangingParams(new
                                        BleCsRangingParams.Builder("00:11:22:33:AA:BB")
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .setLocationType(LOCATION_TYPE_INDOOR)
                                        .setSecurityLevel(CS_SECURITY_LEVEL_ONE)
                                        .setSightType(SIGHT_TYPE_LINE_OF_SIGHT)
                                        .build())
                                .setUwbRangingParams(getUwbRangingParams(11, DEVICE_ROLE_INITIATOR,
                                        new byte[]{0x2, 0x2}))
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


        RawInitiatorRangingConfig csAndUwbConfig = (RawInitiatorRangingConfig)
                csAndUwbPreference.getRangingParams();
        RawRangingDevice rawRangingDeviceCsAndUwb =
                csAndUwbConfig.getRawRangingDevices().getFirst();

        assertEquals(2, rawRangingDeviceCsAndUwb.getRangingIntervalValues().size());
        assertThat(rawRangingDeviceCsAndUwb.getRangingIntervalValues().containsKey(
                RangingManager.BLE_CS)).isTrue();
        assertThat(rawRangingDeviceCsAndUwb.getRangingIntervalValues().containsKey(
                RangingManager.UWB)).isTrue();

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
            sRangingCapabilities.set(capabilities);
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

    private static class OobTransport implements TransportHandle {
        private final CountDownLatch mSendDataCalled = new CountDownLatch(1);

        @Override
        public void sendData(@NonNull byte[] data) {
            mSendDataCalled.countDown();
        }

        @Override
        public void registerReceiveCallback(
                @NonNull Executor executor, @NonNull ReceiveCallback callback
        ) {
        }

        @Override
        public void close() throws Exception {
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testDlTdoaDtTagRangingSession() throws Exception {
        checkUwbDlTDoaSupport();
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();

        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);
        assertThat(capabilitiesCallback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        RangingCapabilities rangingCapabilities = capabilitiesCallback.mRangingCapabilities;
        assertThat(rangingCapabilities).isNotNull();
        UwbRangingCapabilities uwbCapabilities = rangingCapabilities.getUwbCapabilities();
        assertThat(uwbCapabilities).isNotNull();
        assumeTrue(uwbCapabilities.isDlTdoaSupported());

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        RangingSessionCallback callback = new RangingSessionCallback();

        Executor executor = Executors.newSingleThreadExecutor();
        RangingSession rangingSession = mRangingManager.createRangingSession(executor, callback);
        assertThat(rangingSession).isNotNull();

        UwbAddress deviceAddress = UwbAddress.createRandomShortAddress();
        DlTdoaRangingParams dlTdoaParams = new DlTdoaRangingParams.Builder(1)
                .setComplexChannel(new UwbComplexChannel.Builder()
                        .setChannel(9).setPreambleIndex(10).build())
                .setDeviceAddress(deviceAddress)
                .setSessionKeyInfo(new byte[]{0x01, 0x02, 0x03, 0x04})
                .setRangingIntervalMillis(240)
                .setSlotDuration(UwbRangingParams.DURATION_2_MS)
                .setSlotsPerRangingRound(20)
                .setRangingRoundIndexes(new byte[]{0x01, 0x05})
                .build();

        RangingDevice rangingDevice = new RangingDevice.Builder().build();

        RawRangingDevice rawTagDevice = new RawRangingDevice.Builder()
                .setRangingDevice(rangingDevice)
                .setDlTdoaRangingParams(dlTdoaParams)
                .build();

        RawDtTagRangingConfig dtTagConfig = new RawDtTagRangingConfig.Builder(rawTagDevice)
                .build();
        assertThat(dtTagConfig.getDtTag()).isEqualTo(rawTagDevice);

        RangingPreference preference = new RangingPreference.Builder(
                DEVICE_ROLE_DT_TAG, dtTagConfig)
                .setSessionConfig(new SessionConfig.Builder()
                        .build())
                .build();
        assertThat(preference.getDeviceRole()).isEqualTo(DEVICE_ROLE_DT_TAG);
        assertThat(preference.getRangingParams()).isEqualTo(dtTagConfig);


        Log.i(TAG, "Starting DL-TDOA ranging session with preference: " + preference);
        rangingSession.start(preference);
        assertThat(callback.mOnOpenedCalled.await(4, TimeUnit.SECONDS)).isTrue();

        Log.i(TAG, "Mocking a DL-TDOA measurement callback.");
        executor.execute(() -> {
            RangingDevice anchor = new RangingDevice.Builder().build();
            // measurement should not be null in the real case, but since there is no public
            // builder or any constructor of it, we simply ignore the values in this test.
            callback.onDlTdoaResults(anchor, null);
        });
        Log.i(TAG, "Waiting for a DL-TDOA measurement callback.");
        assertThat(callback.mOnDlTdoaResultsCalled.await(2, TimeUnit.SECONDS)).isTrue();

        Log.i(TAG, "DL-TDOA ranging session opened. Closing.");
        rangingSession.close();
        assertThat(callback.mOnClosedCalled.await(2, TimeUnit.SECONDS)).isTrue();
        Log.i(TAG, "DL-TDOA ranging session closed.");

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testDlTdoaRangingParams_createFromFiraConfigPacket_validBleCpConfig()
            throws Exception {
        checkUwbDlTDoaSupport();
        byte[] config = {
            // BLE Specific Header
            (byte) 0x2D, (byte) 0x16, (byte) 0xF3, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x5F, (byte) 0x19,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for DEVICE_MAC_ADDRESS
            (byte) 0x06, (byte) 0x02, (byte) 0x20, (byte) 0x08,
            // Tag-Length-Value for PREAMBLE_CODE_INDEX
            (byte) 0x14, (byte) 0x01, (byte) 0x0C,
            // Tag-Length-Value for VENDOR_ID
            (byte) 0x27, (byte) 0x02, (byte) 0x08, (byte) 0x07,
            // Tag-Length-Value for STATIC_STS_IV
            (byte) 0x28, (byte) 0x06, (byte) 0xCA, (byte) 0xC8,
            (byte) 0xA6, (byte) 0xF7, (byte) 0x6F, (byte) 0x08,
            // Tag-Length-Value for SLOT_DURATION
            (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x09,
            // Tag-Length-Value for SLOTS_PER_RR
            (byte) 0x1B, (byte) 0x01, (byte) 0x0A,
            // Tag-Length-Value for RANGING_DURATION
            (byte) 0x09, (byte) 0x04, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
        };
        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        byte[] expectedDeviceAddress = new byte[] {(byte) 0x20, (byte) 0x08};
        int expectedChannel = 9;
        int expectedPreambleIndex = 12;
        byte[] expectedSessionKeyInfo = new byte[] {
            (byte) 0x08, (byte) 0x07, (byte) 0xCA, (byte) 0xC8,
            (byte) 0xA6, (byte) 0xF7, (byte) 0x6F, (byte) 0x08};
        int expectedSlotDuration = UwbRangingParams.DURATION_2_MS;
        int expectedSlotsPerRangingRound = 10;
        int expectedRangingIntervalMillis = 1000;
        int expectedSessionId = 0x01234567;
        byte[] expectedRangingRoundIndexes = new byte[] {(byte) 0x00};
        int expectedMeasurementVersion = DlTdoaRangingParams.MEASUREMENT_VERSION_1;

        assertThat(params).isNotNull();
        assertThat(params.getDeviceAddress()).isEqualTo(
                UwbAddress.fromBytes(expectedDeviceAddress));
        assertThat(params.getComplexChannel().getChannel()).isEqualTo(expectedChannel);
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(expectedPreambleIndex);
        assertThat(params.getSessionKeyInfo()).isEqualTo(expectedSessionKeyInfo);
        assertThat(params.getSlotDuration()).isEqualTo(expectedSlotDuration);
        assertThat(params.getSlotsPerRangingRound()).isEqualTo(expectedSlotsPerRangingRound);
        assertThat(params.getRangingIntervalMillis()).isEqualTo(expectedRangingIntervalMillis);
        assertThat(params.getSessionId()).isEqualTo(expectedSessionId);
        assertThat(params.getRangingRoundIndexes()).isEqualTo(expectedRangingRoundIndexes);
        assertThat(params.getMeasurementVersion()).isEqualTo(expectedMeasurementVersion);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testDlTdoaRangingParams_createFromFiraConfigPacket_validBleCsConfig()
            throws Exception {
        checkUwbDlTDoaSupport();
        byte[] config = {
            // BLE Specific Header
            (byte) 0x2D, (byte) 0x16, (byte) 0xF4, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x5F, (byte) 0x19,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for DEVICE_MAC_ADDRESS
            (byte) 0x06, (byte) 0x02, (byte) 0x20, (byte) 0x08,
            // Tag-Length-Value for PREAMBLE_CODE_INDEX
            (byte) 0x14, (byte) 0x01, (byte) 0x0C,
            // Tag-Length-Value for VENDOR_ID
            (byte) 0x27, (byte) 0x02, (byte) 0x08, (byte) 0x07,
            // Tag-Length-Value for STATIC_STS_IV
            (byte) 0x28, (byte) 0x06, (byte) 0xCA, (byte) 0xC8,
            (byte) 0xA6, (byte) 0xF7, (byte) 0x6F, (byte) 0x08,
            // Tag-Length-Value for SLOT_DURATION
            (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x09,
            // Tag-Length-Value for SLOTS_PER_RR
            (byte) 0x1B, (byte) 0x01, (byte) 0x0A,
            // Tag-Length-Value for RANGING_DURATION
            (byte) 0x09, (byte) 0x04, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
        };
        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        byte[] expectedDeviceAddress = new byte[] {(byte) 0x20, (byte) 0x08};
        int expectedChannel = 9;
        int expectedPreambleIndex = 12;
        byte[] expectedSessionKeyInfo = new byte[] {
            (byte) 0x08, (byte) 0x07, (byte) 0xCA, (byte) 0xC8,
            (byte) 0xA6, (byte) 0xF7, (byte) 0x6F, (byte) 0x08};
        int expectedSlotDuration = UwbRangingParams.DURATION_2_MS;
        int expectedSlotsPerRangingRound = 10;
        int expectedRangingIntervalMillis = 1000;
        int expectedSessionId = 0x01234567;
        byte[] expectedRangingRoundIndexes = new byte[] {(byte) 0x00};
        int expectedMeasurementVersion = DlTdoaRangingParams.MEASUREMENT_VERSION_1;

        assertThat(params).isNotNull();
        assertThat(params.getDeviceAddress()).isEqualTo(
                UwbAddress.fromBytes(expectedDeviceAddress));
        assertThat(params.getComplexChannel().getChannel()).isEqualTo(expectedChannel);
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(expectedPreambleIndex);
        assertThat(params.getSessionKeyInfo()).isEqualTo(expectedSessionKeyInfo);
        assertThat(params.getSlotDuration()).isEqualTo(expectedSlotDuration);
        assertThat(params.getSlotsPerRangingRound()).isEqualTo(expectedSlotsPerRangingRound);
        assertThat(params.getRangingIntervalMillis()).isEqualTo(expectedRangingIntervalMillis);
        assertThat(params.getSessionId()).isEqualTo(expectedSessionId);
        assertThat(params.getRangingRoundIndexes()).isEqualTo(expectedRangingRoundIndexes);
        assertThat(params.getMeasurementVersion()).isEqualTo(expectedMeasurementVersion);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testDlTdoaRangingParams_createFromFiraConfigPacket_validWifiConfig()
            throws Exception  {
        checkUwbDlTDoaSupport();
        byte[] config = {
            // WiFi Specific Header
            (byte) 0xDD, (byte) 0x2D, (byte) 0x5A, (byte) 0x18, (byte) 0xFF,
            // FiRa Specific Sub-Element Type and Length (with Extension)
            (byte) 0x5F, (byte) 0x19,
            // UWB Configuration Sub-Element Profile ID and UWB Config ID
            (byte) 0x02, (byte) 0x00,
            // Tag-Length-Value for DEVICE_MAC_ADDRESS
            (byte) 0x06, (byte) 0x02, (byte) 0x20, (byte) 0x08,
            // Tag-Length-Value for PREAMBLE_CODE_INDEX
            (byte) 0x14, (byte) 0x01, (byte) 0x0C,
            // Tag-Length-Value for VENDOR_ID
            (byte) 0x27, (byte) 0x02, (byte) 0x08, (byte) 0x07,
            // Tag-Length-Value for STATIC_STS_IV
            (byte) 0x28, (byte) 0x06, (byte) 0xCA, (byte) 0xC8,
            (byte) 0xA6, (byte) 0xF7, (byte) 0x6F, (byte) 0x08,
            // Tag-Length-Value for SLOT_DURATION
            (byte) 0x08, (byte) 0x02, (byte) 0x60, (byte) 0x09,
            // Tag-Length-Value for SLOTS_PER_RR
            (byte) 0x1B, (byte) 0x01, (byte) 0x0A,
            // Tag-Length-Value for RANGING_DURATION
            (byte) 0x09, (byte) 0x04, (byte) 0xE8, (byte) 0x03, (byte) 0x00, (byte) 0x00,
            // Tag-Length-Value for SESSION_ID
            (byte) 0x9F, (byte) 0x04, (byte) 0x67, (byte) 0x45, (byte) 0x23, (byte) 0x01,
        };
        DlTdoaRangingParams params = DlTdoaRangingParams.createFromFiraConfigPacket(config, null);

        byte[] expectedDeviceAddress = new byte[] {(byte) 0x20, (byte) 0x08};
        int expectedChannel = 9;
        int expectedPreambleIndex = 12;
        byte[] expectedSessionKeyInfo = new byte[] {
            (byte) 0x08, (byte) 0x07, (byte) 0xCA, (byte) 0xC8,
            (byte) 0xA6, (byte) 0xF7, (byte) 0x6F, (byte) 0x08};
        int expectedSlotDuration = UwbRangingParams.DURATION_2_MS;
        int expectedSlotsPerRangingRound = 10;
        int expectedRangingIntervalMillis = 1000;
        int expectedSessionId = 0x01234567;
        byte[] expectedRangingRoundIndexes = new byte[] {(byte) 0x00};
        int expectedMeasurementVersion = DlTdoaRangingParams.MEASUREMENT_VERSION_1;

        assertThat(params).isNotNull();
        assertThat(params.getDeviceAddress()).isEqualTo(
                UwbAddress.fromBytes(expectedDeviceAddress));
        assertThat(params.getComplexChannel().getChannel()).isEqualTo(expectedChannel);
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(expectedPreambleIndex);
        assertThat(params.getSessionKeyInfo()).isEqualTo(expectedSessionKeyInfo);
        assertThat(params.getSlotDuration()).isEqualTo(expectedSlotDuration);
        assertThat(params.getSlotsPerRangingRound()).isEqualTo(expectedSlotsPerRangingRound);
        assertThat(params.getRangingIntervalMillis()).isEqualTo(expectedRangingIntervalMillis);
        assertThat(params.getSessionId()).isEqualTo(expectedSessionId);
        assertThat(params.getRangingRoundIndexes()).isEqualTo(expectedRangingRoundIndexes);
        assertThat(params.getMeasurementVersion()).isEqualTo(expectedMeasurementVersion);
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testDlTdoaRangingParams_defaultValues() throws Exception {
        checkUwbDlTDoaSupport();
        DlTdoaRangingParams params = new DlTdoaRangingParams.Builder(12345678).build();
        assertThat(params.getSessionId()).isEqualTo(12345678);
        assertThat(params.getSessionKeyInfo()).isEqualTo(
                new byte[] {7, 8, 1, 2, 3, 4, 5, 6});
        assertThat(params.getComplexChannel().getChannel()).isEqualTo(9);
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(10);
        assertThat(params.getRangingIntervalMillis()).isEqualTo(200);
        assertThat(params.getSlotDuration()).isEqualTo(UwbRangingParams.DURATION_2_MS);
        assertThat(params.getSlotsPerRangingRound()).isEqualTo(25);
        assertThat(params.getRangingRoundIndexes()).isEqualTo(new byte[] {0});
        assertThat(params.getMeasurementVersion()).isEqualTo(
                DlTdoaRangingParams.MEASUREMENT_VERSION_1);
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testDlTdoaRangingParams_getters() throws Exception {
        checkUwbDlTDoaSupport();
        DlTdoaRangingParams params = new DlTdoaRangingParams.Builder(12345678)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02}))
                .setSessionKeyInfo(new byte[] {0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A})
                .setComplexChannel(new UwbComplexChannel.Builder()
                        .setChannel(5)
                        .setPreambleIndex(12)
                        .build())
                .setRangingIntervalMillis(240)
                .setSlotDuration(UwbRangingParams.DURATION_1_MS)
                .setSlotsPerRangingRound(20)
                .setRangingRoundIndexes(new byte[] {1, 3, 5, 7, 9})
                .setMeasurementVersion(DlTdoaRangingParams.MEASUREMENT_VERSION_2)
                .build();
        assertThat(params.getSessionId()).isEqualTo(12345678);
        assertThat(params.getDeviceAddress()).isEqualTo(
                UwbAddress.fromBytes(new byte[] {(byte) 0x01, (byte) 0x02}));
        assertThat(params.getSessionKeyInfo()).isEqualTo(
                new byte[] {0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A});
        assertThat(params.getComplexChannel().getChannel()).isEqualTo(5);
        assertThat(params.getComplexChannel().getPreambleIndex()).isEqualTo(12);
        assertThat(params.getRangingIntervalMillis()).isEqualTo(240);
        assertThat(params.getSlotDuration()).isEqualTo(UwbRangingParams.DURATION_1_MS);
        assertThat(params.getSlotsPerRangingRound()).isEqualTo(20);
        assertThat(params.getRangingRoundIndexes()).isEqualTo(new byte[] {1, 3, 5, 7, 9});
        assertThat(params.getMeasurementVersion()).isEqualTo(
                DlTdoaRangingParams.MEASUREMENT_VERSION_2);
    }

    public void checkUwbDlTDoaSupport() throws Exception {
        assumeTrue(mSupportedTechnologies.contains(RangingManager.UWB));
        enableUwb();

        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
        RangingCapabilities rangingCapabilities = capabilitiesCallback.mRangingCapabilities;
        assertThat(rangingCapabilities).isNotNull();
        UwbRangingCapabilities uwbCapabilities = rangingCapabilities.getUwbCapabilities();
        assertThat(uwbCapabilities).isNotNull();
        assumeTrue(uwbCapabilities.isDlTdoaSupported());
    }
}
