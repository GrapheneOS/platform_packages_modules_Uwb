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


import static com.android.server.uwb.UwbTestUtils.MAX_DATA_SIZE;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_ANDROID_REGULATION_UWB_OFF;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_FAILED;
import static com.android.server.uwb.data.UwbUciConstants.STATUS_CODE_OK;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.ccc.CccParams.CHAPS_PER_SLOT_3;
import static com.google.uwb.support.ccc.CccParams.HOPPING_CONFIG_MODE_NONE;
import static com.google.uwb.support.ccc.CccParams.HOPPING_SEQUENCE_DEFAULT;
import static com.google.uwb.support.ccc.CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE;
import static com.google.uwb.support.ccc.CccParams.SLOTS_PER_ROUND_6;
import static com.google.uwb.support.ccc.CccParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE;
import static com.google.uwb.support.fira.FiraParams.MULTI_NODE_MODE_UNICAST;
import static com.google.uwb.support.fira.FiraParams.RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_RESPONDER;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;
import static com.google.uwb.support.fira.FiraParams.RANGING_ROUND_USAGE_SS_TWR_DEFERRED_MODE;
import static com.google.uwb.support.fira.FiraParams.SESSION_TYPE_RANGING;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;
import android.uwb.AdapterState;
import android.uwb.IOnUwbActivityEnergyInfoListener;
import android.uwb.IUwbAdapterStateCallbacks;
import android.uwb.IUwbOemExtensionCallback;
import android.uwb.IUwbRangingCallbacks;
import android.uwb.IUwbVendorUciCallback;
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
import com.android.server.uwb.multchip.UwbMultichipData;
import com.android.server.uwb.multichip.MultichipConfigFileCreator;
import com.android.server.uwb.pm.ProfileManager;
import com.android.uwb.resources.R;

