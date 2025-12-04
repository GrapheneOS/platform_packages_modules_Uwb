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

package com.android.ranging.rangingtestapp;

import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingPreference;
import android.ranging.SessionConfig;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;

import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AccessoryConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AppleShareableConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.AppleUwbConfigData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.DeviceRole;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.HoppingMode;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.UpdateRateOption;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData.UwbConfigData;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;

public class IosAccessoryRangingParameters {
    static final int RSTU_PER_MS = 1200;

    static AccessoryConfigurationData createAccessoryConfigurationData(
            int uwbDeviceRole, int uwbSlotDuration, int uwbUpdateRate) {
        UpdateRateOption preferredUpdateRate = UpdateRateOption.AUTOMATIC;
        int majorVersion = 2;
        int minorVersion = 0;

        // hard coded parameters for UwbRangingParams.CONFIG_UNICAST_DS_TWR configuration
        HoppingMode hoppingMode = HoppingMode.ENABLE;
        int slotsPerRound = 6;

        DeviceRole deviceRole = (uwbDeviceRole == RangingPreference.DEVICE_ROLE_INITIATOR)
                ? DeviceRole.INITIATOR : DeviceRole.RESPONDER;

        int slotDuration = uwbSlotDuration * RSTU_PER_MS;

        int rangingInterval = 0;
        if (uwbUpdateRate == RawRangingDevice.UPDATE_RATE_FREQUENT) {
            rangingInterval = 120;
        } else if (uwbUpdateRate == RawRangingDevice.UPDATE_RATE_NORMAL) {
            rangingInterval = 240;
        } else if (uwbUpdateRate == RawRangingDevice.UPDATE_RATE_INFREQUENT) {
            rangingInterval = 600;
        }

        // ad hoc values for uncontrollable parameters
        int maximumUwbClockDrift = 200;

        return new AccessoryConfigurationData.Builder()
                .setPreferredUpdateRate(preferredUpdateRate)
                .setUwbConfigData(new UwbConfigData.Builder()
                        .setMajorVersion(majorVersion)
                        .setMinorVersion(minorVersion)
                        .setRangingRole(deviceRole)
                        .setSourceAddress(UwbAddress.createRandomShortAddress().getAddressBytes())
                        .setRequestedHoppingMode(hoppingMode)
                        .setRequestedNumSlotsPerRound(slotsPerRound)
                        .setRequestedSlotDuration(slotDuration)
                        .setRequestedRangingInterval(rangingInterval)
                        .setMaximumUwbClockDrift(maximumUwbClockDrift)
                        .build())
                .build();
    }

    static String createStartRangingAdbCommand(
            AccessoryConfigurationData accessoryConfigurationData,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        UwbConfigData uwbConfigData = accessoryConfigurationData.mUwbConfigData;
        AppleUwbConfigData appleUwbConfigData = appleShareableConfigurationData.mAppleUwbConfigData;
        return "adb shell cmd uwb start-fira-ranging-session"
            + " --session-id " + appleUwbConfigData.mSessionId
            + " --device-type " + (uwbConfigData.mRangingRole == DeviceRole.INITIATOR
                ? "controller" : "controlee")
            + " --device-role " + (uwbConfigData.mRangingRole == DeviceRole.INITIATOR
                ? "initiator" : "responder")
            + " --device-address " + ByteBuffer.wrap(uwbConfigData.mSourceAddress).order(
                ByteOrder.BIG_ENDIAN).getShort()
            + " --dest-addresses " + ByteBuffer.wrap(appleUwbConfigData.mDestAddress).order(
                ByteOrder.BIG_ENDIAN).getShort()
            + " --preamble-code-index " + appleUwbConfigData.mPreambleId
            + " --channel-number " + appleUwbConfigData.mChannelNumber
            + " --slots-per-ranging-round " + appleUwbConfigData.mNumSlotsPerRRound
            + " --slot-duration-rstu " + appleUwbConfigData.mSlotDuration
            + " --ranging-interval-ms " + appleUwbConfigData.mRangingDuration
            + " --vendor-id " + HexFormat.of().formatHex(IosAccessoryConfigurationData.VENDOR_ID)
            + " --sts-iv " + HexFormat.of().formatHex(appleUwbConfigData.mStaticStsIv)
            + " --hopping-mode " + (appleUwbConfigData.mHoppingMode == HoppingMode.ENABLE
                    ? "enabled" : "disabled")
            ;
    }

