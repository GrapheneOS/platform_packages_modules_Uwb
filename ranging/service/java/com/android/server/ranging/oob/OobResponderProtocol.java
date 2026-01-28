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

import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_1;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_11;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_153;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_157;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_161;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_165;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_36;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_40;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_44;
import static android.ranging.wifi.pd.WifiPdConstants.CHANNEL_48;

import static com.android.server.ranging.common.ConfigurationUtils.getUpdateRateFromIntervalMs;
import static com.android.server.ranging.common.RangingUtils.bitset;
import static com.android.server.ranging.common.RangingUtils.macAddressToBytes;
import static com.android.server.ranging.common.RangingUtils.privateAddressIfUserBuild;

import android.net.MacAddress;
import android.ranging.RangingCapabilities;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.ble.rssi.BleRssiRangingCapabilities;
import android.ranging.oob.OobHandle;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.pd.WifiPdConstants;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;
import android.ranging.wifi.pd.WifiPdRangingParams;
import android.ranging.wifi.rtt.RttRangingCapabilities;
import android.ranging.wifi.rtt.RttRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.oob.packets.BleCsCapabilities;
import com.android.server.ranging.oob.packets.BleCsConfiguration;
import com.android.server.ranging.oob.packets.BleRssiCapabilities;
import com.android.server.ranging.oob.packets.BleRssiConfiguration;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.CapabilitiesRequest;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV1;
import com.android.server.ranging.oob.packets.CapabilitiesResponseV2;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.ConfigurationRequestV1;
import com.android.server.ranging.oob.packets.ConfigurationRequestV3;
import com.android.server.ranging.oob.packets.DiscoveryChannels;
import com.android.server.ranging.oob.packets.OobMessage;
import com.android.server.ranging.oob.packets.PreambleType;
import com.android.server.ranging.oob.packets.TechnologySet;
import com.android.server.ranging.oob.packets.TechnologyTransitioning;
import com.android.server.ranging.oob.packets.UwbCapabilities;
import com.android.server.ranging.oob.packets.UwbConfiguration;
import com.android.server.ranging.oob.packets.UwbDeviceRole;
import com.android.server.ranging.oob.packets.Version;
import com.android.server.ranging.oob.packets.WifiBandwidth;
import com.android.server.ranging.oob.packets.WifiNanRttCapabilities;
import com.android.server.ranging.oob.packets.WifiNanRttConfiguration;
import com.android.server.ranging.oob.packets.WifiPdAuthenticatedConfiguration;
import com.android.server.ranging.oob.packets.WifiPdCapabilities;
import com.android.server.ranging.oob.packets.WifiPdUnauthenticatedConfiguration;
import com.android.server.ranging.rtt.RttConfig;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;
import com.android.server.ranging.uwb.UwbConfig;
import com.android.server.ranging.wifipd.WifiPdConfig;
import com.android.server.ranging.wifipd.WifiPdConfigSelector;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Set;

public class OobResponderProtocol {
    private static final String TAG = OobInitiatorProtocol.class.getSimpleName();
    private static final String FAKE_BLE_ADDRESS = "00:00:00:00:00:00";

    private final RangingInjector mInjector;
    private final UwbAddress mLocalUwbAddress;
    private Version mVersion = Version.Current;

    public OobResponderProtocol(@NonNull RangingInjector injector) {
        mInjector = injector;
        mLocalUwbAddress = UwbAddress.createRandomShortAddress();
    }

