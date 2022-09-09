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

import static androidx.core.uwb.backend.impl.internal.Utils.CONFIG_ID_1;
import static androidx.core.uwb.backend.impl.internal.Utils.INFREQUENT;
import static androidx.core.uwb.backend.impl.internal.Utils.STATUS_OK;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;
import android.uwb.UwbManager;

import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraOpenSessionParams;
import com.google.uwb.support.fira.FiraParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class RangingControllerTest {
    @Mock private UwbManager mUwbManager;
    @Mock private RangingSessionCallback mRangingSessionCallback;
    @Mock private UwbComplexChannel mComplexChannel;
    private RangingController mRangingController;

    private static Executor getExecutor() {
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        RangingParameters rangingParameters = new RangingParameters(CONFIG_ID_1, 1,
                new byte[]{1, 2}, mComplexChannel,
                new ArrayList<>(List.of(UwbAddress.getRandomizedShortAddress())), INFREQUENT);
        mRangingController = new RangingController(mUwbManager, getExecutor());
        mRangingController.setRangingParameters(rangingParameters);
        mRangingController.setForTesting(true);
    }

    @Test
    public void testGetOpenSessionParams() {
        FiraOpenSessionParams params = mRangingController.getOpenSessionParams();
        assertEquals(params.getDeviceType(), FiraParams.RANGING_DEVICE_TYPE_CONTROLLER);
    }

    @Test
    public void testGetComplexChannel() {
        UwbComplexChannel channel = mRangingController.getComplexChannel();
        assertEquals(channel.getChannel(), Utils.channelForTesting);
        assertEquals(channel.getPreambleIndex(), Utils.preambleIndexForTesting);
    }

    @Test
    public void testGetBestAvailableComplexChannel() {
        UwbComplexChannel channel = mRangingController.getBestAvailableComplexChannel();
        assertEquals(channel.getChannel(), Utils.channelForTesting);
    }

    @Test
    public void testStartRanging() {
        mRangingController.getLocalAddress();
        mRangingController.getComplexChannel();
        int status = mRangingController.startRanging(mRangingSessionCallback);
        verify(mUwbManager).openRangingSession(any(), any(), any());
        assertEquals(status, STATUS_OK);
    }
}
