/*
 * Copyright 2024 The Android Open Source Project
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

package android.ranging.cs;

import static android.ranging.params.RawRangingDevice.UPDATE_RATE_NORMAL;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;
import android.ranging.cs.CsRangingCapabilities.SecurityLevel;
import android.ranging.params.RawRangingDevice;
import android.ranging.params.RawRangingDevice.RangingUpdateRate;

import com.android.ranging.flags.Flags;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * CsRangingParams encapsulates the parameters required for a bluetooth channel sounding ranging
 * session.
 *
 * @hide
 */
@FlaggedApi(Flags.FLAG_RANGING_CS_ENABLED)
public final class CsRangingParams implements Parcelable {

    /**
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    SIGHT_TYPE_UNKNOWN,
                    SIGHT_TYPE_LINE_OF_SIGHT,
                    SIGHT_TYPE_NON_LINE_OF_SIGHT,
            })
    @interface SightType {
    }

    /**
     * Sight type is unknown.
     */
    public static final int SIGHT_TYPE_UNKNOWN = 0;
    /**
     * Remote device is in line of sight.
     */
    public static final int SIGHT_TYPE_LINE_OF_SIGHT = 1;
    /**
     * Remote device is not in line of sight.
     */
    public static final int SIGHT_TYPE_NON_LINE_OF_SIGHT = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            value = {
                    LOCATION_TYPE_UNKNOWN,
                    LOCATION_TYPE_INDOOR,
                    LOCATION_TYPE_OUTDOOR,
            })
    @interface LocationType {
    }

    /**
     * Location type is unknown.
     */
    public static final int LOCATION_TYPE_UNKNOWN = 0;
    /**
     * Location of the use-case is indoor.
     */
    public static final int LOCATION_TYPE_INDOOR = 1;
    /**
     * Location of the use-case is outdoor.
     */
    public static final int LOCATION_TYPE_OUTDOOR = 2;

    private final byte[] mPeerBluetoothAddress;
    @RawRangingDevice.RangingUpdateRate
    private final int mRangingUpdateRate;
    @SightType
    private final int mSightType;
    @LocationType
    private final int mLocationType;
    @CsRangingCapabilities.SecurityLevel
    private final int mSecurityLevel;

    private CsRangingParams(Builder builder) {
        mPeerBluetoothAddress = builder.mPeerBluetoothAddress;
        mRangingUpdateRate = builder.mRangingUpdateRate;
        mSightType = builder.mSightType;
        mLocationType = builder.mLocationType;
        mSecurityLevel = builder.mSecurityLevel;
    }

    private CsRangingParams(Parcel in) {
        mPeerBluetoothAddress = in.readBlob();
        mRangingUpdateRate = in.readInt();
        mSightType = in.readInt();
        mLocationType = in.readInt();
        mSecurityLevel = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mPeerBluetoothAddress);
        dest.writeInt(mRangingUpdateRate);
        dest.writeInt(mSightType);
        dest.writeInt(mLocationType);
        dest.writeInt(mSecurityLevel);
    }

    @NonNull
    public static final Creator<CsRangingParams> CREATOR = new Creator<CsRangingParams>() {
        @Override
        public CsRangingParams createFromParcel(Parcel in) {
            return new CsRangingParams(in);
        }

        @Override
        public CsRangingParams[] newArray(int size) {
            return new CsRangingParams[size];
        }
    };


    /**
     * Returns the Bluetooth address of the peer device.
     *
     * @return byte array representing the Bluetooth address.
     */
    public byte[] getPeerBluetoothAddress() {
        return mPeerBluetoothAddress;
    }

    /**
     * Returns the ranging update rate.
     *
     * @return ranging update rate.
     */
    @RangingUpdateRate
    public int getRangingUpdateRate() {
        return mRangingUpdateRate;
    }

    /**
     * Returns the sight type for this ranging session.
     *
     * @return the sight type
     */
    @SightType
    public int getSightType() {
        return mSightType;
    }

    /**
     * Returns the location type for the ranging session.
     *
     * @return the location type.
     */
    @LocationType
    public int getLocationType() {
        return mLocationType;
    }

    /**
     * Returns the security level for the ranging session.
     *
     * @return the security level
     */
    @SecurityLevel
    public int getSecurityLevel() {
        return mSecurityLevel;
    }

    /**
     * Builder class to create {@link CsRangingParams} instances.
     */
    public static final class Builder {
        private byte[] mPeerBluetoothAddress;
        @RangingUpdateRate
        private int mRangingUpdateRate = UPDATE_RATE_NORMAL;
        @SightType
        private int mSightType = SIGHT_TYPE_UNKNOWN;
        @LocationType
        private int mLocationType = LOCATION_TYPE_UNKNOWN;
        @SecurityLevel
        private int mSecurityLevel = CsRangingCapabilities.CS_SECURITY_LEVEL_ONE;

        /**
         * Constructs a new {@link Builder} for creating a channel sounding ranging session.
         *
         * <p>Valid Bluetooth hardware addresses must be upper case, in big endian byte order, and
         * in a format such as "00:11:22:33:AA:BB". The helper
         * {@link android.bluetooth.BluetoothAdapter#checkBluetoothAddress} is available to validate
         * a Bluetooth address.
         *
         * @param peerBluetoothAddress The address of the peer device must be non-null.
         * @throws IllegalArgumentException if {@code peerBluetoothAddress} is null.
         */
        public Builder(@NonNull byte[] peerBluetoothAddress) {
            Objects.requireNonNull(peerBluetoothAddress);
            mPeerBluetoothAddress = peerBluetoothAddress;
        }

        /**
         * Sets the update rate for the CS ranging session.
         * <p>Defaults to {@link RangingUpdateRate#UPDATE_RATE_NORMAL}
         *
         * @param updateRate the reporting frequency.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setRangingUpdateRate(@RawRangingDevice.RangingUpdateRate int updateRate) {
            mRangingUpdateRate = updateRate;
            return this;
        }

        /**
         * Sets the sight type for the ranging session.
         * <p>Defaults to {@link #SIGHT_TYPE_UNKNOWN}
         *
         * @param sightType the sight type.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setSightType(@SightType int sightType) {
            mSightType = sightType;
            return this;
        }

        /**
         * Sets the location type for the ranging session.
         * <p>Defaults to {@link #LOCATION_TYPE_UNKNOWN}
         *
         * @param locationType the location type.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setLocationType(@LocationType int locationType) {
            mLocationType = locationType;
            return this;
        }

        /**
         * Sets the security level for the ranging session.
         * <p>Defaults to {@link CsRangingCapabilities#CS_SECURITY_LEVEL_ONE}
         *
         * @param securityLevel the security level.
         * @return this {@link Builder} instance.
         */
        @NonNull
        public Builder setSecurityLevel(@CsRangingCapabilities.SecurityLevel int securityLevel) {
            mSecurityLevel = securityLevel;
            return this;
        }

        /**
         * Builds and returns a {@link CsRangingParams} instance.
         *
         * @return a new {@link CsRangingParams}.
         */
        @NonNull
        public CsRangingParams build() {
            return new CsRangingParams(this);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

}

