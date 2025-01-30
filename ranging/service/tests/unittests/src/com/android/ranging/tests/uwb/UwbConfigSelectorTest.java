/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.tests.uwb;

import static android.ranging.RangingPreference.DEVICE_ROLE_RESPONDER;
import static android.ranging.oob.OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC;
import static android.ranging.oob.OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_5;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_10;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_12;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_27;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_29;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_32;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_9;
import static android.ranging.uwb.UwbRangingParams.CONFIG_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.DURATION_1_MS;
import static android.ranging.uwb.UwbRangingParams.DURATION_2_MS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.SessionHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.util.Range;

import androidx.test.filters.SmallTest;

import com.android.ranging.uwb.backend.internal.Utils;
import com.android.server.ranging.RangingEngine.ConfigSelectionException;
import com.android.server.ranging.uwb.UwbConfig;
import com.android.server.ranging.uwb.UwbConfigSelector;
import com.android.server.ranging.uwb.UwbOobCapabilities;
import com.android.server.ranging.uwb.UwbOobConfig;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

@RunWith(JUnit4.class)
@SmallTest
public class UwbConfigSelectorTest {
    @Rule
    public final MockitoRule mMockito = MockitoJUnit.rule();

    private static final ImmutableList<@UwbRangingParams.ConfigId Integer>
            DEFAULT_SUPPORTED_CONFIG_IDS = ImmutableList.copyOf(IntStream.rangeClosed(
                    CONFIG_UNICAST_DS_TWR, CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST)
            .boxed().toList());
    private static final ImmutableList<@UwbComplexChannel.UwbChannel Integer>
            DEFAULT_SUPPORTED_CHANNELS = ImmutableList.of(UWB_CHANNEL_9, UWB_CHANNEL_5);
    private static final ImmutableList<@UwbComplexChannel.UwbPreambleCodeIndex Integer>
            DEFAULT_SUPPORTED_PREAMBLE_INDEXES = ImmutableList.copyOf(IntStream.rangeClosed(
                    UWB_PREAMBLE_CODE_INDEX_9, UWB_PREAMBLE_CODE_INDEX_32)
            .boxed().toList());
    private static final ImmutableList<@RawRangingDevice.RangingUpdateRate Integer>
            DEFAULT_SUPPORTED_UPDATE_RATES = ImmutableList.of(
            UPDATE_RATE_FREQUENT, UPDATE_RATE_NORMAL, UPDATE_RATE_INFREQUENT);
    private static final ImmutableList<@RawRangingDevice.RangingUpdateRate Integer>
            DEFAULT_SUPPORTED_SLOT_DURATIONS = ImmutableList.of(DURATION_1_MS, DURATION_2_MS);

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SessionConfig mMockSessionConfig;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private OobInitiatorRangingConfig mMockOobConfig;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private UwbRangingCapabilities mMockLocalCapabilities;


    private void mockConfiguredRangingIntervalRange(Range<Duration> range) {
        when(mMockOobConfig.getRangingIntervalRange()).thenReturn(range);
        when(mMockOobConfig.getFastestRangingInterval()).thenReturn(range.getLower());
        when(mMockOobConfig.getSlowestRangingInterval()).thenReturn(range.getUpper());
    }

    /** Create class under test */
    private UwbConfigSelector createConfigSelector() {
        return new UwbConfigSelector(
                mMockSessionConfig, mMockOobConfig, mMockLocalCapabilities,
                mock(SessionHandle.class, Answers.RETURNS_DEEP_STUBS));
    }

    private UwbOobCapabilities.Builder createPeerCapabilities() {
        return UwbOobCapabilities.builder()
                .setUwbAddress(mock(UwbAddress.class, Answers.RETURNS_DEEP_STUBS))
                .setSupportedConfigIds(DEFAULT_SUPPORTED_CONFIG_IDS)
                .setSupportedChannels(DEFAULT_SUPPORTED_CHANNELS)
                .setSupportedPreambleIndexes(DEFAULT_SUPPORTED_PREAMBLE_INDEXES)
                .setMinimumRangingIntervalMs(0)
                .setMinimumSlotDurationMs(DEFAULT_SUPPORTED_SLOT_DURATIONS
                        .stream().min(Integer::compareTo).get())
                .setSupportedDeviceRole(ImmutableList.of(UwbOobConfig.OobDeviceRole.INITIATOR));
    }

