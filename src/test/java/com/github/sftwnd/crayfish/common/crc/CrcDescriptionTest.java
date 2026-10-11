/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class CrcDescriptionTest {

    // every test starts with the predefined models only
    @AfterEach
    void cleanUp() {
        CrcModel.removeRegisteredModels();
    }

    @Test
    void testGetCRC() {
        byte[] buff = "123456789".getBytes(US_ASCII);
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
        byte[] buff = "123456789".getBytes(US_ASCII);
        for (CrcDescription description : new CrcDescription[]{
                new CrcDescription(1, 0x1, 0x0, false, false, 0x0),
                new CrcDescription(64, 0x42F0E1EBA9EA3693L, 0x0, false, false, 0x0)}) {
            assertEquals(CrcReference.crc(description, buff), description.getCRC().update(buff).getCrc(),
                    "Wrong crc for the width " + description.getWidth());
        }
    }

    @Test
    void testParametersOutOfWidth() {
        // CRC-32/ISO-HDLC with the x^32 term in the polynomial
        IllegalArgumentException poly = assertThrows(IllegalArgumentException.class,
                () -> new CrcDescription(32, 0x104C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL), "A poly with the x^32 term has to be rejected");
        assertTrue(poly.getMessage().contains("poly 0x104C11DB7") && poly.getMessage().contains("x^32 term"), "Wrong message: " + poly.getMessage());
        // CRC-32/BZIP2 with the x^32 term: update() gave the correct value, combine() failed
        assertThrows(IllegalArgumentException.class, () -> new CrcDescription(32, 0x104C11DB7L, 0xFFFFFFFFL, false, false, 0xFFFFFFFFL),
                "A non-reflected poly with the x^32 term has to be rejected");
        // CRC-16/IBM-SDLC with a 17-bit xorout
        IllegalArgumentException xorot = assertThrows(IllegalArgumentException.class,
                () -> new CrcDescription(16, 0x1021, 0xFFFF, true, true, 0x1FFFF), "A 17-bit xorout has to be rejected for a 16-bit CRC");
        assertTrue(xorot.getMessage().contains("xorot 0x1FFFF"), "Wrong message: " + xorot.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new CrcDescription(16, 0x1021, 0x10000, false, false, 0x0),
                "A 17-bit init has to be rejected for a 16-bit CRC");
        assertThrows(IllegalArgumentException.class, () -> new CrcDescription(3, 0x3, 0x0, false, false, 0x8),
                "A 4-bit xorout has to be rejected for a 3-bit CRC");
        // CRC-32 written with int literals: 0xFFFFFFFF is the int -1, sign-extended to a long
        IllegalArgumentException literal = assertThrows(IllegalArgumentException.class,
                () -> new CrcDescription(32, 0x04C11DB7, 0xFFFFFFFF, true, true, 0xFFFFFFFF), "A sign-extended init has to be rejected");
        assertTrue(literal.getMessage().contains("0xFFFFFFFFL"), "The message has to suggest the long literal: " + literal.getMessage());
    }

    @Test
    void testParametersAtTheWidthLimit() {
        byte[] buff = "123456789".getBytes(US_ASCII);
        // all bits of the width set: the largest valid values, also for 64 bits where the values are negative
        for (int width : new int[]{1, 3, 8, 31, 32, 33, 63, 64}) {
            long mask = CrcModel.widmask(width);
            for (boolean reflected : new boolean[]{false, true}) {
                CrcDescription description = new CrcDescription(width, mask, mask, reflected, reflected, mask);
                assertEquals(CrcReference.crc(description, buff), description.getCRC().update(buff).getCrc(),
                        "Wrong crc of " + description);
            }
        }
        // every predefined model satisfies the check
        getModels().forEach(m -> assertEquals(m.getCrcDescription(), new CrcDescription(m.getCrcDescription()), "Model " + m + ": the parameters have to be accepted"));
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
        model.getCRC().update("123456789".getBytes(US_ASCII));
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