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

package com.android.server.ranging.oob;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import android.ranging.RangingCapabilities;
import android.ranging.RangingManager;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.ranging.wifi.rtt.RttStationRangingCapabilities;

import com.android.server.ranging.CapabilitiesProvider;
import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV1;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV2;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.TechnologyTransitioning;
import com.android.server.ranging.oob.packets.UwbCapabilities;
import com.android.server.ranging.oob.packets.Version;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@RunWith(JUnit4.class)
public class OobResponderProtocolTest {

    @Mock
    private RangingInjector mInjector;
    @Mock
    private CapabilitiesProvider mCapabilitiesProvider;
    private OobResponderProtocol mProtocol;
    private RangingCapabilities mAllCapabilities;

    private static final Version VERSION_1 = Version.V1;
    private static final Version VERSION_2 = Version.Current;

    private static final String FAKE_BLE_ADDRESS = "01:23:45:67:89:AB";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mInjector.getCapabilitiesProvider()).thenReturn(mCapabilitiesProvider);

        // Setup a full capabilities object for general tests
        mAllCapabilities = createMockRangingCapabilities();
        when(mCapabilitiesProvider.getCapabilities()).thenReturn(mAllCapabilities);

        mProtocol = new OobResponderProtocol(mInjector);
    }

    private RangingCapabilities createMockRangingCapabilities() {
        final UwbRangingCapabilities uwbCap = new UwbRangingCapabilities.Builder()
                .setSupportedChannels(ImmutableList.of(5, 6))
                .setSupportedPreambleIndexes(ImmutableList.of(10, 11))
                .setSupportedConfigIds(ImmutableList.of(9, 10))
                .setMinRangingInterval(Duration.ofMillis(50))
                .setSupportedSlotDurations(ImmutableList.of(8, 12, 16)).build();


        final BleCsRangingCapabilities csCap = new BleCsRangingCapabilities.Builder()
                .setBluetoothAddress(FAKE_BLE_ADDRESS)
                .setSupportedSecurityLevels(List.of(1, 2)).build();

        final RttRangingCapabilities wifiNanCap = new RttRangingCapabilities.Builder()
                .setPeriodicRangingHardwareFeature(true)
                .setMaxSupportedBandwidth(3) // Corresponds to WifiBandwidth.BW_80
                .setMaxSupportedRxChain(4).build();

        final BleRssiRangingCapabilities rssiCap = new BleRssiRangingCapabilities(FAKE_BLE_ADDRESS);

        final RttStationRangingCapabilities wifiStaCap = new RttStationRangingCapabilities.Builder()
                .setSupportedSecurity(2).build();

        return new RangingCapabilities.Builder()
              .addCapabilities(uwbCap)
              .addCapabilities(csCap)
              .addCapabilities(wifiNanCap)
              .addCapabilities(rssiCap)
              .addCapabilities(wifiStaCap)
              .addAvailability(RangingManager.UWB, RangingCapabilities.ENABLED)
              .addAvailability(RangingManager.BLE_CS, RangingCapabilities.ENABLED)
              .addAvailability(RangingManager.WIFI_NAN_RTT, RangingCapabilities.ENABLED)
              .addAvailability(RangingManager.BLE_RSSI, RangingCapabilities.ENABLED)
              .addAvailability(RangingManager.WIFI_STA_RTT , RangingCapabilities.ENABLED)
              .build();
    }

    @Test
    public void getCapabilitiesResponse_v1Request_returnsV1Response() {
        final TechnologySet requested = new TechnologySet.Builder()
                .setUwb(true)
                .setBleCs(true)
                .setWifiNanRtt(true)
                .setBleRssi(true)
                .build();
        final CapabilitiesRequest request = new CapabilitiesRequest.Builder()
                .setVersion(VERSION_1)
                .setRequestedTechnologies(requested).build();

        final OobMessage response = mProtocol.getCapabilitiesResponse(request);

        assertThat(response).isInstanceOf(CapabilitiesResponseV1.class);
        final CapabilitiesResponseV1 responseV1 = (CapabilitiesResponseV1) response;

        // Verify version
        assertEquals(responseV1.getVersion(), VERSION_1);

        // Verify supported technologies match requested
        assertThat(responseV1.getSupportedTechnologies().getUwb()).isTrue();
        assertThat(responseV1.getSupportedTechnologies().getBleCs()).isTrue();
        assertThat(responseV1.getSupportedTechnologies().getWifiNanRtt()).isTrue();
        assertThat(responseV1.getSupportedTechnologies().getBleRssi()).isTrue();
        assertThat(responseV1.getSupportedTechnologies().getWifiApRtt()).isFalse();

        // Verify number of capability objects
        assertEquals(responseV1.getCapabilities().length, 4);

        // Verify UWB Capabilities
        final Capabilities uwbCap = Arrays.stream(responseV1.getCapabilities())
                .filter(c -> c instanceof UwbCapabilities).findFirst().get();
        // Additional detailed checks for UWB
        assertEquals(((UwbCapabilities) uwbCap).getMinInterval(), 50);
        assertEquals(((UwbCapabilities) uwbCap).getMinSlotDuration(), 8);
    }

    @Test
    public void getCapabilitiesResponse_v2Request_returnsV2Response() {
        final TechnologySet requested = new TechnologySet.Builder()
                .setUwb(true)
                .setBleCs(true)
                .setWifiNanRtt(true)
                .setBleRssi(true)
                .setWifiApRtt(true)
                .build();
        final CapabilitiesRequest request = new CapabilitiesRequest.Builder()
                .setVersion(VERSION_2)
                .setRequestedTechnologies(requested)
                .build();

        final OobMessage response = mProtocol.getCapabilitiesResponse(request);

        assertThat(response).isInstanceOf(CapabilitiesResponseV2.class);
        final CapabilitiesResponseV2 responseV2 = (CapabilitiesResponseV2) response;

        // Verify version
        assertEquals(responseV2.getVersion(), VERSION_2);

        // Verify supported technologies match requested
        assertThat(responseV2.getSupportedTechnologies().getUwb()).isTrue();
        assertThat(responseV2.getSupportedTechnologies().getBleCs()).isTrue();
        assertThat(responseV2.getSupportedTechnologies().getBleRssi()).isTrue();
        assertThat(responseV2.getSupportedTechnologies().getWifiNanRtt()).isTrue();
        assertThat(responseV2.getSupportedTechnologies().getWifiApRtt()).isFalse();

        // Verify TechnologyTransitioning is set to MakeBeforeBreak (1)
        assertEquals(
                responseV2.getSupportedTransitioning(),
                TechnologyTransitioning.MakeBeforeBreak);
    }

    @Test
    public void getCapabilitiesResponse_onlyUwbRequestedAndSupported_returnsUwbOnly() {
        // Set capabilities to only support UWB
        final RangingCapabilities uwbOnlyCapabilities = new RangingCapabilities.Builder()
                .addCapabilities(mAllCapabilities.getUwbCapabilities())
                .build();
        when(mCapabilitiesProvider.getCapabilities()).thenReturn(uwbOnlyCapabilities);

        final TechnologySet requested = new TechnologySet.Builder()
                .setUwb(true)
                .setBleCs(true)
                .build();
        final CapabilitiesRequest request = new CapabilitiesRequest.Builder()
                .setVersion(VERSION_1)
                .setRequestedTechnologies(requested)
                .build();

        final OobMessage response = mProtocol.getCapabilitiesResponse(request);
        final CapabilitiesResponseV1 responseV1 = (CapabilitiesResponseV1) response;

        // Verify technology set
        assertThat(responseV1.getSupportedTechnologies().getUwb()).isTrue();
        assertThat(responseV1.getSupportedTechnologies().getBleCs()).isFalse();

        // Verify only one capability object
        assertEquals(responseV1.getCapabilities().length, 1);
        assertThat(responseV1.getCapabilities()[0]).isInstanceOf(UwbCapabilities.class);
    }
}
