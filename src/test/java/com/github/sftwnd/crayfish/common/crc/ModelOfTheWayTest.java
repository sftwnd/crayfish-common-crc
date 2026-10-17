/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The model of the way given by an algorithm of the catalogue and by a description: {@link StandardCrc#model(boolean)},
 * {@link CrcDescription#model(boolean)} and {@link CrcDescription#model(String, boolean)} give the models of
 * {@link CrcModel#of(String, boolean)} and {@link CrcModel#of(CrcParameters, boolean)}, whatever the default.
 * The unit tests run with the class directory, where JdkCrcs is the version of Java 8: java.util.zip.CRC32 calculates
 * CRC-32/ISO-HDLC and CRC-32/JAMCRC, CRC-32C stays with the lookup tables
 */
class ModelOfTheWayTest {

    private static final boolean[] WAYS = {false, true};

    // the default of the JVM before a test, restored after it
    private boolean saved;

    @BeforeEach
    void saveTheDefault() {
        saved = CrcDescription.isJdk();
    }

    @AfterEach
    void restoreTheDefault() {
        CrcDescription.setJdk(saved);
    }

    // whether java.util.zip.CRC32 calculates the parameters, the only class of the JDK of the version of JdkCrcs of
    // Java 8: a reflected CRC 32 bits wide of its polynomial
    private static boolean crc32(final CrcParameters parameters) {
        return parameters.width() == Integer.SIZE && parameters.refin() && parameters.poly() == JdkCrc.CRC32_POLY;
    }

    // the models of the algorithm of both ways by every name, and the models chosen without a choice: those of the default
    private static void assertModelsOfTheWay(final StandardCrc standard, final boolean byDefault) {
        CrcDescription description = standard.model().description();
        for (boolean jdk : WAYS) {
            String way = standard + " of the way jdk=" + jdk + " with the default jdk=" + byDefault;
            CrcModel model = CrcModel.of(standard.crcName(), jdk);
            assertSame(model, standard.model(jdk), way + ": StandardCrc.model(jdk) has to give the model of CrcModel.of(name, jdk)");
            assertSame(model, description.model(jdk), way + ": CrcDescription.model(jdk) has to give the model of CrcModel.of(name, jdk)");
            assertEquals(jdk && crc32(standard.parameters()), model.isJdk(), way + ": the model has to be calculated the way given");
            for (String name : standard.names()) {
                assertSame(CrcModel.of(name, jdk), description.model(name, jdk),
                        way + ": CrcDescription.model(name, jdk) has to give the model of CrcModel.of(name, jdk) of " + name);
            }
        }
        assertSame(standard.model(byDefault), standard.model(), standard + ": StandardCrc.model() has to give the model of the default");
        assertSame(description.model(byDefault), description.model(), standard + ": CrcDescription.model() has to give the model of the default");
        for (String name : standard.names()) {
            assertSame(description.model(name, byDefault), description.model(name),
                    standard + ": CrcDescription.model(name) has to give the model of the default of " + name);
        }
    }

    @Test
    void testCatalogue() {
        for (boolean byDefault : WAYS) {
            CrcDescription.setJdk(byDefault);
            for (StandardCrc standard : StandardCrc.values()) {
                assertModelsOfTheWay(standard, byDefault);
            }
        }
    }

    @Test
    void testOtherParameters() {
        // a reflected CRC-32 of the polynomial of CRC32 outside the catalogue, and a CRC that no class of the JDK calculates
        List<CrcParameters> others = Arrays.asList(
                CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL),
                CrcParameters.of(16, 0x1234L, 0xFFFFL, false, true, 0xFFL));
        for (boolean byDefault : WAYS) {
            CrcDescription.setJdk(byDefault);
            for (CrcParameters parameters : others) {
                for (boolean jdk : WAYS) {
                    CrcModel model = CrcModel.of(parameters, jdk);
                    CrcDescription description = model.description();
                    String way = description.name() + " of the way jdk=" + jdk + " with the default jdk=" + byDefault;
                    assertSame(model, description.model(jdk), way + ": CrcDescription.model(jdk) has to give the model of CrcModel.of(parameters, jdk)");
                    assertSame(model, description.model(description.name(), jdk),
                            way + ": CrcDescription.model(name, jdk) has to give the model of CrcModel.of(parameters, jdk)");
                    assertEquals(jdk && crc32(parameters), model.isJdk(), way + ": the model has to be calculated the way given");
                }
            }
        }
    }

    @Test
    void testNameOfOtherParameters() {
        CrcDescription description = StandardCrc.CRC_32_ISO_HDLC.model(true).description();
        for (boolean jdk : WAYS) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> description.model("CRC-16/ARC", jdk),
                    "A name of other parameters has to be rejected of the way jdk=" + jdk);
            assertEquals("CrcDescription::model - CRC-32/ISO-HDLC has no name CRC-16/ARC", exception.getMessage(), "Wrong message");
        }
    }

}
