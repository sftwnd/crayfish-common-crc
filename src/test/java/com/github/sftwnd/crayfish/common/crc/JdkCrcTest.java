/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The calculation by the classes of the JDK: the models of the class of the JDK against the models of the lookup tables
 * in one JVM. The unit tests run with the class directory, where JdkCrcs is the version of Java 8 on every Java version,
 * which knows java.util.zip.CRC32 only; the property and java.util.zip.CRC32C of Java 9+ are tested by JdkIT with the jar
 */
class JdkCrcTest {

    // the bytes before the position and after the limit of a buffer, which have not got to be calculated
    private static final int BEFORE = 5;
    private static final int AFTER = 3;

    private static final byte[] DATA = new byte[10000];

    static {
        new Random(110).nextBytes(DATA);
    }

    // the length from which java.util.zip.CRC32 calculates, the lookup tables below it
    private static final int MIN_LENGTH = minLength();

    private static int minLength() {
        JdkCrc<?> crc32 = JdkCrcs.of(JdkCrc.CRC32_POLY);
        assertNotNull(crc32, "CRC32 is a class of every Java version");
        return crc32.minLength;
    }

    // the parameters that java.util.zip.CRC32 calculates: CRC-32/ISO-HDLC, CRC-32/JAMCRC, and its polynomial with other
    // init, refout and xorout
    private static List<CrcParameters> parametersOfCrc32() {
        return Arrays.asList(
                StandardCrc.CRC_32_ISO_HDLC.parameters(),
                StandardCrc.CRC_32_JAMCRC.parameters(),
                CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL),
                CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x12345678L, true, true, 0x0L));
    }

    // the length bytes of the data from the offset in a buffer, between the other bytes before and after them
    private static ByteBuffer buffer(boolean direct, boolean readOnly, int offset, int length) {
        int capacity = BEFORE + length + AFTER;
        ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
        byte[] other = new byte[capacity];
        Arrays.fill(other, (byte) 0x5A);
        buffer.put(other);
        buffer.position(BEFORE);
        buffer.put(DATA, offset, length);
        buffer.limit(BEFORE + length);
        buffer.position(BEFORE);
        return readOnly ? buffer.asReadOnlyBuffer() : buffer;
    }

    @Test
    void testProperty() {
        assertTrue(CrcDescription.jdk(name -> CrcDescription.JDK_PROPERTY.equals(name) ? "true" : null), "The property of the name has to be read");
        for (String value : new String[]{"true", "TRUE", " True "}) {
            assertTrue(CrcDescription.jdk(name -> value), "\"" + value + "\" has to turn the classes of the JDK on");
        }
        for (String value : new String[]{"false", "", "yes", "1", "jdk"}) {
            assertFalse(CrcDescription.jdk(name -> value), "\"" + value + "\" has to keep the lookup tables of the library");
        }
        assertFalse(CrcDescription.jdk(name -> null), "The lookup tables of the library have to be the default");
        assertFalse(CrcDescription.jdk(name -> {
            throw new SecurityException("no access to the property " + name);
        }), "A property that cannot be read has to give the default");
    }

    @Test
    void testDefaultWithoutTheProperty() {
        assumeTrue(System.getProperty(CrcDescription.JDK_PROPERTY) == null, "the property is set");
        assertFalse(CrcDescription.isJdk(), "Without the property the default has to be the lookup tables of the library");
        for (String name : new String[]{"CRC-32", "CRC-32/JAMCRC", "CRC-32C"}) {
            assertFalse(CrcModel.of(name).isJdk(), name + ": without the property the lookup tables of the library have to calculate");
        }
    }

    @Test
    void testParametersOfTheJdk() {
        for (CrcParameters parameters : parametersOfCrc32()) {
            assertTrue(CrcModel.of(parameters, true).isJdk(), parameters + ": CRC32 has to calculate a reflected CRC-32 of its polynomial");
            assertFalse(CrcModel.of(parameters, false).isJdk(), parameters + ": the model of the lookup tables has to calculate with the lookup tables");
        }
        // not reflected, another polynomial, another width; CRC-32C: the version of JdkCrcs of Java 8 knows no CRC32C
        List<CrcParameters> others = Arrays.asList(
                StandardCrc.CRC_32_BZIP2.parameters(),
                StandardCrc.CRC_32_MPEG_2.parameters(),
                StandardCrc.CRC_32_CKSUM.parameters(),
                StandardCrc.CRC_32_AUTOSAR.parameters(),
                CrcParameters.of(31, JdkCrc.CRC32_POLY, 0x7FFFFFFFL, true, true, 0x7FFFFFFFL),
                CrcParameters.of(33, JdkCrc.CRC32_POLY, 0x1FFFFFFFFL, true, true, 0x1FFFFFFFFL),
                StandardCrc.CRC_16_ARC.parameters(),
                StandardCrc.CRC_32_ISCSI.parameters());
        for (CrcParameters parameters : others) {
            assertFalse(CrcModel.of(parameters, true).isJdk(), parameters + ": no class of the JDK of Java 8 calculates the CRC");
            assertSame(CrcModel.of(parameters, false), CrcModel.of(parameters, true), parameters + ": the model of the lookup tables has to be given");
        }
    }

    @Test
    void testArrays() {
        for (CrcParameters parameters : parametersOfCrc32()) {
            CrcEngine engine = CrcModel.of(parameters, true).engine();
            // the lookup tables below the minimal length of CRC32, CRC32 from it on
            for (int offset : new int[]{0, 1, 3, 7}) {
                for (int length = 1; length <= 2 * MIN_LENGTH + 9; length++) {
                    assertEquals(CrcReference.crc(parameters, DATA, offset, length), engine.update(engine.empty(), DATA, offset, length),
                            parameters + ": wrong CRC of " + length + " bytes at the offset " + offset);
                }
            }
            for (int length : new int[]{255, 256, 4096, DATA.length - 5}) {
                assertEquals(CrcReference.crc(parameters, DATA, 5, length), engine.update(engine.empty(), DATA, 5, length),
                        parameters + ": wrong CRC of " + length + " bytes");
            }
        }
    }

    @Test
    void testContinuedFromAnyRegister() {
        Random random = new Random(32);
        // the register of a new checksum of the JDK, which needs no difference in the first 4 bytes, the other edges and
        // random registers
        long[] edges = {0xFFFFFFFFL, 0x0L, 0x1L, 0x80000000L, 0x7FFFFFFFL, 0xFFFFFFFEL, 0xFF000000L, 0x000000FFL};
        for (CrcParameters parameters : parametersOfCrc32()) {
            CrcEngine engine = CrcModel.of(parameters, true).engine();
            CrcEngine tables = CrcModel.of(parameters, false).engine();
            for (int i = 0; i < 2000; i++) {
                long register = i < edges.length ? edges[i] : random.nextInt() & 0xFFFFFFFFL;
                long value = tables.value(register);
                int length = MIN_LENGTH + random.nextInt(300);
                int offset = random.nextInt(DATA.length - length);
                assertEquals(tables.update(value, DATA, offset, length), engine.update(value, DATA, offset, length),
                        parameters + ": wrong CRC of " + length + " bytes continued from the register 0x" + Long.toHexString(register));
            }
        }
    }

    @Test
    void testPartsAndSingleBytes() {
        // parts of the lookup tables and of CRC32 one after another, and single bytes, which the lookup tables calculate
        int[] parts = {100, 1, 1, MIN_LENGTH - 1, MIN_LENGTH, 1, 5000, 3, 1000};
        for (CrcParameters parameters : parametersOfCrc32()) {
            CrcEngine engine = CrcModel.of(parameters, true).engine();
            long value = engine.empty();
            int offset = 0;
            for (int part : parts) {
                value = part == 1 ? engine.update(value, DATA[offset]) : engine.update(value, DATA, offset, part);
                offset += part;
                assertEquals(CrcReference.crc(parameters, DATA, 0, offset), value, parameters + ": wrong CRC of the first " + offset + " bytes in parts");
            }
        }
    }

    @Test
    void testByteBuffers() {
        int[] lengths = {1, MIN_LENGTH - 1, MIN_LENGTH, 100, 4095, 4096, 4097, 9000};
        for (CrcParameters parameters : parametersOfCrc32()) {
            CrcEngine engine = CrcModel.of(parameters, true).engine();
            // the value of the first 9 bytes, the buffer the next bytes
            long first = engine.update(engine.empty(), DATA, 0, 9);
            for (int length : lengths) {
                for (boolean direct : new boolean[]{false, true}) {
                    for (boolean readOnly : new boolean[]{false, true}) {
                        String message = parameters + ", " + length + " bytes of a " + (readOnly ? "read-only " : "") + (direct ? "direct" : "heap") + " buffer";
                        ByteBuffer buffer = buffer(direct, readOnly, 9, length);
                        assertEquals(CrcReference.crc(parameters, DATA, 0, 9 + length), engine.update(first, buffer), message + ": wrong CRC after other data");
                        assertEquals(BEFORE + length, buffer.limit(), message + ": the limit has not got to change");
                        assertEquals(buffer.limit(), buffer.position(), message + ": the position has to be advanced to the limit");
                        buffer = buffer(direct, readOnly, 9, length);
                        assertEquals(CrcReference.crc(parameters, DATA, 9, length), engine.update(engine.empty(), buffer), message + ": wrong CRC");
                        assertEquals(buffer.limit(), buffer.position(), message + ": the position has to be advanced to the limit");
                    }
                }
            }
        }
    }

}
