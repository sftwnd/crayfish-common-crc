/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The calculation by the classes of the JDK with the property: maven-failsafe-plugin runs this test alone in a JVM
 * started with {@code -Dcom.github.sftwnd.crayfish.common.crc.jdk=true} (the execution jdk), with the jar of the project,
 * so that Java 9+ loads the version of JdkCrcs in META-INF/versions/9, which knows java.util.zip.CRC32C
 */
class JdkIT {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the parts of a stream: single bytes, parts shorter and longer than the minimal lengths of the classes of the JDK
    private static final int[] PARTS = {1, 7, 1, 8, 1, 1, 31, 32, 3, 63, 64, 100, 4096, 1, 257};

    // around the minimal lengths of the classes of the JDK: 8 and 32 bytes on Java 9+, 64 bytes on Java 8
    private static final int[] LENGTHS = {1, 7, 8, 31, 32, 63, 64, 100, 4096, 5000};

    // the bytes before the position and after the limit of a buffer, which have not got to be calculated
    private static final int BEFORE = 5;
    private static final int AFTER = 3;

    private static final byte[] DATA = new byte[10000];

    static {
        new Random(110).nextBytes(DATA);
    }

    private static boolean java9() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    // the models that a class of the JDK calculates: java.util.zip.CRC32 on every Java version, java.util.zip.CRC32C
    // on Java 9+ (the lookup tables of the library on Java 8), by a name, an alias and by other parameters of the rule
    private static List<CrcModel> models() {
        return Arrays.asList(
                CrcModel.of("CRC-32/ISO-HDLC"),
                CrcModel.of("PKZIP"),
                CrcModel.of("CRC-32/JAMCRC"),
                CrcModel.of("CRC-32C"),
                CrcModel.of("CRC-32/CASTAGNOLI"),
                CrcModel.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL),
                CrcModel.of(32, JdkCrc.CRC32C_POLY, 0x12345678L, true, true, 0x0L));
    }

    // the first length bytes of the data in a buffer, between the other bytes before and after them
    private static ByteBuffer buffer(boolean direct, boolean readOnly, int length) {
        int capacity = BEFORE + length + AFTER;
        ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
        byte[] other = new byte[capacity];
        Arrays.fill(other, (byte) 0x5A);
        buffer.put(other);
        buffer.position(BEFORE);
        buffer.put(DATA, 0, length);
        buffer.limit(BEFORE + length);
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
        assertEquals("true", System.getProperty(CrcDescription.JDK_PROPERTY), "The test has to run with " + CrcDescription.JDK_PROPERTY + "=true");
        assertTrue(CrcDescription.isJdk(), "The property has to make the classes of the JDK the default");
        for (String name : new String[]{"CRC-32/ISO-HDLC", "CRC-32", "PKZIP", "CRC-32/JAMCRC", "JAMCRC"}) {
            assertTrue(CrcModel.of(name).isJdk(), name + ": java.util.zip.CRC32 has to calculate the CRC on every Java version");
        }
        for (String name : new String[]{"CRC-32/ISCSI", "CRC-32C", "CRC-32/CASTAGNOLI"}) {
            assertEquals(java9(), CrcModel.of(name).isJdk(),
                    name + ": java.util.zip.CRC32C has to calculate the CRC on Java 9+, the lookup tables of the library on Java 8");
        }
        for (String name : new String[]{"CRC-32/BZIP2", "CRC-32/MPEG-2", "CRC-32/CKSUM", "CRC-32/AUTOSAR", "CRC-16/ARC", "CRC-64/XZ"}) {
            assertFalse(CrcModel.of(name).isJdk(), name + ": no class of the JDK calculates the CRC");
        }
    }

    @Test
    void testDefaultChangedByACall() {
        CrcModel jdk = CrcModel.of("CRC-32C");
        CrcDescription.setJdk(false);
        try {
            CrcModel tables = CrcModel.of("CRC-32C");
            assertFalse(tables.isJdk(), "After setJdk(false) a model has to be calculated by the lookup tables");
            assertEquals(java9(), jdk.isJdk(), "A model chosen before the change of the default has to keep its way");
            assertSame(jdk, tables.withJdk(true), "withJdk(true) has to give the model chosen before");
            assertEquals(java9(), CrcModel.of("CRC-32C", true).isJdk(), "java.util.zip.CRC32C has to calculate the model of the JDK on Java 9+");
            CRC byJdk = jdk.crc(DATA);
            CRC byTables = tables.crc(DATA);
            assertEquals(CrcReference.crc(jdk.parameters(), DATA), byJdk.value(), "Wrong value of the model of the JDK");
            assertEquals(byJdk, byTables, "The CRCs of both ways have to be equal");
            assertEquals(byJdk.hashCode(), byTables.hashCode(), "The CRCs of both ways have to have the same hash code");
        } finally {
            CrcDescription.setJdk(true);
        }
        assertSame(jdk, CrcModel.of("CRC-32C"), "After setJdk(true) the model of the JDK has to be given again");
    }

    @Test
    void testValues() {
        for (CrcModel model : models()) {
            CrcParameters parameters = model.parameters();
            assertEquals(CrcReference.crc(parameters, CHECK), model.value(CHECK), model + ": wrong check value");
            for (int offset : new int[]{0, 3}) {
                for (int length = 0; length <= 140; length++) {
                    long expected = CrcReference.crc(parameters, DATA, offset, length);
                    assertEquals(expected, model.value(DATA, offset, length), model + ": wrong value of " + length + " bytes at the offset " + offset);
                    assertEquals(expected, model.crc(DATA, offset, length).value(), model + ": wrong CRC of " + length + " bytes at the offset " + offset);
                }
            }
            long expected = CrcReference.crc(parameters, DATA);
            assertEquals(expected, model.value(DATA), model + ": wrong value of the data");
            // continued after the first part, resumed from its saved value and combined with the second part
            for (int split : new int[]{1, 9, 16, 100, 4096, DATA.length - 20}) {
                CRC first = model.crc(DATA, 0, split);
                int rest = DATA.length - split;
                String message = model + ", split at " + split;
                assertEquals(expected, first.update(DATA, split, rest).value(), message + ": wrong CRC continued after the first part");
                assertEquals(expected, model.crc(first.value(), split).update(DATA, split, rest).value(), message + ": wrong CRC resumed from the saved value");
                assertEquals(expected, first.combine(model.crc(DATA, split, rest)).value(), message + ": wrong combined CRC");
            }
        }
    }

    @Test
    void testCrc32OfTheJdk() {
        CRC32 jdk = new CRC32();
        jdk.update(DATA, 0, DATA.length);
        assertEquals(jdk.getValue(), CrcModel.of("CRC-32/ISO-HDLC").value(DATA), "CRC-32/ISO-HDLC has to give the value of java.util.zip.CRC32");
        assertEquals(~jdk.getValue() & 0xFFFFFFFFL, CrcModel.of("CRC-32/JAMCRC").value(DATA),
                "CRC-32/JAMCRC has to give the complement of the value of java.util.zip.CRC32");
    }

    @Test
    void testStreams() throws IOException {
        for (CrcModel model : models()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int offset = 0;
            try (CheckedOutputStream out = new CheckedOutputStream(bytes, model.checksum())) {
                for (int part : PARTS) {
                    // a part of one byte as a byte, the others as parts of the data
                    if (part == 1) {
                        out.write(DATA[offset]);
                    } else {
                        out.write(DATA, offset, part);
                    }
                    offset += part;
                    assertEquals(CrcReference.crc(model.parameters(), DATA, 0, offset), out.getChecksum().getValue(),
                            model + ": wrong value of the first " + offset + " bytes written in parts");
                }
            }
            assertArrayEquals(Arrays.copyOf(DATA, offset), bytes.toByteArray(), "The stream has to write the data");
            try (CheckedInputStream in = new CheckedInputStream(new ByteArrayInputStream(DATA), model.checksum())) {
                byte[] buffer = new byte[1000];
                int total = 0;
                for (int read = in.read(buffer, 0, buffer.length); read >= 0; read = in.read(buffer, 0, buffer.length)) {
                    total += read;
                }
                assertEquals(DATA.length, total, "The stream has to read the data");
                assertEquals(CrcReference.crc(model.parameters(), DATA), in.getChecksum().getValue(), model + ": wrong value of the data read");
            }
        }
    }

    @Test
    void testByteBuffers() {
        for (CrcModel model : models()) {
            CRC first = model.crc(CHECK);
            for (int length : LENGTHS) {
                byte[] after = new byte[CHECK.length + length];
                System.arraycopy(CHECK, 0, after, 0, CHECK.length);
                System.arraycopy(DATA, 0, after, CHECK.length, length);
                long expected = CrcReference.crc(model.parameters(), DATA, 0, length);
                long expectedAfter = CrcReference.crc(model.parameters(), after);
                for (boolean direct : new boolean[]{false, true}) {
                    for (boolean readOnly : new boolean[]{false, true}) {
                        String message = model + ", " + length + " bytes of a " + (readOnly ? "read-only " : "") + (direct ? "direct" : "heap") + " buffer";
                        ByteBuffer buffer = buffer(direct, readOnly, length);
                        assertEquals(expected, model.value(buffer), message + ": wrong value");
                        assertEquals(buffer.limit(), buffer.position(), message + ": the position has to be advanced to the limit");
                        assertEquals(expectedAfter, first.update(buffer(direct, readOnly, length)).value(), message + ": wrong CRC after other data");
                        CrcChecksum checksum = model.checksum();
                        checksum.update(CHECK);
                        checksum.update(buffer(direct, readOnly, length));
                        assertEquals(expectedAfter, checksum.getValue(), message + ": wrong value of the checksum");
                    }
                }
            }
        }
    }

}
