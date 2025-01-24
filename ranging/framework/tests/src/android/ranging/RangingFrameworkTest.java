/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.ranging;

import static android.ranging.DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE;
import static android.ranging.RangingConfig.RANGING_SESSION_OOB;
import static android.ranging.RangingConfig.RANGING_SESSION_RAW;
import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.ble.cs.BleCsRangingParams.LOCATION_TYPE_INDOOR;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_AUTO;
import static android.ranging.oob.OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.DURATION_2_MS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.Process;
import android.os.RemoteException;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.oob.TransportHandle;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingParams;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executor;

@RunWith(JUnit4.class)
@SmallTest
public class RangingFrameworkTest {
    @Mock
    private Context mMockContext;
    private static final int UID = Process.myUid();
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private IRangingAdapter mMockRangingAdapter;

    private RangingManager mRangingManager;
    private RangingSession mRangingSession;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getAttributionSource()).thenReturn(ATTRIBUTION_SOURCE);
        mRangingManager = new RangingManager(mMockContext, mMockRangingAdapter);
        mRangingSession = mRangingManager.createRangingSession(getExecutor(),
                mock(RangingSession.Callback.class));
    }

    private RangingPreference getGenericInitiatorRangingPreference() {
        return new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder().build())
                                .setUwbRangingParams(
                                        getUwbRangingParams(
                                                new byte[]{3, 4}))
                                .setBleRssiRangingParams(new BleRssiRangingParams.Builder(
                                        "AA:BB:CC:AA:BB:CC").build())
                                .setCsRangingParams(new BleCsRangingParams.Builder(
                                        "AA:BB:CC:AA:BB:CC")
                                        .setLocationType(LOCATION_TYPE_INDOOR)
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .setSecurityLevel(1)
                                        .setSightType(1)
                                        .build())
                                .setRttRangingParams(new RttRangingParams.Builder("rtt")
                                        .setRangingUpdateRate(UPDATE_RATE_NORMAL)
                                        .setMatchFilter(new byte[]{})
                                        .setPeriodicRangingHwFeatureEnabled(false)
                                        .build())
                                .build())
                        .build())
                .setSessionConfig(new SessionConfig.Builder()
                        .setDataNotificationConfig(new DataNotificationConfig.Builder()
                                .setNotificationConfigType(NOTIFICATION_CONFIG_ENABLE)
                                .setProximityFarCm(100)
                                .setProximityNearCm(50)
                                .build())
                        .setAngleOfArrivalNeeded(true)
                        .setSensorFusionParams(new SensorFusionParams.Builder()
                                .setSensorFusionEnabled(false)
                                .build())
                        .setRangingMeasurementsLimit(100)
                        .build())
                .build();
    }

    private RangingPreference getGenericResponderRangingPreference() {
        return new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new RawResponderRangingConfig.Builder()
                        .setRawRangingDevice(new RawRangingDevice.Builder()
                                .setRangingDevice(new RangingDevice.Builder()
                                        .setUuid(UUID.randomUUID()).build())
                                .setBleRssiRangingParams(new BleRssiRangingParams.Builder(
                                        "AA:BB:CC:AA:BB:CC").build())
                                .build())
                        .build())
                .build();
    }

    private RangingPreference getGenericOobResponderRangingPreference() {
        return new RangingPreference.Builder(DEVICE_ROLE_RESPONDER,
                new OobResponderRangingConfig.Builder(new DeviceHandle.Builder(
                        new RangingDevice.Builder().build(),
                        mock(TransportHandle.class)).build())
                        .build())
                .build();
    }

    private RangingPreference getGenericOobInitiatorRangingPreference() {
        return new RangingPreference.Builder(DEVICE_ROLE_INITIATOR,
                new OobInitiatorRangingConfig.Builder()
                        .addDeviceHandle(new DeviceHandle.Builder(
                                new RangingDevice.Builder().build(),
                                mock(TransportHandle.class)).build())
                        .setRangingMode(RANGING_MODE_AUTO)
                        .setFastestRangingInterval(Duration.ofMillis(240))
                        .setSlowestRangingInterval(Duration.ofMillis(1000))
                        .setSecurityLevel(SECURITY_LEVEL_BASIC)
                        .addDeviceHandles(new ArrayList<>())
                        .build())
                .build();
    }

    private UwbRangingParams getUwbRangingParams(byte[] peerAddress) {
        return new UwbRangingParams.Builder(10,
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
                .setSubSessionId(100)
                .setSubSessionKeyInfo(new byte[]{})
                .build();
    }

    @Test
    public void testRawRangingInitiatorSession() throws RemoteException {
        RangingPreference rangingPreference = getGenericInitiatorRangingPreference();
        assertThat(rangingPreference).isNotNull();
        SessionConfig sessionConfig = rangingPreference.getSessionConfig();
        assertThat(sessionConfig).isNotNull();

        DataNotificationConfig ntfConfig = sessionConfig.getDataNotificationConfig();
        assertThat(ntfConfig).isNotNull();
        assertEquals(ntfConfig.getNotificationConfigType(), NOTIFICATION_CONFIG_ENABLE);
        assertEquals(ntfConfig.getProximityNearCm(), 50);
        assertEquals(ntfConfig.getProximityFarCm(), 100);

        SensorFusionParams sensorFusionParams = sessionConfig.getSensorFusionParams();
        assertThat(sensorFusionParams).isNotNull();
        assertFalse(sensorFusionParams.isSensorFusionEnabled());

        assertEquals(sessionConfig.getRangingMeasurementsLimit(), 100);
        assertTrue(sessionConfig.isAngleOfArrivalNeeded());

        RangingConfig rangingConfig = rangingPreference.getRangingParams();
        assertThat(rangingConfig).isNotNull();
        assertEquals(rangingConfig.getRangingSessionType(), RANGING_SESSION_RAW);
        if (!(rangingConfig instanceof RawInitiatorRangingConfig)) {
            fail();
        }
        RawInitiatorRangingConfig config = (RawInitiatorRangingConfig) rangingConfig;
        assertThat(config.getRawRangingDevices()).isNotNull();
        RawRangingDevice device = config.getRawRangingDevices().get(0);
        assertThat(device).isNotNull();
        UwbRangingParams uwbRangingParams = device.getUwbRangingParams();
        assertThat(uwbRangingParams).isNotNull();
        assertThat(uwbRangingParams.getPeerAddress()).isNotNull();
        assertThat(uwbRangingParams.getDeviceAddress()).isNotNull();
        assertThat(uwbRangingParams.getComplexChannel()).isNotNull();
        assertThat(uwbRangingParams.getSessionKeyInfo()).isNotNull();
        assertThat(uwbRangingParams.getSubSessionKeyInfo()).isNotNull();
        assertEquals(uwbRangingParams.getSessionId(), 10);
        assertEquals(uwbRangingParams.getRangingUpdateRate(), UPDATE_RATE_NORMAL);
        assertEquals(uwbRangingParams.getSlotDuration(), DURATION_2_MS);
        assertEquals(uwbRangingParams.getSubSessionId(), 100);
        assertEquals(uwbRangingParams.getConfigId(), CONFIG_MULTICAST_DS_TWR);

        UwbComplexChannel complexChannel = uwbRangingParams.getComplexChannel();
        assertThat(complexChannel).isNotNull();
        assertEquals(complexChannel.getChannel(), 9);
        assertEquals(complexChannel.getPreambleIndex(), 11);

        BleCsRangingParams bleCsRangingParams = device.getCsRangingParams();
        assertThat(bleCsRangingParams).isNotNull();
        assertThat(bleCsRangingParams.getPeerBluetoothAddress()).isNotNull();
        assertEquals(bleCsRangingParams.getSightType(), 1);
        assertEquals(bleCsRangingParams.getSecurityLevel(), 1);
        assertEquals(bleCsRangingParams.getLocationType(), LOCATION_TYPE_INDOOR);
        assertEquals(bleCsRangingParams.getRangingUpdateRate(), UPDATE_RATE_NORMAL);

        BleRssiRangingParams bleRssiRangingParams = device.getBleRssiRangingParams();
        assertThat(bleRssiRangingParams).isNotNull();
        assertThat(bleRssiRangingParams.getPeerBluetoothAddress()).isNotNull();
        assertEquals(bleRssiRangingParams.getRangingUpdateRate(), UPDATE_RATE_NORMAL);

        RttRangingParams rttRangingParams = device.getRttRangingParams();
        assertThat(rttRangingParams).isNotNull();
        assertThat(rttRangingParams.getMatchFilter()).isNotNull();
        assertThat(rttRangingParams.getServiceName()).isNotNull();
        assertFalse(rttRangingParams.isPeriodicRangingHwFeatureEnabled());
        assertEquals(rttRangingParams.getRangingUpdateRate(), UPDATE_RATE_NORMAL);

        mRangingSession.start(rangingPreference);
        verify(mMockRangingAdapter, times(1)).startRanging(any(), any(), any(), any());

        mRangingSession.reconfigureRangingInterval(10);
        verify(mMockRangingAdapter, times(1)).reconfigureRangingInterval(any(), eq(10));

        mRangingSession.removeDeviceFromRangingSession(new RangingDevice.Builder().build());
        verify(mMockRangingAdapter, times(1)).removeDevice(any(), any());

        mRangingSession.addDeviceToRangingSession(new RawResponderRangingConfig.Builder().build());
        verify(mMockRangingAdapter, times(1)).addRawDevice(any(), any());

        mRangingSession.stop();
        verify(mMockRangingAdapter, times(1)).stopRanging(any());

    }

    @Test
    public void testRawRangingResponderSession() throws RemoteException {
        RangingPreference rangingPreference = getGenericResponderRangingPreference();
        assertThat(rangingPreference).isNotNull();
        SessionConfig sessionConfig = rangingPreference.getSessionConfig();
        assertThat(sessionConfig).isNotNull();

        RangingConfig rangingConfig = rangingPreference.getRangingParams();
        assertThat(rangingConfig).isNotNull();
        assertEquals(rangingConfig.getRangingSessionType(), RANGING_SESSION_RAW);
        if (!(rangingConfig instanceof RawResponderRangingConfig)) {
            fail();
        }
        RawResponderRangingConfig config = (RawResponderRangingConfig) rangingConfig;
        assertThat(config.getRawRangingDevice()).isNotNull();
        RawRangingDevice device = config.getRawRangingDevice();
        assertThat(device).isNotNull();

        BleRssiRangingParams bleRssiRangingParams = device.getBleRssiRangingParams();
        assertThat(bleRssiRangingParams).isNotNull();
        assertThat(bleRssiRangingParams.getPeerBluetoothAddress()).isNotNull();

        mRangingSession.start(rangingPreference);
        verify(mMockRangingAdapter, times(1)).startRanging(any(), any(), any(), any());

        mRangingSession.close();
        verify(mMockRangingAdapter, times(1)).stopRanging(any());
    }

    @Test
    public void testOobRangingResponderSession() throws RemoteException {
        RangingPreference rangingPreference = getGenericOobResponderRangingPreference();
        assertThat(rangingPreference).isNotNull();
        SessionConfig sessionConfig = rangingPreference.getSessionConfig();
        assertThat(sessionConfig).isNotNull();

        RangingConfig rangingConfig = rangingPreference.getRangingParams();
        assertThat(rangingConfig).isNotNull();
        assertEquals(rangingConfig.getRangingSessionType(), RANGING_SESSION_OOB);
        if (!(rangingConfig instanceof OobResponderRangingConfig)) {
            fail();
        }
        OobResponderRangingConfig config = (OobResponderRangingConfig) rangingConfig;
        assertThat(config.getDeviceHandle()).isNotNull();

        mRangingSession.start(rangingPreference);
        verify(mMockRangingAdapter, times(1)).startRanging(any(), any(), any(), any());
    }

    @Test
    public void testOobRangingInitiatorSession() throws RemoteException {
        RangingPreference rangingPreference = getGenericOobInitiatorRangingPreference();
        assertThat(rangingPreference).isNotNull();
        SessionConfig sessionConfig = rangingPreference.getSessionConfig();
        assertThat(sessionConfig).isNotNull();

        RangingConfig rangingConfig = rangingPreference.getRangingParams();
        assertThat(rangingConfig).isNotNull();
        assertEquals(rangingConfig.getRangingSessionType(), RANGING_SESSION_OOB);
        if (!(rangingConfig instanceof OobInitiatorRangingConfig)) {
            fail();
        }
        OobInitiatorRangingConfig config = (OobInitiatorRangingConfig) rangingConfig;
        assertThat(config.getDeviceHandles()).isNotNull();
        assertEquals(config.getSecurityLevel(), SECURITY_LEVEL_BASIC);
        assertEquals(config.getRangingMode(), RANGING_MODE_AUTO);
        assertEquals(config.getFastestRangingInterval(), Duration.ofMillis(240));
        assertEquals(config.getSlowestRangingInterval(), Duration.ofMillis(1000));

        mRangingSession.start(rangingPreference);
        verify(mMockRangingAdapter, times(1)).startRanging(any(), any(), any(), any());

        mRangingSession.addDeviceToRangingSession(new OobResponderRangingConfig.Builder(
                new DeviceHandle.Builder(new RangingDevice.Builder().build(),
                        mock(TransportHandle.class))
                        .build())
                .build());

        verify(mMockRangingAdapter, times(1)).addOobDevice(any(), any());
    }

    @Test
    public void testRangingCapabilities() throws RemoteException {
        RangingManager.RangingCapabilitiesCallback callback = mock(
                RangingManager.RangingCapabilitiesCallback.class);
        mRangingManager.registerCapabilitiesCallback(getExecutor(), callback);
        verify(mMockRangingAdapter, times(1)).registerCapabilitiesCallback(any());

        mRangingManager.unregisterCapabilitiesCallback(callback);
        verify(mMockRangingAdapter, times(1)).unregisterCapabilitiesCallback(any());

        RangingCapabilities rangingCapabilities = new RangingCapabilities.Builder()
                .addCapabilities(new UwbRangingCapabilities.Builder()
                        .setSupportsDistance(true)
                        .setSupportsAzimuthalAngle(true)
                        .setSupportsElevationAngle(true)
                        .setSupportsRangingIntervalReconfigure(true)
                        .setMinRangingInterval(Duration.ofMillis(96))
                        .setSupportedChannels(new ArrayList<>())
                        .setSupportedRangingUpdateRates(new ArrayList<>())
                        .setSupportedNtfConfigs(new ArrayList<>())
                        .setSupportedPreambleIndexes(new ArrayList<>())
                        .setSupportedPreambleIndexes(new ArrayList<>())
                        .setSupportedConfigIds(new ArrayList<>())
                        .setSupportedConfigIds(new ArrayList<>())
                        .setSupportedSlotDurations(new ArrayList<>())
                        .setHasBackgroundRangingSupport(true)
                        .build())
                .addCapabilities(new BleCsRangingCapabilities.Builder()
                        .setSupportedSecurityLevels(new ArrayList<>())
                        .build())
                .addCapabilities(new RttRangingCapabilities.Builder()
                        .setPeriodicRangingHardwareFeature(true)
                        .build())
                .addAvailability(RangingManager.UWB, RangingCapabilities.ENABLED)
                .addAvailability(RangingManager.BLE_CS, RangingCapabilities.ENABLED)
                .addAvailability(RangingManager.WIFI_NAN_RTT, RangingCapabilities.ENABLED)
                .addAvailability(RangingManager.BLE_RSSI, RangingCapabilities.ENABLED)
                .build();

        assertThat(rangingCapabilities.getUwbCapabilities()).isNotNull();
        UwbRangingCapabilities uwbRangingCapabilities = rangingCapabilities.getUwbCapabilities();

        assertTrue(uwbRangingCapabilities.isDistanceMeasurementSupported());
        assertTrue(uwbRangingCapabilities.isAzimuthalAngleSupported());
        assertTrue(uwbRangingCapabilities.isElevationAngleSupported());
        assertTrue(uwbRangingCapabilities.isBackgroundRangingSupported());
        assertTrue(uwbRangingCapabilities.isRangingIntervalReconfigurationSupported());
        assertThat(uwbRangingCapabilities.getSupportedRangingUpdateRates()).isNotNull();
        assertThat(uwbRangingCapabilities.getSupportedChannels()).isNotNull();
        assertThat(uwbRangingCapabilities.getSupportedSlotDurations()).isNotNull();
        assertThat(uwbRangingCapabilities.getSupportedPreambleIndexes()).isNotNull();
        assertThat(uwbRangingCapabilities.getSupportedNotificationConfigurations()).isNotNull();
        assertThat(uwbRangingCapabilities.getSupportedConfigIds()).isNotNull();
        assertEquals(uwbRangingCapabilities.getMinimumRangingInterval(), Duration.ofMillis(96));

        assertThat(rangingCapabilities.getCsCapabilities()).isNotNull();
        BleCsRangingCapabilities bleCsRangingCapabilities = rangingCapabilities.getCsCapabilities();
        assertThat(bleCsRangingCapabilities.getSupportedSecurityLevels()).isNotNull();

        assertThat(rangingCapabilities.getRttRangingCapabilities()).isNotNull();
        RttRangingCapabilities rttRangingCapabilities =
                rangingCapabilities.getRttRangingCapabilities();
        assertTrue(rttRangingCapabilities.hasPeriodicRangingHardwareFeature());

        assertEquals(
                rangingCapabilities.getTechnologyAvailability().getOrDefault(RangingManager.UWB,
                        RangingCapabilities.NOT_SUPPORTED).intValue(), RangingCapabilities.ENABLED);
        assertEquals(
                rangingCapabilities.getTechnologyAvailability().getOrDefault(RangingManager.BLE_CS,
                        RangingCapabilities.NOT_SUPPORTED).intValue(), RangingCapabilities.ENABLED);
        assertEquals(
                rangingCapabilities.getTechnologyAvailability().getOrDefault(
                        RangingManager.WIFI_NAN_RTT,
                        RangingCapabilities.NOT_SUPPORTED).intValue(), RangingCapabilities.ENABLED);
        assertEquals(
                rangingCapabilities.getTechnologyAvailability().getOrDefault(
                        RangingManager.BLE_RSSI,
                        RangingCapabilities.NOT_SUPPORTED).intValue(), RangingCapabilities.ENABLED);

    }

    private static Executor getExecutor() {
        return Runnable::run;
    }
}
