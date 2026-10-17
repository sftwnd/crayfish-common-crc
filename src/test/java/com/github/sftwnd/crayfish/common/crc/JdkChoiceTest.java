/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The choice of the calculation of a model: the default of {@link CrcDescription#isJdk()}, the models of a name, of
 * the parameters and of the builder with the way, {@link CrcModel#withJdk(boolean)} and {@link CrcModel#isJdk()}.
 * The unit tests run with the class directory, where JdkCrcs is the version of Java 8: java.util.zip.CRC32 calculates
 * CRC-32/ISO-HDLC and CRC-32/JAMCRC, CRC-32C stays with the lookup tables; JdkIT tests CRC32C with the jar
 */
class JdkChoiceTest {

    private static final byte[] DATA = new byte[5000];

    static {
        new Random(114).nextBytes(DATA);
    }

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

    // every way to choose a model of CRC-32/ISO-HDLC without a choice gives the model of the default
    private static void assertDefault(final boolean jdk) {
        assertEquals(jdk, CrcDescription.isJdk(), "Wrong default");
        CrcParameters parameters = StandardCrc.CRC_32_ISO_HDLC.parameters();
        CrcModel expected = CrcModel.of("CRC-32/ISO-HDLC", jdk);
        assertEquals(jdk, expected.isJdk(), "The model of CRC-32 has to be calculated the way of the default");
        assertSame(expected, CrcModel.of("CRC-32/ISO-HDLC"), "of(name) has to give the model of the default");
        assertSame(expected, CrcModel.find("CRC-32/ISO-HDLC").orElse(null), "find(name) has to give the model of the default");
        assertSame(expected, CrcModel.of(parameters.parametricName()), "of(name made of the parameters) has to give the model of the default");
        assertSame(expected, CrcModel.of(parameters), "of(parameters) has to give the model of the default");
        assertSame(expected, CrcModel.of(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL), "of(width, ...) has to give the model of the default");
        assertSame(expected, CrcModel.builder(32).poly(0x04C11DB7L).init(0xFFFFFFFFL).reflected(true).xorout(0xFFFFFFFFL).build(),
                "A builder without a way has to give the model of the default");
        assertSame(expected, StandardCrc.CRC_32_ISO_HDLC.model(), "StandardCrc.model() has to give the model of the default");
        assertSame(expected, expected.description().model(), "CrcDescription.model() has to give the model of the default");
        assertSame(CrcModel.of("PKZIP", jdk), expected.description().model("PKZIP"), "CrcDescription.model(name) has to give the model of the default");
    }

    @Test
    void testDefault() {
        CrcDescription.setJdk(false);
        assertDefault(false);
        CrcDescription.setJdk(true);
        assertDefault(true);
        CrcDescription.setJdk(false);
        assertDefault(false);
    }

    @Test
    void testModelsKeepTheirWay() {
        CrcDescription.setJdk(true);
        CrcModel jdk = CrcModel.of("CRC-32");
        CRC jdkCrc = jdk.crc(DATA, 0, 1000);
        CrcChecksum jdkChecksum = jdk.checksum();
        CrcDescription.setJdk(false);
        CrcModel tables = CrcModel.of("CRC-32");
        CRC tablesCrc = tables.crc(DATA, 0, 1000);
        assertTrue(jdk.isJdk(), "A model chosen before a change of the default has to keep its way");
        assertFalse(tables.isJdk(), "A model chosen after a change of the default has to get the new way");
        assertNotSame(jdk, tables, "The models of the two ways have to be different");
        assertSame(jdk, jdkCrc.update(DATA, 1000, 2000).model(), "A CRC has to keep its model and its way");
        assertSame(jdk, jdkChecksum.model(), "A checksum has to keep its model and its way");
        assertSame(jdk, jdkCrc.combine(tables.crc(DATA, 1000, 2000)).model(), "A combined CRC has to keep the model of the first CRC");
        assertSame(tables, tablesCrc.combine(jdk.crc(DATA, 1000, 2000)).model(), "A combined CRC has to keep the model of the first CRC");
        CrcDescription.setJdk(true);
        assertFalse(tables.isJdk(), "A model chosen before a change of the default has to keep its way");
        assertSame(jdk, CrcModel.of("CRC-32"), "The model of the way has to be unique");
    }

    @Test
    void testChoice() {
        for (boolean byDefault : new boolean[]{false, true}) {
            CrcDescription.setJdk(byDefault);
            for (String name : StandardCrc.CRC_32_ISO_HDLC.names()) {
                CrcModel jdk = CrcModel.of(name, true);
                CrcModel tables = CrcModel.of(name, false);
                assertTrue(jdk.isJdk(), name + ": CRC32 has to calculate the model of the JDK");
                assertFalse(tables.isJdk(), name + ": the lookup tables have to calculate the model of the lookup tables");
                assertSame(jdk, CrcModel.of(name, true), name + ": the model of the JDK has to be unique");
                assertSame(tables, CrcModel.of(name, false), name + ": the model of the lookup tables has to be unique");
                assertSame(jdk, CrcModel.find(name, true).orElse(null), name + ": find has to give the model of the JDK");
                assertSame(tables, CrcModel.find(name, false).orElse(null), name + ": find has to give the model of the lookup tables");
                assertSame(jdk, tables.withJdk(true), name + ": withJdk(true) has to give the model of the JDK");
                assertSame(tables, jdk.withJdk(false), name + ": withJdk(false) has to give the model of the lookup tables");
                assertSame(jdk, jdk.withJdk(true), name + ": withJdk of the way of the model has to give the model");
                assertSame(tables, tables.withJdk(false), name + ": withJdk of the way of the model has to give the model");
                assertEquals(name, jdk.name(), name + ": the model of the JDK has to keep the name");
                assertSame(tables.description(), jdk.description(), name + ": both ways have to have one description");
            }
            CrcParameters parameters = StandardCrc.CRC_32_JAMCRC.parameters();
            assertTrue(CrcModel.of(parameters, true).isJdk(), "of(parameters, true) has to give the model of the JDK");
            assertFalse(CrcModel.of(parameters, false).isJdk(), "of(parameters, false) has to give the model of the lookup tables");
            CrcModel.Builder builder = CrcModel.builder(32).poly(0x04C11DB7L).init(0xFFFFFFFFL).reflected(true);
            assertSame(CrcModel.of(parameters, true), builder.jdk(true).build(), "The builder has to give the model of the JDK");
            assertSame(CrcModel.of(parameters, false), builder.jdk(false).build(), "The builder has to give the model of the lookup tables");
            assertFalse(CrcModel.find("CRC-32/UNKNOWN", true).isPresent(), "find has to give nothing for an unknown name");
            assertThrows(IllegalArgumentException.class, () -> CrcModel.of("CRC-32/UNKNOWN", true), "An unknown name has to be rejected");
        }
    }

    @Test
    void testParametersThatNoClassOfTheJdkCalculates() {
        // other widths and polynomials, a CRC-32 that is not reflected, and CRC-32C: the version of JdkCrcs of Java 8
        // knows no CRC32C
        List<CrcModel> models = new ArrayList<>(Arrays.asList(
                CrcModel.of("CRC-16/ARC", false), CrcModel.of("CRC-32/BZIP2", false), CrcModel.of("CRC-32/MPEG-2", false),
                CrcModel.of("CRC-64/XZ", false), CrcModel.of("CRC-32C", false), CrcModel.of("CRC-32/CASTAGNOLI", false),
                CrcModel.of(CrcParameters.of(31, JdkCrc.CRC32_POLY, 0x7FFFFFFFL, true, true, 0x7FFFFFFFL), false)));
        for (CrcModel model : models) {
            assertSame(model, model.withJdk(true), model + ": the model of the lookup tables has to be given");
            assertSame(model, CrcModel.of(model.name(), true), model + ": the model of the lookup tables has to be given");
            assertFalse(model.withJdk(true).isJdk(), model + ": no class of the JDK of Java 8 calculates the CRC");
        }
    }

    @Test
    void testCrcsOfBothWays() {
        for (StandardCrc standard : new StandardCrc[]{StandardCrc.CRC_32_ISO_HDLC, StandardCrc.CRC_32_JAMCRC}) {
            CrcModel jdk = standard.model().withJdk(true);
            CrcModel tables = standard.model().withJdk(false);
            long expected = CrcReference.crc(standard.parameters(), DATA);
            assertEquals(expected, jdk.value(DATA), standard + ": wrong value of the model of the JDK");
            assertEquals(expected, tables.value(DATA), standard + ": wrong value of the model of the lookup tables");
            CRC byJdk = jdk.crc(DATA);
            CRC byTables = tables.crc(DATA);
            assertEquals(byJdk, byTables, standard + ": the CRCs of both ways have to be equal");
            assertEquals(byTables, byJdk, standard + ": the CRCs of both ways have to be equal");
            assertEquals(byJdk.hashCode(), byTables.hashCode(), standard + ": the CRCs of both ways have to have the same hash code");
            int split = 1234;
            assertEquals(byTables, jdk.crc(DATA, 0, split).combine(tables.crc(DATA, split, DATA.length - split)),
                    standard + ": the CRCs of both ways have to be combined");
            assertEquals(byJdk, tables.crc(DATA, 0, split).combine(jdk.crc(DATA, split, DATA.length - split)),
                    standard + ": the CRCs of both ways have to be combined");
            assertEquals(expected, jdk.combine(jdk.value(DATA, 0, split), tables.value(DATA, split, DATA.length - split), DATA.length - split),
                    standard + ": wrong combined value of the model of the JDK");
        }
    }

    @Test
    void testOneModelPerWayFromThreads() throws InterruptedException, ExecutionException {
        // parameters of their own, so that the models of the JDK are created by the threads
        CrcParameters parameters = CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0BADF00DL, true, true, 0x0L);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<CrcModel>> futures = new ArrayList<>();
            Callable<CrcModel> choose = () -> {
                start.await();
                return CrcModel.of(parameters, true);
            };
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(choose));
            }
            start.countDown();
            CrcModel first = futures.get(0).get();
            assertTrue(first.isJdk(), "The model of the JDK has to be calculated by CRC32");
            for (Future<CrcModel> future : futures) {
                assertSame(first, future.get(), "The threads have to get the same model of the JDK");
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "The threads have to end");
        }
    }

    @Test
    void testOneSetOfLookupTables() {
        // parameters of their own, so that no other test has built their lookup tables and operators
        CrcModel jdk = CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x2468ACE0L, true, false, 0x13579BDFL), true);
        CrcEngine lookup = jdk.description().engine();
        assertFalse(lookup.hasTables(), "The lookup tables have not got to be built yet");
        CrcChecksum checksum = jdk.checksum();
        checksum.update(DATA, 0, 5);
        checksum.update(DATA[5]);
        long value = jdk.combine(checksum.getValue(), jdk.value(DATA, 6, 7), 7);
        assertEquals(CrcReference.crc(jdk.parameters(), DATA, 0, 13), value, "Wrong value of the parts");
        assertTrue(lookup.hasTables(), "Short data and single bytes of the model of the JDK have to be calculated by the lookup tables of the description");
        assertTrue(lookup.hasZeros(), "combine of the model of the JDK has to use the operators of the description");
        assertFalse(jdk.engine().hasTables(), "The engine of the JDK has not got to build lookup tables of its own");
        assertFalse(jdk.engine().hasZeros(), "The engine of the JDK has not got to build operators of its own");
    }

}
