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

package android.ranging.cs;

import android.annotation.FlaggedApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

/**
 * Parameters for Bluetooth Channel Sounding ranging.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class CsRangingParameters implements Parcelable {
    public CsRangingParameters() {
        throw new UnsupportedOperationException("Not implemented!");
    }

    private CsRangingParameters(Parcel in) {
    }

    public static final Creator<CsRangingParameters> CREATOR = new Creator<>() {
        @Override
        public CsRangingParameters createFromParcel(Parcel in) {
            return new CsRangingParameters(in);
        }

        @Override
        public CsRangingParameters[] newArray(int size) {
            return new CsRangingParameters[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
    }
}

