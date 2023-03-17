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

import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_2;
import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_UNICAST_DS_TWR;
import static androidx.core.uwb.backend.impl.internal.Utils.INFREQUENT;
import static androidx.core.uwb.backend.impl.internal.Utils.RANGE_DATA_NTF_ENABLE_PROXIMITY_EDGE_TRIG;

import static com.google.uwb.support.fira.FiraParams.MULTICAST_LIST_UPDATE_ACTION_ADD;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_ROLE_INITIATOR;
import static com.google.uwb.support.fira.FiraParams.RANGING_DEVICE_TYPE_CONTROLLER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraRangingReconfigureParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class ConfigurationManagerTest {
    private static final int TEST_DEVICE_TYPE = RANGING_DEVICE_TYPE_CONTROLLER;
    private static final UwbAddress TEST_LOCAL_ADDRESS = UwbAddress.getRandomizedShortAddress();
    private RangingParameters mRangingParameters;
    @Mock private UwbComplexChannel mComplexChannel;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        UwbRangeDataNtfConfig uwbRangeDataNtfConfig = new UwbRangeDataNtfConfig.Builder()
                .setRangeDataConfigType(RANGE_DATA_NTF_ENABLE_PROXIMITY_EDGE_TRIG)
                .setNtfProximityNear(100)
                .build();
        mRangingParameters = new RangingParameters(CONFIG_UNICAST_DS_TWR, 1, 1,
                new byte[]{1, 2}, new byte[]{1, 2}, mComplexChannel,
                new ArrayList<>(List.of(UwbAddress.getRandomizedShortAddress())), INFREQUENT,
                uwbRangeDataNtfConfig);
        when(mComplexChannel.getChannel()).thenReturn(1);
        when(mComplexChannel.getPreambleIndex()).thenReturn(1);
    }

    @Test
    public void testCreateOpenSessionParams() {
        FiraOpenSessionParams params = ConfigurationManager
                .createOpenSessionParams(TEST_DEVICE_TYPE, TEST_LOCAL_ADDRESS, mRangingParameters);
        assertEquals(params.getDeviceRole(), RANGING_DEVICE_ROLE_INITIATOR);
    }

    @Test
    public void testCreateReconfigureParams() {
        FiraRangingReconfigureParams params = ConfigurationManager
                .createReconfigureParams(CONFIG_UNICAST_DS_TWR, MULTICAST_LIST_UPDATE_ACTION_ADD,
                        new UwbAddress[]{UwbAddress.getRandomizedShortAddress()}, new int[]{0, 1},
                        new byte[]{0, 1});
        assertNotNull(params.getAction());
        assertEquals(params.getAction().intValue(), MULTICAST_LIST_UPDATE_ACTION_ADD);
        assertNull(params.getSubSessionIdList());
    }

    @Test
    public void testIsUnicast() {
        assertTrue(ConfigurationManager.isUnicast(CONFIG_UNICAST_DS_TWR));
        assertFalse(ConfigurationManager.isUnicast(CONFIG_ID_2));
    }
}
