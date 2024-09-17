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

import java.util.Optional;
import java.util.Set;

public class DataFusers {
    /**
     * A data fuser that passes through all provided data as fused data.
     */
    public static class PassthroughDataFuser implements FusionEngine.DataFuser {

        @Override
        public Optional<RangingData> fuse(
                @NonNull RangingData data, final @NonNull Set<RangingTechnology> sources
        ) {
            return Optional.of(data);
        }
    }

    /**
     * A data fuser that prefers a particular technology according to the following rules:
     * <ul>
     *     <li> If the preferred technology is active, all data it produces is produced by the
     *     engine. All data from any other technology is ignored.
     *     <li> If the preferred technology is inactive, report all data received from any
     *     technology.
     * </ul>
     */
    public static class PreferentialDataFuser implements FusionEngine.DataFuser {
        private final RangingTechnology mPreferred;

        /**
         * @param preferred technology. Data from other technologies will be ignored while this one
         *                  is active.
         */
        public PreferentialDataFuser(@NonNull RangingTechnology preferred) {
            mPreferred = preferred;
        }

        @Override
        public Optional<RangingData> fuse(
                @NonNull RangingData data, final @NonNull Set<RangingTechnology> sources
        ) {
            if (sources.contains(mPreferred)) {
                if (data.getTechnology().isPresent() && mPreferred == data.getTechnology().get()) {
                    return Optional.of(data);
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.of(data);
            }
        }
    }
}
