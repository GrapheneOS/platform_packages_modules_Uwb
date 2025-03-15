/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.server.ranging.tests.cs;

import static android.ranging.ble.cs.BleCsRangingCapabilities.CS_SECURITY_LEVEL_FOUR;
import static android.ranging.ble.cs.BleCsRangingCapabilities.CS_SECURITY_LEVEL_ONE;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_FREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_INFREQUENT;
import static android.ranging.raw.RawRangingDevice.UPDATE_RATE_NORMAL;

import static com.android.server.ranging.cs.CsConfig.CS_UPDATE_RATE_DURATIONS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.ranging.RangingDevice;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingCapabilities;
import android.ranging.oob.OobInitiatorRangingConfig;
import android.util.Pair;
import android.util.Range;

import com.android.server.ranging.RangingEngine.ConfigSelectionException;
import com.android.server.ranging.cs.CsConfig;
import com.android.server.ranging.cs.CsConfigSelector;
import com.android.server.ranging.cs.CsOobCapabilities;
import com.android.server.ranging.cs.CsOobConfig;
import com.android.server.ranging.oob.CapabilityResponseMessage;
import com.android.server.ranging.oob.SetConfigurationMessage.TechnologyOobConfig;
import com.android.server.ranging.session.RangingSessionConfig.TechnologyConfig;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Duration;

@RunWith(MockitoJUnitRunner.class)
public class CsConfigSelectorTest {

    private CsConfigSelector mSelector;

    private @Mock SessionConfig mMockSessionConfig;
    private @Mock OobInitiatorRangingConfig mMockOobConfig;
    private @Mock BleCsRangingCapabilities mMockCapabilities;
    private @Mock CsOobCapabilities mMockOobCapabilities;
    private @Mock CapabilityResponseMessage mMockCapabilityResponse;
    private @Mock RangingDevice mMockPeerDevice;