    /**
     * Retrieves the system's supported ranging capabilities based on the provided request.
     *
     * @param request The {@link OobMessage} specifying the desired technologies
     *                and the requested protocol version.
     * @return A {@link CapabilitiesResponseV1} or {@link CapabilitiesResponseV2} object
     * containing the agreed-upon version, a set of all supported technologies,
     * and a list of detailed capability objects for each supported and requested
     * technology. The specific response version depends on the negotiated protocol version.
     */
    public OobMessage getCapabilitiesResponse(CapabilitiesRequest request) {
        if (request.getVersion() instanceof Version.Future) {
            mVersion = Version.Current;
        } else {
            mVersion = request.getVersion();
        }

        RangingCapabilities myCapabilities = mInjector.getCapabilitiesProvider().getCapabilities();

        ArrayList<Capabilities> capabilities = new ArrayList<>();
        TechnologySet.Builder supported = new TechnologySet.Builder();

        UwbRangingCapabilities uwb = myCapabilities.getUwbCapabilities();
        if (request.getRequestedTechnologies().getUwb() && uwb != null) {
            supported.setUwb(true);
            capabilities.add(new UwbCapabilities.Builder()
                    .setAddress(mLocalUwbAddress.getAddressBytes())
                    .setChannels(bitset(uwb.getSupportedChannels()))
                    .setPreambleIndexes(bitset(uwb.getSupportedPreambleIndexes(), i -> i - 1))
                    .setConfigIds(bitset(uwb.getSupportedConfigIds()))
                    .setMinInterval((short) uwb.getMinimumRangingInterval().toMillis())
                    .setMinSlotDuration(uwb.getSupportedSlotDurations().stream()
                            .min(Integer::compare).get().byteValue())
                    .setRoles((byte) (Byte.toUnsignedInt(UwbDeviceRole.Initiator.toByte())
                            | Byte.toUnsignedInt(UwbDeviceRole.Responder.toByte())))
                    .build());
        }

        BleCsRangingCapabilities cs = myCapabilities.getCsCapabilities();
        if (request.getRequestedTechnologies().getBleCs() && cs != null) {
            supported.setBleCs(true);
            capabilities.add(new BleCsCapabilities.Builder()
                    .setAddress(macAddressToBytes(privateAddressIfUserBuild(
                            cs.getBluetoothAddress())))
                    .setSecurityLevels((byte) bitset(cs.getSupportedSecurityLevels()))
                    .build());
        }

        RttRangingCapabilities wifiNan = myCapabilities.getRttRangingCapabilities();
        if (request.getRequestedTechnologies().getWifiNanRtt() && wifiNan != null) {
            supported.setWifiNanRtt(true);
            capabilities.add(new WifiNanRttCapabilities.Builder()
                    .setPeriodic(wifiNan.hasPeriodicRangingHardwareFeature())
                    .setBandwidth(WifiBandwidth.fromByte(
                            (byte) wifiNan.getMaxSupportedBandwidth()))
                    .setNumRxChains((byte) wifiNan.getMaxSupportedRxChain())
                    .build());
        }

        BleRssiRangingCapabilities rssi = myCapabilities.getBleRssiCapabilities();
        if (request.getRequestedTechnologies().getBleRssi() && rssi != null) {
            supported.setBleRssi(true);
            capabilities.add(new BleRssiCapabilities.Builder()
                    .setAddress(macAddressToBytes(privateAddressIfUserBuild(
                            rssi.getBluetoothAddress())))
                    .build());
        }

        WifiPdRangingCapabilities pdCapabilities = myCapabilities.getWifiPdRangingCapabilities();
        if (request.getRequestedTechnologies().getWifiPd() && pdCapabilities != null) {
            supported.setWifiPd(true);
            Set<Integer> discoveryChannels =
                    pdCapabilities.getSupportedDiscoveryChannelFrequenciesMhz();
            capabilities.add(
                    new WifiPdCapabilities.Builder()
                            .setUnauthenticatedPasnSupport(
                                    pdCapabilities.getSupportedPasnModes().contains(
                                            WifiPdRangingCapabilities.UNAUTHENTICATED_PASN_MODE))
                            .setAuthenticatedPasnSupport(
                                    pdCapabilities.getSupportedPasnModes().contains(
                                            WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE))
                            .setAddress(
                                    pdCapabilities.getProximityDetectionMacAddress().toByteArray())
                            .setFeature11mc(pdCapabilities.is80211mcSupported())
                            .setFeature11az(pdCapabilities.is80211azNtbSupported())
                            .setMinInterval11mc((short)
                                    pdCapabilities.get80211mcMinRangingInterval().toMillis())
                            .setMinInterval11az((short)
                                    pdCapabilities.get80211azNtbMinRangingInterval().toMillis())
                            .setMaxPreamble(
                                    PreambleType.fromByte((byte) pdCapabilities.getMaxPreamble()))
                            .setMaxChannelWidth(
                                    WifiBandwidth.fromByte(
                                            (byte) pdCapabilities.getMaxChannelWidth()))
                            .setChannels(
                                    new DiscoveryChannels.Builder()
                                            .setChannel1(discoveryChannels.contains(CHANNEL_1))
                                            .setChannel11(discoveryChannels.contains(CHANNEL_11))
                                            .setChannel36(discoveryChannels.contains(CHANNEL_36))
                                            .setChannel40(discoveryChannels.contains(CHANNEL_40))
                                            .setChannel44(discoveryChannels.contains(CHANNEL_44))
                                            .setChannel48(discoveryChannels.contains(CHANNEL_48))
                                            .setChannel153(discoveryChannels.contains(CHANNEL_153))
                                            .setChannel157(discoveryChannels.contains(CHANNEL_157))
                                            .setChannel161(discoveryChannels.contains(CHANNEL_161))
                                            .setChannel165(discoveryChannels.contains(CHANNEL_165))
                                            .build())
                            .build());
        }

        if (mVersion == Version.V1) {
            return new CapabilitiesResponseV1.Builder()
                    .setSupportedTechnologies(supported.build())
                    .setCapabilities(capabilities.toArray(new Capabilities[0]))
                    .build();
        } else {
            return new CapabilitiesResponseV2.Builder()
                    .setVersion(mVersion)
                    .setSupportedTechnologies(supported.build())
                    .setCapabilities(capabilities.toArray(new Capabilities[0]))
                    .setSupportedTransitioning(TechnologyTransitioning.MakeBeforeBreak)
                    .setDeviceType(mInjector.getDeviceType())
                    .build();
        }
    }

