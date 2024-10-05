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
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

import java.util.UUID;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class RangingDevice implements Parcelable {
    private final UUID mId;

    // Constructor that takes UUID
    public RangingDevice(UUID id) {
        mId = id;
    }

    // Constructor used when recreating object from Parcel
    protected RangingDevice(Parcel in) {
        // Read the UUID as two long values (UUID is stored as two longs)
        long mostSigBits = in.readLong();
        long leastSigBits = in.readLong();
        mId = new UUID(mostSigBits, leastSigBits);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // Write the UUID as two long values
        dest.writeLong(mId.getMostSignificantBits());
        dest.writeLong(mId.getLeastSignificantBits());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    // Parcelable.Creator to recreate the object from Parcel
    public static final Creator<RangingDevice> CREATOR = new Creator<RangingDevice>() {
        @Override
        public RangingDevice createFromParcel(Parcel in) {
            return new RangingDevice(in);
        }

        @Override
        public RangingDevice[] newArray(int size) {
            return new RangingDevice[size];
        }
    };

    // Getter for the UUID
    public UUID getId() {
        return mId;
    }
}
