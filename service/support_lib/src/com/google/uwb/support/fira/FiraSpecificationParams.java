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

package com.google.uwb.support.fira;

import static java.util.Objects.requireNonNull;

import android.os.PersistableBundle;

import com.google.uwb.support.base.FlagEnum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class FiraSpecificationParams extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final FiraProtocolVersion mMinPhyVersionSupported;
    private final FiraProtocolVersion mMaxPhyVersionSupported;
    private final FiraProtocolVersion mMinMacVersionSupported;
    private final FiraProtocolVersion mMaxMacVersionSupported;

    private final List<Integer> mSupportedChannels;

    private final EnumSet<AoaCapabilityFlag> mAoaCapabilities;

    private final EnumSet<DeviceRoleCapabilityFlag> mDeviceRoleCapabilities;

    private final boolean mHasBlockStridingSupport;

    private final boolean mHasNonDeferredModeSupport;

    private final boolean mHasTxAdaptivePayloadPowerSupport;

    private final int mInitiationTimeMs;

    private final EnumSet<MacFcsCrcCapabilityFlag> mMacFcsCrcCapabilities;

    private final EnumSet<MultiNodeCapabilityFlag> mMultiNodeCapabilities;

    private final EnumSet<PreambleCapabilityFlag> mPreambleCapabilities;

    private final EnumSet<PrfCapabilityFlag> mPrfCapabilities;

    private final EnumSet<RangingRoundCapabilityFlag> mRangingRoundCapabilities;

    private final EnumSet<RframeCapabilityFlag> mRframeCapabilities;

    private final EnumSet<SfdCapabilityFlag> mSfdCapabilities;

    private final EnumSet<StsCapabilityFlag> mStsCapabilities;

    private final EnumSet<StsSegmentsCapabilityFlag> mStsSegmentsCapabilities;

    private final EnumSet<BprfPhrDataRateCapabilityFlag> mBprfPhrDataRateCapabilities;

    private final EnumSet<PsduDataRateCapabilityFlag> mPsduDataRateCapabilities;

    private static final String KEY_MIN_PHY_VERSION = "min_phy_version";
    private static final String KEY_MAX_PHY_VERSION = "max_phy_version";
    private static final String KEY_MIN_MAC_VERSION = "min_mac_version";
    private static final String KEY_MAX_MAC_VERSION = "max_mac_version";

    private static final String KEY_SUPPORTED_CHANNELS = "channels";
    private static final String KEY_AOA_CAPABILITIES = "aoa_capabilities";
    private static final String KEY_DEVICE_ROLE_CAPABILITIES = "device_role_capabilities";
    private static final String KEY_BLOCK_STRIDING_SUPPORT = "block_striding";
    private static final String KEY_NON_DEFERRED_MODE_SUPPORT = "non_deferred_mode";
    private static final String KEY_TX_ADAPTIVE_PAYLOAD_POWER_SUPPORT = "tx_adaptive_payload_power";
    private static final String KEY_INITIATION_TIME_MS = "initiation_time_ms";
    private static final String KEY_FCS_CRC_CAPABILITIES = "fcs_crc_capabilities";
    private static final String KEY_MULTI_NODE_CAPABILITIES = "multi_node_capabilities";
    private static final String KEY_PREAMBLE_CAPABILITIES = "preamble_capabilities";
    private static final String KEY_PRF_CAPABILITIES = "prf_capabilities";
    private static final String KEY_RANGING_ROUND_CAPABILITIES = "ranging_round_capabilities";
    private static final String KEY_RFRAME_CAPABILITIES = "rframe_capabilities";
    private static final String KEY_SFD_CAPABILITIES = "sfd_capabilities";
    private static final String KEY_STS_CAPABILITIES = "sts_capabilities";
    private static final String KEY_STS_SEGMENT_CAPABILITIES = "sts_segment_capabilities";
    private static final String KEY_BPRF_PHR_DATA_RATE_CAPABILITIES =
            "bprf_phr_data_rate_capabilities";
    private static final String KEY_PSDU_DATA_RATE_CAPABILITIES = "psdu_data_rate_capabilities";

    private FiraSpecificationParams(
            FiraProtocolVersion minPhyVersionSupported,
            FiraProtocolVersion maxPhyVersionSupported,
            FiraProtocolVersion minMacVersionSupported,
            FiraProtocolVersion maxMacVersionSupported,
            List<Integer> supportedChannels,
            EnumSet<AoaCapabilityFlag> aoaCapabilities,
            EnumSet<DeviceRoleCapabilityFlag> deviceRoleCapabilities,
            boolean hasBlockStridingSupport,
            boolean hasNonDeferredModeSupport,
            boolean hasTxAdaptivePayloadPowerSupport,
            int initiationTimeMs,
            EnumSet<MacFcsCrcCapabilityFlag> macFcsCrcCapabilities,
            EnumSet<MultiNodeCapabilityFlag> multiNodeCapabilities,
            EnumSet<PreambleCapabilityFlag> preambleCapabilities,
            EnumSet<PrfCapabilityFlag> prfCapabilities,
            EnumSet<RangingRoundCapabilityFlag> rangingRoundCapabilities,
            EnumSet<RframeCapabilityFlag> rframeCapabilities,
            EnumSet<SfdCapabilityFlag> sfdCapabilities,
            EnumSet<StsCapabilityFlag> stsCapabilities,
            EnumSet<StsSegmentsCapabilityFlag> stsSegmentsCapabilities,
            EnumSet<BprfPhrDataRateCapabilityFlag> bprfPhrDataRateCapabilities,
            EnumSet<PsduDataRateCapabilityFlag> psduDataRateCapabilities) {
        mMinPhyVersionSupported = minPhyVersionSupported;
        mMaxPhyVersionSupported = maxPhyVersionSupported;
        mMinMacVersionSupported = minMacVersionSupported;
        mMaxMacVersionSupported = maxMacVersionSupported;
        mSupportedChannels = supportedChannels;
        mAoaCapabilities = aoaCapabilities;
        mDeviceRoleCapabilities = deviceRoleCapabilities;
        mHasBlockStridingSupport = hasBlockStridingSupport;
        mHasNonDeferredModeSupport = hasNonDeferredModeSupport;
        mHasTxAdaptivePayloadPowerSupport = hasTxAdaptivePayloadPowerSupport;
        mInitiationTimeMs = initiationTimeMs;
        mMacFcsCrcCapabilities = macFcsCrcCapabilities;
        mMultiNodeCapabilities = multiNodeCapabilities;
        mPreambleCapabilities = preambleCapabilities;
        mPrfCapabilities = prfCapabilities;
        mRangingRoundCapabilities = rangingRoundCapabilities;
        mRframeCapabilities = rframeCapabilities;
        mSfdCapabilities = sfdCapabilities;
        mStsCapabilities = stsCapabilities;
        mStsSegmentsCapabilities = stsSegmentsCapabilities;
        mBprfPhrDataRateCapabilities = bprfPhrDataRateCapabilities;
        mPsduDataRateCapabilities = psduDataRateCapabilities;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public FiraProtocolVersion getMinPhyVersionSupported() {
        return mMinPhyVersionSupported;
    }

    public FiraProtocolVersion getMaxPhyVersionSupported() {
        return mMaxPhyVersionSupported;
    }

    public FiraProtocolVersion getMinMacVersionSupported() {
        return mMinMacVersionSupported;
    }

    public FiraProtocolVersion getMaxMacVersionSupported() {
        return mMaxMacVersionSupported;
    }

    public List<Integer> getSupportedChannels() {
        return mSupportedChannels;
    }

    public EnumSet<AoaCapabilityFlag> getAoaCapabilities() {
        return mAoaCapabilities;
    }

    public EnumSet<DeviceRoleCapabilityFlag> getDeviceRoleCapabilities() {
        return mDeviceRoleCapabilities;
    }

    public boolean hasBlockStridingSupport() {
        return mHasBlockStridingSupport;
    }

    public boolean hasNonDeferredModeSupport() {
        return mHasNonDeferredModeSupport;
    }

    public boolean hasTxAdaptivePayloadPowerSupport() {
        return mHasTxAdaptivePayloadPowerSupport;
    }

    public int getInitiationTimeMs() {
        return mInitiationTimeMs;
    }

    public EnumSet<MacFcsCrcCapabilityFlag> getMacFcsCrcCapabilities() {
        return mMacFcsCrcCapabilities;
    }

    public EnumSet<MultiNodeCapabilityFlag> getMultiNodeCapabilities() {
        return mMultiNodeCapabilities;
    }

    public EnumSet<PreambleCapabilityFlag> getPreambleCapabilities() {
        return mPreambleCapabilities;
    }

    public EnumSet<PrfCapabilityFlag> getPrfCapabilities() {
        return mPrfCapabilities;
    }

    public EnumSet<RangingRoundCapabilityFlag> getRangingRoundCapabilities() {
        return mRangingRoundCapabilities;
    }

    public EnumSet<RframeCapabilityFlag> getRframeCapabilities() {
        return mRframeCapabilities;
    }

    public EnumSet<SfdCapabilityFlag> getSfdCapabilities() {
        return mSfdCapabilities;
    }

    public EnumSet<StsCapabilityFlag> getStsCapabilities() {
        return mStsCapabilities;
    }

    public EnumSet<StsSegmentsCapabilityFlag> getStsSegmentsCapabilities() {
        return mStsSegmentsCapabilities;
    }

    public EnumSet<BprfPhrDataRateCapabilityFlag> getBprfPhrDataRateCapabilities() {
        return mBprfPhrDataRateCapabilities;
    }

    public EnumSet<PsduDataRateCapabilityFlag> getPsduDataRateCapabilities() {
        return mPsduDataRateCapabilities;
    }

    private static int[] toIntArray(List<Integer> data) {
        int[] res = new int[data.size()];
        for (int i = 0; i < data.size(); i++) {
            res[i] = data.get(i);
        }
        return res;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putString(KEY_MIN_PHY_VERSION, mMinPhyVersionSupported.toString());
        bundle.putString(KEY_MAX_PHY_VERSION, mMaxPhyVersionSupported.toString());
        bundle.putString(KEY_MIN_MAC_VERSION, mMinMacVersionSupported.toString());
        bundle.putString(KEY_MAX_MAC_VERSION, mMaxMacVersionSupported.toString());
        bundle.putIntArray(KEY_SUPPORTED_CHANNELS, toIntArray(mSupportedChannels));
        bundle.putInt(KEY_AOA_CAPABILITIES, FlagEnum.toInt(mAoaCapabilities));
        bundle.putInt(KEY_DEVICE_ROLE_CAPABILITIES, FlagEnum.toInt(mDeviceRoleCapabilities));
        bundle.putBoolean(KEY_BLOCK_STRIDING_SUPPORT, mHasBlockStridingSupport);
        bundle.putBoolean(KEY_NON_DEFERRED_MODE_SUPPORT, mHasNonDeferredModeSupport);
        bundle.putBoolean(KEY_TX_ADAPTIVE_PAYLOAD_POWER_SUPPORT, mHasTxAdaptivePayloadPowerSupport);
        bundle.putInt(KEY_INITIATION_TIME_MS, mInitiationTimeMs);
        bundle.putInt(KEY_FCS_CRC_CAPABILITIES, FlagEnum.toInt(mMacFcsCrcCapabilities));
        bundle.putInt(KEY_MULTI_NODE_CAPABILITIES, FlagEnum.toInt(mMultiNodeCapabilities));
        bundle.putInt(KEY_PREAMBLE_CAPABILITIES, FlagEnum.toInt(mPreambleCapabilities));
        bundle.putInt(KEY_PRF_CAPABILITIES, FlagEnum.toInt(mPrfCapabilities));
        bundle.putInt(KEY_RANGING_ROUND_CAPABILITIES, FlagEnum.toInt(mRangingRoundCapabilities));
        bundle.putInt(KEY_RFRAME_CAPABILITIES, FlagEnum.toInt(mRframeCapabilities));
        bundle.putInt(KEY_SFD_CAPABILITIES, FlagEnum.toInt(mSfdCapabilities));
        bundle.putInt(KEY_STS_CAPABILITIES, FlagEnum.toInt(mStsCapabilities));
        bundle.putInt(KEY_STS_SEGMENT_CAPABILITIES, FlagEnum.toInt(mStsSegmentsCapabilities));
        bundle.putInt(
                KEY_BPRF_PHR_DATA_RATE_CAPABILITIES, FlagEnum.toInt(mBprfPhrDataRateCapabilities));
        bundle.putInt(KEY_PSDU_DATA_RATE_CAPABILITIES, FlagEnum.toInt(mPsduDataRateCapabilities));

        return bundle;
    }

    public static FiraSpecificationParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static List<Integer> toIntList(int[] data) {
        List<Integer> res = new ArrayList<>();
        for (int datum : data) {
            res.add(datum);
        }
        return res;
    }

    private static FiraSpecificationParams parseVersion1(PersistableBundle bundle) {
        FiraSpecificationParams.Builder builder = new FiraSpecificationParams.Builder();
        List<Integer> supportedChannels =
                toIntList(requireNonNull(bundle.getIntArray(KEY_SUPPORTED_CHANNELS)));
        return builder.setMinPhyVersionSupported(
                        FiraProtocolVersion.fromString(bundle.getString(KEY_MIN_PHY_VERSION)))
                .setMaxPhyVersionSupported(
                        FiraProtocolVersion.fromString(bundle.getString(KEY_MAX_PHY_VERSION)))
                .setMinMacVersionSupported(
                        FiraProtocolVersion.fromString(bundle.getString(KEY_MIN_MAC_VERSION)))
                .setMaxMacVersionSupported(
                        FiraProtocolVersion.fromString(bundle.getString(KEY_MAX_MAC_VERSION)))
                .setSupportedChannels(supportedChannels)
                .setAoaCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_AOA_CAPABILITIES), AoaCapabilityFlag.values()))
                .setDeviceRoleCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_DEVICE_ROLE_CAPABILITIES),
                                DeviceRoleCapabilityFlag.values()))
                .hasBlockStridingSupport(bundle.getBoolean(KEY_BLOCK_STRIDING_SUPPORT))
                .hasNonDeferredModeSupport(bundle.getBoolean(KEY_NON_DEFERRED_MODE_SUPPORT))
                .hasTxAdaptivePayloadPowerSupport(
                        bundle.getBoolean(KEY_TX_ADAPTIVE_PAYLOAD_POWER_SUPPORT))
                .setInitiationTimeMs(bundle.getInt(KEY_INITIATION_TIME_MS))
                .setMacFcsCrcCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_FCS_CRC_CAPABILITIES),
                                MacFcsCrcCapabilityFlag.values()))
                .setMultiNodeCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_MULTI_NODE_CAPABILITIES),
                                MultiNodeCapabilityFlag.values()))
                .setPreambleCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_PREAMBLE_CAPABILITIES),
                                PreambleCapabilityFlag.values()))
                .setPrfCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_PRF_CAPABILITIES), PrfCapabilityFlag.values()))
                .setRangingRoundCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_RANGING_ROUND_CAPABILITIES),
                                RangingRoundCapabilityFlag.values()))
                .setRframeCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_RFRAME_CAPABILITIES),
                                RframeCapabilityFlag.values()))
                .setSfdCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_SFD_CAPABILITIES), SfdCapabilityFlag.values()))
                .setStsCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_STS_CAPABILITIES), StsCapabilityFlag.values()))
                .setStsSegmentsCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_STS_SEGMENT_CAPABILITIES),
                                StsSegmentsCapabilityFlag.values()))
                .setBprfPhrDataRateCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_BPRF_PHR_DATA_RATE_CAPABILITIES),
                                BprfPhrDataRateCapabilityFlag.values()))
                .setPsduDataRateCapabilities(
                        FlagEnum.toEnumSet(
                                bundle.getInt(KEY_PSDU_DATA_RATE_CAPABILITIES),
                                PsduDataRateCapabilityFlag.values()))
                .build();
    }

    /** Builder */
    public static class Builder {
        // Set all default protocol version to FiRa 1.1
        private FiraProtocolVersion mMinPhyVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMaxPhyVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMinMacVersionSupported = new FiraProtocolVersion(1, 1);
        private FiraProtocolVersion mMaxMacVersionSupported = new FiraProtocolVersion(1, 1);

        private List<Integer> mSupportedChannels;

        private final EnumSet<AoaCapabilityFlag> mAoaCapabilities =
                EnumSet.noneOf(AoaCapabilityFlag.class);

        // Controller-intiator, Cotrolee-responder are mandatory.
        private final EnumSet<DeviceRoleCapabilityFlag> mDeviceRoleCapabilities =
                EnumSet.of(
                        DeviceRoleCapabilityFlag.HAS_CONTROLLER_INITIATOR_SUPPORT,
                        DeviceRoleCapabilityFlag.HAS_CONTROLEE_RESPONDER_SUPPORT);

        private boolean mHasBlockStridingSupport = false;

        private boolean mHasNonDeferredModeSupport = false;

        private boolean mHasTxAdaptivePayloadPowerSupport = false;

        private int mInitiationTimeMs = 0;

        // CRC16 should be used for BPRF
        private final EnumSet<MacFcsCrcCapabilityFlag> mMacFcsCrcCapabilities =
                EnumSet.of(MacFcsCrcCapabilityFlag.HAS_CRC_16_SUPPORT);

        // Unicast support is mandatory
        private final EnumSet<MultiNodeCapabilityFlag> mMultiNodeCapabilities =
                EnumSet.of(MultiNodeCapabilityFlag.HAS_UNICAST_SUPPORT);

        // 64-bit preamble is mandatory
        private final EnumSet<PreambleCapabilityFlag> mPreambleCapabilities =
                EnumSet.of(PreambleCapabilityFlag.HAS_64_SYMBOLS_SUPPORT);

        // BPRF mode is mandatory
        private final EnumSet<PrfCapabilityFlag> mPrfCapabilities =
                EnumSet.of(PrfCapabilityFlag.HAS_BPRF_SUPPORT);

        // DS-TWR is mandatory
        private final EnumSet<RangingRoundCapabilityFlag> mRangingRoundCapabilities =
                EnumSet.of(RangingRoundCapabilityFlag.HAS_DS_TWR_SUPPORT);

        // SP3 RFrame is mandatory
        private final EnumSet<RframeCapabilityFlag> mRframeCapabilities =
                EnumSet.of(RframeCapabilityFlag.HAS_SP3_RFRAME_SUPPORT);

        // SFD2 is mandatory
        private final EnumSet<SfdCapabilityFlag> mSfdCapabilities =
                EnumSet.of(SfdCapabilityFlag.HAS_SFD2_SUPPORT);

        // STATIC STS is mandatory
        private final EnumSet<StsCapabilityFlag> mStsCapabilities =
                EnumSet.of(StsCapabilityFlag.HAS_STATIC_STS_SUPPORT);

        // 0 or 1 STS segments are mandatory
        private final EnumSet<StsSegmentsCapabilityFlag> mStsSegmentsCapabilities =
                EnumSet.of(
                        StsSegmentsCapabilityFlag.HAS_0_SEGMENT_SUPPORT,
                        StsSegmentsCapabilityFlag.HAS_1_SEGMENT_SUPPORT);

        // 850k PHY Header data rate is mandatory
        private final EnumSet<BprfPhrDataRateCapabilityFlag> mBprfPhrDataRateCapabilities =
                EnumSet.of(BprfPhrDataRateCapabilityFlag.HAS_850K_SUPPORT);

        // 6.81Mb/s PSDU data rate is mandatory
        private final EnumSet<PsduDataRateCapabilityFlag> mPsduDataRateCapabilities =
                EnumSet.of(PsduDataRateCapabilityFlag.HAS_6M81_SUPPORT);

        public FiraSpecificationParams.Builder setMinPhyVersionSupported(
                FiraProtocolVersion version) {
            mMinPhyVersionSupported = version;
            return this;
        }

        public FiraSpecificationParams.Builder setMaxPhyVersionSupported(
                FiraProtocolVersion version) {
            mMaxPhyVersionSupported = version;
            return this;
        }

        public FiraSpecificationParams.Builder setMinMacVersionSupported(
                FiraProtocolVersion version) {
            mMinMacVersionSupported = version;
            return this;
        }

        public FiraSpecificationParams.Builder setMaxMacVersionSupported(
                FiraProtocolVersion version) {
            mMaxMacVersionSupported = version;
            return this;
        }

        public FiraSpecificationParams.Builder setSupportedChannels(
                List<Integer> supportedChannels) {
            mSupportedChannels = List.copyOf(supportedChannels);
            return this;
        }

        public FiraSpecificationParams.Builder setAoaCapabilities(
                Collection<AoaCapabilityFlag> aoaCapabilities) {
            mAoaCapabilities.addAll(aoaCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setDeviceRoleCapabilities(
                Collection<DeviceRoleCapabilityFlag> deviceRoleCapabilities) {
            mDeviceRoleCapabilities.addAll(deviceRoleCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder hasBlockStridingSupport(boolean value) {
            mHasBlockStridingSupport = value;
            return this;
        }

        public FiraSpecificationParams.Builder hasNonDeferredModeSupport(boolean value) {
            mHasNonDeferredModeSupport = value;
            return this;
        }

        public FiraSpecificationParams.Builder hasTxAdaptivePayloadPowerSupport(boolean value) {
            mHasTxAdaptivePayloadPowerSupport = value;
            return this;
        }

        public FiraSpecificationParams.Builder setInitiationTimeMs(int initiationTimeMs) {
            mInitiationTimeMs = initiationTimeMs;
            return this;
        }

        public FiraSpecificationParams.Builder setMacFcsCrcCapabilities(
                Collection<MacFcsCrcCapabilityFlag> macFcsCrcCapabilities) {
            mMacFcsCrcCapabilities.addAll(macFcsCrcCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setMultiNodeCapabilities(
                Collection<MultiNodeCapabilityFlag> multiNodeCapabilities) {
            mMultiNodeCapabilities.addAll(multiNodeCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setPreambleCapabilities(
                Collection<PreambleCapabilityFlag> preambleCapabilities) {
            mPreambleCapabilities.addAll(preambleCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setPrfCapabilities(
                Collection<PrfCapabilityFlag> prfCapabilities) {
            mPrfCapabilities.addAll(prfCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setRangingRoundCapabilities(
                Collection<RangingRoundCapabilityFlag> rangingRoundCapabilities) {
            mRangingRoundCapabilities.addAll(rangingRoundCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setRframeCapabilities(
                Collection<RframeCapabilityFlag> rframeCapabilities) {
            mRframeCapabilities.addAll(rframeCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setSfdCapabilities(
                Collection<SfdCapabilityFlag> sfdCapabilities) {
            mSfdCapabilities.addAll(sfdCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setStsCapabilities(
                Collection<StsCapabilityFlag> stsCapabilities) {
            mStsCapabilities.addAll(stsCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setStsSegmentsCapabilities(
                Collection<StsSegmentsCapabilityFlag> stsSegmentsCapabilities) {
            mStsSegmentsCapabilities.addAll(stsSegmentsCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setBprfPhrDataRateCapabilities(
                Collection<BprfPhrDataRateCapabilityFlag> bprfPhrDataRateCapabilities) {
            mBprfPhrDataRateCapabilities.addAll(bprfPhrDataRateCapabilities);
            return this;
        }

        public FiraSpecificationParams.Builder setPsduDataRateCapabilities(
                Collection<PsduDataRateCapabilityFlag> psduDataRateCapabilities) {
            mPsduDataRateCapabilities.addAll(psduDataRateCapabilities);
            return this;
        }

        public FiraSpecificationParams build() {
            if (mSupportedChannels == null || mSupportedChannels.size() == 0) {
                throw new IllegalStateException("Supported channels are not set");
            }

            return new FiraSpecificationParams(
                    mMinPhyVersionSupported,
                    mMaxPhyVersionSupported,
                    mMinMacVersionSupported,
                    mMaxMacVersionSupported,
                    mSupportedChannels,
                    mAoaCapabilities,
                    mDeviceRoleCapabilities,
                    mHasBlockStridingSupport,
                    mHasNonDeferredModeSupport,
                    mHasTxAdaptivePayloadPowerSupport,
                    mInitiationTimeMs,
                    mMacFcsCrcCapabilities,
                    mMultiNodeCapabilities,
                    mPreambleCapabilities,
                    mPrfCapabilities,
                    mRangingRoundCapabilities,
                    mRframeCapabilities,
                    mSfdCapabilities,
                    mStsCapabilities,
                    mStsSegmentsCapabilities,
                    mBprfPhrDataRateCapabilities,
                    mPsduDataRateCapabilities);
        }
    }
}
