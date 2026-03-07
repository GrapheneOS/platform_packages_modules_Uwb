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

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.android.ranging.rangingtestapp.Constants.RangeSessionState;
import com.android.ranging.rangingtestapp.IosAccessoryRangingViewModel.DeviceRoleItem;
import com.android.ranging.rangingtestapp.IosAccessoryRangingViewModel.RangingIntervalItem;
import com.android.ranging.rangingtestapp.IosAccessoryRangingViewModel.SlotDurationItem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/** The fragment holds the initiator of channel sounding. */
@SuppressWarnings("SetTextI18n")
public class IosAccessoryFragment extends Fragment {

    private static final DecimalFormat DISTANCE_DECIMAL_FMT = new DecimalFormat("0.00");

    private ArrayAdapter<DeviceRoleItem> mDeviceRoleArrayAdapter;
    private ArrayAdapter<SlotDurationItem> mSlotDurationArrayAdapter;
    private List<SlotDurationItem> mSlotDurationItemsFromCapabilities;
    private ArrayAdapter<RangingIntervalItem> mRangingIntervalArrayAdapter;
    private List<RangingIntervalItem> mRangingIntervalFromCapabilities;
    private AtomicBoolean mBlockStrideSupported = new AtomicBoolean(false);
    private ArrayAdapter<Integer> mBlockStrideArrayAdapter;
    private TextView mDistanceText;
    private CanvasView mDistanceCanvasView;
    private Spinner mSpinnerDeviceRole;
    private SwitchCompat mSwitchFilterByCapabilities;
    private Spinner mSpinnerSlotDuration;
    private Spinner mSpinnerRangingInterval;
    private Spinner mSpinnerBlockStride;
    private Button mButton;
    private LinearLayout mDistanceViewLayout;

    private IosAccessoryRangingViewModel mRangingViewModel;
    private BleConnectionIosAccessoryViewModel mBleConnectionViewModel;
    private LoggingListener mLoggingListener;

