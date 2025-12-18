/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.uwb.timesync;

import android.annotation.FlaggedApi;
import android.annotation.Hide;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for Framework-Vendor Time Sync BLE timestamps.
 *
 * @hide
 */
@Hide
@FlaggedApi(com.android.ranging.flags.Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class BleTimestamp implements Parcelable {
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

    private BleTimestamp(@NonNull Parcel in) {
        mSystemTimeMicroseconds = in.readLong();
        mBluetoothTimeMicroseconds = in.readLong();
        mDeviceTimeUncertainty = in.readInt();
        mMaxClockSkewPpm = in.readInt();
        mIsClockSkewMeasurementAvailable = in.readBoolean();
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

    @NonNull
    public static final Creator<BleTimestamp> CREATOR =
            new Creator<BleTimestamp>() {
                @Override
                public BleTimestamp createFromParcel(Parcel in) {
                    return new BleTimestamp(in);
                }

                @Override
                public BleTimestamp[] newArray(int size) {
                    return new BleTimestamp[size];
                }
            };

    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mSystemTimeMicroseconds);
        dest.writeLong(mBluetoothTimeMicroseconds);
        dest.writeInt(mDeviceTimeUncertainty);
        dest.writeInt(mMaxClockSkewPpm);
        dest.writeBoolean(mIsClockSkewMeasurementAvailable);
    }

    public int describeContents() {
        return 0;
    }
}
