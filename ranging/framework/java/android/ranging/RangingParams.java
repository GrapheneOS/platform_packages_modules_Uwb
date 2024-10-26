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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.cs.CsRangingParameters;
import android.ranging.rtt.RttRangingParams;
import android.ranging.uwb.UwbRangingParams;

import com.android.ranging.flags.Flags;

import java.util.ArrayList;
import java.util.List;

/**
 * RangingParameters is a container for parameters used in ranging sessions.
 * It supports configuration for multiple ranging technologies, such as UWB (Ultra-Wideband)
 * BLE CS (Channel Sounding), etc.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingParams implements Parcelable {

    private final UwbRangingParams mUwbParameters;

    private final CsRangingParameters mCsParameters;

    private final List<RttRangingParams> mRttRangingParams;

    private RangingParams(Builder builder) {
        mUwbParameters = builder.mUwbParameters;
        mCsParameters = builder.mCsParameters;
        mRttRangingParams = builder.mRttRangingParams;
    }


    private RangingParams(Parcel in) {
        mUwbParameters = in.readParcelable(UwbRangingParams.class.getClassLoader(),
                UwbRangingParams.class);
        mCsParameters = in.readParcelable(CsRangingParameters.class.getClassLoader(),
                CsRangingParameters.class);
        mRttRangingParams = in.createTypedArrayList(RttRangingParams.CREATOR);
    }

    public static final Creator<RangingParams> CREATOR = new Creator<RangingParams>() {
        @Override
        public RangingParams createFromParcel(Parcel in) {
            return new RangingParams(in);
        }

        @Override
        public RangingParams[] newArray(int size) {
            return new RangingParams[size];
        }
    };

    /**
     * Gets the UWB ranging parameters.
     *
     * @return the {@link UwbRangingParams} if present, otherwise {@code null}.
     */
    @Nullable
    public UwbRangingParams getUwbParameters() {
        return mUwbParameters;
    }

    /**
     * Gets the Channel Sounding ranging parameters.
     *
     * @return the {@link CsRangingParameters} if present, otherwise {@code null}.
     * @hide
     */
    @Nullable
    public CsRangingParameters getCsParameters() {
        return mCsParameters;
    }

    /**
     * @hide
     */
    @FlaggedApi(Flags.FLAG_RANGING_RTT_ENABLED)
    public List<RttRangingParams> getRttRangingParams() {
        return mRttRangingParams;
    }

    /**
     * @hide
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * @hide
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mUwbParameters, flags);
        dest.writeParcelable(mCsParameters, flags);
        dest.writeTypedList(mRttRangingParams);
    }

    /**
     * Builder for creating instances of {@link RangingParameters}.
     */
    public static final class Builder {
        private UwbRangingParams mUwbParameters = null;
        private CsRangingParameters mCsParameters = null;
        private List<RttRangingParams> mRttRangingParams = new ArrayList<>();

        /**
         * Sets the UWB ranging parameters.
         *
         * @param uwbParameters The UWB-specific configuration.
         * @return This builder instance.
         * @throws IllegalArgumentException if the uwbParameters is null.
         */
        @NonNull
        public Builder setUwbParameters(@NonNull UwbRangingParams uwbParameters) {
            mUwbParameters = uwbParameters;
            return this;
        }

        /**
         * Sets the Channel Sounding ranging parameters.
         *
         * @param csParameters The CS-specific configuration.
         * @return This builder instance.
         * @throws IllegalArgumentException if the csParameters is null.
         * @hide
         */
        @NonNull
        public Builder setCsParameters(@NonNull CsRangingParameters csParameters) {
            mCsParameters = csParameters;
            return this;
        }

        /**
         * Sets the Wi-Fi RTT ranging parameters.
         *
         * @param rttRangingParams The RTT-specific configuration.
         * @return This builder instance.
         * @throws IllegalArgumentException if the rttRangingParams is null.
         * @hide
         */
        @NonNull
        @FlaggedApi(Flags.FLAG_RANGING_RTT_ENABLED)
        public Builder setRttParameters(@NonNull List<RttRangingParams> rttRangingParams) {
            mRttRangingParams = rttRangingParams;
            return this;
        }

        /**
         * Builds a {@link RangingParams} object with the provided parameters.
         *
         * @return a new {@link RangingParams} instance.
         */
        @NonNull
        public RangingParams build() {
            return new RangingParams(this);
        }
    }
}