    private AtomicReference<RangeSessionState> mRangeSessionState =
            new AtomicReference<>(RangeSessionState.STOPPED);

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_ios_accessory, container, false);
        Fragment bleConnectionFragment = new BleConnectionIosAccessoryFragment();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.init_ble_connection_container, bleConnectionFragment).commit();

        mButton = (Button) root.findViewById(R.id.btn_measure);
        mSpinnerDeviceRole = (Spinner) root.findViewById(R.id.spinner_dm_device_role);
        mSwitchFilterByCapabilities = (SwitchCompat) root.findViewById(R.id.switch_filter_options);
        mSpinnerSlotDuration = (Spinner) root.findViewById(R.id.spinner_dm_slot_duration);
        mSpinnerRangingInterval = (Spinner) root.findViewById(R.id.spinner_dm_ranging_interval);
        mSpinnerBlockStride = (Spinner) root.findViewById(R.id.spinner_dm_block_stride);
        mDistanceViewLayout = (LinearLayout) root.findViewById(R.id.layout_distance_view);
        mDistanceText = new TextView(getContext());
        mDistanceViewLayout.addView(mDistanceText);
        mDistanceText.setText("0.00 m");
        mDistanceText.setTextSize(96);
        mDistanceText.setGravity(Gravity.END);
        mDistanceCanvasView = new CanvasView(getContext(), "Distance");
        mDistanceCanvasView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1200));
        mDistanceViewLayout.addView(mDistanceCanvasView);
        mLoggingListener = new LoggingListener(requireContext().getApplicationContext(), false);
        return root;
    }

    private void updateSlotDurationItems() {
        SlotDurationItem originalItem =
                (SlotDurationItem) mSpinnerSlotDuration.getSelectedItem();
        mSlotDurationArrayAdapter.clear();
        if (mSwitchFilterByCapabilities.isChecked()) {
            if (mSlotDurationItemsFromCapabilities != null) {
                mSlotDurationArrayAdapter.addAll(mSlotDurationItemsFromCapabilities);
            }
        } else {
            mSlotDurationArrayAdapter.addAll(SlotDurationItem.values());
        }
        int position = mSlotDurationArrayAdapter.getPosition(originalItem);
        if (position >= 0 && position < mSlotDurationArrayAdapter.getCount()) {
            mSpinnerSlotDuration.setSelection(position);
        }
        SlotDurationItem item =
                (SlotDurationItem) mSpinnerSlotDuration.getSelectedItem();
        mRangingViewModel.setSlotDurationItem(item);
    }

    private void updateRangingIntervalItems() {
        RangingIntervalItem originalItem =
                (RangingIntervalItem) mSpinnerRangingInterval.getSelectedItem();
        mRangingIntervalArrayAdapter.clear();
        if (mSwitchFilterByCapabilities.isChecked()) {
            if (mRangingIntervalFromCapabilities != null) {
                mRangingIntervalArrayAdapter.addAll(mRangingIntervalFromCapabilities);
            }
        } else {
            mRangingIntervalArrayAdapter.addAll(RangingIntervalItem.values());
        }
        int position = mRangingIntervalArrayAdapter.getPosition(originalItem);
        if (position >= 0 && position < mRangingIntervalArrayAdapter.getCount()) {
            mSpinnerRangingInterval.setSelection(position);
        }
        RangingIntervalItem item =
                (RangingIntervalItem) mSpinnerRangingInterval.getSelectedItem();
        mRangingViewModel.setRangingIntervalItem(item);
    }

    private boolean isBlockStrideToBeEnabled(RangeSessionState state) {
        if (state != RangeSessionState.STARTED) {
            // block stride is not configurable when ranging is not started yet
            return false;
        }
        if (mSpinnerDeviceRole.getSelectedItem() == DeviceRoleItem.INITIATOR
                && mBlockStrideSupported.get()) {
            // block stride is configurable when ranging role is controller
            return true;
        }
        // depends on filter options otherwise
        return !mSwitchFilterByCapabilities.isChecked();
    }

    private boolean isRangingButtonToBeEnabled(RangeSessionState state) {
        if (state == RangeSessionState.STARTED) {
            // ranging button should be enabled for stopping ranging
            return true;
        }
        if (state != RangeSessionState.STOPPED) {
            // ranging button should not be enabled during transitioning
            return false;
        }
        // depends on filter options otherwise
        return !mSwitchFilterByCapabilities.isChecked();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDeviceRoleArrayAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        mDeviceRoleArrayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerDeviceRole.setAdapter(mDeviceRoleArrayAdapter);

        mSwitchFilterByCapabilities.setChecked(true);

        mSlotDurationArrayAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        mSlotDurationArrayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerSlotDuration.setAdapter(mSlotDurationArrayAdapter);

        mRangingIntervalArrayAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        mRangingIntervalArrayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerRangingInterval.setAdapter(mRangingIntervalArrayAdapter);

        mBlockStrideArrayAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, new ArrayList<>());
        mBlockStrideArrayAdapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mSpinnerBlockStride.setAdapter(mBlockStrideArrayAdapter);

        mRangingViewModel =
                new ViewModelProvider(
                        this,
                        new IosAccessoryRangingViewModel.Factory(
                                requireActivity(), mLoggingListener))
                        .get(IosAccessoryRangingViewModel.class);
        mBleConnectionViewModel =
                new ViewModelProvider(
                        this,
                        new BleConnectionIosAccessoryViewModel.Factory(
                                requireActivity().getApplication(), mLoggingListener,
                                mRangingViewModel.getRangingHandler()))
                        .get(BleConnectionIosAccessoryViewModel.class);
        mSwitchFilterByCapabilities.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    updateSlotDurationItems();
                    updateRangingIntervalItems();
                    RangeSessionState state = mRangingViewModel.getSessionState().getValue();
                    mButton.setClickable(isRangingButtonToBeEnabled(state));
                    mButton.setEnabled(isRangingButtonToBeEnabled(state));
                }
        );
        mRangingViewModel
                .getSlotDurationItems()
                .observe(
                        getViewLifecycleOwner(),
                        slotDurationItems -> {
                            mSlotDurationItemsFromCapabilities = slotDurationItems;
                            updateSlotDurationItems();
                        }
                );
        mRangingViewModel
                .getRangingIntervalItems()
                .observe(
                        getViewLifecycleOwner(),
                        rangingIntervalItems -> {
                            mRangingIntervalFromCapabilities = rangingIntervalItems;
                            updateRangingIntervalItems();
                        }
                );
        mRangingViewModel
                .getBlockStrideSupported()
                .observe(
                        getViewLifecycleOwner(),
                        blockStrideSupported -> {
                            mBlockStrideSupported.getAndSet(blockStrideSupported);
                        }
                );
        mRangingViewModel
                .getSessionState()
                .observe(
                        getViewLifecycleOwner(),
                        state-> {
                            mRangeSessionState.set(state);
                            switch (state) {
                                case STARTED -> {
                                    mButton.setText(R.string.stop_measurement);
                                    mDistanceCanvasView.cleanUp();
                                    mButton.setClickable(true);
                                    mButton.setEnabled(true);
                                    mSpinnerDeviceRole.setEnabled(false);
                                    mSwitchFilterByCapabilities.setEnabled(false);
                                    mSpinnerSlotDuration.setEnabled(false);
                                    mSpinnerRangingInterval.setEnabled(false);
                                    mSpinnerBlockStride.setSelection(
                                            mBlockStrideArrayAdapter.getPosition(0));
                                    mSpinnerBlockStride.setEnabled(
                                            isBlockStrideToBeEnabled(state));
                                }
                                case STOPPED -> {
                                    mButton.setText(R.string.start_measurement);
                                    mButton.setClickable(isRangingButtonToBeEnabled(state));
                                    mButton.setEnabled(isRangingButtonToBeEnabled(state));
                                    mSpinnerDeviceRole.setEnabled(true);
                                    mSwitchFilterByCapabilities.setEnabled(true);
                                    mSpinnerSlotDuration.setEnabled(true);
                                    mSpinnerRangingInterval.setEnabled(true);
                                    mSpinnerBlockStride.setEnabled(false);
                                }
                                case STARTING -> {
                                    mButton.setText(R.string.starting_measurement);
                                    mButton.setClickable(false);
                                    mButton.setEnabled(false);
                                    mSpinnerDeviceRole.setEnabled(false);
                                    mSwitchFilterByCapabilities.setEnabled(false);
                                    mSpinnerSlotDuration.setEnabled(false);
                                    mSpinnerRangingInterval.setEnabled(false);
                                    mSpinnerBlockStride.setEnabled(false);
                                }
                                case STOPPING -> {
                                    mButton.setText(R.string.stopping_measurement);
                                    mButton.setClickable(false);
                                    mButton.setEnabled(false);
                                    mSpinnerDeviceRole.setEnabled(false);
                                    mSwitchFilterByCapabilities.setEnabled(false);
                                    mSpinnerSlotDuration.setEnabled(false);
                                    mSpinnerRangingInterval.setEnabled(false);
                                    mSpinnerBlockStride.setEnabled(false);
                                }
                            }
                        });
        mRangingViewModel
                .getDistanceResult()
                .observe(
                        getViewLifecycleOwner(),
                        distanceResult -> {
                            mDistanceCanvasView.addNode(
                                    distanceResult.technology,
                                    distanceResult.distanceMeters,
                                    /* abort= */ false);
                            mDistanceText.setText(
                                    DISTANCE_DECIMAL_FMT.format(distanceResult.distanceMeters)
                                            + " m");
                        });

        mDeviceRoleArrayAdapter.addAll(DeviceRoleItem.values());
        mSpinnerDeviceRole.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        DeviceRoleItem item =
                                (DeviceRoleItem) parent.getItemAtPosition(position);
                        mRangingViewModel.setDeviceRoleItem(item);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Do nothing
                    }
                });
        mSpinnerDeviceRole.setSelection(
                mDeviceRoleArrayAdapter.getPosition(DeviceRoleItem.RESPONDER));

        mSlotDurationArrayAdapter.addAll(SlotDurationItem.values());
        mSpinnerSlotDuration.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        SlotDurationItem item =
                                (SlotDurationItem) parent.getItemAtPosition(position);
                        mRangingViewModel.setSlotDurationItem(item);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Do nothing
                    }
            });
        mSpinnerSlotDuration.setSelection(
                mSlotDurationArrayAdapter.getPosition(SlotDurationItem.DURATION_2_MS));

        mRangingIntervalArrayAdapter.addAll(RangingIntervalItem.values());
        mSpinnerRangingInterval.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        RangingIntervalItem item =
                                (RangingIntervalItem) parent.getItemAtPosition(position);
                        mRangingViewModel.setRangingIntervalItem(item);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Do nothing
                    }
                });
        mSpinnerRangingInterval.setSelection(
                mRangingIntervalArrayAdapter.getPosition(RangingIntervalItem.INTERVAL_240_MS));

        Integer[] blockStrides = IntStream.rangeClosed(0, 255).boxed().toArray(Integer[]::new);
        mBlockStrideArrayAdapter.addAll(blockStrides);
        mSpinnerBlockStride.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        if (!mSpinnerBlockStride.isEnabled()) {
                            return;
                        }
                        Integer item = (Integer) parent.getItemAtPosition(position);
                        mRangingViewModel.setSkipRound(item);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Do nothing
                    }
                });
        mSpinnerBlockStride.setSelection(mBlockStrideArrayAdapter.getPosition(0));

        mButton.setOnClickListener(
                v -> {
                    toggleStartStop();
                });
    }

    private void toggleStartStop() {
        RangeSessionState state = mRangeSessionState.get();
        if (state == RangeSessionState.STOPPED) {
            mBleConnectionViewModel.requestRangingForcibly();
            return;
        }
        if (state == RangeSessionState.STARTED) {
            mRangingViewModel.stopAccessoryRanging();
            return;
        }
        printLog("RangeSessionState is still under transition: " + state);
    }

    private void printLog(String log) {
        mLoggingListener.log(log);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}
