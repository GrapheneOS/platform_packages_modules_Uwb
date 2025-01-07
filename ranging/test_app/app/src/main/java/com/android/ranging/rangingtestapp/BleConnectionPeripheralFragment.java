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
import android.view.View.OnClickListener;
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
public class BleConnectionPeripheralFragment extends Fragment {

    private BleConnectionPeripheralViewModel mViewModel;
    private Button mBtnAdvertising;

    private ArrayAdapter<String> mConnectedBtDevicesArrayAdapterPeripheral;
    private Spinner mSpinnerBtAddressPeripheral;

    public static BleConnectionPeripheralFragment newInstance() {
        return new BleConnectionPeripheralFragment();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_ble_connection_peripheral, container, false);
        mBtnAdvertising = root.findViewById(R.id.btn_advertising);
        mSpinnerBtAddressPeripheral = (Spinner) root.findViewById(R.id.spinner_bt_address_peripheral);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mConnectedBtDevicesArrayAdapterPeripheral =
                new ArrayAdapter<String>(
                        getContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        mConnectedBtDevicesArrayAdapterPeripheral.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerBtAddressPeripheral.setAdapter(mConnectedBtDevicesArrayAdapterPeripheral);

        mViewModel =
                new ViewModelProvider(requireParentFragment()).get(BleConnectionPeripheralViewModel.class);
        mViewModel
                .getConnectedDeviceAddresses()
                .observe(
                        getActivity(),
                        deviceList -> {
                            mConnectedBtDevicesArrayAdapterPeripheral.clear();
                            mConnectedBtDevicesArrayAdapterPeripheral.addAll(deviceList);
                            if (mSpinnerBtAddressPeripheral.getSelectedItem() != null) {
                                String selectedBtAddress =
                                        mSpinnerBtAddressPeripheral.getSelectedItem().toString();
                                mViewModel.setTargetDevice(selectedBtAddress);
                            }
                        });
        mSpinnerBtAddressPeripheral.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> adapterView, View view, int i, long l) {
                        String btAddress = mSpinnerBtAddressPeripheral.getSelectedItem().toString();
                        mViewModel.setTargetDevice(btAddress);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        mViewModel.setTargetDevice("");
                    }
                });
        mViewModel
                .getIsAdvertising()
                .observe(
                        getActivity(),
                        isAdvertising -> {
                            if (isAdvertising) {
                                mBtnAdvertising.setText("Stop Advertising");
                            } else {
                                mBtnAdvertising.setText("Start Advertising");
                            }
                        });

        mBtnAdvertising.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mViewModel.toggleAdvertising();
                    }
                });
    }
}
