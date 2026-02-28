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
import android.util.Range;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.DeviceType;
import com.android.server.ranging.oob.packets.DiscoveryChannels;
import com.android.server.ranging.oob.packets.PreambleType;
import com.android.server.ranging.oob.packets.WifiBandwidth;
import com.android.server.ranging.oob.packets.WifiPdAuthenticatedConfiguration;
import com.android.server.ranging.oob.packets.WifiPdCapabilities;
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
        assertThat(remoteConfig).isInstanceOf(WifiPdAuthenticatedConfiguration.class);
        assertThat(((WifiPdAuthenticatedConfiguration) remoteConfig).getFeature())
                .isEqualTo(WifiPdConstants.IEEE_802_11AZ);
        assertThat(((WifiPdAuthenticatedConfiguration) remoteConfig).getChannel()).isEqualTo(36);
    }
}
