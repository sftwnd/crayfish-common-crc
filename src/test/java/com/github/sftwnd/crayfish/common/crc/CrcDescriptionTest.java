/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrcDescriptionTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    private static final int THREADS = 16;

    // parameters outside the catalogue that depend on the run: every run gets a new description,
    // also when the tests run repeatedly in the same JVM
    private static final AtomicInteger RUNS = new AtomicInteger();

    private static CrcParameters newParameters() {
        return CrcParameters.of(24, 0x5D6DCBL, 0x100000L + RUNS.incrementAndGet(), true, false, 0x1L);
    }

    @Test
    void testDescriptionOfTheCatalogue() {
        CrcDescription description = CrcModel.of("CRC-16/KERMIT").description();
        List<String> aliases = Arrays.asList("CRC-16/BLUETOOTH", "CRC-16/CCITT", "CRC-16/CCITT-TRUE", "CRC-16/V-41-LSB", "CRC-CCITT", "KERMIT");
        assertEquals("CRC-16/KERMIT", description.name(), "Wrong name");
        assertEquals(aliases, description.aliases(), "Wrong aliases");
        List<String> names = new ArrayList<>(aliases);
        names.add(0, "CRC-16/KERMIT");
        assertEquals(names, description.names(), "Wrong names");
        assertEquals(Optional.of(StandardCrc.CRC_16_KERMIT), description.standard(), "Wrong algorithm of the catalogue");
        assertEquals(StandardCrc.CRC_16_KERMIT.parameters(), description.parameters(), "Wrong parameters");
        assertEquals(0x2189L, description.check(), "Wrong check value");
        assertEquals("CRC-16/KERMIT", description.model().name(), "The model of the description has to have its name");
        assertSame(CrcModel.of("KERMIT"), description.model("KERMIT"), "The model of an alias has to be unique");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> description.model("CRC-16/ARC"),
                "A name of other parameters has to be rejected");
        assertEquals("CrcDescription::model - CRC-16/KERMIT has no name CRC-16/ARC", exception.getMessage(), "Wrong message");
        List<String> descriptionNames = description.names();
        assertThrows(UnsupportedOperationException.class, () -> descriptionNames.add("CRC-TEST"), "The names have to be unmodifiable");
        List<String> descriptionAliases = description.aliases();
        assertThrows(UnsupportedOperationException.class, () -> descriptionAliases.add("CRC-TEST"), "The aliases have to be unmodifiable");
        assertEquals("width=16  poly=0x1021  init=0x0000  refin=true  refout=true  xorout=0x0000  check=0x2189  name=\"CRC-16/KERMIT\"",
                description.toString(), "Wrong format");
    }

    @Test
    void testDescriptionOfOtherParameters() {
        CrcParameters parameters = CrcParameters.of(5, 0x15L, 0x1FL, false, true, 0x3L);
        CrcDescription description = CrcModel.of(parameters).description();
        assertEquals("CRC-5/P15_I1F_RO_X3", description.name(), "Wrong name made of the parameters");
        assertEquals(Collections.singletonList("CRC-5/P15_I1F_RO_X3"), description.names(), "Wrong names");
        assertEquals(Collections.emptyList(), description.aliases(), "Parameters outside the catalogue have got no aliases");
        assertFalse(description.standard().isPresent(), "Parameters outside the catalogue have got no algorithm of the catalogue");
        long check = CrcReference.crc(parameters, CHECK);
        assertEquals(check, description.check(), "Wrong check value");
        assertSame(description.model(), description.model("CRC-5/P15_I1F_RO_X3"), "The model of the name has to be unique");
        assertThrows(IllegalArgumentException.class, () -> description.model("CRC-5/USB"), "A name of other parameters has to be rejected");
        assertEquals("width=5  poly=0x15  init=0x1f  refin=false  refout=true  xorout=0x03  check=" + CrcParameters.hex(check, 5)
                + "  name=\"CRC-5/P15_I1F_RO_X3\"", description.toString(), "Wrong format");
    }

    @Test
    void testOneDescriptionPerParameters() {
        CrcDescription description = CrcModel.of("CRC-32C").description();
        assertSame(description, CrcModel.of("CRC-32/ISCSI").description(), "The name and the alias have to share the description");
        assertSame(description, StandardCrc.CRC_32_ISCSI.model().description(), "The algorithm has to have the description");
        assertSame(description, CrcModel.of(StandardCrc.CRC_32_ISCSI.parameters()).description(), "The parameters have to have the description");
        assertSame(description, CrcModel.of(32, 0x1EDC6F41L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL).description(), "Equal parameters have to have the description");
        assertSame(description, CrcModel.builder(32).poly(0x1EDC6F41L).init(0xFFFFFFFFL).reflected(true).xorout(0xFFFFFFFFL).build().description(),
                "The builder has to give the description");
        assertSame(description, CrcModel.of("CRC-32/P1EDC6F41_IFFFFFFFF_RI_RO_XFFFFFFFF").description(), "The name made of the parameters has to give the description");
        assertNotSame(description, CrcModel.of("CRC-32").description(), "Other parameters have another description");
    }

    @Test
    void testConcurrentDescriptions() throws Exception {
        CrcParameters parameters = newParameters();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CyclicBarrier start = new CyclicBarrier(THREADS);
            List<Future<CrcDescription>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return CrcModel.of(parameters).description();
                }));
            }
            Set<CrcDescription> descriptions = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Future<CrcDescription> future : futures) {
                descriptions.add(future.get());
            }
            assertEquals(1, descriptions.size(), "Concurrent threads have to get one description of the parameters");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testTablesAreBuiltOnTheFirstCalculation() {
        CrcParameters parameters = newParameters();
        CrcModel model = CrcModel.of(parameters);
        CrcEngine engine = model.description().engine();
        assertFalse(engine.hasTables(), "The lookup tables have not got to be built for a model");
        long check = model.check();
        assertSame(model, CrcModel.builder(24).poly(parameters.poly()).init(parameters.init()).refin(true).refout(false).xorout(0x1L).check(check).build(),
                "The builder has to give the model");
        CRC saved = model.crc(0x123456L, 10);
        assertEquals(10 + CHECK.length, saved.combine(model.crc(check, CHECK.length)).length(), "Wrong length of a combined CRC");
        assertSame(model.crc(), model.crc(new byte[0]), "The CRC of an empty buffer has to be the CRC of the empty data");
        assertFalse(engine.hasTables(), "The check value, the empty data, a saved CRC and combine have not got to build the lookup tables");
        assertEquals(check, model.crc(CHECK).value(), "Wrong CRC");
        assertTrue(engine.hasTables(), "The lookup tables have to be built on the first calculation with data");
        CrcTables tables = engine.tables();
        assertEquals(check, model.crc(CHECK).value(), "Wrong CRC");
        assertSame(tables, engine.tables(), "The lookup tables have to be built once");
    }

    @Test
    void testAliasesShareTheTables() {
        CrcModel crc32 = CrcModel.of("CRC-32");
        CrcModel pkzip = CrcModel.of("PKZIP");
        assertEquals(crc32.check(), crc32.crc(CHECK).value(), "Wrong CRC");
        assertSame(crc32.description().engine().tables(), pkzip.description().engine().tables(), "The aliases have to share the lookup tables");
    }

    @Test
    @Timeout(10)
    void testNoLockOnDescriptions() throws InterruptedException {
        // the monitors of a description and of its model, held by another thread, do not block the calculation
        CrcParameters parameters = newParameters();
        CrcModel model = CrcModel.of(parameters);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (model.description()) {
                synchronized (model) {
                    held.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        });
        holder.start();
        held.await();
        try {
            assertEquals(CrcReference.crc(parameters, CHECK), model.crc(CHECK).value(), "The lookup tables have to be built without the lock of the description");
            assertSame(model, CrcModel.of(parameters), "The model has to be found without the lock of the description");
            CrcParameters other = newParameters();
            assertEquals(CrcReference.crc(other, CHECK), CrcModel.of(other).check(), "A new description has to be created");
        } finally {
            release.countDown();
            holder.join();
        }
    }

    // a model of the parameters used for a calculation: the probes of its description and of its lookup tables
    private static List<WeakReference<Object>> calculate(CrcParameters parameters) {
        CrcModel model = CrcModel.of(parameters);
        assertEquals(CrcReference.crc(parameters, CHECK), model.crc(CHECK).value(), "Wrong CRC");
        return Arrays.asList(new WeakReference<>(model.description()), new WeakReference<>(model.description().engine().tables()));
    }

    @Test
    void testUnusedDescriptionIsCollected() throws InterruptedException {
        Interner<CrcParameters, CrcDescription> descriptions = CrcDescription.descriptions();
        assumeTrue(descriptions.retention() == Retention.WEAK, "the pool refers to the descriptions softly");
        CrcParameters parameters = newParameters();
        List<WeakReference<Object>> probes = calculate(parameters);
        assertTrue(descriptions.containsKey(parameters), "The description has to be in the pool");
        Gc.awaitCleared(probes.get(0), "The unused description has to be collected");
        Gc.awaitCleared(probes.get(1), "The lookup tables of the unused description have to be collected");
        // before Java 9 the pool removes the entry when it is used, here by size(); on Java 9+ the cleaner does
        Gc.await(() -> descriptions.size() >= 0 && !descriptions.containsKey(parameters), "The entry of the collected description has to be removed");
        assertEquals(CrcReference.crc(parameters, CHECK), CrcModel.of(parameters).crc(CHECK).value(), "A new description of the parameters has to calculate");
    }

}
