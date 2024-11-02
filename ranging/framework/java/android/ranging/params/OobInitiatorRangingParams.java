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

package android.ranging.params;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class OobInitiatorRangingParams extends RangingParams implements Parcelable {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            SECURITY_LEVEL_BASIC,
            SECURITY_LEVEL_SECURE,
    })
    public @interface SecurityLevel {
    }

    public static final int SECURITY_LEVEL_BASIC = 0;
    public static final int SECURITY_LEVEL_SECURE = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            RANGING_MODE_AUTO,
            RANGING_MODE_HIGH_ACCURACY,
            RANGING_MODE_HIGH_ACCURACY_PREFERRED,
            RANGING_MODE_FUSED,
    })
    public @interface RangingMode {
    }

    public static final int RANGING_MODE_AUTO = 0;
    public static final int RANGING_MODE_HIGH_ACCURACY = 1;
    public static final int RANGING_MODE_HIGH_ACCURACY_PREFERRED = 2;
    public static final int RANGING_MODE_FUSED = 3;

    private final List<DeviceHandle> mDeviceHandles;

    private final RangingIntervalRange mRangingIntervalRange;

    @SecurityLevel
    private final int mSecurityLevel;

    @RangingMode
    private final int mRangingMode;

    private OobInitiatorRangingParams(Builder builder) {
        mRangingSessionType = RangingParams.RANGING_SESSION_OOB;
        mDeviceHandles = new ArrayList<>(builder.mDeviceHandles);
        mSecurityLevel = builder.mSecurityLevel;
        mRangingMode = builder.mRangingMode;
        mRangingIntervalRange = builder.mRangingIntervalRange;
    }

    protected OobInitiatorRangingParams(Parcel in) {
        mRangingSessionType = in.readInt();
        mDeviceHandles = in.createTypedArrayList(DeviceHandle.CREATOR);
        mSecurityLevel = in.readInt();
        mRangingMode = in.readInt();
        mRangingIntervalRange = in.readParcelable(RangingIntervalRange.class.getClassLoader(),
                RangingIntervalRange.class);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRangingSessionType);
        dest.writeTypedList(mDeviceHandles);
        dest.writeInt(mSecurityLevel);
        dest.writeInt(mRangingMode);
        dest.writeParcelable(mRangingIntervalRange, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<OobInitiatorRangingParams> CREATOR =
            new Creator<OobInitiatorRangingParams>() {
                @Override
                public OobInitiatorRangingParams createFromParcel(Parcel in) {
                    return new OobInitiatorRangingParams(in);
                }

                @Override
                public OobInitiatorRangingParams[] newArray(int size) {
                    return new OobInitiatorRangingParams[size];
                }
            };

    public List<DeviceHandle> getDeviceHandles() {
        return mDeviceHandles;
    }

    public RangingIntervalRange getRangingIntervalRange() {
        return mRangingIntervalRange;
    }

    public int getSecurityLevel() {
        return mSecurityLevel;
    }

    public int getRangingMode() {
        return mRangingMode;
    }

    public static final class Builder {
        private final List<DeviceHandle> mDeviceHandles = new ArrayList<>();
        private RangingIntervalRange mRangingIntervalRange;
        @SecurityLevel
        private int mSecurityLevel = SECURITY_LEVEL_BASIC; // Default value
        @RangingMode
        private int mRangingMode = RANGING_MODE_AUTO; // Default value

        public Builder addDeviceHandle(DeviceHandle deviceHandle) {
            mDeviceHandles.add(deviceHandle);
            return this;
        }

        public Builder setRangingIntervalRange(RangingIntervalRange intervalRange) {
            this.mRangingIntervalRange = intervalRange;
            return this;
        }

        public Builder setSecurityLevel(@SecurityLevel int securityLevel) {
            this.mSecurityLevel = securityLevel;
            return this;
        }

        public Builder setRangingMode(@RangingMode int rangingMode) {
            this.mRangingMode = rangingMode;
            return this;
        }

        public OobInitiatorRangingParams build() {
            return new OobInitiatorRangingParams(this);
        }
    }


}
