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

import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_DL_TDOA_DT_TAG;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_MULTICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_PROVISIONED_UNICAST_DS_TWR_NO_AOA;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_UNICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_UNICAST_DS_TWR_NO_AOA;
import static androidx.core.uwb.backend.impl.internal.Utils.STATIC_STS_SESSION_KEY_INFO_SIZE;
import static androidx.core.uwb.backend.impl.internal.Utils.VENDOR_ID_SIZE;
import static androidx.core.uwb.backend.impl.internal.Utils.getRangingTimingParams;

import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_FIRA_HOPPING_ENABLE;
import static com.google.uwb.support.fira.FiraParams.MAC_ADDRESS_MODE_2_BYTES;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_ONE_TO_MANY;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.PROTOCOL_VERSION_1_1;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_DT_TAG;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLEE;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_DT_TAG;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DL_TDOA;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_PROVISIONED;
import static com.google.uwb.support.fira.FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY;

import android.util.ArrayMap;

import androidx.annotation.Nullable;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import java.util.Arrays;
import java.util.Map;

/**
 * Creates the session-opening bundles for a FiRa session. The default parameters are
 * profile-dependent.
 */
public final class ConfigurationManager {

    private static final Map<Integer, UwbConfiguration> sConfigs = new ArrayMap<>();

