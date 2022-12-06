/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.uwb.secure.csml;

import static com.android.server.uwb.secure.iso7816.Iso7816Constants.EXTENDED_HEAD_LIST;
import static com.android.server.uwb.secure.iso7816.Iso7816Constants.TAG_LIST;

import androidx.annotation.NonNull;

import com.android.server.uwb.pm.ControlleeInfo;
import com.android.server.uwb.pm.SessionData;
import com.android.server.uwb.pm.UwbCapability;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.secure.iso7816.TlvParser;
import com.android.server.uwb.util.ObjectIdentifier;

import com.google.common.primitives.Bytes;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * Utils used by the FiRa CSML related modules.
 */
public final class CsmlUtil {
    private CsmlUtil() {}

    static final Tag OID_TAG = new Tag((byte) 0x06);
    private static final Tag TAG_LIST_TAG = new Tag(TAG_LIST);
    private static final Tag EXTENDED_HEAD_LIST_TAG = new Tag(EXTENDED_HEAD_LIST);
    // FiRa CSML 8.2.2.7.1.4
    private static final Tag TERMINATE_SESSION_DO_TAG = new Tag((byte) 0x80);
    private static final Tag TERMINATE_SESSION_TOP_DO_TAG = new Tag((byte) 0xBF, (byte) 0x79);

    public static final Tag UWB_CONFIG_AVAILABLE_TAG = new Tag((byte) 0x87);
    public static final Tag SESSION_DATA_DO_TAG = new Tag((byte) 0xBF, (byte) 0x78);
    public static final Tag SESSION_ID_TAG = new Tag((byte) 0x81);
    public static final Tag CONTROLEE_INFO_DO_TAG = new Tag((byte) 0xBF, (byte) 0x70);

    /**
     * Check if the data represents the session data is not available,
     * which defined as 'UWB config is unavailable in the CSML.
     * @param data the single TLV data.
     * @return true the session data is not available, false otherwise.
     */
    public static boolean isSessionDataNotAvailable(@NonNull byte[] data) {
        TlvDatum tlvDatum = TlvParser.parseOneTlv(data);
        if (tlvDatum != null
                && Objects.equals(tlvDatum.tag, CsmlUtil.UWB_CONFIG_AVAILABLE_TAG)
                && Objects.deepEquals(tlvDatum.value, new byte[]{(byte) 0x00})) {
            return true;
        }
        return false;
    }

    /**
     * check if the data contains the Session Data.
     */
    public static boolean isSessionDataDo(@NonNull byte[] data) {
        return isSpecifiedDo(SESSION_DATA_DO_TAG, data);
    }

    /**
     * Get the TLV for get session data command.
     */
    public static TlvDatum constructSessionDataGetDoTlv() {
        return constructGetDoTlv(SESSION_DATA_DO_TAG);
    }

    /**
     * check if the data contains the ControleeInfo DO.
     * @param data
     * @return
     */
    public static boolean isControleeInfoDo(@NonNull byte[] data) {
        return isSpecifiedDo(CONTROLEE_INFO_DO_TAG, data);
    }

    private static boolean isSpecifiedDo(@NonNull Tag specifiedTag, @NonNull byte[] data) {
        TlvDatum tlvDatum = TlvParser.parseOneTlv(data);
        if (tlvDatum != null && Objects.equals(tlvDatum.tag, specifiedTag)) {
            return  true;
        }

        return false;
    }

    /**
     * Encode the {@link ObjectIdentifier} as TLV format, which is used as the payload of TlvDatum
     * @param oid the ObjectIdentifier
     * @return The instance of TlvDatum.
     */
    @NonNull
    public static TlvDatum encodeObjectIdentifierAsTlv(@NonNull ObjectIdentifier oid) {
        return new TlvDatum(OID_TAG, oid.value);
    }

    /**
     * Construct the TLV payload for {@link getDoCommand} top Tag (not nested)
     * defined in ISO7816-4.
     */
    @NonNull
    public static TlvDatum constructGetDoTlv(@NonNull Tag tag) {
        return new TlvDatum(EXTENDED_HEAD_LIST_TAG, constructDeepestTagOfGetDoAllContent(tag));
    }

    /**
     * Get the TLV for terminate session command.
     */
    @NonNull
    public static TlvDatum constructTerminateSessionGetDoTlv() {
        // TODO: confirm the structure defined in CSML 8.2.2.7.1.4, which is not clear.
        byte[] value = constructDeepestTagOfGetDoAllContent(TERMINATE_SESSION_DO_TAG);
        return constructGetOrPutDoTlv(
                new TlvDatum(TERMINATE_SESSION_TOP_DO_TAG, value));
    }

    /**
     * Get the TLV for session id in the session data.
     */
    @NonNull
    public static TlvDatum constructGetSessionIdGetDoTlv() {
        byte[] value = constructDeepestTagOfGetDoAllContent(SESSION_ID_TAG);
        return constructGetOrPutDoTlv(new TlvDatum(SESSION_DATA_DO_TAG, value));
    }

    /**
     * Construct the TLV payload for @link getDoCommand} with
     * EXTENTED HEADER LIST defined in ISO7816-4.
     */
    @NonNull
    public static TlvDatum constructGetOrPutDoTlv(TlvDatum tlvDatum) {
        return new TlvDatum(EXTENDED_HEAD_LIST_TAG, tlvDatum);
    }

    /**
     * Get all content for a specific/deepest Tag in the DO tree with Extented Header List.
     */
    @NonNull
    public static byte[] constructDeepestTagOfGetDoAllContent(Tag tag) {
        return Bytes.concat(tag.literalValue, new byte[] {(byte) 0x00});
    }

    /**
     * Get part of content for a specific/deepest Tag with Extenteed Header List.
     */
    @NonNull
    public static byte[] constructDeepestTagOfGetDoPartContent(Tag tag, int len) {
        if (len > 256) {
            throw new IllegalArgumentException("The content length can not be over 256 bytes");
        }

        return Bytes.concat(tag.literalValue, new byte[] { (byte) len});
    }

    /**
     * Generates a session id, it is an integer random value and greater than 0.
     */
    public static int generateRandomSessionId() {
        Random random = new Random();
        int sessionId = random.nextInt();
        while (sessionId <= 0) {
            sessionId = random.nextInt();
        }

        return sessionId;
    }

    /**
     * Generates the session data used by both controller and controlee by combining
     * all information from the controller and the controlee.
     *
     * @param controllerUwbCapability the UwbCapability of Controller
     * @param controlleeInfo The ControlleeInfo which includes UwbCapability of
     *                       the controlee and other information, see {@link ControlleeInfo}
     * @param needSessionSecureInfo If the session Id and key are not derived in
     *                              applet (AKA default sessionId/Key), the session
     *                              secure info should be provided in {@link SessionData}.
     * @param shareSessionId The main session Id shared amid sessions for multicast case
     * @param uniqueSessionId The session Id/sub session Id(multicast), which is unique
     *                        per session
     * @return The Session Data used by both controller and controlee.
     */
    public static SessionData generateSessionData(
            UwbCapability controllerUwbCapability,
            ControlleeInfo controlleeInfo,
            Optional<Integer> shareSessionId,
            int uniqueSessionId,
            boolean needSessionSecureInfo) {
        // TODO: get the Session data by comparing uwb capabilities.
        return new SessionData.Builder().build();
    }
}
