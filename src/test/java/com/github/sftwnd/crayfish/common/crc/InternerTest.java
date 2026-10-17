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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

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
        // the entry is removed on the next access to the pool, here by size()
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

    // waits until the thread is blocked by a lock: the lock of the entry in compute, as the factory of another thread
    // holds it; the thread found no value on the fast path before
    private static void awaitBlocked(AtomicReference<Thread> thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (thread.get() == null || thread.get().getState() != Thread.State.BLOCKED) {
            if (System.nanoTime() - deadline > 0) {
                throw new IllegalStateException("The second thread has not been blocked by the lock of the entry");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
    }

    @Test
    void testInternOfAKeyBeingCreated() throws Exception {
        // the path of intern where compute finds the value that another thread has just created, in every run:
        // testConcurrentIntern reaches it only when two of its threads meet inside compute
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        AtomicInteger created = new AtomicInteger();
        CountDownLatch creating = new CountDownLatch(1);
        AtomicReference<Thread> second = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // the factory of the first thread waits inside compute until the second thread waits for the entry
            Future<Object> first = executor.submit(() -> interner.intern("a", key -> {
                created.incrementAndGet();
                creating.countDown();
                awaitBlocked(second);
                return new Object();
            }));
            assertTrue(creating.await(30, TimeUnit.SECONDS), "The factory of the first thread has to be called");
            Future<Object> next = executor.submit(() -> {
                second.set(Thread.currentThread());
                return interner.intern("a", key -> {
                    created.incrementAndGet();
                    return new Object();
                });
            });
            Object value = first.get(30, TimeUnit.SECONDS);
            assertSame(value, next.get(30, TimeUnit.SECONDS), "The second thread has to get the value created by the first one");
            assertEquals(1, created.get(), "The value of the key has to be created once");
            assertSame(value, interner.intern("a", key -> new Object()), "The value created has to stay in the pool");
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
            assertSame(Retention.SOFT, Retention.configured(name -> CrcDescription.RETENTION_PROPERTY.equals(name) ? "soft" : null),
                    "The property of the name has to be read");
            assertSame(Retention.WEAK, Retention.configured(name -> {
                throw new SecurityException("no access to the property " + name);
            }), "A property that cannot be read has to give the default");
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
    void testInternRemovesTheEntryOfACollectedValue() throws InterruptedException {
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        WeakReference<Object> probe = intern(interner, "a", new AtomicInteger());
        Object value = interner.intern("b", key -> new Object());
        Gc.awaitCleared(probe, "The unused value has to be collected");
        // no thread removes the entries: the entry of the collected value stays until the pool is used
        assertTrue(interner.containsKey("a"), "The entry of the collected value has to stay until the next access");
        // the reference handler of the JVM queues the cleared reference after the collection: the access is repeated
        // until the pool finds it in its queue
        Gc.await(() -> interner.intern("b", key -> new Object()) == value && !interner.containsKey("a"),
                "The next intern of another key has to remove the entry of the collected value");
        assertEquals(1, interner.size(), "The entry of the value in use has to be kept");
    }

}
