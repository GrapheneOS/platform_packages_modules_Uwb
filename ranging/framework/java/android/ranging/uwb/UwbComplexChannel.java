/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.ranging.uwb;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class UwbComplexChannel implements Parcelable {

    private final int mChannel;
    private final int mPreambleIndex;

    public UwbComplexChannel(int channel, int preambleIndex) {
        mChannel = channel;
        mPreambleIndex = preambleIndex;
    }

    protected UwbComplexChannel(Parcel in) {
        mChannel = in.readInt();
        mPreambleIndex = in.readInt();
    }

    public static final Creator<UwbComplexChannel> CREATOR = new Creator<UwbComplexChannel>() {
        @Override
        public UwbComplexChannel createFromParcel(Parcel in) {
            return new UwbComplexChannel(in);
        }

        @Override
        public UwbComplexChannel[] newArray(int size) {
            return new UwbComplexChannel[size];
        }
    };

    public int getChannel() {
        return mChannel;
    }

    public int getPreambleIndex() {
        return mPreambleIndex;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mChannel);
        dest.writeInt(mPreambleIndex);
    }
}
