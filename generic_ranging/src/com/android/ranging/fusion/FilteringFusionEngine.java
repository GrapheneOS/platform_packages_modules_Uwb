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

package com.android.ranging.fusion;

import androidx.annotation.NonNull;

import com.android.ranging.RangingData;
import com.android.ranging.RangingTechnology;
import com.android.uwb.fusion.UwbFilterEngine;
import com.android.uwb.fusion.math.SphericalVector;

import java.util.EnumMap;
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
        if (data.getTechnology().isEmpty()) {
            return;
        }

        SphericalVector.Annotated in = SphericalVector.fromRadians(
                (float) data.getAzimuthRadians().orElse(0.0),
                (float) data.getElevationRadians().orElse(0.0),
                (float) data.getRangeMeters()
        ).toAnnotated(
                data.getAzimuthRadians().isPresent(),
                data.getElevationRadians().isPresent(),
                true
        );

        UwbFilterEngine engine = mFilters.get(data.getTechnology().get());
        engine.add(in, data.getTimestamp().toMillis());
        SphericalVector.Annotated out = engine.compute(data.getTimestamp().toMillis());
        if (out == null) {
            return;
        }

        RangingData.Builder filteredData = RangingData.Builder.fromBuilt(data);
        filteredData.setRangeDistance(out.distance);
        if (data.getAzimuthRadians().isPresent()) {
            filteredData.setAzimuthRadians(out.azimuth);
        }
        if (data.getElevationRadians().isPresent()) {
            filteredData.setElevationRadians(out.elevation);
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
