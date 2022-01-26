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

package com.google.uwb.support.multichip;

import android.os.PersistableBundle;

/**
 * Defines parameters from return value for  {@link android.uwb.UwbManager#getChipInfos()}.
 */
public final class ChipInfoParams {
    private static final String KEY_CHIP_ID = "KEY_CHIP_ID";
    private static final String UNKNOWN_CHIP_ID = "UNKNOWN_CHIP_ID";

    private final String mChipId;

    private ChipInfoParams(String chipId) {
        mChipId = chipId;
    }

    /** Returns a String identifier of the chip. */
    public String getChipId() {
        return mChipId;
    }

    /** Returns a {@link PersistableBundle} representation of the object. */
    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(KEY_CHIP_ID, mChipId);
        return bundle;
    }

    /** Creates a new {@link ChipInfoParams} from a {@link PersistableBundle}. */
    public static ChipInfoParams fromBundle(PersistableBundle bundle) {
        String chipId = bundle.getString(KEY_CHIP_ID, UNKNOWN_CHIP_ID);
        return new ChipInfoParams(chipId);
    }

    /** Creates and returns a {@link Builder}. */
    public static Builder createBuilder() {
        return new Builder();
    }

    /**
     * A Class for building an object representing the return type of
     * {@link android.uwb.UwbManager#getChipInfos()}.
     */
    public static class Builder {
        String mChipId = UNKNOWN_CHIP_ID;

        /** Sets String identifier of chip */
        public Builder setChipId(String chipId) {
            mChipId = chipId;
            return this;
        }

        /**
         * Builds an object representing the return type of
         * {@link android.uwb.UwbManager#getChipInfos()}.
         */
        public ChipInfoParams build()  {
            return new ChipInfoParams(mChipId);
        }
    }
}
