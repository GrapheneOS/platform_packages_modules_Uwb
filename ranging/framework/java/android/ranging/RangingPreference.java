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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingPreference implements Parcelable {

    private final RangingParameters mRangingParameters;

    private final DataNotificationConfig mDataNotificationConfig;

    private RangingPreference(Builder builder) {
        mRangingParameters = builder.mRangingParameters;
        mDataNotificationConfig = builder.mDataNotificationConfig;
    }

    protected RangingPreference(Parcel in) {
        mRangingParameters = in.readParcelable(
                RangingParameters.class.getClassLoader(), RangingParameters.class);
        mDataNotificationConfig = in.readParcelable(
                DataNotificationConfig.class.getClassLoader(), DataNotificationConfig.class);
    }

    public static final Creator<RangingPreference> CREATOR = new Creator<RangingPreference>() {
        @Override
        public RangingPreference createFromParcel(Parcel in) {
            return new RangingPreference(in);
        }

        @Override
        public RangingPreference[] newArray(int size) {
            return new RangingPreference[size];
        }
    };

    public RangingParameters getRangingParameters() {
        return mRangingParameters;
    }

    public @Nullable DataNotificationConfig getNotificationConfig() {
        return mDataNotificationConfig;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mRangingParameters, flags);
    }

    public static class Builder {
        private RangingParameters mRangingParameters;
        private DataNotificationConfig mDataNotificationConfig;

        public Builder setRangingParameters(RangingParameters rangingParameters) {
            mRangingParameters = rangingParameters;
            return this;
        }

        public Builder setDataNotificationConfig(DataNotificationConfig config) {
            mDataNotificationConfig = config;
            return this;
        }
    }
}
