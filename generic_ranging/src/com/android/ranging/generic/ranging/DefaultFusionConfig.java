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

import location.bluemoon.finder.ConfidenceLevel;
import location.bluemoon.finder.DistanceTraveledCheckConfig;
import location.bluemoon.finder.ExponentiallyWeightedGaussianModelConfig;
import location.bluemoon.finder.FuzzyUpdateSchedulerConfig;
import location.bluemoon.finder.InitialStateSamplerConfig;
import location.bluemoon.finder.ModelConfigContainer;
import location.bluemoon.finder.MultiSensorFinderConfig;
import location.bluemoon.finder.NisDivergenceDetectorConfig;
import location.bluemoon.finder.OdometryBasedEstimatePropagatorConfig;
import location.bluemoon.finder.OdometryNoiseAdderConfig;
import location.bluemoon.finder.OdometryThrottlerConfig;
import location.bluemoon.finder.ParticleFilterConfig;
import location.bluemoon.finder.RangeMeasurementConfig;
import location.bluemoon.finder.RangeMeasurementConfig.RangeSensorModelType;
import location.bluemoon.finder.VarianceBasedSwitchingMeasurementModelConfig;

/** Default configuration for the Fusion algorithm. */
public final class DefaultFusionConfig {

    private DefaultFusionConfig() {}

    public static MultiSensorFinderConfig getDefaultConfig() {
        return MultiSensorFinderConfig.newBuilder()
                .setUseUwbMeasurements(true)
                .setParticleFilterConfig(
                        ParticleFilterConfig.newBuilder().setNumberOfParticles(500).build())
                .setUwbRangeMeasurementConfig(
                        RangeMeasurementConfig.newBuilder()
                                .setSensorModelType(RangeSensorModelType.VARIANCE_BASED_SWITCHING)
                                .setVarianceBasedSwitchingMeasurementModelConfig(
                                        VarianceBasedSwitchingMeasurementModelConfig.newBuilder()
                                                .setSwitchingThreshold(0.04)
                                                .setVarianceWindowSize(5)
                                                .setLowVarianceModelConfig(
                                                        ModelConfigContainer.newBuilder()
                                                                .setExponentiallyWeightedGaussianModelConfig(
                                                                        ExponentiallyWeightedGaussianModelConfig.newBuilder()
                                                                                .setLambdaScaled(0.52711296)
                                                                                .setLoc(-0.16149637)
                                                                                .setScale(0.22877243)
                                                                                .build())
                                                                .build())
                                                .build())
                                .setDistanceTraveledCheckConfig(
                                        DistanceTraveledCheckConfig.newBuilder()
                                                .setDistanceTraveledThresholdM(0.1016)
                                                .build())
                                .build())
                .setFuzzyUpdateSchedulerConfiguration(
                        FuzzyUpdateSchedulerConfig.newBuilder()
                                .setMaxWaitTimeNanos(250000000)
                                .setMaxFrameSizeNanos(250000000)
                                .setMaxBufferSize(10)
                                .build())
                .setDefaultXyUpdateProcessNoiseStddevM(0.001)
                .setOdometryNoiseAdderConfig(
                        OdometryNoiseAdderConfig.newBuilder()
                                .setNumSpeedFilterTaps(2)
                                .setMinNoiseStdDevM(0.01)
                                .setMaxNoiseStdDevM(0.3)
                                .setMinSpeedMps(0.3)
                                .setMaxSpeedMps(5)
                                .build())
                .setNisDivergenceDetectorConfig(
                        NisDivergenceDetectorConfig.newBuilder()
                                .setNisBufferSize(10)
                                .setConfidenceLevel(ConfidenceLevel.CL_99)
                                .setNisSigmaBound(2)
                                .setActivationThresholdM(1)
                                .setDefaultUwbNoiseCovariance(0.5)
                                .build())
                .setOdometryPollingRateHz(60)
                .setOdometryThrottlerConfig(
                        OdometryThrottlerConfig.newBuilder().setThrottlingDtNanos(100000000).build())
                .setOdometryBasedEstimatePropagatorConfig(
                        OdometryBasedEstimatePropagatorConfig.newBuilder().setBufferSize(100).build())
                .setUwbInitialStateSamplerConfig(
                        InitialStateSamplerConfig.newBuilder()
                                .setRangeSamplerConfig(
                                        ModelConfigContainer.newBuilder()
                                                .setExponentiallyWeightedGaussianModelConfig(
                                                        ExponentiallyWeightedGaussianModelConfig.newBuilder()
                                                                .setLambdaScaled(0.3)
                                                                .setLoc(-0.17)
                                                                .setScale(0.7)
                                                                .build())
                                                .build())
                                .build())
                .build();
    }
}
