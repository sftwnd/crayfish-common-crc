/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternerTest {

    private static final int THREADS = 16;

    // a value of the key that nobody refers to: the probe of the value
    private static WeakReference<Object> intern(Interner<String, Object> interner, String key, AtomicInteger created) {
        return new WeakReference<>(interner.intern(key, k -> {
            created.incrementAndGet();
            return new Object();
        }));
    }

    @Test
    void testValueInUse() {
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        Object value = interner.intern("a", key -> new Object());
        assertSame(value, interner.intern("a", key -> new Object()), "The value in use has to be returned");
        System.gc();
        assertSame(value, interner.intern("a", key -> new Object()), "The value in use has not got to be collected");
        Object other = interner.intern("b", key -> new Object());
        assertNotSame(value, other, "Another key has to get another value");
        assertEquals(2, interner.size(), "Wrong number of the entries");
        assertTrue(interner.containsKey("a"), "The entry of the value in use has to be kept");
    }

    @Test
    void testWeakValueIsCollected() throws InterruptedException {
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        AtomicInteger created = new AtomicInteger();
        WeakReference<Object> probe = intern(interner, "a", created);
        Gc.awaitCleared(probe, "The unused value has to be collected");
        // the entry is removed by size() before Java 9, by the cleaner on Java 9+
        Gc.await(() -> interner.size() == 0, "The entry of the collected value has to be removed");
        assertFalse(interner.containsKey("a"), "The entry of the collected value has to be removed");
        assertNotNull(intern(interner, "a", created), "A new value has to be created");
        assertEquals(2, created.get(), "A new value has to be created for the key of a collected value");
    }

    @Test
    void testSoftValueSurvivesGarbageCollection() {
        Interner<String, Object> interner = new Interner<>(Retention.SOFT);
        AtomicInteger created = new AtomicInteger();
        WeakReference<Object> probe = intern(interner, "a", created);
        // soft references are cleared before the memory runs out, not by an explicit garbage collection
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        Object value = probe.get();
        assertNotNull(value, "The unused value has to be kept softly");
        assertSame(value, interner.intern("a", key -> new Object()), "The kept value has to be returned");
        assertEquals(1, created.get(), "The kept value has not got to be created again");
        assertEquals(1, interner.size(), "The entry of the kept value has to be kept");
    }

    @Test
    void testConcurrentIntern() throws Exception {
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        AtomicInteger created = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CyclicBarrier start = new CyclicBarrier(THREADS);
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return interner.intern("a", key -> {
                        created.incrementAndGet();
                        return new Object();
                    });
                }));
            }
            Set<Object> values = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Future<Object> future : futures) {
                values.add(future.get());
            }
            assertEquals(1, values.size(), "Concurrent threads have to get one value of the key");
            assertEquals(1, created.get(), "One value has to be created for the key");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void testFactoryReturningNull() {
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        NullPointerException exception = assertThrows(NullPointerException.class, () -> interner.intern("a", key -> null),
                "A null value has to be rejected");
        assertEquals("Interner::intern - the factory returned null", exception.getMessage(), "Wrong message");
        assertEquals(0, interner.size(), "No entry has to be added for a null value");
        assertThrows(NullPointerException.class, () -> new Interner<String, Object>(null), "A null retention has to be rejected");
    }

    @Test
    void testRetention() {
        assertSame(Retention.WEAK, Retention.of(null), "No value is weak");
        for (String value : new String[]{"weak", "WEAK", "", "softly", "s"}) {
            assertSame(Retention.WEAK, Retention.of(value), "\"" + value + "\" is weak");
        }
        for (String value : new String[]{"soft", "SOFT", " Soft "}) {
            assertSame(Retention.SOFT, Retention.of(value), "\"" + value + "\" is soft");
        }
        String saved = System.getProperty(CrcDescription.RETENTION_PROPERTY);
        try {
            System.setProperty(CrcDescription.RETENTION_PROPERTY, "soft");
            assertSame(Retention.SOFT, Retention.configured(), "The system property has to choose soft references");
            System.clearProperty(CrcDescription.RETENTION_PROPERTY);
            assertSame(Retention.WEAK, Retention.configured(), "Weak references have to be the default");
        } finally {
            if (saved == null) {
                System.clearProperty(CrcDescription.RETENTION_PROPERTY);
            } else {
                System.setProperty(CrcDescription.RETENTION_PROPERTY, saved);
            }
        }
        assertSame(Retention.SOFT, new Interner<String, Object>(Retention.SOFT).retention(), "Wrong retention of the pool");
    }

    @Test
    void testEvictionOfTheClassDirectory() {
        // the unit tests use target/classes, not the multi-release jar: the version of Eviction before Java 9
        assertFalse(Eviction.isAsynchronous(), "The pool has to remove the entries itself");
        Object referent = new Object();
        Runnable action = () -> { };
        assertThrows(UnsupportedOperationException.class, () -> Eviction.register(referent, action),
                "No cleaner is registered before Java 9");
    }

}