import com.google.uwb.support.ccc.CccOpenRangingParams;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccProtocolVersion;
import com.google.uwb.support.ccc.CccPulseShapeCombo;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.ccc.CccStartRangingParams;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.generic.GenericParams;
import com.google.uwb.support.generic.GenericSpecificationParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;
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
    @Rule
    public TemporaryFolder mTempFolder = TemporaryFolder.builder().build();

    private static final int TEST_UID = 44;
    private static final String TEST_PACKAGE_NAME = "com.android.uwb";
    private static final String TEST_DEFAULT_CHIP_ID = "default";
    private static final String TEST_CHIP_ONE_CHIP_ID = "chipIdString1";

    private static final AttributionSource TEST_ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(TEST_UID)
                    .setPackageName(TEST_PACKAGE_NAME)
                    .build();
    private static final FiraOpenSessionParams.Builder TEST_FIRA_OPEN_SESSION_PARAMS =
            new FiraOpenSessionParams.Builder()
                    .setProtocolVersion(FiraParams.PROTOCOL_VERSION_1_1)
                    .setSessionId(1)
                    .setSessionType(SESSION_TYPE_RANGING)
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
                    .setHoppingConfigMode(HOPPING_CONFIG_MODE_NONE)
                    .setHoppingSequence(HOPPING_SEQUENCE_DEFAULT);
    @Mock private Context mContext;
    @Mock private NativeUwbManager mNativeUwbManager;
    @Mock private UwbMetrics mUwbMetrics;
    @Mock private UwbCountryCode mUwbCountryCode;
    @Mock private UwbSessionManager mUwbSessionManager;
    @Mock private UwbConfigurationManager mUwbConfigurationManager;
    @Mock private UwbInjector mUwbInjector;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock private ProfileManager mProfileManager;
    @Mock private PowerManager.WakeLock mUwbWakeLock;
    @Mock private Resources mResources;

    private TestLooper mTestLooper;
    private MockitoSession mMockitoSession;

    private UwbServiceCore mUwbServiceCore;

    private static final int MESSAGE_TYPE_TEST_1 = 4;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        PowerManager powerManager = mock(PowerManager.class);
        when(powerManager.newWakeLock(anyInt(), anyString()))
                .thenReturn(mUwbWakeLock);
        when(mContext.getSystemService(PowerManager.class)).thenReturn(powerManager);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        UwbMultichipData uwbMultichipData = setUpMultichipDataForOneChip();
        when(mUwbInjector.getMultichipData()).thenReturn(uwbMultichipData);
        when(mDeviceConfigFacade.getBugReportMinIntervalMs())
                .thenReturn(DeviceConfigFacade.DEFAULT_BUG_REPORT_MIN_INTERVAL_MS);
        when(mUwbInjector.getProfileManager()).thenReturn(mProfileManager);
        mUwbServiceCore = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, mUwbConfigurationManager,
                mUwbInjector, mTestLooper.getLooper());

        uwbMultichipData.initialize();

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

    private UwbMultichipData setUpMultichipDataForOneChip() throws Exception {
        when(mResources.getBoolean(R.bool.config_isMultichip)).thenReturn(false);
        when(mContext.getResources()).thenReturn(mResources);
        return new UwbMultichipData(mContext);
    }

    private UwbMultichipData setUpMultichipDataForTwoChips() throws Exception {
        when(mResources.getBoolean(R.bool.config_isMultichip)).thenReturn(true);
        String path = MultichipConfigFileCreator.createTwoChipFileFromResource(mTempFolder,
                getClass()).getCanonicalPath();
        when(mResources.getString(R.string.config_multichipConfigPath)).thenReturn(path);
        when(mContext.getResources()).thenReturn(mResources);
        return new UwbMultichipData(mContext);
    }

    private void verifyGetSpecificationInfoSuccess() throws Exception {
        GenericSpecificationParams genericSpecificationParams =
                mock(GenericSpecificationParams.class);
        PersistableBundle genericSpecificationBundle = mock(PersistableBundle.class);
        when(genericSpecificationParams.toBundle()).thenReturn(genericSpecificationBundle);

        when(mUwbConfigurationManager
                .getCapsInfo(eq(GenericParams.PROTOCOL_NAME), any(), anyString()))
                .thenReturn(Pair.create(
                        UwbUciConstants.STATUS_CODE_OK, genericSpecificationParams));

        PersistableBundle specifications = mUwbServiceCore.getSpecificationInfo(
                TEST_DEFAULT_CHIP_ID);
        assertThat(specifications).isEqualTo(genericSpecificationBundle);
        verify(mUwbConfigurationManager)
                .getCapsInfo(eq(GenericParams.PROTOCOL_NAME), any(), eq(TEST_DEFAULT_CHIP_ID));

        assertThat(mUwbServiceCore.getCachedSpecificationParams(TEST_DEFAULT_CHIP_ID)).isEqualTo(
                genericSpecificationParams);
    }

    private void enableUwb() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(
                Pair.create(STATUS_CODE_OK, null));

        mUwbServiceCore.setEnabled(true);
        mTestLooper.dispatchAll();
    }

    private void enableUwbWithCountryCodeChangedCallback() throws Exception {
        enableUwb();

        // Happy case - we receive the onCountryCodeChanged() notification with a valid country
        // code, when the message loop is waiting for it.
        mUwbServiceCore.onCountryCodeChanged("US");
        mTestLooper.dispatchAll();
    }

    private void disableUwb() throws Exception {
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        mUwbServiceCore.setEnabled(false);
        mTestLooper.dispatchAll();
    }

    @Test
    public void testGetSpecificationInfoSuccess() throws Exception {
        enableUwbWithCountryCodeChangedCallback();
        verifyGetSpecificationInfoSuccess();
    }

    @Test
    public void testGetSpecificationInfoFailWhenUwbDisabled() throws Exception {
        try {
            mUwbServiceCore.getCachedSpecificationParams(TEST_DEFAULT_CHIP_ID);
            fail();
        } catch (IllegalStateException e) {
            // pass
        }
    }

    @Test
    public void testEnableWithCountryCode_success() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);
        clearInvocations(cb);

        // Enable (with country code initially unknown, like at boot time).
        when(mUwbCountryCode.getCountryCode()).thenReturn(null);
        enableUwb();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verifyNoMoreInteractions(cb);

        clearInvocations(cb);

        // We receive an initial onCountryCodeChanged() notification with the default (invalid)
        // country code. We don't expect any more AdapterState notifications as the Adapter State
        // is still considered to be the same (STATE_DISABLED).
        mUwbServiceCore.onCountryCodeChanged("00");
        verifyNoMoreInteractions(cb);

        // Valid country code changed notification is received after some time (before the timeout).
        // The message queue immediately has a message to process, which results in a call to the
        // adapter state callback.
        mUwbServiceCore.onCountryCodeChanged("US");
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
    }

    // Unit test for scenario when setting the country code (during UWB Enable) fails with a UWB
    // regulatory error (eg: UWB not available in the configured country). In this case, we expect
    // the apps to be notified with UWB state as Disabled and reason as SYSTEM_REGULATION.
    @Test
    public void testEnableWithCountryCode_statusRegulationUwbOff() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Enable (with country code that results in the Vendor-specific UWB_REGULATION error).
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.getCountryCode()).thenReturn("JP");
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(Pair.create(
                STATUS_CODE_ANDROID_REGULATION_UWB_OFF, "JP"));

        mUwbServiceCore.setEnabled(true);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_REGULATION);
    }

    // Unit test for scenario when setting the country code (during UWB Enable) fails with a generic
    // error (eg: UWBS internal error). In this case, we expect the apps to be notified with
    // UWB state as Disabled and reason as SYSTEM_POLICY.
    @Test
    public void testEnableWithCountryCode_statusFailed() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Enable (with a valid country code), but the firmware returns some error.
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(Pair.create(
                STATUS_CODE_FAILED, "US"));

        mUwbServiceCore.setEnabled(true);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testEnableWhenAlreadyEnabled() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Enabled UWB, we expect an Adapter State notification with State ENABLED_INACTIVE as
        // there is a valid country code.
        enableUwbWithCountryCodeChangedCallback();
        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Enable again. should be ignored.
        enableUwb();
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }

    // Test the UWB stack enable when the NativeUwbManager.doInitialize() is delayed such that the
    // watchdog timer expiry happens.
    @Test
    public void testEnableWhenInitializeDelayed() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Setup doInitialize() to take long time, such that the WatchDog thread times out.
        when(mNativeUwbManager.doInitialize()).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                // Return success but too late, so this result shouldn't matter.
                Thread.sleep(UwbServiceCore.WATCHDOG_MS + 1000);
                return true;
            }
        });
        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(
                Pair.create(STATUS_CODE_OK, "US"));

        // Setup the wakelock to be checked twice (once from the watchdog thread after expiry, and
        // second time from handleEnable()).
        when(mUwbWakeLock.isHeld()).thenReturn(true).thenReturn(false);

        mUwbServiceCore.setEnabled(true);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbWakeLock, times(1)).acquire();
        verify(mUwbWakeLock, times(2)).isHeld();
        verify(mUwbWakeLock, times(1)).release();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testDisable() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Enable first.
        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
        clearInvocations(cb);

        // Disable UWB.
        disableUwb();

        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
    }

    // Test the UWB stack disable when the NativeUwbManager.doDeinitialize() is delayed such that
    // the watchdog timer expiry happens.
    @Test
    public void testDisableWhenInitializeDelayed() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Enable first
        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mUwbWakeLock, cb);

        // Setup doDeinitialize() to take long time, such that the WatchDog thread times out.
        when(mNativeUwbManager.doDeinitialize()).thenAnswer(new Answer<Boolean>() {
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                // Return success but too late, so this result shouldn't matter.
                Thread.sleep(UwbServiceCore.WATCHDOG_MS + 1000);
                return true;
            }
        });

        // Setup the wakelock to be checked twice (once from the watchdog thread after expiry, and
        // second time from handleDisable()).
        when(mUwbWakeLock.isHeld()).thenReturn(true).thenReturn(false);

        // Disable UWB.
        mUwbServiceCore.setEnabled(false);
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).doDeinitialize();
        verify(mUwbWakeLock, times(1)).acquire();
        verify(mUwbWakeLock, times(2)).isHeld();
        verify(mUwbWakeLock, times(1)).release();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testDisableWhenAlreadyDisabled() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Enable first
        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Disable UWB.
        disableUwb();
        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verifyNoMoreInteractions(mUwbCountryCode);
        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Disable again. should be ignored.
        disableUwb();
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }

    @Test
    public void testToggleMultipleEnableDisable() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Enable first (with country code initially unknown, like at boot time).
        when(mUwbCountryCode.getCountryCode()).thenReturn(null);
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(
                Pair.create(STATUS_CODE_OK, null));
        enableUwbWithCountryCodeChangedCallback();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(true);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Disable UWB.
        disableUwb();

        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // Enable again (this time we get the onCountryCodeChanged() callback with a valid
        // country code as it's known).
        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        enableUwbWithCountryCodeChangedCallback();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(anyBoolean());
        verify(cb).onAdapterStateChanged(
                UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
        verifyNoMoreInteractions(cb);
    }

    @Test
    public void testToggleMultipleEnableDisableQuickly() throws Exception {
        when(mNativeUwbManager.doInitialize()).thenReturn(true);
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);

        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);
        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(
                Pair.create(STATUS_CODE_OK, "US"));

        // Quickly enqueue a UWB enable followed by a UWB disable message. Both of these will be
        // processed (in order).
        mUwbServiceCore.setEnabled(true);
        mUwbServiceCore.setEnabled(false);
        mUwbServiceCore.onCountryCodeChanged("US");

        // Now process all the looper messages
        mTestLooper.dispatchAll();

        verify(mNativeUwbManager).doInitialize();
        verify(mUwbCountryCode).setCountryCode(anyBoolean());
        verify(mNativeUwbManager).doDeinitialize();

        // We expect one onAdapterStateChanged() call each, for the "UWB Enabled" and final
        // "UWB Disabled" state.
        verify(cb, times(1)).onAdapterStateChanged(
                UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb, times(1)).onAdapterStateChanged(
                UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testOpenFiraRanging() throws Exception {
        enableUwbWithCountryCodeChangedCallback();
        GenericSpecificationParams genericSpecificationParams =
                mock(GenericSpecificationParams.class);
        FiraSpecificationParams firaSpecificationParams =
                mock(FiraSpecificationParams.class);
        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;
        FiraOpenSessionParams params = TEST_FIRA_OPEN_SESSION_PARAMS.build();
        when(mUwbConfigurationManager
                .getCapsInfo(eq(GenericParams.PROTOCOL_NAME), any(), anyString()))
                .thenReturn(Pair.create(
                        UwbUciConstants.STATUS_CODE_OK, genericSpecificationParams));
        when(genericSpecificationParams.getFiraSpecificationParams())
                .thenReturn(firaSpecificationParams);
        when(firaSpecificationParams.hasRssiReportingSupport())
                .thenReturn(true);
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle(), TEST_DEFAULT_CHIP_ID);

        verify(mUwbSessionManager).initSession(
                eq(attributionSource),
                eq(sessionHandle), eq(params.getSessionId()), eq((byte) params.getSessionType()),
                eq(FiraParams.PROTOCOL_NAME),
                argThat(p -> ((FiraOpenSessionParams) p).getSessionId() == params.getSessionId()),
                eq(cb), eq(TEST_DEFAULT_CHIP_ID));

    }

    @Test
    public void testOpenCccRanging() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;
        mUwbServiceCore.openRanging(
                attributionSource, sessionHandle, cb, params.toBundle(), TEST_DEFAULT_CHIP_ID);

        verify(mUwbSessionManager).initSession(
                eq(attributionSource),
                eq(sessionHandle), eq(params.getSessionId()), eq((byte) params.getSessionType()),
                eq(CccParams.PROTOCOL_NAME),
                argThat(p -> ((CccOpenRangingParams) p).getSessionId() == params.getSessionId()),
                eq(cb), eq(TEST_DEFAULT_CHIP_ID));
    }

    @Test
    public void testOpenRangingWhenUwbDisabled() throws Exception {
        SessionHandle sessionHandle = mock(SessionHandle.class);
        IUwbRangingCallbacks cb = mock(IUwbRangingCallbacks.class);
        CccOpenRangingParams params = TEST_CCC_OPEN_RANGING_PARAMS.build();
        AttributionSource attributionSource = TEST_ATTRIBUTION_SOURCE;

        try {
            mUwbServiceCore.openRanging(attributionSource,
                    sessionHandle,
                    cb,
                    params.toBundle(),
                    TEST_DEFAULT_CHIP_ID);
            fail();
        } catch (IllegalStateException e) {
            // pass
        }

        // Should be ignored.
        verifyNoMoreInteractions(mUwbSessionManager);
    }

    @Test
    public void testStartCccRanging() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        CccStartRangingParams params = new CccStartRangingParams.Builder()
                .setRanMultiplier(6)
                .setSessionId(1)
                .build();
        mUwbServiceCore.startRanging(sessionHandle, params.toBundle());

        verify(mUwbSessionManager).startRanging(eq(sessionHandle),
                argThat(p -> ((CccStartRangingParams) p).getSessionId() == params.getSessionId()));
    }

    @Test
    public void testStartCccRangingWithNoStartParams() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.startRanging(sessionHandle, new PersistableBundle());

        verify(mUwbSessionManager).startRanging(eq(sessionHandle), argThat(p -> (p == null)));
    }

    @Test
    public void testReconfigureRanging() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        final FiraRangingReconfigureParams parameters =
                new FiraRangingReconfigureParams.Builder()
                        .setBlockStrideLength(6)
                        .setRangeDataNtfConfig(RANGE_DATA_NTF_CONFIG_ENABLE_PROXIMITY_LEVEL_TRIG)
                        .setRangeDataProximityFar(6)
                        .setRangeDataProximityNear(4)
                        .build();
        mUwbServiceCore.reconfigureRanging(sessionHandle, parameters.toBundle());
        verify(mUwbSessionManager).reconfigure(eq(sessionHandle),
                argThat((x) ->
                        ((FiraRangingReconfigureParams) x).getBlockStrideLength().equals(6)));
    }

    @Test
    public void testSendData_success() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[] {15, 27});
        PersistableBundle params = mock(PersistableBundle.class);
        byte[] data = new byte[] {1, 3, 5, 7, 11, 13};

        mUwbServiceCore.sendData(sessionHandle, uwbAddress, params, data);
        verify(mUwbSessionManager).sendData(
                eq(sessionHandle), eq(uwbAddress), eq(params), eq(data));
    }

    @Test
    public void testSendData_whenUwbIsDisabled() throws Exception {
        disableUwb();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[] {15, 27});
        PersistableBundle params = mock(PersistableBundle.class);
        byte[] data = new byte[] {1, 3, 5, 7, 11, 13};

        try {
            mUwbServiceCore.sendData(sessionHandle, uwbAddress, params, data);
            fail();
        } catch (IllegalStateException e) { }
    }

    @Test
    public void testAddControlee() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraControleeParams params =
                new FiraControleeParams.Builder()
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        mUwbServiceCore.addControlee(sessionHandle, params.toBundle());
        verify(mUwbSessionManager).reconfigure(eq(sessionHandle),
                argThat((x) -> {
                    FiraRangingReconfigureParams reconfigureParams =
                            (FiraRangingReconfigureParams) x;
                    return reconfigureParams.getAction().equals(MULTICAST_LIST_UPDATE_ACTION_ADD)
                            && Arrays.equals(
                                    reconfigureParams.getAddressList(), params.getAddressList())
                            && Arrays.equals(
                                    reconfigureParams.getSubSessionIdList(),
                                    params.getSubSessionIdList());
                }));
    }

    @Test
    public void testRemoveControlee() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        UwbAddress uwbAddress1 = UwbAddress.fromBytes(new byte[] {1, 2});
        UwbAddress uwbAddress2 = UwbAddress.fromBytes(new byte[] {4, 5});
        UwbAddress[] addressList = new UwbAddress[] {uwbAddress1, uwbAddress2};
        int[] subSessionIdList = new int[] {3, 4};
        FiraControleeParams params =
                new FiraControleeParams.Builder()
                        .setAddressList(addressList)
                        .setSubSessionIdList(subSessionIdList)
                        .build();

        mUwbServiceCore.removeControlee(sessionHandle, params.toBundle());
        verify(mUwbSessionManager).reconfigure(eq(sessionHandle),
                argThat((x) -> {
                    FiraRangingReconfigureParams reconfigureParams =
                            (FiraRangingReconfigureParams) x;
                    return reconfigureParams.getAction().equals(MULTICAST_LIST_UPDATE_ACTION_DELETE)
                            && Arrays.equals(
                                    reconfigureParams.getAddressList(), params.getAddressList())
                            && Arrays.equals(
                                    reconfigureParams.getSubSessionIdList(),
                                    params.getSubSessionIdList());
                }));
    }

    @Test
    public void testStopRanging() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.stopRanging(sessionHandle);

        verify(mUwbSessionManager).stopRanging(sessionHandle);
    }


    @Test
    public void testCloseRanging() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        mUwbServiceCore.closeRanging(sessionHandle);

        verify(mUwbSessionManager).deInitSession(sessionHandle);
    }

    @Test
    public void testGetAdapterState() throws Exception {
        enableUwbWithCountryCodeChangedCallback();
        assertThat(mUwbServiceCore.getAdapterState())
                .isEqualTo(AdapterState.STATE_ENABLED_INACTIVE);

        disableUwb();
        assertThat(mUwbServiceCore.getAdapterState())
                .isEqualTo(AdapterState.STATE_DISABLED);
    }

    @Test
    public void testGetAdapterState_multichip() throws Exception {
        UwbMultichipData multichipData = setUpMultichipDataForTwoChips();
        when(mUwbInjector.getMultichipData()).thenReturn(multichipData);

        mUwbServiceCore = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, mUwbConfigurationManager,
                mUwbInjector, mTestLooper.getLooper());
        multichipData.initialize();

        enableUwbWithCountryCodeChangedCallback();
        assertThat(mUwbServiceCore.getAdapterState())
                .isEqualTo(AdapterState.STATE_ENABLED_INACTIVE);

        // If one chip is active, then getAdapterState should return STATE_ENABLED_ACTIVE.
        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ACTIVE,
                TEST_CHIP_ONE_CHIP_ID);
        mTestLooper.dispatchAll();
        assertThat(mUwbServiceCore.getAdapterState()).isEqualTo(AdapterState.STATE_ENABLED_ACTIVE);

        disableUwb();
        assertThat(mUwbServiceCore.getAdapterState())
                .isEqualTo(AdapterState.STATE_DISABLED);

        // If one chip is disabled, then getAdapter state should always return STATE_DISABLED.
        // (Although in practice, there should never be one ACTIVE chip and one DISABLED chip.)
        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ACTIVE,
                TEST_CHIP_ONE_CHIP_ID);
        mTestLooper.dispatchAll();
        assertThat(mUwbServiceCore.getAdapterState()).isEqualTo(AdapterState.STATE_DISABLED);
    }

    @Test
    public void testGetAdapterState_noChips() throws Exception {
        // Create a new UwbServiceCore instance without initializing multichip data or enabling UWB
        mUwbServiceCore = new UwbServiceCore(mContext, mNativeUwbManager, mUwbMetrics,
                mUwbCountryCode, mUwbSessionManager, mUwbConfigurationManager,
                mUwbInjector, mTestLooper.getLooper());

        assertThat(mUwbServiceCore.getAdapterState()).isEqualTo(AdapterState.STATE_DISABLED);
    }


    @Test
    public void testSendVendorUciCommand() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        int gid = 0;
        int oid = 0;
        byte[] payload = new byte[0];
        UwbVendorUciResponse rsp = new UwbVendorUciResponse(
                (byte) UwbUciConstants.STATUS_CODE_OK, gid, oid, payload);
        when(mNativeUwbManager.sendRawVendorCmd(anyInt(), anyInt(), anyInt(), any(), anyString()))
                .thenReturn(rsp);

        IUwbVendorUciCallback vendorCb = mock(IUwbVendorUciCallback.class);
        mUwbServiceCore.registerVendorExtensionCallback(vendorCb);

        assertThat(mUwbServiceCore.sendVendorUciMessage(1, 0, 0, new byte[0],
                    TEST_DEFAULT_CHIP_ID))
                .isEqualTo(UwbUciConstants.STATUS_CODE_OK);

        verify(vendorCb).onVendorResponseReceived(gid, oid, payload);
    }

    @Test
    public void testSendVendorUciCommandMessageTypeTest() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        int gid = 0;
        int oid = 0;
        byte[] payload = new byte[0];
        UwbVendorUciResponse rsp = new UwbVendorUciResponse(
                (byte) UwbUciConstants.STATUS_CODE_OK, gid, oid, payload);
        when(mNativeUwbManager.sendRawVendorCmd(anyInt(), anyInt(), anyInt(), any(), anyString()))
                .thenReturn(rsp);
        FiraProtocolVersion maxMacVersionSupported = new FiraProtocolVersion(2, 0);
        List<Integer> supportedChannels = List.of(5, 6, 8, 9);
        FiraSpecificationParams firaSpecificationParams = new FiraSpecificationParams.Builder()
                .setMaxMacVersionSupported(maxMacVersionSupported)
                .setSupportedChannels(supportedChannels)
                .build();
        CccSpecificationParams cccSpecificationParams = getTestCccSpecificationParams();

        GenericSpecificationParams genericSpecificationParams =
                new GenericSpecificationParams.Builder()
                        .setFiraSpecificationParams(firaSpecificationParams)
                        .setCccSpecificationParams(cccSpecificationParams)
                        .build();
        when(mUwbConfigurationManager.getCapsInfo(any(), any(), anyString()))
                .thenReturn(Pair.create(
                        UwbUciConstants.STATUS_CODE_OK, genericSpecificationParams));

        IUwbVendorUciCallback vendorCb = mock(IUwbVendorUciCallback.class);
        mUwbServiceCore.registerVendorExtensionCallback(vendorCb);

        assertThat(mUwbServiceCore.sendVendorUciMessage(MESSAGE_TYPE_TEST_1, 0, 0,
                new byte[0], TEST_DEFAULT_CHIP_ID))
                .isEqualTo(UwbUciConstants.STATUS_CODE_OK);

        verify(vendorCb).onVendorResponseReceived(gid, oid, payload);
    }

    @Test
    public void testSendVendorUciCommandUnsupportedMessageType() throws Exception {
        enableUwbWithCountryCodeChangedCallback();
        List<Integer> supportedChannels = List.of(5, 6, 8, 9);
        FiraSpecificationParams firaSpecificationParams = new FiraSpecificationParams.Builder()
                .setSupportedChannels(supportedChannels)
                .build();
        CccSpecificationParams cccSpecificationParams = getTestCccSpecificationParams();
        GenericSpecificationParams genericSpecificationParams =
                new GenericSpecificationParams.Builder()
                        .setFiraSpecificationParams(firaSpecificationParams)
                        .setCccSpecificationParams(cccSpecificationParams)
                        .build();
        when(mUwbConfigurationManager.getCapsInfo(any(), any(), anyString()))
                .thenReturn(Pair.create(
                        UwbUciConstants.STATUS_CODE_OK, genericSpecificationParams));
        IUwbVendorUciCallback vendorCb = mock(IUwbVendorUciCallback.class);
        mUwbServiceCore.registerVendorExtensionCallback(vendorCb);

        assertThat(mUwbServiceCore.sendVendorUciMessage(MESSAGE_TYPE_TEST_1, 0, 0,
                new byte[0], TEST_DEFAULT_CHIP_ID))
                .isEqualTo(STATUS_CODE_FAILED);
    }

    @Test
    public void testQueryDataSize() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);

        when(mUwbSessionManager.queryMaxDataSizeBytes(any())).thenReturn(MAX_DATA_SIZE);
        mUwbServiceCore.queryMaxDataSizeBytes(sessionHandle);
        verify(mUwbSessionManager).queryMaxDataSizeBytes(eq(sessionHandle));
    }

    @Test
    public void testDeviceStateCallback() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ACTIVE,
                TEST_DEFAULT_CHIP_ID);
        mTestLooper.dispatchAll();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE,
                StateChangeReason.SESSION_STARTED);
    }

    @Test
    public void testMultipleDeviceStateCallbacks() throws Exception {
        IUwbAdapterStateCallbacks cb1 = mock(IUwbAdapterStateCallbacks.class);
        when(cb1.asBinder()).thenReturn(mock(IBinder.class));
        IUwbAdapterStateCallbacks cb2 = mock(IUwbAdapterStateCallbacks.class);
        when(cb2.asBinder()).thenReturn(mock(IBinder.class));

        mUwbServiceCore.registerAdapterStateCallbacks(cb1);
        verify(cb1).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        mUwbServiceCore.registerAdapterStateCallbacks(cb2);
        verify(cb2).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        enableUwbWithCountryCodeChangedCallback();
        verify(cb1).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb2).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ACTIVE,
                TEST_DEFAULT_CHIP_ID);
        mTestLooper.dispatchAll();
        verify(cb1).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE,
                StateChangeReason.SESSION_STARTED);
        verify(cb2).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE,
                StateChangeReason.SESSION_STARTED);

        mUwbServiceCore.unregisterAdapterStateCallbacks(cb1);
        mUwbServiceCore.unregisterAdapterStateCallbacks(cb2);
    }

    @Test
    public void testDeviceStateCallback_invalidChipId() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);

        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ACTIVE,
                "invalidChipId");
        verify(cb, never())
                .onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE,
                StateChangeReason.SESSION_STARTED);
    }

    @Test
    public void testToggleOfOnDeviceStateErrorCallback_whenCountryCodeIsValid() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));

        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);

        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);
        when(mNativeUwbManager.doInitialize()).thenReturn(true);

        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // In the second toggle-on iteration, there should be a valid country code known to
        // UwbCountryCode class (from earlier notifications from the Wifi/Telephony stack).
        when(mUwbCountryCode.getCountryCode()).thenReturn("US");
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                mUwbServiceCore.onCountryCodeChanged("US");
                mTestLooper.dispatchAll();
                return Pair.create(STATUS_CODE_OK, true);
            }
        }).when(mUwbCountryCode).setCountryCode(anyBoolean());

        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ERROR,
                TEST_DEFAULT_CHIP_ID);
        mTestLooper.dispatchAll();

        // Verify UWB toggle off.
        verify(mNativeUwbManager).doDeinitialize();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);

        // Verify UWB toggle on.
        verify(mNativeUwbManager).doInitialize();
        verify(cb).onAdapterStateChanged(
                UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
    }

    @Test
    public void testToggleOfOnDeviceStateErrorCallback_whenCountryCodeIsInvalid_setCountryFailure()
            throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));

        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);
        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // UWB Adapter State will internally be Enabled, but we expect an AdapterState notification
        // with State DISABLED to be sent (as the country code is not valid).
        when(mUwbCountryCode.getCountryCode()).thenReturn(null);
        enableUwb();
        verify(cb).onAdapterStateChanged(
                UwbManager.AdapterStateCallback.STATE_DISABLED, StateChangeReason.SYSTEM_POLICY);
        verifyNoMoreInteractions(cb);
        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        // UWB Enable is expected to result in setting the country code; but that may fail and the
        // onCountryCode() callback is not invoked, as the country code is not valid.
        when(mUwbCountryCode.setCountryCode(anyBoolean())).thenReturn(
                Pair.create(STATUS_CODE_FAILED, null));
        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);
        when(mNativeUwbManager.doInitialize()).thenReturn(true);

        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ERROR,
                TEST_DEFAULT_CHIP_ID);
        mTestLooper.dispatchAll();

        // Verify UWB is first toggled off and then on. There should be no AdapterStateCallback
        // sent, as the country code is invalid and so the AdapterState for notification remains
        // the same as before (STATE_DISABLED).
        verify(mNativeUwbManager).doDeinitialize();
        verify(mNativeUwbManager).doInitialize();
        verifyNoMoreInteractions(cb);
    }

    @Test
    public void testToggleOfOnDeviceStateErrorCallback_invalidChipId() throws Exception {
        IUwbAdapterStateCallbacks cb = mock(IUwbAdapterStateCallbacks.class);
        when(cb.asBinder()).thenReturn(mock(IBinder.class));
        mUwbServiceCore.registerAdapterStateCallbacks(cb);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_BOOT);

        // Enable UWB to initialize state.
        enableUwbWithCountryCodeChangedCallback();
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_DISABLED,
                StateChangeReason.SYSTEM_POLICY);
        verify(cb).onAdapterStateChanged(UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE,
                StateChangeReason.SYSTEM_POLICY);
        clearInvocations(mNativeUwbManager, mUwbCountryCode, cb);

        when(mNativeUwbManager.doDeinitialize()).thenReturn(true);
        when(mNativeUwbManager.doInitialize()).thenReturn(true);

        mUwbServiceCore.onDeviceStatusNotificationReceived(UwbUciConstants.DEVICE_STATE_ERROR,
                "invalidChipId");
        mTestLooper.dispatchAll();

        // Verify there are no more UWB stack or state updates (since chipId is invalid).
        verifyNoMoreInteractions(mNativeUwbManager, mUwbCountryCode, cb);
    }

    @Test
    public void testVendorUciNotificationCallback() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        IUwbVendorUciCallback vendorCb = mock(IUwbVendorUciCallback.class);
        mUwbServiceCore.registerVendorExtensionCallback(vendorCb);
        int gid = 0;
        int oid = 0;
        byte[] payload = new byte[0];
        mUwbServiceCore.onVendorUciNotificationReceived(gid, oid, payload);
        verify(vendorCb).onVendorNotificationReceived(gid, oid, payload);

        mUwbServiceCore.unregisterVendorExtensionCallback(vendorCb);
        mUwbServiceCore.onVendorUciNotificationReceived(gid, oid, payload);
        verifyZeroInteractions(vendorCb);
    }

    @Test
    public void testReportUwbActivityEnergyInfo() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        IOnUwbActivityEnergyInfoListener listener = mock(IOnUwbActivityEnergyInfoListener.class);
        mUwbServiceCore.reportUwbActivityEnergyInfo(listener);
        mTestLooper.dispatchAll();
        verify(listener).onUwbActivityEnergyInfo(any());
    }

    @Test
    public void testOemExtensionCallback_registerUnregister() throws RemoteException {
        IUwbOemExtensionCallback oemExtensionCb = mock(IUwbOemExtensionCallback.class);

        assertFalse(mUwbServiceCore.isOemExtensionCbRegistered());
        assertThat(mUwbServiceCore.getOemExtensionCallback()).isNull();

        mUwbServiceCore.registerOemExtensionCallback(oemExtensionCb);
        assertTrue(mUwbServiceCore.isOemExtensionCbRegistered());
        assertThat(mUwbServiceCore.getOemExtensionCallback()).isNotNull();

        mUwbServiceCore.updateDeviceState(0, "");
        verify(oemExtensionCb).onDeviceStatusNotificationReceived(any());

        mUwbServiceCore.unregisterOemExtensionCallback(oemExtensionCb);

        assertFalse(mUwbServiceCore.isOemExtensionCbRegistered());
        assertThat(mUwbServiceCore.getOemExtensionCallback()).isNull();
    }

    @Test
    public void testRangingRoundsUpdateDtTag() throws Exception {
        enableUwbWithCountryCodeChangedCallback();

        SessionHandle sessionHandle = mock(SessionHandle.class);
        PersistableBundle bundle = new PersistableBundle();
        mUwbServiceCore.rangingRoundsUpdateDtTag(sessionHandle, bundle);

        verify(mUwbSessionManager).rangingRoundsUpdateDtTag(sessionHandle, bundle);
    }

    public CccSpecificationParams getTestCccSpecificationParams() {
        CccProtocolVersion[] protocolVersions =
                new CccProtocolVersion[] {
                        new CccProtocolVersion(1, 0),
                        new CccProtocolVersion(2, 0),
                        new CccProtocolVersion(2, 1)
                };

        Integer[] uwbConfigs = new Integer[] {CccParams.UWB_CONFIG_0, CccParams.UWB_CONFIG_1};
        CccPulseShapeCombo[] pulseShapeCombos =
                new CccPulseShapeCombo[] {
                        new CccPulseShapeCombo(
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE,
                                CccParams.PULSE_SHAPE_SYMMETRICAL_ROOT_RAISED_COSINE),
                        new CccPulseShapeCombo(
                                CccParams.PULSE_SHAPE_PRECURSOR_FREE,
                                CccParams.PULSE_SHAPE_PRECURSOR_FREE),
                        new CccPulseShapeCombo(
                                CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL,
                                CccParams.PULSE_SHAPE_PRECURSOR_FREE_SPECIAL)
                };
        int ranMultiplier = 200;
        Integer[] chapsPerSlots =
                new Integer[] {CccParams.CHAPS_PER_SLOT_4, CccParams.CHAPS_PER_SLOT_12};
        Integer[] syncCodes =
                new Integer[] {10, 23};
        Integer[] channels = new Integer[] {CccParams.UWB_CHANNEL_5, CccParams.UWB_CHANNEL_9};
        Integer[] hoppingConfigModes =
                new Integer[] {
                        CccParams.HOPPING_CONFIG_MODE_ADAPTIVE,
                        CccParams.HOPPING_CONFIG_MODE_CONTINUOUS };
        Integer[] hoppingSequences =
                new Integer[] {CccParams.HOPPING_SEQUENCE_AES, CccParams.HOPPING_SEQUENCE_DEFAULT};

        CccSpecificationParams.Builder paramsBuilder = new CccSpecificationParams.Builder();
        for (CccProtocolVersion p : protocolVersions) {
            paramsBuilder.addProtocolVersion(p);
        }

        for (int uwbConfig : uwbConfigs) {
            paramsBuilder.addUwbConfig(uwbConfig);
        }

        for (CccPulseShapeCombo pulseShapeCombo : pulseShapeCombos) {
            paramsBuilder.addPulseShapeCombo(pulseShapeCombo);
        }

        paramsBuilder.setRanMultiplier(ranMultiplier);

        for (int chapsPerSlot : chapsPerSlots) {
            paramsBuilder.addChapsPerSlot(chapsPerSlot);
        }

        for (int syncCode : syncCodes) {
            paramsBuilder.addSyncCode(syncCode);
        }

        for (int channel : channels) {
            paramsBuilder.addChannel(channel);
        }

        for (int hoppingConfigMode : hoppingConfigModes) {
            paramsBuilder.addHoppingConfigMode(hoppingConfigMode);
        }

        for (int hoppingSequence : hoppingSequences) {
            paramsBuilder.addHoppingSequence(hoppingSequence);
        }
        return paramsBuilder.build();
    }
}
