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

import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingSession;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

/** Utility class to help convert results from system API to GMSCore API */
@RequiresApi(api = VERSION_CODES.S)
final class Conversions {

    private static RangingMeasurement createMeasurement(double value, double confidence) {
        @RangingMeasurement.Confidence int confidenceLevel;
        if (confidence > 0.9) {
            confidenceLevel = RangingMeasurement.CONFIDENCE_HIGH;
        } else if (confidence > 0.5) {
            confidenceLevel = RangingMeasurement.CONFIDENCE_MEDIUM;
        } else {
            confidenceLevel = RangingMeasurement.CONFIDENCE_LOW;
        }
        return new RangingMeasurement(confidenceLevel, (float) value);
    }

    /** Convert system API's {@link android.uwb.RangingMeasurement} to {@link RangingPosition} */
    @Nullable
    static RangingPosition convertToPosition(android.uwb.RangingMeasurement measurement) {
        DistanceMeasurement distanceMeasurement = measurement.getDistanceMeasurement();
        if (distanceMeasurement == null) {
            return null;
        }
        RangingMeasurement distance =
                createMeasurement(
                        distanceMeasurement.getMeters(), distanceMeasurement.getConfidenceLevel());

        AngleOfArrivalMeasurement aoaMeasurement = measurement.getAngleOfArrivalMeasurement();

        RangingMeasurement azimuth = null;
        RangingMeasurement altitude = null;
        if (aoaMeasurement != null) {
            AngleMeasurement azimuthMeasurement = aoaMeasurement.getAzimuth();
            if (azimuthMeasurement != null) {
                azimuth =
                        createMeasurement(
                                Math.toDegrees(azimuthMeasurement.getRadians()),
                                azimuthMeasurement.getConfidenceLevel());
            }
            AngleMeasurement altitudeMeasurement = aoaMeasurement.getAltitude();
            if (altitudeMeasurement != null) {
                altitude =
                        createMeasurement(
                                Math.toDegrees(altitudeMeasurement.getRadians()),
                                altitudeMeasurement.getConfidenceLevel());
            }
        }
        if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            return new RangingPosition(
                    distance,
                    azimuth,
                    altitude,
                    measurement.getElapsedRealtimeNanos(),
                    measurement.getRssiDbm());
        }
        return new RangingPosition(
                distance, azimuth, altitude, measurement.getElapsedRealtimeNanos());
    }

    @RangingSessionCallback.RangingSuspendedReason
    static int convertReason(int reason) {
        if (reason == RangingSession.Callback.REASON_BAD_PARAMETERS) {
            return RangingSessionCallback.REASON_WRONG_PARAMETERS;
        }

        if (reason == RangingSession.Callback.REASON_LOCAL_REQUEST) {
            return RangingSessionCallback.REASON_STOP_RANGING_CALLED;
        }

        if (reason == RangingSession.Callback.REASON_REMOTE_REQUEST) {
            return RangingSessionCallback.REASON_STOPPED_BY_PEER;
        }

        if (reason == RangingSession.Callback.REASON_MAX_SESSIONS_REACHED) {
            return RangingSessionCallback.REASON_FAILED_TO_START;
        }

        if (reason == RangingSession.Callback.REASON_PROTOCOL_SPECIFIC_ERROR) {
            return RangingSessionCallback.REASON_MAX_RANGING_ROUND_RETRY_REACHED;
        }

        return RangingSessionCallback.REASON_UNKNOWN;
    }

    static android.uwb.UwbAddress convertUwbAddress(UwbAddress address) {
        return android.uwb.UwbAddress.fromBytes(address.toBytes());
    }

    static List<android.uwb.UwbAddress> convertUwbAddressList(UwbAddress[] addressList) {
        List<android.uwb.UwbAddress> list = new ArrayList<>();
        for (UwbAddress address : addressList) {
            list.add(convertUwbAddress(address));
        }
        return list;
    }

    private Conversions() {}
}
