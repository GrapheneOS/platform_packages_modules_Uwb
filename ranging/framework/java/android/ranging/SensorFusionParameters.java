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

import com.android.ranging.flags.Flags;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class SensorFusionParameters implements Parcelable {
    private final boolean mUseSensorFusion;

    private SensorFusionParameters(Builder builder) {
        mUseSensorFusion = builder.mUseSensorFusion;
    }

    protected SensorFusionParameters(Parcel in) {
        mUseSensorFusion = in.readBoolean();
    }

    /** @return whether or not sensor fusion was requested */
    public boolean getUseSensorFusion() {
        return mUseSensorFusion;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeBoolean(mUseSensorFusion);
    }

    public static final Creator<SensorFusionParameters> CREATOR = new Creator<>() {
        @Override
        public SensorFusionParameters createFromParcel(Parcel in) {
            return new SensorFusionParameters(in);
        }

        @Override
        public SensorFusionParameters[] newArray(int size) {
            return new SensorFusionParameters[size];
        }
    };

    public static class Builder {
        private boolean mUseSensorFusion = true;

        public SensorFusionParameters build() {
            return new SensorFusionParameters(this);
        }

        public Builder useSensorFusion(boolean useSensorFusion) {
            mUseSensorFusion = useSensorFusion;
            return this;
        }
    }
}
