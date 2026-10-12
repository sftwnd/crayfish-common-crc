/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.CHECK_BUFF;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CRCTest {

    // every test starts with the predefined models only
    @AfterEach
    void cleanUp() {
        CrcModel.removeRegisteredModels();
    }

    @Test
    void testUpdate() {
        getModels(m -> m.getCheck() != null).forEach( m -> {
            CRC crc = m.getCRC();
            long crcValue = crc.update(CHECK_BUFF.getBytes(US_ASCII)).getCrc();
            assertEquals(m.getCheck(), crc.getCrc(), "CRC value for byte sequence of 123456789 has to be equals check value");
            assertEquals(m.getCheck(), crcValue, "Crc update result for byte sequence of 123456789 has to be equals check value");
            assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(US_ASCII), 1, -1).getCrc(), "Update with negative length has not got to change result");
            assertEquals(m.getCheck(), crc.update(null, 1, 1).getCrc(), "Update with null reference to buff has not got to change result");
            assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(US_ASCII), 1, 0).getCrc(), "Update with zero length has not got to change result");
            assertEquals(m.getCheck(), m.getCRC().update(CHECK_BUFF.getBytes(US_ASCII), 4).update(CHECK_BUFF.getBytes(US_ASCII), 4, 5).getCrc(), "Update by 1234 and then by 56789 has to give the check value");
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
            CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII));
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
    void testUpdateWithNonAsciiText() {
        // UTF-8 text with German, Russian and Chinese letters: bytes >= 0x80
        byte[] text = "Grüße, Привет, 你好".getBytes(StandardCharsets.UTF_8);
        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(text);
        assertEquals(crc32.getValue(), CrcModel.CRC32.getCRC(text).getCrc(), "CRC-32 of UTF-8 text has to be equal to java.util.zip.CRC32");
        getModels().forEach(m -> assertEquals(CrcReference.crc(m.getCrcDescription(), text), m.getCRC(text).getCrc(),
                "Model " + m + ": crc of UTF-8 text has to be equal to the bitwise calculated value"));
    }

    @Test
    void combineUpdate() {
        getModels(m -> m.getCheck() != null).forEach( m -> {
            CRC crc = m.getCRC();
            crc.update(CHECK_BUFF.getBytes(US_ASCII), 0, 4).combine(m.getCRC().update(CHECK_BUFF.getBytes(US_ASCII), 4, 5));
            assertEquals(m.getCheck(), crc.getCrc(), "CRC combine value of crcs of byte sequence of 1234 & 56789 has to be equals check value");
            crc = m.getCRC().combine(m.getCRC().update(CHECK_BUFF.getBytes(US_ASCII)));
            assertEquals(m.getCheck(), crc.getCrc(), "Clear CRC combine value of crc of byte sequence of 123456789 has to be equals check value");
            crc = m.getCRC().update(CHECK_BUFF.getBytes(US_ASCII)).combine(m.getCRC());
            assertEquals(m.getCheck(), crc.getCrc(), "CRC of byte sequence of 123456789 combine with clear crc has to be equals check value");
        });
    }

    @Test
    void testCombineWithOtherParameters() {
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
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
    void testUpdateAt4GiB() {
        byte[] tail = {0x31, 0x32};
        getModels().forEach(m -> {
            long crc2 = m.getCheck();
            long crc3 = m.getCRC(tail).getCrc();
            // two parts of 2 GiB - 1 byte and 2 bytes more: exactly 4 GiB, where an int length wraps to 0
            CRC crc = m.getCRC(crc2, Integer.MAX_VALUE).combine(m.getCRC(crc3, Integer.MAX_VALUE)).update(tail);
            long expected = m.getCRC(m.getCRC(crc2, Integer.MAX_VALUE).combine(crc3, Integer.MAX_VALUE), 1).update(tail).getCrc();
            assertEquals(expected, crc.getCrc(), "Model " + m + ": the crc after exactly 4 GiB has to be the crc of the processed data");
            assertEquals(0x1_0000_0000L, crc.getLength(), "Model " + m + ": getLength() has to return 4 GiB, not 0");
        });
    }

    @Test
    void testCombineWithPartOf2GiB() {
        int part = 1 << 30;
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        getModels().forEach(m -> {
            long crc2 = m.getCheck();
            long crc3 = m.getCRC(buff, 0, 4).getCrc();
            // two parts of 1 GiB: 2 GiB, more than an int length
            CRC part2 = m.getCRC(crc2, part).combine(m.getCRC(crc3, part));
            assertEquals(m.getCRC(crc2, part).combine(crc3, part), part2.getCrc(), "Model " + m + ": wrong crc of 2 GiB");
            assertEquals(2L * part, part2.getLength(), "Model " + m + ": wrong getLength() of 2 GiB");
            // "123456789" followed by the 2 GiB, the same as "123456789" followed by the two parts of 1 GiB
            long expected = m.getCRC(m.getCRC(buff).combine(crc2, part), buff.length + part).combine(crc3, part);
            assertEquals(expected, m.getCRC(buff).combine(part2).getCrc(), "Model " + m + ": combine() with a CRC of 2 GiB has to give the crc of both parts");
        });
    }

    @Test
    void testLengthBeyond4GiB() {
        // the CRC of 4 GiB - 1 bytes and one byte more: no large input is needed
        CRC crc = CrcModel.CRC32.getCRC(0x12345678L, 0xFFFFFFFFL).update(new byte[1]);
        assertEquals(0x1_0000_0000L, crc.getLength(), "getLength() has to return 4 GiB");
        assertEquals(CrcModel.CRC32.getCRC(0x12345678L, 1).update(new byte[1]).getCrc(), crc.getCrc(),
                "The crc after 4 GiB has to continue from the crc given for 4 GiB - 1 bytes");
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        long[] lengths = {(1L << 31) - 1, 1L << 31, 1L << 32, (1L << 32) + 1, 0x7FF_FFFF_FFFFL, Long.MAX_VALUE};
        getModels().forEach(m -> {
            long crc2 = m.getCRC(buff, 0, 4).getCrc();
            for (long len : lengths) {
                String model = "Model " + m + ", length " + len;
                // a state of any length is restored by getCRC(getCrc(), getLength())
                CRC state = m.getCRC(m.getCheck(), len);
                CRC restored = m.getCRC(state.getCrc(), state.getLength());
                assertEquals(state, restored, model + ": getCRC(getCrc(), getLength()) has to restore the CRC");
                assertEquals(len, m.getCrcDescription().getCRC(m.getCheck(), len).getLength(),
                        model + ": CrcDescription.getCRC(crc, len) has to keep the length");
                // combine(crc2, len2) and combine(CRC) give the same value for a long next buffer
                long combined = m.getCRC(buff).combine(crc2, len);
                assertEquals(combined, m.getCRC(buff).combine(m.getCRC(crc2, len)).getCrc(),
                        model + ": combine(crc2, len2) and combine(CRC) have to give the same crc");
                assertEquals(buff.length + len, m.getCRC(buff).combine(m.getCRC(crc2, len)).getLength(),
                        model + ": combine(CRC) has to add the length of the next buffer");
            }
        });
    }

    @Test
    void testCombineFromLengthsOfParts() {
        // a buffer of 3 GiB + 9 bytes as a sequence of parts, combined from their crc values and lengths only
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        getModels().forEach(m -> {
            long gib = 1L << 30;
            long crc1 = m.getCRC(buff).getCrc();
            long crc2 = m.getCRC(buff, 0, 4).getCrc();
            long crc3 = m.getCRC(buff, 4, 5).getCrc();
            // ("123456789" + 2 GiB) + 1 GiB as one step and as two steps of 1 GiB give the same crc
            long twoGiB = m.getCRC(crc2, gib).combine(crc3, gib);
            long oneStep = m.getCRC(crc1, buff.length).combine(m.getCRC(twoGiB, 2 * gib).combine(crc3, gib), 3 * gib);
            long twoSteps = m.getCRC(m.getCRC(m.getCRC(crc1, buff.length).combine(crc2, gib), buff.length + gib)
                    .combine(crc3, gib), buff.length + 2 * gib).combine(crc3, gib);
            assertEquals(twoSteps, oneStep, "Model " + m + ": combine(crc2, len2) of 3 GiB has to give the crc of all parts");
        });
    }

    @Test
    void testIntOverloads() {
        // the int variants of 1.0.x are kept for compiled callers and give the same results
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        getModels().forEach(m -> {
            long crc2 = m.getCRC(buff, 4, 5).getCrc();
            int len = 5;
            assertEquals(m.getCRC(buff, 0, 4).combine(crc2, (long) len), m.getCRC(buff, 0, 4).combine(crc2, len),
                    "Model " + m + ": combine(long, int) has to be the same as combine(long, long)");
            assertEquals(m.getCRC(crc2, (long) len), m.getCRC(crc2, len), "Model " + m + ": getCRC(long, int) has to be the same as getCRC(long, long)");
            assertEquals(m.getCrcDescription().getCRC(crc2, (long) len), m.getCrcDescription().getCRC(crc2, len),
                    "Model " + m + ": CrcDescription.getCRC(long, int) has to be the same as getCRC(long, long)");
        });
    }

    @Test
    void testEqualsHashCodeAndToStringOf2GiB() {
        int part = 1 << 30;
        getModels().forEach(m -> {
            CRC crc = m.getCRC(m.getCheck(), part).combine(m.getCRC(m.getCheck(), part));
            CRC copy = crc.copy();
            assertEquals(crc, copy, "Model " + m + ": a CRC of 2 GiB has to be equal to its copy");
            assertEquals(crc.hashCode(), copy.hashCode(), "Model " + m + ": a CRC of 2 GiB and its copy have to have the same hash code");
            assertNotEquals(crc, m.getCRC(crc.getCrc(), Integer.MAX_VALUE), "Model " + m + ": CRCs with different lengths have not got to be equal");
            assertTrue(crc.toString().endsWith(",len:2147483648]"), "Model " + m + ": toString() has to show the length of 2 GiB: " + crc);
            // the crc value given for a CRC without data is not used
            assertEquals(m.getCRC(), m.getCRC(m.getCheck(), 0), "Model " + m + ": CRCs without data have to be equal");
        });
    }

    @Test
    void testCrcValueWithBitsAboveTheWidth() {
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        getModels().forEach(m -> {
            // all bits above the width set, as by the sign extension of a negative int
            long highBits = ~CrcModel.widmask(m.getWidth());
            long crc1234 = m.getCRC(buff, 0, 4).getCrc();
            long crc56789 = m.getCRC(buff, 4, 5).getCrc();
            String model = "Model " + m;
            CRC crc = m.getCRC(crc1234 | highBits, 4);
            assertEquals(crc1234, crc.getCrc(), model + ": getCRC(crc, len) has to ignore the bits above the width");
            assertEquals(m.getCheck(), crc.copy().update(buff, 4, 5).getCrc(),
                    model + ": update() after getCRC(crc, len) with bits above the width has to give the check value");
            assertEquals(m.getCheck(), crc.copy().combine(m.getCRC(buff, 4, 5)).getCrc(),
                    model + ": combine() after getCRC(crc, len) with bits above the width has to give the check value");
            assertEquals(m.getCheck(), m.getCRC(buff, 0, 4).combine(crc56789 | highBits, 5),
                    model + ": combine(crc2, len2) has to ignore the bits above the width");
        });
    }

    @Test
    void testCrcValueKeptInIntAndByte() throws IOException {
        byte[] head = CHECK_BUFF.getBytes(US_ASCII);
        byte[] tail = {'x'};
        // a CRC-32 written as int and read back: the negative int is sign-extended to a long
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt((int) CrcModel.CRC32.getCRC(head).getCrc());
        int stored = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())).readInt();
        long expected = CrcModel.CRC32.getCRC(head).update(tail).getCrc();
        assertEquals(CrcModel.CRC32.getCheck(), CrcModel.CRC32.getCRC(stored, head.length).getCrc(), "CRC-32 kept in an int: wrong getCrc()");
        assertEquals(expected, CrcModel.CRC32.getCRC(stored, head.length).update(tail).getCrc(), "CRC-32 kept in an int: wrong update()");
        assertEquals(expected, CrcModel.CRC32.getCRC(stored, head.length).combine(CrcModel.CRC32.getCRC(tail)).getCrc(), "CRC-32 kept in an int: wrong combine(CRC)");
        int storedTail = (int) CrcModel.CRC32.getCRC(tail).getCrc();
        assertEquals(expected, CrcModel.CRC32.getCRC(head).combine(storedTail, tail.length), "CRC-32 kept in an int: wrong combine(crc2, len2)");
        // a CRC-8 kept in a byte
        byte stored8 = (byte) CrcModel.CRC8.getCRC(head).getCrc();
        long expected8 = CrcModel.CRC8.getCRC(head).update(tail).getCrc();
        assertEquals(expected8, CrcModel.CRC8.getCRC(stored8, head.length).update(tail).getCrc(), "CRC-8 kept in a byte: wrong update()");
        assertEquals(expected8, CrcModel.CRC8.getCRC(stored8, head.length).combine(CrcModel.CRC8.getCRC(tail)).getCrc(), "CRC-8 kept in a byte: wrong combine(CRC)");
    }

    @Test
    void testCopy() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII));
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
                    assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(US_ASCII)).getCrc(), "CRC value of 123456789 has to be equals model check value");
                    assertEquals(emptyCrc, m.getCRC(null).getCrc(), "CRC value of empty buff has to be equal to the CRC of the empty message");
                }
        );
    }

    @Test
    void testGetCrcOfEmptyInput() {
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
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
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII));
                    CRC crc1 = new CRC(crc);
                    assertEquals(crc, crc1, "CRC and new CRC(crc) have to be equals");
                }
        );
    }

    @Test
    void testConstructorsBuildLookupTable() {
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
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
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII));
                    CRC crc1 = crc.copy();
                    assertEquals(crc.hashCode(), crc1.hashCode(), "CRC.hashCode() on cloned CRCs have to be equals");
                }
        );
    }

    @Test
    // assertNotEquals(a, b) calls a.equals(b): the object under test has to be the first argument, and with null first
    // equals is not called at all, so the arguments are not swapped for java:S3415 (expected value, actual value)
    @SuppressWarnings("java:S3415")
    void testEquals() {
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        CRC crc = CrcModel.CRC32.getCRC(buff);
        assertEquals(crc, crc, "A CRC has to be equal to itself");
        assertEquals(crc, CrcModel.CRC32.getCRC(buff), "CRCs of the same model over the same data have to be equal");
        assertNotEquals(crc, null, "A CRC must not be equal to null");
        assertNotEquals(crc, CrcModel.CRC32, "A CRC must not be equal to its model");
        assertNotEquals(crc, CrcModel.CRC32.getCRC(buff, 8), "CRCs with different values and lengths must not be equal");
        assertNotEquals(crc, CrcModel.CRC32.getCRC(crc.getCrc() ^ 1, 9), "CRCs with different values must not be equal");
        assertNotEquals(crc, CrcModel.CRC32.getCRC(crc.getCrc(), 10), "CRCs with different lengths must not be equal");
        assertNotEquals(crc, CrcModel.PKZIP.getCRC(buff), "CRCs of an alias and its model must not be equal");
        assertEquals(crc.hashCode(), CrcModel.CRC32.getCRC(buff).hashCode(), "Equal CRCs have to have the same hash code");
        assertEquals(CrcModel.CRC32.getCRC().hashCode(), CrcModel.CRC32.getCRC().hashCode(), "Equal empty CRCs have to have the same hash code");
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
            getModels().peek( m -> strings.add(m.getCRC(CHECK_BUFF.getBytes(US_ASCII)).toString())).count()
           ,strings.stream().distinct().count()
           ,"All toString value for different CRCs has to be different");
    }

    @Test
    void testToStringFormat() {
        byte[] buff = CHECK_BUFF.getBytes(US_ASCII);
        assertEquals("CRC-64/XZ[crc:0x995dc9bbdf1939fa,len:9]", CrcModel.CRC64_XZ.getCRC(buff).toString(),
                "toString() has to show a 64-bit crc value as unsigned hexadecimal number");
        assertEquals("CRC-32[crc:0xcbf43926,len:9]", CrcModel.CRC32.getCRC(buff).toString(), "Wrong toString() of CRC-32");
        assertEquals("CRC-16/ARC[crc:0xbb3d,len:9]", CrcModel.CRC16_ARC.getCRC(buff).toString(), "Wrong toString() of CRC-16/ARC");
        assertEquals("CRC-3/GSM[crc:0x4,len:9]", CrcModel.CRC3_GSM.getCRC(buff).toString(), "Wrong toString() of CRC-3/GSM");
        assertEquals("CRC-32[crc:0x00000000,len:0]", CrcModel.CRC32.getCRC().toString(), "toString() has to show all digits of the CRC width");
    }

}