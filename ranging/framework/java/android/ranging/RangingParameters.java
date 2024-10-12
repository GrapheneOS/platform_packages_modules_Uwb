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

package android.ranging;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.cs.CsRangingParameters;
import android.ranging.uwb.UwbRangingParameters;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingParameters implements Parcelable {

    private final UwbRangingParameters mUwbParameters;

    private final CsRangingParameters mCsParameters;

    private RangingParameters(Builder builder) {
        mUwbParameters = builder.mUwbParameters;
        mCsParameters = builder.mCsParameters;
    }

    private RangingParameters(Parcel in) {
        mUwbParameters = in.readParcelable(
                UwbRangingParameters.class.getClassLoader(), UwbRangingParameters.class);
        mCsParameters = in.readParcelable(CsRangingParameters.class.getClassLoader(),
                CsRangingParameters.class);
    }

    public static final Creator<RangingParameters> CREATOR = new Creator<>() {
        @Override
        public RangingParameters createFromParcel(Parcel in) {
            return new RangingParameters(in);
        }

        @Override
        public RangingParameters[] newArray(int size) {
            return new RangingParameters[size];
        }
    };

    /** @return ranging parameters for UWB, if they were provided */
    public @Nullable UwbRangingParameters getUwbParameters() {
        return mUwbParameters;
    }

    /** @return ranging parameters for CS, if they were provided */
    public @Nullable CsRangingParameters getCsParameters() {
        return mCsParameters;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUwbParameters, flags);
        dest.writeParcelable(mCsParameters, flags);
    }

    public static class Builder {
        private UwbRangingParameters mUwbParameters = null;
        private CsRangingParameters mCsParameters = null;

        /** Build the {@link RangingParameters object} */
        public RangingParameters build() {
            return new RangingParameters(this);
        }

        /**
         * Set parameters for UWB ranging in this session.
         *
         * @param uwbParameters containing a configuration for UWB ranging.
         */
        public Builder setUwbParameters(UwbRangingParameters uwbParameters) {
            mUwbParameters = uwbParameters;
            return this;
        }

        /**
         * Set parameters for Bluetooth Channel Sounding ranging in this session.
         *
         * @param csParameters containing a configuration for CS ranging.
         */
        public Builder setCsParameters(CsRangingParameters csParameters) {
            mCsParameters = csParameters;
            return this;
        }
    }
}
