/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.ranging.tests;

import static android.ranging.RangingPreference.DEVICE_ROLE_INITIATOR;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;
import static android.ranging.uwb.UwbRangingParams.CONFIG_UNICAST_DS_TWR;
import static android.ranging.uwb.UwbRangingParams.DURATION_1_MS;
import static android.ranging.uwb.UwbRangingParams.DURATION_2_MS;

import static org.mockito.Mockito.mock;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.ranging.RangingTechnology;
import com.android.server.ranging.session.RangingSessionConfig;
import com.android.server.ranging.session.RangingSessionConfig.MulticastTechnologyConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.Set;

@SuppressWarnings("ConstantConditions")
@RunWith(JUnit4.class)
@SmallTest
public class RangingSessionConfigTest {

    private RangingSessionConfig mConfig;

    private UwbRangingParams.Builder generateUwbParams(UwbAddress peerAddress) {
        return new UwbRangingParams.Builder(
                10,
                CONFIG_UNICAST_DS_TWR,
                UwbAddress.fromBytes(new byte[]{1, 2}),
                peerAddress)
                .setComplexChannel(
                        new UwbComplexChannel.Builder()
                                .setChannel(9)
                                .setPreambleIndex(11)
                                .build())
                .setRangingUpdateRate(UPDATE_RATE_NORMAL);
    }

    @Before
    public void setup() {
        mConfig = new RangingSessionConfig.Builder()
                .setDeviceRole(DEVICE_ROLE_INITIATOR)
                .setSessionConfig(new SessionConfig.Builder().build())
                .build();
    }

    @Test
    public void should_combineIdenticallyConfiguredUwbSessions() {
        List<Pair<RangingDevice, UwbAddress>> peers = List.of(
                Pair.create(mock(RangingDevice.class), UwbAddress.fromBytes(new byte[]{1, 2})),
                Pair.create(mock(RangingDevice.class), UwbAddress.fromBytes(new byte[]{3, 4})));

        Set<RawRangingDevice> deviceParams = Set.of(
                new RawRangingDevice.Builder()
                        .setRangingDevice(peers.get(0).first)
                        .setUwbRangingParams(generateUwbParams(peers.get(0).second).build())
                        .build(),
                new RawRangingDevice.Builder()
                        .setRangingDevice(peers.get(1).first)
                        .setUwbRangingParams(generateUwbParams(peers.get(1).second).build())
                        .build()
        );

        ImmutableSet<TechnologyConfig> tcs = mConfig.getTechnologyConfigs(deviceParams);

        Assert.assertEquals(1, tcs.size());

        TechnologyConfig tc = Iterables.getOnlyElement(tcs);

        Assert.assertEquals(RangingTechnology.UWB, tc.getTechnology());
        Assert.assertTrue(tc instanceof MulticastTechnologyConfig);

        MulticastTechnologyConfig mtc = (MulticastTechnologyConfig) tc;
        Assert.assertEquals(2, mtc.getPeerDevices().size());
        Assert.assertTrue(mtc.getPeerDevices().contains(peers.get(0).first));
        Assert.assertTrue(mtc.getPeerDevices().contains(peers.get(1).first));
    }

    @Test
    public void shouldNot_combineUniquelyConfiguredUwbSessions() {
        List<Pair<RangingDevice, UwbAddress>> peers = List.of(
                Pair.create(mock(RangingDevice.class), UwbAddress.fromBytes(new byte[]{1, 2})),
                Pair.create(mock(RangingDevice.class), UwbAddress.fromBytes(new byte[]{3, 4})));

        Set<RawRangingDevice> deviceParams = Set.of(
                new RawRangingDevice.Builder()
                        .setRangingDevice(peers.get(0).first)
                        .setUwbRangingParams(
                                generateUwbParams(peers.get(0).second)
                                        .setSlotDuration(DURATION_1_MS)
                                        .build())
                        .build(),
                new RawRangingDevice.Builder()
                        .setRangingDevice(peers.get(1).first)
                        .setUwbRangingParams(
                                generateUwbParams(peers.get(1).second)
                                        .setSlotDuration(DURATION_2_MS)
                                        .build())
                        .build()
        );

        ImmutableSet<TechnologyConfig> tcs = mConfig.getTechnologyConfigs(deviceParams);
        Assert.assertEquals(2, tcs.size());

        for (TechnologyConfig tc : tcs) {
            Assert.assertEquals(RangingTechnology.UWB, tc.getTechnology());
            Assert.assertTrue(tc instanceof MulticastTechnologyConfig);

            MulticastTechnologyConfig mtc = (MulticastTechnologyConfig) tc;
            Assert.assertEquals(1, mtc.getPeerDevices().size());
        }
    }
}
