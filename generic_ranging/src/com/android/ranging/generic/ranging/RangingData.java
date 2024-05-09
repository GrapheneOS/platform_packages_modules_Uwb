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

package com.android.ranging.generic.ranging;

import com.android.ranging.generic.RangingTechnology;

import com.google.auto.value.AutoValue;

/** Ranging Data class contains data received from a ranging technology such as UWB or CS. */
@AutoValue
public abstract class RangingData {

    /** Returns the ranging technology this data is for. */
    public abstract RangingTechnology getRangingTechnology();

    /** Returns range distance in meters. */
    public abstract double getRangeDistance();

    /** Returns rssi. */
    public abstract int getRssi();

    /** Returns timestamp in nanons. */
    public abstract long getTimestamp();

    /** Returns a builder for {@link RangingData}. */
    public static Builder builder() {
        return new AutoValue_RangingData.Builder();
    }

    /** Builder for {@link RangingData}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRangingTechnology(RangingTechnology rangingTechnology);

        public abstract Builder setRangeDistance(double rangeDistance);

        public abstract Builder setRssi(int rssi);

        public abstract Builder setTimestamp(long timestamp);

        public abstract RangingData build();
    }
}
