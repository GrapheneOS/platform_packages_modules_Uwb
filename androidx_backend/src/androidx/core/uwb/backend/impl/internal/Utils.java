/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.core.uwb.backend.impl.internal;

import android.annotation.IntDef;
import android.util.ArrayMap;

import com.google.common.collect.ImmutableList;

import java.util.Map;

/** Definitions that are common for all classes. */
public final class Utils {

    public static final String TAG = "UwbBackend";

    /** Supported Ranging configurations. */
    @IntDef({
        CONFIG_UNICAST_DS_TWR,
        CONFIG_ID_2,
        CONFIG_ID_3,
        CONFIG_ID_4,
        CONFIG_ID_5,
        CONFIG_ID_6,
        CONFIG_ID_7,
        CONFIG_ID_8,
    })
    public @interface UwbConfigId {}

    /**
     * FiRa-defined unicast {@code STATIC STS DS-TWR} ranging, deferred mode, ranging interval 240
     * ms.
     *
     * <p>Typical use case: device tracking tags.
     */
    public static final int CONFIG_UNICAST_DS_TWR = 1;

    public static final int CONFIG_ID_2 = 2;

    /** Same as {@code CONFIG_ID_1}, except Angle-of-arrival (AoA) data is not reported. */
    public static final int CONFIG_ID_3 = 3;

    /** Same as {@code CONFIG_ID_1}, except P-STS security mode is enabled. */
    public static final int CONFIG_ID_4 = 4;

    /** Same as {@code CONFIG_ID_2}, except P-STS security mode is enabled. */
    public static final int CONFIG_ID_5 = 5;

    /** Same as {@code CONFIG_ID_3}, except P-STS security mode is enabled. */
    public static final int CONFIG_ID_6 = 6;

    /** Same as {@code CONFIG_ID_2}, except P-STS individual controlee key mode is enabled. */
    public static final int CONFIG_ID_7 = 7;

    /** FiRa- defined Downlink-TDoA for DT-Tag ranging */
    public static final int CONFIG_ID_8 = 8;

    @IntDef({
        INFREQUENT,
        NORMAL,
        FAST,
    })
    public @interface RangingUpdateRate {}

    /** Reports ranging data in a couple of seconds (default to 4 seconds). */
    public static final int INFREQUENT = 1;

    /**
     * Reports ranging data in hundreds of milliseconds (depending on the ranging interval setting
     * of the config)
     */
    public static final int NORMAL = 2;

    /** Reports ranging data as fast as possible (depending on the device's capability). */
    public static final int FAST = 3;

    /**
     * FiRa-defined one-to-many {@code STATIC STS DS-TWR} ranging, deferred mode, ranging interval
     * 200 ms
     *
     * <p>Typical use case: smart phone interacts with many smart devices.
     */
    public static final int VENDOR_ID_SIZE = 2;

    public static final int STATIC_STS_IV_SIZE = 6;
    public static final int STATIC_STS_SESSION_KEY_INFO_SIZE = VENDOR_ID_SIZE + STATIC_STS_IV_SIZE;

    // A map that stores the ranging interval values. The key is config ID.
    private static final Map<Integer, RangingTimingParams> CONFIG_RANGING_INTERVAL_MAP =
            new ArrayMap<>();

    /** Sets the dafault {@link RangingTimingParams} for given config ID. */
    public static void setRangingTimingParams(
            @UwbConfigId int configId, RangingTimingParams params) {
        CONFIG_RANGING_INTERVAL_MAP.put(configId, params);
    }

    /** Gets the dafault {@link RangingTimingParams} of given config ID. */
    public static RangingTimingParams getRangingTimingParams(@UwbConfigId int configId) {
        return CONFIG_RANGING_INTERVAL_MAP.get(configId);
    }

    @IntDef({
        STATUS_OK,
        STATUS_ERROR,
        INVALID_API_CALL,
        RANGING_ALREADY_STARTED,
        MISSING_PERMISSION_UWB_RANGING,
        UWB_SYSTEM_CALLBACK_FAILURE
    })
    public @interface UwbStatusCodes {}

    // IMPORTANT NOTE: The codes referenced in this file are used on both the client and service
    // side, and must not be modified after launch. It is fine to add new codes, but previously
    // existing codes must be left unmodified.

    // Common status codes that may be used by a variety of actions.

    /** The operation was successful. */
    public static final int STATUS_OK = 0; // 0

    /** The operation failed, without any more information. */
    public static final int STATUS_ERROR = 1; // 13

    /** The call is not valid. For example, get Complex Channel for the controlee. */
    public static final int INVALID_API_CALL = 2;

    /** The ranging is already started, this is a duplicated request. */
    public static final int RANGING_ALREADY_STARTED = 3;

    /** Can't start ranging because the UWB_RANGING permission is not granted. */
    public static final int MISSING_PERMISSION_UWB_RANGING = 4;

    /**
     * Unusual failures happened in UWB system callback, such as stopping ranging or removing a
     * known controlee failed.
     */
    public static final int UWB_SYSTEM_CALLBACK_FAILURE = 5;

    static {
        setRangingTimingParams(
                CONFIG_UNICAST_DS_TWR,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 240,
                        /* rangingIntervalFast= */ 48,
                        /* rangingIntervalInfrequent= */ 2400,
                        /* slotPerRangingRound= */ 6,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_2,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 200,
                        /* rangingIntervalFast= */ 40,
                        /* rangingIntervalInfrequent= */ 2000,
                        /* slotPerRangingRound= */ 20,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_3,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 200,
                        /* rangingIntervalFast= */ 40,
                        /* rangingIntervalInfrequent= */ 2000,
                        /* slotPerRangingRound= */ 20,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_4,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 240,
                        /* rangingIntervalFast= */ 48,
                        /* rangingIntervalInfrequent= */ 2400,
                        /* slotPerRangingRound= */ 6,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_5,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 200,
                        /* rangingIntervalFast= */ 40,
                        /* rangingIntervalInfrequent= */ 2000,
                        /* slotPerRangingRound= */ 20,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_6,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 200,
                        /* rangingIntervalFast= */ 40,
                        /* rangingIntervalInfrequent= */ 2000,
                        /* slotPerRangingRound= */ 20,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_7,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 200,
                        /* rangingIntervalFast= */ 40,
                        /* rangingIntervalInfrequent= */ 2000,
                        /* slotPerRangingRound= */ 20,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));

        setRangingTimingParams(
                CONFIG_ID_8,
                new RangingTimingParams(
                        /* rangingIntervalNormal= */ 200,
                        /* rangingIntervalFast= */ 40,
                        /* rangingIntervalInfrequent= */ 2000,
                        /* slotPerRangingRound= */ 20,
                        /* slotDurationRstu= */ 2400,
                        /* initiationTimeMs= */ 0,
                        /* hoppingEnabled= */ true));
    }

    public static int channelForTesting = 9;
    public static int preambleIndexForTesting = 11;

    // Channels defined in FiRa Spec
    public static final ImmutableList<Integer> SUPPORTED_CHANNELS =
            ImmutableList.of(5, 6, 8, 9, 10, 12, 13, 14);

    // Preamble index used by BPRF (base pulse repetition frequency) mode. BPRF supports bitrate up
    // to 6Mb/s, which is good enough for ranging purpose. Eventually, HPRF (high pulse repetition
    // frequency) support will be added.
    public static final ImmutableList<Integer> SUPPORTED_BPRF_PREAMBLE_INDEX =
            ImmutableList.of(9, 10, 11, 12);

    private Utils() {}
}
