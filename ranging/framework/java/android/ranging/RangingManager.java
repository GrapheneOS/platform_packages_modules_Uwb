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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemService;
import android.content.Context;


/**
 * This class provides a way to perform ranging operations such as querying the
 * device's capabilities and determining the distance and angle between the local device and a
 * remote device.
 *
 * <p>To get a {@link RangingManager}, call the
 * <code>Context.getSystemService(RangingManager.class)</code>.
 */

/**
 * @hide
 */
@SystemService(Context.RANGING_SERVICE)
@FlaggedApi("com.android.ranging.flags.ranging_stack_enabled")
public final class RangingManager {
    private static final String TAG = "RangingManager";

    private final Context mContext;
    private final IRangingAdapter mRangingAdapter;

    /**
     * @hide
     */
    public RangingManager(@NonNull Context context, @NonNull IRangingAdapter adapter) {
        mContext = context;
        mRangingAdapter = adapter;
    }

    /**
     * @hide
     */

    @NonNull
    @FlaggedApi("com.android.ranging.flags.ranging_stack_enabled")
    public RangingSession createRangingSession() {
        return new RangingSession();
    }
}
