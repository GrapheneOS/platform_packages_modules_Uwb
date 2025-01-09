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

import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.ranging.RangingConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.oob.DeviceHandle;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.oob.OobResponderRangingConfig;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.rtt.RttRangingParams;

import java.time.Duration;
import java.util.UUID;

/** Utility class to hold ranging params shared across peer devices */
public class RangingParameters {
    public enum Freq {
        HIGH(RawRangingDevice.UPDATE_RATE_FREQUENT),
        MEDIUM(RawRangingDevice.UPDATE_RATE_NORMAL),
        LOW(RawRangingDevice.UPDATE_RATE_INFREQUENT);
        public final int freq;
        Freq(int freq) {
            this.freq = freq;
        }
        int getFreq() {
            return freq;
        }

        Duration getSlowestIntervalDuration() {
            switch (this) {
                case HIGH:
                    return Duration.ofMillis(1000);
                case MEDIUM:
                    return Duration.ofMillis(5000);
                case LOW:
                    return Duration.ofMillis(10000);
                default:
                    return Duration.ofMillis(100000);
            }
        }

        Duration getFastestIntervalDuration() {
            switch (this) {
                case HIGH:
                    return Duration.ofMillis(100);
                case MEDIUM:
                    return Duration.ofMillis(1000);
                case LOW:
                    return Duration.ofMillis(5000);
                default:
                    return Duration.ofMillis(10000);
            }
        }

        @Override
        public String toString() {
            return name();
        }
        public static Freq fromName(String name) {
            try {
                return Freq.valueOf(name);
            } catch (IllegalArgumentException e) {
                return MEDIUM;
            }
        }
    }

    public enum Technology {
        UWB(RangingManager.UWB),
        BLE_RSSI(RangingManager.BLE_RSSI),
        BLE_CS(RangingManager.BLE_CS),
        WIFI_NAN_RTT(RangingManager.WIFI_NAN_RTT),
        OOB(1000);
        public final int technology;
        Technology(int technology) {
            this.technology = technology;
        }
        int getTechnology() {
            return technology;
        }
        @Override
        public String toString() {
            return name();
        }
        public static Technology fromName(String name) {
            try {
                return Technology.valueOf(name);
            } catch (IllegalArgumentException e) {
                return BLE_RSSI;
            }
        }
    }

    private static RawInitiatorRangingConfig createRawInitiatorConfig(
            String rangingTechnologyName, String freqName,
            ConfigurationParameters configParams,
            BluetoothDevice targetBtDevice) {
        RawRangingDevice.Builder rawRangingDeviceBuilder = new RawRangingDevice.Builder()
                .setRangingDevice(
                        new RangingDevice.Builder()
                                .setUuid(UUID.nameUUIDFromBytes(
                                        targetBtDevice.getAddress().getBytes()))
                                .build());
        if (Technology.fromName(rangingTechnologyName).equals(Technology.UWB)) {
            rawRangingDeviceBuilder.setUwbRangingParams(
                    new UwbRangingParams.Builder(
                            configParams.uwb.sessionId,
                            configParams.uwb.configId,
                            configParams.uwb.deviceAddress,
                            configParams.uwb.peerDeviceAddress)
                            .setComplexChannel(new UwbComplexChannel.Builder()
                                    .setChannel(configParams.uwb.channel)
                                    .setPreambleIndex(configParams.uwb.preamble)
                                    .build())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .setSessionKeyInfo(configParams.uwb.sessionKey)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_CS)) {
            rawRangingDeviceBuilder.setCsRangingParams(
                    new BleCsRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .setSecurityLevel(configParams.bleCs.securityLevel)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_RSSI)) {
            rawRangingDeviceBuilder.setBleRssiRangingParams(
                    new BleRssiRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.WIFI_NAN_RTT)) {
            rawRangingDeviceBuilder.setRttRangingParams(
                    new RttRangingParams.Builder(configParams.wifiNanRtt.serviceName)
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .setPeriodicRangingHwFeatureEnabled(
                                    configParams.wifiNanRtt.isPeriodicRangingEnabled)
                            .build());
        }
        return new RawInitiatorRangingConfig.Builder()
            .addRawRangingDevice(rawRangingDeviceBuilder.build())
            .build();
    }

    private static OobInitiatorRangingConfig createOobInitiatorConfig(
            Context context, BleConnectionCentralViewModel bleConnectionCentralViewModel,
            LoggingListener loggingListener, String freqName,
            ConfigurationParameters configParams,
            BluetoothDevice targetBtDevice) {
        OobBleClient oobBleClient =
                new OobBleClient(context, bleConnectionCentralViewModel, targetBtDevice,
                        loggingListener);
        if (!oobBleClient.waitForSocketCreation()) {
            oobBleClient.close();
            return null;
        }
        return new OobInitiatorRangingConfig.Builder()
            .addDeviceHandle(
                new DeviceHandle.Builder(
                        new RangingDevice.Builder()
                                .setUuid(UUID.nameUUIDFromBytes(
                                        targetBtDevice.getAddress().getBytes()))
                                .build(),
                        oobBleClient)
                    .build())
            .setSecurityLevel(configParams.oob.securityLevel)
            .setRangingMode(configParams.oob.mode)
            .setSlowestRangingInterval(Freq.fromName(freqName).getSlowestIntervalDuration())
            .setFastestRangingInterval(Freq.fromName(freqName).getFastestIntervalDuration())
            .build();
    }

