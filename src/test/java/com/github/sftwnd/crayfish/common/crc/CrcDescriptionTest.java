/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static org.junit.jupiter.api.Assertions.*;

class CrcDescriptionTest {

    // every test starts with the predefined models only
    @AfterEach
    void cleanUp() {
        CrcModel.removeRegisteredModels();
    }

    @Test
    void testGetCRC() {
        byte[] buff = "123456789".getBytes();
        getModels().forEach(
                model -> {
                    CrcDescription crcDescription = model.getCrcDescription();
                    CRC crc = crcDescription.getCRC();
                    crc.update(buff);
                    assertEquals(model.getCheck(), crc.getCrc(), "Wrong description check value for crcDescription");
                }
        );
    }

    @Test
    void testGetCRCCrcVal() {
        getModels().filter(m -> m.getCheck()!= null).forEach(
                m -> {
                    long crcVal = 0x17;
                    int  lenVal = 47;
                    CRC crc = m.getCrcDescription().getCRC(crcVal, lenVal);
                    // 0x17 does not fit into the 3- and 4-bit models: the bits above the width are ignored
                    assertEquals(crcVal & CrcModel.widmask(m.getWidth()), Optional.ofNullable(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC(crc, len)");
                    assertEquals(lenVal, Optional.ofNullable(crc).map(CRC::getLength).orElse(null), "Wrong length in the CRC, created by getCRC(crc, len)");
                }
        );
    }

    @Test
    void testWidthOutOfRange() {
        for (int width : new int[]{0, -1, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CrcDescription(width, 0x07, 0x0, false, false, 0x0),
                    "A CrcDescription with the width " + width + " has to be rejected");
        }
        // the limits 1 and 64 are valid widths
        byte[] buff = "123456789".getBytes();
        for (CrcDescription description : new CrcDescription[]{
                new CrcDescription(1, 0x1, 0x0, false, false, 0x0),
                new CrcDescription(64, 0x42F0E1EBA9EA3693L, 0x0, false, false, 0x0)}) {
            assertEquals(CrcReference.crc(description, buff), description.getCRC().update(buff).getCrc(),
                    "Wrong crc for the width " + description.getWidth());
        }
    }

    @Test
    void testToString() {
        getModels().map(CrcModel::getCrcDescription).forEach(description -> assertDoesNotThrow(description::toString, "CrcDescription::toString hasn't got to throws exception"));
    }

    @Test
    void testToStringFormat() {
        assertEquals("CRC-32/P4C11DB7_IFFFFFFFF_RI_RO_XFFFFFFFF", new CrcDescription(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL).toString(),
                "Wrong toString() of the CRC-32/ISO-HDLC parameters");
        assertEquals("CRC-16/P1021_I0", new CrcDescription(16, 0x1021, 0x0, false, false, 0x0).toString(),
                "Wrong toString() of the CRC-16/XMODEM parameters");
    }

    @Test
    void testEqualsAndHashCodeDoNotChangeOnFirstUse() {
        CrcDescription unused = new CrcDescription(13, 0x1CF5, 0x1, true, true, 0x2);
        CrcModel model = CrcModel.construct("CRC-13/TEST-EQUALS-HASHCODE", new CrcDescription(13, 0x1CF5, 0x1, true, true, 0x2));
        model.getCRC().update("123456789".getBytes());
        CrcDescription used = model.getCrcDescription();
        assertNotNull(used.table_byte, "The lookup table of the used CrcDescription has to be built");
        assertEquals(unused, used, "Used CrcDescription has to be equal to an unused one with the same parameters");
        assertEquals(unused.hashCode(), used.hashCode(), "CrcDescription.hashCode() must not change after first use");
    }

    @Test
    void testGetCRCDoesNotRegisterModelForKnownParameters() {
        // README "Simple case": a new CrcDescription with the parameters of CRC-3/GSM for every calculation
        long expected = CrcModel.CRC3_GSM.getCRC().update("StringA".getBytes()).getCrc();
        long modelCount = getModels().count();
        for (int i = 0; i < 100; i++) {
            CrcDescription crcDescription = new CrcDescription(3, 0x3, 0x0, false, false, 0x7);
            assertEquals(expected, crcDescription.getCRC().update("StringA".getBytes()).getCrc(), "Wrong CRC value for the parameters of CRC-3/GSM");
            assertEquals(modelCount, getModels().count(), "CrcDescription.getCRC() must not register models for the parameters of a registered model");
        }
    }

}