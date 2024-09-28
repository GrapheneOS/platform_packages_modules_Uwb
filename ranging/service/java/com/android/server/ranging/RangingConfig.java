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

package com.android.server.ranging;


import com.google.auto.value.AutoValue;

import java.time.Duration;

/** Configuration for multi-tecnology ranging */
@AutoValue
public abstract class RangingConfig {

    /** Returns whether to use the fusing algorithm or not. */
    public abstract boolean getUseFusingAlgorithm();

    /**
     * Returns the max interval at which data will be reported back. If set to 0 data will be
     * reported immediately on reception. If set to non zero value, only latest received data that
     * hasn't yet been reported will be reported, so there's a chance that some data doesn't get
     * reported if multiple data points were received during the same update interval.
     */
    public abstract Duration getMaxUpdateInterval();

    /**
     * Returns the timeout after which precision ranging will be stopped if no data was produced
     * since precision ranging started.
     */
    public abstract Duration getInitTimeout();

    /**
     * Returns the timeout to stop reporting back new data if fusion algorithm wasn't fed ranging
     * data in that amount of time. Checked only if useFusingAlgorithm is set to true.
     */
    public abstract Duration getFusionAlgorithmDriftTimeout();

    /**
     * Returns the timeout to stop ranging if there were no new data updates sent in that time
     * period.
     */
    public abstract Duration getNoUpdateTimeout();

    /** Returns the fusion algorithm configuration if present. */

    /** Returns a builder for {@link RangingConfig}. */
    public static Builder builder() {
        return new AutoValue_RangingConfig.Builder();
    }

    /** Builder for {@link RangingConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setUseFusingAlgorithm(boolean useFusingAlgorithm);

        public abstract Builder setMaxUpdateInterval(Duration maxUpdateInterval);

        public abstract Builder setFusionAlgorithmDriftTimeout(Duration duration);

        public abstract Builder setNoUpdateTimeout(Duration duration);

        public abstract Builder setInitTimeout(Duration duration);


        abstract RangingConfig autoBuild();

        public RangingConfig build() {
            RangingConfig config = autoBuild();
            return config;
        }
    }
}
