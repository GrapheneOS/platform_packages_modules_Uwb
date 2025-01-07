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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

/** Child fragment to handle BLE GATT connection. */
@SuppressWarnings("SetTextI18n")
public class BleConnectionCentralFragment extends Fragment {

    private BleConnectionCentralViewModel mViewModel;
    private ArrayAdapter<String> mConnectedBtDevicesArrayAdapterCentral;
    private Button mButtonScanConnect;
    private Spinner mSpinnerBtAddressCentral;

    public static BleConnectionCentralFragment newInstance() {
        return new BleConnectionCentralFragment();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_ble_connection_central, container, false);
        mButtonScanConnect = (Button) root.findViewById(R.id.btn_scan_connect);
        mSpinnerBtAddressCentral = (Spinner) root.findViewById(R.id.spinner_bt_address_central);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mConnectedBtDevicesArrayAdapterCentral =
                new ArrayAdapter<String>(
                        getContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        mConnectedBtDevicesArrayAdapterCentral.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerBtAddressCentral.setAdapter(mConnectedBtDevicesArrayAdapterCentral);

        mViewModel =
                new ViewModelProvider(requireParentFragment()).get(BleConnectionCentralViewModel.class);
        mViewModel
                .getGattState()
                .observe(
                        getActivity(),
                        gattSate -> {
                            switch (gattSate) {
                                case SCANNING:
                                    mButtonScanConnect.setText("Stop Scan");
                                    break;
                                case CONNECTED:
                                    mButtonScanConnect.setText("Disconnect Gatt");
                                    break;
                                case DISCONNECTED:
                                default:
                                    mButtonScanConnect.setText("Scan and Connect");
                            }
                        });
        mViewModel
                .getConnectedDeviceAddresses()
                .observe(
                        getActivity(),
                        deviceList -> {
                            mConnectedBtDevicesArrayAdapterCentral.clear();
                            mConnectedBtDevicesArrayAdapterCentral.addAll(deviceList);
                            if (mSpinnerBtAddressCentral.getSelectedItem() != null) {
                                String selectedBtAddress =
                                        mSpinnerBtAddressCentral.getSelectedItem().toString();
                                mViewModel.setTargetDevice(selectedBtAddress);
                            }
                        });
        mSpinnerBtAddressCentral.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> adapterView, View view, int i, long l) {
                        String btAddress = mSpinnerBtAddressCentral.getSelectedItem().toString();
                        mViewModel.setTargetDevice(btAddress);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        mViewModel.setTargetDevice("");
                    }
                });
        mButtonScanConnect.setOnClickListener(
                v -> {
                    mViewModel.toggleScanConnect();
                });
    }
}
