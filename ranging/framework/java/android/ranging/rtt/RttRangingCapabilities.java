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

package android.ranging.rtt;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.RangingCapabilities.TechnologyCapabilities;
import android.ranging.RangingManager;

import com.android.ranging.flags.Flags;

/**
 * Represents the capabilities of the WiFi Neighbor Awareness Networking Round Trip Time (NAN-RTT)
 * ranging.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_RTT_ENABLED)
public class RttRangingCapabilities implements Parcelable, TechnologyCapabilities {

    private final boolean mHasPeriodicRangingHwFeature;

    private RttRangingCapabilities(Builder builder) {
        mHasPeriodicRangingHwFeature = builder.mHasPeriodicRangingHwFeature;
    }

    private RttRangingCapabilities(Parcel in) {
        mHasPeriodicRangingHwFeature = in.readBoolean();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mHasPeriodicRangingHwFeature);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    public static final Creator<RttRangingCapabilities> CREATOR =
            new Creator<RttRangingCapabilities>() {
                @Override
                public RttRangingCapabilities createFromParcel(Parcel in) {
                    return new RttRangingCapabilities(in);
                }

                @Override
                public RttRangingCapabilities[] newArray(int size) {
                    return new RttRangingCapabilities[size];
                }
            };

    /**
     * @hide
     */
    @Override
    public @RangingManager.RangingTechnology int getTechnology() {
        return RangingManager.WIFI_NAN_RTT;
    }

    /**
     * Indicates whether the hardware supports periodic ranging feature.
     *
     * @return {@code true} if periodic ranging is supported; {@code false} otherwise.
     */
    public boolean hasPeriodicRangingHwFeature() {
        return mHasPeriodicRangingHwFeature;
    }

    /**
     * Builder for {@link RttRangingCapabilities}
     *
     * @hide
     */
    public static class Builder {
        private boolean mHasPeriodicRangingHwFeature = false;

        /**
         * Sets whether hardware supports periodic ranging feature.
         *
         * @param periodicRangingHwFeature {@code true} if periodic ranging is supported;
         *                               {@code false} otherwise.
         * @return this {@link Builder} instance for method chaining.
         */
        @NonNull
        public Builder setPeriodicRangingHwFeature(boolean periodicRangingHwFeature) {
            mHasPeriodicRangingHwFeature = periodicRangingHwFeature;
            return this;
        }

        /**
         * Builds and returns an {@link RttRangingCapabilities} instance configured with the
         * provided settings.
         *
         * @return a new {@link RttRangingCapabilities} instance.
         */
        @NonNull
        public RttRangingCapabilities build() {
            return new RttRangingCapabilities(this);
        }
    }

}
