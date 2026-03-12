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

package com.android.ranging.uwb.backend.internal;

import static android.uwb.UwbManager.AdapterStateCallback.STATE_CHANGED_REASON_SYSTEM_POLICY;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_CHANGED_REASON_SYSTEM_REGULATION;

import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingSession;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.fira.FiraOnControleeAddRemoveParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.dltdoa.DlTDoAMeasurement;

import java.lang.Math;
import java.util.ArrayList;
import java.util.List;

/** Utility class to help convert results from system API to GMSCore API */
@RequiresApi(api = VERSION_CODES.S)
final class Conversions {

    private static RangingMeasurement createMeasurement(double value, double confidence,
            boolean valid) {
        @RangingMeasurement.Confidence int confidenceLevel;
        if (confidence > 0.9) {
            confidenceLevel = RangingMeasurement.CONFIDENCE_HIGH;
        } else if (confidence > 0.5) {
            confidenceLevel = RangingMeasurement.CONFIDENCE_MEDIUM;
        } else {
            confidenceLevel = RangingMeasurement.CONFIDENCE_LOW;
        }
        return new RangingMeasurement(confidenceLevel, (float) value, valid);
    }

    public static boolean isDlTdoaMeasurement(android.uwb.RangingMeasurement measurement) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        try {
            return com.google.uwb.support.dltdoa.DlTDoAMeasurement.isDlTDoAMeasurement(
                    measurement.getRangingMeasurementMetadata());
        } catch (NoSuchMethodError e) {
            return false;
        }
    }

    // copied from com.android.server.uwb.util.UwbUtil.radianTodegree()
    private static float radianTodegree(double angleInRadians) {
        return (float) ((angleInRadians) * 180 / Math.PI);
    }

    // Gather all necessary data and put them altogether into DlTdoaMeasurement for the
    // RangingSessionCallback.onDlTdoaRangingResult() callback.
    private static DlTdoaMeasurement convertToInternalDlTdoaMeasurement(
            android.uwb.RangingMeasurement measurement) {
        if (Build.VERSION.SDK_INT < VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return null;
        }

        // Only process the measurement if the status is successful.
        if (measurement.getStatus() != FiraParams.STATUS_CODE_OK) {
            return null;
        }

        // Get the support library DL-TDoA measurement from the metadata bundle.
        com.google.uwb.support.dltdoa.DlTDoAMeasurement supportDlTdoaMeasurement =
                com.google.uwb.support.dltdoa.DlTDoAMeasurement.fromBundle(
                        measurement.getRangingMeasurementMetadata());

        // Workaround for invalid empty measurement reported from UWB stack. If the message control
        // field is 0, the measurement is no use for localization. It is safe to drop the
        // measurement entirely.
        if (supportDlTdoaMeasurement.getMessageControl() == 0) {
            return null;
        }

        // Get angle of arrival data from the measurement.
        float azimuth = Float.NaN;
        int azimuthFom = 0;
        float elevation = Float.NaN;
        int elevationFom = 0;
        AngleOfArrivalMeasurement aoaMeasurement = measurement.getAngleOfArrivalMeasurement();
        if (aoaMeasurement != null) {
            AngleMeasurement azimuthMeasurement = aoaMeasurement.getAzimuth();
            if (azimuthMeasurement != null) {
                azimuth = radianTodegree(azimuthMeasurement.getRadians());
                azimuthFom = (int) (azimuthMeasurement.getConfidenceLevel() * 100);
            }
            AngleMeasurement altitudeMeasurement = aoaMeasurement.getAltitude();
            if (altitudeMeasurement != null) {
                elevation = radianTodegree(altitudeMeasurement.getRadians());
                elevationFom = (int) (altitudeMeasurement.getConfidenceLevel() * 100);
            }
        }

        // Support DlTdoaMeasurement for both measurement v1 and v2
        int measurementVersion = DlTdoaMeasurement.MEASUREMENT_VERSION_1;
        if (supportDlTdoaMeasurement.getMeasurementVersion()
                == DlTDoAMeasurement.MEASUREMENT_VERSION_2) {
            measurementVersion = DlTdoaMeasurement.MEASUREMENT_VERSION_2;
        }

        int superclusterId = supportDlTdoaMeasurement.getSuperclusterId();

        return new DlTdoaMeasurement(
                measurementVersion,
                supportDlTdoaMeasurement.getMessageType(),
                supportDlTdoaMeasurement.getMessageControl(),
                supportDlTdoaMeasurement.getBlockIndex(),
                supportDlTdoaMeasurement.getRoundIndex(),
                supportDlTdoaMeasurement.getNLoS(),
                azimuth,
                azimuthFom,
                elevation,
                elevationFom,
                measurement.getRssiDbm(),
                supportDlTdoaMeasurement.getTxTimestamp(),
                supportDlTdoaMeasurement.getRxTimestamp(),
                supportDlTdoaMeasurement.getTxTimestampV2(),
                supportDlTdoaMeasurement.getRxTimestampV2(),
                supportDlTdoaMeasurement.getAnchorCfo(),
                supportDlTdoaMeasurement.getCfo(),
                supportDlTdoaMeasurement.getInitiatorReplyTime(),
                supportDlTdoaMeasurement.getResponderReplyTime(),
                supportDlTdoaMeasurement.getInitiatorResponderTof(),
                supportDlTdoaMeasurement.getAnchorLocation(),
                supportDlTdoaMeasurement.getActiveRangingRounds(),
                superclusterId);
    }

    /** Convert system API's {@link android.uwb.RangingMeasurement} to {@link RangingPosition} */
    @Nullable
    static RangingPosition convertToPosition(android.uwb.RangingMeasurement measurement) {
        RangingMeasurement distance;
        DlTdoaMeasurement dlTdoaMeasurement = null;
        if (isDlTdoaMeasurement(measurement)) {
            // No distance measurement for DL-TDoA, make it invalid.
            distance = createMeasurement(0.0, 0.0, false);
            // Convert to backend internal DL-TDoA measurement.
            dlTdoaMeasurement = convertToInternalDlTdoaMeasurement(measurement);
        } else {
            DistanceMeasurement distanceMeasurement = measurement.getDistanceMeasurement();
            if (distanceMeasurement == null) {
                return null;
            }
            distance = createMeasurement(
                    distanceMeasurement.getMeters(),
                    distanceMeasurement.getConfidenceLevel(),
                    true);
        }
        AngleOfArrivalMeasurement aoaMeasurement = measurement.getAngleOfArrivalMeasurement();

        RangingMeasurement azimuth = null;
        RangingMeasurement altitude = null;
        if (aoaMeasurement != null) {
            AngleMeasurement azimuthMeasurement = aoaMeasurement.getAzimuth();
            if (azimuthMeasurement != null && !isMeasurementAllZero(azimuthMeasurement)) {
                azimuth =
                        createMeasurement(
                                azimuthMeasurement.getRadians(),
                                azimuthMeasurement.getConfidenceLevel(),
                                true);
            }
            AngleMeasurement altitudeMeasurement = aoaMeasurement.getAltitude();
            if (altitudeMeasurement != null && !isMeasurementAllZero(altitudeMeasurement)) {
                altitude =
                        createMeasurement(
                                altitudeMeasurement.getRadians(),
                                altitudeMeasurement.getConfidenceLevel(),
                                true);
            }
        }
        if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
            return new RangingPosition(
                    distance,
                    azimuth,
                    altitude,
                    dlTdoaMeasurement,
                    measurement.getElapsedRealtimeNanos(),
                    measurement.getRssiDbm(),
                    measurement.getLineOfSight());
        }
        return new RangingPosition(
                distance, azimuth, altitude, measurement.getElapsedRealtimeNanos());
    }

    private static boolean isMeasurementAllZero(AngleMeasurement measurement) {
        return measurement.getRadians() == 0
                && measurement.getErrorRadians() == 0
                && measurement.getConfidenceLevel() == 0;
    }

    @RangingSessionCallback.RangingSuspendedReason
    static int toRangingSuspendedReason(int reason) {
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

        if (reason == RangingSession.Callback.REASON_SYSTEM_POLICY) {
            return RangingSessionCallback.REASON_SYSTEM_POLICY;
        }

        return RangingSessionCallback.REASON_UNKNOWN;
    }

    @RangingSessionCallback.PeerDisconnectedReason
    static int toPeerDisconnectedReason(@FiraOnControleeAddRemoveParams.Reason int reason) {
        switch (reason) {
            case FiraOnControleeAddRemoveParams.Reason.LOST_CONNECTION:
                return RangingSessionCallback.PeerDisconnectedReason.SYSTEM_POLICY;
            default:
                return RangingSessionCallback.PeerDisconnectedReason.UNKNOWN;
        }
    }

    @UwbAvailabilityCallback.UwbStateChangeReason
    static int convertAdapterStateReason(int reason) {
        return switch (reason) {
            case STATE_CHANGED_REASON_SYSTEM_POLICY -> UwbAvailabilityCallback.REASON_SYSTEM_POLICY;
            case STATE_CHANGED_REASON_SYSTEM_REGULATION ->
                    UwbAvailabilityCallback.REASON_COUNTRY_CODE_ERROR;
            default -> UwbAvailabilityCallback.REASON_UNKNOWN;
        };
    }
    static android.uwb.UwbAddress convertUwbAddress(UwbAddress address, boolean reverseMacAddress) {
        return reverseMacAddress
                ? android.uwb.UwbAddress.fromBytes(getReverseBytes(address.toBytes()))
                : android.uwb.UwbAddress.fromBytes(address.toBytes());
    }

    static List<android.uwb.UwbAddress> convertUwbAddressList(
            UwbAddress[] addressList, boolean reverseMacAddress) {
        List<android.uwb.UwbAddress> list = new ArrayList<>();
        for (UwbAddress address : addressList) {
            list.add(convertUwbAddress(address, reverseMacAddress));
        }
        return list;
    }

    static byte[] getReverseBytes(byte[] data) {
        byte[] buffer = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            buffer[i] = data[data.length - 1 - i];
        }
        return buffer;
    }

    private Conversions() {}
}
