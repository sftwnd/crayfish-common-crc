/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * How {@link Interner} removes the entries of the collected values. Before Java 9 the pool polls the queue of its
 * references on every access. The multi-release jar has another version of this class for Java 9+
 * (src/main/java9), where a {@code java.lang.ref.Cleaner} removes the entries. The class directory of a build and
 * a jar without the Multi-Release attribute use this version on every Java version.
 * Methods, not constants: javac would copy the value of a constant of this version into the callers
 */
final class Eviction {

    private Eviction() {
    }

    // whether a cleaner removes the entries, so that the pool does not have to poll the queue of its references
    static boolean isAsynchronous() {
        return false;
    }

    // registers the cleaning action of a value; not used before Java 9
    static void register(@NonNull final Object referent, @NonNull final Runnable action) {
        throw new UnsupportedOperationException("Eviction::register - the entries are removed on access before Java 9");
    }

}
