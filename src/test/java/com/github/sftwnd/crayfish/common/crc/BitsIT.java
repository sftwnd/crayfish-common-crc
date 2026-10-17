/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CRC of data that is not a whole number of bytes calculated by the classes of the JDK: maven-failsafe-plugin runs
 * the integration tests with the jar of the project, so that Java 9+ loads the version of JdkCrcs in META-INF/versions/9,
 * which knows java.util.zip.CRC32C. The class of the JDK calculates the whole bytes from its minimal length on, the rest
 * of the bits are calculated a bit at a time; the expected values are those of the calculation a bit at a time of BitsTest
 */
class BitsIT {

    // longer than the minimal lengths of the classes of the JDK: 8 and 64 bytes on Java 9+, 128 bytes on Java 8
    private static final byte[] DATA = new byte[200];

    static {
        new Random(129).nextBytes(DATA);
    }

    private static boolean java9() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    // the models of the JDK by a name and by other parameters of the polynomials of the classes of the JDK, with refin
    // other than refout: java.util.zip.CRC32 on every Java version, java.util.zip.CRC32C on Java 9+ (the lookup tables
    // of the library on Java 8)
    private static List<CrcModel> models() {
        return Arrays.asList(
                CrcModel.of("CRC-32", true),
                CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x12345678L, true, false, 0x5A5A5A5AL), true),
                CrcModel.of("CRC-32C", true),
                CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32C_POLY, 0x12345678L, true, false, 0x5A5A5A5AL), true));
    }

    @Test
    void testTheJarIsTested() {
        URL location = JdkCrcs.class.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(location.getPath().endsWith(".jar"), "The integration tests have to run with the jar: " + location);
    }

    @Test
    void testClassesOfTheJdk() {
        List<CrcModel> models = models();
        assertTrue(models.get(0).isJdk(), "java.util.zip.CRC32 has to calculate CRC-32 on every Java version");
        assertTrue(models.get(1).isJdk(), "java.util.zip.CRC32 has to calculate the other parameters of its polynomial on every Java version");
        assertEquals(java9(), models.get(2).isJdk(), "java.util.zip.CRC32C has to calculate CRC-32C on Java 9+, the lookup tables of the library on Java 8");
        assertEquals(java9(), models.get(3).isJdk(),
                "java.util.zip.CRC32C has to calculate the other parameters of its polynomial on Java 9+, the lookup tables of the library on Java 8");
    }

    @Test
    void testEveryCount() {
        for (CrcModel model : models()) {
            long[] expected = BitsTest.reference(model.parameters(), BitsTest.bitsOf(DATA, model.parameters().refin()));
            for (int count = 0; count <= 8 * DATA.length; count++) {
                assertEquals(expected[count], model.valueOfBits(DATA, count), model + ": wrong value of the first " + count + " bits");
            }
            for (int length = 0; length <= DATA.length; length++) {
                assertEquals(model.value(DATA, length), model.valueOfBits(DATA, 8L * length),
                        model + ": the bits of " + length + " bytes have to give the value of the bytes");
            }
        }
    }

    @Test
    void testLong() {
        Random random = new Random(64);
        for (CrcModel model : models()) {
            CrcParameters parameters = model.parameters();
            for (int i = 0; i < 10; i++) {
                long bits = random.nextLong();
                for (int count = 0; count <= Long.SIZE; count++) {
                    assertEquals(BitsTest.reference(parameters, BitsTest.bitsOf(bits, count, parameters.refin()))[count], model.valueOfBits(bits, count),
                            model + ": wrong value of " + count + " bits of 0x" + Long.toHexString(bits));
                }
            }
        }
    }

}
