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


import static com.google.common.base.Preconditions.checkArgument;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_ENABLED)
public class DataNotificationConfig implements Parcelable {

    private final @NotificationConfig int mRangeDataNtfConfigType;
    private final int mProximityNearCm;
    private final int mProximityFarCm;

    protected DataNotificationConfig(Parcel in) {
        mRangeDataNtfConfigType = in.readInt();
        mProximityNearCm = in.readInt();
        mProximityFarCm = in.readInt();
    }

    public static final Creator<DataNotificationConfig> CREATOR =
            new Creator<DataNotificationConfig>() {
                @Override
                public DataNotificationConfig createFromParcel(Parcel in) {
                    return new DataNotificationConfig(in);
                }

                @Override
                public DataNotificationConfig[] newArray(int size) {
                    return new DataNotificationConfig[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mRangeDataNtfConfigType);
        dest.writeInt(mProximityNearCm);
        dest.writeInt(mProximityFarCm);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            NotificationConfig.DISABLE,
            NotificationConfig.ENABLE,
            NotificationConfig.PROXIMITY_LEVEL,
            NotificationConfig.PROXIMITY_EDGE,
    })
    @interface NotificationConfig {
        int DISABLE = 0;
        int ENABLE = 1;
        int PROXIMITY_LEVEL = 2;
        int PROXIMITY_EDGE = 3;
    }


    private DataNotificationConfig(Builder builder) {
        checkArgument(builder.mProximityNearCm <= builder.mProximityFarCm,
                "Ntf proximity near cannot be greater than Ntf proximity far");
        mRangeDataNtfConfigType = builder.mRangeDataConfigType;
        mProximityNearCm = builder.mProximityNearCm;
        mProximityFarCm = builder.mProximityFarCm;
    }

    public @NotificationConfig int getRangeDataNtfConfigType() {
        return mRangeDataNtfConfigType;
    }

    public int getProximityNearCm() {
        return mProximityNearCm;
    }

    public int getProximityFarCm() {
        return mProximityFarCm;
    }

    /** Builder for UwbRangeDataNtfConfig */
    public static class Builder {
        private @NotificationConfig int mRangeDataConfigType = NotificationConfig.ENABLE;
        private int mProximityNearCm = 0;
        private int mProximityFarCm = 20_000;

        public Builder setNotificationConfig(@NotificationConfig int config) {
            mRangeDataConfigType = config;
            return this;
        }

        public Builder setProximityNearCm(int proximityCm) {
            mProximityNearCm = proximityCm;
            return this;
        }

        public Builder setProximityFarCm(int proximityCm) {
            mProximityFarCm = proximityCm;
            return this;
        }

        public DataNotificationConfig build() {
            return new DataNotificationConfig(this);
        }
    }
}
