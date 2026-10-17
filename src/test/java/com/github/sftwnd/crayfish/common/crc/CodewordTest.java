/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.zip.CheckedInputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check of a codeword, the data followed by its CRC: valid codewords of every model of the catalogue and of other
 * parameters, every single flipped bit, codewords shorter than the CRC, parts of arrays and buffers. The codewords are
 * made with the CRC of the reference, without the code of the library. The unit tests run with the class directory,
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
        int crcLength = (parameters.width() + 7) / 8;
        long crc = CrcReference.crc(parameters, DATA, 0, length);
        byte[] codeword = Arrays.copyOf(DATA, length + crcLength);
        for (int i = 0; i < crcLength; i++) {
            codeword[length + i] = (byte) (crc >>> (Byte.SIZE * (parameters.refout() ? i : crcLength - 1 - i)));
        }
        return codeword;
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
