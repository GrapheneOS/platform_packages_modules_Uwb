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

package com.google.snippet.ranging;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11;

import android.ranging.DataNotificationConfig;
import android.ranging.RangingDevice;
import android.ranging.RangingParams;
import android.ranging.RangingPreference;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfiguration;
import android.ranging.ble.cs.CsRangingParams;
import android.ranging.ble.rssi.BleRssiRangingParams;
import android.ranging.raw.RawInitiatorRangingParams;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingParams;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.rtt.RttRangingParams;

import com.google.android.mobly.snippet.SnippetObjectConverter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.UUID;

public class RangingPreferenceConverter implements SnippetObjectConverter {

    @Override
    public JSONObject serialize(Object object) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Object deserialize(JSONObject j, Type type) throws JSONException {
        if (type != RangingPreference.class) return null;

        return new RangingPreference.Builder(j.getInt("device_role"),
                getRangingParams(j.getJSONObject("ranging_params"), j.getInt("device_role")))
                .setSessionConfiguration(
                        new SessionConfiguration.Builder()
                                .setSensorFusionParameters(
                                        getSensorFusionParams(
                                                j.getJSONObject("sensor_fusion_params"))
                                )
                                .setDataNotificationConfig(
                                        getDataNotificationConfig(
                                                j.getBoolean("enable_range_data_notifications"))
                                )
                                .build()
                )
                .build();
    }

    private RangingParams getRangingParams(
            JSONObject j, @RangingPreference.DeviceRole int role
    ) throws JSONException {
        RangingParams params;

        if (j.getInt("session_type") == RangingParams.RANGING_SESSION_RAW) {
            if (role == DEVICE_ROLE_INITIATOR) {
                params = getRawInitiatorRangingParams(j);
            } else {
                params = getRawResponderRangingParams(j);
            }
        } else {
            throw new UnsupportedOperationException("OOB ranging not implemented");
        }

        return params;
    }

    private RawInitiatorRangingParams getRawInitiatorRangingParams(
            JSONObject j
    ) throws JSONException {
        RawInitiatorRangingParams.Builder builder = new RawInitiatorRangingParams.Builder();
        JSONArray jPeerParams = j.getJSONArray("peer_params");
        for (int i = 0; i < jPeerParams.length(); i++) {
            builder.addRawRangingDevice(getRawRangingDevice(jPeerParams.getJSONObject(i)));
        }
        return builder.build();
    }

    private RawResponderRangingParams getRawResponderRangingParams(
            JSONObject j
    ) throws JSONException {
        return new RawResponderRangingParams.Builder()
                .setRawRangingDevice(getRawRangingDevice(j.getJSONObject("peer_params")))
                .build();
    }

    private RawRangingDevice getRawRangingDevice(JSONObject j) throws JSONException {
        RawRangingDevice.Builder builder = new RawRangingDevice.Builder();
        builder.setRangingDevice(
                new RangingDevice.Builder()
                        .setUuid(UUID.fromString(j.getString("peer_id")))
                        .build()
        );
        if (!j.isNull("uwb_params")) {
            builder.setUwbRangingParams(getUwbParams(j.getJSONObject("uwb_params")));
        }
        if (!j.isNull("cs_params")) {
            builder.setCsRangingParams(getCsParams(j.getJSONObject("cs_params")));
        }
        if (!j.isNull("rtt_params")) {
            builder.setRttRangingParams(getRttParams(j.getJSONObject("rtt_params")));
        }
        if (!j.isNull("rssi_params")) {
            builder.setBleRssiRangingParams(getBleRssiRangingParams(
                    j.getJSONObject("rssi_params")));
        }
        return builder.build();
    }

    private UwbRangingParams getUwbParams(JSONObject j) throws JSONException {
        UwbRangingParams.Builder builder = new UwbRangingParams.Builder(
                j.getInt("session_id"),
                j.getInt("config_id"),
                UwbAddress.fromBytes(toBytes(j.getJSONArray("device_address"))),
                UwbAddress.fromBytes(toBytes(j.getJSONArray("peer_address")))
        );

        builder.setSubSessionId(j.getInt("sub_session_id"))
                .setSessionKeyInfo(toBytes(j.getJSONArray("session_key_info")))
                .setComplexChannel(
                        new UwbComplexChannel.Builder().setChannel(UWB_CHANNEL_9)
                                .setPreambleIndex(UWB_PREAMBLE_CODE_INDEX_11).build())
                .setRangingUpdateRate(j.getInt("ranging_update_rate"))
                .setSlotDuration(j.getInt("slot_duration_ms"));

        if (!j.isNull("sub_session_key_info")) {
            builder.setSubSessionKeyInfo(toBytes(j.getJSONArray("sub_session_key_info")));
        }
        return builder.build();
    }

    private RttRangingParams getRttParams(JSONObject j) throws JSONException {
        RttRangingParams.Builder builder = new RttRangingParams.Builder(
                j.getString("service_name"));

        return builder.setRangingUpdateRate(j.getInt("ranging_update_rate"))
                .build();
    }

    private BleRssiRangingParams getBleRssiRangingParams(JSONObject j) throws JSONException {
        return new BleRssiRangingParams.Builder(j.getString("peer_address"))
                .setRangingUpdateRate(j.getInt("ranging_update_rate"))
                .build();
    }

    private CsRangingParams getCsParams(JSONObject j) throws JSONException {
        return new CsRangingParams.Builder(j.getString("peer_address"))
                .setRangingUpdateRate(j.getInt("ranging_update_rate"))
                .setSecurityLevel(j.getInt("security_level"))
                .build();
    }

    private SensorFusionParams getSensorFusionParams(JSONObject j) throws JSONException {
        return new SensorFusionParams.Builder()
                .setSensorFusionEnabled(j.getBoolean("is_sensor_fusion_enabled"))
                .build();
    }

    private DataNotificationConfig getDataNotificationConfig(boolean enableRangeDataNotifications) {
        return new DataNotificationConfig.Builder()
                .setNotificationConfigType(
                        enableRangeDataNotifications
                                ? DataNotificationConfig.ENABLE
                                : DataNotificationConfig.DISABLE
                )
                .build();
    }

    private byte[] toBytes(JSONArray jArray) throws JSONException {
        if (jArray == null) {
            return null;
        }
        byte[] bArray = new byte[jArray.length()];
        for (int i = 0; i < jArray.length(); i++) {
            bArray[i] = (byte) jArray.getInt(i);
        }
        return bArray;
    }
}
