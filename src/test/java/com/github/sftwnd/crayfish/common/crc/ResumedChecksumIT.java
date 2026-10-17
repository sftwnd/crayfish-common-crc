/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checksum that continues a CRC of a model chosen with the classes of the JDK, with the jar: maven-failsafe-plugin
 * runs the integration tests with the jar of the project, so that Java 9+ loads the version of JdkCrcs
 * in META-INF/versions/9, and java.util.zip.CRC32C calculates CRC-32C (the lookup tables of the library on Java 8).
 * The assertions are those of ResumedChecksumTest
 */
class ResumedChecksumIT {

    private static boolean java9() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    // the models chosen with the classes of the JDK, by a name, an alias and other parameters of their polynomials
    private static List<CrcModel> models() {
        return Arrays.asList(
                CrcModel.of("CRC-32/ISO-HDLC", true),
                CrcModel.of("PKZIP", true),
                CrcModel.of("CRC-32/JAMCRC", true),
                CrcModel.of("CRC-32C", true),
                CrcModel.of("CRC-32/CASTAGNOLI", true),
                CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL), true),
                CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32C_POLY, 0x12345678L, true, true, 0x0L), true));
    }

    // java.util.zip.CRC32 calculates the model on every Java version, java.util.zip.CRC32C on Java 9+
    private static void assertWay(CrcModel model) {
        boolean crc32 = model.parameters().poly() == JdkCrc.CRC32_POLY;
        assertEquals(crc32 || java9(), model.isJdk(),
                model + ": java.util.zip.CRC32 has to calculate the model on every Java version, java.util.zip.CRC32C on Java 9+");
    }

    @Test
    void testTheJarIsTested() {
        URL location = JdkCrcs.class.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(location.getPath().endsWith(".jar"), "The integration tests have to run with the jar: " + location);
    }

    @Test
    void testUpdates() {
        for (CrcModel model : models()) {
            assertWay(model);
            ResumedChecksumTest.assertUpdates(model);
        }
    }

    @Test
    void testPartsOneAfterAnother() {
        for (CrcModel model : models()) {
            assertWay(model);
            ResumedChecksumTest.assertPartsOneAfterAnother(model);
        }
    }

    @Test
    void testCrcResetAndTheEmptyData() {
        for (CrcModel model : models()) {
            assertWay(model);
            ResumedChecksumTest.assertCrcOfTheChecksum(model);
            ResumedChecksumTest.assertReset(model);
            ResumedChecksumTest.assertChecksumOfTheEmptyData(model);
        }
    }

    @Test
    void testCheckedStreams(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("data");
        for (CrcModel model : models()) {
            assertWay(model);
            ResumedChecksumTest.assertCheckedStreams(model, file);
        }
    }

    @Test
    void testTheWayOfTheModel() {
        // the parameters of CRC-32 and CRC-32C with other init and xorout, of their own, so that no other test has built
        // their lookup tables
        CrcModel crc32 = CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x7A5E1D3CL, true, false, 0x3C1D5E7AL), true);
        CrcModel crc32c = CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32C_POLY, 0x7A5E1D3CL, true, false, 0x3C1D5E7AL), true);
        assertWay(crc32);
        assertWay(crc32c);
        ResumedChecksumTest.assertTheWayOfTheModel(crc32);
        ResumedChecksumTest.assertTheWayOfTheModel(crc32c);
    }

}
