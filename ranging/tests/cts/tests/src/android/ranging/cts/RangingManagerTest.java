/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.ranging.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.ranging.RangingCapabilities;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingManager.RangingCapabilitiesCallback;
import android.ranging.RangingParams;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingCapabilities;
import android.ranging.uwb.UwbRangingParams;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot get RangingManager in instant app mode")
public class RangingManagerTest {
    private static final String TAG = "RangingManagerTest";
    private final Context mContext = InstrumentationRegistry.getContext();
    private RangingManager mRangingManager;

    @Before
    public void setup() throws Exception {
        //TODO : Use this after removing @hide
        mRangingManager = mContext.getSystemService(RangingManager.class);
        assertThat(mRangingManager).isNotNull();
    }

    @After
    public void teardown() throws Exception {
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStartStopRangingSession() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        CallbackVerifier callback = new CallbackVerifier();

        RangingSession rangingSession = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback);
        assertThat(rangingSession).isNotNull();

        RangingPreference preference = new RangingPreference.Builder()
                .setRangingParameters(new RangingParams.Builder()
                        .setUwbParameters(new UwbRangingParams.Builder()
                                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{1, 2}))
                                .setComplexChannel(new UwbComplexChannel.Builder().setChannel(
                                        9).setPreambleIndex(11).build())
                                .setSessionKeyInfo(
                                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1})
                                .setConfigId(UwbRangingParams.ConfigId.UNICAST_DS_TWR)
                                .setPeerAddresses(Map.of(
                                        new RangingDevice.Builder().build(),
                                        UwbAddress.fromBytes(new byte[]{3, 4})))
                                .setRangingUpdateRate(UwbRangingParams.RangingUpdateRate.NORMAL)
                                .build())
                        .build())
                .build();

        rangingSession.start(preference);
        assertThat(callback.mOnStartedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession.stop();
        assertThat(callback.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testStartStopMultipleRangingSessions() throws Exception {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity();

        CallbackVerifier callback1 = new CallbackVerifier();
        CallbackVerifier callback2 = new CallbackVerifier();

        RangingSession rangingSession1 = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback1);
        assertThat(rangingSession1).isNotNull();

        RangingSession rangingSession2 = mRangingManager.createRangingSession(
                MoreExecutors.directExecutor(), callback2);
        assertThat(rangingSession2).isNotNull();

        RangingPreference preference1 = new RangingPreference.Builder()
                .setRangingParameters(new RangingParams.Builder()
                        .setUwbParameters(new UwbRangingParams.Builder()
                                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{1, 2}))
                                .setComplexChannel(new UwbComplexChannel.Builder().setChannel(
                                        9).setPreambleIndex(11).build())
                                .setSessionKeyInfo(
                                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1})
                                .setConfigId(UwbRangingParams.ConfigId.UNICAST_DS_TWR)
                                .setPeerAddresses(Map.of(
                                        new RangingDevice.Builder().build(),
                                        UwbAddress.fromBytes(new byte[]{3, 4})))
                                .setRangingUpdateRate(UwbRangingParams.RangingUpdateRate.NORMAL)
                                .build())
                        .build())
                .build();

        RangingPreference preference2 = new RangingPreference.Builder()
                .setRangingParameters(new RangingParams.Builder()
                        .setUwbParameters(new UwbRangingParams.Builder()
                                .setDeviceAddress(UwbAddress.fromBytes(new byte[]{3, 4}))
                                .setComplexChannel(new UwbComplexChannel.Builder().setChannel(
                                        9).setPreambleIndex(11).build())
                                .setSessionKeyInfo(
                                        new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1})
                                .setConfigId(UwbRangingParams.ConfigId.UNICAST_DS_TWR)
                                .setPeerAddresses(Map.of(
                                        new RangingDevice.Builder().build(),
                                        UwbAddress.fromBytes(new byte[]{1, 2})))
                                .setRangingUpdateRate(UwbRangingParams.RangingUpdateRate.NORMAL)
                                .build())
                        .build())
                .build();

        rangingSession1.start(preference1);
        rangingSession2.start(preference2);

        assertThat(callback1.mOnStartedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callback2.mOnStartedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        rangingSession1.stop();
        rangingSession2.stop();

        assertThat(callback1.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(callback2.mOnClosedCalled.await(1, TimeUnit.SECONDS)).isTrue();

        uiAutomation.dropShellPermissionIdentity();
    }

    private static class CallbackVerifier implements RangingSession.Callback {

        private volatile CountDownLatch mOnStartedCalled = new CountDownLatch(1);
        private volatile CountDownLatch mOnClosedCalled = new CountDownLatch(1);

        @Override
        public void onStarted(int technology) {
            mOnStartedCalled.countDown();
        }

        @Override
        public void onStartFailed(int reason) {

        }

        @Override
        public void onClosed(int reasonCode) {
            mOnClosedCalled.countDown();
        }

        @Override
        public void onRangingStopped(@NonNull RangingDevice device) {

        }

        @Override
        public void onResults(@NonNull RangingDevice device, @NonNull RangingData data) {

        }
    }

    @Test
    @CddTest(requirements = {"7.3.13/C-1-1,C-1-2"})
    @RequiresFlagsEnabled("com.android.ranging.flags.ranging_stack_enabled")
    public void testGetRangingCapabilities() throws InterruptedException {
        CapabilitiesCallback capabilitiesCallback = new CapabilitiesCallback(new CountDownLatch(1));
        mRangingManager.registerCapabilitiesCallback(Executors.newSingleThreadExecutor(),
                capabilitiesCallback);

        assertThat(capabilitiesCallback.mCountDownLatch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(capabilitiesCallback.mOnCapabilitiesReceived).isTrue();
        assertThat(capabilitiesCallback.mRangingCapabilities).isNotNull();

        UwbRangingCapabilities uwbRangingCapabilities =
                capabilitiesCallback.mRangingCapabilities.getUwbCapabilities();
        if (uwbRangingCapabilities != null) {
            assertThat(uwbRangingCapabilities.isSupportsDistance()).isTrue();
            assertThat(uwbRangingCapabilities.getSupportedChannels()).isNotNull();
        }

        mRangingManager.unregisterCapabilitiesCallback(capabilitiesCallback);
    }

    private static class CapabilitiesCallback implements RangingCapabilitiesCallback {

        private final CountDownLatch mCountDownLatch;
        private boolean mOnCapabilitiesReceived = false;
        private RangingCapabilities mRangingCapabilities = null;

        CapabilitiesCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onRangingCapabilities(@NonNull RangingCapabilities capabilities) {
            mOnCapabilitiesReceived = true;
            mRangingCapabilities = capabilities;
            mCountDownLatch.countDown();
        }
    }
}
