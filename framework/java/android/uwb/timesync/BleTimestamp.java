/*
 * Copyright (C) 2021 The Android Open Source Project
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

package org.carconnectivity.android.digitalkey.timesync;

import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for Framework-Vendor Time Sync BLE timestamps.
 *
 * @hide
 */
@SystemApi
public class BleTimestamp implements Parcelable {
    private long mSystemTimeMicroseconds;
    private long mBluetoothTimeMicroseconds;
    private int mDeviceTimeUncertainty;
    private int mMaxClockSkewPpm;
    private boolean mIsClockSkewMeasurementAvailable;

    public BleTimestamp(
            long systemTimeMicroseconds,
            long bluetoothTimeMicroseconds,
            int deviceTimeUncertainty,
            int maxClockSkewPpm,
            boolean isClockSkewMeasurementAvailable) {
        mSystemTimeMicroseconds = systemTimeMicroseconds;
        mBluetoothTimeMicroseconds = bluetoothTimeMicroseconds;
        mDeviceTimeUncertainty = deviceTimeUncertainty;
        mMaxClockSkewPpm = maxClockSkewPpm;
        mIsClockSkewMeasurementAvailable = isClockSkewMeasurementAvailable;
    }

    public long getSystemTimeMicroseconds() {
        return mSystemTimeMicroseconds;
    }

    public long getBluetoothTimeMicroseconds() {
        return mBluetoothTimeMicroseconds;
    }

    public int getDeviceTimeUncertainty() {
        return mDeviceTimeUncertainty;
    }

    public int getMaxClockSkewPpm() {
        return mMaxClockSkewPpm;
    }

    public boolean isClockSkewMeasurementAvailable() {
        return mIsClockSkewMeasurementAvailable;
    }

    public static final Creator<BleTimestamp> CREATOR =
            new Creator<BleTimestamp>() {
                @Override
                public BleTimestamp createFromParcel(Parcel in) {
                    return new BleTimestamp(
                            in.readLong(), in.readLong(), in.readInt(), in.readInt(), in.readBoolean());
                }

                @Override
                public BleTimestamp[] newArray(int size) {
                    return new BleTimestamp[size];
                }
            };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mSystemTimeMicroseconds);
        dest.writeLong(mBluetoothTimeMicroseconds);
        dest.writeInt(mDeviceTimeUncertainty);
        dest.writeInt(mMaxClockSkewPpm);
        dest.writeBoolean(mIsClockSkewMeasurementAvailable);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}