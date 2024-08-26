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

package com.android.ranging;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

import java.util.Optional;

/**
 * Represents both data received from ranging technologies and data from the fusion algorithm.
 */
@AutoValue
public abstract class RangingData {

    /** Returns a list of {@link RangingReport} for different ranging technologies if present. */
    public abstract Optional<ImmutableList<RangingReport>> getRangingReports();

    /** Returns {@link FusionReport} if present. */
    public abstract Optional<FusionReport> getFusionReport();

    /** Returns the timestamp for this data. */
    public abstract long getTimestamp();

    /** Returns a builder for {@link RangingReport}. */
    public static Builder builder() {
        return new AutoValue_RangingData.Builder();
    }

    /** Builder for {@link RangingReport}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRangingReports(ImmutableList<RangingReport> rangingData);

        public abstract Builder setFusionReport(FusionReport fusionReport);

        public abstract Builder setTimestamp(long timestamp);

        public abstract RangingData build();
    }
}