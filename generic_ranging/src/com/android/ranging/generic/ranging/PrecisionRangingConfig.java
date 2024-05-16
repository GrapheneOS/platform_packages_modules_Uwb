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
import com.android.ranging.generic.proto.MultiSensorFinderConfig;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.time.Duration;
import java.util.Optional;

/** Configuration for Precision Ranging. */
@AutoValue
public abstract class PrecisionRangingConfig {

    /** Returns the list of ranging technologies that were requested for this ranging session. */
    public abstract ImmutableList<RangingTechnology> getRangingTechnologiesToRangeWith();

    /** Returns whether to use the fusing algorithm or not. */
    public abstract boolean getUseFusingAlgorithm();

    /**
     * Returns the max interval at which data will be reported back. If set to 0 data will be
     * reported
     * immediately on reception. If set to non zero value, only latest received data that hasn't
     * been
     * yet reported will be reported, so there's a chance that some data doesn't get reported if
     * multiple data points were received during the same update interval.
     */
    public abstract Duration getMaxUpdateInterval();

    /**
     * Returns the timeout after which precision ranging will be stopped if no data was produced
     * since
     * precision ranging started.
     */
    public abstract Duration getInitTimeout();

    /**
     * Returns the timeout to stop reporting back new data if fusion algorithm wasn't feeded ranging
     * data in that amount of time. Checked only if useFusingAlgorithm is set to true.
     */
    public abstract Duration getFusionAlgorithmDriftTimeout();

    /**
     * Returns the timeout to stop precision ranging if there were no new precision data updates
     * sent
     * in that time period.
     */
    public abstract Duration getNoUpdateTimeout();

    /** Returns the fusion algorithm configuration if present. */
    public abstract Optional<MultiSensorFinderConfig> getFusionAlgorithmConfig();

    /** Returns a builder for {@link PrecisionRangingConfig}. */
    public static Builder builder() {
        return new AutoValue_PrecisionRangingConfig.Builder();
    }

    /** Builder for {@link PrecisionRangingConfig}. */
    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setRangingTechnologiesToRangeWith(
                ImmutableList<RangingTechnology> rangingTechnologiesToRangeWith);

        public abstract Builder setUseFusingAlgorithm(boolean useFusingAlgorithm);

        public abstract Builder setMaxUpdateInterval(Duration maxUpdateInterval);

        public abstract Builder setFusionAlgorithmDriftTimeout(Duration duration);

        public abstract Builder setNoUpdateTimeout(Duration duration);

        public abstract Builder setInitTimeout(Duration duration);

        public abstract Builder setFusionAlgorithmConfig(MultiSensorFinderConfig
                fusionAlgorithmConfig);

        abstract PrecisionRangingConfig autoBuild();

        public PrecisionRangingConfig build() {
            PrecisionRangingConfig config = autoBuild();
            Preconditions.checkArgument(
                    !config.getRangingTechnologiesToRangeWith().isEmpty(),
                    "Ranging technologies to range with must contain at least one ranging "
                            + "technology.");
            Preconditions.checkArgument(
                    config.getUseFusingAlgorithm() == config.getFusionAlgorithmConfig()
                    .isPresent(),
                    "Fusion algorithm config must be set when and only when useFusingAlgorithm"
                    + "is set to");
            if (config.getUseFusingAlgorithm()
                    && config.getRangingTechnologiesToRangeWith().contains(RangingTechnology
                    .UWB)) {
                Preconditions.checkArgument(
                        config.getFusionAlgorithmConfig().get().getUseUwbMeasurements(),
                        "Fusion algorithm should accept UWB measurements since UWB was requested.");
            }
            return config;
        }
    }
}
