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

import android.app.Activity;
import android.ranging.RangingCapabilities;
import android.ranging.RangingPreference;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.ranging.rangingtestapp.Constants.RangeSessionState;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AccessoryConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryConfigurationData
        .AppleShareableConfigurationData;
import com.android.ranging.rangingtestapp.IosAccessoryRangingHandler.AccessoryCallback;
import com.android.ranging.rangingtestapp.IosAccessoryRangingManager.RangingCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class IosAccessoryRangingViewModel extends AndroidViewModel {
    enum DeviceRoleItem {
        INITIATOR {
            @Override
            public String toString() {
                return "Initiator";
            }

            @Override
            int toGrapiValue() {
                return RangingPreference.DEVICE_ROLE_INITIATOR;
            }
        },
        RESPONDER {
            @Override
            public String toString() {
                return "Responder";
            }

            @Override
            int toGrapiValue() {
                return RangingPreference.DEVICE_ROLE_RESPONDER;
            }
        };

        abstract int toGrapiValue();

        static DeviceRoleItem fromGrapiValue(int value) {
            for (DeviceRoleItem item: values()) {
                if (item.toGrapiValue() == value) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Illegal DeviceRoleItem value: " + value);
        }
    }

    enum SlotDurationItem {
        DURATION_1_MS {
            @Override
            public String toString() {
                return "1 ms";
            }

            @Override
            int toGrapiValue() {
                return UwbRangingParams.DURATION_1_MS;
            }
        },
        DURATION_2_MS {
            @Override
            public String toString() {
                return "2 ms";
            }

            @Override
            int toGrapiValue() {
                return UwbRangingParams.DURATION_2_MS;
            }
        };

        abstract int toGrapiValue();

        static SlotDurationItem fromGrapiValue(int value) {
            for (SlotDurationItem item: values()) {
                if (item.toGrapiValue() == value) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Illegal SlotDurationItem value: " + value);
        }
    }

    enum RangingIntervalItem {
        INTERVAL_120_MS {
            @Override
            public String toString() {
                return "120 ms";
            }

            @Override
            int toGrapiValue() {
                return RawRangingDevice.UPDATE_RATE_FREQUENT;
            }
        },
        INTERVAL_240_MS {
            @Override
            public String toString() {
                return "240 ms";
            }

            @Override
            int toGrapiValue() {
                return RawRangingDevice.UPDATE_RATE_NORMAL;
            }
        },
        INTERVAL_600_MS {
            @Override
            public String toString() {
                return "600 ms";
            }

            @Override
            int toGrapiValue() {
                return RawRangingDevice.UPDATE_RATE_INFREQUENT;
            }
        };

        abstract int toGrapiValue();

        static RangingIntervalItem fromGrapiValue(int value) {
            for (RangingIntervalItem item: values()) {
                if (item.toGrapiValue() == value) {
                    return item;
                }
            }
            throw new IllegalArgumentException("Illegal RangingIntervalItem value: " + value);
        }
    }

    private final MutableLiveData<Constants.RangeSessionState> mSessionState =
            new MutableLiveData<>(Constants.RangeSessionState.STOPPED);

    private final MutableLiveData<DistanceMeasurementViewModel.DistanceResult> mDistanceResult =
            new MutableLiveData<>();

    private DeviceRoleItem mDeviceRoleItem = DeviceRoleItem.RESPONDER;
    private SlotDurationItem mSlotDurationItem = SlotDurationItem.DURATION_2_MS;
    private RangingIntervalItem mRangingIntervalItem = RangingIntervalItem.INTERVAL_240_MS;

    private MutableLiveData<List<SlotDurationItem>> mSlotDurationItems = new MutableLiveData<>();
    private MutableLiveData<List<RangingIntervalItem>> mRangingIntervalItems =
            new MutableLiveData<>();
    private MutableLiveData<Boolean> mBlockStrideSupported = new MutableLiveData<>();

    private final IosAccessoryRangingManager mRangingManager;
    private AtomicReference<AccessoryCallback> mAccessoryCallback =
            new AtomicReference<>(null);
    private final IosAccessoryRangingHandler mRangingHandler = new IosAccessoryRangingHandler() {
        @Override
        void setAccessoryCallback(AccessoryCallback accessoryCallback) {
            mAccessoryCallback.set(accessoryCallback);
        }

        @Override
        AccessoryConfigurationData createAccessoryConfigurationData() {
            return IosAccessoryRangingParameters.createAccessoryConfigurationData(
                    mDeviceRoleItem.toGrapiValue(),
                    mSlotDurationItem.toGrapiValue(),
                    mRangingIntervalItem.toGrapiValue());
        }

        @Override
        boolean startAccessoryRanging(
                AccessoryConfigurationData accessoryConfigurationData,
                AppleShareableConfigurationData appleShareableConfigurationData) {
            RangeSessionState state = mSessionState.getValue();
            printLog("start ranging in " + state + " state: "
                    + accessoryConfigurationData + ", "
                    + appleShareableConfigurationData);
            if (state != RangeSessionState.STOPPED) {
                printLog("Ranging session is not stopped");
                return false;
            }
            boolean success = mRangingManager.startRanging(
                    accessoryConfigurationData, appleShareableConfigurationData);
            if (success) {
                mSessionState.postValue(Constants.RangeSessionState.STARTING);
            }
            return success;
        }

        @Override
        boolean stopAccessoryRanging() {
            RangeSessionState state = mSessionState.getValue();
            printLog("stop ranging in " + state + " state");
            if (state == RangeSessionState.STOPPED) {
                printLog("Ranging session is already stopped");
                return false;
            }
            boolean success = mRangingManager.stopRanging();
            if (success) {
                mSessionState.postValue(Constants.RangeSessionState.STOPPING);
            }
            return success;
        }
    };

    private final RangingCallback mRangingCallbackHandler = new RangingCallback() {
                @Override
                void onRangingCapabilities(RangingCapabilities rangingCapabilities) {
                    UwbRangingCapabilities uwbRangingCapabilities =
                            rangingCapabilities.getUwbCapabilities();

                    if (uwbRangingCapabilities == null) {
                        return;
                    }

                    List<Integer> slotDurations =
                            uwbRangingCapabilities.getSupportedSlotDurations();
                    List<SlotDurationItem> slotDurationItems = new ArrayList<>();
                    for (SlotDurationItem item: SlotDurationItem.values()) {
                        for (Integer slotDuration: slotDurations) {
                            if (item.toGrapiValue() == slotDuration) {
                                slotDurationItems.add(item);
                                break;
                            }
                        }
                    }
                    mSlotDurationItems.postValue(slotDurationItems);

                    List<Integer> updateRates =
                            uwbRangingCapabilities.getSupportedRangingUpdateRates();
                    List<RangingIntervalItem> rangingIntervalItems = new ArrayList<>();
                    for (RangingIntervalItem item: RangingIntervalItem.values()) {
                        for (Integer updateRate: updateRates) {
                            if (item.toGrapiValue() == updateRate) {
                                rangingIntervalItems.add(item);
                                break;
                            }
                        }
                    }
                    mRangingIntervalItems.postValue(rangingIntervalItems);

                    boolean rangingIntervalReconfigurable =
                            uwbRangingCapabilities.isRangingIntervalReconfigurationSupported();
                    mBlockStrideSupported.postValue(rangingIntervalReconfigurable);
                }

                @Override
                void onRangingStarted() {
                    mSessionState.postValue(Constants.RangeSessionState.STARTED);
                    AccessoryCallback accessoryCallback = mAccessoryCallback.get();
                    if (accessoryCallback != null) {
                        accessoryCallback.onRangingStarted();
                    }
                }

                @Override
                void onRangingStopped() {
                    mSessionState.postValue(Constants.RangeSessionState.STOPPED);
                    AccessoryCallback accessoryCallback = mAccessoryCallback.get();
                    if (accessoryCallback != null) {
                        accessoryCallback.onRangingStopped();
                    }
                }

                @Override
                void onRangingResult(int technology, double distanceMeters) {
                    mDistanceResult.postValue(
                            new DistanceMeasurementViewModel.DistanceResult(
                                    technology, distanceMeters));
                }
            };

    private final LoggingListener mLoggingListener;

    static class Factory implements ViewModelProvider.Factory {
        private Activity mActivity;
        private LoggingListener mLoggingListener;

        Factory(Activity activity, LoggingListener loggingListener) {
            mActivity = activity;
            mLoggingListener = loggingListener;
        }


        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new IosAccessoryRangingViewModel(
                    mActivity, mLoggingListener);
        }
    }

    IosAccessoryRangingViewModel(
            @NonNull Activity activity, LoggingListener loggingListener) {
        super(activity.getApplication());
        mRangingManager = new IosAccessoryRangingManager(
                activity, mRangingCallbackHandler, loggingListener);
        mLoggingListener = loggingListener;
    }

    private void printLog(String log) {
        mLoggingListener.log(log);
    }

    LiveData<List<SlotDurationItem>> getSlotDurationItems() {
        return mSlotDurationItems;
    }

    LiveData<List<RangingIntervalItem>> getRangingIntervalItems() {
        return mRangingIntervalItems;
    }

    LiveData<Boolean> getBlockStrideSupported() {
        return mBlockStrideSupported;
    }

    LiveData<Constants.RangeSessionState> getSessionState() {
        return mSessionState;
    }

    LiveData<DistanceMeasurementViewModel.DistanceResult> getDistanceResult() {
        return mDistanceResult;
    }

    IosAccessoryRangingHandler getRangingHandler() {
        return mRangingHandler;
    }

    void setDeviceRoleItem(DeviceRoleItem item) {
        printLog("setting DeviceRoleItem as " + item);
        if (item == mDeviceRoleItem) {
            return;
        }
        RangeSessionState state = mSessionState.getValue();
        if (state != RangeSessionState.STOPPED) {
            stopAccessoryRanging();
        }
        mDeviceRoleItem = item;
    }

    void setSlotDurationItem(SlotDurationItem item) {
        printLog("setting SlotDurationItem as " + item);
        if (item == mSlotDurationItem) {
            return;
        }

        RangeSessionState state = mSessionState.getValue();
        if (state != RangeSessionState.STOPPED) {
            stopAccessoryRanging();
        }
        mSlotDurationItem = item;
    }

    void setRangingIntervalItem(RangingIntervalItem item) {
        printLog("setting RangingIntervalItem as " + item);
        if (item == mRangingIntervalItem) {
            return;
        }
        RangeSessionState state = mSessionState.getValue();
        if (state != RangeSessionState.STOPPED) {
            stopAccessoryRanging();
        }
        mRangingIntervalItem = item;
    }

    void setSkipRound(int skipRound) {
        RangeSessionState state = mSessionState.getValue();
        if (state != RangeSessionState.STARTED) {
            return;
        }
        mRangingManager.setSkipRound(skipRound);
    }

    boolean stopAccessoryRanging() {
        return mRangingHandler.stopAccessoryRanging();
    }
}
