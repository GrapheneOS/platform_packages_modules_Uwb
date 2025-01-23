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

package com.android.server.ranging.fusion;

import android.ranging.RangingData;
import android.ranging.RangingMeasurement;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.uwb.fusion.UwbFilterEngine;
import com.android.uwb.fusion.filtering.MedAvgFilter;
import com.android.uwb.fusion.filtering.MedAvgRotationFilter;
import com.android.uwb.fusion.filtering.PositionFilterImpl;
import com.android.uwb.fusion.math.SphericalVector;
import com.android.uwb.fusion.pose.RotationPoseSource;
import com.android.uwb.fusion.primers.AoaPrimer;
import com.android.uwb.fusion.primers.BackAzimuthPrimer;
import com.android.uwb.fusion.primers.FovPrimer;

import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;

/**
 * A fusion engine that filters and corrects data from each technology before fusing it.
 */
public class FilteringFusionEngine extends FusionEngine {

    private static final String TAG = FilteringFusionEngine.class.getSimpleName();

    /**
     * Documented at
     * {@code packages/modules/Uwb/service/ServiceUwbResources/res/values/config.xml}
     */
    private static class FilterConfig {
        private static final float DISTANCE_INLIERS_FACTOR = 0f / 100f;
        private static final int DISTANCE_WINDOW = 3;

        private static final float ANGLE_INLIERS_FACTOR = 50f / 100f;
        private static final int ANGLE_WINDOW = 5;

        private static final float PRIMER_FOV_RADS = (float) Math.toRadians(60);

        private static final int POSE_UPDATE_INTERVAL_MS = 100;

        private static final float FRONT_AZIMUTH_RADS_PER_SEC = (float) Math.toRadians(12);
        private static final float BACK_AZIMUTH_RADS_PER_SEC = (float) Math.toRadians(10);
        private static final int BACK_AZIMUTH_DETECTION_WINDOW = 5;
        private static final boolean ENABLE_BACK_AZIMUTH_MASKING = true;
        private static final float MIRROR_SCORE_STD_RADS = (float) Math.toRadians(8);
        private static final float BACK_AZIMUTH_NOISE_COEFF = 8f / 100f;
    }


    private final RangingInjector mInjector;
    private final boolean mUseAoa;
    private final EnumMap<RangingTechnology, UwbFilterEngine> mFilters;

    public FilteringFusionEngine(
            @NonNull DataFuser fuser, boolean useAoa, RangingInjector injector
    ) {
        super(fuser);
        mInjector = injector;
        mFilters = new EnumMap<>(RangingTechnology.class);
        mUseAoa = useAoa;
    }

    /**
     * Construct a filter engine configured for the provided technology.
     * Config forked from
     * {@code packages/modules/Uwb/service/java/com/android/server/uwb/UwbInjector.java}
     */
    private @NonNull UwbFilterEngine createFilter(@NonNull RangingTechnology technology) {

        UwbFilterEngine.Builder builder = new UwbFilterEngine.Builder()
                .setFilter(
                        new PositionFilterImpl(
                                new MedAvgRotationFilter(
                                        FilterConfig.ANGLE_WINDOW,
                                        FilterConfig.ANGLE_INLIERS_FACTOR),
                                new MedAvgRotationFilter(
                                        FilterConfig.ANGLE_WINDOW,
                                        FilterConfig.ANGLE_INLIERS_FACTOR),
                                new MedAvgFilter(
                                        FilterConfig.DISTANCE_WINDOW,
                                        FilterConfig.DISTANCE_INLIERS_FACTOR)));

        if (mUseAoa && technology == RangingTechnology.UWB) {
            builder.setPoseSource(new RotationPoseSource(
                    mInjector.getContext(), FilterConfig.POSE_UPDATE_INTERVAL_MS));

            builder.addPrimer(new AoaPrimer());
            builder.addPrimer(new FovPrimer(FilterConfig.PRIMER_FOV_RADS));
            builder.addPrimer(new BackAzimuthPrimer(
                    FilterConfig.FRONT_AZIMUTH_RADS_PER_SEC, FilterConfig.BACK_AZIMUTH_RADS_PER_SEC,
                    FilterConfig.BACK_AZIMUTH_DETECTION_WINDOW,
                    FilterConfig.ENABLE_BACK_AZIMUTH_MASKING,
                    FilterConfig.MIRROR_SCORE_STD_RADS, FilterConfig.BACK_AZIMUTH_NOISE_COEFF));
        }

        return builder.build();
    }

    @Override
    public void start(@NonNull Callback callback) {
        super.start(callback);
    }

    @Override
    public void stop() {
        for (UwbFilterEngine filter : mFilters.values()) {
            filter.close();
        }
        mFilters.clear();
    }

    @Override
    public void feed(@NonNull RangingData data) {
        Optional<RangingMeasurement> azimuth = Optional.ofNullable(data.getAzimuth());
        Optional<RangingMeasurement> elevation = Optional.ofNullable(data.getElevation());

        SphericalVector.Annotated in = SphericalVector.fromRadians(
                azimuth.map(RangingMeasurement::getMeasurement).orElse(0.0).floatValue(),
                elevation.map(RangingMeasurement::getMeasurement).orElse(0.0).floatValue(),
                (float) data.getDistance().getMeasurement()
        ).toAnnotated(
                azimuth.isPresent(),
                elevation.isPresent(),
                true
        );

        UwbFilterEngine engine = mFilters.get(
                RangingTechnology.TECHNOLOGIES.get(data.getRangingTechnology()));
        engine.add(in, data.getTimestampMillis());
        SphericalVector.Annotated out = engine.compute(data.getTimestampMillis());
        if (out == null) {
            return;
        }

        RangingData.Builder filteredData = new RangingData.Builder()
                .setRangingTechnology(data.getRangingTechnology())
                .setTimestampMillis(data.getTimestampMillis())
                .setDistance(
                        new RangingMeasurement.Builder()
                                .setMeasurement(out.distance)
                                .setConfidence(data.getDistance().getConfidence())
                                .build()
                );

        if (mUseAoa && data.getRangingTechnology() == RangingTechnology.UWB.getValue()) {
            azimuth.ifPresent(azimuthMeasure -> filteredData.setAzimuth(
                    new RangingMeasurement.Builder()
                            .setMeasurement(out.azimuth)
                            .setConfidence(azimuthMeasure.getConfidence())
                            .build()
            ));
            elevation.ifPresent(elevationMeasure -> filteredData.setElevation(
                    new RangingMeasurement.Builder()
                            .setMeasurement(out.elevation)
                            .setConfidence(elevationMeasure.getConfidence())
                            .build()
            ));
        }

        if (data.hasRssi()) {
            filteredData.setRssi(data.getRssi());
        }

        super.feed(filteredData.build());
    }

    @Override
    protected @NonNull Set<RangingTechnology> getDataSources() {
        return mFilters.keySet();
    }

    @Override
    public void addDataSource(@NonNull RangingTechnology technology) {
        if (!mFilters.containsKey(technology)) {
            mFilters.put(technology, createFilter(technology));
        }
    }

    @Override
    public void removeDataSource(@NonNull RangingTechnology technology) {
        UwbFilterEngine removed = mFilters.remove(technology);
        if (removed != null) {
            removed.close();
        }
    }
}
