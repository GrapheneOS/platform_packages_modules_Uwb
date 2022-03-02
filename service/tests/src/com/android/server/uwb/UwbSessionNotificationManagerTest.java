/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb;

import static com.android.server.uwb.data.UwbUciConstants.RANGING_MEASUREMENT_TYPE_TWO_WAY;
import static com.android.server.uwb.util.UwbUtil.convertFloatToQFormat;
import static com.android.server.uwb.util.UwbUtil.degreeToRadian;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;
import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.SessionHandle;
import android.uwb.UwbAddress;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.data.UwbRangingData;
import com.android.server.uwb.data.UwbTwoWayMeasurement;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.params.TlvUtil;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.server.uwb.UwbSettingsStore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbSessionNotificationManagerTest {
    private static final long TEST_SEQ_COUNTER = 5;
    private static final long TEST_SESSION_ID = 7;
    private static final int TEST_RCR_INDICATION = 7;
    private static final long TEST_CURR_RANGING_INTERVAL = 100;
    private static final int TEST_RANGING_MEASURES_TYPE = RANGING_MEASUREMENT_TYPE_TWO_WAY;
    private static final int TEST_MAC_ADDRESS_MODE = 1;
    private static final byte[] TEST_MAC_ADDRESS = {0x1, 0x3};
    private static final int TEST_STATUS = FiraParams.STATUS_CODE_OK;
    private static final int TEST_LOS = 0;
    private static final int TEST_DISTANCE = 101;
    private static final float TEST_AOA_AZIMUTH = 67;
    private static final int TEST_AOA_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_ELEVATION = 37;
    private static final int TEST_AOA_ELEVATION_FOM = 90;
    private static final float TEST_AOA_DEST_AZIMUTH = 67;
    private static final int TEST_AOA_DEST_AZIMUTH_FOM = 50;
    private static final float TEST_AOA_DEST_ELEVATION = 37;
    private static final int TEST_AOA_DEST_ELEVATION_FOM = 90;
    private static final int TEST_SLOT_IDX = 10;
    private static final long TEST_ELAPSED_NANOS = 100L;

    @Mock private UwbInjector mUwbInjector;
    @Mock private UwbSessionManager.UwbSession mUwbSession;
    @Mock private SessionHandle mSessionHandle;
    @Mock private IUwbRangingCallbacks mIUwbRangingCallbacks;
    @Mock private FiraOpenSessionParams mFiraParams;

    private UwbSessionNotificationManager mUwbSessionNotificationManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mUwbSession.getSessionHandle()).thenReturn(mSessionHandle);
        when(mUwbSession.getIUwbRangingCallbacks()).thenReturn(mIUwbRangingCallbacks);
        when(mUwbSession.getProtocolName()).thenReturn(FiraParams.PROTOCOL_NAME);
        when(mUwbSession.getParams()).thenReturn(mFiraParams);
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasResultReportPhase()).thenReturn(false);
        when(mUwbInjector.getElapsedSinceBootNanos()).thenReturn(TEST_ELAPSED_NANOS);
        mUwbSessionNotificationManager = new UwbSessionNotificationManager(mUwbInjector);
    }

    /**
     * Called after each testGG
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
    }

    @Test
    public void testOnRangingResultWithAoa() throws Exception {
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                generateRangingDataAndRangingReport(true, true, false, false);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }

    @Test
    public void testOnRangingResultWithNoAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_NO_AOA_REPORT);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                generateRangingDataAndRangingReport(false, false, false, false);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }

    @Test
    public void testOnRangingResultWithNoAoaElevation() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_AZIMUTH_ONLY);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                generateRangingDataAndRangingReport(true, false, false, false);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }

    @Test
    public void testOnRangingResultWithNoAoaAzimuth() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS_ELEVATION_ONLY);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                generateRangingDataAndRangingReport(false, true, false, false);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }
  
    @Test
    public void testOnRangingResultWithAoaAndDestAoa() throws Exception {
        when(mFiraParams.getAoaResultRequest()).thenReturn(
                FiraParams.AOA_RESULT_REQUEST_MODE_REQ_AOA_RESULTS);
        when(mFiraParams.hasResultReportPhase()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalAzimuthReport()).thenReturn(true);
        when(mFiraParams.hasAngleOfArrivalElevationReport()).thenReturn(true);
        Pair<UwbRangingData, RangingReport> testRangingDataAndRangingReport =
                generateRangingDataAndRangingReport(true, true, true, true);
        mUwbSessionNotificationManager.onRangingResult(
                mUwbSession, testRangingDataAndRangingReport.first);
        verify(mIUwbRangingCallbacks).onRangingResult(
                mSessionHandle, testRangingDataAndRangingReport.second);
    }


    @Test
    public void testOnRangingOpened() throws Exception {
        mUwbSessionNotificationManager.onRangingOpened(mUwbSession);

        verify(mIUwbRangingCallbacks).onRangingOpened(mSessionHandle);
    }

    @Test
    public void testOnRangingOpenFailed() throws Exception {
        int status = UwbUciConstants.STATUS_CODE_ERROR_MAX_SESSIONS_EXCEEDED;
        mUwbSessionNotificationManager.onRangingOpenFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingOpenFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertStatusCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingStarted() throws Exception {
        mUwbSessionNotificationManager.onRangingStarted(mUwbSession, mUwbSession.getParams());

        verify(mIUwbRangingCallbacks).onRangingStarted(mSessionHandle,
                mUwbSession.getParams().toBundle());
    }

    @Test
    public void testOnRangingStartFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_PARAM;
        mUwbSessionNotificationManager.onRangingStartFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStartFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertStatusCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingStopped() throws Exception {
        int reason = UwbUciConstants.REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS;
        mUwbSessionNotificationManager.onRangingStopped(mUwbSession, reason);

        verify(mIUwbRangingCallbacks).onRangingStopped(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertReasonCode(reason)),
                argThat(p-> p.getInt("reason_code") == reason));
    }

    @Test
    public void testORangingStopFailed() throws Exception {
        int status = UwbUciConstants.STATUS_CODE_INVALID_RANGE;
        mUwbSessionNotificationManager.onRangingStopFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingStopFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertStatusCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingReconfigured() throws Exception {
        mUwbSessionNotificationManager.onRangingReconfigured(mUwbSession);

        verify(mIUwbRangingCallbacks).onRangingReconfigured(eq(mSessionHandle), any());
    }

    @Test
    public void testOnRangingReconfigureFailed() throws Exception {
        int status =  UwbUciConstants.STATUS_CODE_INVALID_MESSAGE_SIZE;
        mUwbSessionNotificationManager.onRangingReconfigureFailed(mUwbSession, status);

        verify(mIUwbRangingCallbacks).onRangingReconfigureFailed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertStatusCode(status)),
                argThat(p -> (p.getInt("status_code")) == status));
    }

    @Test
    public void testOnRangingClosed() throws Exception {
        int reason = UwbUciConstants.REASON_ERROR_SLOT_LENGTH_NOT_SUPPORTED;
        mUwbSessionNotificationManager.onRangingClosed(mUwbSession, reason);

        verify(mIUwbRangingCallbacks).onRangingClosed(eq(mSessionHandle),
                eq(UwbSessionNotificationHelper.convertReasonCode(reason)),
                argThat(p-> p.getInt("reason_code") == reason));
    }

    // Helper method to generate a UwbRangingData instance and corresponding RangingMeasurement
    private Pair<UwbRangingData, RangingReport> generateRangingDataAndRangingReport(
            boolean isAoaAzimuthEnabled, boolean isAoaElevationEnabled,
            boolean isDestAoaAzimuthEnabled, boolean isDestAoaElevationEnabled) {
        final int noOfRangingMeasures = 1;
        final UwbTwoWayMeasurement[] uwbTwoWayMeasurements =
                new UwbTwoWayMeasurement[noOfRangingMeasures];
        uwbTwoWayMeasurements[0] = new UwbTwoWayMeasurement(TEST_MAC_ADDRESS, TEST_STATUS, TEST_LOS,
                TEST_DISTANCE, convertFloatToQFormat(TEST_AOA_AZIMUTH, 9, 7),
                TEST_AOA_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_ELEVATION, 9, 7),
                TEST_AOA_ELEVATION_FOM, convertFloatToQFormat(TEST_AOA_DEST_AZIMUTH, 9, 7),
                TEST_AOA_DEST_AZIMUTH_FOM, convertFloatToQFormat(TEST_AOA_DEST_ELEVATION, 9, 7),
                TEST_AOA_DEST_ELEVATION_FOM, TEST_SLOT_IDX);
        UwbRangingData uwbRangingData = new UwbRangingData(TEST_SEQ_COUNTER, TEST_SESSION_ID,
                TEST_RCR_INDICATION, TEST_CURR_RANGING_INTERVAL, TEST_RANGING_MEASURES_TYPE,
                TEST_MAC_ADDRESS_MODE, noOfRangingMeasures, uwbTwoWayMeasurements);

        AngleOfArrivalMeasurement aoaMeasurement = null;
        AngleOfArrivalMeasurement aoaDestMeasurement = null;
        if (isAoaAzimuthEnabled || isAoaElevationEnabled) {
            AngleMeasurement aoaAzimuth = null;
            AngleMeasurement aoaElevation = null;
            if (isAoaAzimuthEnabled) {
                aoaAzimuth =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_AZIMUTH), 0,
                                TEST_AOA_AZIMUTH_FOM / (double) 100);
            }
            if (isAoaElevationEnabled) {
                aoaElevation =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_ELEVATION), 0,
                                TEST_AOA_ELEVATION_FOM / (double) 100);
            }
            aoaMeasurement = new AngleOfArrivalMeasurement.Builder(aoaAzimuth)
                    .setAltitude(aoaElevation)
                    .build();
        }
        if (isDestAoaAzimuthEnabled || isDestAoaElevationEnabled) {
            AngleMeasurement aoaDestAzimuth = null;
            AngleMeasurement aoaDestElevation = null;
            if (isDestAoaAzimuthEnabled) {
                aoaDestAzimuth =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_DEST_AZIMUTH), 0,
                                TEST_AOA_DEST_AZIMUTH_FOM / (double) 100);
            }
            if (isDestAoaElevationEnabled) {
                aoaDestElevation =
                        new AngleMeasurement(
                                degreeToRadian(TEST_AOA_DEST_ELEVATION), 0,
                                TEST_AOA_DEST_ELEVATION_FOM / (double) 100);
            }
            aoaDestMeasurement = new AngleOfArrivalMeasurement.Builder(aoaDestAzimuth)
                    .setAltitude(aoaDestElevation)
                    .build();
        }
        RangingMeasurement rangingMeasurement = new RangingMeasurement.Builder()
                .setRemoteDeviceAddress(UwbAddress.fromBytes(
                        TlvUtil.getReverseBytes(TEST_MAC_ADDRESS)))
                .setStatus(TEST_STATUS)
                .setElapsedRealtimeNanos(TEST_ELAPSED_NANOS)
                .setDistanceMeasurement(
                        new DistanceMeasurement.Builder()
                                .setMeters(TEST_DISTANCE / (double) 100)
                                .setErrorMeters(0)
                                .setConfidenceLevel(0)
                                .build())
                .setAngleOfArrivalMeasurement(aoaMeasurement)
                .setDestinationAngleOfArrivalMeasurement(aoaDestMeasurement)
                .setLineOfSight(TEST_LOS)
                .build();
        RangingReport rangingReport = new RangingReport.Builder()
                .addMeasurement(rangingMeasurement)
                .build();
        return Pair.create(uwbRangingData, rangingReport);
    }
}
