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
import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.ranging.flags.Flags;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single measurement from a Downlink Time Difference of Arrival (DL-TDoA) ranging
 * session.
 *
 * <p>For more details, refer to the FIRA 2.0 specification.
 * @see <a href="https://groups.firaconsortium.org/wg/Technical/document/4590">FiRa_UCI_Technical_Specification_v2_0_0.docx</a>
 *
 * <p>This class encapsulates the data received in a DL-TDoA Message (DTM), including timestamps,
 * location data, and other metadata necessary for position calculation.
 *
 * <h3>MessageControl Field</h3>
 * <p>The {@code MessageControl} field is an integer bitmask that packs several pieces of metadata
 * into a single field to save space. Each group of bits has a specific meaning as follows:
 * <table border="1">
 *     <thead>
 *         <tr>
 *             <th>Bits</th>
 *             <th>Description</th>
 *             <th>Getter Method</th>
 *         </tr>
 *     </thead>
 *     <tbody>
 *         <tr>
 *             <td>0</td>
 *             <td>TX Timestamp in Common Time Base</td>
 *             <td>{@link #isTxTimestampInCommonTimeBase()}</td>
 *         </tr>
 *         <tr>
 *             <td>1-2</td>
 *             <td>TX Timestamp Length</td>
 *             <td>{@link #getTxTimestampLength()}</td>
 *         </tr>
 *         <tr>
 *             <td>3-4</td>
 *             <td>RX Timestamp Length</td>
 *             <td>{@link #getRxTimestampLength()}</td>
 *         </tr>
 *         <tr>
 *             <td>5-6</td>
 *             <td>Anchor Location Presence and Type</td>
 *             <td>{@link #getAnchorLocationPresenceType()}</td>
 *         </tr>
 *         <tr>
 *             <td>7-10</td>
 *             <td>Number of Active Ranging Round Indexes</td>
 *             <td>{@link #getNumActiveRangingRoundIndexes()}</td>
 *         </tr>
 *     </tbody>
 * </table>
 */
@FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
public final class DlTdoaMeasurement implements Parcelable {

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        MESSAGE_TYPE_POLL_DTM,
        MESSAGE_TYPE_RESPONSE_DTM,
        MESSAGE_TYPE_FINAL_DTM
    })
    public @interface DlTdoaMessageType {}
    /** DL-TDoA Message Type: Poll DTM */
    public static final int MESSAGE_TYPE_POLL_DTM = 0x00;
    /** DL-TDoA Message Type: Response DTM */
    public static final int MESSAGE_TYPE_RESPONSE_DTM = 0x01;
    /** DL-TDoA Message Type: Final DTM */
    public static final int MESSAGE_TYPE_FINAL_DTM = 0x02;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        NLOS_LOS,
        NLOS_NLOS,
        NLOS_UNABLE_TO_DETERMINE
    })
    public @interface NLoSType {}
    /** Non-Line-of-Sight Indicator: Line-of-Sight */
    public static final int NLOS_LOS = 0x00;
    /** Non-Line-of-Sight Indicator: Non-Line-of-Sight */
    public static final int NLOS_NLOS = 0x01;
    /** Non-Line-of-Sight Indicator: Unable to determine */
    public static final int NLOS_UNABLE_TO_DETERMINE = 0xFF;

    private final @DlTdoaMessageType int mMessageType;
    /**
     * A bitmask containing control flags and metadata for the message.
     * See the class-level Javadoc for a detailed breakdown of the bit fields.
     */
    private final int mMessageControl;
    private final int mBlockIndex;
    private final int mRoundIndex;
    private final @NLoSType int mNLoS;
    private final long mTxTimestamp;
    private final long mRxTimestamp;
    private final float mAnchorCfo;
    private final float mCfo;
    private final long mInitiatorReplyTime;
    private final long mResponderReplyTime;
    private final int mInitiatorResponderTof;
    private final @Nullable byte[] mAnchorLocationData;
    private final @Nullable List<Integer> mActiveRangingRoundIndexes;
    private final @Nullable Float mRssi;
    private final @Nullable Float mAzimuth;
    private final @Nullable Float mElevation;
    private final @Nullable Integer mAzimuthFom;
    private final @Nullable Integer mElevationFom;

    private DlTdoaMeasurement(Builder builder) {
        mMessageType = builder.mMessageType;
        mMessageControl = builder.mMessageControl;
        mBlockIndex = builder.mBlockIndex;
        mRoundIndex = builder.mRoundIndex;
        mNLoS = builder.mNLoS;
        mTxTimestamp = builder.mTxTimestamp;
        mRxTimestamp = builder.mRxTimestamp;
        mAnchorCfo = builder.mAnchorCfo;
        mCfo = builder.mCfo;
        mInitiatorReplyTime = builder.mInitiatorReplyTime;
        mResponderReplyTime = builder.mResponderReplyTime;
        mInitiatorResponderTof = builder.mInitiatorResponderTof;
        mAnchorLocationData = builder.mAnchorLocationData;
        mActiveRangingRoundIndexes = builder.mActiveRangingRoundIndexes;
        mRssi = builder.mRssi;
        mAzimuth = builder.mAzimuth;
        mElevation = builder.mElevation;
        mAzimuthFom = builder.mAzimuthFom;
        mElevationFom = builder.mElevationFom;
    }

    /**
     * Returns the message type of the received DL-TDoA Message (DTM).
     *
     * @return The message type, e.g., {@link #MESSAGE_TYPE_POLL_DTM}.
     */
    public @DlTdoaMessageType int getMessageType() {
        return mMessageType;
    }

    /**
     * Returns the raw MessageControl field.
     *
     * <p><b>Note:</b> This is an advanced-use field. The value is a bitmask
     * constructed according to the layout described in the {@link DlTdoaMeasurement}
     * class documentation.
     *
     * @return The integer bitmask representing message control flags.
     */
    public int getMessageControl() {
        return mMessageControl;
    }

    /**
     * Returns true if TX timestamp is based on a common time base.
     * <p>This value is derived from bit 0 of the {@code MessageControl} field.
     *
     * @return true if the TX timestamp is in the common time base, false otherwise.
     */
    public boolean isTxTimestampInCommonTimeBase() {
        return (mMessageControl & 0x1) != 0;
    }

    /**
     * Returns the length of the TX timestamp field.
     * <p>This value is derived from bits 1-2 of the {@code MessageControl} field.
     *
     * @return The length of the TX timestamp field.
     */
    public @TxTimestampLength int getTxTimestampLength() {
        return (mMessageControl >> 1) & 0x3;
    }

    /**
     * Returns the length of the RX timestamp field.
     * <p>This value is derived from bits 3-4 of the {@code MessageControl} field.
     *
     * @return The length of the RX timestamp field.
     */
    public @RxTimestampLength int getRxTimestampLength() {
        return (mMessageControl >> 3) & 0x3;
    }

    /**
     * Returns the presence and type of the anchor location data.
     * <p>This value is derived from bits 5-6 of the {@code MessageControl} field.
     *
     * <p><b>Example usage:</b>
     * <pre>{@code
     * void processMeasurement(DlTdoaMeasurement measurement) {
     *     switch (measurement.getAnchorLocationPresenceType()) {
     *         case DlTdoaMeasurement.ANCHOR_LOCATION_WGS84:
     *             Wgs84Location wgs84 = measurement.getAnchorLocationWgs84();
     *             if (wgs84 != null) {
     *                 // Process WGS-84 coordinates
     *             }
     *             break;
     *         case DlTdoaMeasurement.ANCHOR_LOCATION_RELATIVE:
     *             RelativeLocation relative = measurement.getAnchorLocationRelative();
     *             if (relative != null) {
     *                 // Process relative coordinates
     *             }
     *             break;
     *         case DlTdoaMeasurement.ANCHOR_LOCATION_NOT_PRESENT:
     *         default:
     *             // Location is not available
     *             break;
     *     }
     * }
     * }</pre>
     *
     * @return The location presence type, e.g., {@link #ANCHOR_LOCATION_WGS84}.
     */
    public @AnchorLocationPresenceType int getAnchorLocationPresenceType() {
        return (mMessageControl >> 5) & 0x3;
    }

    /**
     * Returns the number of active ranging round indexes present in the message.
     * <p>This value is derived from bits 7-10 of the {@code MessageControl} field.
     */
    @IntRange(from = 0)
    public int getNumActiveRangingRoundIndexes() {
        return (mMessageControl >> 7) & 0xF;
    }

    /**
     * Returns the block index of the current ranging block.
     * <p>
     * This identifies the specific ranging block during which the measurement occurred.
     *
     * @return The block index as an integer.
     */
    @IntRange(from = 0)
    public int getBlockIndex() {
        return mBlockIndex;
    }

    /**
     * Returns the round index of the current ranging round within a block.
     * <p>
     * This specifies the active ranging round.
     *
     * @return The round index as an integer.
     */
    @IntRange(from = 0)
    public int getRoundIndex() {
        return mRoundIndex;
    }

    /**
     * Returns an indicator of the Line of Sight (LoS) status.
     *
     * @return The NLoS status, e.g., {@link #NLOS_LOS}.
     */
    public @NLoSType int getNlos() {
        return mNLoS;
    }

    /**
     * Returns the transmission timestamp of the message reported by the DT-Anchor.
     * <p>
     * This timestamp is in Ranging Ticks (~15.65 ps) and is used for TDoA estimations.
     *
     * @return The TX timestamp as a long.
     */
    public long getTxTimestamp() {
        return mTxTimestamp;
    }

    /**
     * Returns the local reception timestamp of the message by the DT-Tag (Android device).
     * <p>
     * This timestamp is measured locally in Ranging Ticks (~15.65 ps).
     *
     * @return The RX timestamp as a long.
     */
    public long getRxTimestamp() {
        return mRxTimestamp;
    }

    /**
     * Returns the Clock Frequency Offset (CFO) of a Responder DT-Anchor.
     * <p>
     * This offset is relative to the Initiator DT-Anchor of the ranging round, in ppm units.
     *
     * @return The Anchor CFO as a float.
     */
    public float getAnchorCfo() {
        return mAnchorCfo;
    }

    /**
     * Returns the locally measured Clock Frequency Offset (CFO) by the DT-Tag.
     * <p>
     * This offset is relative to the DT-Anchor that sent the message, in ppm units.
     *
     * @return The CFO as a float.
     */
    public float getCfo() {
        return mCfo;
    }

    /**
     * Returns the time difference measured by the Initiator DT-Anchor.
     * <p>
     * It's between the RX timestamp of a Responder's DTM and the TX timestamp of the Final DTM,
     * in Ranging Ticks.
     *
     * @return The Initiator Reply Time as a long.
     */
    public long getInitiatorReplyTime() {
        return mInitiatorReplyTime;
    }

    /**
     * Returns the time difference measured at the Responder DT-Anchor.
     * <p>
     * It's between the RX timestamp of the Poll DTM and the TX timestamp of its Response DTM,
     * in Ranging Ticks.
     *
     * @return The Responder Reply Time as a long.
     */
    public long getResponderReplyTime() {
        return mResponderReplyTime;
    }

    /**
     * Returns the Time of Flight (ToF) between the Responder and Initiator DT-Anchors.
     * <p>
     * This measurement is for the specific ranging round, in Ranging Ticks (~15.65 ps).
     *
     * @return The Initiator-Responder ToF as an integer.
     */
    @IntRange(from = 0)
    public int getInitiatorResponderTof() {
        return mInitiatorResponderTof;
    }

    /**
     * Returns the anchor location in WGS-84 coordinates, if available.
     *
     * <p>This method parses the raw anchor location data when the presence type is
     * {@link #ANCHOR_LOCATION_WGS84}.
     *
     * <p>The WGS-84 coordinates are parsed from a 12-octet field as follows:
     * <ul>
     *     <li>Latitude: 33 bits, signed fixed-point. Scaled by 2<sup>-26</sup> degrees.
     *     <li>Longitude: 33 bits, signed fixed-point. Scaled by 2<sup>-26</sup> degrees.
     *     <li>Altitude: 30 bits, signed fixed-point. Scaled by 2<sup>-16</sup> meters.
     * </ul>
     *
     * @return a {@link Wgs84Location} object, or {@code null} if the location is not available in
     * this format.
     */
    @Nullable
    public Wgs84Location getAnchorLocationWgs84() {
        if (getAnchorLocationPresenceType() != ANCHOR_LOCATION_WGS84
                || mAnchorLocationData == null || mAnchorLocationData.length != 12) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(mAnchorLocationData)
                .order(ByteOrder.LITTLE_ENDIAN);
        long part1 = bb.getLong();
        int part2 = bb.getInt();

        long latRaw = (part1 & 0x1FFFFFFFFL);
        if ((latRaw & (1L << 32)) != 0) {
            latRaw |= ~((1L << 33) - 1); // Sign extension
        }
        double latitude = latRaw / (double) (1L << 26);

        long lonRaw = (part1 >>> 33) | ((part2 & 0x3L) << 31);
        if ((lonRaw & (1L << 32)) != 0) {
            lonRaw |= ~((1L << 33) - 1); // Sign extension
        }
        double longitude = lonRaw / (double) (1L << 26);

        long altRaw = (part2 & 0xFFFFFFFFL) >>> 2;
        if ((altRaw & (1L << 29)) != 0) {
            altRaw |= ~((1L << 30) - 1); // Sign extension
        }
        double altitude = altRaw / (double) (1 << 16);

        return new Wgs84Location(latitude, longitude, altitude);
    }

    /**
     * Returns the anchor location in a relative coordinate system, if available.
     *
     * <p>This method parses the raw anchor location data when the presence type is
     * {@link #ANCHOR_LOCATION_RELATIVE}.
     *
     * <p>The relative coordinates are parsed from a 10-octet field as x-y-z coordinates in
     * millimeters (mm):
     * <ul>
     *     <li>X coordinate: 28 bits, signed integer.
     *     <li>Y coordinate: 28 bits, signed integer.
     *     <li>Z coordinate: 24 bits, signed integer.
     * </ul>
     *
     * @return a {@link RelativeLocation} object, or {@code null} if the location is not available
     * in this format.
     */
    @Nullable
    public RelativeLocation getAnchorLocationRelative() {
        if (getAnchorLocationPresenceType() != ANCHOR_LOCATION_RELATIVE
                || mAnchorLocationData == null || mAnchorLocationData.length != 10) {
            return null;
        }

        ByteBuffer bb = ByteBuffer.wrap(mAnchorLocationData)
                .order(ByteOrder.LITTLE_ENDIAN);
        long l = bb.getLong();
        short s = bb.getShort();

        int x = (int) (l & 0x0FFFFFFF);
        x = (x << 4) >> 4; // Sign extension from 28 to 32 bits

        int y = (int) ((l >>> 28) & 0x0FFFFFFF);
        y = (y << 4) >> 4; // Sign extension from 28 to 32 bits

        int z = (int) (((l >>> 56) & 0xFF) | ((s & 0xFFFF) << 8));
        z = (z << 8) >> 8; // Sign extension from 24 to 32 bits

        return new RelativeLocation(x, y, z);
    }

    /**
     * Returns a list of active ranging round indexes.
     * <p>
     * These are the rounds in which the DT-Anchor associated with this measurement result is
     * present.
     *
     * @return The list of active ranging round indexes.
     */
    @NonNull
    public List<Integer> getActiveRangingRoundIndexes() {
        return mActiveRangingRoundIndexes == null ? List.of() : mActiveRangingRoundIndexes;
    }

    /**
     * Returns the Received Signal Strength Indicator (RSSI) in dBm.
     *
     * @return The RSSI value, or {@code Float.NaN} if not available.
     */
    public float getRssi() {
        return mRssi == null ? Float.NaN : mRssi;
    }

    /**
     * Returns the estimated azimuth angle of arrival in degrees.
     *
     * @return The azimuth angle, or {@code Float.NaN} if not available.
     */
    public float getAzimuth() {
        return mAzimuth == null ? Float.NaN : mAzimuth;
    }

    /**
     * Returns the estimated elevation angle of arrival in degrees.
     *
     * @return The elevation angle, or {@code Float.NaN} if not available.
     */
    public float getElevation() {
        return mElevation == null ? Float.NaN : mElevation;
    }

    /**
     * Returns the Figure of Merit (FOM) for the azimuth measurement.
     *
     * @return The azimuth FOM (0-100), or {@code -1} if not available.
     */
    @IntRange(from = -1, to = 100)
    public int getAzimuthFom() {
        return mAzimuthFom == null ? -1 : mAzimuthFom;
    }

    /**
     * Returns the Figure of Merit (FOM) for the elevation measurement.
     *
     * @return The elevation FOM (0-100), or {@code -1} if not available.
     */
    @IntRange(from = -1, to = 100)
    public int getElevationFom() {
        return mElevationFom == null ? -1 : mElevationFom;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mMessageType);
        dest.writeInt(mMessageControl);
        dest.writeInt(mBlockIndex);
        dest.writeInt(mRoundIndex);
        dest.writeInt(mNLoS);
        dest.writeLong(mTxTimestamp);
        dest.writeLong(mRxTimestamp);
        dest.writeFloat(mAnchorCfo);
        dest.writeFloat(mCfo);
        dest.writeLong(mInitiatorReplyTime);
        dest.writeLong(mResponderReplyTime);
        dest.writeInt(mInitiatorResponderTof);
        dest.writeByteArray(mAnchorLocationData);
        dest.writeList(mActiveRangingRoundIndexes);
        dest.writeValue(mRssi);
        dest.writeValue(mAzimuth);
        dest.writeValue(mElevation);
        dest.writeValue(mAzimuthFom);
        dest.writeValue(mElevationFom);
    }

    private DlTdoaMeasurement(Parcel in) {
        mMessageType = in.readInt();
        mMessageControl = in.readInt();
        mBlockIndex = in.readInt();
        mRoundIndex = in.readInt();
        mNLoS = in.readInt();
        mTxTimestamp = in.readLong();
        mRxTimestamp = in.readLong();
        mAnchorCfo = in.readFloat();
        mCfo = in.readFloat();
        mInitiatorReplyTime = in.readLong();
        mResponderReplyTime = in.readLong();
        mInitiatorResponderTof = in.readInt();
        mAnchorLocationData = in.createByteArray();
        mActiveRangingRoundIndexes = in.readArrayList(Integer.class.getClassLoader());
        mRssi = (Float) in.readValue(Float.class.getClassLoader());
        mAzimuth = (Float) in.readValue(Float.class.getClassLoader());
        mElevation = (Float) in.readValue(Float.class.getClassLoader());
        mAzimuthFom = (Integer) in.readValue(Integer.class.getClassLoader());
        mElevationFom = (Integer) in.readValue(Integer.class.getClassLoader());
    }

    public static final @NonNull Creator<DlTdoaMeasurement> CREATOR =
            new Creator<DlTdoaMeasurement>() {
                @Override
                public DlTdoaMeasurement createFromParcel(Parcel in) {
                    return new DlTdoaMeasurement(in);
                }

                @Override
                public DlTdoaMeasurement[] newArray(int size) {
                    return new DlTdoaMeasurement[size];
                }
            };

    @Override
    public String toString() {
        return "DlTdoaMeasurement {"
                + " MessageType=" + mMessageType
                + ", MessageControl=" + mMessageControl
                + ", BlockIndex=" + mBlockIndex
                + ", RoundIndex=" + mRoundIndex
                + ", NLoS=" + mNLoS
                + ", TxTimestamp=" + mTxTimestamp
                + ", RxTimestamp=" + mRxTimestamp
                + ", AnchorCfo=" + mAnchorCfo
                + ", Cfo=" + mCfo
                + ", InitiatorReplyTime=" + mInitiatorReplyTime
                + ", ResponderReplyTime=" + mResponderReplyTime
                + ", InitiatorResponderTof=" + mInitiatorResponderTof
                + ", AnchorLocationData=" + java.util.Arrays.toString(mAnchorLocationData)
                + ", ActiveRangingRoundIndexes=" + mActiveRangingRoundIndexes
                + ", Rssi=" + mRssi
                + ", Azimuth=" + mAzimuth
                + ", Elevation=" + mElevation
                + ", AzimuthFom=" + mAzimuthFom
                + ", ElevationFom=" + mElevationFom
                + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DlTdoaMeasurement)) return false;
        DlTdoaMeasurement that = (DlTdoaMeasurement) o;
        return mMessageType == that.mMessageType
                && mMessageControl == that.mMessageControl
                && mBlockIndex == that.mBlockIndex
                && mRoundIndex == that.mRoundIndex
                && mNLoS == that.mNLoS
                && mTxTimestamp == that.mTxTimestamp
                && mRxTimestamp == that.mRxTimestamp
                && Float.compare(that.mAnchorCfo, mAnchorCfo) == 0
                && Float.compare(that.mCfo, mCfo) == 0
                && mInitiatorReplyTime == that.mInitiatorReplyTime
                && mResponderReplyTime == that.mResponderReplyTime
                && mInitiatorResponderTof == that.mInitiatorResponderTof
                && java.util.Arrays.equals(mAnchorLocationData, that.mAnchorLocationData)
                && Objects.equals(mActiveRangingRoundIndexes, that.mActiveRangingRoundIndexes)
                && Objects.equals(mRssi, that.mRssi)
                && Objects.equals(mAzimuth, that.mAzimuth)
                && Objects.equals(mElevation, that.mElevation)
                && Objects.equals(mAzimuthFom, that.mAzimuthFom)
                && Objects.equals(mElevationFom, that.mElevationFom);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mMessageType, mMessageControl, mBlockIndex, mRoundIndex, mNLoS,
                mTxTimestamp, mRxTimestamp, mAnchorCfo, mCfo, mInitiatorReplyTime,
                mResponderReplyTime, mInitiatorResponderTof, mActiveRangingRoundIndexes, mRssi,
                mAzimuth, mElevation, mAzimuthFom, mElevationFom);
        result = 31 * result + java.util.Arrays.hashCode(mAnchorLocationData);
        return result;
    }

    /**
     * Represents a location in the WGS-84 coordinate system.
     */
    @FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public static final class Wgs84Location implements Parcelable {
        private final double mLatitude;
        private final double mLongitude;
        private final double mAltitude;

        /** @hide */
        public Wgs84Location(double latitude, double longitude, double altitude) {
            mLatitude = latitude;
            mLongitude = longitude;
            mAltitude = altitude;
        }

        /**
         * Returns the latitude in degrees.
         */
        public double getLatitude() {
            return mLatitude;
        }

        /**
         * Returns the longitude in degrees.
         */
        public double getLongitude() {
            return mLongitude;
        }

        /**
         * Returns the altitude in meters.
         */
        public double getAltitude() {
            return mAltitude;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeDouble(mLatitude);
            dest.writeDouble(mLongitude);
            dest.writeDouble(mAltitude);
        }

        public static final @NonNull Creator<Wgs84Location> CREATOR =
                new Creator<Wgs84Location>() {
                    @Override
                    public Wgs84Location createFromParcel(Parcel in) {
                        return new Wgs84Location(in.readDouble(), in.readDouble(), in.readDouble());
                    }

                    @Override
                    public Wgs84Location[] newArray(int size) {
                        return new Wgs84Location[size];
                    }
                };
        @Override
        public String toString() {
            return "Wgs84Location {"
                    + " Latitude=" + mLatitude
                    + ", Longitude=" + mLongitude
                    + ", Altitude=" + mAltitude
                    + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Wgs84Location)) return false;
            Wgs84Location that = (Wgs84Location) o;
            return Double.compare(that.mLatitude, mLatitude) == 0
                    && Double.compare(that.mLongitude, mLongitude) == 0
                    && Double.compare(that.mAltitude, mAltitude) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mLatitude, mLongitude, mAltitude);
        }
    }

    /**
     * Represents a location in a relative coordinate system.
     */
    @FlaggedApi(Flags.FLAG_RANGING_STACK_UPDATES_26_Q_2)
    public static final class RelativeLocation implements Parcelable {
        private final int mX;
        private final int mY;
        private final int mZ;

        /** @hide */
        public RelativeLocation(int x, int y, int z) {
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
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mX);
            dest.writeInt(mY);
            dest.writeInt(mZ);
        }

        public static final @NonNull Creator<RelativeLocation> CREATOR =
                new Creator<RelativeLocation>() {
                    @Override
                    public RelativeLocation createFromParcel(Parcel in) {
                        return new RelativeLocation(in.readInt(), in.readInt(), in.readInt());
                    }

                    @Override
                    public RelativeLocation[] newArray(int size) {
                        return new RelativeLocation[size];
                    }
                };

        @Override
        public String toString() {
            return "RelativeLocation {"
                    + " X=" + mX
                    + ", Y=" + mY
                    + ", Z=" + mZ
                    + " }";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RelativeLocation)) return false;
            RelativeLocation that = (RelativeLocation) o;
            return mX == that.mX
                    && mY == that.mY
                    && mZ == that.mZ;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mX, mY, mZ);
        }
    }

    /**
     * Builder for {@link DlTdoaMeasurement}.
     *
     * @hide
     */
    public static final class Builder {
        private int mMessageType;
        private int mMessageControl;
        private int mBlockIndex;
        private int mRoundIndex;
        private int mNLoS;
        private long mTxTimestamp;
        private long mRxTimestamp;
        private float mAnchorCfo;
        private float mCfo;
        private long mInitiatorReplyTime;
        private long mResponderReplyTime;
        private int mInitiatorResponderTof;
        private byte[] mAnchorLocationData;
        private List<Integer> mActiveRangingRoundIndexes;
        private Float mRssi;
        private Float mAzimuth;
        private Float mElevation;
        private Integer mAzimuthFom;
        private Integer mElevationFom;

        @NonNull
        public Builder setMessageType(@DlTdoaMessageType int messageType) {
            mMessageType = messageType;
            return this;
        }

        /**
         * Sets the MessageControl field.
         *
         * <p><b>Note:</b> This is an advanced-use field. The value should be a bitmask
         * constructed according to the layout described in the {@link DlTdoaMeasurement}
         * class documentation. Incorrectly setting this field can lead to erroneous
         * measurement data.
         *
         * @param messageControl The integer bitmask representing message control flags.
         */
        @NonNull
        public Builder setMessageControl(int messageControl) {
            mMessageControl = messageControl;
            return this;
        }

        @NonNull
        public Builder setBlockIndex(int blockIndex) {
            mBlockIndex = blockIndex;
            return this;
        }

        @NonNull
        public Builder setRoundIndex(int roundIndex) {
            mRoundIndex = roundIndex;
            return this;
        }

        @NonNull
        public Builder setNlos(@NLoSType int nlos) {
            mNLoS = nlos;
            return this;
        }

        @NonNull
        public Builder setTxTimestamp(long txTimestamp) {
            mTxTimestamp = txTimestamp;
            return this;
        }

        @NonNull
        public Builder setRxTimestamp(long rxTimestamp) {
            mRxTimestamp = rxTimestamp;
            return this;
        }

        @NonNull
        public Builder setAnchorCfo(float anchorCfo) {
            mAnchorCfo = anchorCfo;
            return this;
        }

        @NonNull
        public Builder setCfo(float cfo) {
            mCfo = cfo;
            return this;
        }

        @NonNull
        public Builder setInitiatorReplyTime(long initiatorReplyTime) {
            mInitiatorReplyTime = initiatorReplyTime;
            return this;
        }

        @NonNull
        public Builder setResponderReplyTime(long responderReplyTime) {
            mResponderReplyTime = responderReplyTime;
            return this;
        }

        @NonNull
        public Builder setInitiatorResponderTof(int initiatorResponderTof) {
            mInitiatorResponderTof = initiatorResponderTof;
            return this;
        }

        @NonNull
        public Builder setAnchorLocationData(@Nullable byte[] anchorLocationData) {
            mAnchorLocationData = anchorLocationData;
            return this;
        }

        @NonNull
        public Builder setActiveRangingRoundIndexes(
                @Nullable List<Integer> activeRangingRoundIndexes) {
            mActiveRangingRoundIndexes = activeRangingRoundIndexes;
            return this;
        }

        @NonNull
        public Builder setRssi(float rssi) {
            mRssi = rssi;
            return this;
        }

        @NonNull
        public Builder setAzimuth(float azimuth) {
            mAzimuth = azimuth;
            return this;
        }

        @NonNull
        public Builder setElevation(float elevation) {
            mElevation = elevation;
            return this;
        }

        @NonNull
        public Builder setAzimuthFom(int azimuthFom) {
            mAzimuthFom = azimuthFom;
            return this;
        }

        @NonNull
        public Builder setElevationFom(int elevationFom) {
            mElevationFom = elevationFom;
            return this;
        }

        @NonNull
        public DlTdoaMeasurement build() {
            return new DlTdoaMeasurement(this);
        }
    }



    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        TIMESTAMP_LEN_40,
        TIMESTAMP_LEN_64,
    })
    public @interface TxTimestampLength {}

    /** TX Timestamp Length: 40 bits */
    public static final int TIMESTAMP_LEN_40 = 0;
    /** TX Timestamp Length: 64 bits */
    public static final int TIMESTAMP_LEN_64 = 1;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        TIMESTAMP_LEN_40,
        TIMESTAMP_LEN_64,
    })
    public @interface RxTimestampLength {}

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        ANCHOR_LOCATION_NOT_PRESENT,
        ANCHOR_LOCATION_WGS84,
        ANCHOR_LOCATION_RELATIVE,
    })
    public @interface AnchorLocationPresenceType {}

    /** Anchor Location: Not Present */
    public static final int ANCHOR_LOCATION_NOT_PRESENT = 0;
    /** Anchor Location: WGS-84 Coordinates */
    public static final int ANCHOR_LOCATION_WGS84 = 1;
    /** Anchor Location: Relative Coordinates */
    public static final int ANCHOR_LOCATION_RELATIVE = 2;
}