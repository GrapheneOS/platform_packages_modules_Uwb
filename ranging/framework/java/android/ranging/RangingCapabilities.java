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

package android.ranging;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.uwb.UwbRangingCapabilities;

import com.android.ranging.flags.Flags;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the capabilities and availability of various ranging technologies.
 *
 * <p>The {@code RangingCapabilities} class encapsulates the status of different ranging
 * technologies. It also allows querying the availability of other ranging technologies through a
 * mapping of technology identifiers to availability statuses.</p>
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public final class RangingCapabilities implements Parcelable {

    @Nullable
    private final UwbRangingCapabilities mUwbRangingCapabilities;

    @NonNull
    private HashMap<Integer, Integer>
            mTechnologyAvailabilityMap;

    private RangingCapabilities(Builder builder) {
        mUwbRangingCapabilities = builder.mUwbRangingCapabilities;
        mTechnologyAvailabilityMap = builder.mTechnologyAvailabilityMap;
    }

    private RangingCapabilities(Parcel in) {
        mUwbRangingCapabilities = in.readParcelable(UwbRangingCapabilities.class.getClassLoader(),
                UwbRangingCapabilities.class);
        int size = in.readInt();
        mTechnologyAvailabilityMap = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            int key = in.readInt();
            int value = in.readInt();
            mTechnologyAvailabilityMap.put(key, value);
        }
    }

    @NonNull
    public static final Creator<RangingCapabilities> CREATOR =
            new Creator<RangingCapabilities>() {
                @Override
                public RangingCapabilities createFromParcel(Parcel in) {
                    return new RangingCapabilities(in);
                }

                @Override
                public RangingCapabilities[] newArray(int size) {
                    return new RangingCapabilities[size];
                }
            };

    /**
     * Gets a map containing the availability of various ranging technologies.
     *
     * <p>The map uses technology identifiers as keys and their respective availability
     * statuses as values.</p>
     *
     * @return a {@link Map} containing technology availability statuses.
     */
    @NonNull
    public Map<Integer, Integer> getTechnologyAvailabilityMap() {
        return mTechnologyAvailabilityMap;
    }

    /**
     * Gets the UWB ranging capabilities.
     *
     * @return a {@link UwbRangingCapabilities} object or {@code null} if not available.
     */
    @Nullable
    public UwbRangingCapabilities getUwbCapabilities() {
        return mUwbRangingCapabilities;
    }

    /**
     * @hide
     */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@androidx.annotation.NonNull Parcel dest, int flags) {
        dest.writeParcelable(mUwbRangingCapabilities, flags);
        dest.writeInt(mTechnologyAvailabilityMap.size()); // Write map size
        for (Map.Entry<Integer, Integer> entry : mTechnologyAvailabilityMap.entrySet()) {
            dest.writeInt(entry.getKey()); // Write the key
            dest.writeInt(entry.getValue()); // Write the value
        }
    }

    /**
     * Builder for {@link UwbRangingCapabilities}
     *
     * @hide
     */
    public static class Builder {
        private UwbRangingCapabilities mUwbRangingCapabilities = null;
        private final HashMap<Integer, Integer> mTechnologyAvailabilityMap = new HashMap<>();

        public Builder setUwbRangingCapabilities(UwbRangingCapabilities uwbRangingCapabilities) {
            mUwbRangingCapabilities = uwbRangingCapabilities;
            return this;
        }

        public Builder addAvailability(int technology, int availability) {
            mTechnologyAvailabilityMap.put(technology, availability);
            return this;
        }

        public RangingCapabilities build() {
            return new RangingCapabilities(this);
        }
    }
}
