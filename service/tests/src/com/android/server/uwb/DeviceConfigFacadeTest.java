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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.test.MockAnswerUtil;
import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.test.TestLooper;
import android.provider.DeviceConfig;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.uwb.DeviceConfigFacade.PoseSourceType;
import com.android.uwb.resources.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

public class DeviceConfigFacadeTest {
    @Mock private Resources mResources;
    @Mock private Context mContext;

    final ArgumentCaptor<DeviceConfig.OnPropertiesChangedListener>
            mOnPropertiesChangedListenerCaptor =
            ArgumentCaptor.forClass(DeviceConfig.OnPropertiesChangedListener.class);

    private DeviceConfigFacade mDeviceConfigFacade;
    private TestLooper mLooper = new TestLooper();
    private MockitoSession mSession;

    /**
     * Setup the mocks and an instance of DeviceConfig before each test.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // static mocking
        mSession = ExtendedMockito.mockitoSession()
                .mockStatic(DeviceConfig.class, withSettings().lenient())
                .startMocking();
        // Have DeviceConfig return the default value passed in.
        when(DeviceConfig.getBoolean(anyString(), anyString(), anyBoolean()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public boolean answer(String namespace, String field, boolean def) {
                        return def;
                    }
                });
        when(DeviceConfig.getInt(anyString(), anyString(), anyInt()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public int answer(String namespace, String field, int def) {
                        return def;
                    }
                });
        when(DeviceConfig.getLong(anyString(), anyString(), anyLong()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public long answer(String namespace, String field, long def) {
                        return def;
                    }
                });
        when(DeviceConfig.getString(anyString(), anyString(), anyString()))
                .then(new MockAnswerUtil.AnswerWithArguments() {
                    public String answer(String namespace, String field, String def) {
                        return def;
                    }
                });


        when(mResources.getBoolean(R.bool.enable_filters)).thenReturn(true);
        when(mResources.getBoolean(R.bool.enable_primer_est_elevation)).thenReturn(true);
        when(mResources.getBoolean(R.bool.enable_primer_aoa)).thenReturn(true);
        when(mResources.getInteger(R.integer.filter_distance_inliers_percent))
                .thenReturn(1);
        when(mResources.getInteger(R.integer.filter_distance_window))
                .thenReturn(2);
        when(mResources.getInteger(R.integer.filter_angle_inliers_percent))
                .thenReturn(3);
        when(mResources.getInteger(R.integer.filter_angle_window))
                .thenReturn(4);
        when(mResources.getInteger(R.integer.primer_fov_degrees))
                .thenReturn(5);
        when(mResources.getString(R.string.pose_source_type))
                .thenReturn("ROTATION_VECTOR");
        when(mResources.getInteger(R.integer.prediction_timeout_seconds))
                .thenReturn(6);

        when(mContext.getResources()).thenReturn(mResources);

        mDeviceConfigFacade = new DeviceConfigFacade(new Handler(mLooper.getLooper()),
                mContext);
        verify(() -> DeviceConfig.addOnPropertiesChangedListener(anyString(), any(),
                mOnPropertiesChangedListenerCaptor.capture()));
    }

    /**
     * Called after each test
     */
    @After
    public void cleanup() {
        validateMockitoUsage();
        mSession.finishMocking();
    }

    /**
     * Verifies that default values are set correctly
     */
    @Test
    public void testDefaultValue() throws Exception {
        assertEquals(DeviceConfigFacade.DEFAULT_RANGING_RESULT_LOG_INTERVAL_MS,
                mDeviceConfigFacade.getRangingResultLogIntervalMs());
        assertEquals(false, mDeviceConfigFacade.isDeviceErrorBugreportEnabled());
        assertEquals(DeviceConfigFacade.DEFAULT_BUG_REPORT_MIN_INTERVAL_MS,
                mDeviceConfigFacade.getBugReportMinIntervalMs());

        assertEquals(true, mDeviceConfigFacade.isEnableFilters());
        assertEquals(true, mDeviceConfigFacade.isEnablePrimerEstElevation());
        assertEquals(true, mDeviceConfigFacade.isEnablePrimerAoA());

        assertEquals(1, mDeviceConfigFacade.getFilterDistanceInliersPercent());
        assertEquals(2, mDeviceConfigFacade.getFilterDistanceWindow());
        assertEquals(3, mDeviceConfigFacade.getFilterAngleInliersPercent());
        assertEquals(4, mDeviceConfigFacade.getFilterAngleWindow());
        assertEquals(5, mDeviceConfigFacade.getPrimerFovDegree());
        assertEquals(PoseSourceType.ROTATION_VECTOR, mDeviceConfigFacade.getPoseSourceType());
        assertEquals(6, mDeviceConfigFacade.getPredictionTimeoutSeconds());

        // true because FOV is 5: within limits.
        assertEquals(true, mDeviceConfigFacade.isEnablePrimerFov());
    }

