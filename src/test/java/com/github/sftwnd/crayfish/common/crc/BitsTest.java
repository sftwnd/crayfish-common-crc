/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CRC of data that is not a whole number of bytes, {@link CrcModel#valueOfBits(byte[], long)} and
 * {@link CrcModel#valueOfBits(long, int)}, against a calculation a bit at a time that does not use the code of the
 * library. The unit tests run with the class directory, where java.util.zip.CRC32 calculates the models of the JDK of its
 * polynomial and CRC-32C stays with the lookup tables; BitsIT tests java.util.zip.CRC32C with the jar
 */
class BitsTest {

    // longer than the minimal length of java.util.zip.CRC32 of Java 8, 128 bytes
    private static final byte[] DATA = new byte[200];

    static {
        new Random(129).nextBytes(DATA);
    }

    // the bits of a long at the edges: none, all, bit 0, bit 63, and both of them
    private static final long[] EDGES = {0x0L, -1L, 0x1L, 0x8000000000000000L, 0x8000000000000001L};

    // the models of the catalogue; parameter sets outside it with refin other than refout, of every kind of register
    // (up to 8 bits wide and not reflected, up to 32 bits wide, wider); CRC-32 and CRC-32C with the classes of the JDK
    // and with the lookup tables
    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            models.add(standard.model());
        }
        models.add(CrcModel.of(1, 0x1L, 0x1L, false, true, 0x0L));
        models.add(CrcModel.of(1, 0x1L, 0x0L, true, false, 0x1L));
        models.add(CrcModel.of(3, 0x5L, 0x7L, false, true, 0x0L));
        models.add(CrcModel.of(7, 0x45L, 0x0L, true, false, 0x7FL));
        models.add(CrcModel.of(13, 0x1CF5L, 0x0L, true, false, 0x1FFFL));
        models.add(CrcModel.of(32, 0x04C11DB7L, 0xFFFFFFFFL, false, true, 0x0L));
        models.add(CrcModel.of(40, 0x4820009L, 0x12345L, true, false, 0x0L));
        models.add(CrcModel.of(64, 0x42F0E1EBA9EA3693L, 0x1L, false, true, 0x0L));
        for (String name : new String[]{"CRC-32", "CRC-32C"}) {
            models.add(CrcModel.of(name, true));
            models.add(CrcModel.of(name, false));
        }
        models.add(CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x12345678L, true, false, 0x5A5A5A5AL), true));
        return models;
    }

    // the name of the model and its way, for the messages
    private static String name(CrcModel model) {
        return model.isJdk() ? model + " of the JDK" : model.toString();
    }

    /* the reference a bit at a time, without the code of the library: the CRC values of the first 0 to all bits by the
       Rocksoft model of the parameters, the value of the first i bits at the index i. The register of the width takes
       the bits one by one, as CrcReference takes the bits of a byte; the value of the first bits is the register after
       the last of them, reflected if refout and XOR-ed with xorout */
    static long[] reference(CrcParameters parameters, boolean[] bits) {
        int width = parameters.width();
        long top = 1L << (width - 1);
        long mask = top | (top - 1);
        long[] values = new long[bits.length + 1];
        long register = parameters.init();
        values[0] = value(parameters, register);
        for (int i = 0; i < bits.length; i++) {
            boolean feedback = bits[i] != ((register & top) != 0);
            register = (register << 1) & mask;
            if (feedback) {
                register ^= parameters.poly();
            }
            values[i + 1] = value(parameters, register);
        }
        return values;
    }

    // the CRC value of the register of the reference: reflected if refout, XOR-ed with xorout
    private static long value(CrcParameters parameters, long register) {
        long value = parameters.refout() ? Long.reverse(register) >>> (Long.SIZE - parameters.width()) : register;
        return value ^ parameters.xorout();
    }

    // the bits of the data in the order in which the model reads them: the most significant bit of a byte first, the
    // least significant bit first if refin
    static boolean[] bitsOf(byte[] data, boolean refin) {
        boolean[] bits = new boolean[8 * data.length];
        for (int i = 0; i < bits.length; i++) {
            int bit = refin ? i % 8 : 7 - i % 8;
            bits[i] = (((data[i / 8] & 0xff) >>> bit) & 1) != 0;
        }
        return bits;
    }

    // the count bits of the long in the order in which the model reads them: from bit count - 1 down to bit 0, or from
    // bit 0 up if refin
    static boolean[] bitsOf(long value, int count, boolean refin) {
        boolean[] bits = new boolean[count];
        for (int i = 0; i < count; i++) {
            bits[i] = ((value >>> (refin ? i : count - 1 - i)) & 1) != 0;
        }
        return bits;
    }

    // the count bits of the long in 8 bytes in the order of the model: from the most significant bit of the first byte
    // on, or from its least significant bit on if refin
    private static byte[] bytesOf(long value, int count, boolean refin) {
        ByteBuffer bytes = ByteBuffer.allocate(Long.BYTES);
        if (refin) {
            bytes.order(ByteOrder.LITTLE_ENDIAN).putLong(value);
        } else {
            bytes.putLong(count == 0 ? 0x0L : value << (Long.SIZE - count));
        }
        return bytes.array();
    }

    // the data with every bit after the first count bits inverted, in the order of the model: the low bits of the byte
    // of the last bits, or its high bits if refin, and the bytes after it
    private static byte[] invertedAfter(byte[] data, int count, boolean refin) {
        byte[] inverted = data.clone();
        int last = count / 8;
        if (last < inverted.length) {
            inverted[last] ^= (byte) (refin ? 0xFF << (count % 8) : 0xFF >>> (count % 8));
        }
        for (int i = last + 1; i < inverted.length; i++) {
            inverted[i] ^= (byte) 0xFF;
        }
        return inverted;
    }

    // writes the count bits of the value after the first position bits of the data, the most significant bit first
    private static void put(byte[] data, int position, long value, int count) {
        for (int i = 0; i < count; i++) {
            int bit = position + i;
            byte mask = (byte) (0x80 >>> (bit % 8));
            if (((value >>> (count - 1 - i)) & 1) != 0) {
                data[bit / 8] |= mask;
            } else {
                data[bit / 8] &= (byte) ~mask;
            }
        }
    }

    @Test
    void testEveryCount() {
        for (CrcModel model : models()) {
            long[] expected = reference(model.parameters(), bitsOf(DATA, model.parameters().refin()));
            for (int count = 0; count <= 8 * DATA.length; count++) {
                assertEquals(expected[count], model.valueOfBits(DATA, count), name(model) + ": wrong value of the first " + count + " bits");
            }
        }
    }

    @Test
    void testWholeBytes() {
        for (CrcModel model : models()) {
            for (int length = 0; length <= DATA.length; length++) {
                assertEquals(model.value(DATA, length), model.valueOfBits(DATA, 8L * length),
                        name(model) + ": the bits of " + length + " bytes have to give the value of the bytes");
            }
            assertEquals(model.crc().value(), model.valueOfBits(new byte[0], 0), name(model) + ": no bits have to give the value of the empty data");
        }
    }

    @Test
    void testLong() {
        Random random = new Random(64);
        for (CrcModel model : models()) {
            CrcParameters parameters = model.parameters();
            for (int i = 0; i < 10; i++) {
                long bits = i < EDGES.length ? EDGES[i] : random.nextLong();
                for (int count = 0; count <= Long.SIZE; count++) {
                    String message = name(model) + ", " + count + " bits of 0x" + Long.toHexString(bits);
                    long value = model.valueOfBits(bits, count);
                    assertEquals(reference(parameters, bitsOf(bits, count, parameters.refin()))[count], value, message + ": wrong value");
                    assertEquals(model.valueOfBits(bytesOf(bits, count, parameters.refin()), count), value,
                            message + ": the long has to give the value of the bits in an array");
                }
            }
            for (int b = 0; b < 256; b++) {
                assertEquals(model.value(new byte[]{(byte) b}), model.valueOfBits(b, 8), name(model) + ": 8 bits have to give the value of the byte " + b);
            }
        }
    }

    @Test
    void testBitsAboveTheCount() {
        Random random = new Random(65);
        byte[] data = Arrays.copyOf(DATA, 16);
        for (CrcModel model : models()) {
            for (int count = 0; count < Long.SIZE; count++) {
                long bits = random.nextLong();
                long mask = ~(-1L << count);
                long expected = model.valueOfBits(bits & mask, count);
                String message = name(model) + ", " + count + " bits of 0x" + Long.toHexString(bits);
                assertEquals(expected, model.valueOfBits(bits, count), message + ": the bits above the count have to be ignored");
                assertEquals(expected, model.valueOfBits(bits | ~mask, count), message + ": the bits above the count have to be ignored");
            }
            boolean refin = model.parameters().refin();
            for (int count = 0; count <= 8 * data.length; count++) {
                assertEquals(model.valueOfBits(data, count), model.valueOfBits(invertedAfter(data, count, refin), count),
                        name(model) + ": the bits of the data after the first " + count + " bits have to be ignored");
            }
        }
    }

    @Test
    void testUsbTokens() {
        // the CRC5 is sent from bit 0 of the value of CRC-5/USB on: written most significant bit first, the values are
        // 0x17, 0x1C, 0x0E and 0x14
        CrcModel usb = CrcModel.of("CRC-5/USB");
        assertEquals(0x1DL, usb.valueOfBits(0x15L | (0xEL << 7), 11), "Wrong CRC5 of the token of the address 0x15 and the endpoint 0xE");
        assertEquals(0x07L, usb.valueOfBits(0x3AL | (0xAL << 7), 11), "Wrong CRC5 of the token of the address 0x3A and the endpoint 0xA");
        assertEquals(0x0EL, usb.valueOfBits(0x70L | (0x4L << 7), 11), "Wrong CRC5 of the token of the address 0x70 and the endpoint 0x4");
        assertEquals(0x05L, usb.valueOfBits(0x710L, 11), "Wrong CRC5 of the start of the frame 0x710");
    }

    @Test
    void testCodewords() {
        // the CRC of bits followed by their CRC, in the order of the model, is the residue of the catalogue XOR-ed with
        // xorout, as a receiver checks a frame: 0x06 ^ 0x1F for CRC-5/USB, 0 for CRC-11/FLEXRAY and CRC-15/CAN
        Random random = new Random(129);
        CrcModel usb = CrcModel.of("CRC-5/USB");
        CrcModel flexRay = CrcModel.of("CRC-11/FLEXRAY");
        CrcModel can = CrcModel.of("CRC-15/CAN");
        for (int i = 0; i < 100; i++) {
            long token = random.nextInt(1 << 11);
            long tokenCrc = usb.valueOfBits(token, 11);
            assertEquals(0x06L ^ 0x1FL, usb.valueOfBits(token | (tokenCrc << 11), 16), "Wrong CRC of the USB token 0x" + Long.toHexString(token) + " and its CRC5");
            long header = random.nextInt(1 << 20);
            long headerCrc = flexRay.valueOfBits(header, 20);
            assertEquals(0x0L, flexRay.valueOfBits((header << 11) | headerCrc, 31), "Wrong CRC of the FlexRay header 0x" + Long.toHexString(header) + " and its CRC");
            // a CAN data frame: the 19 bits before the data and the data, the 15 bits of the CRC after them
            int count = 19 + 8 * random.nextInt(9);
            byte[] frame = new byte[(count + 15 + 7) / 8];
            random.nextBytes(frame);
            put(frame, count, can.valueOfBits(frame, count), 15);
            assertEquals(0x0L, can.valueOfBits(frame, count + 15L), "Wrong CRC of a CAN frame of " + count + " bits and its CRC");
        }
    }

    @Test
    void testLookupTables() {
        // parameters of their own, so that no other test has built their lookup tables
        CrcModel model = CrcModel.of(24, 0x864CFBL, 0xABCDEFL, true, false, 0x0L);
        long[] expected = reference(model.parameters(), bitsOf(DATA, true));
        long first = ByteBuffer.wrap(DATA).order(ByteOrder.LITTLE_ENDIAN).getLong();
        assertEquals(expected[61], model.valueOfBits(first, 61), "Wrong value of the first 61 bits of the data in a long");
        assertEquals(expected[7], model.valueOfBits(DATA, 7), "Wrong value of the first 7 bits of the data");
        assertFalse(model.engine().hasTables(), "The bits of a long and less than a byte have to be calculated without the lookup tables");
        assertEquals(expected[15], model.valueOfBits(DATA, 15), "Wrong value of the first 15 bits of the data");
        assertTrue(model.engine().hasTables(), "The whole bytes have to be calculated by the lookup tables");
    }

    @Test
    void testErrors() {
        CrcModel model = CrcModel.of("CRC-15/CAN");
        byte[] data = new byte[2];
        assertEquals(model.crc().value(), model.valueOfBits(data, 0), "No bits of the data have to be accepted");
        assertEquals(model.value(data), model.valueOfBits(data, 16), "All bits of the data have to be accepted");
        ArrayIndexOutOfBoundsException beyond = assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.valueOfBits(data, 17),
                "More bits than the data has to be rejected");
        assertEquals("CrcModel::valueOfBits - count 17 out of the 16 bits of the buffer length 2", beyond.getMessage(), "Wrong message");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.valueOfBits(new byte[0], 1), "A bit of the empty data has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.valueOfBits(data, Long.MAX_VALUE), "The largest count has to be rejected");
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class, () -> model.valueOfBits(data, -1L),
                "A negative count has to be rejected");
        assertEquals("CrcModel::valueOfBits - count is negative: -1", negative.getMessage(), "Wrong message");
        assertThrows(IllegalArgumentException.class, () -> model.valueOfBits(data, Long.MIN_VALUE), "A negative count has to be rejected");
        NullPointerException nullData = assertThrows(NullPointerException.class, () -> model.valueOfBits(null, 0),
                "valueOfBits(null, 0) has to throw NullPointerException");
        assertEquals("CrcModel::valueOfBits - data is null", nullData.getMessage(), "Wrong message");
        for (int count : new int[]{-1, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> model.valueOfBits(0x0L, count),
                    "The count " + count + " of a long has to be rejected");
            assertEquals("CrcModel::valueOfBits - count has to be in 0..64: " + count, exception.getMessage(), "Wrong message");
        }
    }

}