    @Before
    public void setup() {
        when(mMockCapabilities.getSupportedSecurityLevels())
                .thenReturn(ImmutableSet.of(CS_SECURITY_LEVEL_ONE, CS_SECURITY_LEVEL_FOUR));
        when(mMockOobConfig.getRangingIntervalRange())
                .thenReturn(Range.create(
                        CS_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_NORMAL),
                        CS_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_NORMAL)));

        when(mMockCapabilityResponse.getCsCapabilities()).thenReturn(mMockOobCapabilities);
        when(mMockOobCapabilities.getBluetoothAddress()).thenReturn("AC:37:43:BC:A9:28");
    }

    @Test
    public void constructor_withCapableConfig() throws ConfigSelectionException {
        new CsConfigSelector(mMockSessionConfig, mMockOobConfig, mMockCapabilities);
    }

    @Test(expected = ConfigSelectionException.class)
    public void constructorFails_whenIncapableOfConfiguredSecurityLevel()
            throws ConfigSelectionException {

        when(mMockCapabilities.getSupportedSecurityLevels()).thenReturn(ImmutableSet.of());
        new CsConfigSelector(mMockSessionConfig, mMockOobConfig, mMockCapabilities);
    }

    @Test(expected = ConfigSelectionException.class)
    public void constructorFails_whenIncapableOfConfiguredRangingInterval()
            throws ConfigSelectionException {

        when(mMockOobConfig.getRangingIntervalRange())
                .thenReturn(Range.create(Duration.ofMillis(333), Duration.ofMillis(334)));
        new CsConfigSelector(mMockSessionConfig, mMockOobConfig, mMockCapabilities);
    }

    @Test
    public void addPeerCapabilities_addsPeerToConfigure() throws ConfigSelectionException {
        mSelector = new CsConfigSelector(
                mMockSessionConfig, mMockOobConfig, mMockCapabilities);


        mSelector.addPeerCapabilities(mMockPeerDevice, mMockCapabilityResponse);

        assertTrue(mSelector.hasPeersToConfigure());
    }

    @Test(expected = ConfigSelectionException.class)
    public void addPeerCapabilities_failsWhenCsCapabilitiesNull() throws ConfigSelectionException {
        mSelector = new CsConfigSelector(
                mMockSessionConfig, mMockOobConfig, mMockCapabilities);
        when(mMockCapabilityResponse.getCsCapabilities()).thenReturn(null);

        mSelector.addPeerCapabilities(mMockPeerDevice, mMockCapabilityResponse);
    }

    @Test
    public void selectConfigs_selectsCompatibleConfig() throws ConfigSelectionException {
        mSelector = new CsConfigSelector(
                mMockSessionConfig, mMockOobConfig, mMockCapabilities);

        mSelector.addPeerCapabilities(mMockPeerDevice, mMockCapabilityResponse);

        Pair<ImmutableSet<TechnologyConfig>, ImmutableMap<RangingDevice, TechnologyOobConfig>>
                configs = mSelector.selectConfigs();

        assertNotNull(configs);
        assertNotNull(configs.first);
        assertNotNull(configs.second);
        assertFalse(configs.first.isEmpty());
        assertFalse(configs.second.isEmpty());

        CsConfig csConfig = (CsConfig) Iterators.getOnlyElement(configs.first.iterator());
        assertEquals("AC:37:43:BC:A9:28", csConfig.getRangingParams().getPeerBluetoothAddress());
        assertEquals(UPDATE_RATE_NORMAL, csConfig.getRangingParams().getRangingUpdateRate());
        assertEquals(CS_SECURITY_LEVEL_ONE, csConfig.getRangingParams().getSecurityLevel());

        CsOobConfig peerConfig = (CsOobConfig) configs.second.get(mMockPeerDevice);
        assertNotNull(peerConfig);
    }

    @Test
    public void selectConfigs_selectsSecureSecurityLevel_whenConfigured()
            throws ConfigSelectionException {

        when(mMockOobConfig.getSecurityLevel())
                .thenReturn(OobInitiatorRangingConfig.SECURITY_LEVEL_SECURE);

        mSelector = new CsConfigSelector(mMockSessionConfig, mMockOobConfig, mMockCapabilities);
        mSelector.addPeerCapabilities(mMockPeerDevice, mMockCapabilityResponse);

        Pair<ImmutableSet<TechnologyConfig>, ImmutableMap<RangingDevice, TechnologyOobConfig>>
                configs = mSelector.selectConfigs();

        CsConfig csConfig = (CsConfig) Iterators.getOnlyElement(configs.first.iterator());
        assertEquals(CS_SECURITY_LEVEL_FOUR, csConfig.getRangingParams().getSecurityLevel());
    }

    @Test
    public void selectConfigs_selectsFrequentUpdateRate_whenConfigured()
            throws ConfigSelectionException {

        when(mMockOobConfig.getRangingIntervalRange())
                .thenReturn(Range.create(
                        CS_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_FREQUENT),
                        CS_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_FREQUENT)));

        mSelector = new CsConfigSelector(
                mMockSessionConfig, mMockOobConfig, mMockCapabilities);
        mSelector.addPeerCapabilities(mMockPeerDevice, mMockCapabilityResponse);

        Pair<ImmutableSet<TechnologyConfig>, ImmutableMap<RangingDevice, TechnologyOobConfig>>
                configs = mSelector.selectConfigs();
        CsConfig csConfig = (CsConfig) Iterators.getOnlyElement(configs.first.iterator());

        assertEquals(UPDATE_RATE_FREQUENT, csConfig.getRangingParams().getRangingUpdateRate());
    }

    @Test
    public void selectConfigs_selectsInfrequentUpdateRate_whenConfigured()
            throws ConfigSelectionException {

        when(mMockOobConfig.getRangingIntervalRange()).thenReturn(
                Range.create(
                        CS_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_INFREQUENT),
                        CS_UPDATE_RATE_DURATIONS.get(UPDATE_RATE_INFREQUENT)));

        mSelector = new CsConfigSelector(
                mMockSessionConfig, mMockOobConfig, mMockCapabilities);
        mSelector.addPeerCapabilities(mMockPeerDevice, mMockCapabilityResponse);

        Pair<ImmutableSet<TechnologyConfig>, ImmutableMap<RangingDevice, TechnologyOobConfig>>
                configs = mSelector.selectConfigs();
        CsConfig csConfig = (CsConfig) configs.first.iterator().next();

        assertEquals(UPDATE_RATE_INFREQUENT, csConfig.getRangingParams().getRangingUpdateRate());
    }
}
