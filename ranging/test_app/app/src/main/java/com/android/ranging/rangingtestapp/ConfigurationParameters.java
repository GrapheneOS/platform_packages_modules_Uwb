/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.ranging.rangingtestapp;

import static android.net.wifi.ScanResult.CHANNEL_WIDTH_20MHZ;
import static android.net.wifi.ScanResult.PREAMBLE_LEGACY;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11;
import static android.ranging.wifi.pd.WifiPdRangingCapabilities.UNAUTHENTICATED_PASN_MODE;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.MacAddress;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Utility class to hold ranging params shared across peer devices */
public class ConfigurationParameters {
    private static String PREF_CONFIG = "PrefConfig";

    public Global global;
    public Uwb uwb;
    public BleCs bleCs;
    public BleRssi bleRssi;
    public WifiNanRtt wifiNanRtt;
    public WifiPd wifiPd;
    public Oob oob;

    private ConfigurationParameters(Global global, Uwb uwb, BleCs bleCs, BleRssi bleRssi,
            WifiNanRtt wifiNanRtt, WifiPd wifiPd, Oob oob) {
        this.global = global;
        this.uwb = uwb;
        this.bleCs = bleCs;
        this.bleRssi = bleRssi;
        this.wifiNanRtt = wifiNanRtt;
        this.wifiPd = wifiPd;
        this.oob = oob;
    }

    private ConfigurationParameters(boolean isResponder) {
        global = new Global();
        uwb = new Uwb(isResponder);
        bleCs = new BleCs();
        bleRssi = new BleRssi();
        wifiNanRtt = new WifiNanRtt();
        wifiPd = new WifiPd();
        oob = new Oob();
    }

    public void saveInstance(Context context) {
        SharedPreferences pref = context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefEditor = pref.edit();
        global.toPref(prefEditor);
        uwb.toPref(prefEditor);
        bleCs.toPref(prefEditor);
        bleRssi.toPref(prefEditor);
        wifiNanRtt.toPref(prefEditor);
        oob.toPref(prefEditor);
        prefEditor.apply();
    }

    public static ConfigurationParameters resetInstance(Context context, boolean isResponder) {
        SharedPreferences pref = context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefEditor = pref.edit();
        prefEditor.clear();
        prefEditor.apply();
        return new ConfigurationParameters(isResponder);
    }

    public static ConfigurationParameters restoreInstance(Context context, boolean isResponder) {
        SharedPreferences pref = context.getSharedPreferences(PREF_CONFIG, Context.MODE_PRIVATE);
        return new ConfigurationParameters(
                Global.fromPref(pref, isResponder),
                Uwb.fromPref(pref, isResponder),
                BleCs.fromPref(pref, isResponder),
                BleRssi.fromPref(pref, isResponder),
                WifiNanRtt.fromPref(pref, isResponder),
                WifiPd.fromPref(pref, isResponder),
                Oob.fromPref(pref, isResponder));
    }

    public static class BaseTechConfig {
        public RangingParameters.Technology technology;

        public BaseTechConfig(RangingParameters.Technology technology) {
            this.technology = technology;
        }
    }

    public static class Global extends BaseTechConfig {
        public boolean sensorFusionEnabled = true;

        public Global() {
            super(RangingParameters.Technology.OOB);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putBoolean("sensorFusionEnabled", sensorFusionEnabled);
        }

        public static Global fromPref(SharedPreferences pref, boolean isResponder) {
            Global global = new Global();
            global.sensorFusionEnabled = pref.getBoolean("sensorFusionEnabled",
                    global.sensorFusionEnabled);
            return global;
        }

    }

    public static class Uwb extends BaseTechConfig {
        private static final UwbAddress[] UWB_ADDRESSES = new UwbAddress[] {
                UwbAddress.fromBytes(new byte[]{0x5, 0x6}),
                UwbAddress.fromBytes(new byte[]{0x6, 0x5})
        };
        public boolean isResponder;
        public int channel = UWB_CHANNEL_9;
        public int preamble = UWB_PREAMBLE_CODE_INDEX_11;
        public int configId = UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
        public int sessionId = 5;
        public UwbAddress deviceAddress;
        public UwbAddress peerDeviceAddress;
        public byte[] sessionKey = new byte[] {
                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                0x8, 0x7, 0x6, 0x5, 0x4, 0x3, 0x2, 0x1
        };

