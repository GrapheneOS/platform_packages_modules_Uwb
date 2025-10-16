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

import androidx.annotation.NonNull;

import com.android.server.ranging.RangingData;
import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.common.RangingUtils;

import java.util.EnumSet;

public interface RangingEngine {

    @NonNull EnumSet<RangingTechnology> getTechnologiesToStart();

    default void onTechnologyStarted(@NonNull RangingTechnology technology) { }

    default void onTechnologyStopped(
            @NonNull RangingTechnology technology, @RangingUtils.InternalReason int reason) { }

    default void onData(RangingData data) { }
}
