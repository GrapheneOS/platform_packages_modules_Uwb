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
import android.ranging.uwb.UwbParameters;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingParameters implements Parcelable {

    private final UwbParameters mUwbParameters;

    private RangingParameters(Builder builder) {
        mUwbParameters = builder.mUwbParameters;
    }

    protected RangingParameters(Parcel in) {
        mUwbParameters = in.readParcelable(
                UwbParameters.class.getClassLoader(), UwbParameters.class);
    }

    public static final Creator<RangingParameters> CREATOR = new Creator<RangingParameters>() {
        @Override
        public RangingParameters createFromParcel(Parcel in) {
            return new RangingParameters(in);
        }

        @Override
        public RangingParameters[] newArray(int size) {
            return new RangingParameters[size];
        }
    };

    public @NonNull UwbParameters getUwbParameters() {
        return mUwbParameters;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUwbParameters, flags);
    }

    public static class Builder {
        private UwbParameters mUwbParameters;

        public RangingParameters build() {
            return new RangingParameters(this);
        }

        public Builder setUwbParameters(UwbParameters uwbParameters) {
            mUwbParameters = uwbParameters;
            return this;
        }
    }
}