        public Uwb(boolean isResponder, UwbAddress deviceAddress, UwbAddress peerDeviceAddress) {
            super(RangingParameters.Technology.UWB);
            this.isResponder = isResponder;
            this.deviceAddress = deviceAddress;
            this.peerDeviceAddress = peerDeviceAddress;
        }

        public Uwb(boolean isResponder) {
            this(
                    isResponder,
                    isResponder ? UWB_ADDRESSES[0] : UWB_ADDRESSES[1],
                    isResponder ? UWB_ADDRESSES[1] : UWB_ADDRESSES[0]
            );
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putBoolean("isResponder", isResponder);
            prefEditor.putInt("channel", channel);
            prefEditor.putInt("preamble", preamble);
            prefEditor.putInt("configId", configId);
            prefEditor.putInt("sessionId", sessionId);
            prefEditor.putString("deviceAddress", new String(deviceAddress.getAddressBytes()));
            prefEditor.putString("peerDeviceAddress", new String(peerDeviceAddress.getAddressBytes()));
        }

        public static Uwb fromPref(SharedPreferences pref, boolean isResponder) {
            Boolean isPrefResponder = null;
            if (pref.contains("isResponder")) {
                isPrefResponder = pref.getBoolean("isResponder", false);
            }
            Uwb uwb = null;
            if (isPrefResponder == null || isPrefResponder != isResponder) {
                uwb = new Uwb(isResponder);
            } else {
                uwb = new Uwb(
                        isResponder,
                        UwbAddress.fromBytes(pref.getString(
                                "deviceAddress",
                                new String(UWB_ADDRESSES[0].getAddressBytes())).getBytes()),
                        UwbAddress.fromBytes(pref.getString(
                                "peerDeviceAddress",
                                new String(UWB_ADDRESSES[1].getAddressBytes())).getBytes()));

            }
            uwb.channel = pref.getInt("channel", uwb.channel);
            uwb.preamble = pref.getInt("preamble", uwb.preamble);
            uwb.configId = pref.getInt("configId", uwb.configId);
            uwb.sessionId = pref.getInt("sessionId", uwb.sessionId);
            return uwb;
        }
    }

    public static class BleCs extends BaseTechConfig {
        public int securityLevel = BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE;

        public BleCs() {
            super(RangingParameters.Technology.BLE_CS);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putInt("securityLevel", securityLevel);
        }

        public static BleCs fromPref(SharedPreferences pref, boolean isResponder) {
            BleCs bleCs = new BleCs();
            bleCs.securityLevel = pref.getInt("securityLevel", bleCs.securityLevel);
            return bleCs;
        }
    }

    public static class BleRssi extends BaseTechConfig {
        public BleRssi() {
            super(RangingParameters.Technology.BLE_CS);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
        }

        public static BleRssi fromPref(SharedPreferences pref, boolean isResponder) {
            return new BleRssi();
        }
    }

    public static class WifiNanRtt extends BaseTechConfig {
        public String serviceName = "ranging_service";
        public boolean isPeriodicRangingEnabled = false;
        public WifiNanRtt() {
            super(RangingParameters.Technology.WIFI_NAN_RTT);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putString("serviceName", serviceName);
            prefEditor.putBoolean("isPeriodicRangingEnabled", isPeriodicRangingEnabled);
        }

        public static WifiNanRtt fromPref(SharedPreferences pref, boolean isResponder) {
            WifiNanRtt wifiNanRtt = new WifiNanRtt();
            wifiNanRtt.serviceName = pref.getString("serviceName", wifiNanRtt.serviceName);
            wifiNanRtt.isPeriodicRangingEnabled = pref.getBoolean("isPeriodicRangingEnabled",
                    wifiNanRtt.isPeriodicRangingEnabled);
            return wifiNanRtt;
        }
    }