    @Before
    public void setup() {
        when(mMockSessionConfig.isAngleOfArrivalNeeded()).thenReturn(false);

        when(mMockOobConfig.getSecurityLevel()).thenReturn(SECURITY_LEVEL_BASIC);
        mockConfiguredRangingIntervalRange(Range.create(Duration.ZERO, Duration.ofDays(1)));

        when(mMockLocalCapabilities.getSupportedConfigIds())
                .thenReturn(DEFAULT_SUPPORTED_CONFIG_IDS);
        when(mMockLocalCapabilities.getSupportedChannels()).thenReturn(DEFAULT_SUPPORTED_CHANNELS);
        when(mMockLocalCapabilities.getSupportedPreambleIndexes())
                .thenReturn(DEFAULT_SUPPORTED_PREAMBLE_INDEXES);
        when(mMockLocalCapabilities.getMinimumRangingInterval()).thenReturn(Duration.ZERO);
        when(mMockLocalCapabilities.getSupportedRangingUpdateRates())
                .thenReturn(DEFAULT_SUPPORTED_UPDATE_RATES);
        when(mMockLocalCapabilities.getSupportedSlotDurations())
                .thenReturn(DEFAULT_SUPPORTED_SLOT_DURATIONS);
        when(mMockLocalCapabilities.isAzimuthalAngleSupported()).thenReturn(true);
    }

    @Test
    public void isCapableOfConfig_returnsFalseWhenSecurityLevelIncompatible() {
        when(mMockOobConfig.getSecurityLevel()).thenReturn(SECURITY_LEVEL_SECURE);

        when(mMockLocalCapabilities.getSupportedConfigIds())
                .thenReturn(List.of(CONFIG_UNICAST_DS_TWR, CONFIG_MULTICAST_DS_TWR));

        assertThat(UwbConfigSelector.isCapableOfConfig(
                mMockSessionConfig, mMockOobConfig, mMockLocalCapabilities)).isFalse();
    }

    @Test
    public void isCapableOfConfig_returnsFalseWhenRangeIncompatible() {
        Range<Duration> configuredRange = Range.create(
                Duration.ofMillis(100),
                Duration.ofMillis(200));
        mockConfiguredRangingIntervalRange(configuredRange);

        when(mMockLocalCapabilities.getMinimumRangingInterval()).thenReturn(Duration.ofMillis(300));

        assertThat(UwbConfigSelector.isCapableOfConfig(
                mMockSessionConfig, mMockOobConfig, mMockLocalCapabilities)).isFalse();
    }

