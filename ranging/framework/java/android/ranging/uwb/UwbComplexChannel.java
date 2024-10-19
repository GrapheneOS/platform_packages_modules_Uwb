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
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

/**
 * A Class representing the complex channel for UWB which comprises channel and preamble index
 * negotiated between peer devices out of band before ranging.
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class UwbComplexChannel implements Parcelable {

    private final int mChannel;
    private final int mPreambleIndex;

    private UwbComplexChannel(Builder builder) {
        mChannel = builder.mChannel;
        mPreambleIndex = builder.mPreambleIndex;
    }

    private UwbComplexChannel(@NonNull Parcel in) {
        mChannel = in.readInt();
        mPreambleIndex = in.readInt();
    }

    @NonNull
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

    /**
     * Builder for creating instances of {@link UwbComplexChannel}.
     */
    public static final class Builder {
        private int mChannel = 5;
        private int mPreambleIndex =  9;

        /**
         * Sets the channel for the ranging device.
         *
         * @param channel The channel number to be set.
         * @return This {@link Builder} instance.
         */
        @NonNull
        public Builder setChannel(int channel) {
            mChannel = channel;
            return this;
        }

        /**
         * Sets the preamble index for the ranging device.
         *
         * @param preambleIndex The preamble index to be set.
         * @return This {@link Builder} instance.
         */
        @NonNull
        public Builder setPreambleIndex(int preambleIndex) {
            mPreambleIndex = preambleIndex;
            return this;
        }

        /**
         * Builds and returns a new instance of {@link UwbComplexChannel}.
         *
         * @return A new {@link UwbComplexChannel} instance.
         */
        @NonNull
        public UwbComplexChannel build() {
            return new UwbComplexChannel(this);
        }
    }
}
