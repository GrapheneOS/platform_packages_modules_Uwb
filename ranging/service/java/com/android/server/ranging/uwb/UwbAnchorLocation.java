/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.ranging.uwb;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Range;

import com.android.server.ranging.common.BitChunk;
import com.android.server.ranging.common.FixedPointNumber;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an anchor location.
 */
public final class UwbAnchorLocation {
    public static final int COORDINATE_WGS84 = 0x00;
    public static final int COORDINATE_RELATIVE = 0x01;
    public static final int COORDINATE_WGS84_PLUS_Z_ELEMENT = 0x02;
    public static final int COORDINATE_RELATIVE_PLUS_Z_ELEMENT = 0x03;
    public static final int COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED = 0x04;
    public static final int COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED_PLUS_Z_ELEMENT = 0x05;
    public static final int COORDINATE_UNKNOWN = Integer.MAX_VALUE;

    /**
     * Represents a location in the WGS-84 coordinate system.
     */
    public static final class UwbWgs84Location {
        private static int WGS84_BYTE_COUNT = 12;
        private static int Q9_24_BIT_COUNT = 33;
        private static int Q9_24_INTEGER_BIT_COUNT = 8;
        private static int Q9_24_FRACTIONAL_BIT_COUNT = 24;
        private static int Q9_21_BIT_COUNT = 30;
        private static int Q9_21_INTEGER_BIT_COUNT = 8;
        private static int Q9_21_FRACTIONAL_BIT_COUNT = 21;

        private final FixedPointNumber mLatitude;
        private final FixedPointNumber mLongitude;
        private final FixedPointNumber mAltitude;

        private static UwbWgs84Location fromBytes(@NonNull byte[] bytes, int offset) {
            Objects.requireNonNull(bytes);

            if (offset < 0 || bytes.length < offset + WGS84_BYTE_COUNT) {
                throw new IllegalArgumentException(
                        "Invalid byte length: " + bytes.length + ", offset: " + offset
                        + ", required bytes: " + WGS84_BYTE_COUNT);
            }
            int bitOffset = 0;

            // Q9.24 format: 1 bit sign, 8 bits integer, 24 bits fractional.
            long rawLatitude =
                    BitChunk.getBitsFromBytes(bytes, offset, bitOffset, Q9_24_BIT_COUNT, true);
            offset += (bitOffset + Q9_24_BIT_COUNT) / 8;
            bitOffset = (bitOffset + Q9_24_BIT_COUNT) % 8;

            // Q9.24 format: 1 bit sign, 8 bits integer, 24 bits fractional.
            long rawLongitude =
                    BitChunk.getBitsFromBytes(bytes, offset, bitOffset, Q9_24_BIT_COUNT, true);
            offset += (bitOffset + Q9_24_BIT_COUNT) / 8;
            bitOffset = (bitOffset + Q9_24_BIT_COUNT) % 8;

            // Q9.21 format: 1 bit sign, 8 bits integer, 21 bits fractional.
            long rawAltitude =
                    BitChunk.getBitsFromBytes(bytes, offset, bitOffset, Q9_21_BIT_COUNT, true);
            offset += (bitOffset + Q9_21_BIT_COUNT) / 8;
            bitOffset = (bitOffset + Q9_21_BIT_COUNT) % 8;

            return new UwbWgs84Location(rawLatitude, rawLongitude, rawAltitude);
        }

        private UwbWgs84Location(long rawLatitude, long rawLongitude, long rawAltitude) {
            // Q9.24 format: 1 bit sign, 8 bits integer, 24 bits fractional.
            mLatitude = FixedPointNumber.create(
                    true, Q9_24_INTEGER_BIT_COUNT, Q9_24_FRACTIONAL_BIT_COUNT, rawLatitude);
            // Q9.24 format: 1 bit sign, 8 bits integer, 24 bits fractional.
            mLongitude = FixedPointNumber.create(
                    true, Q9_24_INTEGER_BIT_COUNT, Q9_24_FRACTIONAL_BIT_COUNT, rawLongitude);
            // Q9.21 format: 1 bit sign, 8 bits integer, 21 bits fractional.
            mAltitude = FixedPointNumber.create(
                    true, Q9_21_INTEGER_BIT_COUNT, Q9_21_FRACTIONAL_BIT_COUNT, rawAltitude);
        }

