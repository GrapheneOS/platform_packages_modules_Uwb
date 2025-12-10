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

import static android.ranging.RangingManager.BLE_CS;
import static android.ranging.RangingManager.BLE_RSSI;
import static android.ranging.RangingManager.UWB;
import static android.ranging.RangingManager.WIFI_NAN_RTT;
import static android.ranging.RangingManager.WIFI_PD;
import static android.ranging.RangingManager.WIFI_STA_RTT;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi select dialog for picking Ranging Technology
 */
public class MultiSelectDialogFragment extends DialogFragment {

    private static final HashBiMap<String, Integer> RANGING_TECH_STRING_TO_ID = HashBiMap.create(
            ImmutableMap.of(
                    "UWB", UWB,
                    "BLE_CS", BLE_CS,
                    "WIFI_NAN_RTT", WIFI_NAN_RTT,
                    "BLE_RSSI", BLE_RSSI,
                    "WIFI_STA_RTT", WIFI_STA_RTT,
                    "WIFI_PD", WIFI_PD));
    private MultiSelectDialogListener mListener;
    private List<Integer> mSelectedItems;

    /**
     * Create new instance of MultiSelectDialogFragment
     * @param selectedItems selected Ranging Technologies
     * @return new instance
     */
    public static MultiSelectDialogFragment newInstance(ArrayList<Integer> selectedItems) {
        MultiSelectDialogFragment fragment = new MultiSelectDialogFragment();
        Bundle args = new Bundle();
        args.putIntegerArrayList("selectedItems", selectedItems);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            mListener = (MultiSelectDialogListener) getTargetFragment();
        } catch (ClassCastException e) {
            throw new ClassCastException(
                    "Calling fragment must implement MultiSelectDialogListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mSelectedItems = getArguments().getIntegerArrayList("selectedItems");
        if (mSelectedItems == null) {
            mSelectedItems = new ArrayList<>();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        String[] items = new String[RANGING_TECH_STRING_TO_ID.size()];
        boolean[] checkedItems = new boolean[RANGING_TECH_STRING_TO_ID.size()];

        for (int i = 0; i < RANGING_TECH_STRING_TO_ID.size(); i++) {
            items[i] = RANGING_TECH_STRING_TO_ID.inverse().get(i);
            checkedItems[i] = mSelectedItems.contains(i);
        }

        builder.setTitle("Select Technologies")
                .setMultiChoiceItems(items, checkedItems,
                        (dialog, which, isChecked) -> {
                            if (isChecked) {
                                mSelectedItems.add(which);
                            } else {
                                mSelectedItems.remove(Integer.valueOf(which));
                            }
                        })
                .setPositiveButton("OK",
                        (dialog, id) -> mListener.onOk(new ArrayList<>(mSelectedItems)))
                .setNegativeButton("Cancel", (dialog, id) -> {
                });

        return builder.create();
    }

    /**
     * Multi select dialog listener
     */
    public interface MultiSelectDialogListener {
        /**
         * Callback when Ok button is pressed.
         * @param selectedItems list of selected items
         */
        void onOk(List<Integer> selectedItems);
    }
}