    public static class WifiPd extends BaseTechConfig {
        public MacAddress peerMacAddress = MacAddress.fromString("02:00:00:00:00:00");
        public int discoveryChannelFrequencyMhz = 2437;
        public int pasnMode = UNAUTHENTICATED_PASN_MODE;
        public byte[] deviceIk = new byte[0];
        public String password = "password";
        public int preambleType = PREAMBLE_LEGACY;
        public boolean isResponder80211azNtbSupported = true;
        public int channelWidth = CHANNEL_WIDTH_20MHZ;

        public WifiPd() {
            super(RangingParameters.Technology.WIFI_PD);
        }

        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putString("peerMacAddress", peerMacAddress.toString());
            prefEditor.putInt("discoveryChannelFrequencyMhz", discoveryChannelFrequencyMhz);
            prefEditor.putInt("pasnMode", pasnMode);
            prefEditor.putString("deviceIk", HexFormat.of().formatHex(deviceIk));
            prefEditor.putString("password", password);
            prefEditor.putInt("preambleType", preambleType);
            prefEditor.putBoolean("isResponder80211azNtbSupported", isResponder80211azNtbSupported);
            prefEditor.putInt("channelWidth", channelWidth);

        }

        public static WifiPd fromPref(SharedPreferences pref, boolean isResponder) {
            WifiPd wifiPd = new WifiPd();
            wifiPd.peerMacAddress = MacAddress.fromString(pref.getString("peerMacAddress",
                    wifiPd.peerMacAddress.toString()));
            wifiPd.discoveryChannelFrequencyMhz = pref.getInt("discoveryChannelFrequencyMhz",
                    wifiPd.discoveryChannelFrequencyMhz);
            wifiPd.pasnMode = pref.getInt("pasnMode", wifiPd.pasnMode);
            wifiPd.deviceIk = HexFormat.of().parseHex(
                    pref.getString("deviceIk", HexFormat.of().formatHex(wifiPd.deviceIk)));
            wifiPd.password = pref.getString("password", wifiPd.password);
            wifiPd.preambleType = pref.getInt("preambleType", wifiPd.preambleType);
            wifiPd.isResponder80211azNtbSupported = pref.getBoolean("isResponder80211azNtbSupported",
                    wifiPd.isResponder80211azNtbSupported);
            wifiPd.channelWidth = pref.getInt("channelWidth", wifiPd.channelWidth);
            return wifiPd;
        }
    }

    public static class Oob extends BaseTechConfig {
        public int securityLevel = OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC;
        public int mode = OobInitiatorRangingConfig.RANGING_MODE_AUTO;
        public Set<Integer> techFilter = new HashSet<>();

        public Oob() {
            super(RangingParameters.Technology.OOB);
        }

        /**
         * From pref
         * @param pref
         * @param isResponder
         * @return
         */
        public static Oob fromPref(SharedPreferences pref, boolean isResponder) {
            Oob oob = new Oob();
            oob.securityLevel = pref.getInt("securityLevel", oob.securityLevel);
            oob.mode = pref.getInt("mode", oob.mode);
            Set<Integer> techFilter = new HashSet<>();
            for (String strTechId : Objects.requireNonNull(
                    pref.getStringSet("techFilter", new HashSet<>()))) {
                techFilter.add(Integer.parseInt(strTechId));
            }
            oob.techFilter = techFilter;
            return oob;
        }

        /**
         * To pref
         * @param prefEditor
         */
        public void toPref(SharedPreferences.Editor prefEditor) {
            prefEditor.putInt("securityLevel", securityLevel);
            prefEditor.putInt("mode", mode);
            Set<String> strTechFilter = new HashSet<>();
            for (Integer techId : techFilter) {
                strTechFilter.add(String.valueOf(techId));
            }
            prefEditor.putStringSet("techFilter", new HashSet<>(strTechFilter));
        }
    }
}
