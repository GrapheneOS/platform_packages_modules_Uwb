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

import com.android.server.ranging.RangingTechnology;
import com.android.uwb.fusion.UwbFilterEngine;
import com.android.uwb.fusion.math.SphericalVector;

import java.util.EnumMap;
import java.util.Optional;
import java.util.Set;

/**
 * A fusion engine that filters and corrects data from each technology before fusing it.
 */
public class FilteringFusionEngine extends FusionEngine {

    private static final String TAG = FilteringFusionEngine.class.getSimpleName();

    private final EnumMap<RangingTechnology, UwbFilterEngine> mFilters;

    public FilteringFusionEngine(@NonNull DataFuser fuser) {
        super(fuser);
        mFilters = new EnumMap<>(RangingTechnology.class);
    }

    /**
     * Construct a filter engine configured for the provided technology.
     */
    private @NonNull UwbFilterEngine newFilter(@NonNull RangingTechnology unused) {
        // TODO(365631954): Build a properly configured filter depending on the technology.
        return new UwbFilterEngine.Builder().build();
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
            mFilters.put(technology, newFilter(technology));
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
