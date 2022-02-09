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

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE;
import static com.google.uwb.support.ccc.CccParams.SLOTS_PER_ROUND_6;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.HOPPING_MODE_DISABLE;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;
import android.uwb.AdapterState;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.SessionHandle;
import android.uwb.StateChangeReason;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.uwb.data.UwbUciConstants;
import com.android.server.uwb.data.UwbVendorUciResponse;
import com.android.server.uwb.jni.NativeUwbManager;

import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/**
 * Tests for {@link UwbServiceCore}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbServiceCoreTest {
    private static final FiraOpenSessionParams.Builder TEST_FIRA_OPEN_SESSION_PARAMS =
            new FiraOpenSessionParams.Builder()
                    .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                    .setSessionId(1)
                    .setDeviceType(RANGING_DEVICE_TYPE_CONTROLLER)
                    .setDeviceRole(RANGING_DEVICE_ROLE_RESPONDER)
                    .setDeviceAddress(UwbAddress.fromBytes(new byte[] { 0x4, 0x6}))
                    .setDestAddressList(Arrays.asList(UwbAddress.fromBytes(new byte[] { 0x4, 0x6})))
                    .setMultiNodeMode(MULTI_NODE_MODE_UNICAST)
                    .setRangingRoundUsage(RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE)
                    .setVendorId(new byte[]{0x5, 0x78})
                    .setStaticStsIV(new byte[]{0x1a, 0x55, 0x77, 0x47, 0x7e, 0x7d});

    @VisibleForTesting
    private static final CccOpenRangingParams.Builder TEST_CCC_OPEN_RANGING_PARAMS =
            new CccOpenRangingParams.Builder()
                    .setProtocolVersion(CccParams.PROTOCOL_VERSION_1_0)
                    .setUwbConfig(CccParams.UWB_CONFIG_0)
                    .setPulseShapeCombo(
                            new CccPulseShapeCombo(
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                    PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE))
                    .setSessionId(1)
                    .setRanMultiplier(4)
                    .setChannel(UWB_CHANNEL_9)
                    .setNumChapsPerSlot(CHAPS_PER_SLOT_3)
                    .setNumResponderNodes(1)
                    .setNumSlotsPerRound(SLOTS_PER_ROUND_6)
                    .setSyncCodeIndex(1)
                    .setHoppingConfigMode(HOPPING_MODE_DISABLE)
                    .setHoppingSequence(HOPPING_SEQUENCE_DEFAULT);
    @Mock private Context mContext;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Mock private UwbMetrics mUwbMetrics;
    @Mock private UwbCountryCode mUwbCountryCode;
    @Mock private UwbSessionManager mUwbSessionManager;
    @Mock private UwbConfigurationManager mUwbConfigurationManager;
    private TestLooper mTestLooper;
    private MockitoSession mMockitoSession;

    private UwbServiceCore mUwbServiceCore;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        PowerManager powerManager = mock(PowerManager.class);
        when(powerManager.newWakeLock(anyInt(), anyString()))
                .thenReturn(mock(PowerManager.WakeLock.class));
        when(mContext.getSystemService(PowerManager.class)).thenReturn(powerManager);
        mUwbServiceCore = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, mUwbConfigurationManager,
                mTestLooper.getLooper());

        // static mocking for executor service.
        mMockitoSession = ExtendedMockito.mockitoSession()
                .mockStatic(Executors.class, Mockito.withSettings().lenient())
                .strictness(Strictness.LENIENT)
                .startMocking();
        ExecutorService executorService = mock(ExecutorService.class);
        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(1);
            t.run();
            return t;
        }).when(executorService).submit(any(Callable.class));
        doAnswer(invocation -> {
            FutureTask t = invocation.getArgument(0);
            t.run();
            return t;
        }).when(executorService).submit(any(Runnable.class));
        when(Executors.newSingleThreadExecutor()).thenReturn(executorService);
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        if (mMockitoSession != null) {
            mMockitoSession.finishMocking();
        }
    }

    private void verifyGetSpecificationInfoSuccess() throws Exception {
        FiraSpecificationParams firaSpecificationParams = mock(FiraSpecificationParams.class);
        PersistableBundle firaSpecificationBundle = mock(PersistableBundle.class);
        when(firaSpecificationParams.toBundle()).thenReturn(firaSpecificationBundle);
        CccSpecificationParams cccSpecificationParams = mock(CccSpecificationParams.class);
        PersistableBundle cccSpecificationBundle = mock(PersistableBundle.class);
        when(cccSpecificationParams.toBundle()).thenReturn(cccSpecificationBundle);

        when(mUwbConfigurationManager.getCapsInfo(eq(FiraParams.PROTOCOL_NAME), any()))
                .thenReturn(Pair.create(UwbUciConstants.STATUS_CODE_OK, firaSpecificationParams));
        when(mUwbConfigurationManager.getCapsInfo(eq(CccParams.PROTOCOL_NAME), any()))
                .thenReturn(Pair.create(UwbUciConstants.STATUS_CODE_OK, cccSpecificationParams));

        PersistableBundle specifications = mUwbServiceCore.getIUwbAdapter().getSpecificationInfo();
        assertThat(specifications).isNotNull();
        assertThat(specifications.getPersistableBundle(FiraParams.PROTOCOL_NAME))
                .isEqualTo(firaSpecificationBundle);
        assertThat(specifications.getPersistableBundle(CccParams.PROTOCOL_NAME))
                .isEqualTo(cccSpecificationBundle);
        verify(mUwbConfigurationManager).getCapsInfo(eq(FiraParams.PROTOCOL_NAME), any());
        verify(mUwbConfigurationManager).getCapsInfo(eq(CccParams.PROTOCOL_NAME), any());
    }

    @Test
    public void testGetSpecificationInfoSuccess() throws Exception {
        verifyGetSpecificationInfoSuccess();
    }

    @Test
    public void testGetSpecificationInfoUsesCache() throws Exception {
        verifyGetSpecificationInfoSuccess();
        clearInvocations(mUwbConfigurationManager);

        PersistableBundle specifications = mUwbServiceCore.getIUwbAdapter().getSpecificationInfo();
        assertThat(specifications).isNotNull();
        assertThat(specifications.getPersistableBundle(FiraParams.PROTOCOL_NAME)).isNotNull();
        assertThat(specifications.getPersistableBundle(CccParams.PROTOCOL_NAME)).isNotNull();

        verifyNoMoreInteractions(mUwbConfigurationManager);
    }

    private void enableUwb() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.setCountryCode()).thenReturn(true);

        mUwbServiceCore.getIUwbAdapter().setEnabled(true);
        mTestLooper.dispatchAll();
    }

    private void disableUwb() throws Exception {
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        mUwbServiceCore.getIUwbAdapter().setEnabled(false);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testEnable() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.getIUwbAdapter().registerAdapterStateCallbacks(cb);

        enableUwb();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testEnableWhenAlreadyEnabled() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.getIUwbAdapter().registerAdapterStateCallbacks(cb);

        enableUwb();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);
        // Enable again. should be ignored.
        enableUwb();
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }


    @Test
    public void testDisable() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.getIUwbAdapter().registerAdapterStateCallbacks(cb);

        // Enable first
        enableUwb();

        disableUwb();

        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
    }


    @Test
    public void testDisableWhenAlreadyDisabled() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.setCountryCode()).thenReturn(true);
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.getIUwbAdapter().registerAdapterStateCallbacks(cb);

        // Enable first
        enableUwb();

        disableUwb();

        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);
        // Disable again. should be ignored.
        disableUwb();
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }

    @Test
    public void testOpenFiraRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        mUwbServiceCore.getIUwbAdapter().openRanging(
                mock(AttributionSource.class), sessionHandle, cb,
                params.toBundle());

        verify(mUwbSessionManager).initSession(
                eq(sessionHandle), eq(params.getSessionId()), eq(FiraParams.PROTOCOL_NAME),
                argThat(p -> ((FiraOpenSessionParams) p).getSessionId() == params.getSessionId()),
                eq(cb));

    }

    @Test
    public void testOpenCccRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        mUwbServiceCore.getIUwbAdapter().openRanging(
                mock(AttributionSource.class), sessionHandle, cb,
                params.toBundle());

        verify(mUwbSessionManager).initSession(
                eq(sessionHandle), eq(params.getSessionId()), eq(CccParams.PROTOCOL_NAME),
                argThat(p -> ((CccOpenRangingParams) p).getSessionId() == params.getSessionId()),
                eq(cb));
    }

    @Test
    public void testOpenRangingWhenUwbDisabled() throws Exception {
        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();

        try {
            mUwbServiceCore.getIUwbAdapter().openRanging(
                    mock(AttributionSource.class), sessionHandle, cb,
                    params.toBundle());
            fail();
        } catch (RemoteException e) {
            // pass
        }

        // Should be ignored.
        verifyNoMoreInteractions(mUwbSessionManager);
    }

    @Test
    public void testStartRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        PersistableBundle params = mock(PersistableBundle.class);
        mUwbServiceCore.getIUwbAdapter().startRanging(sessionHandle, params);

        verify(mUwbSessionManager).startRanging(sessionHandle, params);
    }

    @Test
    public void testReconfigureRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        PersistableBundle params = mock(PersistableBundle.class);
        mUwbServiceCore.getIUwbAdapter().reconfigureRanging(sessionHandle, params);

        verify(mUwbSessionManager).reconfigure(sessionHandle, params);
    }

    @Test
    public void testStopRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.getIUwbAdapter().stopRanging(sessionHandle);

        verify(mUwbSessionManager).stopRanging(sessionHandle);
    }


    @Test
    public void testCloseRanging() throws Exception {
        enableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.getIUwbAdapter().closeRanging(sessionHandle);

        verify(mUwbSessionManager).deInitSession(sessionHandle);
    }

    @Test
    public void testGetAdapterState() throws Exception {
        enableUwb();
        assertThat(mUwbServiceCore.getIUwbAdapter().getAdapterState())
                .isEqualTo(AdapterState.STATE_ENABLED_INACTIVE);

        disableUwb();
        assertThat(mUwbServiceCore.getIUwbAdapter().getAdapterState())
                .isEqualTo(AdapterState.STATE_DISABLED);
    }


    @Test
    public void testSendVendorUciCommand() throws Exception {
        enableUwb();

        UwbVendorUciResponse rsp = new UwbVendorUciResponse(
                (byte) UwbUciConstants.STATUS_CODE_OK, 0, 0, new byte[0]);
        when(mNativeUwbManager.sendRawVendorCmd(anyInt(), anyInt(), any()))
                .thenReturn(rsp);

        // TODO(b/196225233): Remove this casting when qorvo stack is integrated.
        assertThat(((UwbServiceCore.UwbAdapterService) mUwbServiceCore.getIUwbAdapter())
                .sendVendorUciMessage(0, 0, new byte[0]))
                .isEqualTo(UwbUciConstants.STATUS_CODE_OK);
    }
}
