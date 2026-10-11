/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.CHECK_BUFF;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC32_ISO_HDLC;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.construct;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.lookUp;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrcModelRegistryTest {

    private static final int THREADS = 16;

    // names and parameters depend on the run, so that every run registers new models,
    // also when the tests run repeatedly in the same JVM
    private static final AtomicInteger RUNS = new AtomicInteger();

    // every test starts with the predefined models only
    @AfterEach
    void cleanUp() {
        CrcModel.removeRegisteredModels();
    }

    @Test
    void testRemoveRegisteredModels() throws IllegalAccessException {
        Set<CrcModel> predefined = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Field field : CrcModel.class.getDeclaredFields()) {
            if (field.getType() == CrcModel.class && !field.isAnnotationPresent(Deprecated.class)) {
                predefined.add((CrcModel) field.get(null));
            }
        }
        int run = RUNS.incrementAndGet();
        long modelCount = getModels().count();
        String name = "CRC-21/TEST-REGISTRY-REMOVE-" + run;
        CrcModel named = construct(name, new CrcDescription(21, 0x100001 + 4 * run, 0x0, false, false, 0x0));
        CrcModel unnamed = new CrcDescription(21, 0x100003 + 4 * run, 0x0, false, false, 0x0).getCRC().getModel();
        assertEquals(modelCount + 2, getModels().count(), "Two models have to be registered");
        CrcModel.removeRegisteredModels();
        assertNull(lookUp(name), "A removed model has not got to be found by the name");
        assertEquals(0, getModels(m -> m == named || m == unnamed).count(), "Removed models have not got to be listed");
        assertEquals(predefined.size(), getModels().count(), "Only the predefined models have to stay registered");
        assertTrue(getModels().allMatch(predefined::contains), "All predefined models have to stay registered");
    }

    @Test
    void testRegisteredDescriptionIsACopy() {
        int run = RUNS.incrementAndGet();
        CrcDescription description = new CrcDescription(21, 0x100005 + 4 * run, 0x0, false, false, 0x0);
        CrcModel model = construct(description);
        assertNotSame(description, model.getCrcDescription(), "The model must not keep the CrcDescription of the caller");
        assertEquals(description, model.getCrcDescription(), "The model has to keep the parameters of the CrcDescription");
        model.getCRC().update(CHECK_BUFF.getBytes(US_ASCII));
        description.getCRC().update(CHECK_BUFF.getBytes(US_ASCII));
        assertNull(description.table_byte, "The lookup table must not be built in the CrcDescription of the caller");
        assertNotNull(model.getCrcDescription().table_byte, "The lookup table has to be built in the CrcDescription of the model");
    }

    @Test
    void testNoLockOnDescriptions() throws InterruptedException {
        int run = RUNS.incrementAndGet();
        // the CrcDescription of the caller, and the CrcDescription of a model whose lookup table is not built yet:
        // with a known check value, construct() calculates no CRC
        CrcDescription description = new CrcDescription(21, 0x100007 + 4 * run, 0x0, false, false, 0x0);
        CrcDescription parameters = new CrcDescription(21, 0x100009 + 4 * run, 0x0, false, false, 0x0);
        CrcModel model = construct("CRC-21/TEST-NO-LOCK-" + run, parameters, CrcReference.crc(parameters, CHECK_BUFF.getBytes(US_ASCII)));
        CrcDescription shared = model.getCrcDescription();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (description) {
                synchronized (shared) {
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
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                description.getCRC().update(CHECK_BUFF.getBytes(US_ASCII));
                model.getCRC().update(CHECK_BUFF.getBytes(US_ASCII));
            }, "getCRC() must not wait for the monitor of a CrcDescription that other code holds");
        } finally {
            release.countDown();
            holder.join();
        }
    }

    @Test
    void testLookUpAndConstructFindRegisteredModels() {
        int run = RUNS.incrementAndGet();
        String name = "CRC-21/TEST-REGISTRY-INDEX-" + run;
        CrcDescription description = new CrcDescription(21, 0x10000B + 4 * run, 0x0, false, false, 0x0);
        assertNull(lookUp(name), "An unknown name has to give no model");
        CrcModel model = construct(name, description);
        assertSame(model, lookUp(name), "lookUp() has to find a model registered at run time");
        assertSame(model, construct(new CrcDescription(description)), "construct() has to find a model registered at run time by its parameters");
        // the parameters of a subclass of CrcDescription
        CrcDescription subclass = new CrcDescription(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL) { };
        assertSame(CRC32_ISO_HDLC, construct(subclass), "construct() has to find a model by the parameters of a subclass of CrcDescription");
        // the name of a predefined model cannot be registered for other parameters
        CrcDescription other = new CrcDescription(21, 0x10000D + 4 * run, 0x0, false, false, 0x0);
        assertThrows(IllegalArgumentException.class, () -> construct("CRC-32", other), "The name of a predefined model has to be rejected for other parameters");
        assertSame(CrcModel.CRC32, lookUp("CRC-32"), "lookUp() has to return the predefined model");
        CrcModel.removeRegisteredModels();
        assertNull(lookUp(name), "lookUp() has not got to find a removed model");
        assertSame(CrcModel.CRC32, lookUp("CRC-32"), "lookUp() has to find the predefined models after removeRegisteredModels()");
        assertSame(CRC32_ISO_HDLC, construct(new CrcDescription(CRC32_ISO_HDLC)), "construct() has to find the predefined models after removeRegisteredModels()");
    }

    @Test
    void testRegistrationWhileStreamIsUsed() {
        int run = RUNS.incrementAndGet();
        String name = "CRC-19/TEST-REGISTRY-STREAM-" + run;
        long modelCount = getModels().count();
        Stream<CrcModel> models = getModels();
        CrcModel registered = construct(name, new CrcDescription(19, 0x3A5C1 + 4 * run, 0x0, false, false, 0x0));
        assertEquals(modelCount, models.count(), "A stream of getModels() has to contain the models registered at the time of the call");
        assertEquals(modelCount + 1, getModels().count(), "The model has to be registered");
        assertSame(registered, lookUp(name), "The model registered while a stream of getModels() was open has to be found");
        String forEachName = "CRC-19/TEST-REGISTRY-FOREACH-" + run;
        AtomicInteger visited = new AtomicInteger();
        getModels().forEach(m -> {
            // register a model while the stream is processed
            if (visited.getAndIncrement() == 0) {
                construct(forEachName, new CrcDescription(19, 0x3A5C3 + 4 * run, 0x0, false, false, 0x0));
            }
        });
        assertEquals(modelCount + 1, visited.get(), "getModels().forEach() has to visit the models registered at the time of the call");
        assertEquals(modelCount + 2, getModels().count(), "The model constructed in getModels().forEach() has to be registered");
        assertNotNull(lookUp(forEachName), "The model registered in getModels().forEach() has to be found");
    }

    @Test
    void testConstructRegistersAliasForKnownParameters() {
        String alias = "CRC-32/TEST-REGISTRY-ALIAS-" + RUNS.incrementAndGet();
        long modelCount = getModels().count();
        CrcModel model = construct(alias, new CrcDescription(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL));
        assertEquals(alias, model.getName(), "construct() with a new name for known parameters has to register a model with this name");
        assertSame(CRC32_ISO_HDLC.getCrcDescription(), model.getCrcDescription(), "The new model has to use the parameters of CRC-32/ISO-HDLC");
        assertEquals(modelCount + 1, getModels().count(), "construct() has to register the new model");
        assertSame(model, construct(alias, CRC32_ISO_HDLC), "construct() has to return the registered model");
        assertEquals(modelCount + 1, getModels().count(), "construct() must not register the model twice");
    }

    @Test
    @Timeout(60)
    void testConcurrentConstructRegistersOneModel() throws Exception {
        int run = RUNS.incrementAndGet();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < 20; round++) {
                int id = run * 100 + round;
                // a new name with new parameters
                String name = "CRC-23/TEST-REGISTRY-RACE-" + id;
                CrcDescription description = new CrcDescription(23, 0x400001 + 2 * id, 0x0, false, false, 0x0);
                assertOneModel(pool, "construct(\"" + name + "\", new parameters)",
                        () -> construct(name, description), m -> name.equals(m.getName()));
                // a new name for the parameters of a predefined model
                String alias = "CRC-32/TEST-REGISTRY-RACE-" + id;
                CrcModel model = assertOneModel(pool, "construct(\"" + alias + "\", CRC32_ISO_HDLC)",
                        () -> construct(alias, CRC32_ISO_HDLC), m -> alias.equals(m.getName()));
                assertSame(CRC32_ISO_HDLC.getCrcDescription(), model.getCrcDescription(), "The model " + alias + " has to use the parameters of CRC-32/ISO-HDLC");
                // CrcDescription.getCRC() with new parameters registers a model without a name
                CrcDescription unnamed = new CrcDescription(22, 0x200001 + 2 * id, 0x0, false, false, 0x0);
                assertOneModel(pool, "CrcDescription.getCRC() for " + unnamed,
                        () -> new CrcDescription(unnamed).getCRC().getModel(), m -> unnamed.equals(m.getCrcDescription()));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    void testConcurrentConstructWithOneNameForOtherParameters() throws Exception {
        int run = RUNS.incrementAndGet();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < 20; round++) {
                int id = run * 100 + round;
                String name = "CRC-23/TEST-REGISTRY-NAME-RACE-" + id;
                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Future<CrcModel>> calls = new ArrayList<>();
                for (int t = 0; t < THREADS; t++) {
                    // every thread registers the name for other parameters
                    CrcDescription description = new CrcDescription(23, 0x400001 + 2 * (id * THREADS + t), 0x0, false, false, 0x0);
                    calls.add(pool.submit(() -> {
                        barrier.await();
                        try {
                            return construct(name, description);
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    }));
                }
                Set<CrcModel> registered = Collections.newSetFromMap(new IdentityHashMap<>());
                for (Future<CrcModel> call : calls) {
                    Optional.ofNullable(call.get()).ifPresent(registered::add);
                }
                assertEquals(1, registered.size(), "Only one of the concurrent registrations of " + name + " has to succeed");
                assertEquals(1, getModels(m -> name.equals(m.getName())).count(), "The name " + name + " has to be registered once");
                assertSame(registered.iterator().next(), lookUp(name), "lookUp() has to return the registered model");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(60)
    void testConcurrentReadAndRegister() throws Exception {
        int run = RUNS.incrementAndGet();
        int writers = 4;
        int readers = 4;
        int registrations = 10;
        List<CrcDescription> descriptions = new ArrayList<>();
        for (int i = 0; i < writers * registrations; i++) {
            descriptions.add(new CrcDescription(29, 0x10000001 + 2 * (run * 1000 + i), 0x0, false, false, 0x0));
        }
        long modelCount = getModels().count();
        ExecutorService pool = Executors.newFixedThreadPool(writers + readers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            AtomicBoolean registered = new AtomicBoolean();
            List<Future<?>> writes = new ArrayList<>();
            for (int w = 0; w < writers; w++) {
                List<CrcDescription> writerDescriptions = descriptions.subList(w * registrations, (w + 1) * registrations);
                writes.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    for (CrcDescription description : writerDescriptions) {
                        construct(description);
                    }
                    return null;
                }));
            }
            List<Future<?>> reads = new ArrayList<>();
            for (int r = 0; r < readers; r++) {
                reads.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    do {
                        getModels().count();
                        lookUp("CRC-29/TEST-REGISTRY-UNKNOWN");
                        // parameters of a predefined model: CrcDescription.getCRC() looks the model up in the registry
                        new CrcDescription(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL).getCRC().update(CHECK_BUFF.getBytes(US_ASCII));
                    } while (!registered.get());
                    return null;
                }));
            }
            start.countDown();
            try {
                for (Future<?> write : writes) {
                    write.get();
                }
            } finally {
                registered.set(true);
            }
            for (Future<?> read : reads) {
                read.get();
            }
            assertEquals(modelCount + descriptions.size(), getModels().count(), "Only the new parameter sets have to be registered");
            for (CrcDescription description : descriptions) {
                assertEquals(1, getModels(m -> description.equals(m.getCrcDescription())).count(), "The parameter set " + description + " has to be registered once");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // runs the call in all threads at once: all of them have to get the same model, which is registered once
    private static CrcModel assertOneModel(ExecutorService pool, String call, Callable<CrcModel> action, Predicate<CrcModel> registered) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<CrcModel>> calls = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            calls.add(pool.submit(() -> {
                barrier.await();
                return action.call();
            }));
        }
        Set<CrcModel> results = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Future<CrcModel> result : calls) {
            results.add(result.get());
        }
        assertEquals(1, results.size(), "Concurrent calls of " + call + " have to return the same model");
        assertEquals(1, getModels(registered).count(), "Concurrent calls of " + call + " have to register one model");
        return results.iterator().next();
    }

}
