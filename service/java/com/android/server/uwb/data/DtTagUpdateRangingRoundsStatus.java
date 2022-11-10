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

package com.android.server.uwb.data;

import java.util.Arrays;

public class DtTagUpdateRangingRoundsStatus {
    private final int mStatus;
    private final int mNoOfActiveRangingRounds;
    private final byte[] mRangingRoundIndexes;

    public DtTagUpdateRangingRoundsStatus(int status, int noOfActiveRangingRounds,
            byte[] rangingRoundIndexes) {
        mStatus = status;
        mNoOfActiveRangingRounds = noOfActiveRangingRounds;
        mRangingRoundIndexes = rangingRoundIndexes;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getNoOfActiveRangingRounds() {
        return mNoOfActiveRangingRounds;
    }

    public byte[] getRangingRoundIndexes() {
        return mRangingRoundIndexes;
    }

    @Override
    public String toString() {
        return "DtTagActiveRoundsStatus { "
                + "Status = " + mStatus
                + ", NoOfActiveRangingRound s=" + mNoOfActiveRangingRounds
                + ", RangingRoundIndexes = " + Arrays.toString(mRangingRoundIndexes)
                + '}';
    }
}
