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

package com.google.uwb.support.rftest;

import static com.google.uwb.support.fira.FiraParams.MAC_FCS_TYPE_CRC_16;
import static com.google.uwb.support.fira.FiraParams.MAX_NUMBER_OF_MEASUREMENTS_DEFAULT;
import static com.google.uwb.support.fira.FiraParams.PREAMBLE_DURATION_T64_SYMBOLS;
import static com.google.uwb.support.fira.FiraParams.PRF_MODE_BPRF;
import static com.google.uwb.support.fira.FiraParams.PSDU_DATA_RATE_6M81;
import static com.google.uwb.support.fira.FiraParams.RFRAME_CONFIG_SP3;
import static com.google.uwb.support.fira.FiraParams.SFD_ID_VALUE_2;
import static com.google.uwb.support.fira.FiraParams.STS_SEGMENT_COUNT_VALUE_1;
import static com.google.uwb.support.fira.FiraParams.UWB_CHANNEL_9;
import static com.google.uwb.support.fira.FiraParams.longToUwbAddress;
import static com.google.uwb.support.fira.FiraParams.uwbAddressToLong;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;
import android.uwb.UwbManager;

import androidx.annotation.NonNull;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.fira.FiraParams.MacFcsType;
import com.google.uwb.support.fira.FiraParams.PreambleDuration;
import com.google.uwb.support.fira.FiraParams.PrfMode;
import com.google.uwb.support.fira.FiraParams.PsduDataRate;
import com.google.uwb.support.fira.FiraParams.RangingDeviceRole;
import com.google.uwb.support.fira.FiraParams.RframeConfig;
import com.google.uwb.support.fira.FiraParams.SfdIdValue;
import com.google.uwb.support.fira.FiraParams.StsSegmentCountValue;
import com.google.uwb.support.fira.FiraParams.UwbChannel;
import com.google.uwb.support.fira.FiraParams.UwbPreambleCodeIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Defines parameters to open a Rftest session.
 *
 * <p>This is passed as a bundle to the service API {@link UwbManager#openRangingSession}.
 */
public class RfTestOpenSessionParams extends RfTestParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_SESSION_TYPE = "session_type";
    private static final String KEY_CHANNEL_NUMBER = "channel_number";
    private static final String KEY_NUMBER_OF_CONTROLEES = "number_of_controlees";
    private static final String KEY_DEVICE_ADDRESS = "device_address";
    private static final String KEY_DEST_ADDRESS_LIST = "dest_address_list";
    private static final String KEY_SLOT_DURATION_RSTU = "slot_duration";
    private static final String KEY_STS_INDEX = "sts_index";
    private static final String KEY_FCS_TYPE = "fcs_type";
    private static final String KEY_DEVICE_ROLE = "device_role";
    private static final String KEY_RFRAME_CONFIG = "rframe_config";
    private static final String KEY_PREAMBLE_CODE_INDEX = "preamble_code_index";
    private static final String KEY_SFD_ID = "sfd_id";
    private static final String KEY_PSDU_DATA_RATE = "psdu_data_rate";
    private static final String KEY_PREAMBLE_DURATION = "preamble_duration";
    private static final String KEY_PRF_MODE = "prf_mode";
    private static final String KEY_STS_SEGMENT_COUNT = "sts_segment_count";

    // RF test specific params
    private static final String KEY_NUMBER_OF_PACKETS = "number_of_packets";
    private static final String KEY_T_GAP = "t_gap";
    private static final String KEY_T_START = "t_start";
    private static final String KEY_T_WIN = "t_win";
    private static final String KEY_RANDOMIZE_PSDU = "randomize_psdu";
    private static final String KEY_PHR_RANGING_BIT = "phr_ranging_bit";
    private static final String KEY_RMARKER_TX_START = "rmarker_tx_start";
    private static final String KEY_RMARKER_RX_START = "rmarker_rx_start";
    private static final String KEY_STS_INDEX_AUTO_INCR = "sts_index_auto_incr";
    private static final String KEY_STS_DETECT_BITMAP_EN = "sts_detect_bitmap_en";

    @SessionId
    private final int mSessionId;
    @SessionType
    private final int mSessionType;
    @UwbChannel
    private final int mUwbChannel;
    private final int mNoOfControlee;
    private final UwbAddress mDeviceAddress;
    private final List<UwbAddress> mDestAddressList;
    private final int mSlotDurationRstu;
    private final int mStsIndex;
    @MacFcsType
    private final int mFcsType;
    @RangingDeviceRole
    private final int mDeviceRole;
    @RframeConfig
    private final int mRframeConfig;
    private final int mPreambleCodeIndex;
    @SfdIdValue
    private final int mSfdId;
    @PsduDataRate
    private final int mPsduDataRate;
    @PreambleDuration
    private final int mPreambleDuration;
    @PrfMode
    private final int mPrfMode;
    @StsSegmentCountValue
    private final int mStsSegmentCount;

    private final int mNoOfPackets;
    private final int mTgap;
    private final int mTstart;
    private final int mTwin;
    @RandomizePsdu
    private final int mRandomizePsdu;
    @PhrRangingBit
    private final int mPhrRangingBit;
    private final int mRmarkerTxStart;
    private final int mRmarkerRxStart;
    @StsIndexAutoIncr
    private final int mStsIndexAutoIncr;
    @StsDetectBitmap
    private final int mStsDetectBitmap;

    private RfTestOpenSessionParams(
            int sessionId,
            @SessionType int sessionType,
            @UwbChannel int uwbChannel,
            int noOfControlee,
            UwbAddress deviceAddress,
            List<UwbAddress> destAddressList,
            int slotDurationRstu,
            int stsIndex,
            @MacFcsType int fcsType,
            @RangingDeviceRole int deviceRole,
            @RframeConfig int rframeConfig,
            int preambleCodeIndex,
            @SfdIdValue int sfdId,
            @PsduDataRate int psduDataRate,
            @PreambleDuration int preambleDuration,
            @PrfMode int prfMode,
            @StsSegmentCountValue int stsSegmentCount,
            int noOfPackets,
            int tGap,
            int tStart,
            int tWin,
            @RandomizePsdu int randomizePsdu,
            @PhrRangingBit int phrRangingBit,
            int rmarketTxStart,
            int rmakrkerRxStart,
            @StsIndexAutoIncr int stsIndexAutoIncr,
            @StsDetectBitmap int stsDetectBitmap) {
        mSessionId = sessionId;
        mSessionType = sessionType;
        mUwbChannel = uwbChannel;
        mNoOfControlee = noOfControlee;
        mDeviceAddress = deviceAddress;
        mDestAddressList = destAddressList;
        mSlotDurationRstu = slotDurationRstu;
        mStsIndex = stsIndex;
        mFcsType = fcsType;
        mDeviceRole = deviceRole;
        mRframeConfig = rframeConfig;
        mPreambleCodeIndex = preambleCodeIndex;
        mSfdId = sfdId;
        mPsduDataRate = psduDataRate;
        mPreambleDuration = preambleDuration;
        mPrfMode = prfMode;
        mStsSegmentCount = stsSegmentCount;
        mNoOfPackets = noOfPackets;
        mTgap = tGap;
        mTstart = tStart;
        mTwin = tWin;
        mRandomizePsdu = randomizePsdu;
        mPhrRangingBit = phrRangingBit;
        mRmarkerTxStart = rmarketTxStart;
        mRmarkerRxStart = rmakrkerRxStart;
        mStsIndexAutoIncr = stsIndexAutoIncr;
        mStsDetectBitmap = stsDetectBitmap;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_SESSION_TYPE, mSessionType);
        bundle.putInt(KEY_CHANNEL_NUMBER, mUwbChannel);
        bundle.putInt(KEY_NUMBER_OF_CONTROLEES, mNoOfControlee);
        bundle.putLong(KEY_DEVICE_ADDRESS, uwbAddressToLong(mDeviceAddress));
        if (mDestAddressList != null) {
            long[] destAddressList = new long[mDestAddressList.size()];
            int i = 0;
            for (UwbAddress destAddress : mDestAddressList) {
                destAddressList[i++] = uwbAddressToLong(destAddress);
            }
            bundle.putLongArray(KEY_DEST_ADDRESS_LIST, destAddressList);
        }
        bundle.putInt(KEY_SLOT_DURATION_RSTU, mSlotDurationRstu);
        bundle.putInt(KEY_STS_INDEX, mStsIndex);
        bundle.putInt(KEY_FCS_TYPE, mFcsType);
        bundle.putInt(KEY_DEVICE_ROLE, mDeviceRole);
        bundle.putInt(KEY_RFRAME_CONFIG, mRframeConfig);
        bundle.putInt(KEY_PREAMBLE_CODE_INDEX, mPreambleCodeIndex);
        bundle.putInt(KEY_SFD_ID, mSfdId);
        bundle.putInt(KEY_PSDU_DATA_RATE, mPsduDataRate);
        bundle.putInt(KEY_PREAMBLE_DURATION, mPreambleDuration);
        bundle.putInt(KEY_PRF_MODE, mPrfMode);
        bundle.putInt(KEY_STS_SEGMENT_COUNT, mStsSegmentCount);
        bundle.putInt(KEY_NUMBER_OF_PACKETS, mNoOfPackets);
        bundle.putInt(KEY_T_GAP, mTgap);
        bundle.putInt(KEY_T_START, mTstart);
        bundle.putInt(KEY_T_WIN, mTwin);
        bundle.putInt(KEY_RANDOMIZE_PSDU, mRandomizePsdu);
        bundle.putInt(KEY_PHR_RANGING_BIT, mPhrRangingBit);
        bundle.putInt(KEY_RMARKER_TX_START, mRmarkerTxStart);
        bundle.putInt(KEY_RMARKER_RX_START, mRmarkerRxStart);
        bundle.putInt(KEY_STS_INDEX_AUTO_INCR, mStsIndexAutoIncr);
        bundle.putInt(KEY_STS_DETECT_BITMAP_EN, mStsDetectBitmap);
        return bundle;
    }

    /** Unpack the {@link PersistableBundle} to a {@link RfTestOpenSessionParams} */
    public static RfTestOpenSessionParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static RfTestOpenSessionParams parseBundleVersion1(PersistableBundle bundle) {

        UwbAddress deviceAddress =
                longToUwbAddress(bundle.getLong(KEY_DEVICE_ADDRESS), 2);
        long[] destAddresses = bundle.getLongArray(KEY_DEST_ADDRESS_LIST);
        List<UwbAddress> destAddressList = new ArrayList<>();
        if (destAddresses != null) {
            for (long address : destAddresses) {
                destAddressList.add(longToUwbAddress(address, 2));
            }
        }
        return new Builder()
                .setChannelNumber(bundle.getInt(KEY_CHANNEL_NUMBER))
                .setNumberOfControlee(bundle.getInt(KEY_NUMBER_OF_CONTROLEES))
                .setDeviceAddress(deviceAddress)
                .setDestAddressList(destAddressList)
                .setSlotDurationRstu(bundle.getInt(KEY_SLOT_DURATION_RSTU))
                .setStsIndex(bundle.getInt(KEY_STS_INDEX))
                .setFcsType(bundle.getInt(KEY_FCS_TYPE))
                .setDeviceRole(bundle.getInt(KEY_DEVICE_ROLE))
                .setRframeConfig(bundle.getInt(KEY_RFRAME_CONFIG))
                .setPreambleCodeIndex(bundle.getInt(KEY_PREAMBLE_CODE_INDEX))
                .setSfdId(bundle.getInt(KEY_SFD_ID))
                .setPsduDataRate(bundle.getInt(KEY_PSDU_DATA_RATE))
                .setPreambleDuration(bundle.getInt(KEY_PREAMBLE_DURATION))
                .setPrfMode(bundle.getInt(KEY_PRF_MODE))
                .setStsSegmentCount(bundle.getInt(KEY_STS_SEGMENT_COUNT))
                .setNumberOfPackets(bundle.getInt(KEY_NUMBER_OF_PACKETS))
                .setTgap(bundle.getInt(KEY_T_GAP))
                .setTstart(bundle.getInt(KEY_T_START))
                .setTwin(bundle.getInt(KEY_T_WIN))
                .setRandomizePsdu(bundle.getInt(KEY_RANDOMIZE_PSDU))
                .setPhrRangingBit(bundle.getInt(KEY_PHR_RANGING_BIT))
                .setRmarkerTxStart(bundle.getInt(KEY_RMARKER_TX_START))
                .setRmarkerRxStart(bundle.getInt(KEY_RMARKER_RX_START))
                .setStsIndexAutoIncr(bundle.getInt(KEY_STS_INDEX_AUTO_INCR))
                .setStsDetectBitmap(bundle.getInt(KEY_STS_DETECT_BITMAP_EN))
                .build();
    }

    public int getSessionId() {
        return mSessionId;
    }

    @SessionType
    public int getSessionType() {
        return mSessionType;
    }

    @UwbChannel
    public int getChannelNumber() {
        return mUwbChannel;
    }

    public int getNoOfControlee() {
        return mNoOfControlee;
    }

    public UwbAddress getDeviceAddress() {
        return mDeviceAddress;
    }

    public List<UwbAddress> getDestAddressList() {
        return mDestAddressList != null ? Collections.unmodifiableList(mDestAddressList) : null;
    }

    public int getSlotDurationRstu() {
        return mSlotDurationRstu;
    }

    public int getStsIndex() {
        return mStsIndex;
    }

    @MacFcsType
    public int getFcsType() {
        return mFcsType;
    }

    @RangingDeviceRole
    public int getDeviceRole() {
        return mDeviceRole;
    }

    @RframeConfig
    public int getRframeConfig() {
        return mRframeConfig;
    }

    @UwbPreambleCodeIndex
    public int getPreambleCodeIndex() {
        return mPreambleCodeIndex;
    }

    @SfdIdValue
    public int getSfdId() {
        return mSfdId;
    }

    @PsduDataRate
    public int getPsduDataRate() {
        return mPsduDataRate;
    }

    @PreambleDuration
    public int getPreambleDuration() {
        return mPreambleDuration;
    }

    @PrfMode
    public int getPrfMode() {
        return mPrfMode;
    }

    @StsSegmentCountValue
    public int getStsSegmentCount() {
        return mStsSegmentCount;
    }

    public int getNumberOfPackets() {
        return mNoOfPackets;
    }

    public int getTgap() {
        return mTgap;
    }

    public int getTstart() {
        return mTstart;
    }

    public int getTwin() {
        return mTwin;
    }

    @RandomizePsdu
    public int getRandomizePsdu() {
        return mRandomizePsdu;
    }

    @PhrRangingBit
    public int getPhrRangingBit() {
        return mPhrRangingBit;
    }

    public int getRmarkerTxStart() {
        return mRmarkerTxStart;
    }

    public int getRmarkerRxStart() {
        return mRmarkerRxStart;
    }

    @StsIndexAutoIncr
    public int getStsIndexAutoIncr() {
        return mStsIndexAutoIncr;
    }

    @StsDetectBitmap
    public int getStsDetectBitmap() {
        return mStsDetectBitmap;
    }

    /** Builder */
    public static final class Builder {
        private int mSessionId = 0x00000000;

        @SessionType
        private int mSessionType = RfTestParams.SESSION_TYPE_RFTEST;

        /** UCI spec default: Channel 9, which is the only mandatory channel. */
        @UwbChannel
        private int mUwbChannel = UWB_CHANNEL_9;

        private int mNoOfControlee = MAX_NUMBER_OF_MEASUREMENTS_DEFAULT;

        private RequiredParam<UwbAddress> mDeviceAddress = new RequiredParam<UwbAddress>();

        private List<UwbAddress> mDestAddressList = null;

        /** UCI spec default: 2400 RSTU (2 ms). */
        private int mSlotDurationRstu = 2400;

        private int mStsIndex = 0;

        /** UCI spec default: CRC-16 */
        private @MacFcsType int mFcsType = MAC_FCS_TYPE_CRC_16;

        private @RangingDeviceRole int mDeviceRole;

        private @RframeConfig int mRframeConfig = RFRAME_CONFIG_SP3;

        /** UCI spec default 10 */
        private int mPreambleCodeIndex = 10;

        /** UCI spec default 2, BPRF */
        private @SfdIdValue int mSfdId = SFD_ID_VALUE_2;

        /** UCI spec default 6.81 Mbps */
        private @PsduDataRate int mPsduDataRate = PSDU_DATA_RATE_6M81;

        /** UCI spec default 64 symbols */
        private @PreambleDuration int mPreambleDuration = PREAMBLE_DURATION_T64_SYMBOLS;

        /** UCI spec default BPRF */
        private @PrfMode int mPrfMode = PRF_MODE_BPRF;

        /** UCI spec default 1 STS segment */
        private @StsSegmentCountValue int mStsSegmentCount = STS_SEGMENT_COUNT_VALUE_1;

        /** UCI spec default 1000 */
        private int mNoOfPackets = 1000;

        /** UCI spec default 2000 */
        private int mTgap = 2000;

        /** UCI spec default 450 */
        private int mTstart = 450;

        /** UCI spec default 750 */
        private int mTwin = 750;

        /** UCI spec default no randomization */
        private @RandomizePsdu int mRandomizePsdu = NO_RANDOMIZATION;

        /** UCI spec default disable */
        private @PhrRangingBit int mPhrRangingBit = DISABLE_PHR;

        private RequiredParam<Integer> mRmarkerTxStart = new RequiredParam<Integer>();

        private RequiredParam<Integer> mRmarkerRxStart = new RequiredParam<Integer>();

        /** UCI spec default no increment */
        private @StsIndexAutoIncr int mStsIndexAutoIncr = NO_AUTO_INCR;

        /** UCI spec default don't report bitmap */
        private @StsDetectBitmap int mStsDetectBitmap = NO_STS_DETECT_BITMAP;

        public Builder() {
        }

        public Builder(@NonNull Builder builder) {
            mSessionId = builder.mSessionId;
            mSessionType = builder.mSessionType;
            mUwbChannel = builder.mUwbChannel;
            mNoOfControlee = builder.mNoOfControlee;
            mDeviceAddress.set(builder.mDeviceAddress.get());
            mDestAddressList = builder.mDestAddressList;
            mSlotDurationRstu = builder.mSlotDurationRstu;
            mStsIndex = builder.mStsIndex;
            mFcsType = builder.mFcsType;
            mDeviceRole = builder.mDeviceRole;
            mRframeConfig = builder.mRframeConfig;
            mPreambleCodeIndex = builder.mPreambleCodeIndex;
            mSfdId = builder.mSfdId;
            mPsduDataRate = builder.mPsduDataRate;
            mPreambleDuration = builder.mPreambleDuration;
            mPrfMode = builder.mPrfMode;
            mStsSegmentCount = builder.mStsSegmentCount;
            mNoOfPackets = builder.mNoOfPackets;
            mTgap = builder.mTgap;
            mTstart = builder.mTstart;
            mTwin = builder.mTwin;
            mRandomizePsdu = builder.mRandomizePsdu;
            mPhrRangingBit = builder.mPhrRangingBit;
            mRmarkerTxStart.set(builder.mRmarkerTxStart.get());
            mRmarkerRxStart.set(builder.mRmarkerRxStart.get());
            mStsIndexAutoIncr = builder.mStsIndexAutoIncr;
            mStsDetectBitmap = builder.mStsDetectBitmap;
        }

        public Builder(@NonNull RfTestOpenSessionParams params) {
            mSessionId = params.mSessionId;
            mSessionType = params.mSessionType;
            mUwbChannel = params.mUwbChannel;
            mNoOfControlee = params.mNoOfControlee;
            mDeviceAddress.set(params.mDeviceAddress);
            mDestAddressList = params.mDestAddressList;
            mSlotDurationRstu = params.mSlotDurationRstu;
            mStsIndex = params.mStsIndex;
            mFcsType = params.mFcsType;
            mDeviceRole = params.mDeviceRole;
            mRframeConfig = params.mRframeConfig;
            mPreambleCodeIndex = params.mPreambleCodeIndex;
            mSfdId = params.mSfdId;
            mPsduDataRate = params.mPsduDataRate;
            mPreambleDuration = params.mPreambleDuration;
            mPrfMode = params.mPrfMode;
            mStsSegmentCount = params.mStsSegmentCount;
            mNoOfPackets = params.mNoOfPackets;
            mTgap = params.mTgap;
            mTstart = params.mTstart;
            mTwin = params.mTwin;
            mRandomizePsdu = params.mRandomizePsdu;
            mPhrRangingBit = params.mPhrRangingBit;
            mRmarkerTxStart.set(params.mRmarkerTxStart);
            mRmarkerRxStart.set(params.mRmarkerRxStart);
            mStsIndexAutoIncr = params.mStsIndexAutoIncr;
            mStsDetectBitmap = params.mStsDetectBitmap;
        }

        public Builder setChannelNumber(@UwbChannel int channelNumber) {
            mUwbChannel = channelNumber;
            return this;
        }

        public Builder setNumberOfControlee(int noOfControlee) {
            mNoOfControlee = noOfControlee;
            return this;
        }

        public Builder setDeviceAddress(UwbAddress deviceAddress) {
            mDeviceAddress.set(deviceAddress);
            return this;
        }

        public Builder setDestAddressList(List<UwbAddress> destAddressList) {
            mDestAddressList = destAddressList;
            return this;
        }

        public Builder setSlotDurationRstu(int slotDurationRstu) {
            mSlotDurationRstu = slotDurationRstu;
            return this;
        }

        public Builder setStsIndex(int stsIndex) {
            mStsIndex = stsIndex;
            return this;
        }

        public Builder setFcsType(@MacFcsType int fcsType) {
            mFcsType = fcsType;
            return this;
        }

        public Builder setDeviceRole(@RangingDeviceRole int deviceRole) {
            mDeviceRole = deviceRole;
            return this;
        }

        public Builder setRframeConfig(@RframeConfig int rframeConfig) {
            mRframeConfig = rframeConfig;
            return this;
        }

        public Builder setPreambleCodeIndex(@UwbPreambleCodeIndex int preambleCodeIndex) {
            mPreambleCodeIndex = preambleCodeIndex;
            return this;
        }

        public Builder setSfdId(@SfdIdValue int sfdId) {
            mSfdId = sfdId;
            return this;
        }

        public Builder setPsduDataRate(@PsduDataRate int psduDataRate) {
            mPsduDataRate = psduDataRate;
            return this;
        }

        public Builder setPreambleDuration(@PreambleDuration int preambleDuration) {
            mPreambleDuration = preambleDuration;
            return this;
        }

        public Builder setPrfMode(@PrfMode int prfMode) {
            mPrfMode = prfMode;
            return this;
        }

        public Builder setStsSegmentCount(
                @StsSegmentCountValue int stsSegmentCount) {
            mStsSegmentCount = stsSegmentCount;
            return this;
        }

        public Builder setNumberOfPackets(int noOfPackets) {
            mNoOfPackets = noOfPackets;
            return this;
        }

        public Builder setTgap(int tGap) {
            mTgap = tGap;
            return this;
        }

        public Builder setTstart(int tStart) {
            mTstart = tStart;
            return this;
        }

        public Builder setTwin(int tWin) {
            mTwin = tWin;
            return this;
        }

        public Builder setRandomizePsdu(@RandomizePsdu int randomizePsdu) {
            mRandomizePsdu = randomizePsdu;
            return this;
        }

        public Builder setPhrRangingBit(@PhrRangingBit int phrRangingBit) {
            mPhrRangingBit = phrRangingBit;
            return this;
        }

        public Builder setRmarkerTxStart(int rmarkerTxStart) {
            mRmarkerTxStart.set(rmarkerTxStart);
            return this;
        }

        public Builder setRmarkerRxStart(int rmarkerRxStart) {
            mRmarkerRxStart.set(rmarkerRxStart);
            return this;
        }

        public Builder setStsIndexAutoIncr(@StsIndexAutoIncr int stsIndexAutoIncr) {
            mStsIndexAutoIncr = stsIndexAutoIncr;
            return this;
        }

        public Builder setStsDetectBitmap(@StsDetectBitmap int stsDetectBitmap) {
            mStsDetectBitmap = stsDetectBitmap;
            return this;
        }

        /** Build {@link RfTestOpenSessionParams} */
        public RfTestOpenSessionParams build() {
            return new RfTestOpenSessionParams(
                    mSessionId,
                    mSessionType,
                    mUwbChannel,
                    mNoOfControlee,
                    mDeviceAddress.get(),
                    mDestAddressList,
                    mSlotDurationRstu,
                    mStsIndex,
                    mFcsType,
                    mDeviceRole,
                    mRframeConfig,
                    mPreambleCodeIndex,
                    mSfdId,
                    mPsduDataRate,
                    mPreambleDuration,
                    mPrfMode,
                    mStsSegmentCount,
                    mNoOfPackets,
                    mTgap,
                    mTstart,
                    mTwin,
                    mRandomizePsdu,
                    mPhrRangingBit,
                    mRmarkerTxStart.get(),
                    mRmarkerRxStart.get(),
                    mStsIndexAutoIncr,
                    mStsDetectBitmap
            );
        }
    }
}
