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

package com.android.server.ranging.engine;

import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_AUTO;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_FUSED;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY;
import static android.ranging.oob.OobInitiatorRangingConfig.RANGING_MODE_HIGH_ACCURACY_PREFERRED;

import android.ranging.oob.OobInitiatorRangingConfig;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.oob.packets.Technology;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The default ranging implementation. Chooses a static set of technologies to start ranging with
 * based on the ranging mode and the device-specific technology ranking. Does not attempt to
 * transition technologies during a session.
 */
public class StaticRangingEngine implements RangingEngine {
    private final EnumSet<RangingTechnology> mTechnologies;
    private final OobInitiatorRangingConfig mConfig;
    private final List<RangingTechnology> mTechnologyRanking;

    public StaticRangingEngine(
            @NonNull Set<Technology> technologies,
            @NonNull OobInitiatorRangingConfig config,
            @NonNull RangingInjector injector
    ) {
        this.mTechnologies = technologies.stream()
                .map(t -> RangingTechnology.fromByte(t.toByte()))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RangingTechnology.class)));
        mConfig = config;
        mTechnologyRanking = injector.getTechnologyRanking();
    }

    public @NonNull EnumSet<RangingTechnology> getTechnologiesToStart() {
        EnumSet<RangingTechnology> toStart = EnumSet.noneOf(RangingTechnology.class);
        switch (mConfig.getRangingMode()) {
            case RANGING_MODE_AUTO, RANGING_MODE_HIGH_ACCURACY_PREFERRED ->
                    mTechnologyRanking
                            .stream()
                            .filter(mTechnologies::contains)
                            .findFirst()
                            .ifPresent(toStart::add);
            case RANGING_MODE_HIGH_ACCURACY -> {
                if (mTechnologies.contains(mTechnologyRanking.getFirst())) {
                    toStart.add(mTechnologyRanking.getFirst());
                }
            }
            case RANGING_MODE_FUSED -> toStart.addAll(mTechnologies);
        }
        return toStart;
    }

    @Override
    public void start(Set<TechnologyConfig> configs) { }
}
