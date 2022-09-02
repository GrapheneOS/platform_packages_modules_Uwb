/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.uwb.backend.impl.internal;

import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_1;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_2;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_3;
import static androidx.core.uwb.backend.impl.internal.Utils.STATIC_STS_SESSION_KEY_INFO_SIZE;
import static androidx.core.uwb.backend.impl.internal.Utils.VENDOR_ID_SIZE;
import static androidx.core.uwb.backend.impl.internal.Utils.getRangingTimingParams;

import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_FIRA_HOPPING_ENABLE;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_2_BYTES;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_1_1;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;

import android.annotation.Nullable;
import android.util.ArrayMap;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import java.util.Arrays;
import java.util.Map;

/**
 * Creates the session-opening bundles for a FiRa session. The default parameters are
 * profile-dependent.
 */
public class ConfigurationManager {

    private static final Map<Integer, UwbConfiguration> sConfigs = new ArrayMap<>();

    static {
        // ID_1 properties.
        sConfigs.put(
                CONFIG_ID_1,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_ID_1;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_UNICAST;
                    }

                    @Override
                    public int getStsConfig() {
                        return FiraParams.STS_CONFIG_STATIC;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }
                });

        // ID_2 properties.
        sConfigs.put(
                CONFIG_ID_2,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_ID_2;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_ONE_TO_MANY;
                    }

                    @Override
                    public int getStsConfig() {
                        return FiraParams.STS_CONFIG_STATIC;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }
                });

        // ID_3 properties.
        sConfigs.put(
                CONFIG_ID_3,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_ID_3;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_UNICAST;
                    }

                    @Override
                    public int getStsConfig() {
                        return FiraParams.STS_CONFIG_STATIC;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }
                });
    }

    /** Creates a {@link FiraOpenSessionParams}. */
    public static FiraOpenSessionParams createOpenSessionParams(
            @FiraParams.RangingDeviceType int deviceType,
            UwbAddress localAddress,
            RangingParameters rangingParameters) {
        RangingTimingParams timingParams =
                getRangingTimingParams(rangingParameters.getUwbConfigId());
        UwbConfiguration configuration = sConfigs.get(rangingParameters.getUwbConfigId());
        int deviceRole =
                deviceType == RANGING_DEVICE_TYPE_CONTROLLER
                        ? (configuration.isControllerTheInitiator()
                                ? RANGING_DEVICE_ROLE_INITIATOR
                                : RANGING_DEVICE_ROLE_RESPONDER)
                        : (configuration.isControllerTheInitiator()
                                ? RANGING_DEVICE_ROLE_RESPONDER
                                : RANGING_DEVICE_ROLE_INITIATOR);
        FiraOpenSessionParams.Builder builder =
                new FiraOpenSessionParams.Builder()
                        .setProtocolVersion(PROTOCOL_VERSION_1_1)
                        .setRangingRoundUsage(RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE)
                        .setMultiNodeMode(configuration.getMultiNodeMode())
                        .setMacAddressMode(MAC_ADDRESS_MODE_2_BYTES)
                        .setDeviceType(deviceType)
                        .setDeviceRole(deviceRole)
                        .setSessionId(rangingParameters.getSessionId())
                        .setDeviceAddress(Conversions.convertUwbAddress(localAddress))
                        .setDestAddressList(
                                Conversions.convertUwbAddressList(
                                        rangingParameters
                                                .getPeerAddresses()
                                                .toArray(new UwbAddress[0])))
                        .setAoaResultRequest(configuration.getAoaResultRequestMode())
                        .setChannelNumber(rangingParameters.getComplexChannel().getChannel())
                        .setPreambleCodeIndex(
                                rangingParameters.getComplexChannel().getPreambleIndex())
                        .setInitiationTimeMs(timingParams.getInitiationTimeMs())
                        .setSlotDurationRstu(timingParams.getSlotDurationRstu())
                        .setSlotsPerRangingRound(timingParams.getSlotPerRangingRound())
                        .setRangingIntervalMs(
                                timingParams.getRangingInterval(
                                        rangingParameters.getRangingUpdateRate()))
                        .setInBandTerminationAttemptCount(3);

        if (configuration.getStsConfig() == FiraParams.STS_CONFIG_STATIC) {
            byte[] staticStsIv =
                    Arrays.copyOfRange(
                            rangingParameters.getSessionKeyInfo(),
                            VENDOR_ID_SIZE,
                            STATIC_STS_SESSION_KEY_INFO_SIZE);
            builder.setVendorId(
                            Arrays.copyOf(rangingParameters.getSessionKeyInfo(), VENDOR_ID_SIZE))
                    .setStaticStsIV(staticStsIv);
        }

        if (timingParams.isHoppingEnabled()) {
            builder.setHoppingMode(HOPPING_MODE_FIRA_HOPPING_ENABLE);
        }
        return builder.build();
    }

    /** Creates a {@link FiraRangingReconfigureParams}. */
    public static FiraRangingReconfigureParams createReconfigureParams(
            @Utils.UwbConfigId int configId,
            @FiraParams.MulticastListUpdateAction int action,
            UwbAddress[] peerAddresses,
            @Nullable int[] subSessionIdList) {
        UwbConfiguration configuration = sConfigs.get(configId);
        FiraRangingReconfigureParams.Builder builder =
                new FiraRangingReconfigureParams.Builder()
                        .setAction(action)
                        .setAddressList(
                                Conversions.convertUwbAddressList(peerAddresses)
                                        .toArray(new android.uwb.UwbAddress[0]));
        if (configuration.getStsConfig()
                == FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            builder.setSubSessionIdList(subSessionIdList);
        }
        return builder.build();
    }

    /** Indicates if the ID presents an unicast configuration. */
    public static boolean isUnicast(@Utils.UwbConfigId int configId) {
        return sConfigs.get(configId).getMultiNodeMode() == MULTI_NODE_MODE_UNICAST;
    }
}