        /**
         * Returns the latitude in degrees.
         *
         * <p>The ranging of this value is [-90, 90] with about 6.66 mm resolution.
         */
        public double getLatitude() {
            return mLatitude.getDoubleValue();
        }

        /**
         * Returns the longitude in degrees.
         *
         * <p>The ranging of this value is [-180, 180] with about 6.66 mm resolution.
         */
        public double getLongitude() {
            return mLongitude.getDoubleValue();
        }

        /**
         * Returns the altitude in meters.
         *
         * <p>The unit is kilometer and the resolution of this value is about 0.476 mm.
         */
        public double getAltitude() {
            return mAltitude.getDoubleValue();
        }

        @Override
        public String toString() {
            return "UwbWgs84Location {"
                    + " Latitude=" + mLatitude
                    + ", Longitude=" + mLongitude
                    + ", Altitude=" + mAltitude
                    + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UwbWgs84Location)) return false;
            UwbWgs84Location other = (UwbWgs84Location) o;
            return Objects.equals(mLatitude, other.mLatitude)
                    && Objects.equals(mLongitude, other.mLongitude)
                    && Objects.equals(mAltitude, other.mAltitude);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mLatitude, mLongitude, mAltitude);
        }
    }

    /**
     * Represents a location in a relative coordinate system.
     */
    public static final class UwbRelativeLocation {
        private static int XYZ_BYTE_COUNT = 10;
        private static int X_BIT_COUNT = 28;
        private static int Y_BIT_COUNT = 28;
        private static int Z_BIT_COUNT = 24;

        private final int mX;
        private final int mY;
        private final int mZ;

        @NonNull
        private static UwbRelativeLocation fromBytes(@NonNull byte[] bytes, int offset) {
            Objects.requireNonNull(bytes);

            if (offset < 0 || bytes.length < offset + XYZ_BYTE_COUNT) {
                throw new IllegalArgumentException(
                        "Invalid byte length: " + bytes.length + ", offset: " + offset
                        + ", required bytes: " + XYZ_BYTE_COUNT);
            }
            int bitOffset = 0;

            // 1 bit sign, 27 bits integer
            int x = (int) BitChunk.getBitsFromBytes(bytes, offset, bitOffset, X_BIT_COUNT, true);
            offset += (bitOffset + X_BIT_COUNT) / 8;
            bitOffset = (bitOffset + X_BIT_COUNT) % 8;

            // 1 bit sign, 27 bits integer
            int y = (int) BitChunk.getBitsFromBytes(bytes, offset, bitOffset, Y_BIT_COUNT, true);
            offset += (bitOffset + Y_BIT_COUNT) / 8;
            bitOffset = (bitOffset + Y_BIT_COUNT) % 8;

            // 1 bit sign, 23 bits integer
            int z = (int) BitChunk.getBitsFromBytes(bytes, offset, bitOffset, Z_BIT_COUNT, true);
            offset += (bitOffset + Z_BIT_COUNT) / 8;
            bitOffset = (bitOffset + Z_BIT_COUNT) % 8;

            return new UwbRelativeLocation(x, y, z);
        }

        private UwbRelativeLocation(int x, int y, int z) {
            mX = x;
            mY = y;
            mZ = z;
        }

        /**
         * Returns the X coordinate in millimeters.
         */
        public int getX() {
            return mX;
        }

        /**
         * Returns the Y coordinate in millimeters.
         */
        public int getY() {
            return mY;
        }

        /**
         * Returns the Z coordinate in millimeters.
         */
        public int getZ() {
            return mZ;
        }

        @Override
        public String toString() {
            return "UwbRelativeLocation {"
                    + " X=" + mX
                    + ", Y=" + mY
                    + ", Z=" + mZ
                    + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UwbRelativeLocation)) return false;
            UwbRelativeLocation other = (UwbRelativeLocation) o;
            return mX == other.mX
                    && mY == other.mY
                    && mZ == other.mZ;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mX, mY, mZ);
        }
    }

    /**
     * Represents a Z-element extension regarding the height of a anchor.
     */
    public static final class UwbZElementExtension {
        private static int Z_ELEMENT_EXTENSION_BYTE_COUNT = 6;
        private static int ANCHOR_FLOOR_NUMBER_BIT_COUNT = 14;
        private static int ANCHOR_FLOOR_NUMBER_INTEGER_BIT_COUNT = 9;
        private static int ANCHOR_FLOOR_NUMBER_FRACTIONAL_BIT_COUNT = 4;
        private static int EXPECTED_TO_MOVE_BIT_COUNT = 2;
        private static int ANCHOR_HEIGHT_ABOVE_FLOOR_BIT_COUNT = 24;
        private static int ANCHOR_HEIGHT_ABOVE_FLOOR_INTEGER_BIT_COUNT = 11;
        private static int ANCHOR_HEIGHT_ABOVE_FLOOR_FRACTIONAL_BIT_COUNT = 12;
        private static int ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_BIT_COUNT = 8;

        // 0b100...000 in 14 bits.
        private static final int ANCHOR_FLOOR_NUMBER_UNKNOWN_RAW_VALUE = -8192;
        // 0b111...111 in 14 bits. 8191/16 = 511.9375 floors.
        private static final int ANCHOR_FLOOR_NUMBER_MAX_RAW_VALUE = 8191;
        // 0b100...001 in 14 bits. -8191/16 = -511.9375 floors.
        private static final int ANCHOR_FLOOR_NUMBER_MIN_RAW_VALUE = -8191;

        // 0x800000 in 24 bits.
        private static final int ANCHOR_HEIGHT_ABOVE_FLOOR_UNKNOWN_RAW_VALUE = -8388608;
        // 0x7FFFFF in 24 bits. 8388607/4096 = (roughly) 2048 meters.
        private static final int ANCHOR_HEIGHT_ABOVE_FLOOR_MAX_RAW_VALUE = 8388607;
        // 0x800001 in 24 bits. -8388607/4096 = (roughly) -2048 meters.
        private static final int ANCHOR_HEIGHT_ABOVE_FLOOR_MIN_RAW_VALUE = -8388607;

        private static final int ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_UNKNOWN_VALUE = 0;
        private static final int ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_MAX_VALUE = 24;
        private static final int ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_MIN_VALUE = 1;

        private final FixedPointNumber mAnchorFloorNumber;
        private final int mExpectedToMove;
        private final FixedPointNumber mAnchorHeightAboveFloor;
        private final int mAnchorHeightAboveFloorUncertainty;

        @NonNull
        private static UwbZElementExtension fromBytes(@NonNull byte[] bytes, int offset) {
            Objects.requireNonNull(bytes);

            if (offset < 0 || bytes.length < offset + Z_ELEMENT_EXTENSION_BYTE_COUNT) {
                throw new IllegalArgumentException(
                        "Invalid byte length: " + bytes.length + ", offset: " + offset
                        + ", required bytes: " + Z_ELEMENT_EXTENSION_BYTE_COUNT);
            }
            int bitOffset = 0;

            // Q10.4 format: 1 bit sign, 9 bits integer part, 4 bits fractional part.
            long rawAnchorFloorNumber =
                    BitChunk.getBitsFromBytes(bytes, offset, bitOffset,
                            ANCHOR_FLOOR_NUMBER_BIT_COUNT, true);
            offset += (bitOffset + ANCHOR_FLOOR_NUMBER_BIT_COUNT) / 8;
            bitOffset = (bitOffset + ANCHOR_FLOOR_NUMBER_BIT_COUNT) % 8;

            // 2 bits, 0: no expected to move, 1: expected to move, 2: unknown.
            int expectedToMove = (int) BitChunk.getBitsFromBytes(bytes, offset, bitOffset,
                    EXPECTED_TO_MOVE_BIT_COUNT, false);
            offset += (bitOffset + EXPECTED_TO_MOVE_BIT_COUNT) / 8;
            bitOffset = (bitOffset + EXPECTED_TO_MOVE_BIT_COUNT) % 8;

            // Q12.12 format: 1 bit sign, 11 bits integer part, 12 bits fractional part.
            long  rawAnchorHeightAboveFloor =
                    BitChunk.getBitsFromBytes(bytes, offset, bitOffset,
                            ANCHOR_HEIGHT_ABOVE_FLOOR_BIT_COUNT, true);
            offset += (bitOffset + ANCHOR_HEIGHT_ABOVE_FLOOR_BIT_COUNT) / 8;
            bitOffset = (bitOffset + ANCHOR_HEIGHT_ABOVE_FLOOR_BIT_COUNT) % 8;

            // 8 bits
            int anchorHeightAboveFloorUncertainty = (int) BitChunk.getBitsFromBytes(bytes, offset,
                    bitOffset, ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_BIT_COUNT, false);
            offset += (bitOffset + ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_BIT_COUNT) / 8;
            bitOffset = (bitOffset + ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_BIT_COUNT) % 8;

            return new UwbZElementExtension(
                    rawAnchorFloorNumber,
                    expectedToMove,
                    rawAnchorHeightAboveFloor,
                    anchorHeightAboveFloorUncertainty);
        }

        private UwbZElementExtension(
                long rawAnchorFloorNumber, int expectedToMove, long rawAnchorHeightAboveFloor,
                int anchorHeightAboveFloorUncertainty) {
            mAnchorFloorNumber = FixedPointNumber.create(
                    true,
                    ANCHOR_FLOOR_NUMBER_INTEGER_BIT_COUNT,
                    ANCHOR_FLOOR_NUMBER_FRACTIONAL_BIT_COUNT,
                    rawAnchorFloorNumber);
            mExpectedToMove = expectedToMove;
            mAnchorHeightAboveFloor = FixedPointNumber.create(
                    true,
                    ANCHOR_HEIGHT_ABOVE_FLOOR_INTEGER_BIT_COUNT,
                    ANCHOR_HEIGHT_ABOVE_FLOOR_FRACTIONAL_BIT_COUNT,
                    rawAnchorHeightAboveFloor);
            mAnchorHeightAboveFloorUncertainty = anchorHeightAboveFloorUncertainty;
        }

        /**
         * Returns the anchor floor number.
         *
         * <p>The unit is floor and the resolution of this value is about 1/16 of a floor.
         * <p>The highest floor number which can be represented is 8191/16 = (roughly) 512 floors.
         * <p>The lowest floor number which can be represented is -8191/16 = (roughly) -512 floors.
         * <p>The anchor floor number is unknown if the value is Double.NaN.
         *
         * @return the anchor floor number or Double.NaN if the anchor floor number is unknown.
         */
        public double getAnchorFloorNumber() {
            return mAnchorFloorNumber.getRawValue() == ANCHOR_FLOOR_NUMBER_UNKNOWN_RAW_VALUE
                    ? Double.NaN : mAnchorFloorNumber.getDoubleValue();
        }

        /**
         * Returns whether the anchor floor number is out of range. If the value is out of range,
         * the anchor floor number field is set to the closest boundary value, and this method
         * returns true.
         */
        public boolean isAnchorFloorNumberOutOfRange() {
            long rawValue = mAnchorFloorNumber.getRawValue();
            return rawValue == ANCHOR_FLOOR_NUMBER_MAX_RAW_VALUE
                    || rawValue == ANCHOR_FLOOR_NUMBER_MIN_RAW_VALUE;
        }

        /**
         * Returns the expected to move.
         */
        public int getExpectedToMove() {
            return mExpectedToMove;
        }

        /**
         * Returns the anchor height above floor.
         *
         * <p>The unit is meter and the resolution of this value is about 1/4096 meter (0.24mm).
         * <p>The highest anchor height above floor which can be represented is 8388607/4096 =
         * (roughly) 2048 meters.
         * <p>The lowest anchor height above floor which can be represented is -8388607/4096 =
         * (roughly) -2048 meters.
         * <p>The anchor height above floor is unknown if the value is Double.NaN.
         *
         * @return the anchor height above floor or Double.NaN if the anchor height above floor is
         *         unknown.
         */
        public double getAnchorHeightAboveFloor() {
            return mAnchorHeightAboveFloor.getRawValue() ==
                    ANCHOR_HEIGHT_ABOVE_FLOOR_UNKNOWN_RAW_VALUE
                            ? Double.NaN : mAnchorHeightAboveFloor.getDoubleValue();
        }

        /**
         * Returns whether the anchor height above floor is out of range. If the value is out of
         * range, the anchor height above floor field is set to the closest boundary value, and this
         * method returns true.
         */
        public boolean isAnchorHeightAboveFloorOutOfRange() {
            long rawValue = mAnchorHeightAboveFloor.getRawValue();
            return rawValue == ANCHOR_HEIGHT_ABOVE_FLOOR_MAX_RAW_VALUE
                    || rawValue == ANCHOR_HEIGHT_ABOVE_FLOOR_MIN_RAW_VALUE;
        }

        /**
         * Returns the anchor height above floor uncertainty.
         *
         * <p>An anchor height above floor uncertainty value of 0 indicates an unknown anchor height
         * above floor. Values 25 or higher are reserved. A value from 1 to 24 indicates that the
         * actual anchor height above floor, a, is bounded according to:
         * <p>h − 2 ^ (11 - u) <= a <= h + 2 ^ (11 - u)
         * <p>where
         * <p>h is the value in units of 1/4096 m of the anchor height above floor field
         * <p>u is the value of the Anchors Height Above Floor Uncertainty field
         * <p>If the anchor height above floor field indicates an unknown anchor height above floor,
         * the anchor height above floor uncertainty field is set to 0.
         */
        public int getAnchorHeightAboveFloorUncertainty() {
            return mAnchorHeightAboveFloorUncertainty;
        }

        /**
         * Returns the anchor height above floor range in meters with uncertainty.
         *
         * <p>If the anchor height above floor is unknown or the uncertainty is invalid, this value
         * will be set to null.
         */
        @Nullable
        public Range<Double> getAnchorHeightAboveFloorRange() {
            double height = getAnchorHeightAboveFloor();
            if (Double.isNaN(height)) {
                return null;
            }
            int uncertainty = getAnchorHeightAboveFloorUncertainty();
            if (uncertainty == ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_UNKNOWN_VALUE
                    || uncertainty < ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_MIN_VALUE
                    || uncertainty > ANCHOR_HEIGHT_ABOVE_FLOOR_UNCERTAINTY_MAX_VALUE) {
                return null;
            }
            double margin = Math.pow(2, 11 - uncertainty);
            return new Range<>(height - margin, height + margin);
        }

        @Override
        public String toString() {
            return "UwbZElementExtension {"
                    + " AnchorFloorNumber=" + mAnchorFloorNumber
                    + ", ExpectedToMove=" + mExpectedToMove
                    + ", AnchorHeightAboveFloor=" + mAnchorHeightAboveFloor
                    + ", AnchorHeightAboveFloorUncertainty=" + mAnchorHeightAboveFloorUncertainty
                    + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UwbZElementExtension)) return false;
            UwbZElementExtension other = (UwbZElementExtension) o;
            return Objects.equals(mAnchorFloorNumber, other.mAnchorFloorNumber)
                    && mExpectedToMove == other.mExpectedToMove
                    && Objects.equals(mAnchorHeightAboveFloor, other.mAnchorHeightAboveFloor)
                    && mAnchorHeightAboveFloorUncertainty ==
                            other.mAnchorHeightAboveFloorUncertainty;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAnchorFloorNumber, mExpectedToMove, mAnchorHeightAboveFloor,
                    mAnchorHeightAboveFloorUncertainty);
        }
    }

    private final int mCoordinateType;
    private final byte[] mRawBytes;
    private final @Nullable UwbWgs84Location mWgs84Location;
    private final @Nullable UwbRelativeLocation mRelativeLocation;
    private final @Nullable UwbZElementExtension mZElementExtension;

    @NonNull
    public static UwbAnchorLocation fromBytesV1(int messageControl, @NonNull byte[] data) {
        Objects.requireNonNull(data);

        // [MessageControl] b6-b5: DT-Anchor location presence and type
        int locationPresenceAndType = (messageControl & 0b01100000) >> 5;
        return switch (locationPresenceAndType) {
            case 0b01 -> fromBytesWithType(COORDINATE_WGS84, data, 0);
            case 0b10 -> fromBytesWithType(COORDINATE_RELATIVE, data, 0);
            default -> fromBytesWithUnknownType(data);
        };
    }

    @NonNull
    public static UwbAnchorLocation fromBytesV2(@NonNull byte[] data) {
        Objects.requireNonNull(data);

        if (data.length < 2) {
            return fromBytesWithUnknownType(data);
        }
        // [AnchorLocation] Octet [0] = Type of the coordinate system
        int typeOuter = data[0];
        // [DL_TDOA_ANCHOR_LOCATION_V2] b0: Presence of DT-Anchor location
        boolean presentInner = (data[1] & 0b1) != 0;
        // [DL_TDOA_ANCHOR_LOCATION_V2] b3-b1: Type of coordinates system
        int typeInner = (data[1] & 0b1110) >> 1;
        if (!presentInner || typeOuter != typeInner) {
            return fromBytesWithUnknownType(data);
        }
        return fromBytesWithType(typeInner, data, 2);
    }

    @NonNull
    private static UwbAnchorLocation fromBytesWithType(int type, @NonNull byte[] data, int offset) {
        Objects.requireNonNull(data);

        UwbWgs84Location wgs84Location = null;
        UwbRelativeLocation relativeLocation = null;
        UwbZElementExtension zElementExtension = null;

        switch (type) {
            case COORDINATE_WGS84 -> {
                wgs84Location = UwbWgs84Location.fromBytes(data, offset);
            }
            case COORDINATE_RELATIVE, COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED -> {
                relativeLocation = UwbRelativeLocation.fromBytes(data, offset);
            }
            case COORDINATE_WGS84_PLUS_Z_ELEMENT -> {
                wgs84Location = UwbWgs84Location.fromBytes(data, offset);
                zElementExtension = UwbZElementExtension.fromBytes(
                        data, offset + UwbWgs84Location.WGS84_BYTE_COUNT);
            }
            case COORDINATE_RELATIVE_PLUS_Z_ELEMENT,
                    COORDINATE_RELATIVE_WITH_Z_GRAVITY_ALIGNED_PLUS_Z_ELEMENT -> {
                relativeLocation = UwbRelativeLocation.fromBytes(data, offset);
                zElementExtension = UwbZElementExtension.fromBytes(
                        data, offset + UwbRelativeLocation.XYZ_BYTE_COUNT);
            }
            default -> {
                return fromBytesWithUnknownType(data);
            }
        }
        return new UwbAnchorLocation(
                type, data, wgs84Location, relativeLocation, zElementExtension);
    }

    @NonNull
    public static UwbAnchorLocation fromBytesWithUnknownType(@NonNull byte[] rawBytes) {
        return new UwbAnchorLocation(COORDINATE_UNKNOWN, rawBytes, null, null, null);
    }

    private UwbAnchorLocation(
            int coordinateType,
            @NonNull byte[] rawBytes,
            @Nullable UwbWgs84Location wgs84Location,
            @Nullable UwbRelativeLocation relativeLocation,
            @Nullable UwbZElementExtension zElementExtension) {
        Objects.requireNonNull(rawBytes);

        mCoordinateType = coordinateType;
        mRawBytes = Arrays.copyOf(rawBytes, rawBytes.length);
        mWgs84Location = wgs84Location;
        mRelativeLocation = relativeLocation;
        mZElementExtension = zElementExtension;
    }

    public int getCoordinateType() {
        return mCoordinateType;
    }

    @NonNull
    public byte[] getRawBytes() {
        return mRawBytes;
    }

    @Nullable
    public UwbWgs84Location getWgs84Location() {
        return mWgs84Location;
    }

    @Nullable
    public UwbRelativeLocation getRelativeLocation() {
        return mRelativeLocation;
    }

    @Nullable
    public UwbZElementExtension getZElementExtension() {
        return mZElementExtension;
    }

    @Override
    public String toString() {
        return "UwbAnchorLocation {"
                + "CoordinateType=" + mCoordinateType
                + ", RawBytes=" + Arrays.toString(mRawBytes)
                + ", UwbWgs84Location=" + mWgs84Location
                + ", UwbRelativeLocation=" + mRelativeLocation
                + ", UwbZElementExtension=" + mZElementExtension
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UwbAnchorLocation)) return false;
        UwbAnchorLocation other = (UwbAnchorLocation) o;
        return mCoordinateType == other.mCoordinateType
                && Arrays.equals(mRawBytes, other.mRawBytes)
                && Objects.equals(mWgs84Location, other.mWgs84Location)
                && Objects.equals(mRelativeLocation, other.mRelativeLocation)
                && Objects.equals(mZElementExtension, other.mZElementExtension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCoordinateType, Arrays.hashCode(mRawBytes), mWgs84Location,
                mRelativeLocation, mZElementExtension);
    }
}