    @Test(expected = ConfigSelectionException.class)
    public void restrictConfigToCapabilities_failsWhenPeerIncapableOfInitiatorRole()
            throws ConfigSelectionException {

        UwbConfigSelector configSelector = createConfigSelector();

        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedDeviceRole(
                                ImmutableList.of(UwbOobConfig.OobDeviceRole.RESPONDER))
                        .build());
    }

    @Test(expected = ConfigSelectionException.class)
    public void restrictConfigToCapabilities_failsWhenIntervalsDontOverlap()
            throws ConfigSelectionException {

        Range<Duration> configuredRange = Range.create(
                Duration.ofMillis(500), Duration.ofMillis(600));
        mockConfiguredRangingIntervalRange(configuredRange);

        when(mMockLocalCapabilities.getMinimumRangingInterval()).thenReturn(Duration.ofMillis(550));

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities().setMinimumRangingIntervalMs(700).build());
    }

    @Test
    public void shouldUseProvisionedSts_whenSecurityLevelSecure()
            throws ConfigSelectionException {

        when(mMockOobConfig.getSecurityLevel()).thenReturn(SECURITY_LEVEL_SECURE);

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedConfigIds(ImmutableList.copyOf(IntStream.rangeClosed(
                                        CONFIG_UNICAST_DS_TWR, CONFIG_PROVISIONED_MULTICAST_DS_TWR)
                                .boxed()
                                .toList()))
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();

        UwbConfig localConfig = Iterables.getOnlyElement(config.getLocalConfigs());
        assertThat(localConfig.getParameters().getConfigId())
                .isEqualTo(CONFIG_PROVISIONED_UNICAST_DS_TWR);
        assertThat(localConfig.getParameters().getSessionKeyInfo()).hasLength(16);

        UwbOobConfig peerConfig = Iterables.getOnlyElement(config.getPeerConfigs().values());
        assertThat(peerConfig.getSelectedConfigId()).isEqualTo(CONFIG_PROVISIONED_UNICAST_DS_TWR);
        assertThat(peerConfig.getSessionKeyLength()).isEqualTo(16);
        assertThat(peerConfig.getSessionKey()).hasLength(16);
    }

    @Test
    public void shouldUseVeryFastConfigId_whenSupported() throws ConfigSelectionException {
        when(mMockOobConfig.getSecurityLevel()).thenReturn(SECURITY_LEVEL_SECURE);

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedConfigIds(ImmutableList.of(
                                CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST,
                                CONFIG_PROVISIONED_UNICAST_DS_TWR))
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();

        UwbConfig localConfig = Iterables.getOnlyElement(config.getLocalConfigs());
        assertThat(localConfig.getParameters().getConfigId())
                .isEqualTo(CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST);
        assertThat(localConfig.getParameters().getRangingUpdateRate())
                .isEqualTo(UPDATE_RATE_FREQUENT);

        UwbOobConfig peerConfig = Iterables.getOnlyElement(config.getPeerConfigs().values());
        assertThat(peerConfig.getSelectedConfigId())
                .isEqualTo(CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST);
        assertThat(peerConfig.getSelectedRangingIntervalMs())
                .isEqualTo(Utils.getRangingTimingParams(CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST)
                        .getRangingInterval(UPDATE_RATE_FREQUENT));
    }

    @Test
    public void shouldConfigureOneSession_whenOnePeerAdded() throws ConfigSelectionException {
        RangingDevice peer = new RangingDevice.Builder().build();

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(peer, createPeerCapabilities().build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        assertThat(config.getLocalConfigs()).hasSize(1);
        UwbConfig localConfig = Iterables.getOnlyElement(config.getLocalConfigs());
        assertThat(localConfig.getPeerDevices()).containsExactly(peer);
        assertThat(localConfig.getParameters().getConfigId()).isEqualTo(CONFIG_UNICAST_DS_TWR);
        assertThat(localConfig.getDeviceRole()).isEqualTo(DEVICE_ROLE_RESPONDER);

        assertThat(config.getPeerConfigs().keySet()).containsExactly(peer);
        UwbOobConfig peerConfig = Iterables.getOnlyElement(config.getPeerConfigs().values());
        assertThat(peerConfig.getDeviceRole()).isEqualTo(UwbOobConfig.OobDeviceRole.INITIATOR);
        assertThat(peerConfig.getSelectedConfigId()).isEqualTo(CONFIG_UNICAST_DS_TWR);
    }

    @Test
    public void shouldConfigureMultipleSessions_whenMultiplePeersAdded()
            throws ConfigSelectionException {

        List<RangingDevice> peers = List.of(
                new RangingDevice.Builder().build(),
                new RangingDevice.Builder().build());

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(peers.get(0), createPeerCapabilities().build());
        configSelector.restrictConfigToCapabilities(peers.get(1), createPeerCapabilities().build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        assertThat(config.getPeerConfigs().keySet()).hasSize(2);
        for (UwbConfig localConfig : config.getLocalConfigs()) {
            assertThat(localConfig.getPeerDevices()).hasSize(1);
            assertThat(localConfig.getPeerDevices()).containsAnyIn(peers);
            assertThat(localConfig.getParameters().getConfigId()).isEqualTo(CONFIG_UNICAST_DS_TWR);
            assertThat(localConfig.getDeviceRole()).isEqualTo(DEVICE_ROLE_RESPONDER);
        }

        assertThat(config.getPeerConfigs().keySet()).containsExactlyElementsIn(peers);
        for (UwbOobConfig peerConfig : config.getPeerConfigs().values()) {
            assertThat(peerConfig.getDeviceRole()).isEqualTo(UwbOobConfig.OobDeviceRole.INITIATOR);
            assertThat(peerConfig.getSelectedConfigId()).isEqualTo(CONFIG_UNICAST_DS_TWR);
        }
    }

    @Test
    public void shouldPrioritizeFastestUpdateRate() throws ConfigSelectionException {
        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setMinimumRangingIntervalMs(
                                Utils.getRangingTimingParams(CONFIG_UNICAST_DS_TWR)
                                        .getRangingIntervalFast())
                        .build());
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setMinimumRangingIntervalMs(
                                Utils.getRangingTimingParams(
                                                CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST)
                                        .getRangingIntervalFast())
                        .build());
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setMinimumRangingIntervalMs(
                                Utils.getRangingTimingParams(CONFIG_UNICAST_DS_TWR)
                                        .getRangingIntervalNormal())
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        for (UwbConfig localConfig : config.getLocalConfigs()) {
            assertThat(localConfig.getParameters().getRangingUpdateRate())
                    .isEqualTo(UPDATE_RATE_NORMAL);
        }
        for (UwbOobConfig peerConfig : config.getPeerConfigs().values()) {
            assertThat(peerConfig.getSelectedRangingIntervalMs())
                    .isEqualTo(Utils.getRangingTimingParams(CONFIG_UNICAST_DS_TWR)
                            .getRangingIntervalNormal());
        }
    }

    @Test
    public void shouldPrioritizeChannel9() throws ConfigSelectionException {
        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedChannels(ImmutableList.of(UWB_CHANNEL_5, UWB_CHANNEL_9))
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        UwbConfig localConfig = Iterables.getOnlyElement(config.getLocalConfigs());
        assertThat(localConfig.getParameters().getComplexChannel().getChannel())
                .isEqualTo(UWB_CHANNEL_9);
        assertThat(Iterables.getOnlyElement(config.getPeerConfigs().values()).getSelectedChannel())
                .isEqualTo(UWB_CHANNEL_9);
    }

    @Test
    public void shouldFallbackToChannel5_whenNotAllPeersSupport9()
            throws ConfigSelectionException {

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedChannels(ImmutableList.of(UWB_CHANNEL_5, UWB_CHANNEL_9))
                        .build());
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedChannels(ImmutableList.of(UWB_CHANNEL_5))
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        for (UwbConfig localConfig : config.getLocalConfigs()) {
            assertThat(localConfig.getParameters().getComplexChannel().getChannel())
                    .isEqualTo(UWB_CHANNEL_5);
        }
        for (UwbOobConfig peerConfig : config.getPeerConfigs().values()) {
            assertThat(peerConfig.getSelectedChannel()).isEqualTo(UWB_CHANNEL_5);
        }
    }

    @Test
    public void shouldPrioritizeHprfPreambleIndex() throws ConfigSelectionException {
        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedPreambleIndexes(ImmutableList.of(
                                UWB_PREAMBLE_CODE_INDEX_29,
                                UWB_PREAMBLE_CODE_INDEX_10))
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        UwbConfig localConfig = Iterables.getOnlyElement(config.getLocalConfigs());
        assertThat(localConfig.getParameters().getComplexChannel().getPreambleIndex())
                .isEqualTo(UWB_PREAMBLE_CODE_INDEX_29);
        assertThat(Iterables.getOnlyElement(config.getPeerConfigs().values())
                .getSelectedPreambleIndex())
                .isEqualTo(UWB_PREAMBLE_CODE_INDEX_29);
    }

    @Test
    public void shouldFallbackToBprfPreambleIndex_whenNotAllPeersShareAnHprfIndex()
            throws ConfigSelectionException {
        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedPreambleIndexes(ImmutableList.of(
                                UWB_PREAMBLE_CODE_INDEX_9,
                                UWB_PREAMBLE_CODE_INDEX_12,
                                UWB_PREAMBLE_CODE_INDEX_27))
                        .build());
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(),
                createPeerCapabilities()
                        .setSupportedPreambleIndexes(ImmutableList.of(
                                UWB_PREAMBLE_CODE_INDEX_12,
                                UWB_PREAMBLE_CODE_INDEX_32))
                        .build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        for (UwbConfig localConfig : config.getLocalConfigs()) {
            assertThat(localConfig.getParameters().getComplexChannel().getPreambleIndex())
                    .isEqualTo(UWB_PREAMBLE_CODE_INDEX_12);
        }
        for (UwbOobConfig peerConfig : config.getPeerConfigs().values()) {
            assertThat(peerConfig.getSelectedPreambleIndex()).isEqualTo(UWB_PREAMBLE_CODE_INDEX_12);
        }
    }

    @Test
    public void shouldRespectLocalCapabilities_whenMoreRestrictiveThanPeers()
            throws ConfigSelectionException {
        when(mMockLocalCapabilities.getMinimumRangingInterval()).thenReturn(
                Duration.ofMillis(Utils.getRangingTimingParams(CONFIG_MULTICAST_DS_TWR)
                        .getRangingIntervalInfrequent()));
        when(mMockLocalCapabilities.getSupportedRangingUpdateRates())
                .thenReturn(List.of(UPDATE_RATE_INFREQUENT));
        when(mMockLocalCapabilities.getSupportedChannels()).thenReturn(List.of(UWB_CHANNEL_5));
        when(mMockLocalCapabilities.getSupportedPreambleIndexes())
                .thenReturn(List.of(UWB_PREAMBLE_CODE_INDEX_10));
        when(mMockLocalCapabilities.getSupportedSlotDurations()).thenReturn(List.of(DURATION_2_MS));

        UwbConfigSelector configSelector = createConfigSelector();
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(), createPeerCapabilities().build());
        configSelector.restrictConfigToCapabilities(
                new RangingDevice.Builder().build(), createPeerCapabilities().build());

        UwbConfigSelector.SelectedUwbConfig config = configSelector.selectConfig();
        for (UwbConfig localConfig : config.getLocalConfigs()) {
            assertThat(localConfig.getParameters().getRangingUpdateRate())
                    .isEqualTo(UPDATE_RATE_INFREQUENT);
            assertThat(localConfig.getParameters().getComplexChannel().getChannel())
                    .isEqualTo(UWB_CHANNEL_5);
            assertThat(localConfig.getParameters().getComplexChannel().getPreambleIndex())
                    .isEqualTo(UWB_PREAMBLE_CODE_INDEX_10);
            assertThat(localConfig.getParameters().getSlotDuration())
                    .isEqualTo(DURATION_2_MS);
        }

        for (UwbOobConfig peerConfig : config.getPeerConfigs().values()) {
            assertThat(peerConfig.getSelectedRangingIntervalMs())
                    .isEqualTo(Utils.getRangingTimingParams(CONFIG_UNICAST_DS_TWR)
                            .getRangingIntervalInfrequent());
            assertThat(peerConfig.getSelectedChannel()).isEqualTo(UWB_CHANNEL_5);
            assertThat(peerConfig.getSelectedPreambleIndex()).isEqualTo(UWB_PREAMBLE_CODE_INDEX_10);
            assertThat(peerConfig.getSelectedSlotDurationMs()).isEqualTo(DURATION_2_MS);
        }
    }
}
