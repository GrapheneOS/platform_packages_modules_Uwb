/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.ObjectIdentifier;

import org.junit.Test;

public class CsmlUtilTest {
    @Test
    public void encodeObjectIdentifierAsTlv() {
        ObjectIdentifier oid =
                ObjectIdentifier.fromBytes(new byte[]{(byte) 0x01, (byte) 0x02});
        byte[] actual = CsmlUtil.encodeObjectIdentifierAsTlv(oid).toBytes();
        byte[] expected = DataTypeConversionUtil.hexStringToByteArray("06020102");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void constructGetDoTlvForTopTag() {
        TlvDatum.Tag doTag = new TlvDatum.Tag((byte) 0x0A);
        byte[] actual = CsmlUtil.constructGetDoTlv(doTag).toBytes();
        byte[] expected = DataTypeConversionUtil.hexStringToByteArray("4D020A00");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void constructSessionDataGetDoTlv() {
        byte[] actual = CsmlUtil.constructSessionDataGetDoTlv().toBytes();
        byte[] expected = DataTypeConversionUtil.hexStringToByteArray("4D03BF7800");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void constructTerminateSessionGetDoTlv() {
        byte[] actual = CsmlUtil.constructTerminateSessionGetDoTlv().toBytes();
        byte[] expected = DataTypeConversionUtil.hexStringToByteArray("4D05BF79028000");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void constructDeepestTagOfGetDoPartContent() {
        TlvDatum.Tag tag = new TlvDatum.Tag((byte) 0x0A);
        int len = 2;
        byte[] actual = CsmlUtil.constructDeepestTagOfGetDoPartContent(tag, len);
        byte[] expected = new byte[] {(byte) 0x0A, (byte) 0x02};

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void isSessionDataNotAvailableTrue() {
        TlvDatum tlvDatum = new TlvDatum(
                CsmlUtil.UWB_CONFIG_AVAILABLE_TAG, new byte[] { (byte) 0x00 });

        boolean result = CsmlUtil.isSessionDataNotAvailable(tlvDatum.toBytes());

        assertThat(result).isTrue();
    }

    @Test
    public void isSessionDataNotAvailableFalse() {
        TlvDatum tlvDatum = new TlvDatum(
                CsmlUtil.UWB_CONFIG_AVAILABLE_TAG, new byte[] { (byte) 0x01 });

        boolean result = CsmlUtil.isSessionDataNotAvailable(tlvDatum.toBytes());

        assertThat(result).isFalse();
    }

}
