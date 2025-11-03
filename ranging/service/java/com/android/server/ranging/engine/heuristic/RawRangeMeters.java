/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.engine.heuristic;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingData;

import java.util.concurrent.Executor;

/** A {@link RangeHeuristic} that simply passes through the range in meters. */
public class RawRangeMeters extends RangeHeuristic {

    public RawRangeMeters(Executor executor) {
        super(executor);
    }

    @Override
    public void onData(@NonNull RangingData data) {
        onHeuristicUpdated(data.getRangeMeters());
    }
}