    static RangingPreference createRangingPreference(
            AccessoryConfigurationData accessoryConfigurationData,
            AppleShareableConfigurationData appleShareableConfigurationData) {
        UwbConfigData uwbConfigData = accessoryConfigurationData.mUwbConfigData;
        AppleUwbConfigData appleUwbConfigData = appleShareableConfigurationData.mAppleUwbConfigData;

        // necessary parameters for Apple's Nearby Interaction Accessory Protocol
        int configId = UwbRangingParams.CONFIG_UNICAST_DS_TWR;
        int notificationConfigType = DataNotificationConfig.NOTIFICATION_CONFIG_ENABLE;

        int slotDuration = appleUwbConfigData.mSlotDuration / RSTU_PER_MS;

        int uwbUpdateRate = 0;
        if (appleUwbConfigData.mRangingDuration == 120) {
            uwbUpdateRate = RawRangingDevice.UPDATE_RATE_FREQUENT;
        } else if (appleUwbConfigData.mRangingDuration == 240) {
            uwbUpdateRate = RawRangingDevice.UPDATE_RATE_NORMAL;
        } else if (appleUwbConfigData.mRangingDuration == 600) {
            uwbUpdateRate = RawRangingDevice.UPDATE_RATE_INFREQUENT;
        }

        // RawRangingDevice
        RawRangingDevice uwbRawRangingDevice = new RawRangingDevice.Builder()
                .setRangingDevice(new RangingDevice.Builder().build())
                .setUwbRangingParams(new UwbRangingParams.Builder(
                        (int) appleUwbConfigData.mSessionId,
                        configId,
                        UwbAddress.fromBytes(uwbConfigData.mSourceAddress),
                        UwbAddress.fromBytes(appleUwbConfigData.mDestAddress))
                        .setComplexChannel(new UwbComplexChannel.Builder()
                                .setChannel(appleUwbConfigData.mChannelNumber)
                                .setPreambleIndex(appleUwbConfigData.mPreambleId)
                                .build())
                        .setRangingUpdateRate(uwbUpdateRate)
                        .setSessionKeyInfo(ByteBuffer
                                .allocate(8)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .put(IosAccessoryConfigurationData.VENDOR_ID)
                                .put(appleUwbConfigData.mStaticStsIv)
                                .array())
                        .setSlotDuration(slotDuration)
                        .build())
                .build();

        // SessionConfig
        SessionConfig uwbSessionConfig = new SessionConfig.Builder()
                .setAngleOfArrivalNeeded(true)
                .setDataNotificationConfig(new DataNotificationConfig.Builder()
                        .setNotificationConfigType(notificationConfigType)
                        .build())
                .build();

        // RangingPreference
        if (uwbConfigData.mRangingRole == DeviceRole.RESPONDER) {
            return new RangingPreference.Builder(
                    RangingPreference.DEVICE_ROLE_RESPONDER,
                    new RawResponderRangingConfig.Builder()
                            .setRawRangingDevice(uwbRawRangingDevice)
                            .build())
                    .setSessionConfig(uwbSessionConfig)
                    .build();
        }
        if (uwbConfigData.mRangingRole == DeviceRole.INITIATOR) {
            return new RangingPreference.Builder(
                    RangingPreference.DEVICE_ROLE_INITIATOR,
                    new RawInitiatorRangingConfig.Builder()
                            .addRawRangingDevice(uwbRawRangingDevice)
                            .build())
                    .setSessionConfig(uwbSessionConfig)
                    .build();
        }
        return null;
    }

}
