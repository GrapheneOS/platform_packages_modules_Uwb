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

import android.bluetooth.BluetoothDevice;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

/** Child fragment to handle BLE GATT connection. */
@SuppressWarnings("SetTextI18n")
public class BleConnectionIosAccessoryFragment extends Fragment {

    private BleConnectionIosAccessoryViewModel mViewModel;

    private Button mButtonAdvertising;
    private ArrayAdapter<BluetoothDevice> mConnectedBluetoothDevicesArrayAdapter;
    private Spinner mSpinnerBluetoothDevice;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(
                R.layout.fragment_ble_connection_ios_accessory, container, false);
        mButtonAdvertising = root.findViewById(R.id.btn_ios_accessory_advertising);
        mSpinnerBluetoothDevice = root.findViewById(R.id.spinner_bt_address_ios_accessory);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mConnectedBluetoothDevicesArrayAdapter =
                new ArrayAdapter<BluetoothDevice>(
                        getContext(), android.R.layout.simple_spinner_item, new ArrayList<>()) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        // Customize how the selected item appears in the Spinner
                        TextView textView = (TextView) super.getView(position, convertView, parent);
                        BluetoothDevice item = getItem(position); // Using getItem()
                        if (item != null) {
                            // Append name to the address for better readability
                            textView.setText(item.getAddress() + " (" + item.getName() + ")");
                        }
                        return textView;
                    }

                    @Override
                    public View getDropDownView(int position, View convertView, ViewGroup parent) {
                        // Customize how items appear in the dropdown list
                        TextView textView = (TextView) super.getDropDownView(
                                position, convertView, parent);
                        BluetoothDevice item = getItem(position); // Using getItem()
                        if (item != null) {
                            // Append name to the address for better readability
                            textView.setText(item.getAddress() + " (" + item.getName() + ")");
                        }
                        return textView;
                    }
                };

        mConnectedBluetoothDevicesArrayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerBluetoothDevice.setAdapter(mConnectedBluetoothDevicesArrayAdapter);

        mViewModel =
                new ViewModelProvider(requireParentFragment()).get(
                    BleConnectionIosAccessoryViewModel.class);
        mViewModel
                .getConnectedDevices()
                .observe(
                        getActivity(),
                        deviceList -> {
                            mConnectedBluetoothDevicesArrayAdapter.clear();
                            mConnectedBluetoothDevicesArrayAdapter.addAll(deviceList);
                            BluetoothDevice device =
                                    (BluetoothDevice) mSpinnerBluetoothDevice.getSelectedItem();
                            mViewModel.setTargetDevice(device);
                        });
        mSpinnerBluetoothDevice.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(
                                AdapterView<?> parent, View view, int position, long id) {
                            BluetoothDevice device =
                                    (BluetoothDevice) parent.getItemAtPosition(position);
                            mViewModel.setTargetDevice(device);
                        }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        mViewModel.setTargetDevice(null);
                    }
                });
        mViewModel
                .getIsAdvertising()
                .observe(
                        getActivity(),
                        isAdvertising -> {
                            if (isAdvertising) {
                                mButtonAdvertising.setText("Stop Advertising");
                            } else {
                                mButtonAdvertising.setText("Start Advertising");
                            }
                        });

        mButtonAdvertising.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mViewModel.toggleAdvertising();
                    }
                });
    }
}
