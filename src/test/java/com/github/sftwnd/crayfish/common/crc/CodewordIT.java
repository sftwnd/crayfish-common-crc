/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CheckedInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The check of a codeword by the classes of the JDK: maven-failsafe-plugin runs the integration tests with the jar
 * of the project, so that Java 9+ loads the version of JdkCrcs in META-INF/versions/9, and java.util.zip.CRC32C
 * calculates the models of the JDK of CRC-32C; java.util.zip.CRC32 calculates those of CRC-32 on every Java version
 */
class CodewordIT {

    // around the minimal lengths of the classes of the JDK, 8 and 64 bytes on Java 9+ and 128 bytes on Java 8, and of
    // the parts of a buffer without an accessible array (4096 bytes)
    private static final int[] LENGTHS = {0, 1, 7, 8, 9, 63, 64, 65, 127, 128, 129, 4095, 4096, 4097};

    // the lengths of the data of the codewords with a flipped bit: the empty data, below and from the minimal lengths
    private static final int[] FLIPPED_LENGTHS = {0, 7, 8, 70, 130};

    // the CRC of the models in a codeword: 4 bytes
    private static final int CRC_LENGTH = 4;

    // the bytes before and after a codeword in an array or a buffer, which have not got to be checked
    private static final int BEFORE = 5;
    private static final int AFTER = 3;

    private static final byte[] DATA = new byte[5000];

    static {
        new Random(125).nextBytes(DATA);
    }

