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

import static android.view.View.INVISIBLE;

import android.os.Bundle;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** The fragment holds the responder configuration of channel sounding. */
@SuppressWarnings("SetTextI18n")
public class ConfigurationFragment extends Fragment {
    private ArrayAdapter<Boolean> mGlobalSensorFusionAdapter;
    private Spinner mGlobalSensorFusionSpinner;
    private ArrayAdapter<Integer> mUwbChannelAdapter;
    private Spinner mUwbChannelSpinner;
    private ArrayAdapter<Integer> mUwbPreambleAdapter;
    private Spinner mUwbPreambleSpinner;
    private ArrayAdapter<Integer> mUwbConfigIdAdapter;
    private Spinner mUwbConfigIdSpinner;
    private ArrayAdapter<Integer> mBleCsSecurityLevelAdapter;
    private Spinner mBleCsSecurityLevelSpinner;
    private ArrayAdapter<Boolean> mWifiNanRttPeriodicRangingAdapter;
    private Spinner mWifiNanRttPeriodicRangingSpinner;
    private ArrayAdapter<Integer> mOobSecurityLevelAdapter;
    private Spinner mOobSecurityLevelSpinner;
    private ArrayAdapter<Integer> mOobModeAdapter;
    private Spinner mOobModeSpinner;
    private Button mButtonSave;
    private Button mButtonReset;
    private AtomicReference<ConfigurationParameters> mConfigurationParameters = new AtomicReference<>(null);
    private boolean mIsResponder;