    /**
     * Verifies that all fields are updated properly.
     */
    @Test
    public void testFieldUpdates() throws Exception {
        // Simulate updating the fields
        when(DeviceConfig.getInt(anyString(), eq("ranging_result_log_interval_ms"),
                anyInt())).thenReturn(4000);
        when(DeviceConfig.getBoolean(anyString(), eq("device_error_bugreport_enabled"),
                anyBoolean())).thenReturn(true);
        when(DeviceConfig.getInt(anyString(), eq("bug_report_min_interval_ms"),
                anyInt())).thenReturn(10 * 3600_000);

        when(DeviceConfig.getBoolean(anyString(), eq("enable_filters"),
                anyBoolean())).thenReturn(false);
        when(DeviceConfig.getBoolean(anyString(), eq("enable_primer_est_elevation"),
                anyBoolean())).thenReturn(false);
        when(DeviceConfig.getBoolean(anyString(), eq("enable_primer_aoa"),
                anyBoolean())).thenReturn(false);

        when(DeviceConfig.getInt(anyString(), eq("filter_distance_inliers_percent"),
                anyInt())).thenReturn(6);
        when(DeviceConfig.getInt(anyString(), eq("filter_distance_window"),
                anyInt())).thenReturn(7);
        when(DeviceConfig.getInt(anyString(), eq("filter_angle_inliers_percent"),
                anyInt())).thenReturn(8);
        when(DeviceConfig.getInt(anyString(), eq("filter_angle_window"),
                anyInt())).thenReturn(9);
        when(DeviceConfig.getInt(anyString(), eq("primer_fov_degrees"),
                anyInt())).thenReturn(0);
        when(DeviceConfig.getString(anyString(), eq("pose_source_type"),
                anyString())).thenReturn("NONE");
        when(DeviceConfig.getInt(anyString(), eq("prediction_timeout_seconds"),
                anyInt())).thenReturn(5);

        mOnPropertiesChangedListenerCaptor.getValue().onPropertiesChanged(null);

        // Verifying fields are updated to the new values
        assertEquals(4000, mDeviceConfigFacade.getRangingResultLogIntervalMs());
        assertEquals(true, mDeviceConfigFacade.isDeviceErrorBugreportEnabled());
        assertEquals(10 * 3600_000, mDeviceConfigFacade.getBugReportMinIntervalMs());

        assertEquals(false, mDeviceConfigFacade.isEnableFilters());
        assertEquals(false, mDeviceConfigFacade.isEnablePrimerEstElevation());
        assertEquals(false, mDeviceConfigFacade.isEnablePrimerAoA());
        assertEquals(6, mDeviceConfigFacade.getFilterDistanceInliersPercent());
        assertEquals(7, mDeviceConfigFacade.getFilterDistanceWindow());
        assertEquals(8, mDeviceConfigFacade.getFilterAngleInliersPercent());
        assertEquals(9, mDeviceConfigFacade.getFilterAngleWindow());
        assertEquals(0, mDeviceConfigFacade.getPrimerFovDegree());
        assertEquals(PoseSourceType.NONE, mDeviceConfigFacade.getPoseSourceType());
        assertEquals(5, mDeviceConfigFacade.getPredictionTimeoutSeconds());

        // false because FOV is 0.
        assertEquals(false, mDeviceConfigFacade.isEnablePrimerFov());
    }
}
