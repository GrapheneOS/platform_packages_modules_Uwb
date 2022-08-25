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

package com.android.server.uwb.profile;

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.uwb.pm.ControlleeInfo;
import com.android.server.uwb.pm.UwbCapability;

import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraProtocolVersion;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class ControleeInfoTest {

    @Test
    public void testConversion() {
        FiraProtocolVersion minPhyVersionSupported = new FiraProtocolVersion(0, 1);
        FiraProtocolVersion maxPhyVersionSupported = new FiraProtocolVersion(1, 1);
        FiraProtocolVersion minMacVersionSupported = new FiraProtocolVersion(0, 1);
        FiraProtocolVersion maxMacVersionSupported = new FiraProtocolVersion(1, 1);

        Optional<EnumSet<FiraParams.DeviceRoleCapabilityFlag>> deviceRoles = Optional.of(
                EnumSet.noneOf(FiraParams.DeviceRoleCapabilityFlag.class));
        deviceRoles.get().add(FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);
        deviceRoles.get().add(FiraParams.DeviceRoleCapabilityFlag.HAS_CONTROLLER_RESPONDER_SUPPORT);

        Optional<EnumSet<FiraParams.RangingRoundCapabilityFlag>> rangingMethod = Optional.of(
                EnumSet.noneOf(FiraParams.RangingRoundCapabilityFlag.class));
        rangingMethod.get().add(FiraParams.RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT);

        Optional<EnumSet<FiraParams.StsCapabilityFlag>> stsConfig = Optional.of(
                EnumSet.noneOf(FiraParams.StsCapabilityFlag.class));
        stsConfig.get().add(FiraParams.StsCapabilityFlag.HAS_DYNAMIC_STS_SUPPORT);
        Optional<EnumSet<FiraParams.MultiNodeCapabilityFlag>> multiNodeMode =
                Optional.empty();

        Optional<Byte> rangingTimeStruct = Optional.empty();
        Optional<Byte> scheduledMode = Optional.empty();
        Optional<Boolean> hoppingMode = Optional.of(true);
        Optional<Boolean> blockStriding = Optional.of(false);
        Optional<Boolean> uwbInitiationTime = Optional.of(false);
        Optional<List<Integer>> channels = Optional.empty();
        Optional<EnumSet<FiraParams.RframeCapabilityFlag>> rFrameConfig = Optional.empty();
        Optional<EnumSet<FiraParams.PsduDataRateCapabilityFlag>> ccConstraintLength =
                Optional.empty();
        Optional<EnumSet<FiraParams.AoaCapabilityFlag>> aoaSupport = Optional.of(EnumSet.noneOf(
                FiraParams.AoaCapabilityFlag.class));
        aoaSupport.get().add(FiraParams.AoaCapabilityFlag.HAS_AZIMUTH_SUPPORT);
        aoaSupport.get().add(FiraParams.AoaCapabilityFlag.HAS_FULL_AZIMUTH_SUPPORT);
        aoaSupport.get().add(FiraParams.AoaCapabilityFlag.HAS_ELEVATION_SUPPORT);
        aoaSupport.get().add(FiraParams.AoaCapabilityFlag.HAS_FOM_SUPPORT);

        Optional<Byte> extendedMacSupport = Optional.empty();

        UwbCapability uwbCapability = new UwbCapability.Builder()
                .setMinPhyVersionSupported(minPhyVersionSupported)
                .setMaxPhyVersionSupported(maxPhyVersionSupported)
                .setMinMacVersionSupported(minMacVersionSupported)
                .setMaxMacVersionSupported(maxMacVersionSupported)
                .setDeviceRoles(deviceRoles)
                .setRangingMethod(rangingMethod)
                .setStsConfig(stsConfig)
                .setMultiMode(multiNodeMode)
                .setRangingTimeStruct(rangingTimeStruct)
                .setScheduledMode(scheduledMode)
                .setHoppingMode(hoppingMode)
                .setBlockStriding(blockStriding)
                .setUwbInitiationTime(uwbInitiationTime)
                .setChannels(channels)
                .setRframeConfig(rFrameConfig)
                .setCcConstraintLength(ccConstraintLength)
                .setAoaSupport(aoaSupport)
                .setExtendedMacSupport(extendedMacSupport)
                .build();
        ControlleeInfo controlleeInfo = new ControlleeInfo.Builder()
                .setUwbCapability(uwbCapability)
                .build();

        byte[] controleeRaw = controlleeInfo.toBytes();

        ControlleeInfo controleeInfo1 = ControlleeInfo.fromBytes(controleeRaw);
        UwbCapability uwbCapability1 = controleeInfo1.mUwbCapability.get();

        assertEquals(uwbCapability1.mMinPhyVersionSupported, minPhyVersionSupported);
        assertEquals(uwbCapability1.mMaxPhyVersionSupported, maxPhyVersionSupported);
        assertEquals(uwbCapability1.mMinMacVersionSupported, minMacVersionSupported);
        assertEquals(uwbCapability1.mMaxMacVersionSupported, maxMacVersionSupported);
        assertEquals(uwbCapability1.mDeviceRoles, deviceRoles);
        assertEquals(uwbCapability1.mRangingMethod, rangingMethod);
        assertEquals(uwbCapability1.mStsConfig, stsConfig);
        assertEquals(uwbCapability1.mMultiNodeMode, multiNodeMode);
        assertEquals(uwbCapability1.mRangingTimeStruct, rangingTimeStruct);
        assertEquals(uwbCapability1.mScheduledMode, scheduledMode);
        assertEquals(uwbCapability1.mHoppingMode, hoppingMode);
        assertEquals(uwbCapability1.mBlockStriding, blockStriding);
        assertEquals(uwbCapability1.mUwbInitiationTime, uwbInitiationTime);
        assertEquals(uwbCapability1.mChannels, channels);
        assertEquals(uwbCapability1.mRframeConfig, rFrameConfig);
        assertEquals(uwbCapability1.mCcConstraintLength, ccConstraintLength);
        assertEquals(uwbCapability1.mAoaSupport, aoaSupport);
        assertEquals(uwbCapability1.mExtendedMacSupport, extendedMacSupport);
    }
}
