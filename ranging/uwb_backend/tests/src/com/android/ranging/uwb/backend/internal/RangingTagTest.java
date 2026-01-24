/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_FAILED_TO_START;
import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_STOP_RANGING_CALLED;
import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_UNKNOWN;
import static com.android.ranging.uwb.backend.internal.RangingSessionCallback.REASON_WRONG_PARAMETERS;
import static com.android.ranging.uwb.backend.internal.Utils.STATUS_OK;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_DT_TAG;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_DL_TDOA;
import static com.google.uwb.support.fira.FiraParams.STATUS_CODE_OK;
import static com.google.uwb.support.fira.FiraParams.STATUS_CODE_RANGING_RX_TIMEOUT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.UwbManager;
import androidx.test.runner.AndroidJUnit4;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.dltdoa.DlTDoAMeasurement;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class RangingTagTest {

    @Mock
    private UwbManager mUwbManager;
    @Mock
    private RangingSession mRangingSession;
    @Mock
    private RangingSessionCallback mRangingSessionCallback;
    @Mock
    private UwbFeatureFlags mUwbFeatureFlags;

    private RangingTag mRangingTag;
    private UwbAddress mTestLocalAddress;
    private UwbComplexChannel mTestComplexChannel;

    private static class Mutable<E> {
        E value;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ExecutorService executor = MoreExecutors.newDirectExecutorService();
        OpAsyncCallbackRunner<Boolean> opAsyncCallbackRunner = new OpAsyncCallbackRunner<>();

        doAnswer(
                        invocation -> {
                            RangingSession.Callback callback = invocation.getArgument(2);
                            callback.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));

        mRangingTag = new RangingTag(mUwbManager, executor, opAsyncCallbackRunner,
                mUwbFeatureFlags);
        mRangingTag.setForTesting(true);
        mTestLocalAddress = mRangingTag.getLocalAddress();
        mTestComplexChannel = new UwbComplexChannel(9, 10);

        mRangingTag.setComplexChannel(mTestComplexChannel);
    }

    @Test
    public void testStartRanging_buildsCorrectFiraParams() {
        setupRangingParameters();

        mRangingTag.startRanging(mRangingSessionCallback);

        ArgumentCaptor<PersistableBundle> bundleCaptor =
                ArgumentCaptor.forClass(PersistableBundle.class);
        verify(mUwbManager).openRangingSession(bundleCaptor.capture(), any(Executor.class),
                any(RangingSession.Callback.class));

        FiraOpenSessionParams params = FiraOpenSessionParams.fromBundle(bundleCaptor.getValue());
        assertEquals(RANGING_DEVICE_TYPE_DT_TAG, params.getDeviceType());
        assertEquals(RANGING_ROUND_USAGE_DL_TDOA, params.getRangingRoundUsage());
        assertEquals(20, params.getSlotsPerRangingRound());
        assertEquals(180, params.getRangingIntervalMs());
    }

    @Test
    public void testHashSessionId_isCorrectForTag() {
        RangingParameters rangingParameters =
                new DtTagParameters(
                        0, /* sessionId */
                        new byte[]{1, 2}, /* sessionKeyInfo */
                        mTestComplexChannel,
                        Utils.DURATION_2_MS,
                        new UwbRangeLimitsConfig.Builder().build(), /* rangeLimitsConfig */
                        180, /* rangingIntervalMs */
                        20, /* slotsPerRangingRound */
                        new byte[]{0} /* rangingRoundIndexes */);

        int expectedSessionId = RangingDevice.calculateHashedSessionId(
                mTestLocalAddress, mTestComplexChannel);
        mRangingTag.setRangingParameters(rangingParameters);
        assertEquals(expectedSessionId, mRangingTag.getRangingParameters().getSessionId());
    }

    @Test
    public void testStartRanging_Success() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        mRangingTag.startRanging(mRangingSessionCallback);
        verify(mRangingSessionCallback).onRangingInitialized(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()));
    }

    @Test
    public void testStartRanging_OpenFailed() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpenFailed(
                                    REASON_UNKNOWN, new PersistableBundle());
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));

        mRangingTag.startRanging(mRangingSessionCallback);
        verify(mRangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()), REASON_FAILED_TO_START);
    }

    @Test
    public void testStartRanging_StartFailed() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStartFailed(
                                    REASON_WRONG_PARAMETERS, new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        mRangingTag.startRanging(mRangingSessionCallback);
        verify(mRangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()), REASON_FAILED_TO_START);
    }

    @Test
    public void testStopRanging() {
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();
        // First, successfully start the session.
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value = invocation.getArgument(2);
                            pfRangingSessionCallback.value.onOpened(mRangingSession);
                            return new CancellationSignal();
                        })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));
        mRangingTag.startRanging(mRangingSessionCallback);

        // Now, test stopping.
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onStopped(
                                    RangingSession.Callback.REASON_LOCAL_REQUEST,
                                    new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .stop();
        doAnswer(
                        invocation -> {
                            pfRangingSessionCallback.value.onClosed(
                                    RangingSession.Callback.REASON_LOCAL_REQUEST,
                                    new PersistableBundle());
                            return null;
                        })
                .when(mRangingSession)
                .close();

        mRangingTag.stopRanging();

        verify(mRangingSession).stop();
        verify(mRangingSession).close();
        verify(mRangingSessionCallback).onRangingSuspended(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()),
                REASON_STOP_RANGING_CALLED);
    }

    @Test
    public void testStartRanging_reportsOnlyValidMeasurements() {
        // Arrange
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        // Act
        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(mRangingSession);
                    return new CancellationSignal();
                })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                    return null;
                })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        int status = mRangingTag.startRanging(mRangingSessionCallback);
        pfRangingSessionCallback.value.onReportReceived(createRangingReport());

        // Assert
        assertEquals(status, STATUS_OK);
        verify(mRangingSession).updateRangingRoundsDtTag(any(PersistableBundle.class));
        verify(mRangingSessionCallback).onRangingInitialized(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()));

        ArgumentCaptor<UwbDevice> deviceCaptor = ArgumentCaptor.forClass(UwbDevice.class);
        ArgumentCaptor<DlTdoaMeasurement> measurementCaptor =
                ArgumentCaptor.forClass(DlTdoaMeasurement.class);
        verify(mRangingSessionCallback, timeout(100).times(3))
                .onDlTdoaRangingResult(deviceCaptor.capture(), measurementCaptor.capture());

        List<UwbDevice> capturedDevices = deviceCaptor.getAllValues();
        assertEquals(3, capturedDevices.size());
        assertArrayEquals(new byte[] {0x01, 0x02}, capturedDevices.get(0).getAddress().toBytes());
        assertArrayEquals(new byte[] {0x07, 0x08}, capturedDevices.get(1).getAddress().toBytes());
        assertArrayEquals(new byte[] {0x09, 0x0A}, capturedDevices.get(2).getAddress().toBytes());

        List<DlTdoaMeasurement> capturedMeasurements = measurementCaptor.getAllValues();
        assertEquals(3, capturedMeasurements.size());
        assertEquals(0x00, capturedMeasurements.get(0).getMessageType());
        assertEquals(0x01, capturedMeasurements.get(1).getMessageType());
        assertEquals(0x02, capturedMeasurements.get(2).getMessageType());
    }

    @Test
    public void testStartRanging_sameDevicesAcrossReports() {
        // Arrange
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        // Act
        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(mRangingSession);
                    return new CancellationSignal();
                })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                    return null;
                })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        int status = mRangingTag.startRanging(mRangingSessionCallback);
        pfRangingSessionCallback.value.onReportReceived(createRangingReport());
        pfRangingSessionCallback.value.onReportReceived(createRangingReport());

        // Assert
        assertEquals(status, STATUS_OK);
        verify(mRangingSession).updateRangingRoundsDtTag(any(PersistableBundle.class));
        verify(mRangingSessionCallback).onRangingInitialized(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()));

        ArgumentCaptor<UwbDevice> deviceCaptor = ArgumentCaptor.forClass(UwbDevice.class);
        ArgumentCaptor<DlTdoaMeasurement> measurementCaptor =
                ArgumentCaptor.forClass(DlTdoaMeasurement.class);
        verify(mRangingSessionCallback, timeout(100).times(6))
                .onDlTdoaRangingResult(deviceCaptor.capture(), measurementCaptor.capture());

        List<UwbDevice> capturedDevices = deviceCaptor.getAllValues();
        assertEquals(6, capturedDevices.size());
        assertArrayEquals(new byte[] {0x01, 0x02}, capturedDevices.get(0).getAddress().toBytes());
        assertArrayEquals(new byte[] {0x07, 0x08}, capturedDevices.get(1).getAddress().toBytes());
        assertArrayEquals(new byte[] {0x09, 0x0A}, capturedDevices.get(2).getAddress().toBytes());
        assertEquals(capturedDevices.get(0), capturedDevices.get(3));
        assertEquals(capturedDevices.get(1), capturedDevices.get(4));
        assertEquals(capturedDevices.get(2), capturedDevices.get(5));

        List<DlTdoaMeasurement> capturedMeasurements = measurementCaptor.getAllValues();
        assertEquals(6, capturedMeasurements.size());
        assertEquals(0x00, capturedMeasurements.get(0).getMessageType());
        assertEquals(0x01, capturedMeasurements.get(1).getMessageType());
        assertEquals(0x02, capturedMeasurements.get(2).getMessageType());
        assertEquals(0x00, capturedMeasurements.get(3).getMessageType());
        assertEquals(0x01, capturedMeasurements.get(4).getMessageType());
        assertEquals(0x02, capturedMeasurements.get(5).getMessageType());
    }

    @Test
    public void testStartRanging_noValidMeasurements() {
        // Arrange
        final Mutable<RangingSession.Callback> pfRangingSessionCallback = new Mutable<>();
        setupRangingParameters();

        // Act
        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value = invocation.getArgument(2);
                    pfRangingSessionCallback.value.onOpened(mRangingSession);
                    return new CancellationSignal();
                })
                .when(mUwbManager)
                .openRangingSession(
                        any(PersistableBundle.class),
                        any(Executor.class),
                        any(RangingSession.Callback.class));
        doAnswer(
                invocation -> {
                    pfRangingSessionCallback.value.onStarted(new PersistableBundle());
                    return null;
                })
                .when(mRangingSession)
                .start(any(PersistableBundle.class));

        int status = mRangingTag.startRanging(mRangingSessionCallback);
        pfRangingSessionCallback.value.onReportReceived(createEmptyRangingReport());

        // Assert
        assertEquals(status, STATUS_OK);
        verify(mRangingSession).updateRangingRoundsDtTag(any(PersistableBundle.class));
        verify(mRangingSessionCallback).onRangingInitialized(
                UwbDevice.createForAddress(mTestLocalAddress.toBytes()));

        ArgumentCaptor<UwbDevice> deviceCaptor = ArgumentCaptor.forClass(UwbDevice.class);
        ArgumentCaptor<DlTdoaMeasurement> measurementCaptor =
                ArgumentCaptor.forClass(DlTdoaMeasurement.class);
        verify(mRangingSessionCallback, timeout(100).times(0))
                .onDlTdoaRangingResult(deviceCaptor.capture(), measurementCaptor.capture());

        List<UwbDevice> capturedDevices = deviceCaptor.getAllValues();
        assertEquals(0, capturedDevices.size());

        List<DlTdoaMeasurement> capturedMeasurements = measurementCaptor.getAllValues();
        assertEquals(0, capturedMeasurements.size());
    }

    private void setupRangingParameters() {
        RangingParameters rangingParameters =
                new DtTagParameters(
                        123, /* sessionId */
                        new byte[]{1, 2}, /* sessionKeyInfo */
                        mTestComplexChannel,
                        Utils.DURATION_2_MS,
                        new UwbRangeLimitsConfig.Builder().build(), /* rangeLimitsConfig */
                        180, /* rangingIntervalMs */
                        20, /* slotsPerRangingRound */
                        new byte[]{0} /* rangingRoundIndexes */);
        mRangingTag.setRangingParameters(rangingParameters);
    }

    private DlTDoAMeasurement createDlTDoAMeasurement(int messageType) {
        return new DlTDoAMeasurement.Builder()
                .setMessageType(messageType)
                .setMessageControl(0x0001)
                .setBlockIndex(3)
                .setRoundIndex(5)
                .setNLoS(0x01)
                .setTxTimestamp(1234567890L)
                .setRxTimestamp(9876543210L)
                .setAnchorCfo(0.1f)
                .setCfo(0.2f)
                .setInitiatorReplyTime(12345L)
                .setResponderReplyTime(56789L)
                .setInitiatorResponderTof(123)
                .setAnchorLocation(new byte[10])
                .setActiveRangingRounds(new byte[] {1, 3, 5, 7})
                .build();
    }

    private DlTDoAMeasurement createEmptyDlTDoAMeasurement(int messageType) {
        return new DlTDoAMeasurement.Builder()
                .setMessageType(messageType)
                .setMessageControl(0x0000)
                .setBlockIndex(0)
                .setRoundIndex(0)
                .setNLoS(0x00)
                .setTxTimestamp(0L)
                .setRxTimestamp(0L)
                .setAnchorCfo(0.0f)
                .setCfo(0.0f)
                .setInitiatorReplyTime(0L)
                .setResponderReplyTime(0L)
                .setInitiatorResponderTof(0)
                .setAnchorLocation(new byte[0])
                .setActiveRangingRounds(new byte[0])
                .build();
    }

    private RangingReport createRangingReport() {
        DlTDoAMeasurement pollMeasurement = createDlTDoAMeasurement(0x00);
        DlTDoAMeasurement responseMeasurement1 = createDlTDoAMeasurement(0x01);
        DlTDoAMeasurement responseMeasurement2 = createDlTDoAMeasurement(0x01);
        DlTDoAMeasurement responseMeasurement3 = createDlTDoAMeasurement(0x01);
        DlTDoAMeasurement finalReportMeasurement = createDlTDoAMeasurement(0x02);
        return new RangingReport.Builder()
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(new byte[] {0x01, 0x02}))
                        .setStatus(STATUS_CODE_OK)
                        .setRssiDbm(-20)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(pollMeasurement.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(new byte[] {0x03, 0x04}))
                        .setStatus(STATUS_CODE_RANGING_RX_TIMEOUT)
                        .setRssiDbm(-30)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(responseMeasurement1.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(new byte[] {0x05, 0x06}))
                        .setStatus(STATUS_CODE_RANGING_RX_TIMEOUT)
                        .setRssiDbm(-40)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(responseMeasurement2.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(new byte[] {0x07, 0x08}))
                        .setStatus(STATUS_CODE_OK)
                        .setRssiDbm(-50)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(responseMeasurement3.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(new byte[] {0x09, 0x0A}))
                        .setStatus(STATUS_CODE_OK)
                        .setRssiDbm(-60)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(finalReportMeasurement.toBundle())
                        .build())
                .build();
    }

    private RangingReport createEmptyRangingReport() {
        DlTDoAMeasurement pollMeasurement = createEmptyDlTDoAMeasurement(0x00);
        DlTDoAMeasurement responseMeasurement1 = createEmptyDlTDoAMeasurement(0x01);
        DlTDoAMeasurement responseMeasurement2 = createEmptyDlTDoAMeasurement(0x01);
        DlTDoAMeasurement responseMeasurement3 = createEmptyDlTDoAMeasurement(0x01);
        DlTDoAMeasurement finalReportMeasurement = createEmptyDlTDoAMeasurement(0x02);
        return new RangingReport.Builder()
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(
                                        new byte[] {(byte) 0xFF, (byte) 0xFF}))
                        .setStatus(STATUS_CODE_OK)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(pollMeasurement.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(
                                        new byte[] {(byte) 0xFF, (byte) 0xFF}))
                        .setStatus(STATUS_CODE_OK)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(responseMeasurement1.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(
                                        new byte[] {(byte) 0xFF, (byte) 0xFF}))
                        .setStatus(STATUS_CODE_OK)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(responseMeasurement2.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(
                                        new byte[] {(byte) 0xFF, (byte) 0xFF}))
                        .setStatus(STATUS_CODE_OK)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(responseMeasurement3.toBundle())
                        .build())
                .addMeasurement(new RangingMeasurement.Builder()
                        .setRemoteDeviceAddress(
                                android.uwb.UwbAddress.fromBytes(
                                        new byte[] {(byte) 0xFF, (byte) 0xFF}))
                        .setStatus(STATUS_CODE_OK)
                        .setElapsedRealtimeNanos(1234567890L)
                        .setRangingMeasurementMetadata(finalReportMeasurement.toBundle())
                        .build())
                .build();
    }
}
