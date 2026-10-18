/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.zip.CheckedInputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check of a codeword, the data followed by its CRC: valid codewords of every model of the catalogue and of other
 * parameters, in the byte order of the model and in both byte orders given, every single flipped bit, codewords
 * shorter than the CRC, parts of arrays and buffers, chunks of PNG. The codewords are made with the CRC of the
 * reference, without the code of the library. The unit tests run with the class directory,
 * where java.util.zip.CRC32 calculates the models of the JDK of CRC-32; CodewordIT tests CRC32C with the jar
 */
class CodewordTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the lengths of the data: around the start of the 8-byte loop (24 bytes), the lengths of java.util.zip.CRC32
    // (64 and 128 bytes) and the parts of a buffer without an accessible array (4096 bytes)
    private static final int[] LENGTHS = {0, 1, 7, 8, 9, 23, 24, 25, 63, 64, 65, 127, 128, 129, 4095, 4096, 4097};

    // the lengths of the data of the codewords with a flipped bit: the empty data, short data and data of the classes
    // of the JDK
    private static final int[] FLIPPED_LENGTHS = {0, 13, 130};

    // the bytes before and after a codeword in an array or a buffer, which have not got to be checked
    private static final int BEFORE = 5;
    private static final int AFTER = 3;

    private static final ByteOrder[] ORDERS = {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN};

    private static final byte[] DATA = new byte[5000];

    static {
        new Random(125).nextBytes(DATA);
    }

    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            models.add(standard.model());
        }
        // parameter sets outside the catalogue: widths that are not a multiple of 8, refin != refout
        models.add(CrcModel.of(1, 0x1L, 0x0L, false, false, 0x1L));
        models.add(CrcModel.of(3, 0x5L, 0x7L, false, false, 0x0L));
        models.add(CrcModel.of(5, 0x15L, 0x1FL, false, true, 0x3L));
        models.add(CrcModel.of(12, 0x80FL, 0xABCL, false, true, 0x5A5L));
        models.add(CrcModel.of(13, 0x1CF5L, 0x0L, true, false, 0x1FFFL));
        models.add(CrcModel.of(16, 0x1021L, 0xFFFFL, true, false, 0x1234L));
        models.add(CrcModel.of(21, 0x102899L, 0x1FFFFFL, true, false, 0x1L));
        models.add(CrcModel.of(40, 0x0004820009L, 0x12345L, true, true, 0x5A5A5A5A5AL));
        models.add(CrcModel.of(61, 0x0123456789ABCDEFL, 0x1FEDCBA987654321L, true, false, 0x0F0F0F0F0F0F0F0FL));
        models.add(CrcModel.of(64, 0x42F0E1EBA9EA3693L, 0x1L, false, true, 0x0L));
        // CRC-32 and CRC-32C by the lookup tables and by the classes of the JDK, and the polynomial of CRC32 with
        // refin != refout
        models.add(CrcModel.of("CRC-32", false));
        models.add(CrcModel.of("CRC-32", true));
        models.add(CrcModel.of("CRC-32C", false));
        models.add(CrcModel.of("CRC-32C", true));
        models.add(CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL), true));
        return models;
    }

    // the first length bytes of the data followed by their CRC of the reference in (width + 7) / 8 bytes, the least
    // significant byte first if refout, the most significant byte first otherwise
    private static byte[] codeword(CrcParameters parameters, int length) {
        return codeword(parameters, length, parameters.refout() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }

    // the first length bytes of the data followed by their CRC of the reference in (width + 7) / 8 bytes in the byte
    // order given
    private static byte[] codeword(CrcParameters parameters, int length, ByteOrder order) {
        int crcLength = (parameters.width() + 7) / 8;
        long crc = CrcReference.crc(parameters, DATA, 0, length);
        byte[] codeword = Arrays.copyOf(DATA, length + crcLength);
        for (int i = 0; i < crcLength; i++) {
            codeword[length + i] = (byte) (crc >>> (Byte.SIZE * (order == ByteOrder.LITTLE_ENDIAN ? i : crcLength - 1 - i)));
        }
        return codeword;
    }

    private static ByteOrder other(ByteOrder order) {
        return order == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
    }

    // the kinds of buffers: the codeword between the position and the limit, other bytes before and after it
    private enum Kind {

        HEAP(ByteBuffer::allocate),
        // a slice of a wrapped array: the array offset is not 0
        SLICE(capacity -> {
            ByteBuffer wrapped = ByteBuffer.wrap(new byte[capacity + 7]);
            wrapped.position(7);
            return wrapped.slice();
        }),
        READ_ONLY(ByteBuffer::allocate),
        DIRECT(ByteBuffer::allocateDirect);

        private final IntFunction<ByteBuffer> allocate;

        Kind(IntFunction<ByteBuffer> allocate) {
            this.allocate = allocate;
        }

        ByteBuffer buffer(byte[] codeword) {
            ByteBuffer buffer = allocate.apply(BEFORE + codeword.length + AFTER);
            byte[] other = new byte[BEFORE + codeword.length + AFTER];
            Arrays.fill(other, (byte) 0x5A);
            buffer.put(other);
            buffer.position(BEFORE);
            buffer.put(codeword);
            buffer.limit(BEFORE + codeword.length);
            buffer.position(BEFORE);
            return this == READ_ONLY ? buffer.asReadOnlyBuffer() : buffer;
        }

    }

    private static void assertRead(ByteBuffer buffer, int length, String message) {
        assertEquals(BEFORE + length, buffer.limit(), message + ": the limit has not got to change");
        assertEquals(buffer.limit(), buffer.position(), message + ": the position has to be advanced to the limit");
    }

    @Test
    void testByteOrder() {
        byte[] modbus = Arrays.copyOf(CHECK, CHECK.length + 2);
        modbus[9] = 0x37;
        modbus[10] = 0x4B;
        assertTrue(CrcModel.of("CRC-16/MODBUS").isValid(modbus), "The CRC of CRC-16/MODBUS (refout) has to follow the data with its low byte first");
        modbus[9] = 0x4B;
        modbus[10] = 0x37;
        assertFalse(CrcModel.of("CRC-16/MODBUS").isValid(modbus), "The CRC of CRC-16/MODBUS has not got to follow the data with its high byte first");
        byte[] xmodem = Arrays.copyOf(CHECK, CHECK.length + 2);
        xmodem[9] = 0x31;
        xmodem[10] = (byte) 0xC3;
        assertTrue(CrcModel.of("CRC-16/XMODEM").isValid(xmodem), "The CRC of CRC-16/XMODEM (not refout) has to follow the data with its high byte first");
        byte[] umts = Arrays.copyOf(CHECK, CHECK.length + 2);
        umts[9] = (byte) 0xAF;
        umts[10] = 0x0D;
        assertTrue(CrcModel.of("CRC-12/UMTS").isValid(umts), "The CRC 0xdaf of CRC-12/UMTS (refout, not refin) has to follow the data with its low byte first");
        umts[10] = 0x1D;
        assertFalse(CrcModel.of("CRC-12/UMTS").isValid(umts), "The unused high bits of the CRC bytes have to be 0");
        byte[] cksum = Arrays.copyOf(CHECK, CHECK.length + 4);
        System.arraycopy(new byte[]{0x76, 0x5E, 0x76, (byte) 0x80}, 0, cksum, 9, 4);
        assertTrue(CrcModel.of("CRC-32/CKSUM").isValid(cksum), "The CRC of CRC-32/CKSUM (not refout) has to follow the data with its high byte first");
    }

    // the check data followed by the bytes of its CRC in the byte order given, the other order than that of the model
    private static void assertCheckCodeword(String name, ByteOrder order, int... crc) {
        CrcModel model = CrcModel.of(name);
        byte[] expected = Arrays.copyOf(CHECK, CHECK.length + crc.length);
        for (int i = 0; i < crc.length; i++) {
            expected[CHECK.length + i] = (byte) crc[i];
        }
        String message = name + ", " + order;
        assertArrayEquals(expected, model.codeword(CHECK, order), message + ": wrong codeword of the check data");
        assertTrue(model.isValid(expected, order), message + ": isValid has to accept the codeword in the order given");
        assertFalse(model.isValid(expected), message + ": the CRC in the other order than that of the model has not got to be valid without the order");
    }

    @Test
    void testByteOrderGiven() {
        // CRC-16/MODBUS high byte first, CRC-16/XMODEM low byte first, CRC-12/UMTS high byte first with the unused high
        // bits 0, CRC-32/CKSUM low byte first
        assertCheckCodeword("CRC-16/MODBUS", ByteOrder.BIG_ENDIAN, 0x4B, 0x37);
        assertCheckCodeword("CRC-16/XMODEM", ByteOrder.LITTLE_ENDIAN, 0xC3, 0x31);
        assertCheckCodeword("CRC-12/UMTS", ByteOrder.BIG_ENDIAN, 0x0D, 0xAF);
        assertCheckCodeword("CRC-32/CKSUM", ByteOrder.LITTLE_ENDIAN, 0x80, 0x76, 0x5E, 0x76);
    }

    @Test
    void testPngChunks() {
        // the chunks IHDR of an image of 3 x 2 pixels (8 bits per sample, RGB) and IEND, as javax.imageio writes them:
        // the length of the data, the type, the data and the CRC-32 of the type and the data, most significant byte
        // first as every number of PNG; 0x1216f14d and 0xae426082 are the CRC-32 of zlib as well
        byte[] ihdr = {0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 3, 0, 0, 0, 2, 8, 2, 0, 0, 0, 0x12, 0x16, (byte) 0xF1, 0x4D};
        byte[] iend = {0, 0, 0, 0, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
        for (CrcModel crc32 : new CrcModel[]{CrcModel.of("CRC-32", false), CrcModel.of("CRC-32", true)}) {
            for (byte[] chunk : new byte[][]{ihdr, iend}) {
                int n = chunk.length - 12;
                String message = "The chunk " + new String(chunk, 4, 4, US_ASCII) + (crc32.isJdk() ? " of the JDK" : "");
                assertTrue(crc32.isValid(chunk, 4, n + 8, ByteOrder.BIG_ENDIAN),
                        message + ": the type and the data followed by their CRC-32 high byte first have to be valid big-endian");
                assertFalse(crc32.isValid(chunk, 4, n + 8), message + ": the CRC-32 high byte first has not got to be valid in the order of CRC-32");
                assertFalse(crc32.isValid(chunk, 4, n + 8, ByteOrder.LITTLE_ENDIAN), message + ": the CRC-32 high byte first has not got to be valid little-endian");
                assertTrue(crc32.isValid(ByteBuffer.wrap(chunk, 4, n + 8), ByteOrder.BIG_ENDIAN), message + " in a buffer has to be valid big-endian");
                byte[] stored = Arrays.copyOfRange(chunk, 4, chunk.length);
                assertArrayEquals(stored, crc32.codeword(Arrays.copyOfRange(chunk, 4, n + 8), ByteOrder.BIG_ENDIAN),
                        message + ": the type and the data have to be followed by their CRC-32 high byte first");
                assertArrayEquals(stored, crc32.codeword(chunk, 4, n + 4, ByteOrder.BIG_ENDIAN), message + ": wrong codeword of the type and the data in the chunk");
            }
        }
    }

    @Test
    void testByteOrdersGiven() {
        for (CrcModel model : models()) {
            ByteOrder natural = model.parameters().refout() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            for (ByteOrder order : ORDERS) {
                for (int length : LENGTHS) {
                    String message = model + (model.isJdk() ? " of the JDK" : "") + ", " + length + " bytes of data, " + order;
                    byte[] expected = codeword(model.parameters(), length, order);
                    byte[] codeword = model.codeword(Arrays.copyOf(DATA, length), order);
                    assertArrayEquals(expected, codeword, message + ": the data has to be followed by its CRC in the order given");
                    assertArrayEquals(expected, model.codeword(DATA, 0, length, order), message + ": wrong codeword of the first bytes of the data");
                    assertTrue(model.isValid(codeword, order), message + ": isValid has to accept the codeword in the order given");
                    byte[] inside = new byte[BEFORE + codeword.length + AFTER];
                    Arrays.fill(inside, (byte) 0x5A);
                    System.arraycopy(codeword, 0, inside, BEFORE, codeword.length);
                    assertTrue(model.isValid(inside, BEFORE, codeword.length, order), message + ": the codeword inside an array has to be valid");
                    for (Kind kind : Kind.values()) {
                        // the byte order of the buffer is not used
                        ByteBuffer buffer = kind.buffer(codeword).order(other(order));
                        assertTrue(model.isValid(buffer, order), message + " in a " + kind + " buffer of the other order: the codeword has to be valid");
                        assertRead(buffer, codeword.length, message + " in a " + kind + " buffer");
                    }
                    if (order == natural) {
                        assertArrayEquals(model.codeword(Arrays.copyOf(DATA, length)), codeword, message + ": the order of the model has to give the codeword of codeword(data)");
                        assertTrue(model.isValid(Kind.HEAP.buffer(codeword).order(other(order))),
                                message + ": isValid of a buffer has to take the order of the model, not that of the buffer");
                    } else if (!Arrays.equals(expected, codeword(model.parameters(), length))) {
                        // the CRC bytes of a palindrome, and a CRC of 1 byte, are the same in both orders
                        assertFalse(model.isValid(codeword), message + ": the CRC in the other order than that of the model has not got to be valid without the order");
                        assertFalse(model.isValid(codeword(model.parameters(), length), order),
                                message + ": the CRC in the order of the model has not got to be valid in the other order");
                    }
                }
            }
        }
    }

    @Test
    void testFlippedBitsInTheOrdersGiven() {
        for (CrcModel model : models()) {
            for (ByteOrder order : ORDERS) {
                for (int length : FLIPPED_LENGTHS) {
                    byte[] codeword = codeword(model.parameters(), length, order);
                    String message = model + ", " + length + " bytes of data, " + order;
                    for (int bit = 0; bit < codeword.length * Byte.SIZE; bit++) {
                        codeword[bit / Byte.SIZE] ^= (byte) (1 << (bit % Byte.SIZE));
                        assertFalse(model.isValid(codeword, order), message + ": the codeword with the bit " + bit + " flipped has not got to be valid");
                        codeword[bit / Byte.SIZE] ^= (byte) (1 << (bit % Byte.SIZE));
                    }
                    assertTrue(model.isValid(codeword, order), message + ": the codeword has to be valid again");
                }
            }
        }
    }

    @Test
    void testValidCodewords() {
        for (CrcModel model : models()) {
            for (int length : LENGTHS) {
                byte[] codeword = codeword(model.parameters(), length);
                String message = model + ", " + length + " bytes of data";
                assertTrue(model.isValid(codeword), message + ": the data followed by its CRC has to be a valid codeword");
                byte[] inside = new byte[BEFORE + codeword.length + AFTER];
                Arrays.fill(inside, (byte) 0x5A);
                System.arraycopy(codeword, 0, inside, BEFORE, codeword.length);
                assertTrue(model.isValid(inside, BEFORE, codeword.length), message + ": the codeword inside an array has to be valid");
            }
        }
    }

    @Test
    void testFlippedBits() {
        for (CrcModel model : models()) {
            for (int length : FLIPPED_LENGTHS) {
                byte[] codeword = codeword(model.parameters(), length);
                for (int bit = 0; bit < codeword.length * Byte.SIZE; bit++) {
                    codeword[bit / Byte.SIZE] ^= (byte) (1 << (bit % Byte.SIZE));
                    assertFalse(model.isValid(codeword), model + ", " + length + " bytes of data: the codeword with the bit " + bit
                            + " flipped has not got to be valid");
                    codeword[bit / Byte.SIZE] ^= (byte) (1 << (bit % Byte.SIZE));
                }
                assertTrue(model.isValid(codeword), model + ", " + length + " bytes of data: the codeword has to be valid again");
            }
        }
    }

    @Test
    void testCodewordsShorterThanTheCrc() {
        for (CrcModel model : models()) {
            byte[] empty = codeword(model.parameters(), 0);
            for (int length = 0; length < empty.length; length++) {
                String message = model + ", a codeword of " + length + " bytes";
                assertFalse(model.isValid(Arrays.copyOf(empty, length)), message + " has not got to be valid");
                assertFalse(model.isValid(empty, empty.length - length, length), message + " at the end of a valid one has not got to be valid");
                for (Kind kind : Kind.values()) {
                    ByteBuffer buffer = kind.buffer(Arrays.copyOf(empty, length));
                    assertFalse(model.isValid(buffer), message + " in a " + kind + " buffer has not got to be valid");
                    assertRead(buffer, length, message + " in a " + kind + " buffer");
                }
            }
        }
    }

    @Test
    void testByteBuffers() {
        for (CrcModel model : models()) {
            for (int length : LENGTHS) {
                byte[] codeword = codeword(model.parameters(), length);
                byte[] wrong = codeword.clone();
                // bit 0 of the last byte is a bit of the CRC in both byte orders
                wrong[wrong.length - 1] ^= 0x01;
                for (Kind kind : Kind.values()) {
                    String message = model + ", " + length + " bytes of data in a " + kind + " buffer";
                    ByteBuffer buffer = kind.buffer(codeword);
                    assertTrue(model.isValid(buffer), message + ": the codeword has to be valid");
                    assertRead(buffer, codeword.length, message);
                    buffer = kind.buffer(wrong);
                    assertFalse(model.isValid(buffer), message + ": the codeword with a wrong CRC has not got to be valid");
                    assertRead(buffer, codeword.length, message);
                }
            }
        }
    }

    @Test
    void testCrcOfAValidCodeword() throws IOException {
        int checked = 0;
        for (CrcModel model : models()) {
            CrcParameters parameters = model.parameters();
            if (parameters.width() % Byte.SIZE == 0 && parameters.refin() == parameters.refout()) {
                long expected = model.residue() ^ parameters.xorout();
                for (int length : LENGTHS) {
                    byte[] codeword = codeword(parameters, length);
                    String message = model + ", " + length + " bytes of data";
                    assertEquals(expected, model.value(codeword), message + ": the CRC of a valid codeword has to be residue ^ xorout");
                    try (CheckedInputStream in = new CheckedInputStream(new ByteArrayInputStream(codeword), model.checksum())) {
                        byte[] buffer = new byte[1000];
                        int total = 0;
                        for (int read = in.read(buffer, 0, buffer.length); read >= 0; read = in.read(buffer, 0, buffer.length)) {
                            total += read;
                        }
                        assertEquals(codeword.length, total, message + ": the stream has to read the codeword");
                        assertEquals(expected, in.getChecksum().getValue(), message + ": the checksum of a valid codeword has to be residue ^ xorout");
                    }
                }
                checked++;
            }
        }
        assertEquals(79 + 5, checked, "79 algorithms of the catalogue and 5 other models have got a width that is a multiple of 8 and refin = refout");
        CrcModel crc32 = CrcModel.of("CRC-32");
        assertEquals(0x2144DF1CL, crc32.residue() ^ crc32.parameters().xorout(), "Wrong CRC of a valid codeword of CRC-32");
    }

    @Test
    void testPartsOfAnArray() {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        byte[] data = new byte[16];
        assertFalse(model.isValid(data, 16, 0), "The empty part at the end of the data has to be accepted, and is shorter than the CRC");
        assertFalse(model.isValid(data, 0, 0), "The empty first part of the data has to be accepted, and is shorter than the CRC");
        assertTrue(model.isValid(data, 3, 2), "Two zero bytes are the codeword of the empty data of CRC-16/ARC");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.isValid(data, 0, 17), "More bytes than the data has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.isValid(data, 0, -1), "A negative length has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.isValid(data, 5, 12), "The bytes beyond the data have to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.isValid(data, -1, 8), "A negative offset has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.isValid(data, 17, 0), "An offset beyond the data has to be rejected");
        ArrayIndexOutOfBoundsException exception = assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> model.isValid(data, Integer.MAX_VALUE, 2), "An overflowing end has to be rejected");
        assertEquals("CrcModel::isValid - offset 2147483647 and length 2 out of the buffer length 16", exception.getMessage(), "Wrong message");
        NullPointerException nullCodeword = assertThrows(NullPointerException.class, () -> model.isValid((byte[]) null),
                "isValid(null) has to throw NullPointerException");
        assertEquals("CrcModel::isValid - codeword is null", nullCodeword.getMessage(), "Wrong message");
        assertThrows(NullPointerException.class, () -> model.isValid(null, 0, 0), "isValid(null, 0, 0) has to throw NullPointerException");
        nullCodeword = assertThrows(NullPointerException.class, () -> model.isValid((ByteBuffer) null), "isValid(null buffer) has to throw NullPointerException");
        assertEquals("CrcModel::isValid - codeword is null", nullCodeword.getMessage(), "Wrong message");
        // in a byte order given
        assertTrue(model.isValid(data, 3, 2, ByteOrder.BIG_ENDIAN), "Two zero bytes are the codeword of the empty data of CRC-16/ARC in both byte orders");
        assertFalse(model.isValid(data, 16, 0, ByteOrder.LITTLE_ENDIAN), "The empty part at the end of the data has to be accepted in the order given as well");
        ArrayIndexOutOfBoundsException ordered = assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.isValid(data, 5, 12, ByteOrder.BIG_ENDIAN),
                "The bytes beyond the data have to be rejected in the order given as well");
        assertEquals("CrcModel::isValid - offset 5 and length 12 out of the buffer length 16", ordered.getMessage(), "Wrong message");
        NullPointerException nullOrder = assertThrows(NullPointerException.class, () -> model.isValid(data, null), "isValid(codeword, null) has to throw NullPointerException");
        assertEquals("CrcModel::isValid - order is null", nullOrder.getMessage(), "Wrong message");
        nullOrder = assertThrows(NullPointerException.class, () -> model.isValid(data, 0, 0, null), "isValid(codeword, 0, 0, null) has to throw NullPointerException");
        assertEquals("CrcModel::isValid - order is null", nullOrder.getMessage(), "Wrong message");
        ByteBuffer buffer = ByteBuffer.wrap(data);
        nullOrder = assertThrows(NullPointerException.class, () -> model.isValid(buffer, null), "isValid(buffer, null) has to throw NullPointerException");
        assertEquals("CrcModel::isValid - order is null", nullOrder.getMessage(), "Wrong message");
        assertEquals(0, buffer.position(), "A null order has not got to read the buffer");
    }

    @Test
    void testCodewordOfData() {
        for (CrcModel model : models()) {
            for (int length : LENGTHS) {
                String message = model + (model.isJdk() ? " of the JDK" : "") + ", " + length + " bytes of data";
                byte[] data = Arrays.copyOf(DATA, length);
                byte[] codeword = model.codeword(data);
                assertArrayEquals(codeword(model.parameters(), length), codeword, message + ": the data has to be followed by its CRC");
                assertTrue(model.isValid(codeword), message + ": isValid has to accept the codeword");
                assertArrayEquals(Arrays.copyOf(DATA, length), data, message + ": the data has not got to change");
            }
        }
        // CRC-16/MODBUS low byte first, CRC-16/XMODEM high byte first, CRC-12/UMTS in 2 bytes with the unused high bits 0
        byte[] modbus = Arrays.copyOf(CHECK, CHECK.length + 2);
        modbus[9] = 0x37;
        modbus[10] = 0x4B;
        assertArrayEquals(modbus, CrcModel.of("CRC-16/MODBUS").codeword(CHECK), "Wrong codeword of CRC-16/MODBUS");
        byte[] xmodem = Arrays.copyOf(CHECK, CHECK.length + 2);
        xmodem[9] = 0x31;
        xmodem[10] = (byte) 0xC3;
        assertArrayEquals(xmodem, CrcModel.of("CRC-16/XMODEM").codeword(CHECK), "Wrong codeword of CRC-16/XMODEM");
        byte[] umts = Arrays.copyOf(CHECK, CHECK.length + 2);
        umts[9] = (byte) 0xAF;
        umts[10] = 0x0D;
        assertArrayEquals(umts, CrcModel.of("CRC-12/UMTS").codeword(CHECK), "Wrong codeword of CRC-12/UMTS (refout)");
    }

    @Test
    void testCodewordOfAPart() {
        CrcModel modbus = CrcModel.of("CRC-16/MODBUS");
        byte[] framed = Arrays.copyOf(DATA, 40);
        System.arraycopy(CHECK, 0, framed, 13, CHECK.length);
        assertArrayEquals(modbus.codeword(CHECK), modbus.codeword(framed, 13, CHECK.length),
                "The check data inside other data has to give the codeword of the check data");
        for (CrcModel model : models()) {
            String message = model + (model.isJdk() ? " of the JDK" : "");
            byte[] codeword = model.codeword(DATA, 7, 250);
            assertArrayEquals(model.codeword(Arrays.copyOfRange(DATA, 7, 257)), codeword, message + ": wrong codeword of a part of the data");
            assertTrue(model.isValid(codeword), message + ": isValid has to accept the codeword of a part");
            ByteOrder order = model.parameters().refout() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
            assertArrayEquals(model.crc().bytes(order), model.codeword(DATA, 5000, 0),
                    message + ": the codeword of the empty part at the end has to be the CRC of the empty data");
        }
        byte[] first = modbus.codeword(CHECK);
        assertNotSame(first, modbus.codeword(CHECK), "Every call has to give a new array");
    }

    @Test
    void testCodewordErrors() {
        CrcModel model = CrcModel.of("CRC-16/MODBUS");
        byte[] data = new byte[16];
        NullPointerException nullData = assertThrows(NullPointerException.class, () -> model.codeword(null), "codeword(null) has to throw NullPointerException");
        assertEquals("CrcModel::codeword - data is null", nullData.getMessage(), "Wrong message");
        NullPointerException nullPart = assertThrows(NullPointerException.class, () -> model.codeword(null, 0, 0),
                "codeword(null, offset, length) has to throw NullPointerException");
        assertEquals("CrcModel::codeword - data is null", nullPart.getMessage(), "Wrong message");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.codeword(data, -1, 8), "A negative offset has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.codeword(data, 0, -1), "A negative length has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.codeword(data, 17, 0), "An offset beyond the data has to be rejected");
        ArrayIndexOutOfBoundsException beyond = assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.codeword(data, 5, 12),
                "The bytes beyond the data have to be rejected");
        assertEquals("CrcModel::codeword - offset 5 and length 12 out of the buffer length 16", beyond.getMessage(), "Wrong message");
        // in a byte order given
        NullPointerException nullOrder = assertThrows(NullPointerException.class, () -> model.codeword(data, null), "codeword(data, null) has to throw NullPointerException");
        assertEquals("CrcModel::codeword - order is null", nullOrder.getMessage(), "Wrong message");
        nullOrder = assertThrows(NullPointerException.class, () -> model.codeword(data, 0, 0, null), "codeword(data, 0, 0, null) has to throw NullPointerException");
        assertEquals("CrcModel::codeword - order is null", nullOrder.getMessage(), "Wrong message");
        nullData = assertThrows(NullPointerException.class, () -> model.codeword(null, ByteOrder.BIG_ENDIAN),
                "codeword(null, order) has to throw NullPointerException");
        assertEquals("CrcModel::codeword - data is null", nullData.getMessage(), "Wrong message");
        ArrayIndexOutOfBoundsException ordered = assertThrows(ArrayIndexOutOfBoundsException.class, () -> model.codeword(data, 5, 12, ByteOrder.BIG_ENDIAN),
                "The bytes beyond the data have to be rejected in the order given as well");
        assertEquals("CrcModel::codeword - offset 5 and length 12 out of the buffer length 16", ordered.getMessage(), "Wrong message");
    }

    @Test
    void testModelsOfTheJdk() {
        // the class directory has the version of JdkCrcs of Java 8, which knows CRC32 only: CodewordIT tests CRC32C
        assertTrue(CrcModel.of("CRC-32", true).isJdk(), "java.util.zip.CRC32 has to calculate the model of the JDK of CRC-32");
        assertFalse(CrcModel.of("CRC-32C", true).isJdk(), "The lookup tables have to calculate CRC-32C with the class directory");
        CrcModel jdk = CrcModel.of("PKZIP", true);
        CrcModel tables = CrcModel.of("PKZIP", false);
        for (int length : LENGTHS) {
            byte[] codeword = codeword(jdk.parameters(), length);
            byte[] wrong = codeword.clone();
            // the least significant bit of the CRC
            wrong[length] ^= 0x01;
            String message = jdk + ", " + length + " bytes of data";
            assertTrue(jdk.isValid(codeword), message + ": the model of the JDK has to accept the codeword");
            assertTrue(tables.isValid(codeword), message + ": the model of the lookup tables has to accept the codeword");
            assertFalse(jdk.isValid(wrong), message + ": the model of the JDK has to reject a wrong CRC");
            assertFalse(tables.isValid(wrong), message + ": the model of the lookup tables has to reject a wrong CRC");
        }
    }

}