    private static boolean java9() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    // the models of the classes of the JDK: java.util.zip.CRC32 on every Java version, java.util.zip.CRC32C on Java 9+
    // (the lookup tables of the library on Java 8), by a name, an alias and by other parameters of the rule
    private static List<CrcModel> models() {
        return Arrays.asList(
                CrcModel.of("CRC-32/ISO-HDLC", true),
                CrcModel.of("PKZIP", true),
                CrcModel.of("CRC-32/JAMCRC", true),
                CrcModel.of("CRC-32C", true),
                CrcModel.of("CRC-32/CASTAGNOLI", true),
                CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL), true),
                CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32C_POLY, 0x12345678L, true, false, 0x0L), true));
    }

    // the first length bytes of the data followed by their CRC of the reference, the least significant byte first if
    // refout, the most significant byte first otherwise
    private static byte[] codeword(CrcParameters parameters, int length) {
        return codeword(parameters, length, parameters.refout() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }

    // the first length bytes of the data followed by their CRC of the reference in the byte order given
    private static byte[] codeword(CrcParameters parameters, int length, ByteOrder order) {
        long crc = CrcReference.crc(parameters, DATA, 0, length);
        byte[] codeword = Arrays.copyOf(DATA, length + CRC_LENGTH);
        for (int i = 0; i < CRC_LENGTH; i++) {
            codeword[length + i] = (byte) (crc >>> (Byte.SIZE * (order == ByteOrder.LITTLE_ENDIAN ? i : CRC_LENGTH - 1 - i)));
        }
        return codeword;
    }

    // the codeword in a buffer, between the other bytes before and after it
    private static ByteBuffer buffer(boolean direct, boolean readOnly, byte[] codeword) {
        int capacity = BEFORE + codeword.length + AFTER;
        ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
        byte[] other = new byte[capacity];
        Arrays.fill(other, (byte) 0x5A);
        buffer.put(other);
        buffer.position(BEFORE);
        buffer.put(codeword);
        buffer.limit(BEFORE + codeword.length);
        buffer.position(BEFORE);
        return readOnly ? buffer.asReadOnlyBuffer() : buffer;
    }

    @Test
    void testTheJarIsTested() {
        URL location = JdkCrcs.class.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(location.getPath().endsWith(".jar"), "The integration tests have to run with the jar: " + location);
    }

    @Test
    void testClassesOfTheJdk() {
        for (CrcModel model : models()) {
            assertEquals(model.parameters().poly() == JdkCrc.CRC32_POLY || java9(), model.isJdk(),
                    model + ": java.util.zip.CRC32 has to calculate the model on every Java version, java.util.zip.CRC32C on Java 9+");
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
                System.arraycopy(codeword, 0, inside, BEFORE, codeword.length);
                assertTrue(model.isValid(inside, BEFORE, codeword.length), message + ": the codeword inside an array has to be valid");
                for (boolean direct : new boolean[]{false, true}) {
                    for (boolean readOnly : new boolean[]{false, true}) {
                        String kind = message + " in a " + (readOnly ? "read-only " : "") + (direct ? "direct" : "heap") + " buffer";
                        ByteBuffer buffer = buffer(direct, readOnly, codeword);
                        assertTrue(model.isValid(buffer), kind + ": the codeword has to be valid");
                        assertEquals(buffer.limit(), buffer.position(), kind + ": the position has to be advanced to the limit");
                    }
                }
            }
        }
    }

    @Test
    void testCodewordOfData() {
        for (CrcModel model : models()) {
            for (int length : LENGTHS) {
                String message = model + ", " + length + " bytes of data";
                byte[] codeword = model.codeword(Arrays.copyOf(DATA, length));
                assertArrayEquals(codeword(model.parameters(), length), codeword, message + ": the data has to be followed by its CRC");
                assertTrue(model.isValid(codeword), message + ": isValid has to accept the codeword");
                assertArrayEquals(codeword, model.codeword(DATA, 0, length), message + ": wrong codeword of the first bytes of the data");
                for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                    byte[] ordered = model.codeword(Arrays.copyOf(DATA, length), order);
                    String kind = message + ", " + order;
                    assertArrayEquals(codeword(model.parameters(), length, order), ordered, kind + ": the data has to be followed by its CRC in the order given");
                    assertArrayEquals(ordered, model.codeword(DATA, 0, length, order), kind + ": wrong codeword of the first bytes of the data");
                    assertTrue(model.isValid(ordered, order), kind + ": isValid has to accept the codeword in the order given");
                    assertTrue(model.isValid(buffer(true, false, ordered), order), kind + ": isValid has to accept the codeword in a direct buffer");
                }
            }
        }
    }

    @Test
    void testFlippedBits() {
        for (CrcModel model : models()) {
            for (int length : FLIPPED_LENGTHS) {
                byte[] codeword = codeword(model.parameters(), length);
                ByteBuffer direct = buffer(true, false, codeword);
                for (int bit = 0; bit < codeword.length * Byte.SIZE; bit++) {
                    int index = bit / Byte.SIZE;
                    byte valid = codeword[index];
                    codeword[index] ^= (byte) (1 << (bit % Byte.SIZE));
                    direct.put(BEFORE + index, codeword[index]);
                    direct.position(BEFORE);
                    String message = model + ", " + length + " bytes of data: the codeword with the bit " + bit + " flipped";
                    assertFalse(model.isValid(codeword), message + " has not got to be valid");
                    assertFalse(model.isValid(direct), message + " in a direct buffer has not got to be valid");
                    codeword[index] = valid;
                    direct.put(BEFORE + index, valid);
                }
                direct.position(BEFORE);
                assertTrue(model.isValid(direct), model + ", " + length + " bytes of data: the codeword in a direct buffer has to be valid again");
            }
        }
    }

    @Test
    void testCodewordsShorterThanTheCrc() {
        for (CrcModel model : models()) {
            byte[] empty = codeword(model.parameters(), 0);
            for (int length = 0; length < CRC_LENGTH; length++) {
                String message = model + ", a codeword of " + length + " bytes";
                assertFalse(model.isValid(empty, 0, length), message + " has not got to be valid");
                ByteBuffer buffer = buffer(true, false, Arrays.copyOf(empty, length));
                assertFalse(model.isValid(buffer), message + " in a direct buffer has not got to be valid");
                assertEquals(buffer.limit(), buffer.position(), message + " in a direct buffer: the position has to be advanced to the limit");
            }
        }
    }

    @Test
    void testCrcOfAValidCodeword() throws IOException {
        for (CrcModel model : models()) {
            CrcParameters parameters = model.parameters();
            if (parameters.refin() == parameters.refout()) {
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
            }
        }
        CrcModel crc32 = CrcModel.of("CRC-32", true);
        assertEquals(0x2144DF1CL, crc32.residue() ^ crc32.parameters().xorout(), "Wrong CRC of a valid codeword of CRC-32");
        CrcModel crc32c = CrcModel.of("CRC-32C", true);
        assertEquals(0x48674BC7L, crc32c.residue() ^ crc32c.parameters().xorout(), "Wrong CRC of a valid codeword of CRC-32C");
    }

}
