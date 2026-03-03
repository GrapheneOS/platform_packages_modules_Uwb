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

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
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
import static android.ranging.wifi.pd.WifiPdConstants.IEEE_802_11AZ;
import static android.ranging.wifi.pd.WifiPdConstants.IEEE_802_11MC;
import static android.ranging.wifi.pd.WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE;

import android.net.MacAddress;
import android.net.wifi.WifiAnnotations;
import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.wifi.pd.WifiPdConstants;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;
import android.ranging.wifi.pd.WifiPdRangingParams;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.ranging.common.RangingUtils.InternalReason;
import com.android.server.ranging.oob.packets.Capabilities;
import com.android.server.ranging.oob.packets.Configuration;
import com.android.server.ranging.oob.packets.DiscoveryChannels;
import com.android.server.ranging.oob.packets.PreambleType;
import com.android.server.ranging.oob.packets.WifiBandwidth;
import com.android.server.ranging.oob.packets.WifiPdCapabilities;
import com.android.server.ranging.oob.packets.WifiPdConfiguration;
import com.android.server.ranging.session.ConfigurationManager;
import com.android.server.ranging.session.ConfigurationManager.ConfigSelector;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import com.google.common.collect.ImmutableSet;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;


public class WifiPdConfigSelector extends ConfigSelector {

    private static final String TAG = WifiPdConfigSelector.class.getSimpleName();

    // Static priority list for channel selection (in MHz)
    private static final ImmutableSet<Integer> CHANNEL_PRIORITY_MHZ = ImmutableSet.of(
            CHANNEL_36,
            CHANNEL_40,
            CHANNEL_44,
            CHANNEL_48,
            CHANNEL_153,
            CHANNEL_157,
            CHANNEL_161,
            CHANNEL_165,
            CHANNEL_1,
            CHANNEL_11
    );

    private static final ImmutableSet<Integer> UPDATE_RATES = ImmutableSet.of(
            UPDATE_RATE_FREQUENT,
            UPDATE_RATE_NORMAL,
            UPDATE_RATE_INFREQUENT
    );

    private final SessionConfig mSessionConfig;
    private final OobInitiatorRangingConfig mOobConfig;

    private final MacAddress mLocalMacAddress;

    @WifiPdRangingCapabilities.PasnMode
    private final Set<Integer> mSupportedPasnModes;
    @WifiAnnotations.PreambleType
    private int mMaxCompatiblePreamble;
    private Duration mMinRangingInterval11mc = Duration.ofMillis(200);
    private Duration mMinRangingInterval11az = Duration.ofMillis(200);
    private final Set<Integer> mSupportedChannels;
    private int mMaxCompatibleChannelWidth;
    private boolean mSupports80211az;

    private @Nullable SelectedWifiPdConfig mSelectedConfig = null;

    public WifiPdConfigSelector(
            SessionConfig sessionConfig,
            OobInitiatorRangingConfig oobConfig,
            WifiPdRangingCapabilities capabilities) {
        mSessionConfig = sessionConfig;
        mOobConfig = oobConfig;
        mSupportedPasnModes = new HashSet<>(capabilities.getSupportedPasnModes());
        mSupportedChannels = new HashSet<>(
                capabilities.getSupportedDiscoveryChannelFrequenciesMhz());
        mMaxCompatiblePreamble = capabilities.getMaxPreamble();
        mMaxCompatibleChannelWidth = capabilities.getMaxChannelWidth();
        mSupports80211az = capabilities.is80211azNtbSupported();
        mLocalMacAddress = capabilities.getProximityDetectionMacAddress();
    }

    public static boolean isCapableOfConfig(
            @NonNull OobInitiatorRangingConfig oobConfig,
            @Nullable WifiPdRangingCapabilities capabilities) {

        if (capabilities == null) {
            Log.v(TAG, "Not capable of Wifi PD");
            return false;
        }

        if (oobConfig.getRangingIntervalRange().getUpper().toMillis()
                < capabilities.get80211azNtbMinRangingInterval().toMillis()
                && oobConfig.getRangingIntervalRange().getUpper().toMillis()
                < capabilities.get80211mcMinRangingInterval().toMillis()) {
            Log.d(TAG, "Local config does not support configured ranging intervals");
            return false;
        }
        return true;
    }

