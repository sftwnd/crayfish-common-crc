/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.CHECK_BUFF;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CRCTest {

    @Test
    void testUpdate() {
        getModels(m -> m.getCheck() != null).forEach( m -> {
            CRC crc = m.getCRC();
            long crcValue = crc.update(CHECK_BUFF.getBytes()).getCrc();
            assertEquals(m.getCheck(), crc.getCrc(), "CRC value for byte sequence of 123456789 has to be equals check value");
            assertEquals(m.getCheck(), crcValue, "Crc update result for byte sequence of 123456789 has to be equals check value");
            assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(), 1, -1).getCrc(), "Update with negative length has not got to change result");
            assertEquals(m.getCheck(), crc.update(null, 1, 1).getCrc(), "Update with null reference to buff has not got to change result");
            assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(), 1, -1).getCrc(), "Update with negative length has not got to change result");
            assertEquals(m.getCheck(), m.getCRC().update(CHECK_BUFF.getBytes(), 4).update(CHECK_BUFF.getBytes(), 4, 5).getCrc(), "Update with negative length has not got to change result");
        });
    }

    @Test
    void testUpdateWithBytesAbove0x7F() {
        // models with width <= 8 and refin = false; the expected values were calculated bit by bit
        assertEquals(0x89L, CrcModel.CRC8_SMBUS.getCRC(new byte[]{(byte) 0x80}).getCrc(), "Wrong CRC-8/SMBUS of 0x80");
        assertEquals(0xF3L, CrcModel.CRC8_SMBUS.getCRC(new byte[]{(byte) 0xFF}).getCrc(), "Wrong CRC-8/SMBUS of 0xFF");
        assertEquals(0x98L, CrcModel.CRC8_SMBUS.getCRC(new byte[]{(byte) 0xEA}).getCrc(), "Wrong CRC-8/SMBUS of 0xEA");
        assertEquals(0x30L, CrcModel.CRC8_SMBUS.getCRC(new byte[]{0x16, 0x09, (byte) 0xC4}).getCrc(), "Wrong CRC-8/SMBUS of 0x16 0x09 0xC4");
        byte[] buff = {(byte) 0xFF, 0x00, (byte) 0xA5};
        assertEquals(0xFCL, CrcModel.CRC8_AUTOSAR.getCRC(buff).getCrc(), "Wrong CRC-8/AUTOSAR of 0xFF 0x00 0xA5");
        assertEquals(0x37L, CrcModel.CRC8_SAE_J1850.getCRC(buff).getCrc(), "Wrong CRC-8/SAE-J1850 of 0xFF 0x00 0xA5");
        assertEquals(0x3DL, CrcModel.CRC7_MMC.getCRC(buff).getCrc(), "Wrong CRC-7/MMC of 0xFF 0x00 0xA5");
        assertEquals(0x5L, CrcModel.CRC3_GSM.getCRC(buff).getCrc(), "Wrong CRC-3/GSM of 0xFF 0x00 0xA5");
    }

    @Test
    void testUpdateWithNullBuff() {
        getModels().forEach(m -> {
            CRC crc = m.getCRC(CHECK_BUFF.getBytes());
            assertEquals(m.getCheck(), crc.update((byte[]) null).getCrc(), "Model " + m + ": update(null) has not got to change the crc value");
            assertEquals(CHECK_BUFF.length(), crc.getLength(), "Model " + m + ": update(null) has not got to change the length");
            CRC clear = m.getCRC().update((byte[]) null);
            assertEquals(m.getCRC().getCrc(), clear.getCrc(), "Model " + m + ": update(null) of a clear CRC has to keep the CRC of the empty message");
            assertEquals(0, clear.getLength(), "Model " + m + ": update(null) of a clear CRC has to keep the length 0");
        });
    }

    @Test
    void testUpdateWithAllByteValues() {
        getModels().forEach(m -> {
            Random random = new Random(m.getName().hashCode());
            for (int i = 0; i < 32; i++) {
                byte[] buff = new byte[1 + random.nextInt(512)];
                random.nextBytes(buff);
                int offset = random.nextInt(buff.length);
                int len = buff.length - offset;
                int split = random.nextInt(len + 1);
                long expected = CrcReference.crc(m.getCrcDescription(), buff, offset, len);
                String sample = "Model " + m + ", sample " + i + " (offset " + offset + ", len " + len + ", split " + split + ")";
                assertEquals(expected, m.getCRC(buff, offset, len).getCrc(),
                        sample + ": crc of random bytes has to be equal to the bitwise calculated value");
                assertEquals(expected, m.getCRC().update(buff, offset, split).update(buff, offset + split, len - split).getCrc(),
                        sample + ": crc of random bytes updated in two parts has to be equal to the bitwise calculated value");
                assertEquals(expected, m.getCRC(buff, offset, split).combine(m.getCRC(buff, offset + split, len - split)).getCrc(),
                        sample + ": combined crc of two parts of random bytes has to be equal to the bitwise calculated value");
            }
        });
    }

    @Test
    void combineUpdate() {
        getModels(m -> m.getCheck() != null).forEach( m -> {
            CRC crc = m.getCRC();
            crc.update(CHECK_BUFF.getBytes(), 0, 4).combine(m.getCRC().update(CHECK_BUFF.getBytes(), 4, 5));
            assertEquals(m.getCheck(), crc.getCrc(), "CRC combine value of crcs of byte sequence of 1234 & 56789 has to be equals check value");
            crc = m.getCRC().combine(m.getCRC().update(CHECK_BUFF.getBytes()));
            assertEquals(m.getCheck(), crc.getCrc(), "Clear CRC combine value of crc of byte sequence of 123456789 has to be equals check value");
            crc = m.getCRC().update(CHECK_BUFF.getBytes()).combine(m.getCRC());
            assertEquals(m.getCheck(), crc.getCrc(), "CRC of byte sequence of 123456789 combine with clear crc has to be equals check value");
        });
    }

    @Test
    void testCombineWithOtherParameters() {
        byte[] buff = CHECK_BUFF.getBytes();
        // other width, other polynomial, other reflection
        for (CrcModel other : new CrcModel[]{CrcModel.CRC16_ARC, CrcModel.CRC32_ISCSI, CrcModel.CRC32_BZIP2}) {
            CRC crc = CrcModel.CRC32.getCRC(buff, 0, 4);
            long crcValue = crc.getCrc();
            CRC next = other.getCRC(buff, 4, 5);
            assertThrows(IllegalArgumentException.class, () -> crc.combine(next), "combine() with a CRC of " + other + " has to throw IllegalArgumentException");
            CRC clear = other.getCRC();
            assertThrows(IllegalArgumentException.class, () -> crc.combine(clear), "combine() with a clear CRC of " + other + " has to throw IllegalArgumentException");
            assertEquals(crcValue, crc.getCrc(), "Rejected combine() has not got to change the crc value");
            assertEquals(4, crc.getLength(), "Rejected combine() has not got to change the length");
        }
        // an alias has the same parameters
        assertEquals(CrcModel.CRC32.getCheck(), CrcModel.CRC32.getCRC(buff, 0, 4).combine(CrcModel.PKZIP.getCRC(buff, 4, 5)).getCrc(),
                "combine() with a CRC of an alias has to give the CRC of both parts");
        assertEquals(CrcModel.CRC32.getCheck(), CrcModel.CRC32.getCRC(buff).combine(null).getCrc(), "combine(null) has not got to change the CRC");
    }

    @Test
    void testCopy() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    CRC crc1 = crc.copy();
                    assertEquals(crc, crc1, "CRC and CRC.clone() have to be equals");
                }
        );
    }

    @Test
    void testGetCrc() {
        getModels(m -> m.getCheck() != null).forEach(
                m -> {
                    long emptyCrc = CrcReference.crc(m.getCrcDescription(), new byte[0]);
                    CRC crc = m.getCRC();
                    assertEquals(emptyCrc, crc.getCrc(), "Clear CRC value has to be equal to the CRC of the empty message");
                    assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes()).getCrc(), "CRC value of 123456789 has to be equals model check value");
                    assertEquals(emptyCrc, m.getCRC(null).getCrc(), "CRC value of empty buff has to be equal to the CRC of the empty message");
                }
        );
    }

    @Test
    void testGetCrcOfEmptyInput() {
        byte[] buff = CHECK_BUFF.getBytes();
        getModels().forEach(m -> {
            long emptyCrc = CrcReference.crc(m.getCrcDescription(), new byte[0]);
            String model = "Model " + m;
            assertEquals(emptyCrc, m.getCRC(new byte[0]).getCrc(), model + ": crc of an empty buff has to be equal to the CRC of the empty message");
            assertEquals(emptyCrc, m.getCRC(buff, 0).getCrc(), model + ": crc of 0 bytes of buff has to be equal to the CRC of the empty message");
            assertEquals(emptyCrc, m.getCRC(buff, 4, 0).getCrc(), model + ": crc of 0 bytes from offset 4 has to be equal to the CRC of the empty message");
            assertEquals(emptyCrc, m.getCRC().update(new byte[0]).update(buff, 4, 0).update(null, 0, 1).getCrc(),
                    model + ": update without data has not got to change the CRC of the empty message");
            assertEquals(emptyCrc, m.getCRC().copy().getCrc(), model + ": copy of a clear CRC has to keep the CRC of the empty message");
            assertEquals(emptyCrc, m.getCRC().combine(m.getCRC()).getCrc(), model + ": combine of two clear CRCs has to be equal to the CRC of the empty message");
            assertEquals(emptyCrc, m.getCRC().combine(m.getCheck(), 0), model + ": combine of a clear CRC with 0 bytes has to be equal to the CRC of the empty message");
            CRC empty = m.getCRC();
            assertEquals(m.getCheck(), m.getCRC(empty.getCrc(), empty.getLength()).update(buff).getCrc(),
                    model + ": calculation continued from a clear CRC has to give the check value");
        });
    }

    @Test
    void testGetCrcOfEmptyInputKnownValues() {
        assertEquals(new java.util.zip.CRC32().getValue(), CrcModel.CRC32.getCRC().getCrc(), "CRC-32 of the empty message has to be equal to java.util.zip.CRC32");
        assertEquals(0x0L, CrcModel.CRC32.getCRC(new byte[0]).getCrc(), "Wrong CRC-32 of the empty message");
        assertEquals(0x0L, CrcModel.CRC32_ISCSI.getCRC().getCrc(), "Wrong CRC-32/ISCSI of the empty message");
        assertEquals(0x0L, CrcModel.CRC64_XZ.getCRC().getCrc(), "Wrong CRC-64/XZ of the empty message");
        assertEquals(0x0L, CrcModel.CRC16_X_25.getCRC().getCrc(), "Wrong CRC-16/X-25 of the empty message");
        assertEquals(0xFFFFFFFFL, CrcModel.CRC32_CKSUM.getCRC().getCrc(), "Wrong CRC-32/CKSUM of the empty message");
        assertEquals(0x554DL, CrcModel.CRC16_RIELLO.getCRC().getCrc(), "Wrong CRC-16/RIELLO of the empty message");
        assertEquals(0x7L, CrcModel.CRC3_GSM.getCRC().getCrc(), "Wrong CRC-3/GSM of the empty message");
    }

    @Test
    void testConstructor() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    CRC crc1 = new CRC(crc);
                    assertEquals(crc, crc1, "CRC and new CRC(crc) have to be equals");
                }
        );
    }

    @Test
    void testConstructorsBuildLookupTable() {
        byte[] buff = CHECK_BUFF.getBytes();
        // with a known check value, construct() calculates no CRC, so the lookup table of the new model is not built yet
        CrcDescription description = new CrcDescription(16, 0x2F15, 0x1D0F, false, true, 0x0);
        CrcModel model = CrcModel.construct("CRC-16/TEST-CRC-CONSTRUCTOR", description, CrcReference.crc(description, buff));
        assertEquals(model.getCheck(), new CRC(model).update(buff).getCrc(),
                "new CRC(model) has to build the lookup table of a model that has not been used yet");
        CrcDescription description2 = new CrcDescription(16, 0x2F17, 0x1D0F, false, true, 0x0);
        CrcModel model2 = CrcModel.construct("CRC-16/TEST-CRC-CONSTRUCTOR-CRC-LEN", description2, CrcReference.crc(description2, buff));
        assertEquals(model2.getCheck(), new CRC(model2, CrcReference.crc(description2, buff, 0, 4), 4).update(buff, 4, 5).getCrc(),
                "new CRC(model, crc, len) has to build the lookup table of a model that has not been used yet");
    }

    @Test
    void testHashCode() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    CRC crc1 = crc.copy();
                    assertEquals(crc.hashCode(), crc1.hashCode(), "CRC.hashCode() on cloned CRCs have to be equals");
                }
        );
    }

    @Test
    void testFind() {
        assertNull(CrcModel.lookUp(null), "CrcModel.lookUp(null) has to be null");
        getModels().forEach( model -> assertSame(model, CrcModel.lookUp(model.getName()), "CrcModel.lookUp(\"" + model + "\") has to be same with model"));
    }

    @Test
    void testToString() {
        Set<String> strings = new HashSet<>();
        assertEquals(
            getModels().peek( m -> strings.add(m.getCRC(CHECK_BUFF.getBytes()).toString())).count()
           ,strings.stream().distinct().count()
           ,"All toString value for different CRCs has to be different");
    }

    @Test
    void testToStringFormat() {
        byte[] buff = CHECK_BUFF.getBytes();
        assertEquals("CRC-64/XZ[crc:0x995dc9bbdf1939fa,len:9]", CrcModel.CRC64_XZ.getCRC(buff).toString(),
                "toString() has to show a 64-bit crc value as unsigned hexadecimal number");
        assertEquals("CRC-32[crc:0xcbf43926,len:9]", CrcModel.CRC32.getCRC(buff).toString(), "Wrong toString() of CRC-32");
        assertEquals("CRC-16/ARC[crc:0xbb3d,len:9]", CrcModel.CRC16_ARC.getCRC(buff).toString(), "Wrong toString() of CRC-16/ARC");
        assertEquals("CRC-3/GSM[crc:0x4,len:9]", CrcModel.CRC3_GSM.getCRC(buff).toString(), "Wrong toString() of CRC-3/GSM");
        assertEquals("CRC-32[crc:0x00000000,len:0]", CrcModel.CRC32.getCRC().toString(), "toString() has to show all digits of the CRC width");
    }

}