    public static RangingPreference createInitiatorRangingPreference(
            Context context, BleConnectionCentralViewModel bleConnectionCentralViewModel,
            LoggingListener loggingListener, String rangingTechnologyName, String freqName,
            ConfigurationParameters configParams, int duration, BluetoothDevice targetBtDevice) {
        RangingConfig initiatorRangingConfig = null;
        if (Technology.fromName(rangingTechnologyName).equals(Technology.OOB)) {
            initiatorRangingConfig =
                    createOobInitiatorConfig(context, bleConnectionCentralViewModel,
                            loggingListener, freqName, configParams, targetBtDevice);
        } else {
            initiatorRangingConfig =
                createRawInitiatorConfig(
                        rangingTechnologyName, freqName, configParams, targetBtDevice);
        }
        if (initiatorRangingConfig == null) return null;
        SessionConfig sessionConfig = new SessionConfig.Builder()
                .setSensorFusionParams(new SensorFusionParams.Builder()
                        .setSensorFusionEnabled(configParams.global.sensorFusionEnabled)
                        .build())
                .setRangingMeasurementsLimit(duration)
                .build();
        RangingPreference rangingPreference = new RangingPreference.Builder(
                RangingPreference.DEVICE_ROLE_INITIATOR, initiatorRangingConfig)
                .setSessionConfig(sessionConfig)
                .build();
        return rangingPreference;
    }

    private static RawResponderRangingConfig createRawResponderConfig(
            String rangingTechnologyName, String freqName,
            ConfigurationParameters configParams,
            BluetoothDevice targetBtDevice) {
        RawRangingDevice.Builder rawRangingDeviceBuilder = new RawRangingDevice.Builder()
                .setRangingDevice(
                        new RangingDevice.Builder()
                                .setUuid(UUID.nameUUIDFromBytes(
                                        targetBtDevice.getAddress().getBytes()))
                                .build());
        if (Technology.fromName(rangingTechnologyName).equals(Technology.UWB)) {
            rawRangingDeviceBuilder.setUwbRangingParams(
                    new UwbRangingParams.Builder(
                            configParams.uwb.sessionId,
                            configParams.uwb.configId,
                            configParams.uwb.deviceAddress,
                            configParams.uwb.peerDeviceAddress)
                            .setComplexChannel(new UwbComplexChannel.Builder()
                                    .setChannel(configParams.uwb.channel)
                                    .setPreambleIndex(configParams.uwb.preamble)
                                    .build())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .setSessionKeyInfo(configParams.uwb.sessionKey)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_CS)) {
            rawRangingDeviceBuilder.setCsRangingParams(
                    new BleCsRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .setSecurityLevel(configParams.bleCs.securityLevel)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.BLE_RSSI)) {
            rawRangingDeviceBuilder.setBleRssiRangingParams(
                    new BleRssiRangingParams.Builder(targetBtDevice.getAddress())
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .build());
        } else if (Technology.fromName(rangingTechnologyName).equals(Technology.WIFI_NAN_RTT)) {
            rawRangingDeviceBuilder.setRttRangingParams(
                    new RttRangingParams.Builder(configParams.wifiNanRtt.serviceName)
                            .setRangingUpdateRate(Freq.fromName(freqName).freq)
                            .setPeriodicRangingHwFeatureEnabled(
                                    configParams.wifiNanRtt.isPeriodicRangingEnabled)
                            .build());
        }
        return new RawResponderRangingConfig.Builder()
            .setRawRangingDevice(rawRangingDeviceBuilder.build())
            .build();
    }

    private static OobResponderRangingConfig createOobResponderConfig(
            Context context, BleConnectionPeripheralViewModel bleConnectionPeripheralViewModel,
            LoggingListener loggingListener, String freqName,
            ConfigurationParameters configParams,
            BluetoothDevice targetBtDevice) {
        OobBleServer oobBleServer =
                new OobBleServer(context, bleConnectionPeripheralViewModel, targetBtDevice,
                        loggingListener);
        if (!oobBleServer.waitForSocketCreation()) {
            oobBleServer.close();
            return null;
        }
        return new OobResponderRangingConfig.Builder(
                new DeviceHandle.Builder(
                        new RangingDevice.Builder()
                                .setUuid(UUID.nameUUIDFromBytes(
                                        targetBtDevice.getAddress().getBytes()))
                                .build(),
                        oobBleServer).build())
            .build();
    }

    public static RangingPreference createResponderRangingPreference(
            Context context, BleConnectionPeripheralViewModel bleConnectionPeripheralViewModel,
            LoggingListener loggingListener, String rangingTechnologyName, String freqName,
            ConfigurationParameters configParams, int duration, BluetoothDevice targetBtDevice) {
        RangingConfig responderRangingConfig = null;
        if (Technology.fromName(rangingTechnologyName).equals(Technology.OOB)) {
            responderRangingConfig =
                    createOobResponderConfig(context, bleConnectionPeripheralViewModel,
                            loggingListener, freqName, configParams, targetBtDevice);
        } else {
            responderRangingConfig =
                createRawResponderConfig(rangingTechnologyName, freqName, configParams, targetBtDevice);
        }
        if (responderRangingConfig == null) return null;
        SessionConfig sessionConfig = new SessionConfig.Builder()
                .setSensorFusionParams(new SensorFusionParams.Builder()
                        .setSensorFusionEnabled(configParams.global.sensorFusionEnabled)
                        .build())
                .setRangingMeasurementsLimit(duration)
                .build();
        RangingPreference rangingPreference = new RangingPreference.Builder(
                RangingPreference.DEVICE_ROLE_RESPONDER, responderRangingConfig)
                .setSessionConfig(sessionConfig)
                .build();
        return rangingPreference;
    }
}
