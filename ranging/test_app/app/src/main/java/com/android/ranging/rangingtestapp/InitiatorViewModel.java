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

import android.app.Application;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

/** ViewModel for the Initiator. */
public class InitiatorViewModel extends AndroidViewModel {

    private final MutableLiveData<String> mLogText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> mStarted = new MutableLiveData<>(false);

    private final MutableLiveData<Double> mDistanceResult = new MutableLiveData<>();

    private final DistanceMeasurementInitiator
            mDistanceMeasurementInitiator; // mDistanceMeasurementInitiator;

    public InitiatorViewModel(@NonNull Application application) {
        super(application);

        mDistanceMeasurementInitiator =
                new DistanceMeasurementInitiator(
                        application,
                        mDistanceMeasurementCallback,
                        log -> {
                            mLogText.postValue("BT LOG: " + log);
                        });
    }

    void setTargetDevice(BluetoothDevice targetDevice) {
        mDistanceMeasurementInitiator.setTargetDevice(targetDevice);
    }

    LiveData<String> getLogText() {
        return mLogText;
    }

    LiveData<Boolean> getStarted() {
        return mStarted;
    }

    LiveData<Double> getDistanceResult() {
        return mDistanceResult;
    }

    List<String> getSupportedTechnologies() {
        return mDistanceMeasurementInitiator.getSupportedTechnologies();
    }

    List<String> getMeasurementFreqs() {
        return mDistanceMeasurementInitiator.getMeasurementFreqs();
    }

    List<String> getMeasurementDurations() {
        return mDistanceMeasurementInitiator.getMeasureDurationsInIntervalRounds();
    }

    void toggleStartStop(String technology, String freq, int duration) {
        if (!mStarted.getValue()) {
            mDistanceMeasurementInitiator.startDistanceMeasurement(technology, freq, duration);
        } else {
            mDistanceMeasurementInitiator.stopDistanceMeasurement();
        }
    }

    private DistanceMeasurementInitiator.DistanceMeasurementCallback mDistanceMeasurementCallback =
            new DistanceMeasurementInitiator.DistanceMeasurementCallback() {
                @Override
                public void onStartSuccess() {
                    mStarted.postValue(true);
                }

                @Override
                public void onStartFail() {}

                @Override
                public void onStop() {
                    mStarted.postValue(false);
                }

                @Override
                public void onDistanceResult(double distanceMeters) {
                    mDistanceResult.postValue(distanceMeters);
                }
            };
}