    @Override
    public void addPeerCapabilities(
            @NonNull RangingDevice peer, @NonNull Capabilities capabilities,
            @NonNull com.android.server.ranging.oob.packets.DeviceType deviceType)
            throws ConfigurationManager.ConfigSelectionException {
        if (!(capabilities instanceof WifiPdCapabilities pdCapabilities)) {
            throw new ConfigurationManager.ConfigSelectionException(
                    "Unexpected capabilities: " + capabilities, InternalReason.UNKNOWN);
        }
        mSelectedConfig = null;
        mSupports80211az =
                mSupports80211az && pdCapabilities.getFeature11az();
        Set<Integer> peerPasnMode = new HashSet<>();
        if (pdCapabilities.getAuthenticatedPasnSupport()) {
            peerPasnMode.add(AUTHENTICATED_PASN_MODE);
        }
        if (pdCapabilities.getUnauthenticatedPasnSupport()) {
            peerPasnMode.add(WifiPdRangingCapabilities.UNAUTHENTICATED_PASN_MODE);
        }
        mSupportedPasnModes.retainAll(peerPasnMode);
        mSupportedChannels.retainAll(new HashSet<>(
                getDiscoveryChannelMhz(
                        pdCapabilities.getChannels())));
        mMaxCompatiblePreamble = Math.min(mMaxCompatiblePreamble,
                pdCapabilities.getMaxPreamble().toByte());
        mMaxCompatibleChannelWidth = Math.min(mMaxCompatibleChannelWidth,
                pdCapabilities.getMaxChannelWidth().toByte());

        if (mSupports80211az && (mMinRangingInterval11az.toMillis()
                < (pdCapabilities.getMinInterval11az()))) {
            mMinRangingInterval11az = Duration.ofMillis(
                    pdCapabilities.getMinInterval11az());
        }

        if (mMinRangingInterval11mc.toMillis()
                < (pdCapabilities.getMinInterval11mc())) {
            mMinRangingInterval11mc = Duration.ofMillis(
                    pdCapabilities.getMinInterval11mc());
        }
    }

    @NonNull
    @Override
    public Set<TechnologyConfig> selectLocalConfigs(
            @NonNull Set<RangingDevice> peers)
            throws ConfigurationManager.ConfigSelectionException {
        if (mSelectedConfig == null) {
            mSelectedConfig = new SelectedWifiPdConfig();
        }
        return mSelectedConfig.getLocalConfigs(peers);
    }

    @NonNull
    @Override
    public Configuration selectRemoteConfig(@NonNull RangingDevice peer)
            throws ConfigurationManager.ConfigSelectionException {
        if (mSelectedConfig == null) {
            mSelectedConfig = new SelectedWifiPdConfig();
        }
        return mSelectedConfig.getPeerConfig();
    }

    private class SelectedWifiPdConfig {
        private final WifiPdRangingParams mSelectedParams;

        SelectedWifiPdConfig() throws ConfigurationManager.ConfigSelectionException {
            mSelectedParams = getBestPossibleRangingParams();
        }

        private WifiPdRangingParams getBestPossibleRangingParams()
                throws ConfigurationManager.ConfigSelectionException {
            WifiPdRangingParams.Builder paramsBuilder = new WifiPdRangingParams.Builder(
                    mLocalMacAddress)
                    .setChannelWidth(mMaxCompatibleChannelWidth)
                    .setPreambleType(mMaxCompatiblePreamble)
                    .setResponder80211azNtbSupported(mSupports80211az);
            paramsBuilder.setDiscoveryChannelFrequencyMhz(selectChannel());
            paramsBuilder.setRangingUpdateRate(selectRangingUpdateRate());
            selectPasnMode(paramsBuilder);
            return paramsBuilder.build();
        }

