/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;

import javax.annotation.Nonnegative;

/** Configurable range duration limits for a UWB session. */
public final class UwbRangeLimitsConfig {

    public static final int UWB_PARAM_DISABLED = 0;
    public static final int UWB_PARAM_UPPER_LIMIT = 65535;

    private final int mRangeMaxNumberOfMeasurements;
    private final int mRangeMaxRangingRoundRetries;

    private UwbRangeLimitsConfig(
            @Nonnegative int rangeMaxNumberOfMeasurements,
            @Nonnegative int rangeMaxRangingRoundRetries) {
        checkArgument(
                rangeMaxNumberOfMeasurements <= UWB_PARAM_UPPER_LIMIT,
                "Uwb Range Max Number of Measurements %s should be less than %s",
                rangeMaxNumberOfMeasurements,
                UWB_PARAM_UPPER_LIMIT);
        checkArgument(
                rangeMaxRangingRoundRetries <= UWB_PARAM_UPPER_LIMIT,
                "UWB Range Max Ranging Round Retries should be less than and %s",
                UWB_PARAM_UPPER_LIMIT);

        this.mRangeMaxNumberOfMeasurements = rangeMaxNumberOfMeasurements;
        this.mRangeMaxRangingRoundRetries = rangeMaxRangingRoundRetries;
    }

    public int getRangeMaxNumberOfMeasurements() {
        return mRangeMaxNumberOfMeasurements;
    }

    public int getRangeMaxRangingRoundRetries() {
        return mRangeMaxRangingRoundRetries;
    }

    /** Creates a new instance of {@link UwbRangeLimitsConfig}. */
    public static class Builder {
        private int mRangeMaxNumberOfMeasurements = UWB_PARAM_DISABLED;
        private int mRangeMaxRangingRoundRetries = UWB_PARAM_DISABLED;

        public Builder setRangeMaxNumberOfMeasurements(int rangeMaxNumberOfMeasurements) {
            mRangeMaxNumberOfMeasurements = rangeMaxNumberOfMeasurements;
            return this;
        }

        public Builder setRangeMaxRangingRoundRetries(int rangeMaxRangingRoundRetries) {
            mRangeMaxRangingRoundRetries = rangeMaxRangingRoundRetries;
            return this;
        }

        public UwbRangeLimitsConfig build() {
            return new UwbRangeLimitsConfig(mRangeMaxNumberOfMeasurements,
                    mRangeMaxRangingRoundRetries);
        }
    }

    @Override
    public String toString() {
        return "UwbRangeLimitsConfig{"
                + "rangeMaxNumberOfMeasurements="
                + mRangeMaxNumberOfMeasurements
                + ", rangeMaxRangingRoundRetries="
                + mRangeMaxRangingRoundRetries
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UwbRangeLimitsConfig)) {
            return false;
        }
        UwbRangeLimitsConfig that = (UwbRangeLimitsConfig) o;
        return mRangeMaxNumberOfMeasurements == that.mRangeMaxNumberOfMeasurements
                && mRangeMaxRangingRoundRetries == that.mRangeMaxRangingRoundRetries;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRangeMaxNumberOfMeasurements, mRangeMaxRangingRoundRetries);
    }
}
