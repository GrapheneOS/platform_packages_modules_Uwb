/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.uwb.timesync;

import android.annotation.FlaggedApi;
import android.annotation.Hide;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Class for Framework-Vendor Time Sync interface version.
 *
 * @hide
 */
@Hide
@FlaggedApi(com.android.ranging.flags.Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class Version implements Parcelable {
    private int mMajor;
    private int mMinor;

    public Version(int major, int minor) {
        mMajor = major;
        mMinor = minor;
    }

    public int getMajor() {
        return mMajor;
    }

    public int getMinor() {
        return mMinor;
    }

    /**
     * Compares the value of a Version object against another.
     *
     * @param other Version object to be compared against.
     * @return -1 if other is less than this version, 0 if other is equal to this version, 1 if
     *     other is greater than this version.
     */
    public int compare(@NonNull Version other) {
        if (other.getMajor() > getMajor()) {
            return 1;
        }
        if (other.getMajor() < getMajor()) {
            return -1;
        }
        if (other.getMinor() > getMinor()) {
            return 1;
        }
        if (other.getMinor() < getMinor()) {
            return -1;
        }
        return 0;
    }

    /**
     * Compares the value of a Version object against another.
     *
     * @param other Version object to be compared against.
     * @return true if this version is equal-to other, otherwise false.
     */
    public boolean isEqualTo(@NonNull Version other) {
        return compare(other) == 0;
    }

    /**
     * Compares the value of a Version object against another.
     *
     * @param other Version object to be compared against.
     * @return true if this version is less-than other, otherwise false.
     */
    public boolean isLessThan(@NonNull Version other) {
        return compare(other) > 0;
    }

    /**
     * Compares the value of a Version object against another.
     *
     * @param other Version object to be compared against.
     * @return true if this version is greater-than other, otherwise false.
     */
    public boolean isGreaterThan(@NonNull Version other) {
        return compare(other) < 0;
    }
    //TODO (b/467707737) check if readByte is still correct
    @NonNull
    public static final Creator<Version> CREATOR =
            new Creator<Version>() {
                @Override
                public Version createFromParcel(Parcel in) {
                    return new Version(in.readInt(), in.readInt());
                }

                @Override
                public Version[] newArray(int size) {
                    return new Version[size];
                }
            };

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mMajor);
        dest.writeInt(mMinor);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return String.format("v%d.%d", mMajor, mMinor);
    }
}