        private int selectChannel() throws ConfigurationManager.ConfigSelectionException {
            for (int channelMhz : CHANNEL_PRIORITY_MHZ) {
                if (mSupportedChannels.contains(channelMhz)) {
                    return channelMhz;
                }
            }
            throw new ConfigurationManager.ConfigSelectionException(
                    "Peers do not have support for the same channel",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        private int selectRangingUpdateRate() throws ConfigurationManager.ConfigSelectionException {
            for (int rate : UPDATE_RATES) {
                if (isRangingRateSupportedByCapabilities(rate)
                        && mOobConfig.getRangingIntervalRange().contains(
                        Duration.ofMillis(WifiPdConstants.getIntervalInMs(rate)))) {
                    return rate;
                }
            }
            throw new ConfigurationManager.ConfigSelectionException(
                    "Matching update rates not found",
                    InternalReason.PEER_CAPABILITIES_MISMATCH);
        }

        private boolean isRangingRateSupportedByCapabilities(int rate) {
            long intervalMs = WifiPdConstants.getIntervalInMs(rate);
            if (mSupports80211az) {
                return mMinRangingInterval11az.toMillis() >= intervalMs;
            } else {
                return mMinRangingInterval11mc.toMillis() >= intervalMs;
            }
        }

        private void selectPasnMode(WifiPdRangingParams.Builder paramsBuilder) {
            if (mSupportedPasnModes.contains(AUTHENTICATED_PASN_MODE)) {
                byte[] deviceIk = new byte[16];
                new Random().nextBytes(deviceIk);
                paramsBuilder.setPasnMode(AUTHENTICATED_PASN_MODE)
                        .setPassword(generateRandomPskBytes())
                        .setDeviceIk(deviceIk);
            }
        }

        @NonNull
        private ImmutableSet<TechnologyConfig> getLocalConfigs(Set<RangingDevice> peers) {
            return peers.stream().map(peer -> new WifiPdConfig(
                    DEVICE_ROLE_INITIATOR,
                    mSelectedParams,
                    mSessionConfig,
                    peer
            )).collect(ImmutableSet.toImmutableSet());
        }

        @NonNull
        private Configuration getPeerConfig() {
            WifiPdConfiguration.Builder builder = new WifiPdConfiguration.Builder()
                    .setPasnMode(mSelectedParams.getPasnMode() == AUTHENTICATED_PASN_MODE
                            ? com.android.server.ranging.oob.packets.PasnMode.AuthenticatedMode
                            : com.android.server.ranging.oob.packets.PasnMode.UnauthenticatedMode)
                    .setFeature((byte) (mSupports80211az ? IEEE_802_11AZ : IEEE_802_11MC))
                    .setPeerAddress(mLocalMacAddress.toByteArray())
                    .setRangingInterval(
                            (short) WifiPdConstants.getIntervalInMs(
                                    mSelectedParams.getRangingUpdateRate()))
                    .setPreamble(
                            PreambleType.fromByte((byte) mSelectedParams.getPreambleType()))
                    .setChannelWidth(WifiBandwidth.fromByte(
                            (byte) mSelectedParams.getChannelWidth()))
                    .setChannel(
                            (byte) convertFrequencyToChannel(
                                    mSelectedParams.getDiscoveryChannelFrequencyMhz()));

            if (mSelectedParams.getPasnMode() == AUTHENTICATED_PASN_MODE) {
                builder.setPassword(
                                mSelectedParams.getPassword().getBytes(StandardCharsets.UTF_8))
                        .setDeviceIk(mSelectedParams.getDeviceIk());
            } else {
                builder.setPassword(new byte[0])
                        .setDeviceIk(new byte[0]);
            }
            return builder.build();
        }
    }

    private static Set<Integer> getDiscoveryChannelMhz(DiscoveryChannels discoveryChannels) {
        Set<Integer> channelMhz = new HashSet<>();

        // --- 2.4 GHz Channels ---
        // Channel 1: 2412 MHz
        if (discoveryChannels.getChannel1()) {
            channelMhz.add(CHANNEL_1);
        }
        // Channel 11: 2462 MHz
        if (discoveryChannels.getChannel11()) {
            channelMhz.add(CHANNEL_11);
        }
        // --- 5 GHz Channels (UNII-1 & UNII-3) ---

        // UNII-1 Channels (Lower 5GHz Band)
        // Channel 36: 5180 MHz
        if (discoveryChannels.getChannel36()) {
            channelMhz.add(CHANNEL_36);
        }
        // Channel 40: 5200 MHz
        if (discoveryChannels.getChannel40()) {
            channelMhz.add(CHANNEL_40);
        }
        // Channel 44: 5220 MHz
        if (discoveryChannels.getChannel44()) {
            channelMhz.add(CHANNEL_44);
        }
        // Channel 48: 5240 MHz
        if (discoveryChannels.getChannel48()) {
            channelMhz.add(CHANNEL_48);
        }

        // UNII-3 Channels (Upper 5GHz Band)
        // Channel 153: 5765 MHz
        if (discoveryChannels.getChannel153()) {
            channelMhz.add(CHANNEL_153);
        }
        // Channel 157: 5785 MHz
        if (discoveryChannels.getChannel157()) {
            channelMhz.add(CHANNEL_157);
        }
        // Channel 161: 5805 MHz
        if (discoveryChannels.getChannel161()) {
            channelMhz.add(CHANNEL_161);
        }
        // Channel 165: 5825 MHz
        if (discoveryChannels.getChannel165()) {
            channelMhz.add(CHANNEL_165);
        }
        return channelMhz;
    }

    public static int convertFrequencyToChannel(int frequencyMhz) {
        // --- 2.4 GHz Band (Channels 1-13) ---
        // Formula: f_c = 2407 + 5 * channel  --> channel = (f_c - 2407) / 5
        if (frequencyMhz >= 2412 && frequencyMhz <= 2472) {
            if (frequencyMhz == 2484) {
                return 14; // Special case for Channel 14
            }
            int channel = (frequencyMhz - 2407) / 5;
            // Check for correct spacing (5 MHz steps)
            if ((frequencyMhz - 2407) % 5 == 0) {
                return channel;
            }
            return -1; // Not a valid 2.4 GHz channel frequency
        }

        // --- 5 GHz Band (Channels >= 36) ---
        // Formula: f_c = 5000 + 5 * channel  --> channel = (f_c - 5000) / 5
        if (frequencyMhz >= 5180 && frequencyMhz <= 5825) {
            int channel = (frequencyMhz - 5000) / 5;
            // Check for correct spacing (5 MHz steps)
            if ((frequencyMhz - 5000) % 5 == 0) {
                return channel;
            }
            return -1; // Not a valid 5 GHz channel frequency
        }

        return -1; // Frequency out of known Wi-Fi band ranges
    }

    public static int convertChannelToFrequency(int channel) {
        if (channel < 1) {
            return -1; // Channels start at 1
        }

        // --- 2.4 GHz Band (Channels 1-14) ---
        // Formula: f_c = 2407 + 5 * channel
        if (channel >= 1 && channel <= 13) {
            return 2407 + (5 * channel);
        }

        // Special case for Channel 14
        if (channel == 14) {
            return 2484;
        }

        // --- 5 GHz Band (Channels >= 36) ---
        // Formula: f_c = 5000 + 5 * channel
        // Note: This works for 20MHz primary channels like 36, 40, 153, 165
        if (channel >= 36 && channel <= 165 && (channel % 4 == 0 || channel % 4 == 4
                || channel % 4 == 1 || channel % 4 == 3)) {
            return 5000 + (5 * channel);
        }

        // We only support standard 20MHz channels found in common bands.
        return -1;
    }

    private static String generateRandomPskBytes() {
        // Defines the character pool for the password
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        // Generating a random 20-character ASCII password (well within the 8-63 character limit).
        final int passwordLength = 20;
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(passwordLength);
        for (int i = 0; i < passwordLength; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
