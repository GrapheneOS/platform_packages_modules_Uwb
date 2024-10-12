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

import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemService;
import android.content.AttributionSource;
import android.content.Context;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;


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
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingManager {
    private static final String TAG = "RangingManager";

    private final Context mContext;
    private final IRangingAdapter mRangingAdapter;

    private final RangingSessionManager mRangingSessionManager;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RangingTechnology.UWB,
            RangingTechnology.BT_CS,
            RangingTechnology.WIFI_RTT,
            RangingTechnology.BLE_RSSI,
    })
    public @interface RangingTechnology {
        int UWB = 0;
        int BT_CS = 1;
        int WIFI_RTT = 2;
        int BLE_RSSI = 3;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            /* Ranging technology is not supported on this device. */
            RangingTechnologyAvailability.NOT_SUPPORTED,
            /* Ranging technology is disabled. */
            RangingTechnologyAvailability.DISABLED_USER,
            /* Ranging technology disabled due to regulation. */
            RangingTechnologyAvailability.DISABLED_REGULATORY,
            /* Ranging technology is enabled. */
            RangingTechnologyAvailability.ENABLED,
    })
    public @interface RangingTechnologyAvailability {
        int NOT_SUPPORTED = 0;
        int DISABLED_USER = 1;
        int DISABLED_REGULATORY = 2;
        int ENABLED = 3;
    }


    /**
     * @hide
     */
    public RangingManager(@NonNull Context context, @NonNull IRangingAdapter adapter) {
        mContext = context;
        mRangingAdapter = adapter;
        mRangingSessionManager = new RangingSessionManager(adapter);
    }

    /**
     * @hide
     */

    /**
     * Gets all the ranging capabilities respective to the ranging
     * technology if enabled.
     */
    @NonNull
    public void getRangingCapabilities(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull RangingCapabilitiesListener listener) {
    }

    @Nullable
    @FlaggedApi("com.android.ranging.flags.ranging_stack_enabled")
    public RangingSession createRangingSession(RangingSession.Callback callback,
            Executor executor) {
        return createRangingSessionInternal(mContext.getAttributionSource(), callback, executor);
    }

    private RangingSession createRangingSessionInternal(AttributionSource attributionSource,
            RangingSession.Callback callback, Executor executor) {
        return mRangingSessionManager.createRangingSessionInstance(attributionSource, callback,
                executor);
    }
}
