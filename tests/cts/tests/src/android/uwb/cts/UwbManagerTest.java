/*
 * Copyright 2021 The Android Open Source Project
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

package android.uwb.cts;

import static android.Manifest.permission.UWB_PRIVILEGED;
import static android.Manifest.permission.UWB_RANGING;
import static android.uwb.RangingMeasurement.NON_HYBRID_UWB_SESSION_ID;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_DISABLED;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_ACTIVE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_HW_IDLE;
import static android.uwb.UwbManager.AdapterStateCallback.STATE_ENABLED_INACTIVE;
import static android.uwb.UwbManager.MESSAGE_TYPE_COMMAND;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.PropertyUtil.getVsrApiLevel;

import static com.google.common.truth.Truth.assertThat;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T64_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_BPRF;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_DT_TAG;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP1;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.radar.RadarParams.BITS_PER_SAMPLES_32;
import static com.google.uwb.support.radar.RadarParams.NUMBER_OF_BURSTS_DEFAULT;
import static com.google.uwb.support.radar.RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;
import static com.google.uwb.support.radar.RadarParams.SAMPLES_PER_SWEEP_DEFAULT;
import static com.google.uwb.support.radar.RadarParams.SESSION_PRIORITY_DEFAULT;
import static com.google.uwb.support.radar.RadarParams.SWEEP_OFFSET_DEFAULT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.UiAutomation;
import android.bluetooth.BluetoothDevice;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextParams;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;
import android.uwb.LogicalLinkConnectionParams;
import android.uwb.LogicalLinkConnectionRequest;
import android.uwb.LogicalLinkCreationParams;
import android.uwb.RangingMeasurement;
import android.uwb.RangingReport;
import android.uwb.RangingSession;
import android.uwb.UwbActivityEnergyInfo;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;
import android.uwb.timesync.TimesyncEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.modules.utils.build.SdkLevel;
import com.android.uwb.flags.Flags;

import com.google.uwb.support.aliro.AliroOpenRangingParams;
import com.google.uwb.support.aliro.AliroParams;
import com.google.uwb.support.aliro.AliroProtocolVersion;
import com.google.uwb.support.aliro.AliroPulseShapeCombo;
import com.google.uwb.support.aliro.AliroSpecificationParams;
import com.google.uwb.support.aliro.AliroStartRangingParams;
import com.google.uwb.support.dltdoa.DlTDoAMeasurement;
import com.google.uwb.support.dltdoa.DlTDoARangingRoundsUpdate;
import com.google.uwb.support.fira.FiraControleeParams;
import com.google.uwb.support.fira.FiraLogicalLinkInfo;
import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraPoseUpdateParams;
import com.google.uwb.support.fira.FiraProtocolVersion;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;
import com.google.uwb.support.fira.FiraSpecificationParams;
import com.google.uwb.support.fira.FiraSuspendRangingParams;
import com.google.uwb.support.multichip.ChipInfoParams;
import com.google.uwb.support.oemextension.DeviceStatus;
import com.google.uwb.support.oemextension.RangingReportMetadata;
import com.google.uwb.support.oemextension.SessionConfigParams;
import com.google.uwb.support.oemextension.SessionStatus;
import com.google.uwb.support.radar.RadarOpenSessionParams;
import com.google.uwb.support.radar.RadarParams;
import com.google.uwb.support.radar.RadarSpecificationParams;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Test of {@link UwbManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get UwbManager in instant app mode")
public class UwbManagerTest {
    private static final String TAG = "UwbManagerTest";

    private final Context mContext = InstrumentationRegistry.getContext();
    private UwbManager mUwbManager;
    private String mDefaultChipId;
    public static final int UWB_SESSION_STATE_IDLE = 0x03;
    public static final byte DEVICE_STATE_ACTIVE = 0x02;
    public static final int REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS = 0x00;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();


    @Before
    public void setup() throws Exception {
        mUwbManager = mContext.getSystemService(UwbManager.class);
        assumeTrue(UwbTestUtils.isUwbSupported(mContext));
        assertThat(mUwbManager).isNotNull();

        // Ensure UWB is toggled on.
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            if (!mUwbManager.isUwbEnabled()) {
                setUwbEnabledAndWaitForCompletion(true);
            }
            if (com.android.uwb.flags.Flags.hwState() && mUwbManager.isUwbHwIdleTurnOffEnabled()) {
                // If HW idle mode is turned on, vote for the UWB hardware for tests to pass.
                requestUwbHwEnabledAndWaitForCompletion(true, mUwbManager, true);
            }
            mDefaultChipId = mUwbManager.getDefaultChipId();
            // Clear oem extension callback if registered.
            UwbOemExtensionCallback uwbOemExtensionCallback =
                    new UwbOemExtensionCallback(new CountDownLatch(1));
            mUwbManager.registerUwbOemExtensionCallback(
                    Executors.newSingleThreadExecutor(), uwbOemExtensionCallback);
            mUwbManager.unregisterUwbOemExtensionCallback(uwbOemExtensionCallback);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @After
    public void teardown() throws Exception {
        if (!UwbTestUtils.isUwbSupported(mContext)) return;
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            if (com.android.uwb.flags.Flags.hwState() && mUwbManager.isUwbHwIdleTurnOffEnabled()) {
                // If HW idle mode is turned on, reset vote for the UWB hardware.
                requestUwbHwEnabledAndWaitForCompletion(false, mUwbManager, false);
            }
            mDefaultChipId = mUwbManager.getDefaultChipId();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    // Should be invoked with shell permissions.
    private void setUwbEnabledAndWaitForCompletion(boolean enabled) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        int adapterState = enabled ? STATE_ENABLED_INACTIVE : STATE_DISABLED;
        AdapterStateCallback adapterStateCallback =
                new AdapterStateCallback(countDownLatch, adapterState);
        try {
            mUwbManager.registerAdapterStateCallback(
                    Executors.newSingleThreadExecutor(), adapterStateCallback);
            mUwbManager.setUwbEnabled(enabled);
            assertThat(countDownLatch.await(6, TimeUnit.SECONDS)).isTrue();
            assertThat(mUwbManager.isUwbEnabled()).isEqualTo(enabled);
            assertThat(adapterStateCallback.state).isEqualTo(adapterState);
        } finally {
            mUwbManager.unregisterAdapterStateCallback(adapterStateCallback);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfo() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle persistableBundle = mUwbManager.getSpecificationInfo();
            assertThat(persistableBundle).isNotNull();
            assertThat(persistableBundle.isEmpty()).isFalse();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle persistableBundle =
                    mUwbManager.getSpecificationInfo(mDefaultChipId);
            assertThat(persistableBundle).isNotNull();
            assertThat(persistableBundle.isEmpty()).isFalse();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetChipInfos() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            List<PersistableBundle> chipInfos = mUwbManager.getChipInfos();
            assertThat(chipInfos).hasSize(1);
            ChipInfoParams chipInfoParams = ChipInfoParams.fromBundle(chipInfos.get(0));
            assertThat(chipInfoParams.getChipId()).isEqualTo(mDefaultChipId);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithInvalidChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThrows(IllegalArgumentException.class,
                    () -> mUwbManager.getSpecificationInfo("invalidChipId"));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithoutUwbPrivileged() {
        try {
            mUwbManager.getSpecificationInfo();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetSpecificationInfoWithChipIdWithoutUwbPrivileged() {
        try {
            mUwbManager.getSpecificationInfo(mDefaultChipId);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.uwb.flags.query_timestamp_micros")
    public void testQueryUwbsTimestampMicros() {
        assumeTrue(SdkLevel.isAtLeastV());
        FiraProtocolVersion firaProtocolVersion =
                getFiraSpecificationParams().getMaxMacVersionSupported();
        assumeTrue(firaProtocolVersion.getMajor() >= 2);

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            long prev = mUwbManager.queryUwbsTimestampMicros();
            for (int i = 0; i < 10; i++) {
                Thread.sleep(1); // Sleep for 1ms.
                long next = mUwbManager.queryUwbsTimestampMicros();
                // Accounting for 1ms sleep.
                assertTrue(next - prev > 1_000);
                prev = next;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.uwb.flags.query_timestamp_micros")
    public void testQueryUwbsTimestampMicrosPrivileged() {
        assumeTrue(SdkLevel.isAtLeastV());

        try {
            mUwbManager.queryUwbsTimestampMicros();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            // pass
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanos() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.elapsedRealtimeResolutionNanos() >= 0L).isTrue();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.elapsedRealtimeResolutionNanos(mDefaultChipId) >= 0L)
                    .isTrue();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithInvalidChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThrows(IllegalArgumentException.class,
                    () -> mUwbManager.elapsedRealtimeResolutionNanos("invalidChipId"));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithoutUwbPrivileged() {
        try {
            mUwbManager.elapsedRealtimeResolutionNanos();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testElapsedRealtimeResolutionNanosWithChipIdWithoutUwbPrivileged() {
        try {
            mUwbManager.elapsedRealtimeResolutionNanos(mDefaultChipId);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testAddServiceProfileWithoutUwbPrivileged() {
        try {
            mUwbManager.addServiceProfile(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRemoveServiceProfileWithoutUwbPrivileged() {
        try {
            mUwbManager.removeServiceProfile(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }


    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetAllServiceProfilesWithoutUwbPrivileged() {
        try {
            mUwbManager.getAllServiceProfiles();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetAdfProvisioningAuthoritiesWithoutUwbPrivileged() {
        try {
            mUwbManager.getAdfProvisioningAuthorities(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetAdfCertificateInfoWithoutUwbPrivileged() {
        try {
            mUwbManager.getAdfCertificateInfo(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetChipInfosWithoutUwbPrivileged() {
        try {
            mUwbManager.getChipInfos();
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciWithoutUwbPrivileged() {
        try {
            mUwbManager.sendVendorUciMessage(10, 0, new byte[0]);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    private class AdfProvisionStateCallback extends UwbManager.AdfProvisionStateCallback {
        private final CountDownLatch mCountDownLatch;

        public boolean onSuccessCalled;
        public boolean onFailedCalled;

        AdfProvisionStateCallback(@NonNull CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onProfileAdfsProvisioned(@NonNull PersistableBundle params) {
            onSuccessCalled = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onProfileAdfsProvisionFailed(int reason, @NonNull PersistableBundle params) {
            onFailedCalled = true;
            mCountDownLatch.countDown();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testProvisionProfileAdfByScriptWithoutUwbPrivileged() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdfProvisionStateCallback adfProvisionStateCallback =
                new AdfProvisionStateCallback(countDownLatch);
        try {
            mUwbManager.provisionProfileAdfByScript(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    adfProvisionStateCallback);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRemoveProfileAdfWithoutUwbPrivileged() {
        try {
            mUwbManager.removeProfileAdf(new PersistableBundle());
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    private class UwbVendorUciCallback implements UwbManager.UwbVendorUciCallback {
        private final CountDownLatch mRspCountDownLatch;
        private final CountDownLatch mNtfCountDownLatch;

        public int gid;
        public int oid;
        public byte[] payload;

        UwbVendorUciCallback(
                @NonNull CountDownLatch rspCountDownLatch,
                @NonNull CountDownLatch ntfCountDownLatch) {
            mRspCountDownLatch = rspCountDownLatch;
            mNtfCountDownLatch = ntfCountDownLatch;
        }

        @Override
        public void onVendorUciResponse(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mRspCountDownLatch.countDown();
        }

        @Override
        public void onVendorUciNotification(int gid, int oid, byte[] payload) {
            this.gid = gid;
            this.oid = oid;
            this.payload = payload;
            mNtfCountDownLatch.countDown();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRegisterVendorUciCallbackWithoutUwbPrivileged() {
        UwbManager.UwbVendorUciCallback cb =
                new UwbVendorUciCallback(new CountDownLatch(1), new CountDownLatch(1));
        try {
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testUnregisterVendorUciCallbackWithoutUwbPrivileged() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        UwbManager.UwbVendorUciCallback cb =
                new UwbVendorUciCallback(new CountDownLatch(1), new CountDownLatch(1));
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);
        } catch (SecurityException e) {
            /* pass */
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        try {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
        try {
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            /* pass */
        } catch (SecurityException e) {
            /* fail */
            fail();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testInvalidCallbackUnregisterVendorUciCallback() {
        UwbManager.UwbVendorUciCallback cb =
                new UwbVendorUciCallback(new CountDownLatch(1), new CountDownLatch(1));
        try {
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);
        } catch (SecurityException e) {
            /* registration failed */
        }
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.unregisterUwbVendorUciCallback(cb);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private class RangingSessionCallback implements RangingSession.Callback {
        private CountDownLatch mCtrlCountDownLatch;
        private CountDownLatch mResultCountDownLatch;
        public boolean onOpenedCalled;
        public boolean onOpenFailedCalled;
        public boolean onStartedCalled;
        public boolean onStartFailedCalled;
        public boolean onReconfiguredCalled;
        public boolean onReconfiguredFailedCalled;
        public boolean onStoppedCalled;
        public boolean onClosedCalled;
        public boolean onControleeAddCalled;
        public boolean onControleeAddFailedCalled;
        public boolean onControleeRemoveCalled;
        public boolean onControleeRemoveFailedCalled;
        public boolean onUpdateDtTagStatusCalled;
        public boolean onLogicalLinkCreatedCalled;
        public boolean onLogicalLinkCreationFailedCalled;
        public boolean onLogicalLinkClosedCalled;
        public boolean onLogicalLinkClosureFailedCalled;
        public boolean onDataSentCalled;
        public boolean onDataSendFailedCalled;
        public boolean onPauseCalled;
        public boolean onPauseFailedCalled;
        public boolean onResumeCalled;
        public boolean onResumeFailedCalled;
        public boolean onDataReceived;
        public RangingSession rangingSession;
        public RangingReport rangingReport;
        public int connectId;

        RangingSessionCallback(
                @NonNull CountDownLatch ctrlCountDownLatch) {
            this(ctrlCountDownLatch, null /* resultCountDownLaynch */);
        }

        RangingSessionCallback(
                @NonNull CountDownLatch ctrlCountDownLatch,
                @Nullable CountDownLatch resultCountDownLatch) {
            mCtrlCountDownLatch = ctrlCountDownLatch;
            mResultCountDownLatch = resultCountDownLatch;
        }

        public void replaceCtrlCountDownLatch(@NonNull CountDownLatch countDownLatch) {
            mCtrlCountDownLatch = countDownLatch;
        }

        public void replaceResultCountDownLatch(@NonNull CountDownLatch countDownLatch) {
            mResultCountDownLatch = countDownLatch;
        }

        public void onOpened(@NonNull RangingSession session) {
            onOpenedCalled = true;
            rangingSession = session;
            mCtrlCountDownLatch.countDown();
        }

        public void onOpenFailed(int reason, @NonNull PersistableBundle params) {
            onOpenFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onStarted(@NonNull PersistableBundle sessionInfo) {
            onStartedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onStartFailed(int reason, @NonNull PersistableBundle params) {
            onStartFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onReconfigured(@NonNull PersistableBundle params) {
            onReconfiguredCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onReconfigureFailed(int reason, @NonNull PersistableBundle params) {
            onReconfiguredFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onStopped(int reason, @NonNull PersistableBundle parameters) {
            onStoppedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onStopFailed(int reason, @NonNull PersistableBundle params) {
        }

        public void onClosed(int reason, @NonNull PersistableBundle parameters) {
            onClosedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onReportReceived(@NonNull RangingReport rangingReport) {
            if (mResultCountDownLatch != null) {
                this.rangingReport = rangingReport;
                mResultCountDownLatch.countDown();
            }
        }

        public void onControleeAdded(PersistableBundle params) {
            onControleeAddCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onControleeAddFailed(int reason, PersistableBundle params) {
            onControleeAddFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onControleeRemoved(PersistableBundle params) {
            onControleeRemoveCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onControleeRemoveFailed(int reason, PersistableBundle params) {
            onControleeRemoveFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onPaused(PersistableBundle params) {
            onPauseCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onPauseFailed(int reason, PersistableBundle params) {
            onPauseFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onResumed(PersistableBundle params) {
            onResumeCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onResumeFailed(int reason, PersistableBundle params) {
            onResumeFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onDataSent(UwbAddress remoteDeviceAddress, PersistableBundle params) {
            onDataSentCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onDataSendFailed(UwbAddress remoteDeviceAddress,
                int reason, PersistableBundle params) {
            onDataSendFailedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onDataReceived(UwbAddress remoteDeviceAddress,
                PersistableBundle params, byte[] data) {
            onDataReceived = true;
            mResultCountDownLatch.countDown();
        }

        public void onDataReceiveFailed(UwbAddress remoteDeviceAddress,
                int reason, PersistableBundle params) {
        }

        public void onServiceDiscovered(PersistableBundle params) {
        }

        public void onServiceConnected(PersistableBundle params) {
        }

        public void onRangingRoundsUpdateDtTagStatus(@NonNull PersistableBundle parameters) {
            onUpdateDtTagStatusCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onLogicalLinkCreated(@NonNull LogicalLinkCreationParams params, int connectId) {
            this.connectId = connectId;
            onLogicalLinkCreatedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onLogicalLinkCreationFailed(@NonNull LogicalLinkCreationParams params,
                int status) {
            onLogicalLinkCreationFailedCalled = true;
        }

        public void onLogicalLinkClosed(int connectId, int reason) {
            onLogicalLinkClosedCalled = true;
            mCtrlCountDownLatch.countDown();
        }

        public void onLogicalLinkClosureFailed(int connectId, int status) {
            onLogicalLinkClosureFailedCalled = true;
        }

        public void onRemoteLogicalLinkRequested(@NonNull LogicalLinkConnectionRequest linkInfo) {
        }

        public void onControleeRoleChanged(int deviceRole) {
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithInvalidChipId() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            assertThrows(IllegalArgumentException.class, () -> mUwbManager.openRangingSession(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    "invalidChipId"));
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithChipIdWithBadParams() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            cancellationSignal = mUwbManager.openRangingSession(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on start failed callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isFalse();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithInvalidChipIdWithBadParams() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback = new RangingSessionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Try to start a ranging session with invalid params, should fail.
            cancellationSignal = mUwbManager.openRangingSession(
                    new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on start failed callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isFalse();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                cancellationSignal.cancel();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Simulates the app holding UWB_RANGING permission, but not UWB_PRIVILEGED.
     */
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithoutUwbPrivileged() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_RANGING permission
            uiAutomation.adoptShellPermissionIdentity(UWB_RANGING);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)));
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithChipIdWithoutUwbPrivileged() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_RANGING permission
            uiAutomation.adoptShellPermissionIdentity(UWB_RANGING);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)),
                    mDefaultChipId);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * Simulates the app holding UWB_PRIVILEGED permission, but not UWB_RANGING.
     */
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithoutUwbRanging() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity(UWB_PRIVILEGED);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)));
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testOpenRangingSessionWithChipIdWithoutUwbRanging() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity(UWB_PRIVILEGED);
            mUwbManager.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)),
                    mDefaultChipId);
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private AttributionSource getShellAttributionSourceWithRenouncedPermissions(
            @Nullable Set<String> renouncedPermissions) {
        try {
            // Calculate the shellUid to account for running this from a secondary user.
            int shellUid = UserHandle.getUid(
                    Process.myUserHandle().getIdentifier(), UserHandle.getAppId(Process.SHELL_UID));
            AttributionSource shellAttributionSource =
                    new AttributionSource.Builder(shellUid)
                            .setPackageName("com.android.shell")
                            .setRenouncedPermissions(renouncedPermissions)
                            .build();
            PermissionManager permissionManager =
                    mContext.getSystemService(PermissionManager.class);
            permissionManager.registerAttributionSource(shellAttributionSource);
            return shellAttributionSource;
        } catch (SecurityException e) {
            fail("Failed to create shell attribution source" + e);
            return null;
        }
    }

    private Context createShellContextWithRenouncedPermissionsAndAttributionSource(
            @Nullable Set<String> renouncedPermissions) {
        return mContext.createContext(new ContextParams.Builder()
                .setRenouncedPermissions(renouncedPermissions)
                .setNextAttributionSource(
                        getShellAttributionSourceWithRenouncedPermissions(renouncedPermissions))
                .build());
    }

    /**
     * Simulates the calling app holding UWB_PRIVILEGED permission and UWB_RANGING permission,
     * but
     * the proxied app not holding UWB_RANGING permission.
     */
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testOpenRangingSessionWithoutUwbRangingInNextAttributeSource() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            Context shellContextWithUwbRangingRenounced =
                    createShellContextWithRenouncedPermissionsAndAttributionSource(
                            Set.of(UWB_RANGING));
            UwbManager uwbManagerWithUwbRangingRenounced =
                    shellContextWithUwbRangingRenounced.getSystemService(UwbManager.class);
            uwbManagerWithUwbRangingRenounced.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)));
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testOpenRangingSessionWithChipIdWithoutUwbRangingInNextAttributeSource() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            Context shellContextWithUwbRangingRenounced =
                    createShellContextWithRenouncedPermissionsAndAttributionSource(
                            Set.of(UWB_RANGING));
            UwbManager uwbManagerWithUwbRangingRenounced =
                    shellContextWithUwbRangingRenounced.getSystemService(UwbManager.class);
            uwbManagerWithUwbRangingRenounced.openRangingSession(new PersistableBundle(),
                    Executors.newSingleThreadExecutor(),
                    new RangingSessionCallback(new CountDownLatch(1)),
                    mDefaultChipId);
            // should fail if the call was successful without UWB_RANGING permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private FiraOpenSessionParams.Builder makeOpenSessionBuilder() {
        return new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(1, 1))
                .setSessionId(1)
                .setSessionType(FiraParams.SESSION_TYPE_RANGING)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 0x6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x5, 0x7})));
    }

    private interface VerifyRangingReportInterface {
        void verify(RangingReport rangingReport) throws Exception;
    }

    private interface RunOperationWhenSessionIsRunningInterface {
        void run(@NonNull RangingSessionCallback rangingSessionCallback) throws Exception;
    }

    private void verifyFiraRangingSession(
            @NonNull FiraOpenSessionParams firaOpenSessionParams,
            @Nullable VerifyRangingReportInterface verifyRangingReport,
            @Nullable RunOperationWhenSessionIsRunningInterface runOperationWhenSessionIsRunning)
            throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    firaOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback.rangingSession).isNotNull();

            if (firaOpenSessionParams.getDeviceRole() == RANGING_DEVICE_DT_TAG) {
                runOperationWhenSessionIsRunning.run(rangingSessionCallback);
                runOperationWhenSessionIsRunning = null;
            }

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStartedCalled).isTrue();
            assertThat(rangingSessionCallback.onStartFailedCalled).isFalse();

            // Wait for the on ranging report callback.
            assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.rangingReport).isNotNull();

            // If the test needs to verify the ranging report, do it now.
            if (verifyRangingReport != null) {
                verifyRangingReport.verify(rangingSessionCallback.rangingReport);
            }

            // If the test needs any operation to be run when the session is ongoing, do it now.
            if (runOperationWhenSessionIsRunning != null) {
                runOperationWhenSessionIsRunning.run(rangingSessionCallback);
            }

            // Check the UWB state.
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();

            // Wait for on stopped callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Close session.
                cancellationSignal.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(rangingSessionCallback.onClosedCalled).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private FiraSpecificationParams getFiraSpecificationParams() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle bundle = mUwbManager.getSpecificationInfo();
            if (bundle.keySet().contains(FiraParams.PROTOCOL_NAME)) {
                bundle = requireNonNull(bundle.getPersistableBundle(FiraParams.PROTOCOL_NAME));
            }
            return FiraSpecificationParams.fromBundle(bundle);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private RadarSpecificationParams getRadarSpecificationParams() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle bundle = mUwbManager.getSpecificationInfo();
            if (bundle.keySet().contains(RadarParams.PROTOCOL_NAME)) {
                bundle = requireNonNull(bundle.getPersistableBundle(RadarParams.PROTOCOL_NAME));
            } else {
                Log.i(TAG, "No Radar specification info found.");
                return null;
            }
            return RadarSpecificationParams.fromBundle(bundle);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private AliroSpecificationParams getAliroSpecificationParams() {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Only hold UWB_PRIVILEGED permission
            uiAutomation.adoptShellPermissionIdentity();
            PersistableBundle bundle = mUwbManager.getSpecificationInfo();
            if (bundle.keySet().contains(AliroParams.PROTOCOL_NAME)) {
                bundle = requireNonNull(bundle.getPersistableBundle(AliroParams.PROTOCOL_NAME));
            } else {
                Log.i(TAG, "No Aliro specification info found.");
                return null;
            }
            return AliroSpecificationParams.fromBundle(bundle);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSession() throws Exception {
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .build();
        verifyFiraRangingSession(firaOpenSessionParams, null, null);
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    @RequiresFlagsEnabled("com.android.uwb.flags.query_timestamp_micros")
    public void testQueryMaxDataSizeBytesWithNoPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    try {
                        rangingSessionCallback.rangingSession.queryMaxDataSizeBytes();
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    @RequiresFlagsEnabled("com.android.uwb.flags.data_transfer_phase_config")
    public void testsetDataTransferPhaseConfigWithNoPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    try {
                        rangingSessionCallback.rangingSession.setDataTransferPhaseConfig(
                                new PersistableBundle());
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    @RequiresFlagsEnabled("com.android.uwb.flags.hybrid_session_support")
    public void testsetHybridSessionControllerConfigurationWithNoPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    try {
                        rangingSessionCallback.rangingSession
                                .setHybridSessionControllerConfiguration(new PersistableBundle());
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    @RequiresFlagsEnabled("com.android.uwb.flags.hybrid_session_support")
    public void testsetHybridSessionControleeConfigurationWithNoPermission() throws Exception {
        assumeTrue(SdkLevel.isAtLeastV());
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    try {
                        rangingSessionCallback.rangingSession
                                .setHybridSessionControleeConfiguration(new PersistableBundle());
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testUpdateRangingRoundsDtTagWithNoPermissions() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder().build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    DlTDoARangingRoundsUpdate rangingRoundsUpdate =
                            new DlTDoARangingRoundsUpdate.Builder()
                                    .setSessionId(1)
                                    .setNoOfRangingRounds(1)
                                    .setRangingRoundIndexes(new byte[]{0})
                                    .build();
                    try {
                        rangingSessionCallback.rangingSession.updateRangingRoundsDtTag(
                                rangingRoundsUpdate.toBundle());
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Ignore // Disabled in U as FiRa 2.0 is not fully formalized.
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testDlTdoaRangingSession() throws Exception {
        FiraSpecificationParams params = getFiraSpecificationParams();
        FiraProtocolVersion firaProtocolVersion = params.getMaxMacVersionSupported();
        assumeTrue(params.getRangingRoundCapabilities().contains(
                FiraParams.RangingRoundCapabilityFlag.HAS_OWR_DL_TDOA_SUPPORT));

        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(2, 0))
                .setSessionId(1)
                .setSessionType(FiraParams.SESSION_TYPE_RANGING)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_DT_TAG)
                .setDeviceRole(FiraParams.RANGING_DEVICE_DT_TAG)
                .setRangingRoundUsage(FiraParams.RANGING_ROUND_USAGE_DL_TDOA)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 6}))
                .setRframeConfig(RFRAME_CONFIG_SP1)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                (rangingReport) -> {
                    RangingMeasurement rangingMeasurement =
                            rangingReport.getMeasurements().get(0);
                    PersistableBundle rangingMeasurementMetadata =
                            rangingMeasurement.getRangingMeasurementMetadata();
                    assertThat(DlTDoAMeasurement.isDlTDoAMeasurement(rangingMeasurementMetadata))
                            .isTrue();
                },
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    DlTDoARangingRoundsUpdate rangingRoundsUpdate =
                            new DlTDoARangingRoundsUpdate.Builder()
                                    .setSessionId(1)
                                    .setNoOfRangingRounds(1)
                                    .setRangingRoundIndexes(new byte[]{0})
                                    .build();

                    // Update Ranging Rounds for DT Tag.
                    rangingSessionCallback.rangingSession.updateRangingRoundsDtTag(
                            rangingRoundsUpdate.toBundle());
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onUpdateDtTagStatusCalled).isTrue();
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testSendDataWithNoPermission() throws Exception {
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder().build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    try {
                        rangingSessionCallback.rangingSession.sendData(
                                UwbAddress.fromBytes(new byte[]{0x1, 0x2}),
                                new PersistableBundle(),
                                new byte[]{0x01, 0x02, 0x03, 0x04}
                        );
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Ignore // Disabled in U as FiRa 2.0 is not fully formalized.
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testAdvertisingRangingSession() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();

        FiraSpecificationParams params = getFiraSpecificationParams();
        FiraProtocolVersion firaProtocolVersion = params.getMaxMacVersionSupported();
        assumeTrue(params.getRangingRoundCapabilities().contains(
                FiraParams.RangingRoundCapabilityFlag.HAS_OWR_AOA_SUPPORT));

        // Setup the Fira Configuration Parameters.
        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(2, 0))
                .setSessionId(1)
                .setSessionType(FiraParams.SESSION_TYPE_RANGING)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                // TODO(b/275077682): We likely don't need to set the DeviceType for an OWR_AoA
                // ranging session, update the test based on the bug.
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_OBSERVER)
                .setRframeConfig(RFRAME_CONFIG_SP1)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .setRangingRoundUsage(FiraParams.RANGING_ROUND_USAGE_OWR_AOA_MEASUREMENT)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 0x6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x5, 0x6})))
                .build();

        // Register the UwbOemExtensionCallback with UwbManager, this requires both an API SDK
        // level of at least U, and UWB_PRIVILEGED permission.
        assumeTrue(SdkLevel.isAtLeastU());
        CountDownLatch oemExtensionCountDownLatch = new CountDownLatch(1);
        UwbOemExtensionCallback uwbOemExtensionCallback =
                new UwbOemExtensionCallback(oemExtensionCountDownLatch);
        try {
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbOemExtensionCallback(
                    Executors.newSingleThreadExecutor(), uwbOemExtensionCallback);
            uiAutomation.dropShellPermissionIdentity();
        } catch (SecurityException e) {
            Log.i(TAG, "registerUwbOemExtensionCallback() failed with security exception: " + e);
            fail();
        }

        verifyFiraRangingSession(
                firaOpenSessionParams,
                (rangingReport) -> {
                    assertThat(rangingReport.getMeasurements()).isNotNull();
                    // TODO(b/275137744): Consider adding a RangingMeasurementType field to the
                    //  top-level RangingReportMetadata, and then confirm it's of type OwrAoa.
                },
                (rangingSessionCallback) -> {
                    // Check that onCheckPointedTarget() is called, this should happen when an
                    // OWR_AOA Ranging report is received (on the observer).
                    assertThat(oemExtensionCountDownLatch
                            .await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(uwbOemExtensionCallback.onCheckPointedTargetCalled).isTrue();

                    // Send a Data packet to the remote device (Advertiser)
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    rangingSessionCallback.rangingSession.sendData(
                            UwbAddress.fromBytes(new byte[]{0x1, 0x2}),
                            new PersistableBundle(),
                            new byte[]{0x01, 0x02, 0x03, 0x04}
                    );

                    // Wait for the onDataSent callback.
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onDataSentCalled).isTrue();
                    assertThat(rangingSessionCallback.onDataSendFailedCalled).isFalse();
                });

        try {
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.unregisterUwbOemExtensionCallback(uwbOemExtensionCallback);
            uiAutomation.dropShellPermissionIdentity();
        } catch (SecurityException e) {
            Log.i(TAG, "unregisterUwbOemExtensionCallback() failed with security exception: " + e);
            fail();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSessionWithProvisionedSTS() throws Exception {
        FiraSpecificationParams params = getFiraSpecificationParams();
        assumeTrue(params.getStsCapabilities()
                .contains(FiraParams.StsCapabilityFlag.HAS_PROVISIONED_STS_SUPPORT)
                && params.getStsCapabilities()
                .contains(FiraParams.StsCapabilityFlag
                        .HAS_PROVISIONED_STS_INDIVIDUAL_CONTROLEE_KEY_SUPPORT));

        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(1, 1))
                .setSessionId(1)
                .setStsConfig(FiraParams.STS_CONFIG_PROVISIONED)
                .setSessionKey(new byte[]{
                        0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                        0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8
                })
                .setSubsessionKey(new byte[]{
                        0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                        0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8
                })
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x5, 6})))
                .build();
        verifyFiraRangingSession(firaOpenSessionParams, null, null);
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraPoseChanges() throws Exception {
        FiraPoseUpdateParams poseVQUpdate = new FiraPoseUpdateParams.Builder()
                .setPose(new float[]{0, 0, 0, 0, 0, 0, 1}) // identity vector & quaternion
                .build();
        float[] identityMatrix = new float[]{
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f
        };
        FiraPoseUpdateParams poseMatrixUpdate = new FiraPoseUpdateParams.Builder()
                .setPose(identityMatrix)
                .build();
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setFilterType(FiraParams.FILTER_TYPE_APPLICATION)
                .build();

        assertThat(firaOpenSessionParams.getFilterType())
                .isEqualTo(FiraParams.FILTER_TYPE_APPLICATION);

        // Rebundle to make sure bundling/unbundling works.
        FiraOpenSessionParams rebuiltParams = FiraOpenSessionParams.fromBundle(
                firaOpenSessionParams.toBundle());
        assertThat(rebuiltParams.getFilterType())
                .isEqualTo(FiraParams.FILTER_TYPE_APPLICATION);

        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    // For practical reasons, we will not go through the [extraordinary] effort to
                    // check the pose change results in the CTS test due to the complexity of the
                    // scenario.

                    // Must not throw.
                    rangingSessionCallback.rangingSession.updatePose(poseVQUpdate.toBundle());

                    // Must not throw.
                    rangingSessionCallback.rangingSession.updatePose(poseMatrixUpdate.toBundle());

                    // Wrong number of values.
                    assertThrows(IllegalArgumentException.class,
                            () -> new FiraPoseUpdateParams.Builder()
                                    .setPose(new float[]{5, 1})
                                    .build());

                    // Nonreal numbers.
                    assertThrows(IllegalArgumentException.class,
                            () -> new FiraPoseUpdateParams.Builder()
                                    .setPose(new float[]{1, 2, 3, 4, 5, Float.NaN, 7})
                                    .build());
                    assertThrows(IllegalArgumentException.class,
                            () -> new FiraPoseUpdateParams.Builder()
                                    .setPose(new float[]{
                                            Float.NEGATIVE_INFINITY, 2, 3, 4, 5, 6, 7})
                                    .build());
                });

    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingPoseFailures() throws Exception {
        FiraPoseUpdateParams poseUpdateParams = new FiraPoseUpdateParams.Builder()
                .setPose(new float[]{1, 2, 3, 4, 5, 6, 7})
                .build();
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setFilterType(FiraParams.FILTER_TYPE_NONE)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    assertThrows(IllegalStateException.class,
                            () -> rangingSessionCallback.rangingSession.updatePose(
                                    poseUpdateParams.toBundle()
                            ));
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSessionAddRemoveControlee() throws Exception {
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    // Add new controlee
                    CountDownLatch countDownLatch = new CountDownLatch(2);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UwbAddress uwbAddress = UwbAddress.fromBytes(new byte[]{0x5, 0x5});
                    rangingSessionCallback.rangingSession.addControlee(
                            new FiraControleeParams.Builder()
                                    .setAction(FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD)
                                    .setAddressList(new UwbAddress[]{uwbAddress})
                                    .build().toBundle()
                    );
                    // Wait for the on reconfigured and controlee added callback.
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onReconfiguredCalled).isTrue();
                    assertThat(rangingSessionCallback.onReconfiguredFailedCalled).isFalse();
                    assertThat(rangingSessionCallback.onControleeAddCalled).isTrue();
                    assertThat(rangingSessionCallback.onControleeAddFailedCalled).isFalse();

                    // Wait for a little over a ranging round to see if there are any
                    // ranging timeouts, and remove this controlee if it was not
                    // found in UWB Range.
                    countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceResultCountDownLatch(countDownLatch);
                    assertThat(countDownLatch.await(
                            firaOpenSessionParams.getRangingIntervalMs() + 10,
                            TimeUnit.MILLISECONDS)).isTrue();

                    // Remove controlee
                    countDownLatch = new CountDownLatch(2);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    rangingSessionCallback.rangingSession.removeControlee(
                            new FiraControleeParams.Builder()
                                    .setAction(FiraParams.MULTICAST_LIST_UPDATE_ACTION_DELETE)
                                    .setAddressList(new UwbAddress[]{uwbAddress})
                                    .build().toBundle()
                    );
                    // Wait for the on reconfigured and controlee added callback.
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onReconfiguredCalled).isTrue();
                    assertThat(rangingSessionCallback.onReconfiguredFailedCalled).isFalse();
                    assertThat(rangingSessionCallback.onControleeRemoveCalled).isTrue();
                    assertThat(rangingSessionCallback.onControleeRemoveFailedCalled).isFalse();
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSessionPauseWithNoPermission() throws Exception {
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    FiraSuspendRangingParams pauseParams =
                            new FiraSuspendRangingParams.Builder()
                                    .setSuspendRangingRounds(FiraParams.SUSPEND_RANGING_ENABLED)
                                    .build();
                    try {
                        rangingSessionCallback.rangingSession.pause(pauseParams.toBundle());
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSessionResumeWithNoPermission() throws Exception {
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
                    uiAutomation.dropShellPermissionIdentity();

                    FiraSuspendRangingParams resumeParams =
                            new FiraSuspendRangingParams.Builder()
                                    .setSuspendRangingRounds(FiraParams.SUSPEND_RANGING_DISABLED)
                                    .build();
                    try {
                        rangingSessionCallback.rangingSession.resume(resumeParams.toBundle());
                        fail();
                    } catch (SecurityException e) {
                        /* pass */
                        Log.i(TAG, "Failed with expected security exception: " + e);
                    } finally {
                        uiAutomation.adoptShellPermissionIdentity();
                    }
                });
    }

    @Ignore // b/316828112
    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSessionPauseResume() throws Exception {
        // The AppConfig Parameter SUSPEND_RANGING_ROUNDS (defined in CR-328) is supported
        // only for devices with FiRa 2.0 support.
        FiraSpecificationParams params = getFiraSpecificationParams();
        FiraProtocolVersion firaProtocolVersion = params.getMaxMacVersionSupported();
        assumeTrue(firaProtocolVersion.getMajor() >= 2);

        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    // Pause Session
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                    FiraSuspendRangingParams pauseParams =
                            new FiraSuspendRangingParams.Builder()
                                    .setSuspendRangingRounds(FiraParams.SUSPEND_RANGING_ENABLED)
                                    .build();
                    rangingSessionCallback.rangingSession.pause(pauseParams.toBundle());
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onPauseCalled).isTrue();
                    assertThat(rangingSessionCallback.onPauseFailedCalled).isFalse();

                    // Resume session
                    countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                    FiraSuspendRangingParams resumeParams =
                            new FiraSuspendRangingParams.Builder()
                                    .setSuspendRangingRounds(FiraParams.SUSPEND_RANGING_DISABLED)
                                    .build();
                    rangingSessionCallback.rangingSession.resume(resumeParams.toBundle());
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onResumeCalled).isTrue();
                    assertThat(rangingSessionCallback.onResumeFailedCalled).isFalse();
                });
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testFiraRangingSessionReconfigure() throws Exception {
        FiraOpenSessionParams firaOpenSessionParams = makeOpenSessionBuilder()
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .build();
        verifyFiraRangingSession(
                firaOpenSessionParams,
                null,
                (rangingSessionCallback) -> {
                    // Reconfigure to disable notifications.
                    CountDownLatch countDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                    FiraRangingReconfigureParams reconfigureParams =
                            new FiraRangingReconfigureParams.Builder()
                                    .setRangeDataNtfConfig(FiraParams.RANGE_DATA_NTF_CONFIG_DISABLE)
                                    .build();
                    rangingSessionCallback.rangingSession.reconfigure(reconfigureParams.toBundle());
                    // Wait for the on reconfigured and controlee added callback.
                    assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                    assertThat(rangingSessionCallback.onReconfiguredCalled).isTrue();
                    assertThat(rangingSessionCallback.onReconfiguredFailedCalled).isFalse();

                    // Ensure no more ranging reports are received.
                    CountDownLatch resultCountDownLatch = new CountDownLatch(1);
                    rangingSessionCallback.replaceResultCountDownLatch(resultCountDownLatch);
                    assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isFalse();
                });
    }

    private static class AdapterStateCallback implements UwbManager.AdapterStateCallback {
        private final CountDownLatch mCountDownLatch;
        private final Integer mWaitForState;
        public int state;
        public int reason;

        AdapterStateCallback(@NonNull CountDownLatch countDownLatch,
                @Nullable Integer waitForState) {
            mCountDownLatch = countDownLatch;
            mWaitForState = waitForState;
        }

        public void onStateChanged(int state, int reason) {
            this.state = state;
            this.reason = reason;
            if (mWaitForState != null) {
                if (mWaitForState == state) {
                    mCountDownLatch.countDown();
                }
            } else {
                mCountDownLatch.countDown();
            }
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-4"})
    public void testUwbStateToggle() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.isUwbEnabled()).isTrue();
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_INACTIVE);
            // Toggle the state
            setUwbEnabledAndWaitForCompletion(false);
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_DISABLED);
        } finally {
            // Toggle the state back on.
            setUwbEnabledAndWaitForCompletion(true);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciMessageVendorGid() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        UwbVendorUciCallback cb =
                new UwbVendorUciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Send random payload with a vendor gid.
            byte[] payload = new byte[100];
            new Random().nextBytes(payload);
            int gid = 9;
            int oid = 1;
            mUwbManager.sendVendorUciMessage(gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } catch (SecurityException e) {
            /* pass */
        } finally {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciMessageFiraGid() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        UwbVendorUciCallback cb =
                new UwbVendorUciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Send random payload with a FIRA gid.
            byte[] payload = new byte[100];
            new Random().nextBytes(payload);
            int gid = 1;
            int oid = 3;
            mUwbManager.sendVendorUciMessage(gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } catch (SecurityException e) {
            /* pass */
        } finally {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciMessageWithFragmentedPackets() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        UwbVendorUciCallback cb =
                new UwbVendorUciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Send random payload > 255 bytes with a vendor gid.
            byte[] payload = new byte[400];
            new Random().nextBytes(payload);
            int gid = 9;
            int oid = 1;
            mUwbManager.sendVendorUciMessage(gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } catch (SecurityException e) {
            /* pass */
        } finally {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testSendVendorUciMessageWithMessageType() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch rspCountDownLatch = new CountDownLatch(1);
        CountDownLatch ntfCountDownLatch = new CountDownLatch(1);
        UwbVendorUciCallback cb =
                new UwbVendorUciCallback(rspCountDownLatch, ntfCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbVendorUciCallback(
                    Executors.newSingleThreadExecutor(), cb);

            // Send random payload with a vendor gid.
            byte[] payload = new byte[100];
            new Random().nextBytes(payload);
            int gid = 9;
            int oid = 1;
            mUwbManager.sendVendorUciMessage(MESSAGE_TYPE_COMMAND, gid, oid, payload);

            // Wait for response.
            assertThat(rspCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(cb.gid).isEqualTo(gid);
            assertThat(cb.oid).isEqualTo(oid);
            assertThat(cb.payload).isNotEmpty();
        } catch (SecurityException e) {
            /* pass */
        } finally {
            mUwbManager.unregisterUwbVendorUciCallback(cb);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private class ChannelUsageCallback {

        private CountDownLatch mCountDownLatch;
        public boolean mChannelUsageUpdatedCalled = false;
        public Set<Integer> mUwbChannelUsageInfo;
        public Consumer<Set<Integer>> mChannelUsageInfoConsumer = info -> {
            mUwbChannelUsageInfo = info;
            mChannelUsageUpdatedCalled = true;
            mCountDownLatch.countDown();
        };

        ChannelUsageCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        public void replaceCountDownLatch(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        public void reset() {
            mChannelUsageUpdatedCalled = false;
        }
    }

    private class UwbOemExtensionCallback implements UwbManager.UwbOemExtensionCallback {
        public PersistableBundle mSessionChangeNtf;
        public PersistableBundle mDeviceStatusNtf;
        public PersistableBundle mSessionConfig;
        public RangingReport mRangingReport;
        public boolean onSessionConfigCompleteCalled = false;
        public boolean onRangingReportReceivedCalled = false;
        public boolean onSessionChangedCalled = false;
        public boolean onDeviceStatusNtfCalled = false;
        public boolean onCheckPointedTargetCalled = false;
        private CountDownLatch mCountDownLatch;

        UwbOemExtensionCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        public void replaceCountDownLatch(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSessionStatusNotificationReceived(
                @NonNull PersistableBundle sessionStatusBundle) {
            mSessionChangeNtf = sessionStatusBundle;
            onSessionChangedCalled = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onDeviceStatusNotificationReceived(PersistableBundle deviceStatusBundle) {
            mDeviceStatusNtf = deviceStatusBundle;
            onDeviceStatusNtfCalled = true;
            mCountDownLatch.countDown();
        }

        @NonNull
        @Override
        public int onSessionConfigurationComplete(@NonNull PersistableBundle openSessionBundle) {
            mSessionConfig = openSessionBundle;
            onSessionConfigCompleteCalled = true;
            mCountDownLatch.countDown();
            return 0;
        }

        @NonNull
        @Override
        public RangingReport onRangingReportReceived(
                @NonNull RangingReport rangingReport) {
            onRangingReportReceivedCalled = true;
            mRangingReport = rangingReport;
            mCountDownLatch.countDown();
            return mRangingReport;
        }

        @Override
        public boolean onCheckPointedTarget(
                @NonNull PersistableBundle pointedTargetBundle) {
            onCheckPointedTargetCalled = true;
            mCountDownLatch.countDown();
            return true;
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testOemCallbackExtension() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);
        // Expect to receive onSessionConfigurationComplete and onSessionChangedCalled
        CountDownLatch oemExtensionCountDownLatch = new CountDownLatch(3);
        UwbOemExtensionCallback uwbOemExtensionCallback =
                new UwbOemExtensionCallback(oemExtensionCountDownLatch);

        int sessionId = 1;
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);
        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(1, 1))
                .setSessionId(sessionId)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x5, 6})))
                .build();
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbOemExtensionCallback(
                    Executors.newSingleThreadExecutor(), uwbOemExtensionCallback);
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    firaOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(oemExtensionCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(uwbOemExtensionCallback.onSessionConfigCompleteCalled).isTrue();
            assertThat(uwbOemExtensionCallback.mSessionConfig).isNotNull();

            SessionConfigParams sessionConfigParams = SessionConfigParams
                    .fromBundle(uwbOemExtensionCallback.mSessionConfig);
            assertEquals(sessionConfigParams.getSessionId(), sessionId);
            FiraOpenSessionParams openSessionParamsBundle = FiraOpenSessionParams
                    .fromBundle(sessionConfigParams.getFiraOpenSessionParamsBundle());
            assertEquals(openSessionParamsBundle.getSessionId(), sessionId);
            assertEquals(openSessionParamsBundle.getStsConfig(), FiraParams.STS_CONFIG_STATIC);
            assertEquals(openSessionParamsBundle.getDeviceType(),
                    FiraParams.RANGING_DEVICE_TYPE_CONTROLLER);

            assertThat(uwbOemExtensionCallback.onSessionChangedCalled).isTrue();
            assertThat(uwbOemExtensionCallback.mSessionChangeNtf).isNotNull();

            SessionStatus sessionStatusBundle = SessionStatus
                    .fromBundle(uwbOemExtensionCallback.mSessionChangeNtf);
            assertEquals(sessionStatusBundle.getSessionId(), sessionId);
            assertEquals(sessionStatusBundle.getState(), UWB_SESSION_STATE_IDLE);
            assertEquals(sessionStatusBundle.getReasonCode(),
                    REASON_STATE_CHANGE_WITH_SESSION_MANAGEMENT_COMMANDS);

            countDownLatch = new CountDownLatch(1);
            // Expect to receive onSessionChangedCalled, onDeviceStatusNtfCalled and
            // onRangingReportReceivedCalled
            oemExtensionCountDownLatch = new CountDownLatch(3);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            uwbOemExtensionCallback.replaceCountDownLatch(oemExtensionCountDownLatch);
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(oemExtensionCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(uwbOemExtensionCallback.onSessionChangedCalled).isTrue();
            assertThat(uwbOemExtensionCallback.mSessionChangeNtf).isNotNull();
            assertThat(uwbOemExtensionCallback.onDeviceStatusNtfCalled).isTrue();
            assertThat(uwbOemExtensionCallback.mDeviceStatusNtf).isNotNull();

            DeviceStatus deviceStatusBundle = DeviceStatus
                    .fromBundle(uwbOemExtensionCallback.mDeviceStatusNtf);
            assertEquals(deviceStatusBundle.getDeviceState(), DEVICE_STATE_ACTIVE);

            // Wait for the on ranging report callback.
            assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.rangingReport).isNotNull();
            assertThat(uwbOemExtensionCallback.onRangingReportReceivedCalled).isTrue();
            assertThat(uwbOemExtensionCallback.mRangingReport).isNotNull();
            PersistableBundle reportMetadataBundle = uwbOemExtensionCallback
                    .mRangingReport.getRangingReportMetadata();
            RangingReportMetadata reportMetadata = RangingReportMetadata
                    .fromBundle(reportMetadataBundle);
            assertEquals(reportMetadata.getSessionId(), sessionId);
            assertThat(reportMetadata.getRawNtfData()).isNotEmpty();

            // Check the UWB state.
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();

            // Wait for on stopped callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                countDownLatch = new CountDownLatch(1);
                // Expect to receive onSessionChangedCalled.
                oemExtensionCountDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
                uwbOemExtensionCallback.replaceCountDownLatch(oemExtensionCountDownLatch);

                // Close session.
                cancellationSignal.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(oemExtensionCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(rangingSessionCallback.onClosedCalled).isTrue();
                assertThat(uwbOemExtensionCallback.onSessionChangedCalled).isTrue();
            }
            try {
                mUwbManager.unregisterUwbOemExtensionCallback(uwbOemExtensionCallback);
            } catch (SecurityException e) {
                /* pass */
                fail();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    @RequiresFlagsEnabled(Flags.FLAG_UWB_FIRA_3_0_25Q4)
    public void testChannelUsageCallback() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);

        CountDownLatch channelUsageCountDownLatch = new CountDownLatch(1);
        ChannelUsageCallback channelUsageCallback = new ChannelUsageCallback(
                channelUsageCountDownLatch);

        int sessionId = 1;
        int channel = 9;
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);
        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(1, 1))
                .setSessionId(sessionId)
                .setChannelNumber(channel)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x5, 6})))
                .build();
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerChannelUsageCallback(
                    Executors.newCachedThreadPool(),
                    channelUsageCallback.mChannelUsageInfoConsumer);
            assertThat(channelUsageCallback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(channelUsageCallback.mChannelUsageUpdatedCalled).isTrue();
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    firaOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            countDownLatch = new CountDownLatch(1);

            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            channelUsageCallback.replaceCountDownLatch(new CountDownLatch(1));
            channelUsageCallback.reset();
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();

            // Wait for the on ranging report callback.
            assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.rangingReport).isNotNull();
            assertThat(channelUsageCallback.mCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(channelUsageCallback.mChannelUsageUpdatedCalled).isTrue();
            assertTrue(channelUsageCallback.mUwbChannelUsageInfo.contains(channel));
            channelUsageCallback.reset();

            // Check the UWB state.
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();
            channelUsageCallback.replaceCountDownLatch(new CountDownLatch(1));
            assertThat(channelUsageCallback.mCountDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(channelUsageCallback.mChannelUsageUpdatedCalled).isTrue();
            assertFalse(channelUsageCallback.mUwbChannelUsageInfo.contains(channel));
            channelUsageCallback.reset();
            // Wait for on stopped callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Close session.
                cancellationSignal.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(rangingSessionCallback.onClosedCalled).isTrue();
            }
            try {
                mUwbManager.unregisterChannelUsageCallback(
                        channelUsageCallback.mChannelUsageInfoConsumer);
            } catch (SecurityException e) {
                /* pass */
                fail();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testRegisterUwbOemExtensionCallbackWithoutUwbPrivileged() {
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        CountDownLatch countDownLatch = new CountDownLatch(0);
        UwbManager.UwbOemExtensionCallback cb = new UwbOemExtensionCallback(countDownLatch);
        try {
            mUwbManager.registerUwbOemExtensionCallback(
                    Executors.newSingleThreadExecutor(), cb);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testUnregisterUwbOemExtensionCallbackWithoutUwbPrivileged() {
        Assume.assumeTrue(SdkLevel.isAtLeastU());
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch countDownLatch = new CountDownLatch(0);
        UwbManager.UwbOemExtensionCallback cb = new UwbOemExtensionCallback(countDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.registerUwbOemExtensionCallback(
                    Executors.newSingleThreadExecutor(), cb);
        } catch (SecurityException e) {
            /* fail */
            fail();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
        try {
            mUwbManager.unregisterUwbOemExtensionCallback(cb);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.unregisterUwbOemExtensionCallback(cb);
        } catch (SecurityException e) {
            /* pass */
            fail();
        }
    }

    private static class OnUwbActivityEnergyInfoListener implements
            Consumer<UwbActivityEnergyInfo> {
        private final CountDownLatch mCountDownLatch;
        public UwbActivityEnergyInfo mPowerStats;
        public boolean mIsListenerInvoked = false;

        OnUwbActivityEnergyInfoListener(@NonNull CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void accept(UwbActivityEnergyInfo info) {
            mIsListenerInvoked = true;
            mPowerStats = info;
            mCountDownLatch.countDown();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetUwbActivityEnergyInfoAsync() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        OnUwbActivityEnergyInfoListener listener =
                new OnUwbActivityEnergyInfoListener(countDownLatch);
        try {
            uiAutomation.adoptShellPermissionIdentity();
            mUwbManager.getUwbActivityEnergyInfoAsync(Executors.newSingleThreadExecutor(),
                    listener);
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(listener.mIsListenerInvoked).isTrue();
            if (listener.mPowerStats != null) {
                assertThat(listener.mPowerStats.getControllerIdleDurationMillis() >= 0)
                        .isTrue();
                assertThat(listener.mPowerStats.getControllerWakeCount() >= 0).isTrue();
            }
        } catch (SecurityException e) {
            /* pass */
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetUwbActivityEnergyInfoAsyncWithoutUwbPrivileged() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        OnUwbActivityEnergyInfoListener listener =
                new OnUwbActivityEnergyInfoListener(countDownLatch);
        try {
            mUwbManager.getUwbActivityEnergyInfoAsync(Executors.newSingleThreadExecutor(),
                    listener);
            // should fail if the call was successful without UWB_PRIVILEGED permission.
            fail();
        } catch (SecurityException e) {
            /* pass */
            Log.i(TAG, "Failed with expected security exception: " + e);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    public void testGetUwbActivityEnergyInfoAsyncWithBadParams() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        OnUwbActivityEnergyInfoListener listener =
                new OnUwbActivityEnergyInfoListener(countDownLatch);
        // null Executor
        assertThrows(NullPointerException.class,
                () -> mUwbManager.getUwbActivityEnergyInfoAsync(null, listener));
        // null listener
        assertThrows(NullPointerException.class,
                () -> mUwbManager.getUwbActivityEnergyInfoAsync(Executors.newSingleThreadExecutor(),
                        null));
    }

    private UwbManager createUwbManagerWithAttrTag(String attributionTag) {
        Context contextWithAttrTag = mContext.createContext(
                new ContextParams.Builder()
                        .setAttributionTag(attributionTag)
                        .build()
        );
        return contextWithAttrTag.getSystemService(UwbManager.class);
    }

    // Should be invoked with shell permissions.
    private static void requestUwbHwEnabledAndWaitForCompletion(boolean enabled,
            UwbManager uwbManager, boolean expectAdapterEnable) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        int adapterState = expectAdapterEnable ? STATE_ENABLED_INACTIVE : STATE_ENABLED_HW_IDLE;
        AdapterStateCallback adapterStateCallback =
                new AdapterStateCallback(countDownLatch, adapterState);
        try {
            uwbManager.registerAdapterStateCallback(
                    Executors.newSingleThreadExecutor(), adapterStateCallback);
            uwbManager.requestUwbHwEnabled(enabled);
            assertThat(countDownLatch.await(6, TimeUnit.SECONDS)).isTrue();
            assertThat(adapterStateCallback.state).isEqualTo(adapterState);
        } finally {
            uwbManager.unregisterAdapterStateCallback(adapterStateCallback);
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-4"})
    @RequiresFlagsEnabled("com.android.uwb.flags.hw_state")
    public void testUwbHwStateToggle() throws Exception {
        assumeTrue(mUwbManager.isUwbHwIdleTurnOffEnabled());
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            assertThat(mUwbManager.isUwbHwEnableRequested()).isEqualTo(true);
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_INACTIVE);
            // Toggle the HW state on & off.
            requestUwbHwEnabledAndWaitForCompletion(false, mUwbManager, false);
            requestUwbHwEnabledAndWaitForCompletion(true, mUwbManager, true);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-4"})
    @RequiresFlagsEnabled("com.android.uwb.flags.hw_state")
    public void testUwbHwStateToggleMultipleClients() throws Exception {
        assumeTrue(mUwbManager.isUwbHwIdleTurnOffEnabled());
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        UwbManager uwbManagerWithAttrTag1 = createUwbManagerWithAttrTag("tag1");
        UwbManager uwbManagerWithAttrTag2 = createUwbManagerWithAttrTag("tag2");
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // First remove the vote from the test setup
            requestUwbHwEnabledAndWaitForCompletion(false, mUwbManager, false);

            // Toggle the HW state on & off.
            requestUwbHwEnabledAndWaitForCompletion(true, uwbManagerWithAttrTag1, true);
            requestUwbHwEnabledAndWaitForCompletion(true, uwbManagerWithAttrTag2, true);
            requestUwbHwEnabledAndWaitForCompletion(false, uwbManagerWithAttrTag1, true);
            requestUwbHwEnabledAndWaitForCompletion(false, uwbManagerWithAttrTag2, false);
        } finally {
            // Reset back to vote as expected by the setup.
            requestUwbHwEnabledAndWaitForCompletion(true, mUwbManager, true);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-4"})
    @RequiresFlagsEnabled("com.android.uwb.flags.hw_state")
    public void testUwbHwStateToggle_WhenUwbToggleDisabled() throws Exception {
        assumeTrue(mUwbManager.isUwbHwIdleTurnOffEnabled());
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CountDownLatch countDownLatch;
        AdapterStateCallback adapterStateCallback;
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Toggle the HW state off (to clear the vote from setup).
            requestUwbHwEnabledAndWaitForCompletion(false, mUwbManager, false);

            // Toggle the state to disabled.
            countDownLatch = new CountDownLatch(1);
            adapterStateCallback =
                    new AdapterStateCallback(countDownLatch, STATE_ENABLED_INACTIVE);
            mUwbManager.registerAdapterStateCallback(
                    Executors.newSingleThreadExecutor(), adapterStateCallback);
            mUwbManager.setUwbEnabled(false);
            // Ensure we don't get any state change callback.
            assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isFalse();
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_DISABLED);
            mUwbManager.unregisterAdapterStateCallback(adapterStateCallback);

            // Toggle the HW state on and ensure the state does not change.
            countDownLatch = new CountDownLatch(1);
            adapterStateCallback =
                    new AdapterStateCallback(countDownLatch, STATE_ENABLED_INACTIVE);
            mUwbManager.registerAdapterStateCallback(
                    Executors.newSingleThreadExecutor(), adapterStateCallback);
            mUwbManager.requestUwbHwEnabled(true);
            // Ensure we don't get any state change callback.
            assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isFalse();
            assertThat(mUwbManager.isUwbHwEnableRequested()).isEqualTo(true);
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_DISABLED);
            mUwbManager.unregisterAdapterStateCallback(adapterStateCallback);
        } finally {
            // Reset uwb toggle back on
            setUwbEnabledAndWaitForCompletion(true);
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    @RequiresFlagsEnabled("com.android.uwb.flags.uwb_fira_3_0_25q4")
    public void testHybridUwbSession() throws Exception {
        Assume.assumeTrue(Flags.uwbFira3025q4());
        FiraSpecificationParams params = getFiraSpecificationParams();
        FiraProtocolVersion firaProtocolVersion = params.getMaxMacVersionSupported();
        // Hybrid UWB session is supported only for devices with FiRa 3.0 support.
        assumeTrue(firaProtocolVersion.getMajor() >= 3);

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);

        int sessionId = 1;
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);
        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(1, 1))
                .setSessionId(sessionId)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x5, 0x6})
                .setStaticStsIV(new byte[]{0x5, 0x6, 0x9, 0xa, 0x4, 0x6})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_UNICAST)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x5, 6}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x5, 6})))
                .build();
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    firaOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();

            // Wait for the on ranging report callback.
            assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.rangingReport).isNotNull();
            RangingReport rangingReport = rangingSessionCallback.rangingReport;
            assertThat(rangingReport.getMeasurements()).isNotNull();
            RangingMeasurement rangingMeasurement = rangingReport.getMeasurements().get(0);

            // TODO: update this.
            assertEquals(rangingMeasurement.getHusPrimarySessionId(), NON_HYBRID_UWB_SESSION_ID);

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();

            // Wait for on stopped callback.
            assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                // Close session.
                cancellationSignal.cancel();
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                //assertThat(rangingSessionCallback.onClosedCalled).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled("com.android.uwb.flags.uwb_fira_3_0_25q4")
    public void testLogicalLinkModeDataTransmission() throws Exception {
        Assume.assumeTrue(Flags.uwbFira3025q4());
        FiraSpecificationParams params = getFiraSpecificationParams();
        FiraProtocolVersion firaProtocolVersion = params.getMaxMacVersionSupported();
        assumeTrue(params.hasLogicalLinkSupport());

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);

        FiraOpenSessionParams firaOpenSessionParams = new FiraOpenSessionParams.Builder()
                .setProtocolVersion(new FiraProtocolVersion(3, 0))
                .setSessionType(FiraParams.SESSION_TYPE_DATA_TRANSFER)
                .setInBandTerminationAttemptCount(0)
                .setRframeConfig(FiraParams.RFRAME_CONFIG_SP1)
                .setLinkLayerMode(FiraParams.LINK_LAYER_MODE_LOGICAL_LINK)
                .setRangingRoundUsage(FiraParams.RANGING_ROUND_USAGE_DATA_TRANSFER_MODE)
                .setSessionId(1)
                .setStsConfig(FiraParams.STS_CONFIG_STATIC)
                .setVendorId(new byte[]{0x05, 0x06})
                .setStaticStsIV(new byte[]{0x05, 0x06, 0x09, 0x0A, 0x04, 0x06})
                .setDeviceType(FiraParams.RANGING_DEVICE_TYPE_CONTROLLER)
                .setDeviceRole(FiraParams.RANGING_DEVICE_ROLE_INITIATOR)
                .setMultiNodeMode(FiraParams.MULTI_NODE_MODE_ONE_TO_MANY)
                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{0x05, 0x06}))
                .setDestAddressList(List.of(UwbAddress.fromBytes(new byte[]{0x33, 0x22})))
                .build();

        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    firaOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback.rangingSession).isNotNull();

            LogicalLinkCreationParams.Builder builder = new LogicalLinkCreationParams.Builder(
                    LogicalLinkCreationParams.LINK_LAYER_MODE_CONNECTIONLESS_NON_SECURE,
                    UwbAddress.fromBytes(new byte[]{(byte) 0x33, (byte) 0x22}));

            if (params.getFiraLogicalLinkVersionSupported()
                    .equals(FiraSpecificationParams.DEFAULT_LOGICAL_LINK_VERSION)) {
                builder.setLogicalLinkClassLength(0);
            } else {
                builder.setLogicalLinkClassLength(1)
                        .setMaxSduTransmitSize(LogicalLinkCreationParams.SDU_SIZE_64_BYTES)
                        .setMaxSduReceiveSize(LogicalLinkCreationParams.SDU_SIZE_128_BYTES);
            }

            rangingSessionCallback.rangingSession.createLogicalLink(builder.build());
            assertThat(rangingSessionCallback.onLogicalLinkCreationFailedCalled).isFalse();

            countDownLatch = new CountDownLatch(2);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStartedCalled).isTrue();
            assertThat(rangingSessionCallback.onStartFailedCalled).isFalse();

            // check for logical link create callback
            assertThat(rangingSessionCallback.onLogicalLinkCreatedCalled).isTrue();
            assertThat(rangingSessionCallback.onLogicalLinkCreationFailedCalled).isFalse();

            //Get logical link params
            LogicalLinkConnectionParams getParamsResponse =
                    rangingSessionCallback.rangingSession.getLogicalLinkCreationParams(
                            rangingSessionCallback.connectId);
            assertThat(getParamsResponse).isNotNull();

            // Send logical link mode data
            PersistableBundle bundle = new FiraLogicalLinkInfo.Builder(
                    rangingSessionCallback.connectId).build().toBundle();
            UwbAddress address = UwbAddress.fromBytes(new byte[]{(byte) 0xff, (byte) 0xff});
            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.sendData(address, bundle,
                    new byte[]{0x11, 0x22});
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onDataSentCalled).isTrue();
            assertThat(rangingSessionCallback.onDataSendFailedCalled).isFalse();

            // Close the logical link
            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.closeLogicalLink(
                    rangingSessionCallback.connectId);
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onLogicalLinkClosedCalled).isTrue();
            assertThat(rangingSessionCallback.onLogicalLinkClosureFailedCalled).isFalse();

            // Check the UWB state.
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();

            // Wait for on stopped callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Close session.
                cancellationSignal.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                // not getting invoked
                // assertThat(rangingSessionCallback.onClosedCalled).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private RadarOpenSessionParams.Builder makeRadarOpenSessionBuilder() {
        return new RadarOpenSessionParams.Builder()
                .setSessionId(3)
                .setBurstPeriod(64)
                .setSweepPeriod(4800)
                .setSweepsPerBurst(16)
                .setSamplesPerSweep(SAMPLES_PER_SWEEP_DEFAULT)
                .setChannelNumber(FiraParams.UWB_CHANNEL_9)
                .setSweepOffset(SWEEP_OFFSET_DEFAULT)
                .setRframeConfig(RFRAME_CONFIG_SP3)
                .setPreambleDuration(PREAMBLE_DURATION_T64_SYMBOLS)
                .setPreambleCodeIndex(11)
                .setSessionPriority(SESSION_PRIORITY_DEFAULT)
                .setBitsPerSample(BITS_PER_SAMPLES_32)
                .setPrfMode(PRF_MODE_BPRF)
                .setNumberOfBursts(NUMBER_OF_BURSTS_DEFAULT)
                .setRadarDataType(RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES);
    }

    @Test
    @SdkSuppress(minSdkVersion = 37)
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2,C-1-5"})
    public void testRadarSession() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();
        RadarSpecificationParams params = getRadarSpecificationParams();
        assumeTrue(params != null && params.getRadarCapabilities()
                .contains(RadarParams.RadarCapabilityFlag.HAS_RADAR_SWEEP_SAMPLES_SUPPORT));
        RadarOpenSessionParams radarOpenSessionParams = makeRadarOpenSessionBuilder().build();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch, resultCountDownLatch);
        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    radarOpenSessionParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback.rangingSession).isNotNull();

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStartedCalled).isTrue();
            assertThat(rangingSessionCallback.onStartFailedCalled).isFalse();

            // Wait for the on data received callback.
            assertThat(resultCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();

            // Check the UWB state.
            assertThat(mUwbManager.getAdapterState()).isEqualTo(STATE_ENABLED_ACTIVE);

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();

            // Wait for on stopped callback.
            assertThat(countDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Close session.
                cancellationSignal.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(rangingSessionCallback.onClosedCalled).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.ranging.flags.Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testClearSessions() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal1Ses1 = null;
        CancellationSignal cancellationSignal1Ses2 = null;
        CancellationSignal cancellationSignal2Ses1 = null;
        UwbManager uwbManagerWithAttrTag1 = createUwbManagerWithAttrTag("tag1");
        //Create UwbManager with Null tag
        Context contextWithNullAttrTag = mContext.createContext(
                new ContextParams.Builder()
                        .setAttributionTag(null)
                        .build()
        );
        UwbManager uwbManagerWithNullTag = contextWithNullAttrTag.getSystemService(
                UwbManager.class);

        CountDownLatch countDownLatch1Ses1 = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch1Ses1 = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback1Ses1 =
                new RangingSessionCallback(countDownLatch1Ses1, resultCountDownLatch1Ses1);
        CountDownLatch countDownLatch1Ses2 = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch1Ses2 = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback1Ses2 =
                new RangingSessionCallback(countDownLatch1Ses2, resultCountDownLatch1Ses2);
        CountDownLatch countDownLatch2Ses1 = new CountDownLatch(1);
        CountDownLatch resultCountDownLatch2Ses1 = new CountDownLatch(1);
        RangingSessionCallback rangingSessionCallback2Ses1 =
                new RangingSessionCallback(countDownLatch2Ses1, resultCountDownLatch2Ses1);

        FiraOpenSessionParams.Builder firaOpenSessionParams = makeOpenSessionBuilder();
        try {
            // Needs UWB_PRIVILEGED permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Start first set of ranging sessions
            cancellationSignal1Ses1 = uwbManagerWithAttrTag1.openRangingSession(
                    firaOpenSessionParams.setSessionId(5).build().toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback1Ses1,
                    mDefaultChipId);
            // Wait for the on opened callback for session 1_1
            assertThat(countDownLatch1Ses1.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback1Ses1.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback1Ses1.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback1Ses1.rangingSession).isNotNull();
            //Starting session 1_2
            cancellationSignal1Ses2 = uwbManagerWithAttrTag1.openRangingSession(
                    firaOpenSessionParams.setSessionId(10).build().toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback1Ses2,
                    mDefaultChipId);
            // Wait for the on opened callback for session 1_2
            assertThat(countDownLatch1Ses2.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback1Ses2.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback1Ses2.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback1Ses2.rangingSession).isNotNull();
            // Starting session 2_1
            cancellationSignal2Ses1 = uwbManagerWithNullTag.openRangingSession(
                    firaOpenSessionParams.setSessionId(20).build().toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback2Ses1,
                    mDefaultChipId);
            // Wait for the on opened callback for session 2_1
            assertThat(countDownLatch2Ses1.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback2Ses1.onOpenedCalled).isTrue();
            assertThat(rangingSessionCallback2Ses1.onOpenFailedCalled).isFalse();
            assertThat(rangingSessionCallback2Ses1.rangingSession).isNotNull();

            //Start all rangingSessions
            countDownLatch1Ses1 = new CountDownLatch(1);
            rangingSessionCallback1Ses1.replaceCtrlCountDownLatch(countDownLatch1Ses1);
            rangingSessionCallback1Ses1.rangingSession.start(new PersistableBundle());
            countDownLatch1Ses2 = new CountDownLatch(1);
            rangingSessionCallback1Ses2.replaceCtrlCountDownLatch(countDownLatch1Ses2);
            rangingSessionCallback1Ses2.rangingSession.start(new PersistableBundle());
            countDownLatch2Ses1 = new CountDownLatch(1);
            rangingSessionCallback2Ses1.replaceCtrlCountDownLatch(countDownLatch2Ses1);
            rangingSessionCallback2Ses1.rangingSession.start(new PersistableBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch1Ses1.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(countDownLatch1Ses2.await(1, TimeUnit.SECONDS)).isTrue();

            // Clear all sessions with Tag1
            uwbManagerWithAttrTag1.clearSessions();
            // Check if all tag1 sessions are closed
            countDownLatch1Ses1 = new CountDownLatch(1);
            rangingSessionCallback1Ses1.replaceCtrlCountDownLatch(countDownLatch1Ses1);
            countDownLatch1Ses2 = new CountDownLatch(1);
            rangingSessionCallback1Ses2.replaceCtrlCountDownLatch(countDownLatch1Ses2);
            assertThat(countDownLatch1Ses1.await(6, TimeUnit.SECONDS)).isTrue();
            assertThat(countDownLatch1Ses2.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback1Ses1.onClosedCalled).isTrue();
            assertThat(rangingSessionCallback1Ses2.onClosedCalled).isTrue();
            // Check if no-tag sessions are open
            assertThat(rangingSessionCallback2Ses1.onClosedCalled).isFalse();

            countDownLatch2Ses1 = new CountDownLatch(1);
            rangingSessionCallback2Ses1.replaceCtrlCountDownLatch(countDownLatch2Ses1);

            //Close the remaining sessions
            rangingSessionCallback2Ses1.rangingSession.stop();
            // Check if no-tag sessions are closed
            assertThat(countDownLatch2Ses1.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback2Ses1.onStoppedCalled).isTrue();
            //Check if clearSessions on a null tag throws an exception
            assertThrows(IllegalArgumentException.class,
                    () -> uwbManagerWithNullTag.clearSessions());
        } finally {
            if (cancellationSignal1Ses1 != null) {
                // Close session
                countDownLatch1Ses1 = new CountDownLatch(1);
                rangingSessionCallback1Ses1.replaceCtrlCountDownLatch(countDownLatch1Ses1);
                cancellationSignal1Ses1.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch1Ses1.await(2, TimeUnit.SECONDS)).isTrue();
            }
            if (cancellationSignal1Ses2 != null) {
                // Close session.
                countDownLatch1Ses2 = new CountDownLatch(1);
                rangingSessionCallback1Ses2.replaceCtrlCountDownLatch(countDownLatch1Ses2);
                cancellationSignal1Ses2.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch1Ses2.await(2, TimeUnit.SECONDS)).isTrue();
            }
            if (cancellationSignal2Ses1 != null) {
                // Close session.
                countDownLatch2Ses1 = new CountDownLatch(1);
                rangingSessionCallback2Ses1.replaceCtrlCountDownLatch(countDownLatch2Ses1);
                cancellationSignal2Ses1.cancel();

                // Wait for the on closed callback.
                assertThat(countDownLatch2Ses1.await(2, TimeUnit.SECONDS)).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private class TimesyncCallback implements UwbManager.TimesyncCallback {
        CountDownLatch mRegisteredLatch;
        CountDownLatch mFailedLatch;
        public boolean registered = false;
        public boolean failed = false;

        TimesyncCallback(CountDownLatch registeredLatch, CountDownLatch failedLatch) {
            Log.e(">>>>>", "inside timsynccallback in test constructor");
            mRegisteredLatch = registeredLatch;
            mFailedLatch = failedLatch;
        }

        @Override
        public void onRegistered() {
            registered = true;
            mRegisteredLatch.countDown();
        }

        @Override
        public void onRegisteredFailed() {
            failed = true;
            mFailedLatch.countDown();
        }

        @Override
        public void onTimesyncEvent(@NonNull TimesyncEvent event) {
            Log.e(">>>>", event.toString());
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.ranging.flags.Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testTimesyncCallback() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        String macAddress = "00:11:22:AA:BB:CC";
        int addressType = BluetoothDevice.ADDRESS_TYPE_PUBLIC;

        CountDownLatch registeredLatch = new CountDownLatch(1);
        CountDownLatch failedLatch = new CountDownLatch(1);
        UwbManager.TimesyncCallback cb = new TimesyncCallback(registeredLatch, failedLatch);

        try {
            //Get UWB permission
            uiAutomation.adoptShellPermissionIdentity();

            mUwbManager.registerTimesyncCallback(Executors.newSingleThreadExecutor(),
                    macAddress, BluetoothDevice.ADDRESS_TYPE_PUBLIC, cb);
            assertThat(registeredLatch.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(failedLatch.await(1, TimeUnit.SECONDS)).isFalse();

            mUwbManager.unregisterTimesyncCallback(cb);

        } catch (Exception e) {
            fail("Test failed due to exception: " + e.getMessage());
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 37)
    @RequiresFlagsEnabled(com.android.ranging.flags.Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public void testAliroSession_withHostKey() throws Exception {
        AliroSpecificationParams params = getAliroSpecificationParams();
        assumeTrue(params != null && params.getProtocolVersions() != null);
        if (getVsrApiLevel() >= Build.VERSION_CODES.CINNAMON_BUN) {
            assertTrue(params.getProtocolVersions().stream().anyMatch(
                    v -> v.getMajor() != 0 || v.getMinor() != 0));
        } else {
            // Skip if protocol version is 0x0000
            assumeTrue(params.getProtocolVersions().stream().anyMatch(
                    v -> v.getMajor() != 0 || v.getMinor() != 0));
        }

        AliroProtocolVersion protocolVersion = params.getProtocolVersions().stream()
                .filter(v -> v.getMajor() != 0 || v.getMinor() != 0)
                .findFirst()
                .get();
        int uwbConfig = params.getUwbConfigs().get(0);
        AliroPulseShapeCombo pulseShapeCombo = params.getPulseShapeCombos().get(0);
        int channel = params.getChannels().get(0);
        int chapsPerSlot = params.getChapsPerSlot().get(0);
        int syncCodeIndex = params.getSyncCodes().get(0);
        int ranMultiplier = params.getRanMultiplier();
        int hoppingConfigMode = params.getHoppingConfigModes().get(0);
        int hoppingSequence = params.getHoppingSequences().get(0);

        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        CancellationSignal cancellationSignal = null;
        CountDownLatch countDownLatch = new CountDownLatch(1);

        int sessionId = 1;
        RangingSessionCallback rangingSessionCallback =
                new RangingSessionCallback(countDownLatch);

        AliroOpenRangingParams aliroOpenRangingParams = new AliroOpenRangingParams.Builder()
                .setProtocolVersion(protocolVersion)
                .setUwbConfig(uwbConfig)
                .setPulseShapeCombo(pulseShapeCombo)
                .setSessionId(sessionId)
                .setRanMultiplier(ranMultiplier)
                .setChannel(channel)
                .setNumChapsPerSlot(chapsPerSlot)
                .setNumResponderNodes(1)
                .setHoppingConfigMode(hoppingConfigMode)
                .setHoppingSequence(hoppingSequence)
                .setNumSlotsPerRound(AliroParams.SLOTS_PER_ROUND_6)
                .setSyncCodeIndex(syncCodeIndex)
                // Host based
                .setSessionKey(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                        0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x01, 0x02, 0x03, 0x04, 0x05,
                        0x06, 0x07, 0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10})
                .build();

        AliroStartRangingParams aliroStartRangingParams = new AliroStartRangingParams.Builder()
                .setSessionId(sessionId)
                .setRanMultiplier(ranMultiplier)
                .build();

        try {
            // Needs UWB_PRIVILEGED & UWB_RANGING permission which is held by shell.
            uiAutomation.adoptShellPermissionIdentity();
            // Start ranging session
            cancellationSignal = mUwbManager.openRangingSession(
                    aliroOpenRangingParams.toBundle(),
                    Executors.newSingleThreadExecutor(),
                    rangingSessionCallback,
                    mDefaultChipId);
            // Wait for the on opened callback.
            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onOpenedCalled).isTrue();

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            rangingSessionCallback.rangingSession.start(aliroStartRangingParams.toBundle());
            // Wait for the on started callback.
            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStartedCalled).isTrue();

            countDownLatch = new CountDownLatch(1);
            rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);
            // Stop ongoing session.
            rangingSessionCallback.rangingSession.stop();

            // Wait for on stopped callback.
            assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(rangingSessionCallback.onStoppedCalled).isTrue();
        } finally {
            if (cancellationSignal != null) {
                // Close session.
                cancellationSignal.cancel();
                countDownLatch = new CountDownLatch(1);
                rangingSessionCallback.replaceCtrlCountDownLatch(countDownLatch);

                // Wait for the on closed callback.
                assertThat(countDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
            }
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
