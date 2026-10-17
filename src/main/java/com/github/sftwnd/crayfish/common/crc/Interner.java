/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A pool of canonical values: one value per key while the value is referenced. The pool refers to the values weakly
 * or softly only (see {@link Retention}), so the garbage collector collects a value that nobody uses.
 * The entry of a collected value is removed on the next access to the pool, which polls the queue of its references,
 * on every Java version. No cleaner removes the entries: its thread would keep the class loader of the library from
 * being collected, by the cleaning actions it keeps (classes of the library) and by the access control context it
 * inherits from its caller
 * @param <K> type of the keys
 * @param <V> type of the values
 */
final class Interner<K, V> {

    // the reference of an entry knows its key: a cleared reference removes its own entry only
    private interface Ref<K, V> {
        @Nullable V get();

        @NonNull K key();
    }

    private static final class WeakRef<K, V> extends WeakReference<V> implements Ref<K, V> {
        private final K key;

        WeakRef(final K key, final V value, @NonNull final ReferenceQueue<? super V> queue) {
            super(value, queue);
            this.key = key;
        }

        @Override
        public @NonNull K key() {
            return this.key;
        }
    }

    private static final class SoftRef<K, V> extends SoftReference<V> implements Ref<K, V> {
        private final K key;

        SoftRef(final K key, final V value, @NonNull final ReferenceQueue<? super V> queue) {
            super(value, queue);
            this.key = key;
        }

        @Override
        public @NonNull K key() {
            return this.key;
        }
    }

    private final ConcurrentMap<K, Ref<K, V>> entries = new ConcurrentHashMap<>();
    private final Retention retention;
    // the cleared references, polled on every access
    private final ReferenceQueue<V> queue = new ReferenceQueue<>();

    Interner(@NonNull final Retention retention) {
        this.retention = Objects.requireNonNull(retention, "Interner::new - retention is null");
    }

    @NonNull Retention retention() {
        return this.retention;
    }

    /**
     * The value of the key: the value in the pool if it is still referenced, otherwise a new value of the factory.
     * Concurrent calls for a key create one value: the factory is called under the lock of the entry
     */
    @NonNull V intern(@NonNull final K key, @NonNull final Function<? super K, ? extends V> factory) {
        expungeStaleEntries();
        Ref<K, V> ref = entries.get(key);
        V value = ref == null ? null : ref.get();
        if (value != null) {
            return value;
        }
        AtomicReference<V> interned = new AtomicReference<>();
        entries.compute(key, (k, current) -> {
            V existing = current == null ? null : current.get();
            if (existing != null) {
                interned.set(existing);
                return current;
            }
            V created = Objects.requireNonNull(factory.apply(k), "Interner::intern - the factory returned null");
            Ref<K, V> fresh = retention == Retention.SOFT ? new SoftRef<>(k, created, queue) : new WeakRef<>(k, created, queue);
            interned.set(created);
            return fresh;
        });
        return interned.get();
    }

    // the number of entries; the entries of the collected values are removed first
    int size() {
        expungeStaleEntries();
        return entries.size();
    }

    // whether the pool has an entry of the key, also of a collected value whose entry is not removed yet
    boolean containsKey(@NonNull final K key) {
        return entries.containsKey(key);
    }

    // removes the entries of the collected values; the poll of an empty queue reads a volatile field only
    private void expungeStaleEntries() {
        for (Reference<? extends V> cleared = queue.poll(); cleared != null; cleared = queue.poll()) {
            Ref<?, ?> ref = (Ref<?, ?>) cleared;
            entries.remove(ref.key(), ref);
        }
    }

}