    public void setIsResponder(boolean isResponder) {
        mIsResponder = isResponder;
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_configuration, container, false);
        if (mIsResponder) {
            View oobView = root.findViewById(R.id.layout_oob);
            oobView.setVisibility(INVISIBLE);
        }
        mGlobalSensorFusionSpinner = (Spinner) root.findViewById(R.id.global_sensor_fusion_spinner);
        mUwbChannelSpinner = (Spinner) root.findViewById(R.id.uwb_channel_spinner);
        mUwbPreambleSpinner = (Spinner) root.findViewById(R.id.uwb_preamble_spinner);
        mUwbConfigIdSpinner = (Spinner) root.findViewById(R.id.uwb_config_spinner);
        mBleCsSecurityLevelSpinner = (Spinner) root.findViewById(R.id.ble_cs_security_spinner);
        mWifiNanRttPeriodicRangingSpinner =
                (Spinner) root.findViewById(R.id.wifi_nan_rtt_periodic_ranging_spinner);
        mOobSecurityLevelSpinner = (Spinner) root.findViewById(R.id.oob_security_level_spinner);
        mOobModeSpinner = (Spinner) root.findViewById(R.id.oob_mode_spinner);
        mButtonSave = (Button) root.findViewById(R.id.btn_save);
        mButtonReset = (Button) root.findViewById(R.id.btn_reset);
        return root;
    }

    void populateEditFields() {
        mGlobalSensorFusionSpinner.setSelection(mGlobalSensorFusionAdapter.getPosition(
                mConfigurationParameters.get().global.sensorFusionEnabled));
        mUwbChannelSpinner.setSelection(mUwbChannelAdapter.getPosition(
                mConfigurationParameters.get().uwb.channel));
        mUwbPreambleSpinner.setSelection(mUwbPreambleAdapter.getPosition(
                mConfigurationParameters.get().uwb.preamble));
        mUwbConfigIdSpinner.setSelection(mUwbConfigIdAdapter.getPosition(
                mConfigurationParameters.get().uwb.configId));
        mBleCsSecurityLevelSpinner.setSelection(mBleCsSecurityLevelAdapter.getPosition(
                mConfigurationParameters.get().bleCs.securityLevel));
        mWifiNanRttPeriodicRangingSpinner.setSelection(mWifiNanRttPeriodicRangingAdapter.getPosition(
                mConfigurationParameters.get().wifiNanRtt.isPeriodicRangingEnabled));
        mOobSecurityLevelSpinner.setSelection(mOobSecurityLevelAdapter.getPosition(
                mConfigurationParameters.get().oob.securityLevel));
        mOobModeSpinner.setSelection(mOobModeAdapter.getPosition(
                mConfigurationParameters.get().oob.mode));
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mGlobalSensorFusionAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(true, false));
        mGlobalSensorFusionAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mGlobalSensorFusionSpinner.setAdapter(mGlobalSensorFusionAdapter);
        mUwbChannelAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(
                            UwbComplexChannel.UWB_CHANNEL_5,
                            UwbComplexChannel.UWB_CHANNEL_9
                ));
        mUwbChannelAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mUwbChannelSpinner.setAdapter(mUwbChannelAdapter);
        mUwbPreambleAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_9,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_10,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_12,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_25,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_26,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_27,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_28,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_29,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_30,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_31,
                        UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_32
                ));
        mUwbPreambleAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mUwbPreambleSpinner.setAdapter(mUwbPreambleAdapter);
        mUwbConfigIdAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(
                        UwbRangingParams.CONFIG_UNICAST_DS_TWR,
                        UwbRangingParams.CONFIG_MULTICAST_DS_TWR,
                        UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR,
                        UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR,
                        UwbRangingParams.CONFIG_PROVISIONED_INDIVIDUAL_MULTICAST_DS_TWR,
                        UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR_VERY_FAST
                ));
        mUwbConfigIdAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mUwbConfigIdSpinner.setAdapter(mUwbConfigIdAdapter);
        mBleCsSecurityLevelAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(
                        BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE,
                        BleCsRangingCapabilities.CS_SECURITY_LEVEL_FOUR
                ));
        mBleCsSecurityLevelAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mBleCsSecurityLevelSpinner.setAdapter(mBleCsSecurityLevelAdapter);
        mWifiNanRttPeriodicRangingAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(true, false));
        mWifiNanRttPeriodicRangingAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mWifiNanRttPeriodicRangingSpinner.setAdapter(mWifiNanRttPeriodicRangingAdapter);
        mOobSecurityLevelAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(
                        OobInitiatorRangingConfig.SECURITY_LEVEL_BASIC,
                        OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE
                ));
        mOobSecurityLevelAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mOobSecurityLevelSpinner.setAdapter(mOobSecurityLevelAdapter);
        mOobModeAdapter =
                new ArrayAdapter<>(
                        getContext(), android.R.layout.simple_spinner_item, List.of(
                        OobInitiatorRangingConfig.RANGING_MODE_AUTO,
                        OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY,
                        OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY_PREFERRED,
                        OobInitiatorRangingConfig.RANGING_MODE_FUSED
                ));
        mOobModeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mOobModeSpinner.setAdapter(mOobModeAdapter);

        mConfigurationParameters.set(
            ConfigurationParameters.restoreInstance(getContext(), mIsResponder));
        populateEditFields();

        mGlobalSensorFusionSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                        mConfigurationParameters.get().global.sensorFusionEnabled =
                                (boolean) mGlobalSensorFusionSpinner.getItemAtPosition(position);
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        mUwbChannelSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                        mConfigurationParameters.get().uwb.channel =
                                (int) mUwbChannelSpinner.getItemAtPosition(position);
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        mUwbPreambleSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                        mConfigurationParameters.get().uwb.preamble =
                                (int) mUwbPreambleSpinner.getItemAtPosition(position);
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        mUwbConfigIdSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                        mConfigurationParameters.get().uwb.configId =
                                (int) mUwbConfigIdSpinner.getItemAtPosition(position);
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        mBleCsSecurityLevelSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                        mConfigurationParameters.get().bleCs.securityLevel =
                                (int) mBleCsSecurityLevelSpinner.getItemAtPosition(position);
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        mWifiNanRttPeriodicRangingSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
                        mConfigurationParameters.get().wifiNanRtt.isPeriodicRangingEnabled =
                                (boolean) mWifiNanRttPeriodicRangingSpinner.getItemAtPosition(position);
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                    }
                });
        mOobModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mConfigurationParameters.get().oob.mode = (int) mOobModeSpinner.getItemAtPosition(
                        position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        mButtonSave.setOnClickListener(
                v -> {
                    mConfigurationParameters.get().saveInstance(getContext());
                });
        mButtonReset.setOnClickListener(
                v -> {
                    mConfigurationParameters.set(
                        ConfigurationParameters.resetInstance(getContext(), mIsResponder));
                    populateEditFields();
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
