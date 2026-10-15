/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CRCTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the models of the catalogue with the names of the algorithms
    private static List<CrcModel> models() {
        return Arrays.stream(StandardCrc.values()).map(StandardCrc::model).collect(Collectors.toList());
    }

    // the models of the catalogue with every name and alias
    private static List<CrcModel> modelsOfAllNames() {
        return Arrays.stream(StandardCrc.values()).flatMap(crc -> crc.names().stream()).map(CrcModel::of).collect(Collectors.toList());
    }

    // the models of the catalogue and a model of every width 1..64 with every refin and refout
    private static List<CrcModel> modelsOfAllWidths() {
        List<CrcModel> result = new ArrayList<>(models());
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 4; kind++) {
                result.add(CrcModel.of(width, 0x42F0E1EBA9EA3693L & mask, 0x0123456789ABCDEFL & mask, (kind & 1) != 0, (kind & 2) != 0,
                        0xFEDCBA9876543210L & mask));
            }
        }
        return result;
    }

    private static long reference(CrcModel model, byte[] data, int offset, int length) {
        return CrcReference.crc(model.parameters(), data, offset, length);
    }

    @Test
    void testCheckValues() {
        for (CrcModel model : modelsOfAllNames()) {
            CRC crc = model.crc(CHECK);
            assertEquals(model.check(), crc.value(), model + ": the CRC of 123456789 has to be the check value");
            assertEquals(CHECK.length, crc.length(), model + ": wrong length");
            assertEquals(model.check(), model.crc().update(CHECK, 0, 4).update(CHECK, 4, 5).value(), model + ": the CRC of 1234 and 56789 has to be the check value");
        }
    }

    @Test
    void testImmutability() {
        CrcModel model = CrcModel.of("CRC-32");
        CRC empty = model.crc();
        long emptyValue = empty.value();
        CRC first = empty.update(CHECK, 0, 4);
        CRC second = first.update(CHECK, 4, 5);
        assertNotSame(empty, first, "update() has to return a new CRC");
        assertNotSame(first, second, "update() has to return a new CRC");
        assertEquals(emptyValue, empty.value(), "update() has not got to change the CRC");
        assertEquals(0, empty.length(), "update() has not got to change the CRC");
        assertEquals(reference(model, CHECK, 0, 4), first.value(), "update() has not got to change the CRC");
        assertEquals(4, first.length(), "update() has not got to change the CRC");
        assertEquals(model.check(), second.value(), "Wrong CRC of both parts");
        CRC next = model.crc(CHECK, 4, 5);
        CRC combined = first.combine(next);
        assertNotSame(first, combined, "combine() has to return a new CRC");
        assertEquals(second, combined, "combine() has to give the CRC of both parts");
        assertEquals(4, first.length(), "combine() has not got to change the CRC");
        assertEquals(5, next.length(), "combine() has not got to change the next CRC");
        assertEquals(second, first.combine(next.value(), next.length()), "combine(value, length) has to give the CRC of both parts");
        assertEquals(4, first.length(), "combine(value, length) has not got to change the CRC");
        // nothing to add: the CRC itself
        assertSame(first, first.update(new byte[0]), "update() of the empty data has to return the CRC itself");
        assertSame(first, first.update(CHECK, 3, 0), "update() of 0 bytes has to return the CRC itself");
        assertSame(first, first.combine(model.crc()), "combine() with the CRC of the empty data has to return the CRC itself");
        assertSame(first, first.combine(0x1234L, 0), "combine() with 0 bytes has to return the CRC itself");
    }

    @Test
    void testRandomData() {
        for (CrcModel model : models()) {
            Random random = new Random(model.name().hashCode());
            for (int i = 0; i < 32; i++) {
                byte[] data = new byte[1 + random.nextInt(512)];
                random.nextBytes(data);
                int offset = random.nextInt(data.length);
                int length = data.length - offset;
                int split = random.nextInt(length + 1);
                long expected = reference(model, data, offset, length);
                String sample = model + ", sample " + i + " (offset " + offset + ", length " + length + ", split " + split + ")";
                assertEquals(expected, model.crc(data, offset, length).value(), sample + ": wrong CRC");
                assertEquals(expected, model.crc().update(data, offset, split).update(data, offset + split, length - split).value(),
                        sample + ": wrong CRC updated in two parts");
                assertEquals(expected, model.crc(data, offset, split).combine(model.crc(data, offset + split, length - split)).value(),
                        sample + ": wrong combined CRC of two parts");
            }
        }
    }

    @Test
    void testAllWidthsAndReflections() {
        // up to 48 bytes: a byte at a time below 24 bytes, 8 bytes at a time and the rest from 24 bytes on
        byte[] data = new byte[48];
        new Random(64).nextBytes(data);
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 4; kind++) {
                CrcModel model = CrcModel.of(width, 0x42F0E1EBA9EA3693L & mask, 0x0123456789ABCDEFL & mask, (kind & 1) != 0, (kind & 2) != 0,
                        0xFEDCBA9876543210L & mask);
                for (int length = 0; length <= data.length; length++) {
                    assertEquals(reference(model, data, 0, length), model.crc(data, 0, length).value(), model + ": wrong CRC of " + length + " bytes");
                }
                assertEquals(CrcReference.crc(model.parameters(), CHECK), model.check(), model + ": wrong check value");
                assertEquals(reference(model, data, 0, data.length), model.crc(data, 0, 20).combine(model.crc(data, 20, 28)).value(),
                        model + ": wrong combined CRC");
                assertEquals(reference(model, new byte[0], 0, 0), model.crc().value(), model + ": wrong CRC of the empty data");
            }
        }
    }

    @Test
    void testSlicingBoundaries() {
        // 8 bytes at a time from 24 bytes on: blocks of 8 bytes and the rest, at an offset that is not a multiple of 8
        byte[] data = new byte[64];
        new Random(62).nextBytes(data);
        for (CrcModel model : models()) {
            for (int length = 0; length <= 40; length++) {
                assertEquals(reference(model, data, 3, length), model.crc(data, 3, length).value(), model + ": wrong CRC of " + length + " bytes");
            }
            long expected = reference(model, data, 3, 40);
            for (int split = 0; split <= 40; split++) {
                assertEquals(expected, model.crc(data, 3, split).update(data, 3 + split, 40 - split).value(),
                        model + ": wrong CRC of 40 bytes updated in two parts at " + split);
            }
        }
    }

    @Test
    void testAllByteValues() {
        byte[] all = new byte[256];
        for (int value = 0; value < all.length; value++) {
            all[value] = (byte) value;
        }
        for (CrcModel model : models()) {
            for (int value = 0; value < 256; value++) {
                assertEquals(reference(model, all, value, 1), model.crc(all, value, 1).value(),
                        model + ": wrong CRC of the byte 0x" + Integer.toHexString(value));
            }
            long expected = reference(model, all, 0, all.length);
            assertEquals(expected, model.crc(all).value(), model + ": wrong CRC of the bytes 0x00..0xFF");
            assertEquals(expected, model.crc(all, 0, 100).combine(model.crc(all, 100, 156)).value(), model + ": wrong combined CRC of the bytes 0x00..0xFF");
        }
    }

    @Test
    void testNonAsciiText() {
        // UTF-8 text with German, Russian and Chinese letters: bytes >= 0x80
        byte[] text = "Grüße, Привет, 你好".getBytes(UTF_8);
        CRC32 crc32 = new CRC32();
        crc32.update(text, 0, text.length);
        assertEquals(crc32.getValue(), CrcModel.of("CRC-32").crc(text).value(), "CRC-32 of UTF-8 text has to be equal to java.util.zip.CRC32");
        for (CrcModel model : models()) {
            assertEquals(reference(model, text, 0, text.length), model.crc(text).value(), model + ": wrong CRC of UTF-8 text");
        }
    }

    @Test
    void testEmptyData() {
        for (CrcModel model : models()) {
            long empty = reference(model, new byte[0], 0, 0);
            assertEquals(empty, model.crc().value(), model + ": wrong CRC of the empty data");
            assertEquals(empty, model.crc(new byte[0]).value(), model + ": wrong CRC of an empty buffer");
            assertEquals(empty, model.crc(CHECK, 4, 0).value(), model + ": wrong CRC of 0 bytes");
            assertEquals(empty, model.crc().combine(model.crc()).value(), model + ": wrong combined CRC of the empty data");
            assertEquals(model.check(), model.crc(model.crc().value(), 0).update(CHECK).value(), model + ": wrong CRC continued from the empty data");
        }
        assertEquals(new CRC32().getValue(), CrcModel.of("CRC-32").crc().value(), "CRC-32 of the empty data has to be equal to java.util.zip.CRC32");
        assertEquals(0x0L, CrcModel.of("CRC-32/ISCSI").crc().value(), "Wrong CRC-32/ISCSI of the empty data");
        assertEquals(0x0L, CrcModel.of("CRC-64/XZ").crc().value(), "Wrong CRC-64/XZ of the empty data");
        assertEquals(0x0L, CrcModel.of("CRC-16/X-25").crc().value(), "Wrong CRC-16/X-25 of the empty data");
        assertEquals(0xFFFFFFFFL, CrcModel.of("CRC-32/CKSUM").crc().value(), "Wrong CRC-32/CKSUM of the empty data");
        assertEquals(0x554DL, CrcModel.of("CRC-16/RIELLO").crc().value(), "Wrong CRC-16/RIELLO of the empty data");
        assertEquals(0x7L, CrcModel.of("CRC-3/GSM").crc().value(), "Wrong CRC-3/GSM of the empty data");
    }

    @Test
    void testCombine() {
        for (CrcModel model : models()) {
            for (int split = 0; split <= CHECK.length; split++) {
                CRC first = model.crc(CHECK, 0, split);
                CRC next = model.crc(CHECK, split, CHECK.length - split);
                assertEquals(model.check(), first.combine(next).value(), model + ": wrong combined CRC at " + split);
                assertEquals(CHECK.length, first.combine(next).length(), model + ": wrong combined length at " + split);
                assertEquals(model.check(), first.combine(next.value(), next.length()).value(), model + ": wrong combined value at " + split);
                assertEquals(model.check(), model.combine(first.value(), next.value(), next.length()), model + ": wrong combine() of the model at " + split);
            }
        }
    }

    @Test
    void testCombineWithZeros() {
        // the CRC continued over zero bytes is the CRC combined with the CRC of the zeros: the lengths set the bits 0..16
        byte[] data = new byte[11];
        new Random(11).nextBytes(data);
        int[] lengths = {1, 2, 3, 7, 8, 9, 24, 255, 256, 257, 4096, 65537};
        for (CrcModel model : modelsOfAllWidths()) {
            CRC first = model.crc(data);
            for (int length : lengths) {
                byte[] zeros = new byte[length];
                assertEquals(first.update(zeros).value(), first.combine(model.crc(zeros)).value(), model + ": wrong combined CRC of " + length + " zero bytes");
                assertEquals(first.update(zeros).value(), model.combine(first.value(), model.value(zeros), length),
                        model + ": wrong combine() of the model of " + length + " zero bytes");
            }
        }
    }

    @Test
    void testCombineWithLongLengths() {
        // lengths with the high bits set, compared with the matrices of crcany
        long[] lengths = {1, 8, 1L << 31, (1L << 32) + 5, 0x123456789ABL, 1L << 62, Long.MAX_VALUE - 4};
        for (CrcModel model : modelsOfAllWidths()) {
            CRC first = model.crc(CHECK, 0, 4);
            long next = model.crc(CHECK, 4, 5).value();
            for (long length : lengths) {
                long reference = CrcReference.combine(model.parameters(), first.value(), next, length);
                assertEquals(reference, first.combine(next, length).value(), model + ": wrong combined CRC of a second part of " + length + " bytes");
                assertEquals(reference, model.combine(first.value(), next, length), model + ": wrong combine() of the model of a second part of " + length + " bytes");
            }
        }
    }

    @Test
    void testCombineOfTheModel() {
        for (CrcModel model : modelsOfAllWidths()) {
            long first = model.value(CHECK, 4);
            long next = model.value(CHECK, 4, 5);
            // the value of the empty data as the first value gives the second value, whatever the length of the second data
            assertEquals(next, model.combine(model.crc().value(), next, 5), model + ": combine() of the empty first data has to give the second value");
            assertEquals(next, model.combine(model.crc().value(), next, Long.MAX_VALUE), model + ": combine() of the empty first data has to give the second value");
            assertEquals(first, model.combine(first, next, 0), model + ": combine() of the empty second data has to give the first value");
            assertEquals(first, model.combine(first, model.crc().value(), 0), model + ": combine() of the empty second data has to give the first value");
        }
        CrcModel model = CrcModel.of("CRC-32");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> model.combine(0x0L, 0x0L, -1),
                "A negative length of the second data has to be rejected");
        assertEquals("CrcModel::combine - length2 is negative: -1", exception.getMessage(), "Wrong message");
    }

    @Test
    void testCombineWithOtherParameters() {
        CRC crc = CrcModel.of("CRC-32").crc(CHECK, 0, 4);
        // other width, other polynomial, other reflection, other xorout
        for (String other : new String[]{"CRC-16/ARC", "CRC-32/ISCSI", "CRC-32/BZIP2", "CRC-32/JAMCRC"}) {
            CRC next = CrcModel.of(other).crc(CHECK, 4, 5);
            assertThrows(IllegalArgumentException.class, () -> crc.combine(next), "combine() with a CRC of " + other + " has to be rejected");
            CRC empty = CrcModel.of(other).crc();
            assertThrows(IllegalArgumentException.class, () -> crc.combine(empty), "combine() with an empty CRC of " + other + " has to be rejected");
        }
        // an alias has the same description
        CRC combined = crc.combine(CrcModel.of("PKZIP").crc(CHECK, 4, 5));
        assertEquals(CrcModel.of("CRC-32").check(), combined.value(), "combine() with a CRC of an alias has to give the CRC of both parts");
        assertEquals("CRC-32", combined.name(), "The combined CRC has to have the model of the first CRC");
        assertThrows(NullPointerException.class, () -> crc.combine(null), "combine(null) has to throw NullPointerException");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> crc.combine(0x0L, -1L), "A negative length has to be rejected");
        assertEquals("CRC::combine - length is negative: -1", exception.getMessage(), "Wrong message");
    }

    @Test
    void testUpdateAt4GiB() {
        byte[] tail = {0x31, 0x32};
        for (CrcModel model : models()) {
            long crc2 = model.check();
            long crc3 = model.crc(tail).value();
            // two parts of 2 GiB - 1 byte and 2 bytes more: exactly 4 GiB, where an int length wraps to 0
            CRC crc = model.crc(crc2, Integer.MAX_VALUE).combine(model.crc(crc3, Integer.MAX_VALUE)).update(tail);
            long expected = model.crc(model.crc(crc2, Integer.MAX_VALUE).combine(crc3, Integer.MAX_VALUE).value(), 1).update(tail).value();
            assertEquals(expected, crc.value(), model + ": the CRC after exactly 4 GiB has to be the CRC of the processed data");
            assertEquals(0x1_0000_0000L, crc.length(), model + ": the length has to be 4 GiB, not 0");
        }
    }

    @Test
    void testCombineWithPartOf2GiB() {
        int part = 1 << 30;
        for (CrcModel model : models()) {
            long crc2 = model.check();
            long crc3 = model.crc(CHECK, 0, 4).value();
            // two parts of 1 GiB: 2 GiB, more than an int length
            CRC part2 = model.crc(crc2, part).combine(model.crc(crc3, part));
            assertEquals(model.crc(crc2, part).combine(crc3, part).value(), part2.value(), model + ": wrong CRC of 2 GiB");
            assertEquals(2L * part, part2.length(), model + ": wrong length of 2 GiB");
            // "123456789" followed by the 2 GiB, the same as "123456789" followed by the two parts of 1 GiB
            long expected = model.crc(model.crc(CHECK).combine(crc2, part).value(), CHECK.length + part).combine(crc3, part).value();
            assertEquals(expected, model.crc(CHECK).combine(part2).value(), model + ": combine() with a CRC of 2 GiB has to give the CRC of both parts");
        }
    }

    @Test
    void testLengthBeyond4GiB() {
        CrcModel crc32 = CrcModel.of("CRC-32");
        // the CRC of 4 GiB - 1 bytes and one byte more: no large input is needed
        CRC crc = crc32.crc(0x12345678L, 0xFFFFFFFFL).update(new byte[1]);
        assertEquals(0x1_0000_0000L, crc.length(), "The length has to be 4 GiB");
        assertEquals(crc32.crc(0x12345678L, 1).update(new byte[1]).value(), crc.value(), "The CRC after 4 GiB has to continue from the given CRC");
        long[] lengths = {(1L << 31) - 1, 1L << 31, 1L << 32, (1L << 32) + 1, 0x7FF_FFFF_FFFFL, Long.MAX_VALUE - CHECK.length};
        for (CrcModel model : models()) {
            long crc2 = model.crc(CHECK, 0, 4).value();
            for (long length : lengths) {
                String sample = model + ", length " + length;
                CRC state = model.crc(model.check(), length);
                assertEquals(state, model.crc(state.value(), state.length()), sample + ": a saved CRC has to be restored");
                long combined = model.crc(CHECK).combine(crc2, length).value();
                assertEquals(combined, model.crc(CHECK).combine(model.crc(crc2, length)).value(), sample + ": combine(value, length) and combine(CRC) have to agree");
                assertEquals(CHECK.length + length, model.crc(CHECK).combine(model.crc(crc2, length)).length(), sample + ": wrong combined length");
            }
        }
    }

    @Test
    void testCombineFromLengthsOfParts() {
        long gib = 1L << 30;
        for (CrcModel model : models()) {
            long crc1 = model.crc(CHECK).value();
            long crc2 = model.crc(CHECK, 0, 4).value();
            long crc3 = model.crc(CHECK, 4, 5).value();
            // ("123456789" + 2 GiB) + 1 GiB as one step and as two steps of 1 GiB give the same CRC
            long twoGiB = model.crc(crc2, gib).combine(crc3, gib).value();
            long oneStep = model.crc(crc1, CHECK.length).combine(model.crc(twoGiB, 2 * gib).combine(crc3, gib).value(), 3 * gib).value();
            long twoSteps = model.crc(model.crc(model.crc(crc1, CHECK.length).combine(crc2, gib).value(), CHECK.length + gib)
                    .combine(crc3, gib).value(), CHECK.length + 2 * gib).combine(crc3, gib).value();
            assertEquals(twoSteps, oneStep, model + ": combine(value, length) of 3 GiB has to give the CRC of all parts");
        }
    }

    @Test
    void testLengthOverflow() {
        CrcModel model = CrcModel.of("CRC-32");
        CRC crc = model.crc(0x12345678L, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> crc.update(new byte[1]), "The length of update() has to exceed Long.MAX_VALUE");
        assertThrows(ArithmeticException.class, () -> crc.combine(0x0L, 1), "The length of combine(value, length) has to exceed Long.MAX_VALUE");
        CRC next = model.crc(new byte[1]);
        assertThrows(ArithmeticException.class, () -> crc.combine(next), "The length of combine(CRC) has to exceed Long.MAX_VALUE");
    }

    @Test
    void testValueWithBitsAboveTheWidth() {
        for (CrcModel model : models()) {
            // all bits above the width set, as by the sign extension of a negative int
            long highBits = ~CrcParameters.mask(model.parameters().width());
            long crc1234 = model.crc(CHECK, 0, 4).value();
            long crc56789 = model.crc(CHECK, 4, 5).value();
            CRC crc = model.crc(crc1234 | highBits, 4);
            assertEquals(crc1234, crc.value(), model + ": crc(value, length) has to ignore the bits above the width");
            assertEquals(model.check(), crc.update(CHECK, 4, 5).value(), model + ": wrong update() after crc(value, length) with bits above the width");
            assertEquals(model.check(), crc.combine(model.crc(CHECK, 4, 5)).value(), model + ": wrong combine() after crc(value, length) with bits above the width");
            assertEquals(model.check(), model.crc(CHECK, 0, 4).combine(crc56789 | highBits, 5).value(), model + ": combine(value, length) has to ignore the bits above the width");
            assertEquals(crc56789, model.crc().combine(crc56789 | highBits, 5).value(), model + ": combine(value, length) of the empty data has to ignore the bits above the width");
            assertEquals(model.check(), model.combine(crc1234 | highBits, crc56789 | highBits, 5), model + ": combine() of the model has to ignore the bits above the width");
            assertEquals(crc1234, model.combine(crc1234 | highBits, crc56789 | highBits, 0), model + ": combine() of the model has to ignore the bits above the width of the first value");
        }
    }

    @Test
    void testValueKeptInIntAndByte() throws IOException {
        byte[] tail = {'x'};
        CrcModel crc32 = CrcModel.of("CRC-32");
        // a CRC-32 written as int and read back: the negative int is sign-extended to a long
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt((int) crc32.crc(CHECK).value());
        int stored = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())).readInt();
        long expected = crc32.crc(CHECK).update(tail).value();
        assertEquals(crc32.check(), crc32.crc(stored, CHECK.length).value(), "CRC-32 kept in an int: wrong value");
        assertEquals(expected, crc32.crc(stored, CHECK.length).update(tail).value(), "CRC-32 kept in an int: wrong update()");
        assertEquals(expected, crc32.crc(stored, CHECK.length).combine(crc32.crc(tail)).value(), "CRC-32 kept in an int: wrong combine(CRC)");
        int storedTail = (int) crc32.crc(tail).value();
        assertEquals(expected, crc32.crc(CHECK).combine(storedTail, tail.length).value(), "CRC-32 kept in an int: wrong combine(value, length)");
        assertEquals(expected, crc32.combine(stored, storedTail, tail.length), "CRC-32 kept in an int: wrong combine() of the model");
        // a CRC-8 kept in a byte
        CrcModel crc8 = CrcModel.of("CRC-8");
        byte stored8 = (byte) crc8.crc(CHECK).value();
        long expected8 = crc8.crc(CHECK).update(tail).value();
        assertEquals(expected8, crc8.crc(stored8, CHECK.length).update(tail).value(), "CRC-8 kept in a byte: wrong update()");
        assertEquals(expected8, crc8.crc(stored8, CHECK.length).combine(crc8.crc(tail)).value(), "CRC-8 kept in a byte: wrong combine(CRC)");
    }

    @Test
    void testBounds() {
        CrcModel model = CrcModel.of("CRC-32");
        CRC crc = model.crc(CHECK);
        byte[] data = new byte[16];
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> crc.update(data, 5, 12), "The bytes beyond the buffer have to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> crc.update(data, -1, 8), "A negative offset has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> crc.update(data, 0, -1), "A negative length has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> crc.update(data, 17, 0), "An offset beyond the buffer has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> crc.update(data, Integer.MAX_VALUE, 2), "An overflowing end has to be rejected");
        ArrayIndexOutOfBoundsException exception = assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.crc(data, 10, 7),
                "The bytes beyond the buffer have to be rejected");
        assertEquals("CRC::update - offset 10 and length 7 out of the buffer length 16", exception.getMessage(), "Wrong message");
        assertSame(crc, crc.update(data, 16, 0), "0 bytes at the end of the buffer are no data");
        assertThrows(NullPointerException.class, () -> crc.update((byte[]) null), "update(null) has to throw NullPointerException");
        assertThrows(NullPointerException.class, () -> crc.update(null, 0, 0), "update(null, 0, 0) has to throw NullPointerException");
        assertThrows(NullPointerException.class, () -> model.crc((byte[]) null), "crc(null) has to throw NullPointerException");
        assertEquals(model.check(), crc.value(), "A rejected update has not got to change the CRC");
        assertEquals(CHECK.length, crc.length(), "A rejected update has not got to change the CRC");
    }

    @Test
    // assertNotEquals(a, b) calls a.equals(b): the object under test has to be the first argument
    @SuppressWarnings("java:S3415")
    void testEqualsAndHashCode() {
        CrcModel model = CrcModel.of("CRC-32");
        CRC crc = model.crc(CHECK);
        assertEquals(crc, crc, "A CRC has to be equal to itself");
        assertEquals(crc, model.crc(CHECK), "CRCs of the same model over the same data have to be equal");
        assertEquals(crc.hashCode(), model.crc(CHECK).hashCode(), "Equal CRCs have to have the same hash code");
        assertEquals(crc, model.crc(crc.value(), crc.length()), "A restored CRC has to be equal");
        assertNotEquals(crc, null, "A CRC is not equal to null");
        assertNotEquals(crc, model, "A CRC is not equal to its model");
        assertNotEquals(crc, model.crc(crc.value() ^ 1, CHECK.length), "CRCs with different values are not equal");
        assertNotEquals(crc, model.crc(crc.value(), CHECK.length + 1), "CRCs with different lengths are not equal");
        assertNotEquals(crc, CrcModel.of("PKZIP").crc(CHECK), "CRCs of an alias and of its model are not equal");
        assertEquals(model.crc().hashCode(), CrcModel.of("CRC-32").crc().hashCode(), "Equal empty CRCs have to have the same hash code");
    }

    @Test
    void testToString() {
        assertEquals("CRC-64/XZ[crc:0x995dc9bbdf1939fa,len:9]", CrcModel.of("CRC-64/XZ").crc(CHECK).toString(), "Wrong format of a 64-bit CRC");
        assertEquals("CRC-32[crc:0xcbf43926,len:9]", CrcModel.of("CRC-32").crc(CHECK).toString(), "Wrong format of an alias");
        assertEquals("CRC-16/ARC[crc:0xbb3d,len:9]", CrcModel.of("CRC-16/ARC").crc(CHECK).toString(), "Wrong format of CRC-16/ARC");
        assertEquals("CRC-3/GSM[crc:0x4,len:9]", CrcModel.of("CRC-3/GSM").crc(CHECK).toString(), "Wrong format of CRC-3/GSM");
        assertEquals("CRC-32[crc:0x00000000,len:0]", CrcModel.of("CRC-32").crc().toString(), "toString() has to show all digits of the width");
        assertEquals("CRC-12/P123_I0[crc:0x" + Long.toHexString(CrcReference.crc(CrcParameters.of(12, 0x123L, 0x0L, false, false, 0x0L), CHECK)) + ",len:9]",
                CrcModel.of(12, 0x123L, 0x0L, false, false, 0x0L).crc(CHECK).toString(), "Wrong format of other parameters");
        List<CrcModel> models = modelsOfAllNames();
        Set<String> strings = models.stream().map(model -> model.crc(CHECK).toString()).collect(Collectors.toSet());
        assertEquals(models.size(), strings.size(), "The CRCs of different names have to have different strings");
    }

    @Test
    void testNameOfAlias() {
        CRC crc = CrcModel.of("PKZIP").crc(CHECK);
        assertEquals("PKZIP", crc.name(), "The CRC has to have the name its model was chosen by");
        assertEquals("PKZIP", crc.update(CHECK).name(), "An updated CRC has to keep the name");
        assertEquals("PKZIP", crc.combine(CrcModel.of("CRC-32").crc(CHECK)).name(), "A combined CRC has to keep the name of the first CRC");
        assertEquals("CRC-32/ISO-HDLC", crc.model().description().name(), "The description has to have the name of the algorithm");
    }

}