    public ImmutableSet<TechnologyConfig> getConfigurations(
            OobHandle handle, OobMessage request
    ) {
        // TODO: Only start for technologies who have the start ranging immediately
        //  bit set. Otherwise we need to wait for the start ranging message

        final Configuration[] configs;
        if (request instanceof ConfigurationRequestV1 v1) {
            configs = v1.getConfigs();
        } else if (request instanceof ConfigurationRequestV3 v3) {
            configs = v3.getConfigs();
        } else {
            Log.w(TAG, "Got unexpected " + request);
            return ImmutableSet.of();
        }

        ImmutableSet.Builder<TechnologyConfig> configsBuilder = ImmutableSet.builder();

        for (Configuration config : configs) {
            switch (config) {
                case UwbConfiguration uwb -> configsBuilder.add(new UwbConfig.Builder(
                        new UwbRangingParams.Builder(
                                uwb.getSessionId(), Byte.toUnsignedInt(uwb.getConfigId()),
                                mLocalUwbAddress, UwbAddress.fromBytes(uwb.getAddress()))
                                .setSessionKeyInfo(uwb.getSessionKey())
                                .setComplexChannel(new UwbComplexChannel.Builder()
                                        .setChannel(Byte.toUnsignedInt(uwb.getChannel()))
                                        .setPreambleIndex(
                                                Byte.toUnsignedInt(uwb.getPreambleIndex()))
                                        .build())
                                .setRangingUpdateRate(getUpdateRateFromIntervalMs(
                                        Short.toUnsignedInt(uwb.getInterval()),
                                        Byte.toUnsignedInt(uwb.getConfigId())))
                                .setSlotDuration(Byte.toUnsignedInt(uwb.getSlotDuration()))
                                .build())
                        .setPeerAddresses(ImmutableBiMap.of(
                                handle.getRangingDevice(),
                                UwbAddress.fromBytes(uwb.getAddress())))
                        .setDeviceRole(uwbDeviceRole(uwb.getDeviceRole()))
                        .build());
                case BleCsConfiguration unused -> {
                    // Skip: BLE CS does not need to be configured on responder.
                }
                case BleRssiConfiguration unused -> {
                    // Skip: BLE RSSI does not need to be configured on responder.
                }
                case WifiNanRttConfiguration wifiNan -> configsBuilder.add(new RttConfig(
                        Byte.toUnsignedInt(wifiNan.getDeviceRole().toByte()),
                        new RttRangingParams.Builder(
                                new String(wifiNan.getServiceName(), StandardCharsets.UTF_8))
                                .setPeriodicRangingHwFeatureEnabled(wifiNan.getPeriodic())
                                .build(),
                        new SessionConfig.Builder().build(),
                        handle.getRangingDevice()));
                case WifiPdUnauthenticatedConfiguration wifiPd -> configsBuilder.add(
                        new WifiPdConfig(
                                RangingPreference.DEVICE_ROLE_RESPONDER,
                                new WifiPdRangingParams.Builder(
                                        MacAddress.fromBytes(wifiPd.getPeerAddress()))
                                        .setRangingUpdateRate(WifiPdConstants.getUpdateRateFromMs(
                                                wifiPd.getRangingInterval()))
                                        .setPreambleType(wifiPd.getPreamble().toByte())
                                        .setChannelWidth(wifiPd.getChannelWidth().toByte())
                                        .setDiscoveryChannelFrequencyMhz(
                                                WifiPdConfigSelector.convertChannelToFrequency(
                                                        wifiPd.getChannel()))
                                        .build(),
                                new SessionConfig.Builder().build(),
                                handle.getRangingDevice()));
                case WifiPdAuthenticatedConfiguration wifiPd -> configsBuilder.add(
                        new WifiPdConfig(
                                RangingPreference.DEVICE_ROLE_RESPONDER,
                                new WifiPdRangingParams.Builder(
                                        MacAddress.fromBytes(wifiPd.getPeerAddress()))
                                        .setRangingUpdateRate(WifiPdConstants.getUpdateRateFromMs(
                                                wifiPd.getRangingInterval()))
                                        .setPreambleType(wifiPd.getPreamble().toByte())
                                        .setChannelWidth(wifiPd.getChannelWidth().toByte())
                                        .setDiscoveryChannelFrequencyMhz(
                                                WifiPdConfigSelector.convertChannelToFrequency(
                                                        wifiPd.getChannel()))
                                        .setDeviceIk(wifiPd.getDeviceIk())
                                        .setPassword(
                                                new String(wifiPd.getPassword(),
                                                        StandardCharsets.UTF_8))
                                        .build(),
                                new SessionConfig.Builder().build(),
                                handle.getRangingDevice()));
                default -> Log.w(TAG, "Received unhandled configuration");
            }
        }

        return configsBuilder.build();
    }

    private static @RangingPreference.DeviceRole int uwbDeviceRole(UwbDeviceRole role) {
        return switch (role) {
            case UwbDeviceRole.Initiator unused -> RangingPreference.DEVICE_ROLE_INITIATOR;
            case UwbDeviceRole.Responder unused -> RangingPreference.DEVICE_ROLE_RESPONDER;
        };
    }
}
