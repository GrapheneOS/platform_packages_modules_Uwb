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

import static com.android.server.ranging.common.RangingUtils.InternalReason;

import android.ranging.RangingData;

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.ConfigurationManager.TechnologyConfig;

import java.util.EnumSet;
import java.util.Set;

/**
 * Determines which technologies should be active during a ranging session. This may be done
 * statically before the session starts as is implemented in the {@link StaticRangingEngine}, or an
 * engine may choose to activate or deactivate technologies dynamically by notifying an
 * {@link EngineListener}.
 */
public interface RangingEngine {

    /** Called by a {@link RangingEngine} to start or stop technologies. */
    interface EngineListener {
        void startTechnologies(Set<RangingTechnology> technologies);
        void stopTechnologies(Set<RangingTechnology> technologies, @InternalReason int reason);
        void stopSession();
    }

    /** Get the set of technologies to start ranging with when the session begins. */
    @NonNull EnumSet<RangingTechnology> getTechnologiesToStart();

    /**
     * Start the engine.
     *
     * Noted that the design should ensure that this method be called before
     * {@link #onTechnologyStopped}.
     **/
    void start(Set<TechnologyConfig> configs);

    /** Notify the engine that ranging data has been received. */
    default void onData(@NonNull RangingData data) { }

    /** Notify the engine that a technology has started. */
    default void onTechnologyStarted(@NonNull RangingTechnology technology) { }

    /** Notify the engine that a technology has stopped. */
    default void onTechnologyStopped(
            @NonNull RangingTechnology technology, @InternalReason int reason) { }
}
