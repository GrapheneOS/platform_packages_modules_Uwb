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

package com.android.server.ranging.wifipd;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.net.MacAddress;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.wifi.pd.WifiPdConstants;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;
import android.util.Log;
import android.util.Range;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.oob.packets.DiscoveryChannels;
import com.android.server.ranging.oob.packets.PasnMode;
import com.android.server.ranging.oob.packets.PreambleType;
import com.android.server.ranging.oob.packets.WifiBandwidth;
import com.android.server.ranging.oob.packets.WifiPdCapabilities;
import com.android.server.ranging.oob.packets.WifiPdConfiguration;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelectionException;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Unit tests for {@link WifiPdConfigSelector}.
 */
@SmallTest
@RunWith(JUnit4.class)
public class WifiPdConfigSelectorTest {
    private static final int PREAMBLE_HE = 3;
    private static final MacAddress MAC_ADDRESS = MacAddress.fromString("00:01:02:03:04:05");
    private static final DeviceType DEVICETYPE_PHONE = DeviceType.Phone;

    @Mock
    private OobInitiatorRangingConfig mOobInitiatorRangingConfig;
    @Mock
    private WifiPdRangingCapabilities mWifiPdRangingCapabilities;

    private WifiPdConfigSelector mWifiPdConfigSelector;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void isCapableOfConfig_withNullCapabilities_returnsFalse() {
        assertThat(WifiPdConfigSelector.isCapableOfConfig(mOobInitiatorRangingConfig, null))
                .isFalse();
    }

    @Test
    public void isCapableOfConfig_unsupportedRangingInterval_returnsFalse() {
        when(mOobInitiatorRangingConfig.getRangingIntervalRange()).thenReturn(
                new Range<>(Duration.ofMillis(100), Duration.ofMillis(100)));
        when(mWifiPdRangingCapabilities.get80211azNtbMinRangingInterval()).thenReturn(
                Duration.ofMillis(200));
        when(mWifiPdRangingCapabilities.get80211mcMinRangingInterval()).thenReturn(
                Duration.ofMillis(200));
        assertThat(WifiPdConfigSelector.isCapableOfConfig(mOobInitiatorRangingConfig,
                mWifiPdRangingCapabilities)).isFalse();
    }

    @Test(expected = ConfigSelectionException.class)
    public void selectBestPossibleRangingParams_noCommonChannel_throwsException() throws Exception {
        WifiPdRangingCapabilities localCapabilities = new WifiPdRangingCapabilities.Builder()
                .setSupportedDiscoveryChannelFrequenciesMhz(Collections.singleton(2412))
                .setProximityDetectionMacAddress(MAC_ADDRESS)
                .build();
        when(mOobInitiatorRangingConfig.getRangingIntervalRange()).thenReturn(
                new Range<>(Duration.ofMillis(200), Duration.ofMillis(1000)));

        mWifiPdConfigSelector = new WifiPdConfigSelector(new SessionConfig.Builder().build(),
                mOobInitiatorRangingConfig, localCapabilities);

        Capabilities peerCapabilities = new WifiPdCapabilities.Builder()
                .setChannels(new DiscoveryChannels.Builder().setChannel11(true).build())
                .setMaxPreamble(PreambleType.fromByte((byte) PREAMBLE_HE))
                .setMaxChannelWidth(WifiBandwidth.Mhz80)
                .build();
        mWifiPdConfigSelector.addPeerCapabilities(new RangingDevice.Builder().build(),
                peerCapabilities, DEVICETYPE_PHONE);
        mWifiPdConfigSelector.selectLocalConfigs(
                Collections.singleton(new RangingDevice.Builder().build()));
    }

    @Test(expected = ConfigSelectionException.class)
    public void selectBestPossibleRangingParams_noCommonUpdateRate_throwsException()
            throws Exception {
        WifiPdRangingCapabilities localCapabilities = new WifiPdRangingCapabilities.Builder()
                .setSupportedDiscoveryChannelFrequenciesMhz(Collections.singleton(2412))
                .setProximityDetectionMacAddress(MAC_ADDRESS)
                .build();
        when(mOobInitiatorRangingConfig.getRangingIntervalRange()).thenReturn(
                new Range<>(Duration.ofMillis(100), Duration.ofMillis(100)));

        mWifiPdConfigSelector = new WifiPdConfigSelector(new SessionConfig.Builder().build(),
                mOobInitiatorRangingConfig, localCapabilities);

        Capabilities peerCapabilities = new WifiPdCapabilities.Builder()
                .setChannels(new DiscoveryChannels.Builder().setChannel1(true).build())
                .setMaxPreamble(PreambleType.fromByte((byte) PREAMBLE_HE))
                .setMaxChannelWidth(WifiBandwidth.Mhz80)
                .setMinInterval11mc((short) 200)
                .build();
        mWifiPdConfigSelector.addPeerCapabilities(new RangingDevice.Builder().build(),
                peerCapabilities, DEVICETYPE_PHONE);
        mWifiPdConfigSelector.selectLocalConfigs(
                Collections.singleton(new RangingDevice.Builder().build()));
    }

