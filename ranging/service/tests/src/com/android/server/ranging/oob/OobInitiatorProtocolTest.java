/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ranging.oob;

import static com.google.common.truth.Truth.assertThat;

import android.ranging.RangingDevice;

import com.android.server.ranging.RangingInjector;
import com.android.server.ranging.oob.packets.ConfigurationRequestV1;
import com.android.server.ranging.oob.packets.ConfigurationRequestV3;
import com.android.server.ranging.oob.packets.MotionIndicator;
import com.android.server.ranging.oob.packets.Version;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

@RunWith(JUnit4.class)
public class OobInitiatorProtocolTest {

    @Mock private RangingInjector mRangingInjector;
    @Mock private RangingDevice mRangingDevice;

    private OobInitiatorProtocol mOobInitiatorProtocol;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mOobInitiatorProtocol = new OobInitiatorProtocol(mRangingInjector);
    }

    @Test
    public void getConfigurationRequest_olderVersion_returnsConfigurationRequestV1() {
        mOobInitiatorProtocol.mPeerVersions.put(mRangingDevice, Version.V1);

        Object request =
                mOobInitiatorProtocol.getConfigurationRequest(
                        mRangingDevice, Collections.emptySet(), MotionIndicator.Supported);

        assertThat(request).isInstanceOf(ConfigurationRequestV1.class);
    }

    @Test
    public void getConfigurationRequest_currentVersion_returnsConfigurationRequestV3() {
        mOobInitiatorProtocol.mPeerVersions.put(mRangingDevice, Version.Current);

        Object request =
                mOobInitiatorProtocol.getConfigurationRequest(
                        mRangingDevice, Collections.emptySet(), MotionIndicator.Supported);

        assertThat(request).isInstanceOf(ConfigurationRequestV3.class);
    }
}
