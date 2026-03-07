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

import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_AUTO;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_FUSED;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY_PREFERRED;
import static android.view.View.INVISIBLE;

import android.annotation.SuppressLint;
import android.net.MacAddress;
import android.os.Bundle;
import android.ranging.RangingCapabilities;
import android.ranging.RangingManager;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.ranging.wifi.pd.WifiPdRangingCapabilities;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** The fragment holds the responder configuration of channel sounding. */
@SuppressWarnings("SetTextI18n")
@SuppressLint("NewApi")
public class ConfigurationFragment extends Fragment implements
        MultiSelectDialogFragment.MultiSelectDialogListener {
    private RangingCapabilities mCachedRangingCapabilities;
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
    private ArrayAdapter<String> mWifiPdPasnModeAdapter;
    private Spinner mWifiPdPasnModeSpinner;
    private ArrayAdapter<Integer> mOobSecurityLevelAdapter;
    private Spinner mOobSecurityLevelSpinner;
    private ArrayAdapter<String> mOobModeAdapter;
    private Spinner mOobModeSpinner;
    private TextView mTechFilterSelection;
    private TextView mWifiPdOwnMacAddress;
    private EditText mWifiPdPeerMacAddress;
    private ArrayList<Integer> mSelectedTechs = new ArrayList<>();
    private Button mButtonSave;
    private Button mButtonReset;

    private static final HashBiMap<String, Integer> WIFI_PD_PASN_MODE_STRING_TO_INT
        = HashBiMap.create(
            ImmutableMap.of(
                    "Mode 1", WifiPdRangingCapabilities.UNAUTHENTICATED_PASN_MODE,
                    "Mode 2", WifiPdRangingCapabilities.AUTHENTICATED_PASN_MODE));

    private static final HashBiMap<Integer, String> RANGING_MODE_STRING_TO_INT =
            HashBiMap.create(
                    ImmutableMap.of(
                            RANGING_MODE_AUTO, "Auto",
                            RANGING_MODE_FUSED, "Fused",
                            RANGING_MODE_HIGH_ACCURACY_PREFERRED, "High Accuracy Preferred"));

    private final AtomicReference<ConfigurationParameters> mConfigurationParameters =
            new AtomicReference<>();
    private boolean mIsResponder;

    public void setIsResponder(boolean isResponder) {
        mIsResponder = isResponder;
    }

    private void cacheRangingCapabilities() {
        if (mCachedRangingCapabilities != null) return;
        RangingManager rangingManager = requireContext().getSystemService(RangingManager.class);
        rangingManager.registerCapabilitiesCallback(
                requireActivity().getMainExecutor(),
                rangingCapabilities -> {
                    mCachedRangingCapabilities = rangingCapabilities;
                    populateEditFields();
                });
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
        mWifiPdPasnModeSpinner = (Spinner) root.findViewById(R.id.wifi_pd_pasn_mode_spinner);
        mWifiPdOwnMacAddress = (TextView) root.findViewById(R.id.wifi_pd_own_mac_address_value);
        mWifiPdPeerMacAddress = (EditText) root.findViewById(R.id.wifi_pd_peer_mac_address_value);
        mOobSecurityLevelSpinner = (Spinner) root.findViewById(R.id.oob_security_level_spinner);
        mOobModeSpinner = (Spinner) root.findViewById(R.id.oob_mode_spinner);
        mTechFilterSelection = (TextView) root.findViewById(R.id.oob_tech_filter_selection);
        mButtonSave = (Button) root.findViewById(R.id.btn_save);
        mButtonReset = (Button) root.findViewById(R.id.btn_reset);
        return root;
    }

    void populateEditFields() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (mConfigurationParameters.get() == null) {
                    return;
                }

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
                mWifiNanRttPeriodicRangingSpinner.setSelection(
                        mWifiNanRttPeriodicRangingAdapter.getPosition(
                                mConfigurationParameters.get().wifiNanRtt
                                        .isPeriodicRangingEnabled));
                mWifiPdPasnModeSpinner.setSelection(
                        mWifiPdPasnModeAdapter.getPosition(
                                WIFI_PD_PASN_MODE_STRING_TO_INT.inverse().get(
                                        mConfigurationParameters.get().wifiPd.pasnMode)));
                mWifiPdOwnMacAddress.setText("02:00:00:00:00:00"); // Default value
                if (mCachedRangingCapabilities != null && Constants.isAtLeastC()) {
                    try {
                        WifiPdRangingCapabilities wifiPdCapabilities =
                                (WifiPdRangingCapabilities) mCachedRangingCapabilities.getClass()
                                        .getMethod("getWifiPdRangingCapabilities")
                                        .invoke(mCachedRangingCapabilities);
                        LoggingListener.getInstance().log("Wifi PD mac address: "
                            + wifiPdCapabilities.getProximityDetectionMacAddress());
                        if (wifiPdCapabilities != null) {
                            mWifiPdOwnMacAddress.setText(
                                    wifiPdCapabilities.getProximityDetectionMacAddress()
                                            .toString());
                        }
                    } catch (Exception e) {
                    }
                }
                mWifiPdPeerMacAddress.setText(
                        mConfigurationParameters.get().wifiPd.peerMacAddress.toString());
                mOobSecurityLevelSpinner.setSelection(mOobSecurityLevelAdapter.getPosition(
                        mConfigurationParameters.get().oob.securityLevel));
                mOobModeSpinner.setSelection(
                        mOobModeAdapter.getPosition(RANGING_MODE_STRING_TO_INT.get(
                                mConfigurationParameters.get().oob.mode)));
                mSelectedTechs = new ArrayList<>(mConfigurationParameters.get().oob.techFilter);
                updateTechFilterSelectionText();
            });
        }
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
                                UwbRangingParams.CONFIG_PROVISIONED_UNICAST_DS_TWR
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

        mWifiPdPasnModeAdapter =
                new ArrayAdapter<>(
                        getContext(),
                        android.R.layout.simple_spinner_item,
                        new ArrayList<>(WIFI_PD_PASN_MODE_STRING_TO_INT.keySet()));
        mWifiPdPasnModeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mWifiPdPasnModeSpinner.setAdapter(mWifiPdPasnModeAdapter);

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
                        getContext(),
                        android.R.layout.simple_spinner_item,
                        new ArrayList<>(RANGING_MODE_STRING_TO_INT.values()));
        mOobModeAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mOobModeSpinner.setAdapter(mOobModeAdapter);

        mTechFilterSelection.setOnClickListener(
                v -> {
                    MultiSelectDialogFragment dialog =
                            MultiSelectDialogFragment.newInstance(
                                    new ArrayList<>(mSelectedTechs));
                    dialog.setTargetFragment(this, 0);
                    dialog.show(getParentFragmentManager(), "MultiSelectDialogFragment");
                });

        mButtonSave.setOnClickListener(
                v -> {
                    ConfigurationParameters params = mConfigurationParameters.get();
                    params.global.sensorFusionEnabled =
                            (boolean) mGlobalSensorFusionSpinner.getSelectedItem();
                    params.uwb.channel = (int) mUwbChannelSpinner.getSelectedItem();
                    params.uwb.preamble = (int) mUwbPreambleSpinner.getSelectedItem();
                    params.uwb.configId = (int) mUwbConfigIdSpinner.getSelectedItem();
                    params.bleCs.securityLevel = (int) mBleCsSecurityLevelSpinner.getSelectedItem();
                    params.wifiNanRtt.isPeriodicRangingEnabled =
                            (boolean) mWifiNanRttPeriodicRangingSpinner.getSelectedItem();
                    params.wifiPd.pasnMode =
                            WIFI_PD_PASN_MODE_STRING_TO_INT.get(
                                    mWifiPdPasnModeSpinner.getSelectedItem().toString());
                    params.wifiPd.peerMacAddress =
                            MacAddress.fromString(mWifiPdPeerMacAddress.getText().toString());
                    params.oob.securityLevel = (int) mOobSecurityLevelSpinner.getSelectedItem();
                    params.oob.mode =
                            RANGING_MODE_STRING_TO_INT.inverse().get(
                                    mOobModeSpinner.getSelectedItem().toString());
                    params.oob.techFilter = new HashSet<>(mSelectedTechs);
                    params.saveInstance(getContext());
                });

        mButtonReset.setOnClickListener(
                v -> {
                    mConfigurationParameters.set(
                            ConfigurationParameters.resetInstance(getContext(), mIsResponder));
                    populateEditFields();
                });

        mConfigurationParameters.set(
                ConfigurationParameters.restoreInstance(getContext(), mIsResponder));
        cacheRangingCapabilities();
    }

    private void updateTechFilterSelectionText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mSelectedTechs.size(); i++) {
            sb.append(getTechnologyName(mSelectedTechs.get(i)));
            if (i < mSelectedTechs.size() - 1) {
                sb.append(", ");
            }
        }
        if (sb.length() == 0) {
            sb.append("None");
        }
        mTechFilterSelection.setText(sb.toString());
    }

    private String getTechnologyName(int technology) {
        switch (technology) {
            case RangingManager.UWB: return "UWB";
            case RangingManager.BLE_CS: return "BLE CS";
            case RangingManager.WIFI_NAN_RTT: return "Wi-Fi RTT";
            case RangingManager.WIFI_STA_RTT: return "Wi-Fi STA RTT";
            case RangingManager.WIFI_PD: return "Wi-Fi PD";
            case RangingManager.BLE_RSSI: return "BLE RSSI";
            default: return "Other (" + technology + ")";
        }
    }

    @Override
    public void onOk(List<Integer> selectedItems) {
        mSelectedTechs.clear();
        mSelectedTechs.addAll(selectedItems);
        mConfigurationParameters.get().oob.techFilter = new HashSet<>(selectedItems);
        updateTechFilterSelectionText();
    }
}