    static {
        // ID_1 properties.
        sConfigs.put(
                CONFIG_UNICAST_DS_TWR,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_UNICAST_DS_TWR;
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

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_2 properties.
        sConfigs.put(
                CONFIG_MULTICAST_DS_TWR,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_MULTICAST_DS_TWR;
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

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_3 properties.
        sConfigs.put(
                CONFIG_UNICAST_DS_TWR_NO_AOA,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_UNICAST_DS_TWR_NO_AOA;
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

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_4 properties.
        sConfigs.put(
                CONFIG_PROVISIONED_UNICAST_DS_TWR,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_PROVISIONED_UNICAST_DS_TWR;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_UNICAST;
                    }

                    @Override
                    public int getStsConfig() {
                        return STS_CONFIG_PROVISIONED;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_5 properties.
        sConfigs.put(
                CONFIG_PROVISIONED_MULTICAST_DS_TWR,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_PROVISIONED_MULTICAST_DS_TWR;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_ONE_TO_MANY;
                    }

                    @Override
                    public int getStsConfig() {
                        return STS_CONFIG_PROVISIONED;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_6 properties.
        sConfigs.put(
                CONFIG_PROVISIONED_UNICAST_DS_TWR_NO_AOA,
                new UwbConfiguration() {
                    @Override
                    public int getConfigId() {
                        return CONFIG_PROVISIONED_UNICAST_DS_TWR_NO_AOA;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_UNICAST;
                    }

                    @Override
                    public int getStsConfig() {
                        return STS_CONFIG_PROVISIONED;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_7 properties.
        sConfigs.put(
                CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR;
                    }

                    @Override
                    public int getMultiNodeMode() {
                        return MULTI_NODE_MODE_ONE_TO_MANY;
                    }

                    @Override
                    public int getStsConfig() {
                        return FiraParams.STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY;
                    }

                    @Override
                    public int getAoaResultRequestMode() {
                        return FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS;
                    }

                    @Override
                    public boolean isControllerTheInitiator() {
                        return true;
                    }

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DS_TWR_DEFERRED_MODE;
                    }
                });

        // ID_8 properties.
        sConfigs.put(
                CONFIG_DL_TDOA_DT_TAG,
                new UwbConfiguration() {

                    @Override
                    public int getConfigId() {
                        return CONFIG_DL_TDOA_DT_TAG;
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

                    @Override
                    public int getRangingRoundUsage() {
                        return RANGING_ROUND_USAGE_DL_TDOA;
                    }
                });
    }

    private ConfigurationManager() {
    }

    /** Creates a {@link FiraOpenSessionParams}. */
    public static FiraOpenSessionParams createOpenSessionParams(
            @FiraParams.RangingDeviceType int deviceType,
            UwbAddress localAddress,
            RangingParameters rangingParameters,
            UwbFeatureFlags featureFlags) {
        RangingTimingParams timingParams =
                getRangingTimingParams(rangingParameters.getUwbConfigId());
        UwbConfiguration configuration = sConfigs.get(rangingParameters.getUwbConfigId());
        int deviceRole;
        switch (deviceType) {
            case RANGING_DEVICE_TYPE_CONTROLLER:
                deviceRole =
                        configuration.isControllerTheInitiator()
                                ? RANGING_DEVICE_ROLE_INITIATOR
                                : RANGING_DEVICE_ROLE_RESPONDER;
                break;
            case RANGING_DEVICE_TYPE_CONTROLEE:
                deviceRole =
                        configuration.isControllerTheInitiator()
                                ? RANGING_DEVICE_ROLE_RESPONDER
                                : RANGING_DEVICE_ROLE_INITIATOR;
                break;
            case RANGING_DEVICE_TYPE_DT_TAG:
                deviceRole = RANGING_DEVICE_DT_TAG;
                break;
            default:
                deviceRole = RANGING_DEVICE_ROLE_RESPONDER;
                break;
        }

        FiraOpenSessionParams.Builder builder =
                new FiraOpenSessionParams.Builder()
                        .setProtocolVersion(PROTOCOL_VERSION_1_1)
                        .setRangingRoundUsage(configuration.getRangingRoundUsage())
                        .setMultiNodeMode(configuration.getMultiNodeMode())
                        .setMacAddressMode(MAC_ADDRESS_MODE_2_BYTES)
                        .setDeviceType(deviceType)
                        .setDeviceRole(deviceRole)
                        .setSessionId(rangingParameters.getSessionId())
                        .setDeviceAddress(Conversions.convertUwbAddress(localAddress,
                                featureFlags.isReversedMacAddress()))
                        .setDestAddressList(
                                Conversions.convertUwbAddressList(
                                        rangingParameters
                                                .getPeerAddresses()
                                                .toArray(new UwbAddress[0]),
                                        featureFlags.isReversedMacAddress()))
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
                        .setRangeDataNtfConfig(
                                Utils.convertToFiraNtfConfig(
                                        rangingParameters
                                                .getUwbRangeDataNtfConfig()
                                                .getRangeDataNtfConfigType()))
                        .setRangeDataNtfProximityNear(
                                rangingParameters.getUwbRangeDataNtfConfig().getNtfProximityNear())
                        .setRangeDataNtfProximityFar(
                                rangingParameters.getUwbRangeDataNtfConfig().getNtfProximityFar())
                        .setInBandTerminationAttemptCount(3)
                        .setStsConfig(configuration.getStsConfig());

        if (configuration.getStsConfig() == FiraParams.STS_CONFIG_STATIC) {
            byte[] staticStsIv =
                    Arrays.copyOfRange(
                            rangingParameters.getSessionKeyInfo(),
                            VENDOR_ID_SIZE,
                            STATIC_STS_SESSION_KEY_INFO_SIZE);
            builder.setVendorId(
                            Arrays.copyOf(rangingParameters.getSessionKeyInfo(), VENDOR_ID_SIZE))
                    .setStaticStsIV(staticStsIv);
        } else if (configuration.getStsConfig() == STS_CONFIG_PROVISIONED) {
            builder.setSessionKey(rangingParameters.getSessionKeyInfo())
                    .setIsKeyRotationEnabled(true)
                    .setKeyRotationRate(0);
        } else if (configuration.getStsConfig()
                == STS_CONFIG_PROVISIONED_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            builder.setSessionKey(rangingParameters.getSessionKeyInfo())
                    .setSubSessionId(rangingParameters.getSubSessionId())
                    .setSubsessionKey(rangingParameters.getSubSessionKeyInfo())
                    .setIsKeyRotationEnabled(true)
                    .setKeyRotationRate(0);
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
            @Nullable int[] subSessionIdList,
            @Nullable byte[] subSessionKey,
            UwbFeatureFlags uwbFeatureFlags) {
        UwbConfiguration configuration = sConfigs.get(configId);
        FiraRangingReconfigureParams.Builder builder =
                new FiraRangingReconfigureParams.Builder()
                        .setAction(action)
                        .setAddressList(
                                Conversions.convertUwbAddressList(peerAddresses,
                                                uwbFeatureFlags.isReversedMacAddress())
                                        .toArray(new android.uwb.UwbAddress[0]));
        if (configuration.getStsConfig()
                == FiraParams.STS_CONFIG_DYNAMIC_FOR_CONTROLEE_INDIVIDUAL_KEY) {
            builder.setSubSessionIdList(subSessionIdList).setSubSessionKeyList(subSessionKey);
        }
        return builder.build();
    }

    /** Indicates if the ID presents an unicast configuration. */
    public static boolean isUnicast(@Utils.UwbConfigId int configId) {
        return sConfigs.get(configId).getMultiNodeMode() == MULTI_NODE_MODE_UNICAST;
    }
}