    @Test
    public void selectBestPossibleRangingParams_returnBestPossible() throws Exception {
        WifiPdRangingCapabilities localCapabilities = new WifiPdRangingCapabilities.Builder()
                .setSupportedDiscoveryChannelFrequenciesMhz(ImmutableSet.of(2412, 5180))
                .set80211azNtbSupported(true)
                .setProximityDetectionMacAddress(MAC_ADDRESS)
                .set80211azNtbMinRangingIntervalMillis(100)
                .setSupportedPasnModes(
                        ImmutableSet.of(WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE))
                .build();
        when(mOobInitiatorRangingConfig.getRangingIntervalRange()).thenReturn(
                new Range<>(Duration.ofMillis(100), Duration.ofMillis(300)));

        mWifiPdConfigSelector = new WifiPdConfigSelector(new SessionConfig.Builder().build(),
                mOobInitiatorRangingConfig, localCapabilities);

        Capabilities peerCapabilities = new WifiPdCapabilities.Builder()
                .setChannels(new DiscoveryChannels.Builder().setChannel1(true).setChannel36(true)
                        .build())
                .setMaxPreamble(PreambleType.fromByte((byte) PREAMBLE_HE))
                .setMaxChannelWidth(WifiBandwidth.Mhz80)
                .setFeature11az(true)
                .setAuthenticatedPasnSupport(true)
                .build();
        RangingDevice peer = new RangingDevice.Builder().build();
        mWifiPdConfigSelector.addPeerCapabilities(peer, peerCapabilities, DEVICETYPE_PHONE);

        Set<TechnologyConfig> localConfigs =
                mWifiPdConfigSelector.selectLocalConfigs(Collections.singleton(peer));
        WifiPdConfig localConfig =
                (WifiPdConfig) Iterables.getOnlyElement(localConfigs);
        assertThat(localConfig.getPdRangingParams().getDiscoveryChannelFrequencyMhz()).isEqualTo(
                5180);
        assertThat(localConfig.getPdRangingParams().isResponder80211azNtbSupported()).isTrue();
        assertThat(localConfig.getPdRangingParams().getPasnMode())
                .isEqualTo(WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE);

        Configuration remoteConfig = mWifiPdConfigSelector.selectRemoteConfig(peer);
        assertThat(remoteConfig).isInstanceOf(WifiPdConfiguration.class);
        assertThat(((WifiPdConfiguration) remoteConfig).getPasnMode())
                .isEqualTo(PasnMode.AuthenticatedMode);
        assertThat(((WifiPdConfiguration) remoteConfig).getFeature())
                .isEqualTo((byte) WifiPdConstants.IEEE_802_11AZ);
        assertThat(((WifiPdConfiguration) remoteConfig).getChannel()).isEqualTo((byte) 36);
    }

    @Test
    public void wifiPdAuthenticatedConfiguration_serializationTest() throws Exception {
        byte[] deviceIk = new byte[]{16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

        WifiPdConfiguration config = new WifiPdConfiguration.Builder()
                .setPasnMode(PasnMode.AuthenticatedMode)
                .setFeature((byte) WifiPdConstants.IEEE_802_11AZ)
                .setPeerAddress(MAC_ADDRESS.toByteArray())
                .setRangingInterval((short) 200)
                .setPreamble(PreambleType.fromByte((byte) PREAMBLE_HE))
                .setChannelWidth(com.android.server.ranging.oob.packets.WifiBandwidth.Mhz80)
                .setChannel((byte) 36)
                .setPassword("testpassword12345678".getBytes(StandardCharsets.UTF_8))
                .setDeviceIk(deviceIk)
                .build();

        byte[] bytes = config.toBytes();
        Log.e("Authenticated config", " " + config.toString());
        WifiPdConfiguration parsedConfig = WifiPdConfiguration.fromBytes(
                bytes);

        assertThat(parsedConfig).isNotNull();
        assertThat(parsedConfig.getPasnMode()).isEqualTo(config.getPasnMode());
        assertThat(parsedConfig.getFeature()).isEqualTo(config.getFeature());
        assertThat(parsedConfig.getPeerAddress()).isEqualTo(config.getPeerAddress());
        assertThat(parsedConfig.getRangingInterval()).isEqualTo(config.getRangingInterval());
        assertThat(parsedConfig.getPreamble()).isEqualTo(config.getPreamble());
        assertThat(parsedConfig.getChannelWidth()).isEqualTo(config.getChannelWidth());
        assertThat(parsedConfig.getChannel()).isEqualTo(config.getChannel());
        assertThat(parsedConfig.getPassword()).isEqualTo(config.getPassword());
        assertThat(parsedConfig.getDeviceIk()).isEqualTo(config.getDeviceIk());
    }

    @Test
    public void wifiPdUnauthenticatedConfiguration_serializationTest() throws Exception {
        WifiPdConfiguration config = new WifiPdConfiguration.Builder()
                .setPasnMode(PasnMode.UnauthenticatedMode)
                .setFeature((byte) WifiPdConstants.IEEE_802_11MC)
                .setPeerAddress(MAC_ADDRESS.toByteArray())
                .setRangingInterval((short) 500)
                .setPreamble(PreambleType.fromByte((byte) PREAMBLE_HE))
                .setChannelWidth(com.android.server.ranging.oob.packets.WifiBandwidth.Mhz40)
                .setChannel((byte) 11)
                .setDeviceIk(new byte[0])
                .setPassword(new byte[0])
                .build();

        byte[] bytes = config.toBytes();
        Log.e("Unauthenticated config: ", " " + config.toString());
        WifiPdConfiguration parsedConfig =
                WifiPdConfiguration.fromBytes(bytes);

        assertThat(parsedConfig).isNotNull();
        assertThat(parsedConfig.getPasnMode()).isEqualTo(config.getPasnMode());
        assertThat(parsedConfig.getFeature()).isEqualTo(config.getFeature());
        assertThat(parsedConfig.getPeerAddress()).isEqualTo(config.getPeerAddress());
        assertThat(parsedConfig.getRangingInterval()).isEqualTo(config.getRangingInterval());
        assertThat(parsedConfig.getPreamble()).isEqualTo(config.getPreamble());
        assertThat(parsedConfig.getChannelWidth()).isEqualTo(config.getChannelWidth());
        assertThat(parsedConfig.getChannel()).isEqualTo(config.getChannel());
    }
}
